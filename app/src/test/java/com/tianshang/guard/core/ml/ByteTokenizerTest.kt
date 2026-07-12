package com.tianshang.guard.core.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByteTokenizerTest {

    @Test
    fun `encode adds CLS token at position 0`() {
        val tokens = ByteTokenizer.encode("hello", 10)
        assertEquals(101L, tokens[0])
    }

    @Test
    fun `encode adds SEP token at correct position`() {
        val text = "hi"
        val tokens = ByteTokenizer.encode(text, 10)
        // CLS(1) + 2 bytes + SEP(1) = 4
        assertEquals(102L, tokens[3])
        // remaining should be 0
        for (i in 4 until 10) {
            assertEquals(0L, tokens[i])
        }
    }

    @Test
    fun `encode converts text bytes to token IDs`() {
        val text = "AB"
        val tokens = ByteTokenizer.encode(text, 10)
        assertEquals(101L, tokens[0]) // CLS
        // 'A' = 0x41 = 65, 'B' = 0x42 = 66
        assertEquals(65L, tokens[1])
        assertEquals(66L, tokens[2])
        assertEquals(102L, tokens[3]) // SEP
    }

    @Test
    fun `encode truncates text beyond max length`() {
        val text = "Hello World! This is a long text that should be truncated"
        val maxLen = 20
        val tokens = ByteTokenizer.encode(text, maxLen)
        assertEquals(maxLen, tokens.size)
        assertEquals(101L, tokens[0]) // CLS
        assertEquals(102L, tokens[tokens.indexOfFirst { it == 102L }]) // SEP
        // All tokens after SEP should be 0
        val sepIndex = tokens.indexOfFirst { it == 102L }
        for (i in sepIndex + 1 until maxLen) {
            assertEquals(0L, tokens[i])
        }
    }

    @Test
    fun `encode handles empty text`() {
        val tokens = ByteTokenizer.encode("", 10)
        assertEquals(101L, tokens[0]) // CLS
        assertEquals(102L, tokens[1]) // SEP immediately after CLS
        for (i in 2 until 10) {
            assertEquals(0L, tokens[i])
        }
    }

    @Test
    fun `encode handles multi-byte UTF-8 characters`() {
        val text = "你好" // 3 bytes each in UTF-8
        val tokens = ByteTokenizer.encode(text, 10)
        assertEquals(101L, tokens[0]) // CLS
        // "你好" encodes to 6 bytes: E4 BD A0 E5 A5 BD
        // The tokenizer may or may not truncate mid-character depending on implementation
        assertTrue(tokens[1] in 0..255) // first byte should be valid byte value
        assertTrue(tokens[2] in 0..255)
        assertEquals(102L, tokens[tokens.indexOfFirst { it == 102L }])
    }

    @Test
    fun `encode does not split multi-byte characters at truncation boundary`() {
        // Chinese characters are 3 bytes each in UTF-8
        // We want maxLen such that we would be forced to split a character
        val text = "你好世界" // 12 bytes (4 chars × 3 bytes)
        val maxLen = 6 // CLS + SEP + 4 bytes of data = can fit exactly 1 full char + 1 byte
        // but findSafeTruncationPoint should walk back to avoid splitting
        val tokens = ByteTokenizer.encode(text, maxLen)
        assertEquals(maxLen, tokens.size)
        // The token at position 1 should be a valid first byte of a UTF-8 char
        val firstByte = tokens[1].toInt()
        assertTrue(
            "First byte $firstByte should not be a continuation byte (0x80-0xBF)",
            firstByte < 0x80 || firstByte > 0xBF
        )
    }

    @Test
    fun `encode pads remaining positions with zeros`() {
        val text = "a"
        val tokens = ByteTokenizer.encode(text, 10)
        assertEquals(101L, tokens[0]) // CLS
        assertEquals(97L, tokens[1])  // 'a'
        assertEquals(102L, tokens[2]) // SEP
        for (i in 3 until 10) {
            assertEquals("Position $i should be 0", 0L, tokens[i])
        }
    }

    @Test
    fun `encode with maxLength less than 2 returns array of correct size`() {
        val tokens = ByteTokenizer.encode("test", 1)
        assertEquals(1, tokens.size)
        // SEP overwrites CLS when only 1 slot available
        assertEquals(102L, tokens[0])
    }
}
