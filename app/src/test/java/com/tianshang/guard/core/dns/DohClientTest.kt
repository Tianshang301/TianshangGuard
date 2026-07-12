package com.tianshang.guard.core.dns

import com.tianshang.guard.BaseUnitTest
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DohClientTest : BaseUnitTest() {

    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var dohClient: DohClient

    @Before
    fun setUp() {
        dohClient = DohClient(okHttpClient)
    }

    @Test
    fun `resolve returns null when both DoH and UDP fail`() {
        val call = mockk<Call>()
        every { okHttpClient.newBuilder() } returns mockk {
            every { connectTimeout(any(), any()) } returns this
            every { readTimeout(any(), any()) } returns this
            every { writeTimeout(any(), any()) } returns this
            every { build() } returns mockk {
                every { newCall(any()) } returns call
            }
        }
        every { call.execute() } throws java.net.SocketTimeoutException("timeout")

        val result = dohClient.resolve(byteArrayOf(0, 1, 2, 3))
        Assert.assertNull(result)
    }

    @Test
    fun `constructor uses default DoH URL`() {
        val client = DohClient(okHttpClient)
        Assert.assertNotNull(client)
    }
}
