package com.example.uzb_qqs_for_dip.data

import android.content.Context
import android.content.SharedPreferences
import com.example.uzb_qqs_for_dip.data.settings.AppThemeMode
import com.example.uzb_qqs_for_dip.data.settings.ThemeManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemeManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val prefsMap = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        prefsMap.clear()
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getString("app_theme_mode", any()) } answers {
            (prefsMap["app_theme_mode"] as? String) ?: secondArg()
        }
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString("app_theme_mode", any()) } answers {
            prefsMap["app_theme_mode"] = secondArg<String>()
            mockEditor
        }
        every { mockEditor.apply() } answers { }
    }

    @Test
    fun `default theme mode is SYSTEM`() {
        val manager = ThemeManager(mockContext)
        assertEquals(AppThemeMode.SYSTEM, manager.themeMode.value)
    }

    @Test
    fun `setThemeMode LIGHT persists and updates StateFlow`() {
        val manager = ThemeManager(mockContext)
        manager.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, manager.themeMode.value)
        assertEquals("LIGHT", prefsMap["app_theme_mode"])
    }

    @Test
    fun `setThemeMode DARK persists and updates StateFlow`() {
        val manager = ThemeManager(mockContext)
        manager.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, manager.themeMode.value)
        assertEquals("DARK", prefsMap["app_theme_mode"])
    }

    @Test
    fun `load existing persisted theme on init`() {
        prefsMap["app_theme_mode"] = "DARK"
        val manager = ThemeManager(mockContext)
        assertEquals(AppThemeMode.DARK, manager.themeMode.value)
    }

    @Test
    fun `corrupt persisted value falls back safely to SYSTEM`() {
        prefsMap["app_theme_mode"] = "INVALID_CORRUPT_MODE"
        val manager = ThemeManager(mockContext)
        assertEquals(AppThemeMode.SYSTEM, manager.themeMode.value)
    }

    @Test
    fun `runtime theme switching cycle SYSTEM to LIGHT to DARK to SYSTEM`() {
        val manager = ThemeManager(mockContext)
        assertEquals(AppThemeMode.SYSTEM, manager.themeMode.value)

        // 1. SYSTEM -> LIGHT
        manager.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, manager.themeMode.value)
        assertEquals("LIGHT", prefsMap["app_theme_mode"])

        // 2. LIGHT -> DARK
        manager.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, manager.themeMode.value)
        assertEquals("DARK", prefsMap["app_theme_mode"])

        // 3. DARK -> SYSTEM
        manager.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, manager.themeMode.value)
        assertEquals("SYSTEM", prefsMap["app_theme_mode"])
    }
}
