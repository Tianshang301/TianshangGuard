package com.tianshang.guard.core.alert

import com.tianshang.guard.core.dns.BlockReason
import com.tianshang.guard.core.ml.RiskLevel

enum class AlertLevel {
    SILENT,
    BANNER,
    DIALOG,
    FULLSCREEN
}

data class AlertConfig(
    val level: AlertLevel,
    val requireConfirm: Boolean,
    val cooldownSeconds: Int,
    val showEducationalTip: Boolean,
    val playSound: Boolean,
    val vibrate: Boolean
)

interface AlertEngine {
    fun showScreenShareWarning()
    fun showPhishingWarning(url: String, riskLevel: RiskLevel)
    fun showSuspiciousDomainWarning(domain: String, score: Float)
    fun notifyBlocked(domain: String, reason: BlockReason)
    fun notifyVisited(domain: String)
    fun showSmsWarning(sender: String, body: String, riskLevel: RiskLevel)
}
