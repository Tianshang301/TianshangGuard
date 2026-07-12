package com.tianshang.guard.core.ml

class InputSanitizer {

    companion object {
        private val nulRegex = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]")
        private val controlRegex = Regex("[\u007F\u0080-\u009F]")
        private val urlEncodedRegex = Regex("%[0-9A-Fa-f]{2}")
        private val unicodeEscRegex = Regex("\\\\u[0-9A-Fa-f]{4}")
        private val repeatedCharRegex = Regex("(.)\\1{10,}")
        private val repeatedPatternRegex = Regex("(.{2,5})\\1{5,}")
        private val maxTextLength = 2048
    }

    data class SanitizedInput(
        val text: String,
        val wasTruncated: Boolean = false,
        val hadControlChars: Boolean = false,
        val hadRepetitions: Boolean = false,
        val originalLength: Int = 0
    )

    fun sanitize(text: String): SanitizedInput {
        val originalLength = text.length
        var result = text
        var hadControlChars = false
        var hadRepetitions = false

        result = result.replace(nulRegex, "")
        if (result.length != originalLength) hadControlChars = true

        result = result.replace(controlRegex, "")
        if (result.length != originalLength - (originalLength - result.length)) {
            hadControlChars = true
        }

        result = result.replace(urlEncodedRegex) { match ->
            val hex = match.value.substring(1)
            val charCode = Integer.parseInt(hex, 16)
            if (charCode < 0x20 || charCode == 0x7F) "" else match.value
        }

        result = result.replace(unicodeEscRegex) { match ->
            val hex = match.value.substring(2)
            val charCode = Integer.parseInt(hex, 16)
            if (charCode < 0x20 || charCode == 0x7F) "" else match.value
        }

        val beforeRepeat = result
        result = result.replace(repeatedCharRegex) { match ->
            hadRepetitions = true
            match.value.take(10)
        }
        result = result.replace(repeatedPatternRegex) { match ->
            hadRepetitions = true
            match.value.take(20)
        }

        val wasTruncated = result.length > maxTextLength
        if (wasTruncated) {
            result = result.take(maxTextLength)
        }

        return SanitizedInput(
            text = result,
            wasTruncated = wasTruncated,
            hadControlChars = hadControlChars,
            hadRepetitions = hadRepetitions,
            originalLength = originalLength
        )
    }

    fun normalizeUrl(url: String): String {
        var normalized = url.lowercase().trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        normalized = normalized.replace("http://", "https://")
        val uri = try {
            java.net.URI(normalized)
        } catch (_: Exception) {
            return normalized
        }
        val host = uri.host?.removePrefix("www.") ?: return normalized
        val path = uri.path ?: ""
        val query = uri.query?.let { "?$it" } ?: ""
        return "https://$host$path$query"
    }

    fun isSuspiciousInput(text: String): Boolean {
        val entropy = calculateEntropy(text)
        if (entropy < 1.0f && text.length > 20) return true
        if (text.any { it.code in 0x200B..0x200F || it.code in 0x2028..0x2029 ||
            it.code in 0xFE00..0xFE0F || it.code == 0xFEFF }) return true
        val asciiRatio = text.count { it.code < 128 }.toFloat() / maxOf(text.length, 1)
        if (text.any { it.code > 0x4E00 } && asciiRatio > 0.95f && text.length > 50) return true
        return false
    }

    private fun calculateEntropy(text: String): Float {
        if (text.isEmpty()) return 0f
        val freq = mutableMapOf<Char, Int>()
        for (c in text) freq[c] = (freq[c] ?: 0) + 1
        val len = text.length.toDouble()
        val log2 = { x: Double -> kotlin.math.ln(x) / kotlin.math.ln(2.0) }
        return -freq.values.sumOf { count ->
            val p = count.toDouble() / len
            p * log2(p)
        }.toFloat()
    }
}
