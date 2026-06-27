package com.tianshang.guard.core.dns

import java.net.IDN

object HomographDetector {

    // HD-01: Extended confusable character map covering Cyrillic, Greek, Armenian, Fullwidth
    private val homographMap = mapOf(
        // Cyrillic -> Latin
        '\u0430' to 'a', '\u0435' to 'e', '\u043E' to 'o', '\u0440' to 'p',
        '\u0441' to 'c', '\u0445' to 'x', '\u0443' to 'y',
        '\u0410' to 'A', '\u0415' to 'E', '\u041E' to 'O', '\u0420' to 'P',
        '\u0421' to 'C', '\u0425' to 'X',
        '\u0456' to 'i', '\u0455' to 's', '\u04BB' to 'h', '\u043A' to 'k',
        '\u043D' to 'h', '\u0444' to 'f', '\u0432' to 'b', '\u043C' to 'm',
        '\u0442' to 't',
        // Greek -> Latin
        '\u03BF' to 'o', '\u03B1' to 'a', '\u03B5' to 'e', '\u03B9' to 'i',
        '\u03BA' to 'k', '\u03BD' to 'v', '\u03C1' to 'p', '\u03C3' to 's',
        '\u03C4' to 't', '\u03C7' to 'x',
        // Fullwidth -> ASCII
        '\uFF41' to 'a', '\uFF42' to 'b', '\uFF43' to 'c', '\uFF44' to 'd',
        '\uFF45' to 'e', '\uFF46' to 'f', '\uFF47' to 'g', '\uFF48' to 'h',
        '\uFF49' to 'i', '\uFF4A' to 'j', '\uFF4B' to 'k', '\uFF4C' to 'l',
        '\uFF4D' to 'm', '\uFF4E' to 'n', '\uFF4F' to 'o', '\uFF50' to 'p',
        '\uFF51' to 'q', '\uFF52' to 'r', '\uFF53' to 's', '\uFF54' to 't',
        '\uFF55' to 'u', '\uFF56' to 'v', '\uFF57' to 'w', '\uFF58' to 'x',
        '\uFF59' to 'y', '\uFF5A' to 'z',
        // Armenian -> Latin
        '\u0561' to 'a', '\u0565' to 'e', '\u0578' to 'o', '\u0585' to 'o',
        // Latin lookalikes
        '\u0131' to 'i', '\u0261' to 'g', '\u019A' to 'l'
    )

    private val pinyinHomophones = mapOf(
        "taobao" to listOf("ta0bao", "tao-bao", "tаоbao", "taoba0", "ta0ba0"),
        "alipay" to listOf("a1ipay", "allpay", "al1pay", "a11pay", "al1pa1"),
        "wechat" to listOf("we1chat", "weehat", "wech4t", "weech4t"),
        "icbc" to listOf("1cbc", "ic8c", "lcbc", "iсbc"),
        "baidu" to listOf("ba1du", "baidü", "baіdu"),
        "jd" to listOf("jд", "jdсom"),
        "taobao" to listOf("ta0bao", "tao-bao"),
        "pinduoduo" to listOf("p1nduoduo", "pinduo-duo")
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

        // HD-02: Check all labels for Punycode, not just prefix
        if (hasSuspiciousPunycode(domain)) {
            val punycode = try { IDN.toASCII(domain) } catch (_: Exception) { domain } // HD-05
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
        // HD-06: Use maxOfOrNull to handle empty collections
        return pinyinHomophones.maxOfOrNull { (_, variants) ->
            variants.maxOfOrNull { variant ->
                fuzzyRatio(variant, lowerDomain) / 100f
            } ?: 0f
        } ?: 0f
    }

    private fun fuzzyRatio(a: String, b: String): Int {
        val distance = levenshteinDistance(a, b)
        return ((1f - distance.toFloat() / maxOf(a.length, b.length)) * 100).toInt()
    }

    // HD-02: Check all labels in the domain for suspicious Punycode
    private fun hasSuspiciousPunycode(domain: String): Boolean {
        val labels = domain.split(".")
        for (label in labels) {
            if (!label.startsWith("xn--")) continue
            val unicode = try {
                IDN.toUnicode(label)
            } catch (_: Exception) {
                return true // HD-04: Malformed Punycode is suspicious
            }
            if (unicode == label) continue
            val normalized = unicode.map { homographMap[it] ?: it }.joinToString("")
            if (normalized != unicode) return true
        }
        return false
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
