package com.tianshang.guard.domain

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.dns.BlockReason
import com.tianshang.guard.core.ml.RiskLevel

class TriggerAlertUseCase(
    private val alertEngine: AlertEngine
) {
    fun showScreenShareWarning() {
        alertEngine.showScreenShareWarning()
    }

    fun showPhishingWarning(url: String, riskLevel: RiskLevel) {
        alertEngine.showPhishingWarning(url, riskLevel)
    }

    fun showSuspiciousDomainWarning(domain: String, score: Float) {
        alertEngine.showSuspiciousDomainWarning(domain, score)
    }

    fun notifyBlocked(domain: String, reason: BlockReason) {
        alertEngine.notifyBlocked(domain, reason)
    }
}
