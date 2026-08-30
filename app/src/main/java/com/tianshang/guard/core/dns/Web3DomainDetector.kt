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

    /**
     * Extract the registrable domain (right-most label + the label before it) when
     * the TLD is a known Web3 suffix. e.g. "legit.com.evil.eth" -> ("ENS", "evil.eth")
     * so nested subdomains cannot hide the real Web3 name (M-02).
     * Returns null when the domain is not a Web3 domain.
     */
    private fun web3Registrable(lower: String): Pair<Web3Provider, String>? {
        val labels = lower.split('.')
        if (labels.size < 2) return null
        val tld = labels.last()
        val provider = when {
            tld in ensSuffixes.map { it.removePrefix(".") } -> Web3Provider.ENS
            tld in udSuffixes.map { it.removePrefix(".") } -> Web3Provider.UNSTOPPABLE
            tld in sidSuffixes.map { it.removePrefix(".") } -> Web3Provider.SID
            else -> null
        } ?: return null
        val name = "${labels[labels.size - 2]}.$tld"
        return provider to name
    }

    fun detect(domain: String): Web3DomainResult {
        val lower = domain.lowercase().trim()
        val reg = web3Registrable(lower) ?: return Web3DomainResult.NotWeb3
        return Web3DomainResult.Detected(provider = reg.first, resolvedName = reg.second)
    }

    fun isWeb3Domain(domain: String): Boolean {
        return detect(domain) !is Web3DomainResult.NotWeb3
    }

    fun getRiskLevel(domain: String): Float {
        val lower = domain.lowercase().trim()
        val reg = web3Registrable(lower) ?: return 0.0f
        return when {
            reg.second.endsWith(".eth") -> 0.6f
            reg.second.endsWith(".crypto") || reg.second.endsWith(".nft") -> 0.7f
            reg.second.endsWith(".wallet") -> 0.8f
            reg.second.endsWith(".x") || reg.second.endsWith(".888") -> 0.75f
            reg.second.endsWith(".bitcoin") || reg.second.endsWith(".blockchain") -> 0.8f
            sidSuffixes.any { reg.second.endsWith(it) } -> 0.5f
            else -> 0.5f
        }
    }
}
