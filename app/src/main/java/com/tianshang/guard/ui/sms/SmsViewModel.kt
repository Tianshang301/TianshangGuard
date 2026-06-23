package com.tianshang.guard.ui.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.domain.AnalyzeSmsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SmsAnalysisResult(
    val sender: String,
    val body: String,
    val riskLevel: RiskLevel,
    val analyzed: Boolean = false
)

class SmsViewModel(
    private val analyzeSmsUseCase: AnalyzeSmsUseCase,
    private val alertEngine: AlertEngine
) : ViewModel() {

    private val _result = MutableStateFlow(SmsAnalysisResult("", "", RiskLevel.SAFE))
    val result: StateFlow<SmsAnalysisResult> = _result.asStateFlow()

    fun analyze(sender: String, body: String) {
        viewModelScope.launch {
            val riskLevel = analyzeSmsUseCase.execute(sender, body)
            _result.value = SmsAnalysisResult(sender, body, riskLevel, true)
            if (riskLevel == RiskLevel.DANGEROUS || riskLevel == RiskLevel.SUSPICIOUS) {
                alertEngine.showSmsWarning(sender, body, riskLevel)
            }
        }
    }

    fun reset() {
        _result.value = SmsAnalysisResult("", "", RiskLevel.SAFE)
    }
}
