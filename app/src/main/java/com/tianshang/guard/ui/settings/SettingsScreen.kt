package com.tianshang.guard.ui.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tianshang.guard.R
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = koinViewModel()
    val context = LocalContext.current
    val vpnAutoStart by viewModel.vpnAutoStart.collectAsState(initial = true)
    val behaviorMonitor by viewModel.behaviorMonitor.collectAsState(initial = true)
    val bootStart by viewModel.bootStart.collectAsState(initial = true)
    val soundAlert by viewModel.soundAlert.collectAsState(initial = true)
    val vibrateAlert by viewModel.vibrateAlert.collectAsState(initial = true)
    val smsMonitor by viewModel.smsMonitor.collectAsState(initial = false)
    val language by viewModel.language.collectAsState(initial = "system")
    val batteryOptimized = remember { viewModel.isBatteryOptimizationIgnored(context) }
    val brandName = remember { viewModel.getPhoneBrand() }

    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var listDomain by remember { mutableStateOf("") }

    // Language dialog
    if (showLanguageDialog) {
        val languageOptions = listOf(
            "system" to stringResource(R.string.settings_language_system),
            "zh" to stringResource(R.string.settings_language_zh),
            "en" to stringResource(R.string.settings_language_en)
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    languageOptions.forEach { (code, label) ->
                        val activity = context as? android.app.Activity
                        val onSelect = {
                            viewModel.setLanguage(code) {
                                activity?.recreate()
                            }
                            showLanguageDialog = false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onSelect)
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = language == code,
                                onClick = onSelect
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    if (showWhitelistDialog) {
        AlertDialog(
            onDismissRequest = { showWhitelistDialog = false; listDomain = "" },
            title = { Text(stringResource(R.string.settings_add_whitelist)) },
            text = {
                OutlinedTextField(
                    value = listDomain,
                    onValueChange = { listDomain = it },
                    label = { Text(stringResource(R.string.label_domain)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (listDomain.isNotBlank()) {
                        viewModel.addToWhitelist(listDomain.trim())
                        Toast.makeText(context, context.getString(R.string.toast_added_domain, listDomain.trim()), Toast.LENGTH_SHORT).show()
                        showWhitelistDialog = false
                        listDomain = ""
                    }
                }) { Text(stringResource(R.string.button_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showWhitelistDialog = false; listDomain = "" }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }

    if (showBlacklistDialog) {
        AlertDialog(
            onDismissRequest = { showBlacklistDialog = false; listDomain = "" },
            title = { Text(stringResource(R.string.settings_add_blacklist)) },
            text = {
                OutlinedTextField(
                    value = listDomain,
                    onValueChange = { listDomain = it },
                    label = { Text(stringResource(R.string.label_domain)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (listDomain.isNotBlank()) {
                        viewModel.addToBlacklist(listDomain.trim())
                        Toast.makeText(context, context.getString(R.string.toast_blocked_domain, listDomain.trim()), Toast.LENGTH_SHORT).show()
                        showBlacklistDialog = false
                        listDomain = ""
                    }
                }) { Text(stringResource(R.string.button_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showBlacklistDialog = false; listDomain = "" }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepNavy).padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.settings_screen_title), style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(20.dp))
        // Language section - only show in unified flavor
        val isUnified = context.packageName == "com.tianshang.guard"
        if (isUnified) {
            SettingsSection(stringResource(R.string.settings_section_general)) {
                val currentLanguageLabel = when (language) {
                    "zh" -> stringResource(R.string.settings_language_zh)
                    "en" -> stringResource(R.string.settings_language_en)
                    else -> stringResource(R.string.settings_language_system)
                }
                SettingsClickable(stringResource(R.string.settings_language), currentLanguageLabel, onClick = { showLanguageDialog = true })
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        SettingsSection(stringResource(R.string.settings_section_protection)) {
            SettingsToggle(stringResource(R.string.settings_vpn_auto_start), stringResource(R.string.settings_vpn_auto_start_desc), checked = vpnAutoStart, onCheckedChange = { viewModel.setVpnAutoStart(it) })
            SettingsToggle(stringResource(R.string.settings_behavior_monitor), stringResource(R.string.settings_behavior_monitor_desc), checked = behaviorMonitor, onCheckedChange = { viewModel.setBehaviorMonitor(it) })
            SettingsToggle(stringResource(R.string.settings_sms_monitor), stringResource(R.string.settings_sms_monitor_desc), checked = smsMonitor, onCheckedChange = { viewModel.setSmsMonitor(it) })
            SettingsToggle(stringResource(R.string.settings_boot_start), stringResource(R.string.settings_boot_start_desc), checked = bootStart, onCheckedChange = { viewModel.setBootStart(it) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection(stringResource(R.string.settings_battery_optimization_section, brandName)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(if (batteryOptimized) androidx.compose.ui.graphics.Color(0xFF4CAF50) else GuardRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (batteryOptimized) stringResource(R.string.settings_battery_optimized) else stringResource(R.string.settings_battery_not_optimized),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDark
                )
            }
            SettingsClickable(stringResource(R.string.settings_battery_optimization), stringResource(R.string.settings_battery_optimization_desc), onClick = { viewModel.openBatterySettings(context) })
            SettingsClickable(stringResource(R.string.settings_auto_start), stringResource(R.string.settings_auto_start_desc), onClick = { viewModel.openAutoStartSettings(context) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection(stringResource(R.string.settings_section_list_management)) {
            SettingsClickable(stringResource(R.string.settings_whitelist_management), stringResource(R.string.settings_whitelist_management_desc), onClick = { showWhitelistDialog = true })
            SettingsClickable(stringResource(R.string.settings_blacklist_management), stringResource(R.string.settings_blacklist_management_desc), onClick = { showBlacklistDialog = true })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection(stringResource(R.string.settings_section_alerts)) {
            SettingsToggle(stringResource(R.string.settings_sound_alert), stringResource(R.string.settings_sound_alert_desc), checked = soundAlert, onCheckedChange = { viewModel.setSoundAlert(it) })
            SettingsToggle(stringResource(R.string.settings_vibrate_alert), stringResource(R.string.settings_vibrate_alert_desc), checked = vibrateAlert, onCheckedChange = { viewModel.setVibrateAlert(it) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection(stringResource(R.string.settings_section_data)) {
            SettingsClickable(stringResource(R.string.settings_export_logs), stringResource(R.string.settings_export_logs_desc), onClick = { viewModel.exportLogs(context) })
            SettingsClickable(stringResource(R.string.settings_clear_data), stringResource(R.string.settings_clear_data_desc), onClick = {
                viewModel.clearData()
                Toast.makeText(context, context.getString(R.string.toast_data_cleared), Toast.LENGTH_SHORT).show()
            })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection(stringResource(R.string.settings_section_about)) {
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Exception) { "1.0.0" }
            SettingsInfo(stringResource(R.string.settings_version), "v$versionName")
            SettingsInfo(stringResource(R.string.settings_license), "MIT")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            viewModel.checkRuleUpdates()
            Toast.makeText(context, context.getString(R.string.toast_rules_updated), Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
            Text(stringResource(R.string.settings_check_rule_updates))
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark, modifier = Modifier.padding(bottom = 8.dp))
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(modifier = Modifier.padding(4.dp)) { content() }
    }
}

@Composable
fun SettingsToggle(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // BUGFIX: Remove Row clickable to prevent double-trigger with Switch
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).clickable { onCheckedChange(!checked) }) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDark)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = GuardRed, checkedTrackColor = SurfaceVariantDark))
    }
}

@Composable
fun SettingsClickable(label: String, description: String, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDark)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        }
        Text(">", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariantDark)
    }
}

@Composable
fun SettingsInfo(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceDark, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
    }
}


