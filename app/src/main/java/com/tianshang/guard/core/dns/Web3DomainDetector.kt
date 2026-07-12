package com.tianshang.guard.core.dns

sealed class Web3DomainResult {
    data object NotWeb3 : Web3DomainResult()
    data class Detected(val provider: Web3Provider, val resolvedName: String) : Web3DomainResult()
}

enum class Web3Provider {
    ENS,
    UNSTOPPABLE,
    SID
}

class Web3DomainDetector {

    private val ensSuffixes = setOf(".eth")
    private val udSuffixes = setOf(
        ".crypto", ".nft", ".blockchain", ".bitcoin", ".dao",
        ".888", ".wallet", ".x", ".klever", ".zil", ".go"
    )
    private val sidSuffixes = setOf(
        ".bnb", ".arb", ".polygon", ".op"
    )

    private val knownWeb3Tlds = (ensSuffixes + udSuffixes + sidSuffixes).toSet()

    fun detect(domain: String): Web3DomainResult {
        val lower = domain.lowercase().trim()
        for (suffix in ensSuffixes) {
            if (lower.endsWith(suffix)) {
                return Web3DomainResult.Detected(
                    provider = Web3Provider.ENS,
                    resolvedName = lower.removeSuffix(suffix)
                )
            }
        }
        for (suffix in udSuffixes) {
            if (lower.endsWith(suffix)) {
                return Web3DomainResult.Detected(
                    provider = Web3Provider.UNSTOPPABLE,
                    resolvedName = lower.removeSuffix(suffix)
                )
            }
        }
        for (suffix in sidSuffixes) {
            if (lower.endsWith(suffix)) {
                return Web3DomainResult.Detected(
                    provider = Web3Provider.SID,
                    resolvedName = lower.removeSuffix(suffix)
                )
            }
        }
        return Web3DomainResult.NotWeb3
    }

    fun isWeb3Domain(domain: String): Boolean {
        return detect(domain) !is Web3DomainResult.NotWeb3
    }

    fun getRiskLevel(domain: String): Float {
        val lower = domain.lowercase().trim()
        return when {
            lower.endsWith(".eth") -> 0.6f
            lower.endsWith(".crypto") || lower.endsWith(".nft") -> 0.7f
            lower.endsWith(".wallet") -> 0.8f
            lower.endsWith(".x") || lower.endsWith(".888") -> 0.75f
            lower.endsWith(".bitcoin") || lower.endsWith(".blockchain") -> 0.8f
            sidSuffixes.any { lower.endsWith(it) } -> 0.5f
            else -> 0.5f
        }
    }
}
