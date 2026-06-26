package com.tianshang.guard.core.feedback

import com.tianshang.guard.data.local.database.FeedbackDao
import com.tianshang.guard.data.local.database.FeedbackEntity
import com.tianshang.guard.data.local.database.FeedbackLabel
import com.tianshang.guard.data.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedbackEngine(
    private val feedbackDao: FeedbackDao,
    private val ruleRepository: RuleRepository
) {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val maxFeedbackCount = 500

    fun recordFeedback(text: String, modelScore: Float, label: FeedbackLabel, source: String) {
        ioScope.launch {
            val textHash = computeTextHash(text)
            val feedback = FeedbackEntity(
                textHash = textHash,
                modelScore = modelScore,
                label = label,
                source = source
            )
            feedbackDao.insert(feedback)
            enforceLimit()

            // Auto-update rules based on feedback
            autoUpdateRules(text, label, source)
        }
    }

    private suspend fun autoUpdateRules(text: String, label: FeedbackLabel, source: String) {
        when (label) {
            FeedbackLabel.PHISHING -> {
                // Extract sender/URL/domain and add to blacklist
                val extracted = extractIdentifier(text, source)
                if (extracted != null) {
                    ruleRepository.addToBlacklist(extracted)
                    android.util.Log.i("FeedbackEngine", "Auto-blacklisted: $extracted")
                }
            }
            FeedbackLabel.FALSE_POSITIVE -> {
                // Extract sender/URL/domain and add to whitelist
                val extracted = extractIdentifier(text, source)
                if (extracted != null) {
                    ruleRepository.addToWhitelist(extracted)
                    android.util.Log.i("FeedbackEngine", "Auto-whitelisted: $extracted")
                }
            }
        }
    }

    private fun extractIdentifier(text: String, source: String): String? {
        return when (source) {
            "sms" -> {
                // Extract phone number or sender ID from SMS
                val phonePattern = Regex("^1[3-9]\\d{9}")
                val senderPattern = Regex("^[A-Za-z0-9]{3,11}")
                val firstLine = text.lines().firstOrNull()?.trim() ?: return null
                when {
                    phonePattern.containsMatchIn(firstLine) -> phonePattern.find(firstLine)?.value
                    senderPattern.containsMatchIn(firstLine) -> senderPattern.find(firstLine)?.value
                    else -> null
                }
            }
            "webpage" -> {
                // Extract domain from URL
                val urlPattern = Regex("https?://([^/\\s]+)")
                val match = urlPattern.find(text)
                match?.groupValues?.get(1)?.lowercase()
            }
            "domain" -> {
                // Direct domain
                text.trim().lowercase().takeIf { it.isNotBlank() && it.contains('.') }
            }
            else -> null
        }
    }

    suspend fun queryFeedback(text: String, topK: Int = 10): FeedbackQueryResult {
        val recentFeedback = feedbackDao.getRecentFeedbackSync(maxFeedbackCount)
        if (recentFeedback.isEmpty()) return FeedbackQueryResult(0f, 0, 0)

        val tokens = tokenize(text)
        val scoredFeedback = recentFeedback.map { fb ->
            val fbTokens = tokenize(fb.textHash)
            val overlap = tokens.intersect(fbTokens.toSet()).size
            Pair(fb, overlap)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
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
            // Delete oldest entries beyond limit
            val excess = total - maxFeedbackCount
            val oldest = feedbackDao.getRecentFeedbackSync(total).takeLast(excess)
            oldest.forEach { fb ->
                feedbackDao.deleteById(fb.id)
            }
        }
    }

    private fun computeTextHash(text: String): String {
        val tokens = tokenize(text)
        return tokens.take(50).joinToString("")
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        for (ch in text) {
            when {
                ch in '\u4e00'..'\u9fff' -> tokens.add(ch.toString())
                ch.isLetterOrDigit() -> tokens.add(ch.lowercaseChar().toString())
            }
        }
        return tokens
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
