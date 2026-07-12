package com.tianshang.guard.core.telemetry

import com.tianshang.guard.BaseUnitTest
import org.junit.After
import org.junit.Assert
import org.junit.Test

class PerformanceTracerTest : BaseUnitTest() {

    @After
    fun tearDown() {
        // Tracer is a singleton, clear state between tests
        PerformanceTracer.getReport().keys.forEach { key ->
            PerformanceTracer.getReport()[key]?.let { stats ->
                stats.count.set(0)
                stats.totalTime.set(0)
                stats.maxTime.set(0)
                stats.minTime.set(Long.MAX_VALUE)
            }
        }
    }

    @Test
    fun `recordDnsResolveTime records metric`() {
        PerformanceTracer.recordDnsResolveTime(42)
        val report = PerformanceTracer.getReport()
        Assert.assertTrue(report.containsKey("dns_resolve"))
        Assert.assertEquals(1, report["dns_resolve"]!!.count.get())
    }

    @Test
    fun `recordInferenceTime records metric`() {
        PerformanceTracer.recordInferenceTime(150)
        val report = PerformanceTracer.getReport()
        Assert.assertTrue(report.containsKey("ml_inference"))
        Assert.assertEquals(150, report["ml_inference"]!!.totalTime.get())
    }

    @Test
    fun `recordTimeout records metric with zero time`() {
        PerformanceTracer.recordTimeout()
        val report = PerformanceTracer.getReport()
        Assert.assertTrue(report.containsKey("ml_timeout"))
    }

    @Test
    fun `multiple calls accumulate stats`() {
        PerformanceTracer.recordDnsResolveTime(10)
        PerformanceTracer.recordDnsResolveTime(20)
        PerformanceTracer.recordDnsResolveTime(30)
        val stats = PerformanceTracer.getReport()["dns_resolve"]!!
        Assert.assertEquals(3, stats.count.get())
        Assert.assertEquals(60, stats.totalTime.get())
        Assert.assertEquals(30, stats.maxTime.get())
        Assert.assertEquals(10, stats.minTime.get())
    }

    @Test
    fun `getReport returns same keys after new recordings`() {
        PerformanceTracer.recordDnsResolveTime(50)
        val report = PerformanceTracer.getReport()
        PerformanceTracer.recordInferenceTime(200)
        val report2 = PerformanceTracer.getReport()
        Assert.assertTrue(report.containsKey("dns_resolve"))
        Assert.assertFalse(report.containsKey("ml_inference"))
        Assert.assertTrue(report2.containsKey("ml_inference"))
    }
}
