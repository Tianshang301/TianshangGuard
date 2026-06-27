package com.tianshang.guard.core.dns

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocalDnsEngine(
    private val ruleRepository: RuleRepository,
    private val alertEngine: AlertEngine,
    private val homographDetector: HomographDetector,
    private val mlEngine: MlEngine
) : DnsEngine {

    @Volatile private var bloomFilter = AdaptiveBloomFilter(
        expectedItems = 100_000,
        targetFpp = 0.001
    )
    private var cachedKnownDomains: List<String> = emptyList()
    private var lastDomainCacheUpdate = 0L
    private val domainCacheTtl = 60_000L
    @Volatile private var initialized = false
    
    // BK-tree for efficient domain similarity search
    private var bkTree = BkTree(threshold = 3)

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
        if (now - lastDomainCacheUpdate > domainCacheTtl) {
            cachedKnownDomains = runBlocking { ruleRepository.getKnownDomains() }
            lastDomainCacheUpdate = now
            // Rebuild BK-tree with updated domains
            rebuildBkTree()
        }
        
        if (cachedKnownDomains.isEmpty()) return 0f
        
        // Use BK-tree to find closest match within threshold
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

    override fun start() {
        runBlocking {
            val allDomains = ruleRepository.getKnownDomains()
            bloomFilter = AdaptiveBloomFilter(
                expectedItems = if (allDomains.size * 2 > 100_000) allDomains.size * 2 else 100_000,
                targetFpp = 0.001
            )
            allDomains.forEach { bloomFilter.add(it) }
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

    override fun addToWhitelist(domain: String) {
        scope.launch { ruleRepository.addToWhitelist(domain) }
    }

    override fun addToBlacklist(domain: String) {
        scope.launch {
            ruleRepository.addToBlacklist(domain)
            bloomFilter.add(domain)
        }
    }
}
