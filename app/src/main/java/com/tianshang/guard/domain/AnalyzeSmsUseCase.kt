package com.tianshang.guard.domain

import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.ModelType
import com.tianshang.guard.core.ml.RiskLevel

class AnalyzeSmsUseCase(private val mlEngine: MlEngine) {

    @Suppress("UNUSED_PARAMETER")
    fun execute(sender: String, body: String): RiskLevel {
        if (body.isBlank()) return RiskLevel.SAFE
        return mlEngine.analyzeSms(body)
    }
}
