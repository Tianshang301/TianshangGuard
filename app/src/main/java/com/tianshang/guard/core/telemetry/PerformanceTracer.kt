package com.tianshang.guard.core.telemetry

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object PerformanceTracer {
    // BUGFIX: Use ConcurrentHashMap + AtomicLong for thread safety
    private val metrics = ConcurrentHashMap<String, MetricStats>()

    data class MetricStats(
        val count: AtomicLong = AtomicLong(0),
        val totalTime: AtomicLong = AtomicLong(0),
        val maxTime: AtomicLong = AtomicLong(0),
        val minTime: AtomicLong = AtomicLong(Long.MAX_VALUE)
    )

    fun recordDnsResolveTime(timeMs: Long) = record("dns_resolve", timeMs)
    fun recordInferenceTime(timeMs: Long) = record("ml_inference", timeMs)
    fun recordTimeout() = record("ml_timeout", 0)

    private fun record(name: String, timeMs: Long) {
        val stats = metrics.getOrPut(name) { MetricStats() }
        stats.count.incrementAndGet()
        if (timeMs > 0) {
            stats.totalTime.addAndGet(timeMs)
            // Atomic max update
            var currentMax = stats.maxTime.get()
            while (timeMs > currentMax) {
                if (stats.maxTime.compareAndSet(currentMax, timeMs)) break
                currentMax = stats.maxTime.get()
            }
            // Atomic min update
            var currentMin = stats.minTime.get()
            while (timeMs < currentMin) {
                if (stats.minTime.compareAndSet(currentMin, timeMs)) break
                currentMin = stats.minTime.get()
            }
        }
    }

    fun getReport(): Map<String, MetricStats> = metrics.toMap()
}
