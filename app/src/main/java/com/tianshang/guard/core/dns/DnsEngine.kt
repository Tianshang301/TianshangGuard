package com.tianshang.guard.core.dns

sealed class DnsResult {
    data object Allow : DnsResult()
    data class Block(val reason: BlockReason) : DnsResult()
    data class Unknown(val riskScore: Float) : DnsResult()
}

enum class BlockReason {
    BLACKLIST,
    SUSPICIOUS,
    USER_OVERRIDE
}

interface DnsEngine {
    suspend fun start()
    fun stop()
    fun resolve(domain: String): DnsResult
    suspend fun addToWhitelist(domain: String)
    suspend fun addToBlacklist(domain: String)
    /**
     * Rebuild the in-memory block/allow filters (bloom filter, BK-tree, known
     * domain cache) from the current rule repository. Called after a remote rule
     * update so newly added blocklist entries take effect within the running VPN
     * session (H-03).
     */
    suspend fun reloadFilter()
}
