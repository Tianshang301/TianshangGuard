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
     */
    fun encode(text: String, maxLength: Int): LongArray {
        val tokens = LongArray(maxLength) { 0L }
        tokens[0] = 101L // [CLS]
        val textBytes = text.encodeToByteArray().take(maxLength - 2)
        for (i in textBytes.indices) {
            tokens[i + 1] = textBytes[i].toLong() and 0xFF
        }
        tokens[minOf(textBytes.size + 1, maxLength - 1)] = 102L // [SEP]
        return tokens
    }
}
