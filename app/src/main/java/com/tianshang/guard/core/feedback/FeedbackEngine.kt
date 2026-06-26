package com.tianshang.guard.core.feedback

import com.tianshang.guard.core.rl.FeatureExtractor
import com.tianshang.guard.core.rl.FeatureStore
import com.tianshang.guard.data.local.database.FeedbackDao
import com.tianshang.guard.data.local.database.FeedbackEntity
import com.tianshang.guard.data.local.database.FeedbackLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class FeedbackEngine(
    private val feedbackDao: FeedbackDao,
    private val featureExtractor: FeatureExtractor,
    private val featureStore: FeatureStore
) {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val maxFeedbackCount = 1000

    fun recordFeedback(text: String, modelScore: Float, label: FeedbackLabel, source: String) {
        ioScope.launch {
            val textHash = computeTextHash(text)
            val tokens = tokenize(text).joinToString(" ")
            val features = featureExtractor.extractFeatures(text)
            val feedback = FeedbackEntity(
                textHash = textHash,
                tokens = tokens,
                modelScore = modelScore,
                label = label,
                source = source,
                features = features.toJson()
            )
            feedbackDao.insert(feedback)
            enforceLimit()
        }
    }

    suspend fun isWhitelisted(text: String): Boolean {
        val textHash = computeTextHash(text)
        val feedback = feedbackDao.getByTextHash(textHash)
        return feedback?.label == FeedbackLabel.FALSE_POSITIVE
    }

    suspend fun queryFeedback(text: String, topK: Int = 10): FeedbackQueryResult {
        val recentFeedback = feedbackDao.getRecentFeedbackSync(maxFeedbackCount)
        if (recentFeedback.isEmpty()) return FeedbackQueryResult(0f, 0, 0)

        val tokens = tokenize(text).toSet()
        if (tokens.isEmpty()) return FeedbackQueryResult(0f, 0, 0)

        val scoredFeedback = recentFeedback.mapNotNull { fb ->
            val fbTokens = fb.tokens.split(" ").toSet()
            val overlap = tokens.intersect(fbTokens).size
            if (overlap > 0) Pair(fb, overlap) else null
        }.sortedByDescending { it.second }
            .take(topK)

        if (scoredFeedback.isEmpty()) return FeedbackQueryResult(0f, 0, 0)

        val phishingCount = scoredFeedback.count { it.first.label == FeedbackLabel.PHISHING }
        val matchCount = scoredFeedback.size
        val phishingRatio = phishingCount.toFloat() / matchCount

        return FeedbackQueryResult(phishingRatio, topK, matchCount)
    }

    suspend fun getStats(): FeedbackStats {
        val total = feedbackDao.getTotalCount()
        val phishing = feedbackDao.getCountByLabel(FeedbackLabel.PHISHING)
        val falsePositive = feedbackDao.getCountByLabel(FeedbackLabel.FALSE_POSITIVE)
        return FeedbackStats(total, phishing, falsePositive)
    }

    suspend fun clearAll() {
        feedbackDao.clearAll()
    }

    private suspend fun enforceLimit() {
        val total = feedbackDao.getTotalCount()
        if (total > maxFeedbackCount) {
            val excess = total - maxFeedbackCount
            val oldest = feedbackDao.getOldestFeedback(excess)
            oldest.forEach { fb ->
                feedbackDao.deleteById(fb.id)
            }
        }
    }

    private fun computeTextHash(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val CHINESE_RANGE = '\u4e00'..'\u9fff'

        fun tokenize(text: String): List<String> {
            val tokens = mutableListOf<String>()
            val chars = mutableListOf<Char>()

            for (ch in text) {
                when {
                    ch in CHINESE_RANGE -> {
                        chars.add(ch)
                    }
                    ch.isLetterOrDigit() -> {
                        chars.add(ch.lowercaseChar())
                    }
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
    }

    data class FeedbackQueryResult(
        val phishingRatio: Float,
        val topK: Int,
        val matchCount: Int
    )

    data class FeedbackStats(
        val total: Int,
        val phishing: Int,
        val falsePositive: Int
    )
}
