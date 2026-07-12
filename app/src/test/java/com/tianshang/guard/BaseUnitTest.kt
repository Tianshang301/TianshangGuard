package com.tianshang.guard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.tianshang.guard.core.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
open class BaseUnitTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected fun loadTestData(filename: String): String {
        return javaClass.classLoader!!.getResourceAsStream("test_data/$filename")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Test data not found: $filename")
    }
}
