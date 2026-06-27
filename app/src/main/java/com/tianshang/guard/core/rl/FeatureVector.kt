package com.tianshang.guard.core.rl

import com.google.gson.Gson

data class FeatureVector(
    val textLength: Int = 0,
    val urlCount: Int = 0,
    val hasPhoneNumber: Boolean = false,
    val hasFinancialKeywords: Boolean = false,
    val hasUrgencyKeywords: Boolean = false,
    val hasThreatKeywords: Boolean = false,
    val senderType: SenderType = SenderType.UNKNOWN,
    val language: Language = Language.CHINESE,
    // New features (v1.3.x)
    val specialCharRatio: Float = 0f,
    val digitRatio: Float = 0f,
    val avgWordLength: Float = 0f,
    val exclamationCount: Int = 0,
    val questionCount: Int = 0,
    val uppercaseRatio: Float = 0f,
    val repeatedCharCount: Int = 0,
    val linkCount: Int = 0,
    val ipAddressCount: Int = 0,
    val chineseCharRatio: Float = 0f,
    val emojiCount: Int = 0,
    val moneySymbolCount: Int = 0,
    val urgencyWordCount: Int = 0,
    val threatWordCount: Int = 0,
    val financialWordCount: Int = 0,
    val sentimentScore: Float = 0f
) {
    enum class SenderType {
        PHONE_NUMBER,
        SHORT_CODE,
        ALPHABETIC,
        UNKNOWN
    }

    enum class Language {
        CHINESE,
        ENGLISH
    }

    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            // Original features (8)
            (textLength.toFloat() / 500f).coerceAtMost(1f),
            (urlCount.toFloat() / 5f).coerceAtMost(1f),
            if (hasPhoneNumber) 1f else 0f,
            if (hasFinancialKeywords) 1f else 0f,
            if (hasUrgencyKeywords) 1f else 0f,
            if (hasThreatKeywords) 1f else 0f,
            senderType.ordinal.toFloat() / 3f,
            language.ordinal.toFloat() / (Language.entries.size - 1).coerceAtLeast(1).toFloat(),
            
            // New features (16) - all normalized to [0, 1]
            specialCharRatio.coerceIn(0f, 1f),
            digitRatio.coerceIn(0f, 1f),
            (avgWordLength / 20f).coerceAtMost(1f),  // Assume max 20 chars per word
            (exclamationCount.toFloat() / 10f).coerceAtMost(1f),  // Max 10 exclamation marks
            (questionCount.toFloat() / 10f).coerceAtMost(1f),  // Max 10 question marks
            uppercaseRatio.coerceIn(0f, 1f),
            (repeatedCharCount.toFloat() / 20f).coerceAtMost(1f),  // Max 20 repeated chars
            (linkCount.toFloat() / 5f).coerceAtMost(1f),  // Max 5 links
            (ipAddressCount.toFloat() / 3f).coerceAtMost(1f),  // Max 3 IP addresses
            chineseCharRatio.coerceIn(0f, 1f),
            (emojiCount.toFloat() / 10f).coerceAtMost(1f),  // Max 10 emojis
            (moneySymbolCount.toFloat() / 5f).coerceAtMost(1f),  // Max 5 money symbols
            (urgencyWordCount.toFloat() / 10f).coerceAtMost(1f),  // Max 10 urgency words
            (threatWordCount.toFloat() / 10f).coerceAtMost(1f),  // Max 10 threat words
            (financialWordCount.toFloat() / 10f).coerceAtMost(1f),  // Max 10 financial words
            sentimentScore.coerceIn(0f, 1f)  // Already normalized
        )
    }

    companion object {
        private val gson = Gson()
        
        // Dimension version for backward compatibility
        const val CURRENT_VERSION = 2
        const val LEGACY_DIMENSIONS = 8
        const val CURRENT_DIMENSIONS = 24

        fun fromJson(json: String): FeatureVector? {
            return try {
                gson.fromJson(json, FeatureVector::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }
}
