package com.zhengui.waterreminder.util

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        // 重置状态
        PreferenceManager.setReminderEnabled(context, false)
        PreferenceManager.setCurrentTypeId(context, 1L)
        PreferenceManager.setAutoStartEnabled(context, false)
    }

    @Test
    fun `isReminderEnabled returns false by default`() {
        assertFalse(PreferenceManager.isReminderEnabled(context))
    }

    @Test
    fun `setReminderEnabled persists value`() {
        PreferenceManager.setReminderEnabled(context, true)
        assertTrue(PreferenceManager.isReminderEnabled(context))
    }

    @Test
    fun `setReminderEnabled to false works`() {
        PreferenceManager.setReminderEnabled(context, true)
        PreferenceManager.setReminderEnabled(context, false)
        assertFalse(PreferenceManager.isReminderEnabled(context))
    }

    @Test
    fun `getCurrentTypeId returns default 1L`() {
        assertEquals(1L, PreferenceManager.getCurrentTypeId(context))
    }

    @Test
    fun `setCurrentTypeId persists value`() {
        PreferenceManager.setCurrentTypeId(context, 42L)
        assertEquals(42L, PreferenceManager.getCurrentTypeId(context))
    }

    @Test
    fun `isAutoStartEnabled returns false by default`() {
        assertFalse(PreferenceManager.isAutoStartEnabled(context))
    }

    @Test
    fun `setAutoStartEnabled persists value`() {
        PreferenceManager.setAutoStartEnabled(context, true)
        assertTrue(PreferenceManager.isAutoStartEnabled(context))
    }
}
