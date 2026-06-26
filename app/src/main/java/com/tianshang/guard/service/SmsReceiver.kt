package com.tianshang.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.domain.AnalyzeSmsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val analyzeSmsUseCase: AnalyzeSmsUseCase by inject()
    private val alertEngine: AlertEngine by inject()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()

        ioScope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (message in messages) {
                    val sender = message.displayOriginatingAddress ?: "unknown"
                    val body = message.messageBody ?: continue

                    val riskLevel = analyzeSmsUseCase.execute(sender, body)
                    if (riskLevel == RiskLevel.SUSPICIOUS || riskLevel == RiskLevel.DANGEROUS) {
                        alertEngine.showSmsWarning(sender, body, riskLevel)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
