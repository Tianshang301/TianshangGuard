package com.tianshang.guard.ui.sms

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianshang.guard.R
import com.tianshang.guard.ui.DisclaimerText
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardGreen
import com.tianshang.guard.ui.theme.GuardOrange
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import org.koin.androidx.compose.koinViewModel

@Composable
fun SmsScreen() {
    val viewModel: SmsViewModel = koinViewModel()
    val result by viewModel.result.collectAsState()
    var sender by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepNavy).padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.sms_screen_title), style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.sms_screen_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text(stringResource(R.string.sms_sender_label)) },
                    placeholder = { Text(stringResource(R.string.sms_sender_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GuardRed,
                        unfocusedBorderColor = SurfaceVariantDark,
                        focusedLabelColor = GuardRed,
                        cursorColor = GuardRed
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.sms_body_label)) },
                    placeholder = { Text(stringResource(R.string.sms_body_placeholder)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GuardRed,
                        unfocusedBorderColor = SurfaceVariantDark,
                        focusedLabelColor = GuardRed,
                        cursorColor = GuardRed
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.analyze(sender, body) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = body.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GuardRed)
                ) {
                    Text(stringResource(R.string.sms_button_analyze), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (result.analyzed) {
            Spacer(modifier = Modifier.height(16.dp))
            SmsResultCard(result = result)
        }
    }
}

@Composable
fun SmsResultCard(result: SmsAnalysisResult) {
    val (color, label, emoji) = when (result.riskLevel) {
        RiskLevel.SAFE -> Triple(GuardGreen, stringResource(R.string.risk_level_safe), "\u2705")
        RiskLevel.SUSPICIOUS -> Triple(GuardOrange, stringResource(R.string.risk_level_suspicious), "\u26A0\uFE0F")
        RiskLevel.DANGEROUS -> Triple(GuardRed, stringResource(R.string.risk_level_dangerous), "\uD83D\uDEA8")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.sms_risk_level_label, label), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
                    if (result.sender.isNotEmpty()) {
                        Text(stringResource(R.string.sms_sender_display, result.sender), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = result.body,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark,
                modifier = Modifier.fillMaxWidth().background(SurfaceVariantDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            val tip = when (result.riskLevel) {
                RiskLevel.SAFE -> stringResource(R.string.sms_tip_safe)
                RiskLevel.SUSPICIOUS -> stringResource(R.string.sms_tip_suspicious)
                RiskLevel.DANGEROUS -> stringResource(R.string.sms_tip_dangerous)
            }
            Text(tip, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
            Spacer(modifier = Modifier.height(8.dp))
            DisclaimerText()
        }
    }
}
