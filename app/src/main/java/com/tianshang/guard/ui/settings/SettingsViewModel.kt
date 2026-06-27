package com.tianshang.guard.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tianshang.guard.R
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.monitor.ScreenShareMonitor
import com.tianshang.guard.core.optimizer.BatteryOptimizer
import com.tianshang.guard.core.feedback.FeedbackEngine
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.repository.AlertRepository
import com.tianshang.guard.data.repository.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingsViewModel(
    private val prefs: GuardPreferences,
    private val dnsEngine: DnsEngine,
    private val alertRepository: AlertRepository,
    private val ruleRepository: RuleRepository,
    private val monitor: ScreenShareMonitor,
    private val feedbackEngine: FeedbackEngine
) : ViewModel() {

    val vpnAutoStart: StateFlow<Boolean> = prefs.vpnAutoStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val behaviorMonitor: StateFlow<Boolean> = prefs.behaviorMonitor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val bootStart: StateFlow<Boolean> = prefs.bootStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val soundAlert: StateFlow<Boolean> = prefs.soundAlert.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val vibrateAlert: StateFlow<Boolean> = prefs.vibrateAlert.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val smsMonitor: StateFlow<Boolean> = prefs.smsMonitor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setVpnAutoStart(enabled: Boolean) = viewModelScope.launch { prefs.setVpnAutoStart(enabled) }
    fun setBehaviorMonitor(enabled: Boolean) = viewModelScope.launch {
        prefs.setBehaviorMonitor(enabled)
        if (enabled) monitor.startMonitoring() else monitor.stopMonitoring()
    }
    fun setBootStart(enabled: Boolean) = viewModelScope.launch { prefs.setBootStart(enabled) }
    fun setSoundAlert(enabled: Boolean) = viewModelScope.launch { prefs.setSoundAlert(enabled) }
    fun setVibrateAlert(enabled: Boolean) = viewModelScope.launch { prefs.setVibrateAlert(enabled) }
    fun setSmsMonitor(enabled: Boolean) = viewModelScope.launch { prefs.setSmsMonitor(enabled) }

    fun addToWhitelist(domain: String) = viewModelScope.launch {
        dnsEngine.addToWhitelist(domain)
    }

    fun addToBlacklist(domain: String) = viewModelScope.launch {
        dnsEngine.addToBlacklist(domain)
    }

    fun exportLogs(context: Context) = viewModelScope.launch {
        val alerts = alertRepository.getAlertsAscSync(1000)
        val content = buildString {
            appendLine(context.getString(R.string.export_log_header))
            appendLine("${context.getString(R.string.export_log_timestamp)} ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("---")
            for (alert in alerts) {
                appendLine("[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(alert.timestamp))}] ${alert.type} | domain=${alert.domain ?: ""} url=${alert.url ?: ""}")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_chooser_title)))
    }

    fun clearData() = viewModelScope.launch {
        alertRepository.clearAll()
        ruleRepository.clearAll()
        feedbackEngine.clearAll() // BUGFIX: Also clear feedback data
    }

    fun checkRuleUpdates() = viewModelScope.launch {
        // BUGFIX: Run DNS engine start on IO dispatcher to avoid blocking main thread
        withContext(Dispatchers.IO) { dnsEngine.start() }
    }

    fun openBatterySettings(context: Context) {
        BatteryOptimizer.openBatterySettings(context)
    }

    fun openAutoStartSettings(context: Context) {
        BatteryOptimizer.openAutoStartSettings(context)
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return BatteryOptimizer.isIgnoringBatteryOptimizations(context)
    }

    fun getPhoneBrand(): String = BatteryOptimizer.getBrandName()
}
