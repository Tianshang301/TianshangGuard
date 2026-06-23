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

    private fun getState(type: ModelType): MlState = states[type] ?: MlState.Loading

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
        return analyzeWithFallback(text, ModelType.CHINESE)
    }

    private fun analyzeWithFallback(text: String, type: ModelType): RiskLevel {
        val cacheKey = "${type.name}:$text"
        resultCache[cacheKey]?.let { return it }

        val result = when (getState(type)) {
            is MlState.Ready -> runWithTimeout(text, type)
            else -> fallbackEngine.analyzeSms(text)
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
            states[type] = MlState.Fallback
            fallbackEngine.analyzeSms(text)
        } catch (e: Exception) {
            states[type] = MlState.Failed(e.message ?: "Unknown")
            fallbackEngine.analyzeSms(text)
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
            states[ModelType.URL] = MlState.Fallback
            fallbackEngine.analyzeDomain(domain)
        } catch (e: Exception) {
            states[ModelType.URL] = MlState.Failed(e.message ?: "Unknown")
            fallbackEngine.analyzeDomain(domain)
        }
    }

    fun loadModelAsync(modelPath: String, type: ModelType = ModelType.URL) {
        try {
            android.util.Log.i("MlEngine", "Loading model: $modelPath ($type)")
            onnxEngine.loadModel(modelPath, type)
            states[type] = MlState.Ready
            android.util.Log.i("MlEngine", "Model loaded successfully: $type")
        } catch (e: Exception) {
            android.util.Log.e("MlEngine", "Model load FAILED: $type", e)
            states[type] = MlState.Failed(e.message ?: "Load failed")
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {
        loadModelAsync(modelPath, type)
    }

    override fun isModelLoaded(type: ModelType): Boolean = getState(type) is MlState.Ready
}
