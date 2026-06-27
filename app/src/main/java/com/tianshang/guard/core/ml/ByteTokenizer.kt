package com.tianshang.guard.core.ml

/**
 * Byte-level tokenizer for TianshangGuard.
 * 
 * This tokenizer maps each UTF-8 byte to a token ID (0-255).
 * It's used as a fallback when the BPE tokenizer is not available.
 */
object ByteTokenizer {

    /**
     * Encode text to byte-level token IDs.
     * Ensures multi-byte UTF-8 characters are not split mid-character.
     */
    fun encode(text: String, maxLength: Int): LongArray {
        val tokens = LongArray(maxLength) { 0L }
        tokens[0] = 101L // [CLS]
        val textBytes = text.encodeToByteArray()
        // Find safe truncation point that doesn't split multi-byte characters
        val safeEnd = findSafeTruncationPoint(textBytes, maxLength - 2)
        for (i in 0 until safeEnd) {
            tokens[i + 1] = textBytes[i].toLong() and 0xFF
        }
        tokens[minOf(safeEnd + 1, maxLength - 1)] = 102L // [SEP]
        return tokens
    }

    /**
     * Find a safe truncation point that doesn't split a multi-byte UTF-8 character.
     * UTF-8 continuation bytes start with 10xxxxxx (0x80-0xBF).
     * We truncate before any incomplete multi-byte sequence.
     */
    private fun findSafeTruncationPoint(bytes: ByteArray, maxLen: Int): Int {
        if (bytes.size <= maxLen) return bytes.size
        // Walk back to find a non-continuation byte
        var pos = maxLen
        while (pos > 0 && (bytes[pos].toInt() and 0xC0) == 0x80) {
            pos--
        }
        return pos
    }
}
