package com.tianshang.guard.core.dns

import com.tianshang.guard.core.util.SecureLog
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * DNS over HTTPS (DoH) client for secure DNS resolution.
 *
 * Uses a list of DoH endpoints (default: Cloudflare, with AliDNS as an encrypted
 * fallback). If **all** encrypted endpoints fail or time out, this client returns
 * `null` — it does NOT fall back to plaintext UDP (audit finding C-02). The caller
 * (`GuardVpnService`) responds with a SERVFAIL instead, so a network-level
 * adversary who blocks DoH cannot silently downgrade resolution to spoofable
 * plaintext and poison the cache.
 */
class DohClient(
    private val client: OkHttpClient,
    private val endpoints: List<DohEndpoint> = DEFAULT_ENDPOINTS
) {

    companion object {
        private const val DOH_TIMEOUT_MS = 3000L
        private val DNS_MEDIA_TYPE = "application/dns-message".toMediaType()
        // Cloudflare certificate pins for the primary DoH endpoint.
        private val CLOUDFLARE_PINNER = CertificatePinner.Builder()
            .add("cloudflare-dns.com", "sha256/yio0sMlhW0kS4fJo1pJl0tF6TvG3EKGY0pmMgkGnVY=")
            .add("cloudflare-dns.com", "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY=")
            .build()

        private val DEFAULT_ENDPOINTS = listOf(
            DohEndpoint("https://cloudflare-dns.com/dns-query", CLOUDFLARE_PINNER),
            // AliDNS DoH (encrypted, system-CA validated) as a secondary endpoint.
            DohEndpoint("https://dns.alidns.com/dns-query", null)
        )
    }

    private val httpClients: Map<String, OkHttpClient> = endpoints.associate { endpoint ->
        val builder = client.newBuilder()
            .connectTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(DOH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        endpoint.pinner?.let { builder.certificatePinner(it) }
        endpoint.url to builder.build()
    }

    /**
     * Resolve a DNS query over DoH. Returns raw DNS response bytes (wire format),
     * or `null` if every configured DoH endpoint failed.
     */
    fun resolve(dnsPayload: ByteArray): ByteArray? {
        for (endpoint in endpoints) {
            try {
                val response = resolveViaDoh(endpoint.url, dnsPayload)
                if (response != null) return response
            } catch (e: Exception) {
                SecureLog.w("DohClient", "DoH failed for ${endpoint.url}, trying next endpoint", e)
            }
        }
        SecureLog.w("DohClient", "All DoH endpoints failed; refusing plaintext fallback (C-02)")
        return null
    }

    private fun resolveViaDoh(url: String, dnsPayload: ByteArray): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-message")
            .post(dnsPayload.toRequestBody(DNS_MEDIA_TYPE))
            .build()

        val response = httpClients.getValue(url).newCall(request).execute()
        response.use { resp ->
            if (resp.isSuccessful) {
                return resp.body?.bytes()
            } else {
                SecureLog.w("DohClient", "DoH request failed ($url): ${resp.code}")
                return null
            }
        }
    }

    /**
     * L-01: Encrypted keepalive. Instead of a spoofable plaintext UDP ping to
     * 1.1.1.1:53, send a benign DNS query over TLS to a DoH endpoint so the
     * upstream connection / TLS session stays warm. Returns true if at least one
     * endpoint answered successfully.
     */
    fun sendKeepalive(): Boolean {
        for (endpoint in endpoints) {
            try {
                val url = endpoint.url + "?name=keepalive.cloudflare-dns.com&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .get()
                    .build()
                httpClients.getValue(endpoint.url).newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) return true
                }
            } catch (e: Exception) {
                SecureLog.w("DohClient", "Keepalive failed for ${endpoint.url}", e)
            }
        }
        return false
    }
}

data class DohEndpoint(
    val url: String,
    val pinner: CertificatePinner? = null
)
