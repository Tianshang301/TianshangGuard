package com.tianshang.guard.core.dns

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.BitSet

class AdaptiveBloomFilterTest {

    private lateinit var filter: AdaptiveBloomFilter

    @Before
    fun setUp() {
        filter = AdaptiveBloomFilter(1000, 0.01)
    }

    @Test
    fun `added element is found`() {
        filter.add("phishing.com")
        assertTrue(filter.mightContain("phishing.com"))
    }

    @Test
    fun `non added element may be absent`() {
        assertFalse(filter.mightContain("safe.example.com"))
    }

    @Test
    fun `multiple added elements are all found`() {
        val domains = listOf("evil.com", "bad.net", "malware.org", "phish.xyz")
        domains.forEach { filter.add(it) }
        domains.forEach { assertTrue(filter.mightContain(it)) }
    }

    @Test
    fun `replaceData clears and loads new bits`() {
        filter.add("old.com")
        assertTrue(filter.mightContain("old.com"))

        filter.replaceData(BitSet())
        assertFalse(filter.mightContain("old.com"))
        assertFalse(filter.mightContain("anything.com"))
    }

    @Test
    fun `falsePositiveRate below target`() {
        val n = 500
        val testFilter = AdaptiveBloomFilter(n, 0.05)
        repeat(n) { testFilter.add("domain-$it.com") }

        var falsePositives = 0
        val trials = 10_000
        repeat(trials) {
            if (testFilter.mightContain("not-in-filter-$it.org")) {
                falsePositives++
            }
        }
        val fpr = falsePositives.toDouble() / trials
        assertTrue(fpr < 0.1)
    }

    @Test
    fun `optimalBitSize produces positive integer`() {
        val size = AdaptiveBloomFilter.optimalBitSize(100_000, 0.001)
        assertTrue(size > 0)
    }

    @Test
    fun `optimalHashCount produces positive integer`() {
        val count = AdaptiveBloomFilter.optimalHashCount(100_000, 1_438_058)
        assertTrue(count > 0)
    }

    @Test
    fun `empty filter returns false`() {
        assertFalse(filter.mightContain("anything.com"))
    }
}
