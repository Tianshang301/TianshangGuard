package com.tianshang.guard.core.dns

import java.net.IDN

object HomographDetector {

    private val homographMap = mapOf(
        '\u0430' to 'a', '\u0435' to 'e', '\u043E' to 'o', '\u0440' to 'p',
        '\u0441' to 'c', '\u0445' to 'x', '\u0443' to 'y',
        '\u0410' to 'A', '\u0415' to 'E', '\u041E' to 'O', '\u0420' to 'P',
        '\u0421' to 'C', '\u0425' to 'X',
        '\u03BF' to 'o', '\u0435' to 'e',
        '\uFF41' to 'a', '\uFF45' to 'e', '\uFF4F' to 'o'
    )

    private val pinyinHomophones = mapOf(
        "taobao" to listOf("ta0bao", "tao-bao", "tаоbao"),
        "alipay" to listOf("a1ipay", "allpay", "al1pay"),
        "wechat" to listOf("we1chat", "weehat", "wech4t")
    )

    fun detect(domain: String): HomographResult {
        val normalized = domain.map { homographMap[it] ?: it }.joinToString("")

        if (normalized != domain) {
            return HomographResult.Detected(
                type = HomographType.VISUAL_SPOOFING,
                original = domain,
                normalized = normalized
            )
        }

        if (hasSuspiciousPunycode(domain)) {
            val punycode = IDN.toASCII(domain)
            return HomographResult.Detected(
                type = HomographType.PUNYCODE_SPOOFING,
                original = domain,
                punycode = punycode
            )
        }

        return HomographResult.Clean
    }

    fun checkPinyinConfusion(domain: String): Float {
        val lowerDomain = domain.lowercase()
        return pinyinHomophones.maxOf { (_, variants) ->
            variants.maxOf { variant ->
                fuzzyRatio(variant, lowerDomain) / 100f
            }
        }
    }

    private fun fuzzyRatio(a: String, b: String): Int {
        val distance = levenshteinDistance(a, b)
        return ((1f - distance.toFloat() / maxOf(a.length, b.length)) * 100).toInt()
    }

    private fun hasSuspiciousPunycode(domain: String): Boolean {
        if (!domain.startsWith("xn--")) return false
        val unicode = try { IDN.toUnicode(domain) } catch (_: Exception) { return false }
        if (unicode == domain) return false
        val normalized = unicode.map { homographMap[it] ?: it }.joinToString("")
        return normalized != unicode
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val costs = IntArray(b.length + 1)
        for (j in 0..b.length) costs[j] = j
        for (i in 1..a.length) {
            costs[0] = i
            var previous = i - 1
            for (j in 1..b.length) {
                val current = previous
                previous = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    current + if (a[i - 1] != b[j - 1]) 1 else 0
                )
            }
        }
        return costs[b.length]
    }
}

sealed class HomographResult {
    object Clean : HomographResult()
    data class Detected(
        val type: HomographType,
        val original: String,
        val normalized: String? = null,
        val punycode: String? = null
    ) : HomographResult()
}

enum class HomographType {
    VISUAL_SPOOFING,
    PUNYCODE_SPOOFING
}
