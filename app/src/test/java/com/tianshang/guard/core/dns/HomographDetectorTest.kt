package com.tianshang.guard.core.dns

import com.tianshang.guard.BaseUnitTest
import org.junit.Assert
import org.junit.Test

class HomographDetectorTest : BaseUnitTest() {

    @Test
    fun `detect returns Clean for ASCII domain`() {
        val result = HomographDetector.detect("google.com")
        Assert.assertTrue(result is HomographResult.Clean)
    }

    @Test
    fun `detect returns VISUAL_SPOOFING for Cyrillic homograph`() {
        val cyrillicA = '\u0430' // Cyrillic 'а' looks like Latin 'a'
        val domain = "g${cyrillicA}ogle.com"
        val result = HomographDetector.detect(domain)
        Assert.assertTrue(result is HomographResult.Detected)
        Assert.assertEquals(HomographType.VISUAL_SPOOFING, (result as HomographResult.Detected).type)
    }

    @Test
    fun `detect returns VISUAL_SPOOFING for Fullwidth characters`() {
        val fullwidthA = '\uFF41' // Fullwidth 'ａ'
        val domain = "${fullwidthA}bc.com"
        val result = HomographDetector.detect(domain)
        Assert.assertTrue(result is HomographResult.Detected)
        Assert.assertEquals(HomographType.VISUAL_SPOOFING, (result as HomographResult.Detected).type)
    }

    @Test
    fun `detect returns PUNYCODE_SPOOFING for suspicious Punycode`() {
        val domain = "xn--pple-43d.com" // Punycode for Cyrillic-аpple.com
        val result = HomographDetector.detect(domain)
        Assert.assertTrue(result is HomographResult.Detected)
        Assert.assertEquals(HomographType.PUNYCODE_SPOOFING, (result as HomographResult.Detected).type)
    }

    @Test
    fun `detect returns Clean for legitimate Punycode`() {
        val domain = "xn--fiqs8s.com" // "中国".com
        val result = HomographDetector.detect(domain)
        Assert.assertTrue(result is HomographResult.Clean)
    }

    @Test
    fun `detect returns Clean for empty domain`() {
        val result = HomographDetector.detect("")
        Assert.assertTrue(result is HomographResult.Clean)
    }

    @Test
    fun `checkPinyinConfusion returns low score for unrelated domain`() {
        val score = HomographDetector.checkPinyinConfusion("zzzzzzzzzzzzzzzzzzzz.com")
        Assert.assertTrue("Expected score < 0.2 but was $score", score < 0.2f)
    }

    @Test
    fun `checkPinyinConfusion returns positive for homophone domain`() {
        val score = HomographDetector.checkPinyinConfusion("ta0bao.com")
        Assert.assertTrue(score > 0f)
    }

    @Test
    fun `checkPinyinConfusion returns high score for exact match variant`() {
        val score = HomographDetector.checkPinyinConfusion("ta0bao")
        Assert.assertTrue(score > 0.5f)
    }

    @Test
    fun `checkPinyinConfusion returns 0 for empty domain`() {
        val score = HomographDetector.checkPinyinConfusion("")
        Assert.assertEquals(0f, score, 0.001f)
    }

    @Test
    fun `multiple domain labels are checked for Punycode`() {
        val domain = "sub.xn--pple-43d.com"
        val result = HomographDetector.detect(domain)
        Assert.assertTrue(result is HomographResult.Detected)
    }
}
