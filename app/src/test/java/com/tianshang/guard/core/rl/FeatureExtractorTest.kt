package com.tianshang.guard.core.rl

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class FeatureExtractorTest : BaseUnitTest() {

    private lateinit var extractor: FeatureExtractor

    @Before
    fun setUp() {
        extractor = FeatureExtractor()
    }

    @Test
    fun `extractFeatures detects Chinese language`() {
        val fv = extractor.extractFeatures("这是一条测试短信")
        Assert.assertEquals(FeatureVector.Language.CHINESE, fv.language)
    }

    @Test
    fun `extractFeatures detects English language`() {
        val fv = extractor.extractFeatures("This is a test message")
        Assert.assertEquals(FeatureVector.Language.ENGLISH, fv.language)
    }

    @Test
    fun `extractFeatures detects phone number`() {
        val fv = extractor.extractFeatures("联系我 13800138000 谢谢")
        Assert.assertTrue(fv.hasPhoneNumber)
    }

    @Test
    fun `extractFeatures detects URL`() {
        val fv = extractor.extractFeatures("点击 https://evil.com 查看")
        Assert.assertTrue(fv.urlCount > 0)
        Assert.assertTrue(fv.linkCount > 0)
    }

    @Test
    fun `extractFeatures detects IP address`() {
        val fv = extractor.extractFeatures("服务器 192.168.1.1 异常")
        Assert.assertTrue(fv.ipAddressCount > 0)
    }

    @Test
    fun `extractFeatures detects financial keywords`() {
        val fv = extractor.extractFeatures("您的银行账户需要转账验证")
        Assert.assertTrue(fv.hasFinancialKeywords)
        Assert.assertTrue(fv.financialWordCount > 0)
    }

    @Test
    fun `extractFeatures detects urgency keywords`() {
        val fv = extractor.extractFeatures("立即处理，账户即将冻结")
        Assert.assertTrue(fv.hasUrgencyKeywords)
        Assert.assertTrue(fv.urgencyWordCount > 0)
    }

    @Test
    fun `extractFeatures detects threat keywords`() {
        val fv = extractor.extractFeatures("你涉嫌洗钱，公安已立案调查")
        Assert.assertTrue(fv.hasThreatKeywords)
        Assert.assertTrue(fv.threatWordCount > 0)
    }

    @Test
    fun `extractFeatures calculates text length`() {
        val fv = extractor.extractFeatures("短线")
        Assert.assertEquals(2, fv.textLength)
    }

    @Test
    fun `extractFeatures calculates special char ratio`() {
        val fv = extractor.extractFeatures("a!@#b")
        Assert.assertTrue(fv.specialCharRatio > 0.5f)
    }

    @Test
    fun `extractFeatures calculates digit ratio`() {
        val fv = extractor.extractFeatures("abc123")
        Assert.assertEquals(0.5f, fv.digitRatio, 0.01f)
    }

    @Test
    fun `extractFeatures counts exclamation marks`() {
        val fv = extractor.extractFeatures("紧急！！！立即处理！")
        Assert.assertTrue(fv.exclamationCount >= 4)
    }

    @Test
    fun `extractFeatures counts emoji`() {
        val fv = extractor.extractFeatures("您好😊明天见🎉")
        Assert.assertTrue(fv.emojiCount > 0)
    }

    @Test
    fun `extractFeatures detects Chinese char ratio`() {
        val fv = extractor.extractFeatures("中文测试abc")
        Assert.assertTrue(fv.chineseCharRatio > 0)
        Assert.assertTrue(fv.chineseCharRatio < 1f)
    }

    @Test
    fun `extractFeatures detects sender type from phone number`() {
        val fv = extractor.extractFeatures("13800138000\n这是短信内容")
        Assert.assertEquals(FeatureVector.SenderType.PHONE_NUMBER, fv.senderType)
    }

    @Test
    fun `extractFeatures detects sender type from short code`() {
        val fv = extractor.extractFeatures("10086\n您的积分即将过期")
        Assert.assertEquals(FeatureVector.SenderType.SHORT_CODE, fv.senderType)
    }

    @Test
    fun `extractFeatures calculates sentiment score`() {
        val fv = extractor.extractFeatures("危险警告！您的账户已被冻结")
        Assert.assertTrue(fv.sentimentScore < 0.5f)
    }

    @Test
    fun `extractFeatures counts repeated characters`() {
        val fv = extractor.extractFeatures("aaaabbbccd")
        Assert.assertTrue(fv.repeatedCharCount > 0)
    }

    @Test
    fun `extractFeatures handles empty text`() {
        val fv = extractor.extractFeatures("")
        Assert.assertEquals(0, fv.textLength)
        Assert.assertEquals(FeatureVector.Language.ENGLISH, fv.language)
        Assert.assertEquals(0f, fv.specialCharRatio, 0.01f)
        Assert.assertEquals(0f, fv.digitRatio, 0.01f)
    }

    @Test
    fun `toFloatArray produces 24 elements`() {
        val fv = extractor.extractFeatures("测试文本 https://evil.com 13800138000 紧急！")
        val arr = fv.toFloatArray()
        Assert.assertEquals(24, arr.size)
        arr.forEach { Assert.assertTrue("Element $it out of [0,1]", it in 0f..1f) }
    }

    @Test
    fun `extractFeatures detects money symbols`() {
        val fv = extractor.extractFeatures("转账 ¥10000 到安全账户")
        Assert.assertTrue(fv.moneySymbolCount > 0)
    }

    @Test
    fun `extractFeatures detects uppercase ratio`() {
        val fv = extractor.extractFeatures("ALERT: Your account is SUSPENDED!")
        Assert.assertTrue(fv.uppercaseRatio > 0f)
    }

    @Test
    fun `extractFeatures calculates avg word length`() {
        val fv = extractor.extractFeatures("hello world")
        Assert.assertEquals(5f, fv.avgWordLength, 0.1f)
    }
}
