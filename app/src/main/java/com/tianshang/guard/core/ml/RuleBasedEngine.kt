package com.tianshang.guard.core.ml

class RuleBasedEngine : MlEngine {

    private val phishingKeywords = listOf(
        "安全账户", "屏幕共享", "涉嫌洗钱", "验证身份",
        "银行卡密码", "转账到", "退款链接", "点击验证"
    )

    private val smsPhishingKeywords = listOf(
        "安全账户", "涉嫌洗钱", "验证身份", "银行卡密码",
        "转账到", "退款链接", "点击验证", "冻结账户",
        "法院传票", "包裹异常", "积分兑换", "ETC失效",
        "社保异常", "医保异常", "税务异常", "中奖通知",
        "贷款审批", "信用额度", "逾期未还", "公安协查",
        "涉案调查", "配合调查", "资金清查", "安全认证",
        "身份过期", "账户异常", "登录异常", "密码重置",
        "领取补贴", "退税通知", "航班取消", "学费退费"
    )

    private val smsPhishingKeywordsJapanese = listOf(
        "口座", "振込", "詐欺", "不正アクセス", "本人確認",
        "パスワード", "ワンタイムパスワード", "クリック",
        "緊急", "アカウント停止", "補償", "還元", "当選",
        "未納", "督促", "税務署", "警察", "裁判所",
        "SMS認証", "ログイン", "退会手続き", "配送業者",
        "تصر明", "تصر明手続き", "تصر明金", "تصر明料",
        "当選金", "当選通知", "تصر明完了", "تصر明確認"
    )

    override fun analyzeWebPage(text: String): RiskLevel {
        val matchCount = phishingKeywords.count { text.contains(it) }
        return when {
            matchCount >= 3 -> RiskLevel.DANGEROUS
            matchCount >= 1 -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun analyzeDomain(domain: String): RiskLevel {
        return RiskLevel.SAFE
    }

    override fun analyzeSms(text: String): RiskLevel {
        val hasHiragana = text.any { it in '\u3040'..'\u309F' }
        val hasKatakana = text.any { it in '\u30A0'..'\u30FF' }
        val isJapanese = hasHiragana || hasKatakana

        val keywords = if (isJapanese) smsPhishingKeywordsJapanese else smsPhishingKeywords
        val matchCount = keywords.count { text.contains(it) }
        return when {
            matchCount >= 3 -> RiskLevel.DANGEROUS
            matchCount >= 1 -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {}
    override fun isModelLoaded(type: ModelType): Boolean = true
}
