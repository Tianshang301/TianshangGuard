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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        Text("\u77ED\u4FE1\u68C0\u6D4B", style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(4.dp))
        Text("\u7C98\u8D34\u53EF\u7591\u77ED\u4FE1\u5185\u5BB9\uFF0C\u5FEB\u901F\u5206\u6790\u98CE\u9669", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("\u53D1\u9001\u65B9\uFF08\u53EF\u9009\uFF09") },
                    placeholder = { Text("\u4F8B\u5982: 10086") },
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
                    label = { Text("\u77ED\u4FE1\u5185\u5BB9") },
                    placeholder = { Text("\u7C98\u8D34\u77ED\u4FE1\u5168\u6587...") },
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
                    Text("\u5F00\u59CB\u5206\u6790", fontWeight = FontWeight.Bold)
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
        RiskLevel.SAFE -> Triple(GuardGreen, "\u5B89\u5168", "\u2705")
        RiskLevel.SUSPICIOUS -> Triple(GuardOrange, "\u53EF\u7591", "\u26A0\uFE0F")
        RiskLevel.DANGEROUS -> Triple(GuardRed, "\u5371\u9669", "\uD83D\uDEA8")
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
                    Text("\u98CE\u9669\u7B49\u7EA7: $label", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
                    if (result.sender.isNotEmpty()) {
                        Text("\u53D1\u9001\u65B9: ${result.sender}", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
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
                RiskLevel.SAFE -> "\u8BE5\u77ED\u4FE1\u672A\u68C0\u6D4B\u5230\u660E\u663E\u9493\u9C7C\u7279\u5F81\uFF0C\u4F46\u4ECD\u8BF7\u4FDD\u6301\u8B66\u89C9\u3002"
                RiskLevel.SUSPICIOUS -> "\u8BE5\u77ED\u4FE1\u5305\u542B\u53EF\u7591\u5185\u5BB9\uFF0C\u8BF7\u52FF\u70B9\u51FB\u4EFB\u4F55\u94FE\u63A5\u6216\u56DE\u590D\u4E2A\u4EBA\u4FE1\u606F\u3002"
                RiskLevel.DANGEROUS -> "\u8BE5\u77ED\u4FE1\u9AD8\u5EA6\u53EF\u7591\uFF01\u53EF\u80FD\u662F\u9493\u9C7C\u6216\u8BC8\u9A97\u4FE1\u606F\uFF0C\u8BF7\u7ACB\u5373\u5220\u9664\uFF0C\u5207\u52FF\u64CD\u4F5C\u3002"
            }
            Text(tip, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
        }
    }
}
