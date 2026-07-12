package com.tianshang.guard.core.quish

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader

enum class QrType {
    URL,
    PAYMENT,
    WIFI,
    TEXT
}

data class QrContent(
    val raw: String,
    val type: QrType,
    val content: String,
    val zxingResult: Result? = null
)

class QrCodeDecoder {

    private val reader = QRCodeMultiReader()

    fun decode(rawData: String): QrContent {
        val trimmed = rawData.trim()
        val type = classify(trimmed)
        return QrContent(
            raw = trimmed,
            type = type,
            content = extractContent(trimmed, type)
        )
    }

    fun decodeBitmap(pixels: IntArray, width: Int, height: Int): QrContent? {
        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeMultiple(bitmap).firstOrNull() ?: return null
        return decode(result.text)
    }

    private fun classify(text: String): QrType {
        val lower = text.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> QrType.URL
            lower.startsWith("upi://") || lower.startsWith("alipay://") ||
                lower.startsWith("wxp://") || lower.startsWith("weixin://") ||
                lower.startsWith("mqqapi://") || lower.startsWith("银行卡") -> QrType.PAYMENT
            lower.startsWith("wifi:") || lower.startsWith("wifi;") -> QrType.WIFI
            else -> QrType.TEXT
        }
    }

    private fun extractContent(text: String, type: QrType): String {
        return when (type) {
            QrType.URL -> text
            QrType.PAYMENT -> text
            QrType.WIFI -> text.removePrefix("wifi:").removePrefix("wifi;").trim()
            QrType.TEXT -> text
        }
    }
}
