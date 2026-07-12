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
}
