package com.tianshang.guard.core.alert

import android.content.Context
import android.content.Intent
import com.tianshang.guard.core.dns.BlockReason
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.core.util.SecureLog
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
    
    // Global rate limiter: max 1 alert per 5 seconds.
    // M-04: DANGEROUS alerts are exempt from this global limiter so an attacker
    // cannot flood the window with low/medium phishing to suppress the critical
    // one; the per-key cooldown still applies to prevent unbounded repetition.
    @Volatile private var lastGlobalAlertTime = 0L
    private val globalCooldownMs = 5000L
    private val globalAlertLock = Any()

    private fun canLaunchAlert(riskLevel: RiskLevel): Boolean {
        if (riskLevel == RiskLevel.DANGEROUS) return true
        // H-10: Synchronized to prevent duplicate alerts
        synchronized(globalAlertLock) {
            val now = System.currentTimeMillis()
            if (now - lastGlobalAlertTime < globalCooldownMs) return false
            lastGlobalAlertTime = now
            return true
        }
    }

    private fun record(type: AlertType, domain: String? = null, url: String? = null, riskLevel: String? = null) {
        SecureLog.i("TieredAlert", "record: type=$type")
        ioScope.launch {
            try {
                alertRepository.insert(AlertEntity(type = type, domain = domain, url = url, riskLevel = riskLevel, userAction = null))
            } catch (e: Exception) {
                SecureLog.e("TieredAlert", "insert FAILED", e)
            }
        }
    }

    private fun launchAlert(alertKey: String) {
        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_key", alertKey)
        }
        context.startActivity(intent)
    }

    override fun showScreenShareWarning() {
        record(AlertType.SCREEN_SHARE)
        val key = "screen_share_${System.currentTimeMillis()}"
        AlertDataHolder.put(key, AlertDataHolder.AlertData(
            alertType = AlertType.SCREEN_SHARE.name,
            level = AlertLevel.FULLSCREEN.name,
            requireConfirm = true
        ))
        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("alert_key", key)
        }
        context.startActivity(intent)
    }

    override fun showPhishingWarning(url: String, riskLevel: RiskLevel) {
        record(AlertType.PHISHING_PAGE, url = url, riskLevel = riskLevel.name)
        val config = resolveAlertConfig(riskLevel)
        if (cooldownManager.isInCooldown("phishing_$url", config.cooldownSeconds)) return
        if (!canLaunchAlert(riskLevel)) return // Global rate limit

        val key = "phishing_${System.currentTimeMillis()}"
        AlertDataHolder.put(key, AlertDataHolder.AlertData(
            alertType = AlertType.PHISHING_PAGE.name,
            url = url,
            riskLevel = riskLevel.name,
            level = config.level.name,
            requireConfirm = config.requireConfirm
        ))
        launchAlert(key)
        cooldownManager.recordTrigger("phishing_$url")
    }

    override fun showSuspiciousDomainWarning(domain: String, score: Float) {
        record(AlertType.SUSPICIOUS_DOMAIN, domain = domain)
        val config = resolveAlertConfig(RiskLevel.SUSPICIOUS)
        if (cooldownManager.isInCooldown("domain_$domain", config.cooldownSeconds)) return
        if (!canLaunchAlert(RiskLevel.SUSPICIOUS)) return // Global rate limit

        val key = "domain_${System.currentTimeMillis()}"
        AlertDataHolder.put(key, AlertDataHolder.AlertData(
            alertType = AlertType.SUSPICIOUS_DOMAIN.name,
            domain = domain,
            level = config.level.name,
            requireConfirm = config.requireConfirm
        ))
        launchAlert(key)
        cooldownManager.recordTrigger("domain_$domain")
    }

    override fun notifyBlocked(domain: String, reason: BlockReason) {
        record(AlertType.BLACKLIST_BLOCKED, domain = domain)
        val key = "blocked_${System.currentTimeMillis()}"
        AlertDataHolder.put(key, AlertDataHolder.AlertData(
            alertType = AlertType.BLACKLIST_BLOCKED.name,
            domain = domain,
            level = AlertLevel.BANNER.name
        ))
        launchAlert(key)
    }

    override fun notifyVisited(domain: String) {
        // L-02: respect the user's visit-history retention switch. When disabled,
        // visited domains are not persisted (no browsing history is stored).
        if (!prefs.isVisitHistoryEnabled()) return
        record(AlertType.VISITED, domain = domain)
        periodicallyPurgeOldVisited()
    }

    // L-02: bound retention of VISITED records to 30 days, purging older entries
    // opportunistically (rate-limited so it is not a hot-path DB write).
    @Volatile private var lastVisitedPurge = 0L
    private fun periodicallyPurgeOldVisited() {
        val now = System.currentTimeMillis()
        if (now - lastVisitedPurge < 600_000L) return
        lastVisitedPurge = now
        ioScope.launch {
            try {
                alertRepository.purgeOlderThan(now - VISITED_RETENTION_MS)
            } catch (e: Exception) {
                SecureLog.e("TieredAlert", "purge old VISITED failed", e)
            }
        }
    }

    private companion object {
        private const val VISITED_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }

    override fun showSmsWarning(sender: String, body: String, riskLevel: RiskLevel) {
        showSmsWarning(sender, body, riskLevel, useCooldown = true)
    }

    fun showSmsWarning(sender: String, body: String, riskLevel: RiskLevel, useCooldown: Boolean) {
        record(AlertType.SMS_PHISHING, domain = sender, riskLevel = riskLevel.name)
        val config = resolveAlertConfig(riskLevel)

        // M-04: use a SHA-256 of the (trimmed) body for the cooldown key so a
        // crafted different body cannot collide on the same key and bypass cooldown.
        val bodyKey = sha256(body.trim())
        val cooldownKey = "sms_${sender}_$bodyKey"

        if (useCooldown && cooldownManager.isInCooldown(cooldownKey, config.cooldownSeconds)) return
        if (!canLaunchAlert(riskLevel)) return // Global rate limit

        val key = "sms_${System.currentTimeMillis()}"
        AlertDataHolder.put(key, AlertDataHolder.AlertData(
            alertType = AlertType.SMS_PHISHING.name,
            smsSender = sender,
            smsBody = body,
            riskLevel = riskLevel.name,
            level = config.level.name,
            requireConfirm = config.requireConfirm
        ))
        launchAlert(key)
        if (useCooldown) cooldownManager.recordTrigger(cooldownKey)
    }

    private fun sha256(text: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Should never happen; fall back to identity-based key.
            text
        }
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
