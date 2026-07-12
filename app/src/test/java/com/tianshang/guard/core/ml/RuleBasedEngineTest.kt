package com.tianshang.guard.core.ml

import android.content.Context
import android.content.res.AssetManager
import com.tianshang.guard.BaseUnitTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException

class RuleBasedEngineTest : BaseUnitTest() {

    private lateinit var engine: RuleBasedEngine

    @Before
    fun setUp() {
        val mockAssets = mockk<AssetManager>()
        every { mockAssets.open(any<String>()) } throws FileNotFoundException("No assets in test")
        val mockContext = mockk<Context>()
        every { mockContext.assets } returns mockAssets
        engine = RuleBasedEngine(mockContext)
    }

    @Test
    fun `SMS analysis returns DANGEROUS for 3+ keyword matches`() {
        val text = "您的安全账户异常，涉嫌洗钱案件，请配合调查立即验证身份"
        val result = engine.analyzeSms(text)
        Assert.assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `SMS analysis returns SUSPICIOUS for 1-2 keyword matches`() {
        // "安全账户" is in the default SMS keyword list → 1 match
        val text = "您的安全账户异常，请立即处理"
        val result = engine.analyzeSms(text)
        Assert.assertEquals(RiskLevel.SUSPICIOUS, result)
    }

    @Test
    fun `SMS analysis returns SAFE for 0 keyword matches`() {
        val text = "明天下午三点在咖啡厅见面"
        val result = engine.analyzeSms(text)
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `Web page analysis returns DANGEROUS for 3+ keyword matches`() {
        val text = "安全账户屏幕共享涉嫌洗钱"
        val result = engine.analyzeWebPage(text)
        Assert.assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `Web page analysis returns SUSPICIOUS for 1-2 keyword matches`() {
        val text = "请点击验证您的身份"
        val result = engine.analyzeWebPage(text)
        Assert.assertEquals(RiskLevel.SUSPICIOUS, result)
    }

    @Test
    fun `Web page analysis returns SAFE for 0 keyword matches`() {
        val text = "今天的天气真好"
        val result = engine.analyzeWebPage(text)
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `Domain analysis always returns SAFE`() {
        Assert.assertEquals(RiskLevel.SAFE, engine.analyzeDomain("phishing.com"))
        Assert.assertEquals(RiskLevel.SAFE, engine.analyzeDomain("example.org"))
        Assert.assertEquals(RiskLevel.SAFE, engine.analyzeDomain(""))
    }

    @Test
    fun `isModelLoaded returns true after construction`() {
        Assert.assertTrue(engine.isModelLoaded(ModelType.URL))
    }


}
