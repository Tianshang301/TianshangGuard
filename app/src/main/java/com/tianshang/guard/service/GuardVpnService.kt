package com.tianshang.guard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.tianshang.guard.R
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.dns.DnsPacketHandler
import com.tianshang.guard.core.dns.DnsResult
import com.tianshang.guard.core.dns.DohClient
import com.tianshang.guard.core.util.SecureLog
import com.tianshang.guard.ui.main.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class GuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var isRestarting = false
    @Volatile private var isDestroying = false

    private val dnsEngine: DnsEngine by inject()
    private val dohClient: DohClient by inject()

    private val packetHandler = DnsPacketHandler()
    // L-9: Secure random for DNS transaction IDs
    private val secureRandom = java.security.SecureRandom()

    // VPN-03: DNS cache with TTL and size limit
    private data class CacheEntry(val result: DnsResult, val timestamp: Long)
    private val dnsCache = object : LinkedHashMap<String, CacheEntry>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > 2048
        }
    }
    private val CACHE_TTL_MS = 300_000L // 5 minutes

    // H-02: Bounded async resolution. The single read loop only reads packets and
    // dispatches resolution to a fixed thread pool; responses are written back
    // under a single lock. This prevents a flood of (cache-missing) queries from
    // serially blocking the one packet-handling thread and stalling all DNS.
    private val resolutionExecutor = Executors.newFixedThreadPool(POOL_SIZE)
    private val inflight = Semaphore(MAX_INFLIGHT, true)
    private val perSourceLimiters = ConcurrentHashMap<String, TokenBucket>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepaliveJob: Job? = null
    private var watchdogJob: Job? = null
    @Volatile private var lastPacketTime = 0L // VPN-05: Thread-safe
    // BUGFIX: Create new Thread on each startVpn() - Thread can only be started once
    private var handlerThread: Thread? = null
    // C-5: Store restart runnable so it can be cancelled on stop
    private var restartRunnable: Runnable? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "guard_vpn_channel"
        const val ACTION_START = "com.tianshang.guard.START_VPN"
        const val ACTION_STOP = "com.tianshang.guard.STOP_VPN"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 3000
        private const val KEEPALIVE_INTERVAL_MS = 30_000L
        private const val IDLE_TIMEOUT_MS = 120_000L
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        // H-02: concurrency / overload bounds
        private const val POOL_SIZE = 8
        private const val MAX_INFLIGHT = 32
        private const val PER_SOURCE_CAPACITY = 64.0
        private const val PER_SOURCE_REFILL_PER_MS = 0.064 // ~64 req/sec refill
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            null -> {
                if (!running && vpnInterface == null) {
                    SecureLog.i("GuardVpnService", "Recreated by system (START_STICKY), restoring VPN")
                    startVpn()
                }
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        SecureLog.i("GuardVpnService", "Starting VPN...")
        val builder = Builder().apply {
            addAddress("198.18.0.1", 15)
            addDnsServer("198.18.0.2")
            addRoute("198.18.0.0", 15)
            setBlocking(true)
        }

        vpnInterface = builder.establish() ?: run {
            SecureLog.e("GuardVpnService", "establish() returned null")
            return
        }
        running = true
        lastPacketTime = System.currentTimeMillis()
        SecureLog.i("GuardVpnService", "VPN established")

        runBlocking { dnsEngine.start() }
        SecureLog.i("GuardVpnService", "DnsEngine started")

        startForeground(NOTIFICATION_ID, createNotification())

        // BUGFIX: Create a new thread each time (Thread can only start once)
        handlerThread = Thread(null, ::handlePackets, "VpnPacketHandler").apply {
            setUncaughtExceptionHandler { _, e ->
                SecureLog.e("GuardVpnService", "Handler thread crashed unexpectedly", e)
                runOnMainThread { restartVpn() }
            }
            start()
        }

        keepaliveJob = serviceScope.launch { runKeepaliveLoop() }
        watchdogJob = serviceScope.launch { runWatchdogLoop() }
    }

    private fun handlePackets() {
        val fd = vpnInterface?.fileDescriptor ?: run {
            SecureLog.e("GuardVpnService", "handlePackets: fd is null")
            return
        }
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val packet = ByteArray(1500)
        var consecutiveErrors = 0

        SecureLog.i("GuardVpnService", "Packet handler started")
        try {
            while (running) {
                try {
                    val length = input.read(packet)
                    if (length <= 0) {
                        if (++consecutiveErrors > 100) {
                            SecureLog.w("GuardVpnService", "Too many consecutive empty reads, restarting VPN")
                            runOnMainThread { restartVpn() }
                            break
                        }
                        Thread.sleep(10)
                        continue
                    }
                    consecutiveErrors = 0

                    val buffer = ByteBuffer.wrap(packet, 0, length)
                    if (!packetHandler.isDnsQuery(buffer)) continue

                    lastPacketTime = System.currentTimeMillis()

                    val domain = packetHandler.extractDomain(buffer)
                    if (domain.isEmpty()) continue

                    // VPN-03: Cache lookup with TTL check (served synchronously; cheap).
                    val cached = synchronized(dnsCache) {
                        val entry = dnsCache[domain]
                        if (entry != null && System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                            entry.result
                        } else {
                            dnsCache.remove(domain)
                            null
                        }
                    }

                    if (cached != null) {
                        val response = if (cached is DnsResult.Block) {
                            SecureLog.w("GuardVpnService", "Blocked (cached): $domain")
                            packetHandler.buildNxDomainResponse(buffer)
                        } else {
                            forwardToUpstreamDns(buffer)
                        }
                        if (response != null) {
                            synchronized(output) {
                                output.write(response.array(), response.arrayOffset(), response.remaining())
                            }
                        }
                        continue
                    }

                    // Cache miss: enforce per-source rate limiting and a global
                    // in-flight cap before dispatching to the resolution pool.
                    val srcKey = extractSourceIp(buffer)
                    val bucket = perSourceLimiters.computeIfAbsent(srcKey) {
                        TokenBucket(PER_SOURCE_CAPACITY, PER_SOURCE_REFILL_PER_MS)
                    }
                    if (!bucket.tryAcquire()) {
                        SecureLog.w("GuardVpnService", "Per-source rate limit exceeded for $srcKey, returning SERVFAIL")
                        val sf = packetHandler.buildErrorResponse(buffer, 2)
                        synchronized(output) {
                            output.write(sf.array(), sf.arrayOffset(), sf.remaining())
                        }
                        continue
                    }
                    if (!inflight.tryAcquire()) {
                        SecureLog.w("GuardVpnService", "Resolution in-flight cap reached, returning SERVFAIL")
                        val sf = packetHandler.buildErrorResponse(buffer, 2)
                        synchronized(output) {
                            output.write(sf.array(), sf.arrayOffset(), sf.remaining())
                        }
                        continue
                    }

                    // H-02: copy the packet bytes — the read loop reuses `packet`.
                    val copy = ByteArray(length)
                    System.arraycopy(packet, 0, copy, 0, length)
                    resolutionExecutor.execute {
                        try {
                            val cbuf = ByteBuffer.wrap(copy)
                            val result = dnsEngine.resolve(domain)
                            synchronized(dnsCache) {
                                dnsCache[domain] = CacheEntry(result, System.currentTimeMillis())
                            }
                            val response = if (result is DnsResult.Block) {
                                SecureLog.w("GuardVpnService", "Blocked: $domain")
                                packetHandler.buildNxDomainResponse(cbuf)
                            } else {
                                forwardToUpstreamDns(cbuf)
                            }
                            if (response != null) {
                                synchronized(output) {
                                    output.write(response.array(), response.arrayOffset(), response.remaining())
                                }
                            }
                        } catch (e: Exception) {
                            SecureLog.e("GuardVpnService", "Resolution task error", e)
                        } finally {
                            inflight.release()
                        }
                    }
                } catch (e: java.io.EOFException) {
                    SecureLog.w("GuardVpnService", "VPN interface closed (EOF)")
                    runOnMainThread { restartVpn() }
                    break
                } catch (e: java.io.InterruptedIOException) {
                    SecureLog.w("GuardVpnService", "VPN interface I/O interrupted")
                    if (!running) break
                    continue
                } catch (e: Exception) {
                    SecureLog.e("GuardVpnService", "Packet handler error", e)
                    if (++consecutiveErrors > 50) {
                        SecureLog.w("GuardVpnService", "Too many packet handler errors, restarting VPN")
                        runOnMainThread { restartVpn() }
                        break
                    }
                }
            }
        } finally {
            // H-7: Close file streams in finally block
            try { input.close() } catch (_: Exception) {}
            try { output.close() } catch (_: Exception) {}
        }
        SecureLog.i("GuardVpnService", "Packet handler stopped")
    }

    private fun extractSourceIp(buffer: ByteBuffer): String {
        val version = buffer.get(0).toInt() shr 4 and 0xF
        return try {
            if (version == 4) {
                val b0 = buffer.get(12).toInt() and 0xFF
                val b1 = buffer.get(13).toInt() and 0xFF
                val b2 = buffer.get(14).toInt() and 0xFF
                val b3 = buffer.get(15).toInt() and 0xFF
                "$b0.$b1.$b2.$b3"
            } else {
                // IPv6: use first 8 bytes as a coarse key
                buildString {
                    for (i in 8 until 24 step 2) {
                        append((buffer.get(i).toInt() and 0xFF).toString(16))
                    }
                }
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private suspend fun runKeepaliveLoop() {
        SecureLog.i("GuardVpnService", "Keepalive loop started")
        while (running) {
            sendKeepaliveQuery()
            delay(KEEPALIVE_INTERVAL_MS)
        }
        SecureLog.i("GuardVpnService", "Keepalive loop stopped")
    }

    private suspend fun runWatchdogLoop() {
        SecureLog.i("GuardVpnService", "Watchdog loop started")
        while (running) {
            delay(WATCHDOG_INTERVAL_MS)
            val idle = System.currentTimeMillis() - lastPacketTime
            if (idle > IDLE_TIMEOUT_MS && running) {
                SecureLog.w("GuardVpnService",
                    "No packets for ${idle / 1000}s, restarting VPN")
                runOnMainThread { restartVpn() }
                break
            }
        }
        SecureLog.i("GuardVpnService", "Watchdog loop stopped")
    }

    private fun sendKeepaliveQuery() {
        if (!running) return
        try {
            // L-01: encrypted keepalive via DoH only. There is deliberately NO
            // plaintext UDP probe/fallback: a fixed plaintext query to 1.1.1.1:53
            // would leak a stable identifier and echo the downgrade behaviour the
            // audit flagged. If DoH is unreachable the keepalive simply no-ops.
            dohClient.sendKeepalive()
        } catch (e: Exception) {
            SecureLog.v("GuardVpnService", "Keepalive error (normal if idle)", e)
        }
    }

    private fun forwardToUpstreamDns(query: ByteBuffer): ByteBuffer? {
        return try {
            val dnsPayload = packetHandler.extractDnsPayload(query)

            // Use DoH (DNS over HTTPS). On total failure we return an explicit
            // SERVFAIL rather than silently dropping (or downgrading to plaintext
            // UDP), so the client fails fast and cannot be poisoned (C-02).
            val responseBytes = dohClient.resolve(dnsPayload)
            if (responseBytes == null) {
                SecureLog.w("GuardVpnService", "DNS resolution failed (all DoH endpoints); returning SERVFAIL")
                return packetHandler.buildErrorResponse(query, 2)
            }

            val upstreamResponse = ByteBuffer.wrap(responseBytes)

            // Validate DNS response integrity
            if (!packetHandler.validateDnsResponse(query, upstreamResponse)) {
                SecureLog.w("GuardVpnService", "DNS response validation failed, dropping")
                return null
            }

            packetHandler.buildResponseFromUpstream(query, upstreamResponse)
        } catch (e: Exception) {
            SecureLog.w("GuardVpnService", "Upstream DNS error", e)
            null
        }
    }

    private fun restartVpn() {
        synchronized(this) {
            if (isRestarting || isDestroying) return
            isRestarting = true
        }
        SecureLog.i("GuardVpnService", "Restarting VPN...")

        running = false
        // C-7: Only teardown resources, do NOT set isDestroying
        keepaliveJob?.cancel()
        keepaliveJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        dnsEngine.stop()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        // C-5: Store restart runnable so it can be cancelled
        restartRunnable = Runnable {
            synchronized(this) {
                isRestarting = false
            }
            if (!isDestroying) startVpn()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(restartRunnable!!, 500)
    }

    private fun stopVpn() {
        synchronized(this) {
            if (!running && vpnInterface == null) return
            running = false
        }
        // C-5: Cancel pending restart runnable
        restartRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        restartRunnable = null
        teardownInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun teardownInternal() {
        isDestroying = true
        keepaliveJob?.cancel()
        keepaliveJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        dnsEngine.stop()
        shutdownResolutionPool()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
    }

    private fun shutdownResolutionPool() {
        perSourceLimiters.clear()
        try {
            resolutionExecutor.shutdownNow()
        } catch (_: Exception) {}
    }

    private fun runOnMainThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.vpn_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isDestroying = true
        running = false
        // M-21: Always perform cleanup, cancel restart runnable
        restartRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        restartRunnable = null
        keepaliveJob?.cancel()
        watchdogJob?.cancel()
        dnsEngine.stop()
        shutdownResolutionPool()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}

/**
 * Simple thread-safe token-bucket rate limiter used to bound DNS queries per
 * source IP (H-02). Tokens refill continuously at [refillPerMs] tokens per ms up
 * to [capacity]; [tryAcquire] succeeds only when at least one token is available.
 */
private class TokenBucket(capacity: Double, private val refillPerMs: Double) {
    private val capacity = capacity
    @Volatile private var tokens = capacity
    @Volatile private var lastNs = System.nanoTime()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = System.nanoTime()
        val elapsedMs = (now - lastNs) / 1_000_000.0
        lastNs = now
        tokens = (tokens + elapsedMs * refillPerMs).coerceAtMost(capacity)
        return if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else {
            false
        }
    }
}
