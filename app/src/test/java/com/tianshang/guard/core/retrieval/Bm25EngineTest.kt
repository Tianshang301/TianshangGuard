package com.tianshang.guard.core.retrieval

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

class Bm25EngineTest : BaseUnitTest() {

    private lateinit var engine: Bm25Engine

    @Before
    fun setUp() {
        engine = Bm25Engine()
    }

    @Test
    fun `query returns empty result before load`() {
        val result = engine.query("test")
        Assert.assertEquals(0f, result.phishingRatio, 0.001f)
        Assert.assertEquals(0, result.matchCount)
    }

    @Test
    fun `loadFromAssets loads index successfully`() {
        val data = createTestIndex()
        val loaded = engine.loadFromAssets(ByteArrayInputStream(data))
        Assert.assertTrue(loaded)
        Assert.assertTrue(engine.isReady())
    }

    @Test
    fun `query returns matches after loading`() {
        val data = createTestIndex()
        engine.loadFromAssets(ByteArrayInputStream(data))
        val result = engine.query("转账")
        Assert.assertTrue(result.matchCount > 0)
    }

    @Test
    fun `query returns zero for non-matching term`() {
        val data = createTestIndex()
        engine.loadFromAssets(ByteArrayInputStream(data))
        val result = engine.query("xyznotfound")
        Assert.assertEquals(0, result.matchCount)
    }

    @Test
    fun `addFeedbackDocument adds to feedback index`() {
        val data = createTestIndex()
        engine.loadFromAssets(ByteArrayInputStream(data))
        Assert.assertEquals(0, engine.getFeedbackDocCount())

        engine.addFeedbackDocument("紧急通知", true)
        Assert.assertEquals(1, engine.getFeedbackDocCount())
    }

    @Test
    fun `query includes feedback documents`() {
        val data = createTestIndex()
        engine.loadFromAssets(ByteArrayInputStream(data))
        engine.addFeedbackDocument("紧急转账通知", true)
        val result = engine.query("紧急")
        Assert.assertTrue(result.matchCount > 0)
    }

    @Test
    fun `clearFeedbackIndex resets dynamic index`() {
        val data = createTestIndex()
        engine.loadFromAssets(ByteArrayInputStream(data))
        engine.addFeedbackDocument("test", true)
        Assert.assertEquals(1, engine.getFeedbackDocCount())
        engine.clearFeedbackIndex()
        Assert.assertEquals(0, engine.getFeedbackDocCount())
    }

    @Test
    fun `getDocCount includes static and dynamic docs`() {
        val data = createTestIndex()
        engine.loadFromAssets(ByteArrayInputStream(data))
        val staticCount = engine.getDocCount()
        engine.addFeedbackDocument("test", true)
        Assert.assertEquals(staticCount + 1, engine.getDocCount())
    }

    private fun createTestIndex(): ByteArray {
        val buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

        // Header: 2 docs
        buf.putInt(2)

        // Doc labels: doc 0 = safe (0), doc 1 = phishing (1)
        buf.put(0.toByte())
        buf.put(1.toByte())

        // Token "转账": appears in doc 0 (score 5) and doc 1 (score 50)
        appendToken(buf, "转账", listOf(PostingEntry(0, 5), PostingEntry(1, 50)))

        // Token "安全账户": appears in doc 1 (score 80)
        appendToken(buf, "安全账户", listOf(PostingEntry(1, 80)))

        // Token "正常": appears in doc 0 (score 20)
        appendToken(buf, "正常", listOf(PostingEntry(0, 20)))

        // Token "紧急通知": appears in doc 1 (score 60)
        appendToken(buf, "紧急通知", listOf(PostingEntry(1, 60)))

        val raw = ByteArray(buf.position())
        buf.flip()
        buf.get(raw)

        // Compress with zlib
        val deflater = Deflater()
        deflater.setInput(raw)
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            compressed.write(buffer, 0, count)
        }
        deflater.end()
        return compressed.toByteArray()
    }

    private fun appendToken(buf: ByteBuffer, token: String, postings: List<PostingEntry>) {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        buf.putShort(tokenBytes.size.toShort())
        buf.put(tokenBytes)
        buf.putInt(postings.size)
        for (p in postings) {
            buf.putInt(p.docId)
            buf.putShort(p.score.toShort())
        }
    }

    private data class PostingEntry(val docId: Int, val score: Int)
}
