package com.tianshang.guard.core.ml

import android.content.Context
import com.tianshang.guard.core.util.SecureLog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * BPE (Byte Pair Encoding) tokenizer for TianshangGuard.
 * 
 * This tokenizer loads a pre-trained BPE model and vocabulary from assets,
 * and performs tokenization using the BPE algorithm.
 * 
 * The vocabulary file should be a JSON file mapping tokens to IDs.
 */
class BpeTokenizer(private val context: Context? = null) {

    private var vocab: Map<String, Int> = emptyMap()
    private var invVocab: Map<Int, String> = emptyMap()
    private var isLoaded = false

    companion object {
        private const val VOCAB_FILE = "tokenizer/bpe_tokenizer_vocab.json"
        private const val MODEL_FILE = "tokenizer/bpe_tokenizer.model"
        private const val PAD_ID = 0
        private const val UNK_ID = 1
        private const val CLS_ID = 2
        private const val SEP_ID = 3
        private const val MAX_LENGTH = 512

        fun fromVocabMap(vocab: Map<String, Int>): BpeTokenizer {
            val tokenizer = BpeTokenizer(context = null)
            tokenizer.vocab = vocab
            tokenizer.invVocab = vocab.entries.associate { (k, v) -> v to k }
            tokenizer.isLoaded = true
            return tokenizer
        }
    }

    /**
     * Load the BPE vocabulary from assets.
     */
    fun load(): Boolean {
        val ctx = context ?: return false
        return try {
            val vocabJson = ctx.assets.open(VOCAB_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(vocabJson)
            val vocabMap = mutableMapOf<String, Int>()
            val invVocabMap = mutableMapOf<Int, String>()

            for (key in jsonObject.keys()) {
                val id = jsonObject.getInt(key)
                vocabMap[key] = id
                invVocabMap[id] = key
            }

            vocab = vocabMap
            invVocab = invVocabMap
            isLoaded = true

            SecureLog.i("BpeTokenizer", "Loaded vocabulary: ${vocab.size} tokens")
            true
        } catch (e: Exception) {
            SecureLog.e("BpeTokenizer", "Failed to load vocabulary", e)
            false
        }
    }

    /**
     * Tokenize text and return token IDs.
     */
    fun encode(text: String, maxLength: Int = MAX_LENGTH): LongArray {
        if (!isLoaded) {
            SecureLog.w("BpeTokenizer", "Tokenizer not loaded, falling back to byte tokenizer")
            return ByteTokenizer.encode(text, maxLength)
        }

        val tokens = tokenize(text)
        val ids = tokens.map { vocab[it] ?: UNK_ID.toLong() }

        // Build final token array: [CLS] + tokens + [SEP] + padding
        val result = LongArray(maxLength) { PAD_ID.toLong() }
        result[0] = CLS_ID.toLong()

        val maxContentLength = maxLength - 2 // Reserve space for [CLS] and [SEP]
        val contentLength = minOf(ids.size, maxContentLength)

        for (i in 0 until contentLength) {
            result[i + 1] = ids[i].toLong()
        }
        result[contentLength + 1] = SEP_ID.toLong()

        return result
    }

    /**
     * Tokenize text into subword tokens using BPE.
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0

        while (i < text.length) {
            val ch = text[i]

            when {
                // Chinese characters: each character is a token
                ch in '\u4E00'..'\u9FFF' -> {
                    tokens.add(ch.toString())
                    i++
                }
                // Digits: each digit is a token
                ch.isDigit() -> {
                    tokens.add(ch.toString())
                    i++
                }
                // ASCII letters: try to find longest matching token
                ch.isLetter() && ch.code < 128 -> {
                    val word = buildAsciiWord(text, i)
                    val wordTokens = bpeTokenize(word)
                    tokens.addAll(wordTokens)
                    i += word.length
                }
                // Other characters: each character is a token
                else -> {
                    tokens.add(ch.toString())
                    i++
                }
            }
        }

        return tokens
    }

    /**
     * Build a contiguous ASCII word from the text.
     */
    private fun buildAsciiWord(text: String, start: Int): String {
        val sb = StringBuilder()
        var i = start
        while (i < text.length && text[i].isLetter() && text[i].code < 128) {
            sb.append(text[i].lowercaseChar())
            i++
        }
        return sb.toString()
    }

    /**
     * Apply BPE algorithm to a word.
     */
    private fun bpeTokenize(word: String): List<String> {
        if (word.isEmpty()) return emptyList()

        // Start with individual characters
        var tokens = word.map { it.toString() }.toMutableList()

        // Repeatedly merge the most frequent pair
        while (tokens.size > 1) {
            var bestPair: Pair<String, String>? = null
            var bestId = Int.MAX_VALUE

            // Find the pair with the lowest ID (highest priority)
            for (i in 0 until tokens.size - 1) {
                val pair = tokens[i] + tokens[i + 1]
                val id = vocab[pair] ?: continue
                if (id < bestId) {
                    bestId = id
                    bestPair = Pair(tokens[i], tokens[i + 1])
                }
            }

            if (bestPair == null) break

            // Merge the best pair
            val newTokens = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                if (i < tokens.size - 1 && tokens[i] == bestPair.first && tokens[i + 1] == bestPair.second) {
                    newTokens.add(tokens[i] + tokens[i + 1])
                    i += 2
                } else {
                    newTokens.add(tokens[i])
                    i++
                }
            }
            tokens = newTokens
        }

        return tokens
    }

    /**
     * Decode token IDs back to text.
     */
    fun decode(ids: LongArray): String {
        if (!isLoaded) return ""

        val sb = StringBuilder()
        for (id in ids) {
            val idInt = id.toInt()
            if (idInt == PAD_ID || idInt == CLS_ID || idInt == SEP_ID) continue
            val token = invVocab[idInt] ?: continue
            // Remove BPE markers
            val cleanToken = token.replace("▁", " ").trim()
            sb.append(cleanToken)
        }
        return sb.toString().trim()
    }

    /**
     * Check if the tokenizer is loaded.
     */
    fun isReady(): Boolean = isLoaded
}
