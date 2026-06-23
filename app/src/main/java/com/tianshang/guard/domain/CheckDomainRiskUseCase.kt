package com.tianshang.guard.domain

import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.dns.DnsResult

class CheckDomainRiskUseCase(
    private val dnsEngine: DnsEngine
) {
    fun execute(domain: String): DnsResult {
        return dnsEngine.resolve(domain)
    }
}
