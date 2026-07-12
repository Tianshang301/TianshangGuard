package com.tianshang.guard.domain

import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.core.ml.RuleBasedEngine

class AnalyzeSmsUseCase(
    private val mlEngine: MlEngine,
    private val ruleEngine: RuleBasedEngine
) {
    fun execute(body: String): RiskLevel {
        if (body.isBlank()) return RiskLevel.SAFE

        val ruleResult = ruleEngine.analyzeSms(body)
        if (ruleResult == RiskLevel.DANGEROUS) {
            return RiskLevel.DANGEROUS
        }

        val mlResult = mlEngine.analyzeSms(body)
        return maxOf(ruleResult, mlResult, compareBy { it.ordinal })
    }
}
