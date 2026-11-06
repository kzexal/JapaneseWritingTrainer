package com.example.japanesewritingtrainer

import android.content.Context

enum class AppThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "Theo hệ thống"),
    LIGHT("light", "Sáng"),
    DARK("dark", "Tối");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/** Lưu các tùy chọn giao diện cục bộ trên thiết bị. */
class AppSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "app_settings",
        Context.MODE_PRIVATE
    )

    fun getThemeMode(): AppThemeMode =
        AppThemeMode.fromStorage(preferences.getString(KEY_THEME_MODE, null))

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
