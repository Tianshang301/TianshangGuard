package com.tianshang.guard.core.feedback

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Test

class FeedbackEngineTokenizerTest : BaseUnitTest() {

    @Test
    fun `tokenize splits Chinese characters`() {
        val tokens = FeedbackEngine.tokenize("你好世界")
        Assert.assertFalse(tokens.isEmpty())
        Assert.assertTrue(tokens.all { it.isNotEmpty() })
    }

    @Test
    fun `tokenize splits on non-alphanumeric`() {
        val tokens = FeedbackEngine.tokenize("hello world!")
        Assert.assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun `tokenize generates 2-gram and 3-gram for long strings`() {
        val tokens = FeedbackEngine.tokenize("转账汇款")
        // 4 chars: bi-grams (3) + tri-grams (2) = 5 tokens
        Assert.assertTrue("Expected >= 5 tokens for 4-char string, got ${tokens.size}", tokens.size >= 5)
    }

    @Test
    fun `tokenize short strings preserved as-is`() {
        val tokens = FeedbackEngine.tokenize("测试")
        Assert.assertEquals(1, tokens.size)
        Assert.assertEquals("测试", tokens[0])
    }

    @Test
    fun `tokenize handles mixed Chinese and ASCII`() {
        val tokens = FeedbackEngine.tokenize("安全账户123")
        Assert.assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun `tokenize handles empty string`() {
        val tokens = FeedbackEngine.tokenize("")
        Assert.assertTrue(tokens.isEmpty())
    }

    @Test
    fun `tokenize lowercases ASCII letters`() {
        val tokens = FeedbackEngine.tokenize("HELLO")
        Assert.assertTrue(tokens.all { it.all { c -> !c.isUpperCase() } })
    }
}
