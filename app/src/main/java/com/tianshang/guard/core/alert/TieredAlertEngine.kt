package com.tianshang.guard.core.alert

import android.content.Context
import android.content.Intent
import com.tianshang.guard.core.dns.BlockReason
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.repository.AlertRepository
import com.tianshang.guard.ui.alert.AlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TieredAlertEngine(
    private val context: Context,
    private val prefs: GuardPreferences,
    private val alertRepository: AlertRepository
) : AlertEngine {

    private val cooldownManager = CooldownManager(prefs)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private fun record(type: AlertType, domain: String? = null, url: String? = null, riskLevel: String? = null) {
        android.util.Log.i("TieredAlert", "record: type=$type domain=$domain")
        ioScope.launch {
            try {
                alertRepository.insert(AlertEntity(type = type, domain = domain, url = url, riskLevel = riskLevel, userAction = null))
                android.util.Log.i("TieredAlert", "insert OK: type=$type")
            } catch (e: Exception) {
                android.util.Log.e("TieredAlert", "insert FAILED", e)
            }
        }
    }

    override fun showScreenShareWarning() {
        record(AlertType.SCREEN_SHARE)
        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("alert_type", AlertType.SCREEN_SHARE.name)
            putExtra("level", AlertLevel.FULLSCREEN.name)
            putExtra("require_confirm", true)
        }
        context.startActivity(intent)
    }

    override fun showPhishingWarning(url: String, riskLevel: RiskLevel) {
        record(AlertType.PHISHING_PAGE, url = url, riskLevel = riskLevel.name)
        val config = resolveAlertConfig(riskLevel)
        if (cooldownManager.isInCooldown("phishing_$url", config.cooldownSeconds)) return

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.PHISHING_PAGE.name)
            putExtra("url", url)
            putExtra("risk_level", riskLevel.name)
            putExtra("level", config.level.name)
            putExtra("require_confirm", config.requireConfirm)
        }
        context.startActivity(intent)
        cooldownManager.recordTrigger("phishing_$url")
    }

    override fun showSuspiciousDomainWarning(domain: String, score: Float) {
        record(AlertType.SUSPICIOUS_DOMAIN, domain = domain)
        val config = resolveAlertConfig(RiskLevel.SUSPICIOUS)
        if (cooldownManager.isInCooldown("domain_$domain", config.cooldownSeconds)) return

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.SUSPICIOUS_DOMAIN.name)
            putExtra("domain", domain)
            putExtra("score", score)
            putExtra("level", config.level.name)
            putExtra("require_confirm", config.requireConfirm)
        }
        context.startActivity(intent)
        cooldownManager.recordTrigger("domain_$domain")
    }

    override fun notifyBlocked(domain: String, reason: BlockReason) {
        record(AlertType.BLACKLIST_BLOCKED, domain = domain)
        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.BLACKLIST_BLOCKED.name)
            putExtra("domain", domain)
            putExtra("reason", reason.name)
            putExtra("level", AlertLevel.BANNER.name)
        }
        context.startActivity(intent)
    }

    override fun notifyVisited(domain: String) {
        record(AlertType.VISITED, domain = domain)
    }

    override fun showSmsWarning(sender: String, body: String, riskLevel: RiskLevel) {
        record(AlertType.SMS_PHISHING, domain = sender, riskLevel = riskLevel.name)
        val config = resolveAlertConfig(riskLevel)
        if (cooldownManager.isInCooldown("sms_$sender", config.cooldownSeconds)) return

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.SMS_PHISHING.name)
            putExtra("sms_sender", sender)
            putExtra("sms_body", body)
            putExtra("risk_level", riskLevel.name)
            putExtra("level", config.level.name)
            putExtra("require_confirm", config.requireConfirm)
        }
        context.startActivity(intent)
        cooldownManager.recordTrigger("sms_$sender")
    }

    private fun resolveAlertConfig(riskLevel: RiskLevel): AlertConfig {
        return when (riskLevel) {
            RiskLevel.SAFE -> AlertConfig(
                level = AlertLevel.SILENT,
                requireConfirm = false,
                cooldownSeconds = 0,
                showEducationalTip = false,
                playSound = false,
                vibrate = false
            )
            RiskLevel.SUSPICIOUS -> AlertConfig(
                level = AlertLevel.BANNER,
                requireConfirm = false,
                cooldownSeconds = prefs.suspiciousCooldownSeconds,
                showEducationalTip = true,
                playSound = false,
                vibrate = false
            )
            RiskLevel.DANGEROUS -> AlertConfig(
                level = AlertLevel.DIALOG,
                requireConfirm = true,
                cooldownSeconds = 300,
                showEducationalTip = true,
                playSound = true,
                vibrate = true
            )
        }
    }
}
