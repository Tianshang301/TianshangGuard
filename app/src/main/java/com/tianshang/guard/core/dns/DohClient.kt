package com.tianshang.guard.core.dns

import com.tianshang.guard.core.util.SecureLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * DNS over HTTPS (DoH) client for secure DNS resolution.
 * Uses Cloudflare's DoH endpoint (https://cloudflare-dns.com/dns-query).
 * Falls back to UDP if DoH fails or times out.
 */
class DohClient(private val client: OkHttpClient) {

    companion object {
        private const val DOH_URL = "https://cloudflare-dns.com/dns-query"
        private const val DOH_TIMEOUT_MS = 3000L
        private const val UDP_FALLBACK_TIMEOUT_MS = 2000L
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_DNS_PORT = 53
        private val DNS_MEDIA_TYPE = "application/dns-message".toMediaType()
    }

    private val dohClient = client.newBuilder()
        .connectTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Resolve DNS query using DoH (DNS over HTTPS).
     * Falls back to UDP if DoH fails.
     *
     * @param dnsPayload Raw DNS query bytes (wire format)
     * @return Raw DNS response bytes (wire format), or null on failure
     */
    fun resolve(dnsPayload: ByteArray): ByteArray? {
        // Try DoH first
        try {
            val response = resolveViaDoh(dnsPayload)
            if (response != null) {
                return response
            }
        } catch (e: Exception) {
            SecureLog.w("DohClient", "DoH failed, falling back to UDP", e)
        }

        // Fallback to UDP
        return resolveViaUdp(dnsPayload)
    }

    /**
     * Resolve DNS query using DoH (DNS over HTTPS).
     * Sends HTTP POST to Cloudflare's DoH endpoint.
     */
    private fun resolveViaDoh(dnsPayload: ByteArray): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(DOH_URL)
                .header("Accept", "application/dns-message")
                .post(dnsPayload.toRequestBody(DNS_MEDIA_TYPE))
                .build()

            val response = dohClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                SecureLog.w("DohClient", "DoH request failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            SecureLog.w("DohClient", "DoH request error", e)
            null
        }
    }

    /**
     * Resolve DNS query using traditional UDP (fallback).
     */
    private fun resolveViaUdp(dnsPayload: ByteArray): ByteArray? {
        return try {
            val socket = java.net.DatagramSocket()
            socket.soTimeout = UDP_FALLBACK_TIMEOUT_MS.toInt()
            val request = java.net.DatagramPacket(
                dnsPayload,
                dnsPayload.size,
                InetAddress.getByName(UPSTREAM_DNS),
                UPSTREAM_DNS_PORT
            )
            socket.send(request)
            val responseBuf = ByteArray(1500)
            val responsePkt = java.net.DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(responsePkt)
            socket.close()
            responseBuf.copyOf(responsePkt.length)
        } catch (e: Exception) {
            SecureLog.w("DohClient", "UDP fallback failed", e)
            null
        }
    }
}
