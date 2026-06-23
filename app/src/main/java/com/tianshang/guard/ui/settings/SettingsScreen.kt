package com.tianshang.guard.ui.settings

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
import androidx.compose.ui.unit.dp
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
    val batteryOptimized = remember { viewModel.isBatteryOptimizationIgnored(context) }
    val brandName = remember { viewModel.getPhoneBrand() }

    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    var listDomain by remember { mutableStateOf("") }

    if (showWhitelistDialog) {
        AlertDialog(
            onDismissRequest = { showWhitelistDialog = false; listDomain = "" },
            title = { Text("\u6DFB\u52A0\u767D\u540D\u5355") },
            text = {
                OutlinedTextField(
                    value = listDomain,
                    onValueChange = { listDomain = it },
                    label = { Text("\u57DF\u540D") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (listDomain.isNotBlank()) {
                        viewModel.addToWhitelist(listDomain.trim())
                        Toast.makeText(context, "\u5DF2\u6DFB\u52A0: ${listDomain.trim()}", Toast.LENGTH_SHORT).show()
                        showWhitelistDialog = false
                        listDomain = ""
                    }
                }) { Text("\u786E\u5B9A") }
            },
            dismissButton = {
                TextButton(onClick = { showWhitelistDialog = false; listDomain = "" }) { Text("\u53D6\u6D88") }
            }
        )
    }

    if (showBlacklistDialog) {
        AlertDialog(
            onDismissRequest = { showBlacklistDialog = false; listDomain = "" },
            title = { Text("\u6DFB\u52A0\u9ED1\u540D\u5355") },
            text = {
                OutlinedTextField(
                    value = listDomain,
                    onValueChange = { listDomain = it },
                    label = { Text("\u57DF\u540D") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (listDomain.isNotBlank()) {
                        viewModel.addToBlacklist(listDomain.trim())
                        Toast.makeText(context, "\u5DF2\u62E6\u622A: ${listDomain.trim()}", Toast.LENGTH_SHORT).show()
                        showBlacklistDialog = false
                        listDomain = ""
                    }
                }) { Text("\u786E\u5B9A") }
            },
            dismissButton = {
                TextButton(onClick = { showBlacklistDialog = false; listDomain = "" }) { Text("\u53D6\u6D88") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepNavy).padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("\u8BBE\u7F6E", style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(20.dp))
        SettingsSection("\u4FDD\u62A4") {
            SettingsToggle("VPN \u81EA\u542F", "\u5F00\u673A\u81EA\u52A8\u542F\u52A8 VPN \u4FDD\u62A4", checked = vpnAutoStart, onCheckedChange = { viewModel.setVpnAutoStart(it) })
            SettingsToggle("\u884C\u4E3A\u76D1\u63A7", "\u68C0\u6D4B\u5C4F\u5E55\u5171\u4EAB + \u94F6\u884C\u5E94\u7528\u7EC4\u5408", checked = behaviorMonitor, onCheckedChange = { viewModel.setBehaviorMonitor(it) })
            SettingsToggle("\u77ED\u4FE1\u76D1\u63A7", "\u5B9E\u65F6\u62E6\u622A\u5E76\u5206\u6790\u53EF\u7591\u77ED\u4FE1", checked = smsMonitor, onCheckedChange = { viewModel.setSmsMonitor(it) })
            SettingsToggle("\u5F00\u673A\u542F\u52A8", "\u7CFB\u7EDF\u542F\u52A8\u65F6\u81EA\u52A8\u8FD0\u884C", checked = bootStart, onCheckedChange = { viewModel.setBootStart(it) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection("\u7535\u6C60\u4F18\u5316\uFF08${brandName}\uFF09") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(if (batteryOptimized) androidx.compose.ui.graphics.Color(0xFF4CAF50) else GuardRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (batteryOptimized) "\u7535\u6C60\u4F18\u5316\u5DF2\u5173\u95ED" else "\u7535\u6C60\u4F18\u5316\u672A\u5173\u95ED",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDark
                )
            }
            SettingsClickable("\u8BBE\u7F6E\u7535\u6C60\u4F18\u5316", "\u5141\u8BB8\u540E\u53F0\u8FD0\u884C\uFF0C\u907F\u514D\u88AB\u7CFB\u7EDF\u6740\u6B7B", onClick = { viewModel.openBatterySettings(context) })
            SettingsClickable("\u8BBE\u7F6E\u81EA\u542F\u52A8", "\u52A0\u5165\u81EA\u542F\u52A8\u767D\u540D\u5355\uFF0C\u907F\u514D\u542F\u52A8\u88AB\u62E6\u622A", onClick = { viewModel.openAutoStartSettings(context) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection("\u5217\u8868\u7BA1\u7406") {
            SettingsClickable("\u767D\u540D\u5355\u7BA1\u7406", "\u7BA1\u7406\u4FE1\u4EFB\u57DF\u540D", onClick = { showWhitelistDialog = true })
            SettingsClickable("\u9ED1\u540D\u5355\u7BA1\u7406", "\u7BA1\u7406\u62E6\u622A\u57DF\u540D", onClick = { showBlacklistDialog = true })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection("\u9884\u8B66") {
            SettingsToggle("\u58F0\u97F3\u63D0\u9192", "\u9AD8\u5371\u9884\u8B66\u65F6\u64AD\u653E\u63D0\u793A\u97F3", checked = soundAlert, onCheckedChange = { viewModel.setSoundAlert(it) })
            SettingsToggle("\u9707\u52A8\u63D0\u9192", "\u9AD8\u5371\u9884\u8B66\u65F6\u9707\u52A8", checked = vibrateAlert, onCheckedChange = { viewModel.setVibrateAlert(it) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection("\u6570\u636E") {
            SettingsClickable("\u5BFC\u51FA\u65E5\u5FD7", "\u5BFC\u51FA\u62E6\u622A\u8BB0\u5F55\u5230\u6587\u4EF6", onClick = { viewModel.exportLogs(context) })
            SettingsClickable("\u6E05\u9664\u6570\u636E", "\u6E05\u9664\u672C\u5730\u7F13\u5B58\u548C\u65E5\u5FD7", onClick = {
                viewModel.clearData()
                Toast.makeText(context, "\u6570\u636E\u5DF2\u6E05\u9664", Toast.LENGTH_SHORT).show()
            })
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection("\u5173\u4E8E") {
            SettingsInfo("\u7248\u672C", "v1.0.0")
            SettingsInfo("\u5F00\u6E90\u8BB8\u53EF", "MIT")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            viewModel.checkRuleUpdates()
            Toast.makeText(context, "\u89C4\u5219\u5DF2\u66F4\u65B0", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
            Text("\u68C0\u67E5\u89C4\u5219\u66F4\u65B0")
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
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
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


