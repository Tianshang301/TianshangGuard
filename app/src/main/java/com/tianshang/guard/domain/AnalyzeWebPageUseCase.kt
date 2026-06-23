package com.tianshang.guard.domain

import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel

class AnalyzeWebPageUseCase(
    private val mlEngine: MlEngine
) {
    fun execute(text: String): RiskLevel {
        return mlEngine.analyzeWebPage(text)
    }
}
