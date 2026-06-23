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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tianshang.guard.R
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
        Text(stringResource(R.string.button_back), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark, modifier = Modifier.clickable(onClick = onBack))
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.report_screen_title), style = MaterialTheme.typography.headlineLarge, color = OnSurfaceDark)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.report_screen_subtitle), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariantDark)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = domain, onValueChange = { domain = it },
            label = { Text(stringResource(R.string.label_domain)) }, placeholder = { Text("example.com") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text(stringResource(R.string.report_description_label)) }, placeholder = { Text(stringResource(R.string.report_description_placeholder)) },
            minLines = 4, modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (domain.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.toast_enter_domain), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, context.getString(R.string.toast_report_submitted), Toast.LENGTH_SHORT).show()
                    domain = ""
                    description = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GuardRed)
        ) { Text(stringResource(R.string.report_button_submit)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.report_privacy_notice), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnSurfaceDark, unfocusedTextColor = OnSurfaceDark,
    cursorColor = GuardRed, focusedBorderColor = GuardRed,
    unfocusedBorderColor = OutlineDark, focusedLabelColor = GuardRed,
    unfocusedLabelColor = OnSurfaceVariantDark
)
