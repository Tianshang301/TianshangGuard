package com.tianshang.guard.data.remote

import retrofit2.http.GET

data class PhishTankEntry(
    val url: String,
    val domain: String,
    val verified: Boolean
)

interface PhishTankApi {

    @GET("api/online/")
    suspend fun getPhishingSites(): List<PhishTankEntry>
}
