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
import com.tianshang.guard.ui.main.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class GuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var isRestarting = false
    @Volatile private var isDestroying = false

    private val dnsEngine: DnsEngine by inject()

    private val packetHandler = DnsPacketHandler()

    private val dnsCache = ConcurrentHashMap<String, DnsResult>(2048)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepaliveJob: Job? = null
    private var watchdogJob: Job? = null
    private var lastPacketTime = 0L
    private val handlerThread = Thread(null, ::handlePackets, "VpnPacketHandler")

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
                    android.util.Log.i("GuardVpnService", "Recreated by system (START_STICKY), restoring VPN")
                    startVpn()
                }
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        android.util.Log.i("GuardVpnService", "Starting VPN...")
        val builder = Builder().apply {
            addAddress("198.18.0.1", 15)
            addDnsServer("198.18.0.2")
            addRoute("198.18.0.0", 15)
            setBlocking(true)
        }

        vpnInterface = builder.establish() ?: run {
            android.util.Log.e("GuardVpnService", "establish() returned null")
            return
        }
        running = true
        lastPacketTime = System.currentTimeMillis()
        android.util.Log.i("GuardVpnService", "VPN established")

        dnsEngine.start()
        android.util.Log.i("GuardVpnService", "DnsEngine started")

        startForeground(NOTIFICATION_ID, createNotification())

        handlerThread.setUncaughtExceptionHandler { _, e ->
            android.util.Log.e("GuardVpnService", "Handler thread crashed unexpectedly", e)
            runOnMainThread { restartVpn() }
        }
        handlerThread.start()

        keepaliveJob = serviceScope.launch { runKeepaliveLoop() }
        watchdogJob = serviceScope.launch { runWatchdogLoop() }
    }

    private fun handlePackets() {
        val fd = vpnInterface?.fileDescriptor ?: run {
            android.util.Log.e("GuardVpnService", "handlePackets: fd is null")
            return
        }
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val packet = ByteArray(1500)
        var consecutiveErrors = 0

        android.util.Log.i("GuardVpnService", "Packet handler started")
        while (running) {
            try {
                val length = input.read(packet)
                if (length <= 0) {
                    if (++consecutiveErrors > 100) {
                        android.util.Log.w("GuardVpnService", "Too many consecutive empty reads, restarting VPN")
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

                val result = synchronized(dnsCache) {
                    dnsCache.getOrPut(domain) { dnsEngine.resolve(domain) }
                }

                val response = if (result is DnsResult.Block) {
                    android.util.Log.w("GuardVpnService", "Blocked: $domain")
                    packetHandler.buildNxDomainResponse(buffer)
                } else {
                    forwardToUpstreamDns(buffer)
                }

                if (response != null) {
                    output.write(response.array(), response.arrayOffset(), response.limit())
                }
            } catch (e: java.io.EOFException) {
                android.util.Log.w("GuardVpnService", "VPN interface closed (EOF)")
                runOnMainThread { restartVpn() }
                break
            } catch (e: java.io.InterruptedIOException) {
                android.util.Log.w("GuardVpnService", "VPN interface I/O interrupted")
                if (!running) break
                continue
            } catch (e: Exception) {
                android.util.Log.e("GuardVpnService", "Packet handler error", e)
                if (++consecutiveErrors > 50) {
                    android.util.Log.w("GuardVpnService", "Too many packet handler errors, restarting VPN")
                    runOnMainThread { restartVpn() }
                    break
                }
            }
        }
        android.util.Log.i("GuardVpnService", "Packet handler stopped")
    }

    private suspend fun runKeepaliveLoop() {
        android.util.Log.i("GuardVpnService", "Keepalive loop started")
        while (running) {
            sendKeepaliveQuery()
            delay(KEEPALIVE_INTERVAL_MS)
        }
        android.util.Log.i("GuardVpnService", "Keepalive loop stopped")
    }

    private suspend fun runWatchdogLoop() {
        android.util.Log.i("GuardVpnService", "Watchdog loop started")
        while (running) {
            delay(WATCHDOG_INTERVAL_MS)
            val idle = System.currentTimeMillis() - lastPacketTime
            if (idle > IDLE_TIMEOUT_MS && running) {
                android.util.Log.w("GuardVpnService",
                    "No packets for ${idle / 1000}s, restarting VPN")
                runOnMainThread { restartVpn() }
                break
            }
        }
        android.util.Log.i("GuardVpnService", "Watchdog loop stopped")
    }

    private fun sendKeepaliveQuery() {
        if (!running) return
        try {
            val id = (System.currentTimeMillis() and 0xFFFF).toShort()
            val query = ByteBuffer.allocate(512)
            query.putShort(id)
            query.putShort(0x0100)
            query.putShort(1); query.putShort(0); query.putShort(0); query.putShort(0)
            query.put(9); query.put("keepalive".toByteArray(Charsets.US_ASCII))
            query.put(10); query.put("tianshang".toByteArray(Charsets.US_ASCII))
            query.put(5); query.put("local".toByteArray(Charsets.US_ASCII))
            query.put(0)
            query.putShort(1); query.putShort(1)

            DatagramSocket().use { socket ->
                socket.soTimeout = 2000
                val request = DatagramPacket(
                    query.array(), query.position(),
                    InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT
                )
                socket.send(request)
            }
        } catch (e: Exception) {
            android.util.Log.v("GuardVpnService", "Keepalive error (normal if idle)", e)
        }
    }

    private fun forwardToUpstreamDns(query: ByteBuffer): ByteBuffer? {
        return try {
            val dnsPayload = packetHandler.extractDnsPayload(query)
            DatagramSocket().use { socket ->
                socket.soTimeout = DNS_TIMEOUT_MS
                val request = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT)
                socket.send(request)
                val responseBuf = ByteArray(1500)
                val responsePkt = DatagramPacket(responseBuf, responseBuf.size)
                socket.receive(responsePkt)
                val upstreamResponse = ByteBuffer.wrap(responsePkt.data, 0, responsePkt.length)
                packetHandler.buildResponseFromUpstream(query, upstreamResponse)
            }
        } catch (e: Exception) {
            android.util.Log.w("GuardVpnService", "Upstream DNS error", e)
            null
        }
    }

    private fun restartVpn() {
        synchronized(this) {
            if (isRestarting || isDestroying) return
            isRestarting = true
        }
        android.util.Log.i("GuardVpnService", "Restarting VPN...")

        teardownInternal()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            synchronized(this) {
                isRestarting = false
            }
            startVpn()
        }, 500)
    }

    private fun stopVpn() {
        synchronized(this) {
            if (!running && vpnInterface == null) return
            running = false
        }
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
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
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
        if (!isDestroying) {
            isDestroying = true
            stopVpn()
        }
    }
}
