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

    fun getNewCardsPerDay(): Int =
        preferences.getInt(KEY_NEW_CARDS_PER_DAY, DEFAULT_NEW_CARDS_PER_DAY)
            .coerceIn(MIN_NEW_CARDS_PER_DAY, MAX_NEW_CARDS_PER_DAY)

    fun setNewCardsPerDay(value: Int) {
        preferences.edit()
            .putInt(KEY_NEW_CARDS_PER_DAY, value.coerceIn(MIN_NEW_CARDS_PER_DAY, MAX_NEW_CARDS_PER_DAY))
            .apply()
    }

    fun getShowGrid(): Boolean = preferences.getBoolean(KEY_SHOW_GRID, true)

    fun setShowGrid(value: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_GRID, value).apply()
    }

    fun getShowHints(): Boolean = preferences.getBoolean(KEY_SHOW_HINTS, true)

    fun setShowHints(value: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_HINTS, value).apply()
    }

    fun getSelectedCharacterType(): String =
        preferences.getString(KEY_SELECTED_CHARACTER_TYPE, DEFAULT_CHARACTER_TYPE)
            ?.takeIf { it in SUPPORTED_CHARACTER_TYPES }
            ?: DEFAULT_CHARACTER_TYPE

    fun setSelectedCharacterType(value: String) {
        if (value in SUPPORTED_CHARACTER_TYPES) {
            preferences.edit().putString(KEY_SELECTED_CHARACTER_TYPE, value).apply()
        }
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_NEW_CARDS_PER_DAY = "new_cards_per_day"
        const val KEY_SHOW_GRID = "show_grid"
        const val KEY_SHOW_HINTS = "show_hints"
        const val KEY_SELECTED_CHARACTER_TYPE = "selected_character_type"
        const val DEFAULT_CHARACTER_TYPE = "hiragana"
        val SUPPORTED_CHARACTER_TYPES = setOf("hiragana", "katakana", "all")
        const val DEFAULT_NEW_CARDS_PER_DAY = 10
        const val MIN_NEW_CARDS_PER_DAY = 0
        const val MAX_NEW_CARDS_PER_DAY = 50
    }
}
