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
import org.koin.android.ext.android.inject

class GuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    private val dnsEngine: DnsEngine by inject()

    private val packetHandler = DnsPacketHandler()

    private val dnsCache = object : LinkedHashMap<String, DnsResult>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DnsResult>?): Boolean {
            return size > 2048
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "guard_vpn_channel"
        const val ACTION_START = "com.tianshang.guard.START_VPN"
        const val ACTION_STOP = "com.tianshang.guard.STOP_VPN"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 3000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
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
        android.util.Log.i("GuardVpnService", "VPN established")

        dnsEngine.start()
        android.util.Log.i("GuardVpnService", "DnsEngine started")

        startForeground(NOTIFICATION_ID, createNotification())
        Thread(null, ::handlePackets, "VpnPacketHandler").start()
    }

    private fun handlePackets() {
        val fd = vpnInterface?.fileDescriptor ?: run {
            android.util.Log.e("GuardVpnService", "handlePackets: fd is null")
            return
        }
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val packet = ByteArray(1500)

        android.util.Log.i("GuardVpnService", "Packet handler started")
        while (running) {
            try {
                val length = input.read(packet)
                if (length <= 0) continue

                val buffer = ByteBuffer.wrap(packet, 0, length)
                if (!packetHandler.isDnsQuery(buffer)) continue

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
                    output.write(response.array(), 0, response.remaining())
                }
            } catch (e: Exception) {
                android.util.Log.e("GuardVpnService", "Packet handler error", e)
            }
        }
        android.util.Log.i("GuardVpnService", "Packet handler stopped")
    }

    private fun forwardToUpstreamDns(query: ByteBuffer): ByteBuffer? {
        return try {
            val dnsPayload = packetHandler.extractDnsPayload(query)
            val socket = DatagramSocket()
            socket.soTimeout = DNS_TIMEOUT_MS
            val request = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT)
            socket.send(request)
            val responseBuf = ByteArray(1500)
            val responsePkt = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(responsePkt)
            socket.close()
            val upstreamResponse = ByteBuffer.wrap(responsePkt.data, 0, responsePkt.length)
            packetHandler.buildResponseFromUpstream(query, upstreamResponse)
        } catch (e: Exception) {
            android.util.Log.w("GuardVpnService", "Upstream DNS error", e)
            null
        }
    }

    private fun stopVpn() {
        running = false
        dnsEngine.stop()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        stopVpn()
    }
}
