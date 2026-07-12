package com.tianshang.guard.core.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class DnsPacketHandlerTest {

    private lateinit var handler: DnsPacketHandler

    @Before
    fun setUp() {
        handler = DnsPacketHandler()
    }

    @Test
    fun `isDnsQuery returns true for valid IPv4 UDP packet to port 53`() {
        val packet = buildIpv4DnsQuery(domain = "example.com")
        assertTrue(handler.isDnsQuery(packet))
    }

    @Test
    fun `isDnsQuery returns false for too short packet`() {
        val packet = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        assertFalse(handler.isDnsQuery(packet))
    }

    @Test
    fun `isDnsQuery returns false for non-UDP protocol`() {
        val packet = buildIpv4Packet(protocol = 6, payload = ByteArray(8)) // TCP
        assertFalse(handler.isDnsQuery(packet))
    }

    @Test
    fun `isDnsQuery returns false for non-DNS port`() {
        val packet = buildIpv4Packet(
            protocol = 17,
            payload = buildUdpPacket(dstPort = 80, dnsPayload = ByteArray(12))
        )
        assertFalse(handler.isDnsQuery(packet))
    }

    @Test
    fun `isDnsQuery returns true for valid IPv6 UDP packet to port 53`() {
        val packet = buildIpv6DnsQuery(domain = "test.org")
        assertTrue(handler.isDnsQuery(packet))
    }

    @Test
    fun `extractDomain parses simple domain`() {
        val packet = buildIpv4DnsQuery(domain = "example.com")
        assertEquals("example.com", handler.extractDomain(packet))
    }

    @Test
    fun `extractDomain parses multi-level domain`() {
        val packet = buildIpv4DnsQuery(domain = "sub.domain.example.com")
        assertEquals("sub.domain.example.com", handler.extractDomain(packet))
    }

    @Test
    fun `extractDomain returns empty for packet with no DNS section`() {
        val packet = ByteBuffer.allocate(20)
        packet.put(0, 0x45.toByte()) // IPv4, IHL=5
        packet.putShort(2, 20)       // total length
        packet.put(9, 17)            // UDP
        assertEquals("", handler.extractDomain(packet))
    }

    @Test
    fun `extractDnsPayload extracts correct bytes`() {
        val dnsPayload = byteArrayOf(0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val packet = buildIpv4Packet(protocol = 17, payload = buildUdpPacket(dstPort = 53, dnsPayload = dnsPayload))
        val extracted = handler.extractDnsPayload(packet)
        assertArrayEquals(dnsPayload, extracted)
    }

    @Test
    fun `extractDnsPayload returns empty for truncated packet`() {
        val packet = ByteBuffer.allocate(20)
        val extracted = handler.extractDnsPayload(packet)
        assertEquals(0, extracted.size)
    }

    @Test
    fun `buildNxDomainResponse returns valid NXDOMAIN`() {
        val query = buildIpv4DnsQuery(domain = "evil-phishing.com")
        val response = handler.buildNxDomainResponse(query)

        val dnsOffset = 28 // 20 IPv4 + 8 UDP
        val flags = response.getShort(dnsOffset + 2).toInt() and 0xFFFF
        assertTrue((flags and 0x8000) != 0)    // QR = 1 (response)
        assertEquals(3, flags and 0x000F)       // RCODE = 3 (NXDOMAIN)
    }

    @Test
    fun `buildNxDomainResponse swaps IP addresses`() {
        val query = buildIpv4DnsQuery(domain = "test.com")
        val srcBefore = query.getInt(12)
        val dstBefore = query.getInt(16)

        val response = handler.buildNxDomainResponse(query)
        assertEquals(dstBefore, response.getInt(12)) // new src = old dst
        assertEquals(srcBefore, response.getInt(16)) // new dst = old src
    }

    @Test
    fun `validateDnsResponse returns true for matching response`() {
        val queryId = 0x1234.toShort()
        val queryBuffer = ByteBuffer.allocate(256)
        queryBuffer.putShort(0, queryId)
        queryBuffer.putShort(2, 0x0100) // standard query, RD=1
        queryBuffer.putShort(4, 1)      // QDCOUNT=1
        queryBuffer.putShort(6, 0)
        queryBuffer.putShort(8, 0)
        queryBuffer.putShort(10, 0)
        queryBuffer.position(12) // move past header for question section
        // dummy question
        queryBuffer.put(3) // length of "www"
        queryBuffer.put("www".toByteArray())
        queryBuffer.put(7) // length of "example"
        queryBuffer.put("example".toByteArray())
        queryBuffer.put(3) // length of "com"
        queryBuffer.put("com".toByteArray())
        queryBuffer.put(0) // terminator
        queryBuffer.putShort(1)   // QTYPE=A
        queryBuffer.putShort(1)   // QCLASS=IN
        queryBuffer.rewind()
        queryBuffer.limit(queryBuffer.capacity())

        val responseBuffer = ByteBuffer.allocate(12)
        responseBuffer.putShort(0, queryId)
        responseBuffer.putShort(2, 0x8180.toShort()) // QR=1, RD=1, RA=1
        responseBuffer.putShort(4, 1)
        responseBuffer.putShort(6, 1)
        responseBuffer.putShort(8, 0)
        responseBuffer.putShort(10, 0)
        responseBuffer.rewind()
        responseBuffer.limit(responseBuffer.capacity())

        assertTrue(handler.validateDnsResponse(queryBuffer, responseBuffer))
    }

    @Test
    fun `validateDnsResponse returns false on ID mismatch`() {
        val queryBuf = ByteBuffer.allocate(12)
        queryBuf.putShort(0, 0xAAAA.toShort())
        queryBuf.rewind()
        queryBuf.limit(12)

        val responseBuf = ByteBuffer.allocate(12)
        responseBuf.putShort(0, 0xBBBB.toShort())
        responseBuf.putShort(2, 0x8180.toShort())
        responseBuf.rewind()
        responseBuf.limit(12)

        assertFalse(handler.validateDnsResponse(queryBuf, responseBuf))
    }

    @Test
    fun `validateDnsResponse returns false on short response`() {
        val responseBuf = ByteBuffer.allocate(4)
        assertFalse(handler.validateDnsResponse(ByteBuffer.allocate(12), responseBuf))
    }

    // ── Helper: build DNS query domain section ──
    private fun encodeDnsName(domain: String): ByteArray {
        val parts = domain.split(".")
        val baos = java.io.ByteArrayOutputStream()
        for (part in parts) {
            baos.write(part.length)
            baos.write(part.toByteArray())
        }
        baos.write(0)
        return baos.toByteArray()
    }

    private fun buildDnsQueryBytes(domain: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val id: Short = 0x1234
        baos.write((id.toInt() shr 8) and 0xFF)
        baos.write(id.toInt() and 0xFF)
        baos.write(0x01); baos.write(0x00) // flags: standard query, RD=1
        baos.write(0x00); baos.write(0x01) // QDCOUNT=1
        baos.write(0x00); baos.write(0x00) // ANCOUNT=0
        baos.write(0x00); baos.write(0x00) // NSCOUNT=0
        baos.write(0x00); baos.write(0x00) // ARCOUNT=0
        baos.write(encodeDnsName(domain))
        baos.write(0x00); baos.write(0x01) // QTYPE=A
        baos.write(0x00); baos.write(0x01) // QCLASS=IN
        return baos.toByteArray()
    }

    private fun buildUdpPacket(dstPort: Int, dnsPayload: ByteArray): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        baos.write(0x12); baos.write(0x34) // src port
        baos.write((dstPort shr 8) and 0xFF); baos.write(dstPort and 0xFF)
        val udpLen = 8 + dnsPayload.size
        baos.write((udpLen shr 8) and 0xFF); baos.write(udpLen and 0xFF)
        baos.write(0x00); baos.write(0x00) // checksum
        baos.write(dnsPayload)
        return baos.toByteArray()
    }

    private fun buildIpv4Packet(protocol: Int, payload: ByteArray): ByteBuffer {
        val totalLen = 20 + payload.size
        val buf = ByteBuffer.allocate(totalLen)
        buf.put(0, 0x45.toByte())            // version=4, IHL=5
        buf.putShort(2, totalLen.toShort())   // total length
        buf.put(9, protocol.toByte())         // protocol
        buf.putShort(12, 0xC0A8.toShort())    // src IP 192.168.0.1
        buf.putShort(14, 0x0001.toShort())
        buf.putShort(16, 0x0808.toShort())    // dst IP 8.8.8.8
        buf.putShort(18, 0x0808.toShort())
        buf.position(20)
        buf.put(payload)
        buf.rewind()
        return buf
    }

    private fun buildIpv4DnsQuery(domain: String): ByteBuffer {
        val dnsPayload = buildDnsQueryBytes(domain)
        val udpPayload = buildUdpPacket(dstPort = 53, dnsPayload = dnsPayload)
        return buildIpv4Packet(protocol = 17, payload = udpPayload)
    }

    private fun buildIpv6Packet(payload: ByteArray): ByteBuffer {
        val totalLen = 40 + payload.size
        val buf = ByteBuffer.allocate(totalLen)
        buf.put(0, 0x60.toByte())             // version=6, traffic class=0
        buf.putInt(4, payload.size)           // payload length
        buf.put(6, 17)                        // next header = UDP
        buf.position(8)
        // src IP 2001:db8::1
        for (b in listOf(0x20, 0x01, 0x0D, 0xB8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)) {
            buf.put(b.toByte())
        }
        // dst IP 2001:db8::2
        for (b in listOf(0x20, 0x01, 0x0D, 0xB8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)) {
            buf.put(b.toByte())
        }
        buf.put(payload)
        buf.rewind()
        return buf
    }

    private fun buildIpv6DnsQuery(domain: String): ByteBuffer {
        val dnsPayload = buildDnsQueryBytes(domain)
        val udpPayload = buildUdpPacket(dstPort = 53, dnsPayload = dnsPayload)
        return buildIpv6Packet(udpPayload)
    }
}
