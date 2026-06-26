package com.tianshang.guard.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DomainCategory {
    WHITELIST,
    BLACKLIST,
    SUSPICIOUS,
    UNKNOWN
}

@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey val domain: String,
    val category: DomainCategory,
    val source: String,
    val addedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f
)

enum class AlertType {
    SCREEN_SHARE,
    PHISHING_PAGE,
    SUSPICIOUS_DOMAIN,
    BLACKLIST_BLOCKED,
    VISITED,
    SMS_PHISHING
}

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: AlertType,
    val domain: String?,
    val url: String?,
    val riskLevel: String?,
    val userAction: String?
)

enum class FeedbackLabel {
    PHISHING,
    FALSE_POSITIVE
}

@Entity(tableName = "user_feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val textHash: String,
    val tokens: String,
    val modelScore: Float,
    val label: FeedbackLabel,
    val source: String,
    val features: String? = null
)
