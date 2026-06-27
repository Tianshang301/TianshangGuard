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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.tianshang.guard.R
import com.tianshang.guard.core.feedback.FeedbackEngine
import com.tianshang.guard.core.calibration.ThresholdCalibrator
import com.tianshang.guard.data.local.database.FeedbackLabel
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import com.tianshang.guard.ui.theme.TianshangGuardTheme
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.core.alert.AlertDataHolder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.tianshang.guard.data.repository.AlertRepository

class AlertActivity : ComponentActivity(), KoinComponent {

    private val alertRepository: AlertRepository by inject()
    private val feedbackEngine: FeedbackEngine by inject()
    private val thresholdCalibrator: ThresholdCalibrator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alertKey = intent.getStringExtra("alert_key")
        val data = alertKey?.let { AlertDataHolder.consume(it) }

        val alertType = data?.alertType ?: "SCREEN_SHARE"
        val domain = data?.domain
        val url = data?.url
        val smsSender = data?.smsSender
        val smsBody = data?.smsBody
        val riskLevel = data?.riskLevel

        setContent {
            TianshangGuardTheme {
                when (alertType) {
                    "SCREEN_SHARE" -> ScreenShareAlert(onDismiss = { finish() })
                    "PHISHING_PAGE" -> PhishingAlert(
                        url = url ?: "",
                        onDismiss = { finish() },
                        onReturn = { finish() },
                        onFeedback = { label -> submitFeedback(url ?: "", riskLevel ?: "SUSPICIOUS", label, "webpage") }
                    )
                    "SUSPICIOUS_DOMAIN" -> SuspiciousDomainAlert(
                        domain = domain ?: "unknown",
                        onDismiss = { finish() },
                        onFeedback = { label -> submitFeedback(domain ?: "", riskLevel ?: "SUSPICIOUS", label, "domain") }
                    )
                    "SMS_PHISHING" -> SmsPhishingAlert(
                        sender = smsSender ?: "unknown",
                        body = smsBody ?: "",
                        onDismiss = { finish() },
                        onFeedback = { label -> submitFeedback(smsBody ?: "", riskLevel ?: "SUSPICIOUS", label, "sms") },
                        detectionReasons = data?.detectionReasons ?: emptyList(),
                        mlScore = data?.mlScore
                    )
                    else -> BlockedAlert(domain = domain ?: "unknown", onDismiss = { finish() })
                }
            }
        }
    }

    private fun submitFeedback(text: String, riskLevel: String, label: FeedbackLabel, source: String) {
        val modelScore = when (riskLevel) {
            "DANGEROUS" -> 0.8f
            "SUSPICIOUS" -> 0.3f
            else -> 0.1f
        }
        feedbackEngine.recordFeedback(text, modelScore, label, source)
        thresholdCalibrator.recordFeedback(modelScore, label)
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
            Text(stringResource(R.string.alert_screen_share_title), style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.alert_screen_share_message), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.alert_screen_share_warning), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text(stringResource(R.string.alert_screen_share_confirm), color = GuardRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PhishingAlert(url: String, onDismiss: () -> Unit, onReturn: () -> Unit, onFeedback: (FeedbackLabel) -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize().background(DeepNavy), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(GuardRed.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("\u26A0\uFE0F", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.alert_phishing_title), style = MaterialTheme.typography.headlineMedium, color = GuardRed)
                Spacer(modifier = Modifier.height(8.dp))
                if (url.isNotEmpty()) {
                    Text(url, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(stringResource(R.string.alert_phishing_message), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)) {
                        Text(stringResource(R.string.button_close), color = OnSurfaceDark)
                    }
                    Button(onClick = onReturn, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                        Text(stringResource(R.string.button_return))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onFeedback(FeedbackLabel.FALSE_POSITIVE); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)) {
                        Text(stringResource(R.string.feedback_false_positive), color = OnSurfaceDark)
                    }
                    Button(onClick = { onFeedback(FeedbackLabel.PHISHING); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                        Text(stringResource(R.string.feedback_confirm_phishing))
                    }
                }
            }
        }
    }
}

@Composable
fun SuspiciousDomainAlert(domain: String, onDismiss: () -> Unit, onFeedback: (FeedbackLabel) -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = GuardRed.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("\u26A0\uFE0F", fontSize = 24.sp)
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.alert_suspicious_domain_title), style = MaterialTheme.typography.titleSmall, color = GuardRed)
                    Text(domain, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Feedback buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onFeedback(FeedbackLabel.FALSE_POSITIVE); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                ) {
                    Text(stringResource(R.string.feedback_false_positive), color = OnSurfaceDark)
                }
                Button(
                    onClick = { onFeedback(FeedbackLabel.PHISHING); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GuardRed)
                ) {
                    Text(stringResource(R.string.feedback_confirm_phishing))
                }
            }
        }
    }
}

@Composable
fun BlockedAlert(domain: String, onDismiss: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u2705", fontSize = 18.sp)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.alert_blocked_phishing_domain, domain), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark, modifier = Modifier.weight(1f))
            // BUGFIX: Add dismiss button so user can close the alert
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                Text(stringResource(R.string.button_confirm))
            }
        }
    }
}

@Composable
fun SmsPhishingAlert(sender: String, body: String, onDismiss: () -> Unit, onFeedback: (FeedbackLabel) -> Unit = {},
                     detectionReasons: List<String> = emptyList(), mlScore: Float? = null) {
    var showDetails by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize().background(DeepNavy), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(GuardRed.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("\u26A0\uFE0F", fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.alert_sms_phishing_title), style = MaterialTheme.typography.headlineMedium, color = GuardRed)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.alert_sms_sender_label, sender), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
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
                Text(stringResource(R.string.alert_sms_phishing_message), style = MaterialTheme.typography.bodySmall, color = GuardRed, textAlign = TextAlign.Center)
                
                // Detection details section
                if (detectionReasons.isNotEmpty() || mlScore != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(
                            if (showDetails) stringResource(R.string.alert_hide_details) else stringResource(R.string.alert_show_details),
                            color = OnSurfaceVariantDark
                        )
                    }
                    
                    if (showDetails) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (mlScore != null) {
                                    Text(
                                        stringResource(R.string.alert_ml_score, String.format("%.2f", mlScore)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDark
                                    )
                                }
                                detectionReasons.forEach { reason ->
                                    Text(
                                        "• $reason",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDark,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onFeedback(FeedbackLabel.FALSE_POSITIVE); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)) {
                        Text(stringResource(R.string.feedback_false_positive), color = OnSurfaceDark)
                    }
                    Button(onClick = { onFeedback(FeedbackLabel.PHISHING); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = GuardRed)) {
                        Text(stringResource(R.string.feedback_confirm_phishing))
                    }
                }
            }
        }
    }
}
