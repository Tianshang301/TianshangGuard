package com.tianshang.guard.domain

import com.tianshang.guard.core.quish.QrCodeDecoder
import com.tianshang.guard.core.quish.QrDecision
import com.tianshang.guard.core.quish.QuishGuardEngine

class InterceptQrUseCase(
    private val quishGuardEngine: QuishGuardEngine
) {
    fun execute(rawData: String): QrDecision {
        return quishGuardEngine.analyzeQrContent(rawData)
    }
}
