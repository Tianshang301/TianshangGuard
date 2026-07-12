package com.tianshang.guard.core.ml

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BpeTokenizerTest : BaseUnitTest() {

    private lateinit var tokenizer: BpeTokenizer

    @Before
    fun setUp() {
        val vocab = mapOf(
            " " to 0, "[PAD]" to 0, "[UNK]" to 1, "[CLS]" to 2, "[SEP]" to 3,
            "h" to 4, "e" to 5, "l" to 6, "o" to 7, "w" to 8, "r" to 9, "d" to 10,
            "he" to 11, "ll" to 12, "wo" to 14, "ld" to 16,
            "hel" to 17, "wor" to 18,
            "i" to 20, "t" to 21, "s" to 22,
            "is" to 23
        )
        tokenizer = BpeTokenizer.fromVocabMap(vocab)
    }

    @Test
    fun `isReady returns true after fromVocabMap`() {
        Assert.assertTrue(tokenizer.isReady())
    }

    @Test
    fun `encode starts with CLS`() {
        val result = tokenizer.encode("hi", maxLength = 16)
        Assert.assertEquals(2L, result[0])
    }

    @Test
    fun `encode contains SEP after content`() {
        val result = tokenizer.encode("hello", maxLength = 16)
        // "hello" → bpe → ["he", "ll", "o"] → ids [11, 12, 7]
        // result = [CLS=2, 11, 12, 7, SEP=3, PAD...]
        Assert.assertEquals(3L, result[4]) // SEP at contentLength+1 = 3+1 = 4
    }

    @Test
    fun `encode pads remaining positions with 0`() {
        val result = tokenizer.encode("hi", maxLength = 10)
        // content: h→4, i→20, contentLength = 2
        // SEP at index 3, indices 4-9 should be PAD(0)
        for (i in 4 until 10) {
            Assert.assertEquals("index $i should be PAD", 0L, result[i])
        }
    }

    @Test
    fun `encode unknown character uses UNK token`() {
        val result = tokenizer.encode("z", maxLength = 6)
        // 'z' not in vocab → UNK(1) at index 1
        Assert.assertEquals(1L, result[1])
    }

    @Test
    fun `encode respects max length by truncating`() {
        val result = tokenizer.encode("hello world", maxLength = 7)
        // maxContentLength = 7-2 = 5, but we have more tokens
        // result size = 7
        Assert.assertEquals(7, result.size)
    }

    @Test
    fun `encode empty string returns CLS SEP with padding`() {
        val result = tokenizer.encode("", maxLength = 6)
        Assert.assertEquals(2L, result[0]) // [CLS]
        Assert.assertEquals(3L, result[1]) // [SEP]
        for (i in 2 until 6) {
            Assert.assertEquals("index $i should be PAD", 0L, result[i])
        }
    }

    @Test
    fun `decode handles empty result`() {
        val unloadedTokenizer = BpeTokenizer()
        val result = unloadedTokenizer.decode(LongArray(10))
        Assert.assertEquals("", result)
    }

    @Test
    fun `fallback to ByteTokenizer when not loaded`() {
        val unloadedTokenizer = BpeTokenizer()
        Assert.assertFalse(unloadedTokenizer.isReady())
        val result = unloadedTokenizer.encode("hello", maxLength = 8)
        // ByteTokenizer: [CLS=101, bytes..., SEP=102]
        Assert.assertEquals(101L, result[0])
        // Find the last non-PAD position
        val lastNonZero = result.indexOfLast { it != 0L }
        Assert.assertEquals(102L, result[lastNonZero])
    }
}
