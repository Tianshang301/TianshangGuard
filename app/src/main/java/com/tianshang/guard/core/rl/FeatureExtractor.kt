package com.tianshang.guard.core.rl

class FeatureExtractor {

    private val financialKeywords = listOf(
        "银行", "转账", "密码", "账户", "资金", "贷款", "信用卡", "借记卡",
        "余额", "提现", "充值", "支付", "理财", "投资", "收益", "分红"
    )

    private val urgencyKeywords = listOf(
        "紧急", "立即", "马上", "尽快", "限时", "截止", "逾期", "过期",
        "失效", "冻结", "锁定", "异常", "风险", "安全", "警告"
    )

    private val threatKeywords = listOf(
        "涉案", "传票", "拘留", "逮捕", "违法", "犯罪", "洗钱", "诈骗",
        "公安", "法院", "检察院", "纪委", "监察", "通缉", "协查"
    )

    private val moneySymbols = listOf("$", "€", "£", "¥", "₹", "₽", "₩")
    private val emojiPattern = Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F1E0}-\\x{1F1FF}\\x{2702}-\\x{27B0}\\x{24C2}-\\x{1F251}]")
    private val ipPattern = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val urlPattern = Regex("https?://[^\\s]+")

    fun extractFeatures(text: String): FeatureVector {
        val textLength = text.length
        val urlCount = countUrls(text)
        val hasPhoneNumber = containsPhoneNumber(text)
        val hasFinancialKeywords = containsKeywords(text, financialKeywords)
        val hasUrgencyKeywords = containsKeywords(text, urgencyKeywords)
        val hasThreatKeywords = containsKeywords(text, threatKeywords)
        val senderType = detectSenderType(text)
        val language = detectLanguage(text)

        // New features
        val specialCharRatio = calculateSpecialCharRatio(text)
        val digitRatio = calculateDigitRatio(text)
        val avgWordLength = calculateAvgWordLength(text)
        val exclamationCount = text.count { it == '!' || it == '！' }
        val questionCount = text.count { it == '?' || it == '？' }
        val uppercaseRatio = calculateUppercaseRatio(text)
        val repeatedCharCount = calculateRepeatedCharCount(text)
        val linkCount = urlPattern.findAll(text).count()
        val ipAddressCount = ipPattern.findAll(text).count()
        val chineseCharRatio = calculateChineseCharRatio(text)
        val emojiCount = emojiPattern.findAll(text).count()
        val moneySymbolCount = moneySymbols.sumOf { symbol -> text.count { it.toString() == symbol } }
        val urgencyWordCount = urgencyKeywords.count { text.contains(it) }
        val threatWordCount = threatKeywords.count { text.contains(it) }
        val financialWordCount = financialKeywords.count { text.contains(it) }
        val sentimentScore = calculateSentimentScore(text)

        return FeatureVector(
            textLength = textLength,
            urlCount = urlCount,
            hasPhoneNumber = hasPhoneNumber,
            hasFinancialKeywords = hasFinancialKeywords,
            hasUrgencyKeywords = hasUrgencyKeywords,
            hasThreatKeywords = hasThreatKeywords,
            senderType = senderType,
            language = language,
            specialCharRatio = specialCharRatio,
            digitRatio = digitRatio,
            avgWordLength = avgWordLength,
            exclamationCount = exclamationCount,
            questionCount = questionCount,
            uppercaseRatio = uppercaseRatio,
            repeatedCharCount = repeatedCharCount,
            linkCount = linkCount,
            ipAddressCount = ipAddressCount,
            chineseCharRatio = chineseCharRatio,
            emojiCount = emojiCount,
            moneySymbolCount = moneySymbolCount,
            urgencyWordCount = urgencyWordCount,
            threatWordCount = threatWordCount,
            financialWordCount = financialWordCount,
            sentimentScore = sentimentScore
        )
    }

    private fun countUrls(text: String): Int {
        val urlPattern = Regex("https?://[^\\s]+")
        return urlPattern.findAll(text).count()
    }

    private fun containsPhoneNumber(text: String): Boolean {
        val phonePattern = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
        return phonePattern.containsMatchIn(text)
    }

    private fun containsKeywords(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun detectSenderType(text: String): FeatureVector.SenderType {
        val firstLine = text.lines().firstOrNull()?.trim() ?: return FeatureVector.SenderType.UNKNOWN
        return when {
            Regex("^1[3-9]\\d{9}$").matches(firstLine) -> FeatureVector.SenderType.PHONE_NUMBER
            Regex("^\\d{3,6}$").matches(firstLine) -> FeatureVector.SenderType.SHORT_CODE
            Regex("^[A-Za-z]{3,11}$").matches(firstLine) -> FeatureVector.SenderType.ALPHABETIC
            else -> FeatureVector.SenderType.UNKNOWN
        }
    }

    private fun detectLanguage(text: String): FeatureVector.Language {
        val hasChinese = text.any { it in '\u4E00'..'\u9FFF' }
        return if (hasChinese) FeatureVector.Language.CHINESE else FeatureVector.Language.ENGLISH
    }

    private fun calculateSpecialCharRatio(text: String): Float {
        if (text.isEmpty()) return 0f
        val specialChars = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        return specialChars.toFloat() / text.length
    }

    private fun calculateDigitRatio(text: String): Float {
        if (text.isEmpty()) return 0f
        return text.count { it.isDigit() }.toFloat() / text.length
    }

    private fun calculateAvgWordLength(text: String): Float {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0f
        return words.sumOf { it.length }.toFloat() / words.size
    }

    private fun calculateUppercaseRatio(text: String): Float {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        return letters.count { it.isUpperCase() }.toFloat() / letters.length
    }

    private fun calculateRepeatedCharCount(text: String): Int {
        if (text.length < 2) return 0
        var count = 0
        for (i in 1 until text.length) {
            if (text[i] == text[i - 1]) count++
        }
        return count
    }

    private fun calculateChineseCharRatio(text: String): Float {
        if (text.isEmpty()) return 0f
        return text.count { it in '\u4E00'..'\u9FFF' }.toFloat() / text.length
    }

    private fun calculateSentimentScore(text: String): Float {
        // Simple rule-based sentiment (0 = negative, 1 = positive)
        val negativeWords = listOf("危险", "警告", "威胁", "风险", "诈骗", "违法", "犯罪", "冻结", "锁定")
        val positiveWords = listOf("安全", "保护", "正常", "成功", "已验证", "已确认")
        
        val negCount = negativeWords.count { text.contains(it) }
        val posCount = positiveWords.count { text.contains(it) }
        
        return when {
            negCount + posCount == 0 -> 0.5f  // Neutral
            else -> posCount.toFloat() / (negCount + posCount)
        }
    }
}
