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
