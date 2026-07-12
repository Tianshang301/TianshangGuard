package com.tianshang.guard.domain

import com.tianshang.guard.BaseUnitTest
import com.tianshang.guard.data.local.GuardPreferences
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateRulesUseCaseTest : BaseUnitTest() {

    private val prefs = mockk<GuardPreferences>(relaxed = true)
    private lateinit var useCase: UpdateRulesUseCase

    @Before
    fun setUp() {
        useCase = UpdateRulesUseCase(prefs)
    }

    @Test
    fun `execute delegates to setRulesVersion`() = runTest {
        useCase.execute("2.0.0")
        coVerify { prefs.setRulesVersion("2.0.0") }
    }
}
