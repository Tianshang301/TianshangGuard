package com.tianshang.guard.core.ml

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RuleBasedEngineTest {

    private lateinit var engine: RuleBasedEngine

    @Before
    fun setUp() {
        engine = RuleBasedEngine()
    }

    @Test
    fun `safe text returns SAFE`() {
        val result = engine.analyzeWebPage("今天天气真不错，适合出去玩。")
        assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `one phishing keyword returns SUSPICIOUS`() {
        val result = engine.analyzeWebPage("请点击验证身份链接")
        assertEquals(RiskLevel.SUSPICIOUS, result)
    }

    @Test
    fun `two phishing keywords returns SUSPICIOUS`() {
        val result = engine.analyzeWebPage("您的安全账户涉嫌洗钱，请配合调查")
        assertEquals(RiskLevel.SUSPICIOUS, result)
    }

    @Test
    fun `three phishing keywords returns DANGEROUS`() {
        val result = engine.analyzeWebPage(
            "您的安全账户涉嫌洗钱，请进行屏幕共享验证身份。转账到安全账户。"
        )
        assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `empty text returns SAFE`() {
        val result = engine.analyzeWebPage("")
        assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `all phishing keywords returns DANGEROUS`() {
        val text = "安全账户 屏幕共享 涉嫌洗钱 验证身份 银行卡密码 转账到 退款链接 点击验证"
        val result = engine.analyzeWebPage(text)
        assertEquals(RiskLevel.DANGEROUS, result)
    }

    @Test
    fun `analyzeDomain always returns SAFE`() {
        assertEquals(RiskLevel.SAFE, engine.analyzeDomain("phishing.com"))
    }

    @Test
    fun `isModelLoaded returns true`() {
        assertEquals(true, engine.isModelLoaded())
    }
}
