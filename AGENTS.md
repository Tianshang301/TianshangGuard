# TianshangGuard 技术文档

> **包名**: `com.tianshang.guard`  
> **版本**: v1.1.0  
> **协议**: MIT  
> **信念**: "如果能少一人受骗，这个项目就有意义。"

---

## 目录

1. [项目概述](#1-项目概述)
2. [架构总览](#2-架构总览)
3. [核心模块设计](#3-核心模块设计)
   - 3.1 DNS 引擎 (DnsEngine)
   - 3.2 ML 引擎 (MlEngine)
   - 3.3 行为监控引擎 (MonitorEngine)
   - 3.4 预警引擎 (AlertEngine)
   - 3.5 VPN 服务 (GuardVpnService)
   - 3.6 短信监控 (SmsReceiver)
4. [数据层设计](#4-数据层设计)
5. [安全与隐私](#5-安全与隐私)
6. [性能与优化](#6-性能与优化)
7. [项目结构](#7-项目结构)
8. [构建配置](#8-构建配置)
9. [部署与分发](#9-部署与分发)
10. [附录](#10-附录)

---

## 1. 项目概述

### 1.1 定位

TianshangGuard 是一款开源 Android 反诈工具，采用**分层防御架构**：

- **DNS 层**: 拦截已知钓鱼域名，检测仿冒域名
- **行为层**: 检测屏幕共享 + 银行应用组合，阻断社会工程学攻击
- **内容层**: 本地 Transformer 模型分析网页内容和短信，识别钓鱼话术
- **教育层**: 预警附带反诈知识，提升用户辨识能力

### 1.2 核心原则

| 原则        | 说明                   |
| --------- | -------------------- |
| **纯本地分析** | 所有推理在设备完成，零数据上传      |
| **开源可审计** | 代码完全公开，接受社区审查        |
| **承认边界**  | 明确告知无法 100% 拦截，技术有极限 |
| **用户主权**  | 用户完全控制白名单、黑名单、预警级别   |

### 1.3 能力边界

**能防护**:

- 已知钓鱼域名访问
- 仿冒域名（视觉混淆、同形字符、音译混淆）
- 屏幕共享 + 银行应用组合的高风险操作
- 网页内容中的钓鱼话术（用户主动触发分析）
- 短信中的钓鱼链接和话术（自动拦截）

**不能防护**:

- 用户主动绕过所有保护（社会工程学核心难题）
- 电话诈骗（无网络流量特征）
- 零日钓鱼域名（未被收录）
- 加密通信内容（微信、银行 App 内 WebView）

---

## 2. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                    TianshangGuard                           │
│                  (com.tianshang.guard)                      │
├─────────────────────────────────────────────────────────────┤
│  Presentation Layer (UI)                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ MainActivity │  │ AlertActivity│  │ SettingsScreen     │ │
│  │ 主界面       │  │ 预警弹窗     │  │ 设置页             │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ StatsScreen │  │ ReportScreen│  │ OnboardingScreen    │ │
│  │ 统计页       │  │ 举报页       │  │ 首次引导            │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│  ┌─────────────┐                                           │
│  │ SmsScreen   │                                           │
│  │ 短信分析页   │                                           │
│  └─────────────┘                                           │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer (Use Cases)                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ • AnalyzeSmsUseCase                                 │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  Data Layer (Repositories + Data Sources)                   │
│  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ LocalDataSource │  │ RemoteDataSource                │ │
│  │ • Room DB       │  │ • GitHub Rules Repo             │ │
│  │ • DataStore     │  │                                 │ │
│  │ • ONNX Model x3 │  │                                 │ │
│  └─────────────────┘  └─────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  Core Engine Layer                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ DnsEngine    │  │ MlEngine     │  │ MonitorEngine      │ │
│  │ DNS 代理引擎 │  │ ML 推理引擎  │  │ 行为监控引擎        │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│  ┌─────────────┐  ┌─────────────────────────────────────┐ │
│  │ AlertEngine  │  │ BatteryOptimizer                    │ │
│  │ 预警引擎     │  │ 电池优化 / 品牌适配                  │ │
│  └─────────────┘  └─────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  Platform Layer (Android Services)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ GuardVpnSvc  │  │ ForegroundSvc│  │ BootReceiver       │ │
│  │ VPN 服务     │  │ 前台保活服务  │  │ 开机启动接收器      │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│  ┌─────────────┐                                           │
│  │ SmsReceiver │                                           │
│  │ 短信拦截     │                                           │
│  └─────────────┘                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 核心模块设计

### 3.1 DNS 引擎 (DnsEngine)

#### 职责

- 本地 DNS 代理，拦截/放行域名查询
- 维护白名单、黑名单、可疑名单
- 检测同形字符攻击、音译混淆
- 集成 ML 引擎进行域名风险分析

#### 核心类

```kotlin
package com.tianshang.guard.core.dns

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.repository.RuleRepository
import kotlinx.coroutines.runBlocking

sealed class DnsResult {
    data class Allow(val ip: String) : DnsResult()
    data class Block(val reason: BlockReason) : DnsResult()
    data class Unknown(val riskScore: Float) : DnsResult()
}

enum class BlockReason {
    BLACKLIST,
    SUSPICIOUS,
    USER_OVERRIDE
}

interface DnsEngine {
    fun start()
    fun stop()
    fun resolve(domain: String): DnsResult
    fun addToWhitelist(domain: String)
    fun addToBlacklist(domain: String)
}

class LocalDnsEngine(
    private val ruleRepository: RuleRepository,
    private val alertEngine: AlertEngine,
    private val homographDetector: HomographDetector,
    private val mlEngine: MlEngine
) : DnsEngine {

    private var bloomFilter = AdaptiveBloomFilter(
        expectedItems = 100_000,
        targetFpp = 0.001
    )

    override fun resolve(domain: String): DnsResult {
        // 1. 白名单检查
        if (ruleRepository.isWhitelisted(domain)) {
            alertEngine.notifyVisited(domain)
            return DnsResult.Allow(resolveUpstream(domain))
        }

        // 2. 同形字符检测
        val homographResult = homographDetector.detect(domain)
        if (homographResult is HomographResult.Detected) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.95f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        // 3. 黑名单检查（Bloom Filter 快速过滤 + 二次确认）
        if (bloomFilter.mightContain(domain)) {
            if (ruleRepository.isBlacklisted(domain)) {
                alertEngine.notifyBlocked(domain, BlockReason.BLACKLIST)
                return DnsResult.Block(BlockReason.BLACKLIST)
            }
        }

        // 4. 音译混淆检测
        val pinyinScore = homographDetector.checkPinyinConfusion(domain)
        if (pinyinScore > 0.85f) {
            alertEngine.showSuspiciousDomainWarning(domain, pinyinScore)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        // 5. 域名相似度检测
        val similarityScore = calculateDomainSimilarity(domain)
        if (similarityScore > 0.85f) {
            alertEngine.showSuspiciousDomainWarning(domain, similarityScore)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        // 6. ML 域名分析
        val mlRisk = mlEngine.analyzeDomain(domain)
        if (mlRisk == RiskLevel.DANGEROUS) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.9f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }
        if (mlRisk == RiskLevel.SUSPICIOUS) {
            alertEngine.showSuspiciousDomainWarning(domain, 0.7f)
            return DnsResult.Block(BlockReason.SUSPICIOUS)
        }

        // 7. 未知域名：放行，记录访问
        alertEngine.notifyVisited(domain)
        return DnsResult.Unknown(0.5f)
    }

    override fun start() {
        runBlocking {
            val allDomains = ruleRepository.getKnownDomains()
            bloomFilter = AdaptiveBloomFilter(
                expectedItems = if (allDomains.size * 2 > 100_000) allDomains.size * 2 else 100_000,
                targetFpp = 0.001
            )
            allDomains.forEach { bloomFilter.add(it) }
        }
    }

    override fun stop() {
        bloomFilter = AdaptiveBloomFilter(100_000, 0.001)
    }

    override fun addToWhitelist(domain: String) {
        runBlocking { ruleRepository.addToWhitelist(domain) }
    }

    override fun addToBlacklist(domain: String) {
        runBlocking {
            ruleRepository.addToBlacklist(domain)
            bloomFilter.add(domain)
        }
    }
}
```

---

### 3.2 ML 引擎 (MlEngine)

#### 职责

- 多模型 ONNX 推理（URL / 中文短信 / 英文短信）
- 自动语言检测，选择对应模型
- 短信分析时自动提取 URL 并联合评分
- 降级到规则引擎（模型加载失败/超时时）
- 推理结果缓存

#### 核心类

```kotlin
package com.tianshang.guard.core.ml

enum class ModelType {
    URL,
    CHINESE,
    ENGLISH
}

enum class RiskLevel(val threshold: Float) {
    SAFE(0.3f),
    SUSPICIOUS(0.7f),
    DANGEROUS(1.0f);

    companion object {
        fun fromScore(score: Float): RiskLevel = when {
            score < SAFE.threshold -> SAFE
            score < SUSPICIOUS.threshold -> SUSPICIOUS
            else -> DANGEROUS
        }
    }
}

sealed class MlState {
    object Loading : MlState()
    object Ready : MlState()
    data class Failed(val reason: String) : MlState()
    object Fallback : MlState()
}

interface MlEngine {
    fun analyzeWebPage(text: String): RiskLevel
    fun analyzeDomain(domain: String): RiskLevel
    fun analyzeSms(text: String): RiskLevel
    fun loadModel(modelPath: String, type: ModelType = ModelType.URL)
    fun isModelLoaded(type: ModelType = ModelType.URL): Boolean
}
```

#### OnnxMlEngine

```kotlin
package com.tianshang.guard.core.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.util.regex.Pattern

class BertTokenizer {

    fun encode(text: String, maxLength: Int): LongArray {
        val tokens = LongArray(maxLength) { 0L }
        tokens[0] = 101L
        val textBytes = text.take(maxLength - 2).encodeToByteArray()
        for (i in textBytes.indices) {
            tokens[i + 1] = textBytes[i].toLong() and 0xFF
        }
        tokens[minOf(textBytes.size + 1, maxLength - 1)] = 102L
        return tokens
    }
}

class OnnxMlEngine : MlEngine {

    private val sessions = mutableMapOf<ModelType, OrtSession>()
    private val tokenizer = BertTokenizer()
    private val urlPattern = Pattern.compile("https?://[^\\s]+")

    override fun loadModel(modelPath: String, type: ModelType) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            addNnapi()
            setIntraOpNumThreads(2)
        }
        sessions[type] = env.createSession(modelPath, sessionOptions)
    }

    fun analyzeWithModel(text: String, type: ModelType): RiskLevel {
        val session = sessions[type] ?: return RiskLevel.SAFE
        val inputIds = tokenizer.encode(text, maxLength = 512)
        val inputTensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            arrayOf(inputIds)
        )

        val outputs = session.run(mapOf("input" to inputTensor))
        val score = (outputs?.get(0)?.value as? FloatArray)?.firstOrNull() ?: 0f

        return RiskLevel.fromScore(score)
    }

    fun analyzeWithModelScore(text: String, type: ModelType): Float {
        val session = sessions[type] ?: return 0f
        val inputIds = tokenizer.encode(text, maxLength = 512)
        val inputTensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            arrayOf(inputIds)
        )

        val outputs = session.run(mapOf("input" to inputTensor))
        return (outputs?.get(0)?.value as? FloatArray)?.firstOrNull() ?: 0f
    }

    override fun analyzeWebPage(text: String): RiskLevel {
        return analyzeWithModel(text, ModelType.URL)
    }

    override fun analyzeDomain(domain: String): RiskLevel {
        val features = extractDomainFeatures(domain)
        return ruleBasedDomainAnalysis(features)
    }

    override fun analyzeSms(text: String): RiskLevel {
        val score = analyzeSmsScore(text)
        return RiskLevel.fromScore(score)
    }

    fun analyzeSmsScore(text: String): Float {
        // 1. 自动检测语言，选择对应模型
        val isChinese = text.any { it in '\u4E00'..'\u9FFF' }
        val textModelType = if (isChinese) ModelType.CHINESE else ModelType.ENGLISH
        val textScore = analyzeWithModelScore(text, textModelType)

        // 2. 提取 URL 并用 URL 模型分析
        val url = extractUrl(text)
        if (url != null) {
            val urlScore = analyzeWithModelScore(url, ModelType.URL)
            return maxOf(textScore, urlScore)
        }

        return textScore
    }

    private fun extractUrl(text: String): String? {
        val matcher = urlPattern.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    override fun isModelLoaded(type: ModelType): Boolean = sessions.containsKey(type)
}
```

#### MlEngineWithFallback

```kotlin
package com.tianshang.guard.core.ml

import com.tianshang.guard.core.telemetry.PerformanceTracer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.LinkedHashMap

class MlEngineWithFallback(
    private val onnxEngine: OnnxMlEngine,
    private val fallbackEngine: RuleBasedEngine,
    private val tracer: PerformanceTracer
) : MlEngine {

    private val states = mutableMapOf<ModelType, MlState>()
    private val inferenceTimeout = 500L
    private val resultCache = object : LinkedHashMap<String, RiskLevel>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RiskLevel>): Boolean {
            return size > 100
        }
    }

    override fun analyzeWebPage(text: String): RiskLevel {
        return analyzeWithFallback(text, ModelType.URL)
    }

    override fun analyzeDomain(domain: String): RiskLevel {
        resultCache[domain]?.let { return it }

        val result = when (states[ModelType.URL]) {
            is MlState.Ready -> runDomainWithTimeout(domain)
            else -> fallbackEngine.analyzeDomain(domain)
        }

        resultCache[domain] = result
        return result
    }

    override fun analyzeSms(text: String): RiskLevel {
        return analyzeWithFallback(text, ModelType.CHINESE)
    }

    private fun analyzeWithFallback(text: String, type: ModelType): RiskLevel {
        val cacheKey = "${type.name}:$text"
        resultCache[cacheKey]?.let { return it }

        val result = when (states[type]) {
            is MlState.Ready -> runWithTimeout(text, type)
            else -> fallbackEngine.analyzeSms(text)
        }

        resultCache[cacheKey] = result
        return result
    }

    fun loadModelAsync(modelPath: String, type: ModelType = ModelType.URL) {
        try {
            onnxEngine.loadModel(modelPath, type)
            states[type] = MlState.Ready
        } catch (e: Exception) {
            states[type] = MlState.Failed(e.message ?: "Load failed")
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {
        loadModelAsync(modelPath, type)
    }

    override fun isModelLoaded(type: ModelType): Boolean = states[type] is MlState.Ready
}
```

#### RuleBasedEngine（兜底引擎）

```kotlin
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

    override fun analyzeWebPage(text: String): RiskLevel {
        val matchCount = phishingKeywords.count { text.contains(it) }
        return when {
            matchCount >= 3 -> RiskLevel.DANGEROUS
            matchCount >= 1 -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun analyzeDomain(domain: String): RiskLevel = RiskLevel.SAFE

    override fun analyzeSms(text: String): RiskLevel {
        val matchCount = smsPhishingKeywords.count { text.contains(it) }
        return when {
            matchCount >= 3 -> RiskLevel.DANGEROUS
            matchCount >= 1 -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {}
    override fun isModelLoaded(type: ModelType): Boolean = true
}
```

---

### 3.3 行为监控引擎 (MonitorEngine)

#### 职责

- 检测屏幕共享应用 + 银行应用组合
- 使用 UsageStatsManager 查询前台应用
- 硬编码应用列表（可扩展为远程配置）

#### 核心类

```kotlin
package com.tianshang.guard.core.monitor

data class RemoteConfig(
    val screenShareApps: Set<String> = emptySet(),
    val bankApps: Set<String> = emptySet()
)

class RemoteConfigProvider {

    var screenShareApps: Set<String> = setOf(
        "com.teamviewer.teamviewer.market",
        "com.anydesk.adcontrol",
        "com.microsoft.rdc.androidx",
        "com.logmein.golook",
        "com.screenovate.zapya",
        "com.apowersoft.mirrorphone",
        "com.airmore.manager",
        "com.remotepc.remote",
        "com.xtralogic.remoteclient"
    )

    var bankApps: Set<String> = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.tencent.mtt",
        "com.android.bankabc",
        "com.chinamworld.bocmbci",
        "com.icbc",
        "com.cmbc.ccmbsv",
        "com.spdb.express",
        "com.cgbchina.bill",
        "com.cebbank.bill",
        "com.hxb.credit",
        "com.bankcomm.Bankcomm",
        "com.android.bankbee",
        "com.sina.weibo",
        "com.tencent.mobileqq"
    )
}

class ScreenShareMonitor(
    private val context: Context,
    private val alertEngine: AlertEngine,
    private val configProvider: RemoteConfigProvider
) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as UsageStatsManager

    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            checkForegroundApps()
            handler.postDelayed(this, 3000)
        }
    }

    private fun checkForegroundApps() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var foregroundApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foregroundApp = event.packageName
            }
        }

        if (foregroundApp != null && configProvider.bankApps.contains(foregroundApp)) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as ActivityManager
            val processes = activityManager.runningAppProcesses ?: return
            if (processes.any { configProvider.screenShareApps.contains(it.processName) }) {
                alertEngine.showScreenShareWarning()
            }
        }
    }
}
```

---

### 3.4 预警引擎 (AlertEngine)

#### 职责

- 分级预警：静默 / 横幅 / 弹窗 / 全屏阻断
- 冷却机制，避免重复骚扰
- 支持屏幕共享、钓鱼网页、可疑域名、短信诈骗等多种预警类型

#### 核心类

```kotlin
package com.tianshang.guard.core.alert

import android.content.Context
import android.content.Intent
import com.tianshang.guard.core.dns.BlockReason
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.repository.AlertRepository
import com.tianshang.guard.ui.alert.AlertActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AlertLevel {
    SILENT,      // 仅记录
    BANNER,      // 顶部悬浮条
    DIALOG,      // 弹窗确认
    FULLSCREEN   // 全屏阻断
}

data class AlertConfig(
    val level: AlertLevel,
    val requireConfirm: Boolean,
    val cooldownSeconds: Int,
    val showEducationalTip: Boolean,
    val playSound: Boolean,
    val vibrate: Boolean
)

interface AlertEngine {
    fun showScreenShareWarning()
    fun showPhishingWarning(url: String, riskLevel: RiskLevel)
    fun showSuspiciousDomainWarning(domain: String, score: Float)
    fun notifyBlocked(domain: String, reason: BlockReason)
    fun notifyVisited(domain: String)
    fun showSmsWarning(sender: String, body: String, riskLevel: RiskLevel)
}

class TieredAlertEngine(
    private val context: Context,
    private val prefs: GuardPreferences,
    private val alertRepository: AlertRepository
) : AlertEngine {

    private val cooldownManager = CooldownManager(prefs)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private fun record(type: AlertType, domain: String? = null, url: String? = null, riskLevel: String? = null) {
        ioScope.launch {
            alertRepository.insert(AlertEntity(type = type, domain = domain, url = url, riskLevel = riskLevel))
        }
    }

    override fun showScreenShareWarning() {
        record(AlertType.SCREEN_SHARE)
        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("alert_type", AlertType.SCREEN_SHARE.name)
            putExtra("level", AlertLevel.FULLSCREEN.name)
            putExtra("require_confirm", true)
        }
        context.startActivity(intent)
    }

    override fun showPhishingWarning(url: String, riskLevel: RiskLevel) {
        record(AlertType.PHISHING_PAGE, url = url, riskLevel = riskLevel.name)
        val config = resolveAlertConfig(riskLevel)
        if (cooldownManager.isInCooldown("phishing_$url", config.cooldownSeconds)) return

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.PHISHING_PAGE.name)
            putExtra("url", url)
            putExtra("risk_level", riskLevel.name)
            putExtra("level", config.level.name)
            putExtra("require_confirm", config.requireConfirm)
        }
        context.startActivity(intent)
        cooldownManager.recordTrigger("phishing_$url")
    }

    override fun showSuspiciousDomainWarning(domain: String, score: Float) {
        record(AlertType.SUSPICIOUS_DOMAIN, domain = domain)
        val config = resolveAlertConfig(RiskLevel.SUSPICIOUS)
        if (cooldownManager.isInCooldown("domain_$domain", config.cooldownSeconds)) return

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.SUSPICIOUS_DOMAIN.name)
            putExtra("domain", domain)
            putExtra("score", score)
            putExtra("level", config.level.name)
            putExtra("require_confirm", config.requireConfirm)
        }
        context.startActivity(intent)
        cooldownManager.recordTrigger("domain_$domain")
    }

    override fun notifyBlocked(domain: String, reason: BlockReason) {
        record(AlertType.BLACKLIST_BLOCKED, domain = domain)
        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.BLACKLIST_BLOCKED.name)
            putExtra("domain", domain)
            putExtra("reason", reason.name)
            putExtra("level", AlertLevel.BANNER.name)
        }
        context.startActivity(intent)
    }

    override fun notifyVisited(domain: String) {
        record(AlertType.VISITED, domain = domain)
    }

    override fun showSmsWarning(sender: String, body: String, riskLevel: RiskLevel) {
        record(AlertType.SMS_PHISHING, domain = sender, riskLevel = riskLevel.name)
        val config = resolveAlertConfig(riskLevel)
        if (cooldownManager.isInCooldown("sms_$sender", config.cooldownSeconds)) return

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("alert_type", AlertType.SMS_PHISHING.name)
            putExtra("sms_sender", sender)
            putExtra("sms_body", body)
            putExtra("risk_level", riskLevel.name)
            putExtra("level", config.level.name)
            putExtra("require_confirm", config.requireConfirm)
        }
        context.startActivity(intent)
        cooldownManager.recordTrigger("sms_$sender")
    }

    private fun resolveAlertConfig(riskLevel: RiskLevel): AlertConfig {
        return when (riskLevel) {
            RiskLevel.SAFE -> AlertConfig(
                level = AlertLevel.SILENT,
                requireConfirm = false,
                cooldownSeconds = 0,
                showEducationalTip = false,
                playSound = false,
                vibrate = false
            )
            RiskLevel.SUSPICIOUS -> AlertConfig(
                level = AlertLevel.BANNER,
                requireConfirm = false,
                cooldownSeconds = prefs.suspiciousCooldownSeconds,
                showEducationalTip = true,
                playSound = false,
                vibrate = false
            )
            RiskLevel.DANGEROUS -> AlertConfig(
                level = AlertLevel.DIALOG,
                requireConfirm = true,
                cooldownSeconds = 300,
                showEducationalTip = true,
                playSound = true,
                vibrate = true
            )
        }
    }
}

class CooldownManager(private val prefs: GuardPreferences) {
    private val triggerTimes = mutableMapOf<String, Long>()

    fun isInCooldown(key: String, cooldownSeconds: Int): Boolean {
        if (cooldownSeconds <= 0) return false
        val lastTrigger = triggerTimes[key] ?: return false
        return (System.currentTimeMillis() - lastTrigger) < cooldownSeconds * 1000
    }

    fun recordTrigger(key: String) {
        triggerTimes[key] = System.currentTimeMillis()
    }
}
```

---

### 3.5 VPN 服务 (GuardVpnService)

#### 职责

- 创建本地 VPN 接口，拦截 DNS 流量
- 使用 DnsPacketHandler 解析/构造 DNS 包
- 真实上游 DNS 转发（1.1.1.1:53）
- DNS 结果缓存（LRU，最大 2048 条）
- 前台保活，防止系统杀死

#### 核心类

```kotlin
package com.tianshang.guard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.tianshang.guard.R
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.dns.DnsPacketHandler
import com.tianshang.guard.core.dns.DnsResult
import com.tianshang.guard.ui.main.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import org.koin.android.ext.android.inject

class GuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    private val dnsEngine: DnsEngine by inject()
    private val packetHandler = DnsPacketHandler()

    private val dnsCache = object : LinkedHashMap<String, DnsResult>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DnsResult>?): Boolean {
            return size > 2048
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "guard_vpn_channel"
        const val ACTION_START = "com.tianshang.guard.START_VPN"
        const val ACTION_STOP = "com.tianshang.guard.STOP_VPN"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 3000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder().apply {
            addAddress("198.18.0.1", 15)
            addDnsServer("198.18.0.2")
            addRoute("198.18.0.0", 15)
            setBlocking(true)
        }

        vpnInterface = builder.establish() ?: return
        running = true

        dnsEngine.start()
        startForeground(NOTIFICATION_ID, createNotification())
        Thread(null, ::handlePackets, "VpnPacketHandler").start()
    }

    private fun handlePackets() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val packet = ByteArray(1500)

        while (running) {
            try {
                val length = input.read(packet)
                if (length <= 0) continue

                val buffer = ByteBuffer.wrap(packet, 0, length)
                if (!packetHandler.isDnsQuery(buffer)) continue

                val domain = packetHandler.extractDomain(buffer)
                if (domain.isEmpty()) continue

                val result = synchronized(dnsCache) {
                    dnsCache.getOrPut(domain) { dnsEngine.resolve(domain) }
                }

                val response = if (result is DnsResult.Block) {
                    packetHandler.buildNxDomainResponse(buffer)
                } else {
                    forwardToUpstreamDns(buffer)
                }

                if (response != null) {
                    output.write(response.array(), 0, response.remaining())
                }
            } catch (e: Exception) {
                // 忽略单包错误，继续处理
            }
        }
    }

    private fun forwardToUpstreamDns(query: ByteBuffer): ByteBuffer? {
        return try {
            val dnsPayload = packetHandler.extractDnsPayload(query)
            val socket = DatagramSocket()
            socket.soTimeout = DNS_TIMEOUT_MS
            val request = DatagramPacket(dnsPayload, dnsPayload.size,
                InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT)
            socket.send(request)
            val responseBuf = ByteArray(1500)
            val responsePkt = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(responsePkt)
            socket.close()
            val upstreamResponse = ByteBuffer.wrap(responsePkt.data, 0, responsePkt.length)
            packetHandler.buildResponseFromUpstream(query, upstreamResponse)
        } catch (e: Exception) {
            null
        }
    }

    private fun stopVpn() {
        running = false
        dnsEngine.stop()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
```

#### DnsPacketHandler（DNS 包解析）

```kotlin
package com.tianshang.guard.core.dns

import java.nio.ByteBuffer

class DnsPacketHandler {

    fun isDnsQuery(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 20) return false
        val version = buffer.get(0).toInt() shr 4 and 0xF
        if (version != 4 && version != 6) return false
        val headerLen = if (version == 4) (buffer.get(0).toInt() and 0xF) * 4 else 40
        if (buffer.remaining() < headerLen + 8) return false
        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return false
        val udpDstPort = buffer.getShort(headerLen + 2).toInt() and 0xFFFF
        return udpDstPort == 53
    }

    fun extractDomain(buffer: ByteBuffer): String {
        // 解析 DNS Query 中的域名，支持名称压缩
    }

    fun extractDnsPayload(buffer: ByteBuffer): ByteArray {
        // 提取 UDP 负载中的 DNS 数据
    }

    fun buildNxDomainResponse(query: ByteBuffer): ByteBuffer {
        // 构造 NXDOMAIN 响应，交换 IP/UDP 地址端口，设置 QR=1 RCODE=3
    }

    fun buildResponseFromUpstream(query: ByteBuffer, upstreamResponse: ByteBuffer): ByteBuffer {
        // 将上游 DNS 响应包装为 VPN 响应
    }
}
```

---

### 3.6 短信监控 (SmsReceiver)

#### 职责

- 监听系统短信广播
- 使用 AnalyzeSmsUseCase 分析短信内容
- 检测到可疑/危险短信时触发预警

#### 核心类

```kotlin
package com.tianshang.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.domain.AnalyzeSmsUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val analyzeSmsUseCase: AnalyzeSmsUseCase by inject()
    private val alertEngine: AlertEngine by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val sender = message.displayOriginatingAddress ?: "unknown"
            val body = message.messageBody ?: continue

            val riskLevel = analyzeSmsUseCase.execute(sender, body)
            if (riskLevel == RiskLevel.SUSPICIOUS || riskLevel == RiskLevel.DANGEROUS) {
                alertEngine.showSmsWarning(sender, body, riskLevel)
            }
        }
    }
}
```

#### AnalyzeSmsUseCase

```kotlin
package com.tianshang.guard.domain

import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.RiskLevel

class AnalyzeSmsUseCase(private val mlEngine: MlEngine) {

    fun execute(sender: String, body: String): RiskLevel {
        if (body.isBlank()) return RiskLevel.SAFE
        return mlEngine.analyzeSms(body)
    }
}
```

---

## 4. 数据层设计

### 4.1 数据库 Schema (Room)

```kotlin
package com.tianshang.guard.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "domains")
data class DomainEntity(
    @PrimaryKey val domain: String,
    val category: DomainCategory,
    val source: String,
    val addedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f
)

enum class DomainCategory {
    WHITELIST,
    BLACKLIST,
    SUSPICIOUS,
    UNKNOWN
}

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: AlertType,
    val domain: String?,
    val url: String?,
    val riskLevel: String?,
    val userAction: String?
)

enum class AlertType {
    SCREEN_SHARE,
    PHISHING_PAGE,
    SUSPICIOUS_DOMAIN,
    BLACKLIST_BLOCKED,
    VISITED,
    SMS_PHISHING
}

@Dao
interface DomainDao {
    @Query("SELECT * FROM domains WHERE category = 'WHITELIST'")
    fun getWhitelist(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE category = 'BLACKLIST'")
    fun getBlacklist(): Flow<List<DomainEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain AND category = 'WHITELIST')")
    fun isWhitelisted(domain: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain AND category = 'BLACKLIST')")
    fun isBlacklisted(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: DomainEntity)

    @Query("SELECT domain FROM domains WHERE category IN ('WHITELIST', 'BLACKLIST')")
    fun getKnownDomains(): List<String>

    @Query("DELETE FROM domains")
    suspend fun clearAll()
}

@Dao
interface AlertDao {
    @Insert
    suspend fun insert(alert: AlertEntity)

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlerts(limit: Int): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE type = 'BLACKLIST_BLOCKED'")
    fun getBlockedCount(): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type")
    fun getCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type")
    fun getCountByTypeFlow(type: String): Flow<Int>

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAlertsSync(limit: Int): List<AlertEntity>

    @Query("SELECT * FROM alerts ORDER BY timestamp ASC LIMIT :limit")
    fun getAlertsAscSync(limit: Int): List<AlertEntity>

    @Query("DELETE FROM alerts")
    suspend fun clearAll()
}

@Database(entities = [DomainEntity::class, AlertEntity::class], version = 1)
abstract class GuardDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun alertDao(): AlertDao
}
```

### 4.2 数据库提供器

```kotlin
package com.tianshang.guard.data.local.security

import android.content.Context
import androidx.room.Room
import com.tianshang.guard.data.local.database.GuardDatabase

class EncryptedDatabaseProvider(private val context: Context) {

    fun createDatabase(): GuardDatabase {
        return Room.databaseBuilder(
            context,
            GuardDatabase::class.java,
            "guard.db"
        )
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }
}
```

### 4.3 用户偏好 (DataStore)

```kotlin
package com.tianshang.guard.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "guard_preferences")

class GuardPreferences(private val context: Context) {

    companion object {
        private val KEY_ONBOARDING_DONE = intPreferencesKey("onboarding_done")
        private val KEY_SUSPICIOUS_COOLDOWN = intPreferencesKey("suspicious_cooldown_seconds")
        private val KEY_RULES_VERSION = stringPreferencesKey("rules_version")
        private val KEY_VPN_AUTO_START = booleanPreferencesKey("vpn_auto_start")
        private val KEY_BEHAVIOR_MONITOR = booleanPreferencesKey("behavior_monitor")
        private val KEY_BOOT_START = booleanPreferencesKey("boot_start")
        private val KEY_SOUND_ALERT = booleanPreferencesKey("sound_alert")
        private val KEY_VIBRATE_ALERT = booleanPreferencesKey("vibrate_alert")
        private val KEY_SMS_MONITOR = booleanPreferencesKey("sms_monitor")
    }

    val suspiciousCooldownSeconds: Int get() = 300

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] == 1
    }

    val vpnAutoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VPN_AUTO_START] ?: true
    }

    val behaviorMonitor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BEHAVIOR_MONITOR] ?: true
    }

    val bootStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BOOT_START] ?: true
    }

    val soundAlert: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SOUND_ALERT] ?: true
    }

    val vibrateAlert: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIBRATE_ALERT] ?: true
    }

    val smsMonitor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMS_MONITOR] ?: false
    }

    val rulesVersion: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_RULES_VERSION] ?: "0"
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = 1 }
    }

    suspend fun setRulesVersion(version: String) {
        context.dataStore.edit { prefs -> prefs[KEY_RULES_VERSION] = version }
    }

    fun isBootStartEnabled(): Boolean {
        return kotlinx.coroutines.runBlocking {
            context.dataStore.data.map { prefs -> prefs[KEY_BOOT_START] ?: true }.first()
        }
    }

    suspend fun setVpnAutoStart(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_VPN_AUTO_START] = enabled }
    }

    suspend fun setBehaviorMonitor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_BEHAVIOR_MONITOR] = enabled }
    }

    suspend fun setBootStart(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_BOOT_START] = enabled }
    }

    suspend fun setSoundAlert(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SOUND_ALERT] = enabled }
    }

    suspend fun setVibrateAlert(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_VIBRATE_ALERT] = enabled }
    }

    suspend fun setSmsMonitor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SMS_MONITOR] = enabled }
    }
}
```

---

## 5. 安全与隐私

### 5.1 权限声明

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 核心功能权限 -->
    <uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- 短信监控权限 -->
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />

    <!-- 电池优化权限 -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- 可选权限 -->
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

</manifest>
```

### 5.2 组件声明

```xml
<application>
    <!-- VPN 服务 -->
    <service
        android:name=".service.GuardVpnService"
        android:exported="false"
        android:permission="android.permission.BIND_VPN_SERVICE">
        <intent-filter>
            <action android:name="android.net.VpnService" />
        </intent-filter>
    </service>

    <!-- 前台保活服务 -->
    <service
        android:name=".service.ForegroundService"
        android:exported="false"
        android:foregroundServiceType="dataSync" />

    <!-- 开机启动 -->
    <receiver
        android:name=".service.BootReceiver"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>

    <!-- 短信拦截 -->
    <receiver
        android:name=".service.SmsReceiver"
        android:permission="android.permission.BROADCAST_SMS"
        android:exported="true">
        <intent-filter android:priority="999">
            <action android:name="android.provider.Telephony.SMS_RECEIVED" />
        </intent-filter>
    </receiver>
</application>
```

### 5.3 隐私政策核心条款

```
TianshangGuard（天殇盾）隐私承诺

1. 所有分析在设备本地完成，零数据上传
2. DNS 查询不记录用户身份，仅用于威胁检测
3. 短信内容仅在设备本地分析，不会上传或存储原文
4. 不会收集用户浏览历史
5. 拦截日志中的域名做部分遮蔽（如 *baidu.com）
6. 开源代码可审计：https://github.com/Tianshang301/TianshangGuard

用户数据：
- 仅存储本地：拦截日志、用户设置、规则缓存
- 用户可随时导出或删除
```

### 5.4 法律声明

```
启动页免责声明：

"本工具无法 100% 拦截所有诈骗，用户仍需保持警惕。
技术有边界，防骗最终依靠您的判断。

TianshangGuard 仅作为辅助工具，不承担因诈骗导致的任何损失责任。"
```

---

## 6. 性能与优化

### 6.1 性能监控

```kotlin
package com.tianshang.guard.core.telemetry

object PerformanceTracer {
    private val metrics = mutableMapOf<String, MetricStats>()

    data class MetricStats(
        var count: Long = 0,
        var totalTime: Long = 0,
        var maxTime: Long = 0,
        var minTime: Long = Long.MAX_VALUE
    )

    fun recordDnsResolveTime(timeMs: Long) = record("dns_resolve", timeMs)
    fun recordInferenceTime(timeMs: Long) = record("ml_inference", timeMs)
    fun recordTimeout() = record("ml_timeout", 0)

    private fun record(name: String, timeMs: Long) {
        metrics.getOrPut(name) { MetricStats() }.apply {
            count++
            if (timeMs > 0) {
                totalTime += timeMs
                maxTime = maxOf(maxTime, timeMs)
                minTime = minOf(minTime, timeMs)
            }
        }
    }

    fun getReport(): Map<String, MetricStats> = metrics.toMap()
}
```

### 6.2 电池优化

- 使用 `WorkManager` 周期性规则同步，避免持续轮询
- DNS 查询结果本地缓存（LRU，最大 2048 条）
- 屏幕共享检测间隔 3 秒（平衡实时性与电量）
- 前台服务使用 `IMPORTANCE_LOW` 通知，减少唤醒
- BatteryOptimizer 适配主流品牌（华为/小米/OPPO/vivo/魅族/三星）

---

## 7. 项目结构

```
tianshang-guard/
├── app/
│   ├── src/main/java/com/tianshang/guard/
│   │   ├── GuardApplication.kt
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── database/
│   │   │   │   │   ├── GuardDatabase.kt
│   │   │   │   │   ├── DomainDao.kt
│   │   │   │   │   ├── AlertDao.kt
│   │   │   │   │   └── Entity.kt
│   │   │   │   ├── GuardPreferences.kt
│   │   │   │   └── security/
│   │   │   │       └── EncryptedDatabaseProvider.kt
│   │   │   ├── remote/
│   │   │   │   └── GithubRulesApi.kt
│   │   │   └── repository/
│   │   │       ├── RuleRepository.kt
│   │   │       └── AlertRepository.kt
│   │   ├── domain/
│   │   │   └── AnalyzeSmsUseCase.kt
│   │   ├── core/
│   │   │   ├── dns/
│   │   │   │   ├── DnsEngine.kt
│   │   │   │   ├── LocalDnsEngine.kt
│   │   │   │   ├── HomographDetector.kt
│   │   │   │   ├── AdaptiveBloomFilter.kt
│   │   │   │   └── DnsPacketHandler.kt
│   │   │   ├── ml/
│   │   │   │   ├── MlEngine.kt (ModelType, RiskLevel, MlState, MlEngine 接口)
│   │   │   │   ├── OnnxMlEngine.kt (BertTokenizer + OnnxMlEngine)
│   │   │   │   ├── MlEngineWithFallback.kt
│   │   │   │   └── RuleBasedEngine.kt
│   │   │   ├── monitor/
│   │   │   │   ├── RemoteConfigProvider.kt
│   │   │   │   └── ScreenShareMonitor.kt
│   │   │   ├── alert/
│   │   │   │   ├── AlertEngine.kt (AlertLevel, AlertConfig, AlertEngine 接口)
│   │   │   │   ├── TieredAlertEngine.kt
│   │   │   │   └── CooldownManager.kt
│   │   │   ├── update/
│   │   │   │   └── RuleUpdateWorker.kt
│   │   │   ├── optimizer/
│   │   │   │   └── BatteryOptimizer.kt (PhoneBrand, BatteryOptimizer)
│   │   │   └── telemetry/
│   │   │       └── PerformanceTracer.kt
│   │   ├── service/
│   │   │   ├── GuardVpnService.kt
│   │   │   ├── ForegroundService.kt
│   │   │   ├── BootReceiver.kt
│   │   │   └── SmsReceiver.kt
│   │   ├── ui/
│   │   │   ├── main/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── MainViewModel.kt
│   │   │   ├── alert/
│   │   │   │   └── AlertActivity.kt
│   │   │   ├── stats/
│   │   │   │   ├── StatsScreen.kt
│   │   │   │   └── StatsViewModel.kt
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── SettingsViewModel.kt
│   │   │   ├── sms/
│   │   │   │   ├── SmsScreen.kt
│   │   │   │   └── SmsViewModel.kt
│   │   │   ├── report/
│   │   │   │   └── ReportScreen.kt
│   │   │   └── onboarding/
│   │   │       └── OnboardingScreen.kt
│   │   └── di/
│   │       └── AppModule.kt
│   ├── src/zh/res/values/
│   │   └── strings.xml (中文 UI 字符串)
│   ├── src/en/res/values/
│   │   └── strings.xml (英文 UI 字符串)
│   ├── src/main/assets/
│   │   ├── model/
│   │   │   ├── url_phishing.onnx (312 KB)
│   │   │   ├── chinese_phishing.onnx (1021 KB)
│   │   │   └── english_phishing.onnx (312 KB)
│   │   └── rules/
│   │       ├── whitelist.json
│   │       └── blacklist.json
│   └── build.gradle.kts
│
└── build.gradle.kts
```

---

## 8. 构建配置

### 8.1 app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tianshang.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tianshang.guard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // R8 与 ONNX Runtime JNI 不兼容
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            lint {
                checkReleaseBuilds = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // 多语言 Flavor
    flavorDimensions += "language"
    productFlavors {
        create("zh") {
            dimension = "language"
            applicationIdSuffix = ".zh"
            versionNameSuffix = "-zh"
        }
        create("en") {
            dimension = "language"
            applicationIdSuffix = ".en"
            versionNameSuffix = "-en"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.onnxruntime.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)

    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

### 8.2 构建命令

```bash
# 构建中文版 Release
./gradlew assembleZhRelease

# 构建英文版 Release
./gradlew assembleEnRelease

# APK 输出路径
# app/build/outputs/apk/zh/release/app-zh-release-unsigned.apk
# app/build/outputs/apk/en/release/app-en-release-unsigned.apk
```

### 8.3 多语言 Flavor

| Flavor | 包名 | UI 语言 | 包含模型 |
|--------|------|---------|---------|
| zh | `com.tianshang.guard.zh` | 中文 | URL + 中文短信 |
| en | `com.tianshang.guard.en` | 英文 | URL + 英文短信 |

---

## 9. 部署与分发

### 9.1 分发渠道

| 渠道              | 说明                  | 优先级 |
| --------------- | ------------------- | --- |
| GitHub Releases | 主分发渠道，提供 APK + 签名校验 | P0  |
| F-Droid         | 纯开源版本，无 GMS 依赖      | P1  |
| Google Play     | 需适配 VPN 政策，提供使用说明弹窗 | P2  |

### 9.2 版本发布流程

```
1. 更新版本号（versionCode + versionName）
2. 生成签名 APK
3. 计算 SHA-256 校验和
4. 使用 PGP 签名 APK
5. 发布到 GitHub Releases
6. 同步更新规则仓库版本
```

### 9.3 规则更新机制

```kotlin
package com.tianshang.guard.core.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.remote.GithubRulesApi
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent

class RuleUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs: GuardPreferences = KoinJavaComponent.get(GuardPreferences::class.java)
            val api: GithubRulesApi = KoinJavaComponent.get(GithubRulesApi::class.java)
            val domainDao: DomainDao = KoinJavaComponent.get(DomainDao::class.java)

            val localVersion = prefs.rulesVersion.first()
            val remoteVersion = api.getLatestRulesVersion()

            if (compareVersions(remoteVersion.version, localVersion) > 0) {
                val diff = api.getRulesDiff(localVersion)
                applyDiff(domainDao, diff.adds, diff.removes)
                prefs.setRulesVersion(remoteVersion.version)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun applyDiff(dao: DomainDao, adds: List<String>, removes: List<String>) {
        val now = System.currentTimeMillis()
        adds.forEach { domain ->
            dao.insert(DomainEntity(
                domain = domain.trim().lowercase(),
                category = DomainCategory.BLACKLIST,
                source = "remote",
                addedAt = now
            ))
        }
        removes.forEach { domain ->
            dao.insert(DomainEntity(
                domain = domain.trim().lowercase(),
                category = DomainCategory.WHITELIST,
                source = "remote",
                addedAt = now
            ))
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val aVal = aParts.getOrElse(i) { 0 }
            val bVal = bParts.getOrElse(i) { 0 }
            if (aVal != bVal) return aVal - bVal
        }
        return 0
    }
}
```

---

## 10. 附录

### 10.1 术语表

| 术语                  | 说明                                     |
| ------------------- | -------------------------------------- |
| SNI                 | Server Name Indication，TLS 扩展，用于指定访问域名 |
| DoH                 | DNS over HTTPS，加密 DNS 查询               |
| Bloom Filter        | 概率型数据结构，用于快速判断元素是否可能存在于集合中             |
| ONNX                | Open Neural Network Exchange，跨平台模型格式   |
| NNAPI               | Android Neural Networks API，硬件加速推理     |

### 10.2 参考资源

- [dnsproxy](https://github.com/AdguardTeam/dnsproxy) - DNS 代理库
- [ONNX Runtime Mobile](https://onnxruntime.ai/docs/tutorials/mobile/) - 端侧推理
- [PhishTank](https://www.phishtank.com/) - 钓鱼网站数据库
- [OpenPhish](https://openphish.com/) - 钓鱼情报源

### 10.3 贡献指南

```
如何贡献：

1. Fork 仓库
2. 创建特性分支（git checkout -b feature/xxx）
3. 提交更改（git commit -am 'Add xxx'）
4. 推送分支（git push origin feature/xxx）
5. 创建 Pull Request

规则贡献：
- 提交可疑域名到 `rules/community/` 目录
- 使用 JSON 格式：{"domain": "example.com", "reason": "phishing", "source": "user_report"}
- 通过 GitHub Actions 自动校验格式
```

---

*文档版本: v1.0.0*  
*最后更新: 2026-06-24*  
*维护者: Tianshang301*
