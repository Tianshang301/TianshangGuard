package com.tianshang.guard.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardOrange
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.ShieldBlue
import com.tianshang.guard.ui.theme.SurfaceDark
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatsScreen() {
    val viewModel: StatsViewModel = koinViewModel()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val behaviorCount by viewModel.behaviorCount.collectAsState()
    val visitedCount by viewModel.visitedCount.collectAsState()
    val smsCount by viewModel.smsCount.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)
    ) {
        Text("\u7EDF\u8BA1", style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(20.dp))
        StatsBigNumbers(blockedCount = blockedCount, behaviorCount = behaviorCount, visitedCount = visitedCount, smsCount = smsCount)
        Spacer(modifier = Modifier.height(20.dp))
        RecentAlertsSection(viewModel = viewModel)
    }
}

@Composable
fun StatsBigNumbers(blockedCount: Int, behaviorCount: Int, visitedCount: Int, smsCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(value = blockedCount.toString(), label = "\u62E6\u622A\u57DF\u540D", color = GuardRed)
                BigNumber(value = visitedCount.toString(), label = "\u8BBF\u95EE\u57DF\u540D", color = ShieldBlue)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(value = behaviorCount.toString(), label = "\u884C\u4E3A\u9884\u8B66", color = GuardOrange)
                BigNumber(value = smsCount.toString(), label = "\u77ED\u4FE1\u62E6\u622A", color = GuardRed)
            }
        }
    }
}

@Composable
fun BigNumber(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displayLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
    }
}

@Composable
fun RecentAlertsSection(viewModel: StatsViewModel) {
    val alerts by viewModel.recentAlerts.collectAsState(initial = emptyList())

    Column {
        Text("\u8FD1\u671F\u9884\u8B66", style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark, modifier = Modifier.padding(bottom = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (alerts.isEmpty()) {
                    Text("\u6682\u65E0\u9884\u8B66\u8BB0\u5F55", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    alerts.take(20).forEach { alert ->
                        val (label, color) = when (alert.type) {
                            com.tianshang.guard.data.local.database.AlertType.BLACKLIST_BLOCKED -> "\u62E6\u622A\u57DF\u540D" to GuardRed
                            com.tianshang.guard.data.local.database.AlertType.SUSPICIOUS_DOMAIN -> "\u53EF\u7591\u57DF\u540D" to GuardOrange
                            com.tianshang.guard.data.local.database.AlertType.PHISHING_PAGE -> "\u7F51\u9875\u9884\u8B66" to ShieldBlue
                            com.tianshang.guard.data.local.database.AlertType.SCREEN_SHARE -> "\u884C\u4E3A\u9884\u8B66" to GuardRed
                            com.tianshang.guard.data.local.database.AlertType.VISITED -> "\u8BBF\u95EE\u57DF\u540D" to ShieldBlue
                            com.tianshang.guard.data.local.database.AlertType.SMS_PHISHING -> "\u77ED\u4FE1\u9493\u9C7C" to GuardRed
                        }
                        val displayText = alert.domain ?: alert.url ?: ""
                        AlertTimelineItem(type = label, domain = displayText, time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(alert.timestamp)), color = color)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertTimelineItem(type: String, domain: String, time: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(type, style = MaterialTheme.typography.labelSmall, color = color)
            Text(domain, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
        }
        Text(time, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
    }
}
