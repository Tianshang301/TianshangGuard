package com.tianshang.guard.core.retrieval

import com.tianshang.guard.core.util.SecureLog
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater

class Bm25Engine {

    private var docCount: Int = 0
    private var docLabels: ByteArray = ByteArray(0)
    private var index: Map<String, List<Posting>> = emptyMap()
    private var isLoaded: Boolean = false

    // Dynamic feedback index for runtime updates
    private val feedbackIndex = ConcurrentHashMap<String, MutableList<Posting>>()
    @Volatile private var feedbackDocCount = 0
    private val maxFeedbackDocs = 2000
    private val feedbackLock = Any()

    data class Posting(val docId: Int, val score: Int)
    data class RetrievalResult(val phishingRatio: Float, val topK: Int, val matchCount: Int)

    fun loadFromAssets(inputStream: InputStream): Boolean {
        return try {
            val compressed = inputStream.readBytes()
            val decompressed = decompressZlib(compressed)
            val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)

            // Read header
            docCount = buffer.int

            // Read doc labels
            docLabels = ByteArray(docCount)
            buffer.get(docLabels)

            // Read index entries
            val indexMap = mutableMapOf<String, List<Posting>>()
            while (buffer.hasRemaining()) {
                val tokenLen = buffer.short.toInt() and 0xFFFF
                if (tokenLen == 0 || buffer.remaining() < tokenLen) break
                val tokenBytes = ByteArray(tokenLen)
                buffer.get(tokenBytes)
                val token = String(tokenBytes, Charsets.UTF_8)

                val postingsCount = buffer.int
                if (postingsCount <= 0 || postingsCount > 100000 || buffer.remaining() < postingsCount.toLong() * 6) break
                val postings = mutableListOf<Posting>()
                for (i in 0 until postingsCount) {
                    val docId = buffer.int
                    val score = buffer.short.toInt() and 0xFFFF
                    postings.add(Posting(docId, score))
                }
                indexMap[token] = postings
            }

            index = indexMap
            isLoaded = true
            true
        } catch (e: Exception) {
            SecureLog.e("Bm25Engine", "Failed to load index", e)
            false
        }
    }

    /**
     * Add a feedback document to the dynamic index.
     * Uses a separate feedback index to avoid modifying the static index.
     * Thread-safe: synchronized to prevent race conditions.
     */
    fun addFeedbackDocument(text: String, isPhishing: Boolean) {
        synchronized(feedbackLock) {
            if (feedbackDocCount >= maxFeedbackDocs) return

            val docId = docCount + feedbackDocCount
            val tokens = tokenize(text)
            val score = if (isPhishing) 100 else 10

            for (token in tokens) {
                feedbackIndex.getOrPut(token) { mutableListOf() }.add(Posting(docId, score))
            }
            feedbackDocCount++
        }
    }

    fun query(text: String, topK: Int = 10): RetrievalResult {
        if (!isLoaded || docCount == 0) return RetrievalResult(0f, 0, 0)

        val tokens = tokenize(text)
        val docScores = mutableMapOf<Int, Int>() // docId -> cumulative score

        // Query static index
        for (token in tokens) {
            val postings = index[token] ?: continue
            for (posting in postings) {
                docScores[posting.docId] = (docScores[posting.docId] ?: 0) + posting.score
            }
        }

        // Query dynamic feedback index
        for (token in tokens) {
            val postings = feedbackIndex[token] ?: continue
            for (posting in postings) {
                docScores[posting.docId] = (docScores[posting.docId] ?: 0) + posting.score
            }
        }

        if (docScores.isEmpty()) return RetrievalResult(0f, 0, 0)

        // Get top-K documents by score
        val topDocs = docScores.entries
            .sortedByDescending { it.value }
            .take(topK)

        // Count phishing in top-K
        val phishingCount = topDocs.count { (docId, _) ->
            if (docId < docCount) {
                // Static document
                docId < docLabels.size && docLabels[docId].toInt() == 1
            } else {
                // Feedback document - check if score indicates phishing
                val feedbackScore = docScores[docId] ?: 0
                feedbackScore >= 50
            }
        }

        val matchCount = topDocs.size
        val phishingRatio = if (matchCount > 0) phishingCount.toFloat() / matchCount else 0f

        return RetrievalResult(phishingRatio, topK, matchCount)
    }

    fun isReady(): Boolean = isLoaded

    fun getDocCount(): Int = docCount + feedbackDocCount

    fun getFeedbackDocCount(): Int = feedbackDocCount

    fun clearFeedbackIndex() {
        // M-14: Synchronized to prevent race condition with addFeedbackDocument
        synchronized(feedbackLock) {
            feedbackIndex.clear()
            feedbackDocCount = 0
        }
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val chars = mutableListOf<Char>()

        for (ch in text) {
            when {
                ch in '\u4e00'..'\u9fff' -> chars.add(ch)
                ch.isLetterOrDigit() -> chars.add(ch.lowercaseChar())
                else -> {
                    if (chars.isNotEmpty()) {
                        addNgramTokens(chars, tokens)
                        chars.clear()
                    }
                }
            }
        }
        if (chars.isNotEmpty()) {
            addNgramTokens(chars, tokens)
        }

        return tokens
    }

    private fun addNgramTokens(chars: List<Char>, tokens: MutableList<String>) {
        val str = chars.joinToString("")
        if (str.length <= 2) {
            tokens.add(str)
        } else {
            for (i in 0 until str.length - 1) {
                tokens.add(str.substring(i, i + 2))
            }
            for (i in 0 until str.length - 2) {
                tokens.add(str.substring(i, i + 3))
            }
        }
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var zeroCount = 0
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
                // M-17: Safety valve to prevent infinite loop on malformed data
                if (++zeroCount > 100) break
                continue
            }
            zeroCount = 0
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }
}
