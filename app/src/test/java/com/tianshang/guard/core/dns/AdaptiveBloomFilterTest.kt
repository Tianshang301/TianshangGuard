package com.tianshang.guard.core.dns

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Test
import java.util.BitSet

class AdaptiveBloomFilterTest : BaseUnitTest() {

    @Test
    fun `mightContain returns true for added element`() {
        val filter = AdaptiveBloomFilter(100, 0.01)
        filter.add("example.com")
        Assert.assertTrue(filter.mightContain("example.com"))
    }

    @Test
    fun `mightContain returns false for unknown element`() {
        val filter = AdaptiveBloomFilter(100, 0.01)
        filter.add("known.com")
        Assert.assertFalse(filter.mightContain("unknown.com"))
    }

    @Test
    fun `multiple elements can be added and checked`() {
        val filter = AdaptiveBloomFilter(1000, 0.001)
        val domains = listOf("google.com", "facebook.com", "twitter.com")
        domains.forEach { filter.add(it) }
        domains.forEach { Assert.assertTrue(filter.mightContain(it)) }
    }

    @Test
    fun `optimalBitSize returns positive value`() {
        val size = AdaptiveBloomFilter.optimalBitSize(1000, 0.01)
        Assert.assertTrue(size > 0)
    }

    @Test
    fun `optimalHashCount returns positive value`() {
        val count = AdaptiveBloomFilter.optimalHashCount(1000, 10000)
        Assert.assertTrue(count > 0)
    }

    @Test
    fun `replaceData replaces existing data`() {
        val filter = AdaptiveBloomFilter(100, 0.01)
        filter.add("old.com")
        Assert.assertTrue(filter.mightContain("old.com"))

        val newBits = BitSet()
        filter.replaceData(newBits)
        Assert.assertFalse(filter.mightContain("old.com"))
    }

    @Test
    fun `handles empty string`() {
        val filter = AdaptiveBloomFilter(100, 0.01)
        filter.add("")
        Assert.assertTrue(filter.mightContain(""))
    }

    @Test
    fun `handles expectedItems of 1`() {
        val filter = AdaptiveBloomFilter(1, 0.001)
        filter.add("test.com")
        Assert.assertTrue(filter.mightContain("test.com"))
    }
}
