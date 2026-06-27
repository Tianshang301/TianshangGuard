package com.tianshang.guard.core.dns

import java.util.BitSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

class AdaptiveBloomFilter(
    expectedItems: Int,
    targetFpp: Double = 0.001
) {
    // ABF-03: Input validation
    private val safeExpectedItems = max(1, expectedItems)
    private val bitSize = max(64, minOf(optimalBitSize(safeExpectedItems, targetFpp), MAX_BIT_SIZE))
    private val numHashFunctions = max(1, optimalHashCount(safeExpectedItems, bitSize))
    private val bits = BitSet(bitSize)
    private val lock = ReentrantReadWriteLock() // ABF-04: Thread safety

    companion object {
        private const val MAX_BIT_SIZE = 10_000_000 // ABF-06: ~1.25 MB max

        fun optimalBitSize(n: Int, p: Double): Int {
            return (-n * ln(p) / (ln(2.0) * ln(2.0))).toInt()
        }

        fun optimalHashCount(n: Int, m: Int): Int {
            if (n <= 0 || m <= 0) return 1
            return maxOf(1, (m.toDouble() / n * ln(2.0)).toInt())
        }

        // ABF-01: MurmurHash3 implementation for independent hash functions
        private fun murmur3(data: ByteArray, seed: Int): Int {
            val length = data.size
            var h1 = seed
            val c1 = 0xcc9e2d51.toInt()
            val c2 = 0x1b873593

            var i = 0
            while (i + 4 <= length) {
                var k1 = (data[i].toInt() and 0xFF) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    ((data[i + 2].toInt() and 0xFF) shl 16) or
                    ((data[i + 3].toInt() and 0xFF) shl 24)

                k1 *= c1
                k1 = (k1 shl 15) or (k1 ushr 17)
                k1 *= c2

                h1 = h1 xor k1
                h1 = (h1 shl 13) or (h1 ushr 19)
                h1 = h1 * 5 + 0xe6546b64.toInt()

                i += 4
            }

            var k1 = 0
            when (length % 4) {
                3 -> k1 = (data[i + 2].toInt() and 0xFF) shl 16
                2 -> k1 = k1 or ((data[i + 1].toInt() and 0xFF) shl 8)
                1 -> k1 = k1 or (data[i].toInt() and 0xFF)
            }
            k1 *= c1
            k1 = (k1 shl 15) or (k1 ushr 17)
            k1 *= c2
            h1 = h1 xor k1

            h1 = h1 xor length
            h1 = h1 xor (h1 ushr 16)
            h1 *= 0x85ebca6b.toInt()
            h1 = h1 xor (h1 ushr 13)
            h1 *= 0xc2b2ae35.toInt()
            h1 = h1 xor (h1 ushr 16)

            return h1
        }
    }

    fun add(element: String) {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val hash1 = murmur3(bytes, 0)
        val hash2 = murmur3(bytes, 0x5bd1e995.toInt()) // Different seed

        lock.writeLock().lock()
        try {
            for (i in 0 until numHashFunctions) {
                // ABF-02: Safe unsigned arithmetic
                val combinedHash = (hash1.toLong() + i * hash2.toLong())
                val index = ((combinedHash and 0xFFFFFFFFL) % bitSize).toInt()
                bits.set(index)
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun mightContain(element: String): Boolean {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val hash1 = murmur3(bytes, 0)
        val hash2 = murmur3(bytes, 0x5bd1e995.toInt())

        lock.readLock().lock()
        try {
            for (i in 0 until numHashFunctions) {
                val combinedHash = (hash1.toLong() + i * hash2.toLong())
                val index = ((combinedHash and 0xFFFFFFFFL) % bitSize).toInt()
                if (!bits.get(index)) return false
            }
            return true
        } finally {
            lock.readLock().unlock()
        }
    }

    // ABF-05: Atomic replace operation
    fun replaceData(newBits: BitSet) {
        lock.writeLock().lock()
        try {
            bits.clear()
            bits.or(newBits)
        } finally {
            lock.writeLock().unlock()
        }
    }
}
