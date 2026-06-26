package com.tianshang.guard.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianshang.guard.R
import com.tianshang.guard.data.repository.TimeRange
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardOrange
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.ShieldBlue
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatsScreen() {
    val viewModel: StatsViewModel = koinViewModel()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val behaviorCount by viewModel.behaviorCount.collectAsState()
    val visitedCount by viewModel.visitedCount.collectAsState()
    val smsCount by viewModel.smsCount.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)
    ) {
        Text(stringResource(R.string.stats_screen_title), style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(16.dp))
        TimeRangeChips(selected = selectedRange, onSelect = { viewModel.selectRange(it) })
        Spacer(modifier = Modifier.height(16.dp))
        StatsBigNumbers(
            rangeLabel = stringResource(selectedRange.labelRes),
            blockedCount = blockedCount,
            behaviorCount = behaviorCount,
            visitedCount = visitedCount,
            smsCount = smsCount
        )
        Spacer(modifier = Modifier.height(20.dp))
        RecentAlertsSection(viewModel = viewModel)
    }
}

@Composable
fun TimeRangeChips(selected: TimeRange, onSelect: (TimeRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) GuardRed else SurfaceVariantDark)
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(range.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else OnSurfaceVariantDark,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StatsBigNumbers(rangeLabel: String, blockedCount: Int, behaviorCount: Int, visitedCount: Int, smsCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(rangeLabel, style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(value = blockedCount.toString(), label = stringResource(R.string.stats_blocked_domains), color = GuardRed)
                BigNumber(value = visitedCount.toString(), label = stringResource(R.string.stats_visited_domains), color = ShieldBlue)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigNumber(value = behaviorCount.toString(), label = stringResource(R.string.stats_behavior_alerts), color = GuardOrange)
                BigNumber(value = smsCount.toString(), label = stringResource(R.string.stats_sms_blocked), color = GuardRed)
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
        Text(stringResource(R.string.stats_recent_alerts), style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark, modifier = Modifier.padding(bottom = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (alerts.isEmpty()) {
                    Text(stringResource(R.string.stats_no_alerts), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    alerts.take(20).forEach { alert ->
                        val (label, color) = when (alert.type) {
                            com.tianshang.guard.data.local.database.AlertType.BLACKLIST_BLOCKED -> stringResource(R.string.alert_type_blocked_domain) to GuardRed
                            com.tianshang.guard.data.local.database.AlertType.SUSPICIOUS_DOMAIN -> stringResource(R.string.alert_type_suspicious_domain) to GuardOrange
                            com.tianshang.guard.data.local.database.AlertType.PHISHING_PAGE -> stringResource(R.string.alert_type_phishing_page) to ShieldBlue
                            com.tianshang.guard.data.local.database.AlertType.SCREEN_SHARE -> stringResource(R.string.alert_type_behavior_alert) to GuardRed
                            com.tianshang.guard.data.local.database.AlertType.VISITED -> stringResource(R.string.alert_type_visited_domain) to ShieldBlue
                            com.tianshang.guard.data.local.database.AlertType.SMS_PHISHING -> stringResource(R.string.alert_type_sms_phishing) to GuardRed
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
