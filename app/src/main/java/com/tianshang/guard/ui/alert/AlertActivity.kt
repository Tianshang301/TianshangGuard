package com.tianshang.guard.ui.alert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import com.tianshang.guard.ui.theme.TianshangGuardTheme
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.local.database.AlertEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.tianshang.guard.data.repository.AlertRepository

class AlertActivity : ComponentActivity(), KoinComponent {

    private val alertRepository: AlertRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alertType = intent.getStringExtra("alert_type") ?: "SCREEN_SHARE"
        val domain = intent.getStringExtra("domain")
        val url = intent.getStringExtra("url")
        val smsSender = intent.getStringExtra("sms_sender")
        val smsBody = intent.getStringExtra("sms_body")
        setContent {
            TianshangGuardTheme {
                when (alertType) {
                    "SCREEN_SHARE" -> ScreenShareAlert(onDismiss = { finish() })
                    "PHISHING_PAGE" -> PhishingAlert(
                        url = url ?: "",
                        onDismiss = { finish() },
                        onReturn = { finish() }
                    )
                    "SUSPICIOUS_DOMAIN" -> SuspiciousDomainAlert(
                        domain = domain ?: "unknown",
                        onDismiss = { finish() }
                    )
                    "SMS_PHISHING" -> SmsPhishingAlert(
                        sender = smsSender ?: "unknown",
                        body = smsBody ?: "",
                        onDismiss = { finish() }
                    )
                    else -> BlockedAlert(domain = domain ?: "unknown")
                }
            }
        }
    }
}

@Composable
fun ScreenShareAlert(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(GuardRed.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\u26A0\uFE0F", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("\u9AD8\u98CE\u9669\u64CD\u4F5C\u68C0\u6D4B", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("\u68C0\u6D4B\u5230\u5C4F\u5E55\u5171\u4EAB\u8F6F\u4EF6\u6B63\u5728\u8FD0\u884C\n\u540C\u65F6\u60A8\u6B63\u5728\u4F7F\u7528\u94F6\u884C/\u652F\u4ED8\u5E94\u7528", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Text("\u8BF7\u52FF\u5728\u5171\u4EAB\u671F\u95F4\u8F93\u5165\u5BC6\u7801\u3001\u8F6C\u8D26\u6216\u5C55\u793A\u9A8C\u8BC1\u7801", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text("\u6211\u77E5\u9053\u4E86\uFF0C\u7ACB\u5373\u5904\u7406", color = GuardRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PhishingAlert(url: String, onDismiss: () -> Unit, onReturn: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DeepNavy), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(GuardRed.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("\u26A0\uFE0F", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("\u7F51\u9875\u9493\u9C7C\u8B66\u544A", style = MaterialTheme.typography.headlineMedium, color = GuardRed)
                Spacer(modifier = Modifier.height(8.dp))
                if (url.isNotEmpty()) {
                    Text(url, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("\u6B64\u7F51\u9875\u53EF\u80FD\u662F\u9493\u9C7C\u7F51\u7AD9\uFF0C\u8BF7\u52FF\u8F93\u5165\u4EFB\u4F55\u4E2A\u4EBA\u4FE1\u606F", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)) {
                        Text("\u5173\u95ED", color = OnSurfaceDark)
                    }
                    Button(onClick = onReturn, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                        Text("\u8FD4\u56DE")
                    }
                }
            }
        }
    }
}

@Composable
fun SuspiciousDomainAlert(domain: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = GuardRed.copy(alpha = 0.15f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u26A0\uFE0F", fontSize = 24.sp)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("\u53EF\u7591\u57DF\u540D", style = MaterialTheme.typography.titleSmall, color = GuardRed)
                Text(domain, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
            }
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                Text("\u786E\u5B9A")
            }
        }
    }
}

@Composable
fun BlockedAlert(domain: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u2705", fontSize = 18.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Text("\u5DF2\u62E6\u622A\u9493\u9C7C\u57DF\u540D: $domain", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
        }
    }
}

@Composable
fun SmsPhishingAlert(sender: String, body: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DeepNavy), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(GuardRed.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("\u26A0\uFE0F", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("\u77ED\u4FE1\u9493\u9C7C\u8B66\u544A", style = MaterialTheme.typography.headlineMedium, color = GuardRed)
                Spacer(modifier = Modifier.height(8.dp))
                Text("\u53D1\u9001\u65B9: $sender", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDark,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("\u6B64\u77ED\u4FE1\u53EF\u80FD\u5305\u542B\u9493\u9C7C\u94FE\u63A5\u6216\u8BC8\u9A97\u4FE1\u606F\uFF0C\u8BF7\u52FF\u70B9\u51FB\u4EFB\u4F55\u94FE\u63A5\u6216\u56DE\u590D\u4E2A\u4EBA\u4FE1\u606F", style = MaterialTheme.typography.bodySmall, color = GuardRed, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                    Text("\u6211\u77E5\u9053\u4E86", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
