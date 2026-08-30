package com.tianshang.guard.data.remote

import retrofit2.http.GET
import retrofit2.http.Header

data class RulesVersion(val version: String)
data class RulesDiff(
    val version: String = "",
    val timestamp: Long = 0L,
    val nonce: String = "",
    val adds: List<String>,
    val removes: List<String>,
    val signature: String? = null // Base64 Ed25519 signature over the canonical payload (see SignatureVerifier)
)

interface GithubRulesApi {

    @GET("api/rules/latest-version")
    suspend fun getLatestRulesVersion(): RulesVersion

    @GET("api/rules/diff")
    suspend fun getRulesDiff(
        @Header("If-None-Match") localVersion: String
    ): RulesDiff
}
