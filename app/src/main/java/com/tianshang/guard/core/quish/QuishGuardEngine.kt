package com.tianshang.guard.core.quish

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.alert.AlertLevel
import com.tianshang.guard.core.alert.TieredAlertEngine
import com.tianshang.guard.core.dns.DnsResult
import com.tianshang.guard.domain.CheckDomainRiskUseCase

sealed class QrDecision {
    data object Pass : QrDecision()
    data object WarnWithPreview : QrDecision()
    data object BlockWithPreview : QrDecision()
}

class QuishGuardEngine(
    private val qrDecoder: QrCodeDecoder,
    private val urlRiskAnalyzer: CheckDomainRiskUseCase,
    private val alertEngine: AlertEngine
) {

    fun analyzeQrContent(rawData: String): QrDecision {
        val parsed = qrDecoder.decode(rawData)
        return when (parsed.type) {
            QrType.URL -> analyzeUrl(parsed.content)
            QrType.PAYMENT -> analyzePaymentQr(parsed.content)
            QrType.WIFI -> QrDecision.Pass
            QrType.TEXT -> {
                val embeddedUrl = extractEmbeddedUrl(parsed.content)
                if (embeddedUrl != null) analyzeUrl(embeddedUrl)
                else QrDecision.Pass
            }
        }
    }

    private fun analyzeUrl(url: String): QrDecision {
        val domain = extractDomain(url) ?: return QrDecision.Pass
        val result = urlRiskAnalyzer.execute(domain)
        return when (result) {
            is DnsResult.Block -> QrDecision.BlockWithPreview
            is DnsResult.Unknown -> {
                if (result.riskScore >= 0.50f) QrDecision.BlockWithPreview
                else if (result.riskScore >= 0.10f) QrDecision.WarnWithPreview
                else QrDecision.Pass
            }
            is DnsResult.Allow -> QrDecision.Pass
        }
    }

    private fun analyzePaymentQr(content: String): QrDecision {
        val embeddedUrl = extractEmbeddedUrl(content)
        if (embeddedUrl != null) return analyzeUrl(embeddedUrl)
        return QrDecision.WarnWithPreview
    }

    private fun extractDomain(url: String): String? {
        return try {
            val u = java.net.URI(url)
            u.host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun extractEmbeddedUrl(text: String): String? {
        val urlPattern = java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+")
        val matcher = urlPattern.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }
}
