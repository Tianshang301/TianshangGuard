package com.tianshang.guard.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianshang.guard.R
import com.tianshang.guard.service.GuardVpnService
import com.tianshang.guard.ui.onboarding.OnboardingScreen
import com.tianshang.guard.ui.report.ReportScreen
import com.tianshang.guard.ui.settings.SettingsScreen
import com.tianshang.guard.ui.sms.SmsScreen
import com.tianshang.guard.ui.stats.StatsScreen
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardGreen
import com.tianshang.guard.ui.theme.GuardOrange
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.ShieldBlue
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import com.tianshang.guard.ui.theme.TianshangGuardTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val prefs: com.tianshang.guard.data.local.GuardPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TianshangGuardTheme {
                val onboardingDone by prefs.onboardingDone.collectAsState(initial = false)
                val scope = rememberCoroutineScope()
                if (!onboardingDone) {
                    OnboardingScreen(
                        onComplete = {
                            scope.launch {
                                prefs.setOnboardingDone()
                            }
                        }
                    )
                } else {
                    MainApp()
                }
            }
        }
    }
}

private fun startVpnService(context: Context) {
    val intent = Intent(context, GuardVpnService::class.java).apply {
        action = GuardVpnService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopVpnService(context: Context) {
    val intent = Intent(context, GuardVpnService::class.java).apply {
        action = GuardVpnService.ACTION_STOP
    }
    context.startService(intent)
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val viewModel: MainViewModel = koinViewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = OnSurfaceDark
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("\uD83C\uDFE0", fontSize = 20.sp) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GuardRed, selectedTextColor = GuardRed,
                        unselectedIconColor = OnSurfaceVariantDark, unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = SurfaceVariantDark
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("\uD83D\uDCE8", fontSize = 20.sp) },
                    label = { Text(stringResource(R.string.nav_sms)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GuardRed, selectedTextColor = GuardRed,
                        unselectedIconColor = OnSurfaceVariantDark, unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = SurfaceVariantDark
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("\uD83D\uDCCA", fontSize = 20.sp) },
                    label = { Text(stringResource(R.string.nav_stats)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GuardRed, selectedTextColor = GuardRed,
                        unselectedIconColor = OnSurfaceVariantDark, unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = SurfaceVariantDark
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("\u2699\uFE0F", fontSize = 20.sp) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GuardRed, selectedTextColor = GuardRed,
                        unselectedIconColor = OnSurfaceVariantDark, unselectedTextColor = OnSurfaceVariantDark,
                        indicatorColor = SurfaceVariantDark
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomePage(viewModel, onNavigateToStats = { selectedTab = 2 }, onNavigateToReport = { selectedTab = 4 })
                1 -> SmsScreen()
                2 -> StatsScreen()
                3 -> SettingsScreen()
                4 -> ReportScreen(onBack = { selectedTab = 0 })
            }
        }
    }
}

@Composable
fun HomePage(
    viewModel: MainViewModel,
    onNavigateToStats: () -> Unit,
    onNavigateToReport: () -> Unit
) {
    val context = LocalContext.current
    val vpnRunning by viewModel.vpnRunning.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val visitedCount by viewModel.visitedCount.collectAsState()
    val behaviorCount by viewModel.behaviorCount.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(context)
            viewModel.setVpnRunning(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        Spacer(modifier = Modifier.height(20.dp))
        VpnStatusCard(
            vpnRunning = vpnRunning,
            onToggle = {
                if (vpnRunning) {
                    stopVpnService(context)
                    viewModel.setVpnRunning(false)
                } else {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent != null) {
                        vpnLauncher.launch(prepareIntent)
                    } else {
                        startVpnService(context)
                        viewModel.setVpnRunning(true)
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatsOverviewCard(blockedCount = blockedCount, visitedCount = visitedCount, behaviorCount = behaviorCount)
        Spacer(modifier = Modifier.height(16.dp))
        QuickActionsRow(onNavigateToStats = onNavigateToStats, onNavigateToReport = onNavigateToReport)
    }
}

@Composable
fun VpnStatusCard(vpnRunning: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (vpnRunning) GuardGreen else GuardRed)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (vpnRunning) stringResource(R.string.vpn_status_enabled) else stringResource(R.string.vpn_status_disabled),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceDark
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (vpnRunning) stringResource(R.string.vpn_blocking_phishing) else stringResource(R.string.vpn_tap_to_start),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vpnRunning) GuardRed else ShieldBlue
                )
            ) {
                Text(if (vpnRunning) stringResource(R.string.vpn_button_stop) else stringResource(R.string.vpn_button_start))
            }
        }
    }
}

@Composable
fun StatsOverviewCard(blockedCount: Int, visitedCount: Int, behaviorCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.stats_today_title), style = MaterialTheme.typography.titleMedium, color = OnSurfaceDark)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = blockedCount.toString(), label = stringResource(R.string.stats_blocked_domains), color = GuardRed)
                StatItem(value = visitedCount.toString(), label = stringResource(R.string.stats_visited_domains), color = ShieldBlue)
                StatItem(value = behaviorCount.toString(), label = stringResource(R.string.stats_behavior_alerts), color = GuardOrange)
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
    }
}

@Composable
fun QuickActionsRow(onNavigateToStats: () -> Unit, onNavigateToReport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(emoji = "\uD83D\uDEAB", label = stringResource(R.string.action_quick_report), onClick = onNavigateToReport)
            ActionButton(emoji = "\uD83D\uDCCA", label = stringResource(R.string.action_view_stats), onClick = onNavigateToStats)
            ActionButton(emoji = "\uD83D\uDEE1\uFE0F", label = stringResource(R.string.action_full_check), onClick = onNavigateToReport)
        }
    }
}

@Composable
fun ActionButton(emoji: String, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
    }
}
