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

    fun extractFeatures(text: String): FeatureVector {
        val textLength = text.length
        val urlCount = countUrls(text)
        val hasPhoneNumber = containsPhoneNumber(text)
        val hasFinancialKeywords = containsKeywords(text, financialKeywords)
        val hasUrgencyKeywords = containsKeywords(text, urgencyKeywords)
        val hasThreatKeywords = containsKeywords(text, threatKeywords)
        val senderType = detectSenderType(text)
        val language = detectLanguage(text)

        return FeatureVector(
            textLength = textLength,
            urlCount = urlCount,
            hasPhoneNumber = hasPhoneNumber,
            hasFinancialKeywords = hasFinancialKeywords,
            hasUrgencyKeywords = hasUrgencyKeywords,
            hasThreatKeywords = hasThreatKeywords,
            senderType = senderType,
            language = language
        )
    }

    private fun countUrls(text: String): Int {
        val urlPattern = Regex("https?://[^\\s]+")
        return urlPattern.findAll(text).count()
    }

    private fun containsPhoneNumber(text: String): Boolean {
        val phonePattern = Regex("1[3-9]\\d{9}")
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
}
