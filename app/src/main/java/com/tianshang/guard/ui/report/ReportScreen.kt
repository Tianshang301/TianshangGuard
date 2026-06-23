package com.tianshang.guard.ui.report

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.OutlineDark
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import com.tianshang.guard.data.repository.AlertRepository
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.local.database.AlertType

@Composable
fun ReportScreen(onBack: () -> Unit = {}) {
    var domain by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val alertRepository = remember { get<AlertRepository>(AlertRepository::class.java) }

    Column(modifier = Modifier.fillMaxSize().background(DeepNavy).padding(16.dp)) {
        Text("\u2190 \u8FD4\u56DE", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark, modifier = Modifier.clickable(onClick = onBack))
        Spacer(modifier = Modifier.height(12.dp))
        Text("\u4E3E\u62A5", style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(8.dp))
        Text("\u63D0\u4EA4\u53EF\u7591\u57DF\u540D\u5E2E\u52A9\u6211\u4EEC\u5B8C\u5584\u9632\u62A4", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = domain, onValueChange = { domain = it },
            label = { Text("\u57DF\u540D") }, placeholder = { Text("example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("\u63CF\u8FF0\uFF08\u53EF\u9009\uFF09") }, placeholder = { Text("\u4E3A\u4EC0\u4E48\u8BA4\u4E3A\u8FD9\u662F\u94F1\u9C7C\u7F51\u7AD9\uFF1F") },
            minLines = 4, modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (domain.isBlank()) {
                    Toast.makeText(context, "\u8BF7\u8F93\u5165\u57DF\u540D", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    alertRepository.insert(AlertEntity(
                        type = AlertType.SUSPICIOUS_DOMAIN,
                        domain = domain,
                        url = null,
                        riskLevel = "USER_REPORT",
                        userAction = description
                    ))
                    Toast.makeText(context, "\u4E3E\u62A5\u5DF2\u63D0\u4EA4\uFF0C\u611F\u8C22\u60A8\u7684\u8D21\u732E", Toast.LENGTH_SHORT).show()
                    domain = ""
                    description = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GuardRed)
        ) { Text("\u63D0\u4EA4\u4E3E\u62A5") }
        Spacer(modifier = Modifier.height(8.dp))
        Text("\u4E3E\u62A5\u4FE1\u606F\u4EC5\u7528\u4E8E\u6539\u8FDB\u89C4\u5219\u5E93\uFF0C\u4E0D\u4F1A\u6536\u96C6\u60A8\u7684\u4E2A\u4EBA\u4FE1\u606F", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnSurfaceDark, unfocusedTextColor = OnSurfaceDark,
    cursorColor = GuardRed, focusedBorderColor = GuardRed,
    unfocusedBorderColor = OutlineDark, focusedLabelColor = GuardRed,
    unfocusedLabelColor = OnSurfaceVariantDark
)
