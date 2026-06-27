package com.tianshang.guard.data.remote

import retrofit2.http.GET
import retrofit2.http.Header

data class RulesVersion(val version: String)
data class RulesDiff(
    val adds: List<String>,
    val removes: List<String>,
    val signature: String? = null // SHA-256 signature for integrity verification
)

interface GithubRulesApi {

    @GET("api/rules/latest-version")
    suspend fun getLatestRulesVersion(): RulesVersion

    @GET("api/rules/diff")
    suspend fun getRulesDiff(
        @Header("If-None-Match") localVersion: String
    ): RulesDiff
}
