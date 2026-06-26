package com.tianshang.guard.core.dns

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocalDnsEngine(
    private val ruleRepository: RuleRepository,
    private val alertEngine: AlertEngine,
    private val homographDetector: HomographDetector,
    private val mlEngine: MlEngine
) : DnsEngine {

    private var bloomFilter = AdaptiveBloomFilter(
        expectedItems = 100_000,
        targetFpp = 0.001
    )
    private var cachedKnownDomains: List<String> = emptyList()
    private var lastDomainCacheUpdate = 0L
    private val domainCacheTtl = 60_000L // 1 minute TTL

    override fun resolve(domain: String): DnsResult {
        if (ruleRepository.isWhitelisted(domain)) {
            alertEngine.notifyVisited(domain)
            return DnsResult.Allow(resolveUpstream(domain))
        }

        val homographResult = homographDetector.detect(domain)
        if (homographResult is HomographResult.Detected) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.95f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        if (bloomFilter.mightContain(domain)) {
            if (ruleRepository.isBlacklisted(domain)) {
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

    private fun calculateDomainSimilarity(domain: String): Float {
        val now = System.currentTimeMillis()
        if (now - lastDomainCacheUpdate > domainCacheTtl) {
            cachedKnownDomains = ruleRepository.getKnownDomains()
            lastDomainCacheUpdate = now
        }
        if (cachedKnownDomains.isEmpty()) return 0f
        return cachedKnownDomains.maxOf { levenshteinSimilarity(domain, it) }
    }

    private fun levenshteinSimilarity(a: String, b: String): Float {
        val distance = levenshteinDistance(a, b)
        return 1f - (distance.toFloat() / maxOf(a.length, b.length))
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val costs = IntArray(b.length + 1)
        for (j in 0..b.length) costs[j] = j
        for (i in 1..a.length) {
            costs[0] = i
            var previous = i - 1
            for (j in 1..b.length) {
                val current = previous
                previous = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    current + if (a[i - 1] != b[j - 1]) 1 else 0
                )
            }
        }
        return costs[b.length]
    }

    private fun resolveUpstream(domain: String): String {
        return "1.1.1.1"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun start() {
        scope.launch {
            val allDomains = ruleRepository.getKnownDomains()
            bloomFilter = AdaptiveBloomFilter(
                expectedItems = if (allDomains.size * 2 > 100_000) allDomains.size * 2 else 100_000,
                targetFpp = 0.001
            )
            allDomains.forEach { bloomFilter.add(it) }
            cachedKnownDomains = allDomains
            lastDomainCacheUpdate = System.currentTimeMillis()
        }
    }

    override fun stop() {
        bloomFilter = AdaptiveBloomFilter(100_000, 0.001)
    }

    override fun addToWhitelist(domain: String) {
        runBlocking { ruleRepository.addToWhitelist(domain) }
    }

    override fun addToBlacklist(domain: String) {
        runBlocking {
            ruleRepository.addToBlacklist(domain)
            bloomFilter.add(domain)
        }
    }
}
