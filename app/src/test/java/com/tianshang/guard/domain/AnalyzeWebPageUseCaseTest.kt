package com.tianshang.guard.domain

import com.tianshang.guard.BaseUnitTest
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AnalyzeWebPageUseCaseTest : BaseUnitTest() {

    private val mlEngine = mockk<MlEngine>(relaxed = true)
    private lateinit var useCase: AnalyzeWebPageUseCase

    @Before
    fun setUp() {
        useCase = AnalyzeWebPageUseCase(mlEngine)
    }

    @Test
    fun `execute delegates to mlEngine analyzeWebPage`() {
        every { mlEngine.analyzeWebPage("phishing content here") } returns RiskLevel.DANGEROUS
        val result = useCase.execute("phishing content here")
        Assert.assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `execute returns SAFE when ML returns SAFE`() {
        every { mlEngine.analyzeWebPage("safe content") } returns RiskLevel.SAFE
        val result = useCase.execute("safe content")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }
}
