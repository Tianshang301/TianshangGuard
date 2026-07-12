package com.tianshang.guard.domain

import com.tianshang.guard.BaseUnitTest
import com.tianshang.guard.core.dns.BlockReason
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.dns.DnsResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CheckDomainRiskUseCaseTest : BaseUnitTest() {

    private val dnsEngine = mockk<DnsEngine>(relaxed = true)
    private lateinit var useCase: CheckDomainRiskUseCase

    @Before
    fun setUp() {
        useCase = CheckDomainRiskUseCase(dnsEngine)
    }

    @Test
    fun `execute delegates to dnsEngine resolve`() {
        every { dnsEngine.resolve("example.com") } returns DnsResult.Allow
        val result = useCase.execute("example.com")
        Assert.assertTrue(result is DnsResult.Allow)
    }

    @Test
    fun `execute returns block result for blacklisted domain`() {
        every { dnsEngine.resolve("evil.com") } returns DnsResult.Block(BlockReason.BLACKLIST)
        val result = useCase.execute("evil.com")
        val block = result as? DnsResult.Block
        Assert.assertNotNull(block)
        Assert.assertEquals(BlockReason.BLACKLIST, block!!.reason)
    }

    @Test
    fun `execute returns unknown for unresolved domain`() {
        every { dnsEngine.resolve("unknown.com") } returns DnsResult.Unknown(0.5f)
        val result = useCase.execute("unknown.com")
        Assert.assertTrue(result is DnsResult.Unknown)
    }
}
