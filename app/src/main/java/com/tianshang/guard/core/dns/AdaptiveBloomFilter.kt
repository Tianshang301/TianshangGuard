package com.tianshang.guard.core.dns

import java.util.BitSet
import kotlin.math.abs
import kotlin.math.ln

class AdaptiveBloomFilter(
    expectedItems: Int,
    targetFpp: Double = 0.001
) {
    private val bitSize = optimalBitSize(expectedItems, targetFpp)
    private val numHashFunctions = optimalHashCount(expectedItems, bitSize)
    private val bits = BitSet(bitSize)

    companion object {
        fun optimalBitSize(n: Int, p: Double): Int {
            return (-n * ln(p) / (ln(2.0) * ln(2.0))).toInt()
        }

        fun optimalHashCount(n: Int, m: Int): Int {
            return maxOf(1, (m / n * ln(2.0)).toInt())
        }
    }

    fun add(element: String) {
        val hash1 = element.hashCode()
        val hash2 = element.reversed().hashCode()

        for (i in 0 until numHashFunctions) {
            val combinedHash = hash1 + i * hash2
            val index = abs(combinedHash % bitSize)
            bits.set(index)
        }
    }

    fun mightContain(element: String): Boolean {
        val hash1 = element.hashCode()
        val hash2 = element.reversed().hashCode()

        for (i in 0 until numHashFunctions) {
            val combinedHash = hash1 + i * hash2
            val index = abs(combinedHash % bitSize)
            if (!bits.get(index)) return false
        }
        return true
    }

    fun replaceData(newBits: BitSet) {
        bits.clear()
        bits.or(newBits)
    }
}
