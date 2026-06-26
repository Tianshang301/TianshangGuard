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
    val language: Language = Language.CHINESE
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
            textLength.toFloat() / 500f,  // Normalize to [0,1]
            urlCount.toFloat() / 5f,
            if (hasPhoneNumber) 1f else 0f,
            if (hasFinancialKeywords) 1f else 0f,
            if (hasUrgencyKeywords) 1f else 0f,
            if (hasThreatKeywords) 1f else 0f,
            senderType.ordinal.toFloat() / 3f,
            language.ordinal.toFloat()
        )
    }

    fun toJson(): String {
        return gson.toJson(this)
    }

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): FeatureVector? {
            return try {
                gson.fromJson(json, FeatureVector::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
