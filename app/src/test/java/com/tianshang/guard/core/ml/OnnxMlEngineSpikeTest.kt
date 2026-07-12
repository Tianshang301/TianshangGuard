package com.tianshang.guard.core.ml

import com.tianshang.guard.BaseUnitTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert
import org.junit.Test

class OnnxMlEngineSpikeTest : BaseUnitTest() {

    @Test
    fun `mockk basic mock works`() {
        val mockListener = mockk<TestListener>()
        every { mockListener.onEvent(any()) } returns true

        val result = mockListener.onEvent("hello")

        Assert.assertTrue(result)
        verify { mockListener.onEvent("hello") }
    }

    @Test
    fun `mockk slot capture works`() {
        val mockListener = mockk<TestListener>()
        val captured = slot<String>()
        every { mockListener.onEvent(capture(captured)) } returns true

        mockListener.onEvent("captured-value")

        Assert.assertEquals("captured-value", captured.captured)
    }

    @Test
    fun `mockk multiple calls returns different values`() {
        val mockListener = mockk<TestListener>()
        every { mockListener.onEvent(any()) } returnsMany listOf(true, false, true)

        Assert.assertTrue(mockListener.onEvent("a"))
        Assert.assertFalse(mockListener.onEvent("b"))
        Assert.assertTrue(mockListener.onEvent("c"))
    }

    @Test
    fun `mockk can be verified with any matcher`() {
        val mockListener = mockk<TestListener>()
        every { mockListener.onEvent(any()) } returns true

        mockListener.onEvent("first")
        mockListener.onEvent("second")

        verify(exactly = 2) { mockListener.onEvent(any()) }
    }
}

interface TestListener {
    fun onEvent(event: String): Boolean
}
