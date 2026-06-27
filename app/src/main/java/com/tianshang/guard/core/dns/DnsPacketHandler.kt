package com.tianshang.guard.core.dns

import java.nio.ByteBuffer

class DnsPacketHandler {

    fun isDnsQuery(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 20) return false
        val version = buffer.get(0).toInt() shr 4 and 0xF
        if (version != 4 && version != 6) return false
        val headerLen = if (version == 4) (buffer.get(0).toInt() and 0xF) * 4 else 40
        if (buffer.remaining() < headerLen + 8) return false
        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return false
        val udpDstPort = buffer.getShort(headerLen + 2).toInt() and 0xFFFF
        return udpDstPort == 53
    }

    fun extractDomain(buffer: ByteBuffer): String {
        val version = buffer.get(0).toInt() shr 4 and 0xF
        val ipHeaderLen = if (version == 4) (buffer.get(0).toInt() and 0xF) * 4 else 40
        val udpHeaderLen = 8
        val dnsOffset = ipHeaderLen + udpHeaderLen

        if (buffer.remaining() < dnsOffset + 12) return ""

        val sb = StringBuilder()
        var pos = dnsOffset + 12

        while (true) {
            if (pos >= buffer.remaining()) break
            val len = buffer.get(pos).toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) {
                val offset = ((len and 0x3F) shl 8) or (buffer.get(pos + 1).toInt() and 0xFF)
                return sb.toString() + resolveCompressedName(buffer, dnsOffset, offset)
            }
            pos++
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append((buffer.get(pos).toInt() and 0xFF).toChar())
                pos++
            }
        }
        return sb.toString()
    }

    private fun resolveCompressedName(buffer: ByteBuffer, dnsOffset: Int, offset: Int, depth: Int = 0): String {
        if (depth > MAX_COMPRESSION_DEPTH) return ""
        if (offset < 0 || dnsOffset + offset >= buffer.remaining()) return ""

        val sb = StringBuilder()
        var pos = dnsOffset + offset
        while (true) {
            if (pos >= buffer.remaining()) break
            val len = buffer.get(pos).toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) {
                if (pos + 1 >= buffer.remaining()) break
                val nextOffset = ((len and 0x3F) shl 8) or (buffer.get(pos + 1).toInt() and 0xFF)
                return sb.toString() + resolveCompressedName(buffer, dnsOffset, nextOffset, depth + 1)
            }
            pos++
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                if (pos >= buffer.remaining()) return sb.toString()
                sb.append((buffer.get(pos).toInt() and 0xFF).toChar())
                pos++
            }
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_COMPRESSION_DEPTH = 10
    }

    fun extractDnsPayload(buffer: ByteBuffer): ByteArray {
        val version = buffer.get(0).toInt() shr 4 and 0xF
        val ipHeaderLen = if (version == 4) (buffer.get(0).toInt() and 0xF) * 4 else 40
        val udpOffset = ipHeaderLen + 8
        val payloadLen = buffer.remaining() - udpOffset
        val payload = ByteArray(payloadLen)
        buffer.position(udpOffset)
        buffer.get(payload)
        buffer.rewind()
        return payload
    }

    fun buildNxDomainResponse(query: ByteBuffer): ByteBuffer {
        val version = query.get(0).toInt() shr 4 and 0xF
        val ipHeaderLen = if (version == 4) (query.get(0).toInt() and 0xF) * 4 else 40
        val udpOffset = ipHeaderLen
        val dnsOffset = ipHeaderLen + 8
        val totalLen = query.getShort(2).toInt() and 0xFFFF

        val response = ByteBuffer.allocate(totalLen)
        query.rewind()
        response.put(query.array(), 0, totalLen)
        response.rewind()

        // Swap src/dst IP addresses
        swapIpAddresses(response, version)

        // Swap UDP ports
        val srcPort = response.getShort(udpOffset)
        val dstPort = response.getShort(udpOffset + 2)
        response.putShort(udpOffset, dstPort)
        response.putShort(udpOffset + 2, srcPort)

        // DNS header: preserve ID, set QR=1, preserve RD, set RA=1, RCODE=3 (NXDOMAIN)
        val queryFlags = query.getShort(dnsOffset + 2).toInt() and 0xFFFF
        val rd = queryFlags and 0x0100
        val responseFlags = 0x8000 or 0x0080 or 0x0003 or rd
        response.putShort(dnsOffset, query.getShort(dnsOffset))
        response.putShort(dnsOffset + 2, responseFlags.toShort())
        response.putShort(dnsOffset + 4, query.getShort(dnsOffset + 4))
        response.putShort(dnsOffset + 6, 0)
        response.putShort(dnsOffset + 8, 0)
        response.putShort(dnsOffset + 10, 0)

        // Recalculate IP checksum (IPv4 only)
        if (version == 4) {
            response.putShort(2, totalLen.toShort())
            response.putShort(10, 0)
            response.putShort(10, computeIpChecksum(response, ipHeaderLen))
        }

        // Clear UDP checksum
        val udpLen = totalLen - ipHeaderLen
        response.putShort(ipHeaderLen + 4, udpLen.toShort())
        response.putShort(ipHeaderLen + 6, 0)

        response.rewind()
        return response
    }

    fun buildResponseFromUpstream(query: ByteBuffer, upstreamResponse: ByteBuffer): ByteBuffer {
        val version = query.get(0).toInt() shr 4 and 0xF
        val ipHeaderLen = if (version == 4) (query.get(0).toInt() and 0xF) * 4 else 40
        val udpOffset = ipHeaderLen

        val upstreamLen = upstreamResponse.remaining()
        val udpPayloadLen = 8 + upstreamLen
        val newTotalLen = ipHeaderLen + udpPayloadLen

        val response = ByteBuffer.allocate(newTotalLen)
        query.rewind()
        response.put(query.array(), 0, ipHeaderLen)
        response.rewind()

        // Swap src/dst IP addresses
        swapIpAddresses(response, version)

        // Update total length
        response.putShort(2, newTotalLen.toShort())

        // Recalculate IP checksum (IPv4 only)
        if (version == 4) {
            response.putShort(10, 0)
            response.putShort(10, computeIpChecksum(response, ipHeaderLen))
        }

        // UDP header: swap ports, set length, zero checksum
        val srcPort = query.getShort(udpOffset)
        val dstPort = query.getShort(udpOffset + 2)
        response.position(udpOffset)
        response.putShort(dstPort)
        response.putShort(srcPort)
        response.putShort(udpPayloadLen.toShort())
        response.putShort(0)

        // DNS payload
        response.position(udpOffset + 8)
        response.put(upstreamResponse)

        response.rewind()
        return response
    }

    private fun swapIpAddresses(buffer: ByteBuffer, version: Int) {
        if (version == 4) {
            val src = buffer.getInt(12)
            val dst = buffer.getInt(16)
            buffer.putInt(12, dst)
            buffer.putInt(16, src)
        } else {
            val srcBytes = ByteArray(16)
            val dstBytes = ByteArray(16)
            buffer.position(8); buffer.get(srcBytes)
            buffer.position(24); buffer.get(dstBytes)
            buffer.position(8); buffer.put(dstBytes)
            buffer.position(24); buffer.put(srcBytes)
        }
    }

    private fun computeIpChecksum(buffer: ByteBuffer, headerLen: Int): Short {
        var sum = 0L
        for (i in 0 until headerLen step 2) {
            sum += (buffer.get(i).toInt() and 0xFF) shl 8 or (buffer.get(i + 1).toInt() and 0xFF)
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }
}
