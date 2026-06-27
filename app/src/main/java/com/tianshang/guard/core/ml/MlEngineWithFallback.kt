package com.tianshang.guard.core.ml

import com.tianshang.guard.core.feedback.FeedbackEngine
import com.tianshang.guard.core.retrieval.KnowledgeBase
import com.tianshang.guard.core.rl.FeatureBasedPredictor
import com.tianshang.guard.core.rl.FeatureExtractor
import com.tianshang.guard.core.telemetry.PerformanceTracer
import com.tianshang.guard.core.util.SecureLog
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.LinkedHashMap

class MlEngineWithFallback(
    private val onnxEngine: OnnxMlEngine,
    private val fallbackEngine: RuleBasedEngine,
    private val tracer: PerformanceTracer,
    private val knowledgeBase: KnowledgeBase,
    private val feedbackEngine: FeedbackEngine,
    private val featureExtractor: FeatureExtractor,
    private val featurePredictor: FeatureBasedPredictor
) : MlEngine {

    // BUGFIX: Use ConcurrentHashMap for thread-safe state management
    private val states = Collections.synchronizedMap(mutableMapOf<ModelType, MlState>())
    private val inferenceTimeout = 500L

    // BUGFIX: Use synchronizedMap for thread-safe LRU cache
    private val resultCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, RiskLevel>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RiskLevel>): Boolean {
                return size > 100
            }
        }
    )

    // BUGFIX: Retry mechanism instead of permanent disable on timeout
    private val retryTimestamps = Collections.synchronizedMap(mutableMapOf<ModelType, Long>())
    private val baseRetryDelay = 30_000L // 30 seconds

    private fun getState(type: ModelType): MlState {
        // Check if we should retry a previously failed model
        val state = states[type] ?: MlState.Loading
        if (state is MlState.Fallback || state is MlState.Failed) {
            val retryAt = retryTimestamps[type] ?: 0L
            if (System.currentTimeMillis() >= retryAt) {
                states[type] = MlState.Ready
                return MlState.Ready
            }
        }
        return state
    }

    override fun analyzeWebPage(text: String): RiskLevel {
        return analyzeWithFallback(text, ModelType.URL)
    }

    override fun analyzeDomain(domain: String): RiskLevel {
        resultCache[domain]?.let { return it }

        val result = when (getState(ModelType.URL)) {
            is MlState.Ready -> runDomainWithTimeout(domain)
            else -> fallbackEngine.analyzeDomain(domain)
        }

        resultCache[domain] = result
        return result
    }

    override fun analyzeSms(text: String): RiskLevel {
        val cacheKey = "SMS:$text"
        resultCache[cacheKey]?.let { return it }

        // Check feedback whitelist first (user-marked false positives)
        val isWhitelisted = runBlocking {
            feedbackEngine.isWhitelisted(text)
        }
        if (isWhitelisted) {
            resultCache[cacheKey] = RiskLevel.SAFE
            return RiskLevel.SAFE
        }

        // 1. Model inference → continuous score
        val modelScore = when {
            states.any { it.value is MlState.Ready } -> runSmsWithScore(text)
            // BUGFIX: Use .toScore() instead of .threshold to avoid boundary escalation
            else -> fallbackEngine.analyzeSms(text).toScore()
        }

        // 2. BM25 retrieval → continuous score (phishingRatio)
        val bm25Score = if (knowledgeBase.isReady()) {
            val retrieval = knowledgeBase.query(text, topK = 10)
            if (retrieval.matchCount > 0) retrieval.phishingRatio else null
        } else {
            null
        }

        // 3. Feedback retrieval → continuous score (phishingRatio)
        val feedbackScore = runBlocking {
            val feedbackQuery = feedbackEngine.queryFeedback(text, topK = 10)
            if (feedbackQuery.matchCount > 0) feedbackQuery.phishingRatio else null
        }

        // 4. Feature-based prediction → continuous score
        val featureScore = runBlocking {
            val features = featureExtractor.extractFeatures(text)
            val prediction = featurePredictor.predict(features)
            // BUGFIX: Use .toScore() instead of .threshold
            if (prediction.matchCount > 0) prediction.riskLevel.toScore() else null
        }

        // Weighted fusion with continuous scores
        val scores = mutableListOf<Pair<Float, Float>>() // (score, weight)
        scores.add(Pair(modelScore, 0.5f))
        bm25Score?.let { scores.add(Pair(it, 0.2f)) }
        feedbackScore?.let { scores.add(Pair(it, 0.2f)) }
        featureScore?.let { scores.add(Pair(it, 0.1f)) }

        val totalWeight = scores.sumOf { it.second.toDouble() }.toFloat()
        val combinedScore = if (totalWeight > 0) {
            scores.sumOf { (it.first * it.second).toDouble() }.toFloat() / totalWeight
        } else {
            modelScore
        }

        val result = RiskLevel.fromScore(combinedScore)

        resultCache[cacheKey] = result
        return result
    }

    private fun runSmsWithScore(text: String): Float {
        return try {
            val startTime = System.currentTimeMillis()
            val score = runBlocking {
                withTimeout(inferenceTimeout) {
                    onnxEngine.analyzeSmsScore(text)
                }
            }
            tracer.recordInferenceTime(System.currentTimeMillis() - startTime)
            score
        } catch (e: TimeoutCancellationException) {
            tracer.recordTimeout()
            // BUGFIX: Use .toScore() instead of .threshold
            fallbackEngine.analyzeSms(text).toScore()
        } catch (e: Exception) {
            fallbackEngine.analyzeSms(text).toScore()
        }
    }

    private fun runSmsWithTimeout(text: String): RiskLevel {
        return RiskLevel.fromScore(runSmsWithScore(text))
    }

    // BUGFIX: Use correct fallback method based on type (not always analyzeSms)
    private fun analyzeWithFallback(text: String, type: ModelType): RiskLevel {
        val cacheKey = "${type.name}:$text"
        resultCache[cacheKey]?.let { return it }

        val result = when (getState(type)) {
            is MlState.Ready -> runWithTimeout(text, type)
            else -> when (type) {
                ModelType.URL -> fallbackEngine.analyzeWebPage(text)
                else -> fallbackEngine.analyzeSms(text)
            }
        }

        resultCache[cacheKey] = result
        return result
    }

    private fun runWithTimeout(text: String, type: ModelType): RiskLevel {
        return try {
            val startTime = System.currentTimeMillis()
            val result = runBlocking {
                withTimeout(inferenceTimeout) {
                    onnxEngine.analyzeWithModel(text, type)
                }
            }
            tracer.recordInferenceTime(System.currentTimeMillis() - startTime)
            result
        } catch (e: TimeoutCancellationException) {
            tracer.recordTimeout()
            // BUGFIX: Set retry timestamp instead of permanent disable
            retryTimestamps[type] = System.currentTimeMillis() + baseRetryDelay
            when (type) {
                ModelType.URL -> fallbackEngine.analyzeWebPage(text)
                else -> fallbackEngine.analyzeSms(text)
            }
        } catch (e: Exception) {
            // BUGFIX: Set retry timestamp instead of permanent disable
            retryTimestamps[type] = System.currentTimeMillis() + baseRetryDelay
            when (type) {
                ModelType.URL -> fallbackEngine.analyzeWebPage(text)
                else -> fallbackEngine.analyzeSms(text)
            }
        }
    }

    private fun runDomainWithTimeout(domain: String): RiskLevel {
        return try {
            val startTime = System.currentTimeMillis()
            val result = runBlocking {
                withTimeout(inferenceTimeout) {
                    onnxEngine.analyzeDomain(domain)
                }
            }
            tracer.recordInferenceTime(System.currentTimeMillis() - startTime)
            result
        } catch (e: TimeoutCancellationException) {
            tracer.recordTimeout()
            retryTimestamps[ModelType.URL] = System.currentTimeMillis() + baseRetryDelay
            fallbackEngine.analyzeDomain(domain)
        } catch (e: Exception) {
            retryTimestamps[ModelType.URL] = System.currentTimeMillis() + baseRetryDelay
            fallbackEngine.analyzeDomain(domain)
        }
    }

    fun loadModelAsync(modelPath: String, type: ModelType = ModelType.URL) {
        try {
            SecureLog.i("MlEngine", "Loading model: $modelPath ($type)")
            onnxEngine.loadModel(modelPath, type)
            states[type] = MlState.Ready
            retryTimestamps.remove(type)
            SecureLog.i("MlEngine", "Model loaded successfully: $type")
        } catch (e: Exception) {
            SecureLog.e("MlEngine", "Model load FAILED: $type", e)
            states[type] = MlState.Failed(e.message ?: "Load failed")
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {
        loadModelAsync(modelPath, type)
    }

    override fun isModelLoaded(type: ModelType): Boolean = getState(type) is MlState.Ready
}
