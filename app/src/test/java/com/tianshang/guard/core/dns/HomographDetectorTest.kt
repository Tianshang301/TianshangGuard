package com.tianshang.guard.core.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomographDetectorTest {

    @Test
    fun `clean domain returns Clean`() {
        val result = HomographDetector.detect("example.com")
        assertTrue(result is HomographResult.Clean)
    }

    @Test
    fun `cyrillic homograph returns VISUAL_SPOOFING`() {
        val result = HomographDetector.detect("\u0440\u0430\u0443\u0440\u0430\u1ec9.com")
        assertTrue(result is HomographResult.Detected)
        val detected = result as HomographResult.Detected
        assertEquals(HomographType.VISUAL_SPOOFING, detected.type)
    }

    @Test
    fun `punycode domain without homographs returns Clean`() {
        val result = HomographDetector.detect("xn--example")
        assertTrue(result is HomographResult.Clean)
    }

    @Test
    fun `ascii domain with no homograph returns Clean`() {
        val result = HomographDetector.detect("google.com")
        assertTrue(result is HomographResult.Clean)
    }

    @Test
    fun `checkPinyinConfusion returns high score for taobao variant`() {
        val score = HomographDetector.checkPinyinConfusion("ta0bao")
        assertTrue(score > 0.8f)
    }

    @Test
    fun `checkPinyinConfusion returns low score for unrelated domain`() {
        val score = HomographDetector.checkPinyinConfusion("abcdefg.com")
        assertTrue(score < 0.5f)
    }

    @Test
    fun `checkPinyinConfusion returns high score for alipay variant`() {
        val score = HomographDetector.checkPinyinConfusion("a1ipay")
        assertTrue(score > 0.8f)
    }

    @Test
    fun `checkPinyinConfusion returns high score for wechat variant`() {
        val score = HomographDetector.checkPinyinConfusion("we1chat")
        assertTrue(score > 0.8f)
    }

    @Test
    fun `fullwidth homograph detected`() {
        val result = HomographDetector.detect("\uFF41\uFF45\uFF4F.com")
        assertTrue(result is HomographResult.Detected)
        val detected = result as HomographResult.Detected
        assertEquals(HomographType.VISUAL_SPOOFING, detected.type)
        assertEquals("aeo.com", detected.normalized)
    }
}
