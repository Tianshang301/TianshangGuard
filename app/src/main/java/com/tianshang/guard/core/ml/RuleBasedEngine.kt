package com.tianshang.guard.core.ml

import android.content.Context
import com.tianshang.guard.core.util.SecureLog
import org.json.JSONArray

class RuleBasedEngine(private val context: Context) : MlEngine {

    private var phishingKeywords: List<String> = emptyList()
    private var smsPhishingKeywords: List<String> = emptyList()
    private var loaded = false

    init {
        loadKeywords()
    }

    private fun loadKeywords() {
        try {
            phishingKeywords = loadKeywordList("rules/keywords_web.json")
            smsPhishingKeywords = loadKeywordList("rules/keywords_sms.json")
            if (phishingKeywords.isEmpty() && smsPhishingKeywords.isEmpty()) {
                SecureLog.w("RuleBasedEngine", "All keyword lists empty, falling back to defaults")
                loadDefaults()
            }
            loaded = true
            SecureLog.i("RuleBasedEngine", "Keywords loaded: web=${phishingKeywords.size}, sms=${smsPhishingKeywords.size}")
        } catch (e: Exception) {
            SecureLog.e("RuleBasedEngine", "Failed to load keywords, using defaults", e)
            loadDefaults()
            loaded = true
        }
    }

    private fun loadKeywordList(path: String): List<String> {
        return try {
            val json = context.assets.open(path).bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            SecureLog.w("RuleBasedEngine", "Failed to load $path, using empty list")
            emptyList()
        }
    }

    private fun loadDefaults() {
        phishingKeywords = listOf(
            "安全账户", "屏幕共享", "涉嫌洗钱", "验证身份",
            "银行卡密码", "转账到", "退款链接", "点击验证"
        )

        smsPhishingKeywords = listOf(
            "安全账户", "涉嫌洗钱", "验证身份", "银行卡密码",
            "转账到", "退款链接", "点击验证", "冻结账户",
            "法院传票", "包裹异常", "积分兑换", "ETC失效",
            "社保异常", "医保异常", "税务异常", "中奖通知",
            "大奖", "免费领取", "点击链接", "立即验证",
            "异常登录", "即将停用", "被封", "补交",
            "保证金", "手续费", "公安", "通缉",
            "暂停使用", "停机", "贷款审批", "信用额度",
            "逾期未还", "公安协查", "涉案调查", "配合调查",
            "资金清查", "安全认证", "身份过期", "账户异常",
            "登录异常", "密码重置", "领取补贴", "退税通知",
            "航班取消", "学费退费", "手机掉水里", "培训费",
            "缴费", "激活账户", "重新激活"
        )
        loaded = true
    }

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
        val matchCount = smsPhishingKeywords.count { text.contains(it) }
        return when {
            matchCount >= 3 -> RiskLevel.DANGEROUS
            matchCount >= 1 -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {}
    override fun isModelLoaded(type: ModelType): Boolean = loaded
}
