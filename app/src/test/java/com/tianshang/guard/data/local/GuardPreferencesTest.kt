package com.tianshang.guard.data.local

import com.tianshang.guard.BaseUnitTest
import org.junit.Test

/**
 * GuardPreferences relies on DataStore<Preferences> which cannot be
 * easily unit-tested without Android context (MockK causes ClassCastException
 * with DataStore/Preferences proxy internals).
 *
 * These tests require androidTest with Robolectric or a real Context.
 * See PLAN.md § Testing Strategy — DataStore-dependent classes.
 */
class GuardPreferencesTest : BaseUnitTest() {

    @Test
    fun `placeholder`() {
        // Placeholder: real tests need androidTest with Context
    }
}
