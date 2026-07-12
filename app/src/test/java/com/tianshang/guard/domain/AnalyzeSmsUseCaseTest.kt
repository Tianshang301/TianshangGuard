package com.tianshang.guard.domain

import com.tianshang.guard.BaseUnitTest
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.core.ml.RuleBasedEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AnalyzeSmsUseCaseTest : BaseUnitTest() {

    private val mlEngine = mockk<MlEngine>(relaxed = true)
    private val ruleEngine = mockk<RuleBasedEngine>(relaxed = true)
    private lateinit var useCase: AnalyzeSmsUseCase

    @Before
    fun setUp() {
        useCase = AnalyzeSmsUseCase(mlEngine, ruleEngine)
    }

    @Test
    fun `execute returns SAFE for blank body`() {
        val result = useCase.execute("   ")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `execute returns SAFE for empty body`() {
        val result = useCase.execute("")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `execute returns DANGEROUS when rule engine flags DANGEROUS`() {
        every { ruleEngine.analyzeSms("phishing sms test") } returns RiskLevel.DANGEROUS
        every { mlEngine.analyzeSms(any()) } returns RiskLevel.SAFE
        val result = useCase.execute("phishing sms test")
        Assert.assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `execute returns max of rule and ML results`() {
        every { ruleEngine.analyzeSms("suspicious message") } returns RiskLevel.SUSPICIOUS
        every { mlEngine.analyzeSms("suspicious message") } returns RiskLevel.SAFE
        val result = useCase.execute("suspicious message")
        Assert.assertEquals(RiskLevel.SUSPICIOUS, result)
    }

    @Test
    fun `execute returns ML result when ML is riskier than rule`() {
        every { ruleEngine.analyzeSms("ml flags this") } returns RiskLevel.SUSPICIOUS
        every { mlEngine.analyzeSms("ml flags this") } returns RiskLevel.DANGEROUS
        val result = useCase.execute("ml flags this")
        Assert.assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `execute returns SAFE when both engines return SAFE`() {
        every { ruleEngine.analyzeSms("normal text") } returns RiskLevel.SAFE
        every { mlEngine.analyzeSms("normal text") } returns RiskLevel.SAFE
        val result = useCase.execute("normal text")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }
}
