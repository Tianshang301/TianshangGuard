package com.tianshang.guard.core.dns

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class LocalDnsEngine(
    private val ruleRepository: RuleRepository,
    private val alertEngine: AlertEngine,
    private val homographDetector: HomographDetector,
    private val mlEngine: MlEngine,
    private val web3Detector: Web3DomainDetector = Web3DomainDetector()
) : DnsEngine {

    @Volatile private var bloomFilter = AdaptiveBloomFilter(
        expectedItems = 100_000,
        targetFpp = 0.001
    )
    @Volatile private var cachedKnownDomains: List<String> = emptyList()
    @Volatile private var lastDomainCacheUpdate = 0L
    private val domainCacheTtl = 60_000L
    @Volatile private var initialized = false
    
    // BK-tree for efficient domain similarity search
    @Volatile private var bkTree = BkTree(threshold = 3)
    // H-9: Lock for cache refresh to prevent concurrent BK-tree rebuilds
    private val cacheLock = Any()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun resolve(domain: String): DnsResult {
        if (!initialized) {
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        val isWhitelisted = runBlocking { ruleRepository.isWhitelisted(domain) }
        if (isWhitelisted) {
            // Still check for homograph attacks even on whitelisted domains
            val homographResult = homographDetector.detect(domain)
            if (homographResult is HomographResult.Detected) {
                alertEngine.showSuspiciousDomainWarning(domain, 0.95f)
                return DnsResult.Block(BlockReason.SUSPICIOUS)
            }
            alertEngine.notifyVisited(domain)
            return DnsResult.Allow
        }

        val homographResult = homographDetector.detect(domain)
        if (homographResult is HomographResult.Detected) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.95f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        if (bloomFilter.mightContain(domain)) {
            val isBlacklisted = runBlocking { ruleRepository.isBlacklisted(domain) }
            if (isBlacklisted) {
                alertEngine.notifyBlocked(domain, BlockReason.BLACKLIST)
                return DnsResult.Block(BlockReason.BLACKLIST)
            }
        }

        val pinyinScore = homographDetector.checkPinyinConfusion(domain)
        if (pinyinScore > 0.85f) {
            alertEngine.showSuspiciousDomainWarning(domain, pinyinScore)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        // v1.5.0: Web3 domain detection (ENS, Unstoppable, SID)
        val web3Result = web3Detector.detect(domain)
        if (web3Result is Web3DomainResult.Detected) {
            val riskScore = web3Detector.getRiskLevel(domain)
            if (riskScore >= 0.7f) {
                alertEngine.showSuspiciousDomainWarning(domain, riskScore)
                return DnsResult.Block(BlockReason.SUSPICIOUS)
            }
        }

        val similarityScore = calculateDomainSimilarity(domain)
        if (similarityScore > 0.85f) {
            alertEngine.showSuspiciousDomainWarning(domain, similarityScore)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        val mlRisk = mlEngine.analyzeDomain(domain)
        if (mlRisk == RiskLevel.DANGEROUS) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.9f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }
        if (mlRisk == RiskLevel.SUSPICIOUS) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.7f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        alertEngine.notifyVisited(domain)
        return DnsResult.Unknown(0.5f)
    }

    /**
     * Calculate domain similarity using BK-tree for efficient search.
     * BK-tree provides O(log n) average case instead of O(n) linear scan.
     */
    private fun calculateDomainSimilarity(domain: String): Float {
        val now = System.currentTimeMillis()
        // H-9: Double-checked locking to prevent concurrent BK-tree rebuilds
        if (now - lastDomainCacheUpdate > domainCacheTtl) {
            synchronized(cacheLock) {
                if (now - lastDomainCacheUpdate > domainCacheTtl) {
                    cachedKnownDomains = runBlocking { ruleRepository.getKnownDomains() }
                    lastDomainCacheUpdate = System.currentTimeMillis()
                    rebuildBkTree()
                }
            }
        }
        
        if (cachedKnownDomains.isEmpty()) return 0f
        
        val closest = bkTree.findClosest(domain) ?: return 0f
        val distance = closest.second
        val maxLen = maxOf(domain.length, closest.first.length)
        return 1f - (distance.toFloat() / maxLen)
    }

    /**
     * Rebuild BK-tree from cached known domains.
     */
    private fun rebuildBkTree() {
        bkTree = BkTree(threshold = 3)
        for (domain in cachedKnownDomains) {
            bkTree.insert(domain)
        }
    }

    override suspend fun start() {
        val allDomains = ruleRepository.getKnownDomains()
        val newFilter = AdaptiveBloomFilter(
            expectedItems = if (allDomains.size * 2 > 100_000) allDomains.size * 2 else 100_000,
            targetFpp = 0.001
        )
        allDomains.forEach { newFilter.add(it) }
        // M-16: Atomic swap to prevent partial initialization
        synchronized(cacheLock) {
            bloomFilter = newFilter
            cachedKnownDomains = allDomains
            lastDomainCacheUpdate = System.currentTimeMillis()
            rebuildBkTree()
            initialized = true
        }
    }

    override fun stop() {
        initialized = false
        bloomFilter = AdaptiveBloomFilter(100_000, 0.001)
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override suspend fun addToWhitelist(domain: String) {
        ruleRepository.addToWhitelist(domain)
    }

    override suspend fun addToBlacklist(domain: String) {
        ruleRepository.addToBlacklist(domain)
        bloomFilter.add(domain)
    }
}
