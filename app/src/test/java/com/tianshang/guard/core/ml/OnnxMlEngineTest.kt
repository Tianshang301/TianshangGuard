package com.tianshang.guard.core.ml

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OnnxMlEngineTest : BaseUnitTest() {

    private lateinit var engine: OnnxMlEngine

    @Before
    fun setUp() {
        engine = OnnxMlEngine(ortEnvironment = null, bpeTokenizer = null)
    }

    @Test
    fun `analyzeWebPage returns SAFE when no model loaded`() {
        val result = engine.analyzeWebPage("https://example.com")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `analyzeSms returns SAFE when no model loaded`() {
        val result = engine.analyzeSms("test message")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `isModelLoaded returns false for unloaded model`() {
        Assert.assertFalse(engine.isModelLoaded(ModelType.URL))
    }

    @Test
    fun `analyzeWithModelScore returns 0 when model not loaded`() {
        val score = engine.analyzeWithModelScore("test", ModelType.URL)
        Assert.assertEquals(0f, score, 0.001f)
    }

    @Test
    fun `analyzeDomain returns SAFE for normal domain`() {
        val result = engine.analyzeDomain("google.com")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `analyzeDomain returns SUSPICIOUS for domain with homograph chars`() {
        val result = engine.analyzeDomain("\u0400\u0401\u0402\u0403-example.com")
        Assert.assertEquals(RiskLevel.SUSPICIOUS, result)
    }

    @Test
    fun `analyzeDomain returns SAFE for empty domain`() {
        val result = engine.analyzeDomain("")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `analyzeDomain returns SAFE for all-ASCII domain`() {
        val result = engine.analyzeDomain("normal-site-123.com")
        Assert.assertEquals(RiskLevel.SAFE, result)
    }

    @Test
    fun `analyzeSmsScore returns 0 when models not loaded`() {
        val score = engine.analyzeSmsScore("Click http://phish.com to verify")
        Assert.assertEquals(0f, score, 0.001f)
    }

    @Test
    fun `close does not crash when sessions map is empty`() {
        engine.close()
        Assert.assertTrue(engine.sessions.isEmpty())
    }
}
