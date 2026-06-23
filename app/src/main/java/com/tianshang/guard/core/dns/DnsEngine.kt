package com.tianshang.guard.core.dns

sealed class DnsResult {
    data class Allow(val ip: String) : DnsResult()
    data class Block(val reason: BlockReason) : DnsResult()
    data class Unknown(val riskScore: Float) : DnsResult()
}

enum class BlockReason {
    BLACKLIST,
    SUSPICIOUS,
    USER_OVERRIDE
}

interface DnsEngine {
    fun start()
    fun stop()
    fun resolve(domain: String): DnsResult
    fun addToWhitelist(domain: String)
    fun addToBlacklist(domain: String)
}
