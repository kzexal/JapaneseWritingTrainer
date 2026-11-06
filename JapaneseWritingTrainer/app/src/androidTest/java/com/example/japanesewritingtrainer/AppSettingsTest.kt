package com.example.japanesewritingtrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {

    private lateinit var context: Context

    @Before
    fun clearSettings() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun cleanUp() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultsAreAppliedWhenNoSettingsHaveBeenSaved() {
        val settings = AppSettings(context)

        assertEquals(AppThemeMode.SYSTEM, settings.getThemeMode())
        assertEquals(10, settings.getNewCardsPerDay())
        assertTrue(settings.getShowGrid())
        assertTrue(settings.getShowHints())
    }

    @Test
    fun allSettingsSurviveCreatingANewSettingsInstance() {
        val settings = AppSettings(context)
        settings.setThemeMode(AppThemeMode.DARK)
        settings.setNewCardsPerDay(25)
        settings.setShowGrid(false)
        settings.setShowHints(false)

        // A new instance reads from the same persistent SharedPreferences file.
        val restored = AppSettings(context)

        assertEquals(AppThemeMode.DARK, restored.getThemeMode())
        assertEquals(25, restored.getNewCardsPerDay())
        assertFalse(restored.getShowGrid())
        assertFalse(restored.getShowHints())
    }

    @Test
    fun invalidThemeValueFallsBackToSystemMode() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "unknown")
            .commit()

        assertEquals(AppThemeMode.SYSTEM, AppSettings(context).getThemeMode())
    }

    @Test
    fun dailyNewCardLimitIsClampedToSupportedRange() {
        val settings = AppSettings(context)

        settings.setNewCardsPerDay(-5)
        assertEquals(0, settings.getNewCardsPerDay())

        settings.setNewCardsPerDay(100)
        assertEquals(50, settings.getNewCardsPerDay())
    }
}
