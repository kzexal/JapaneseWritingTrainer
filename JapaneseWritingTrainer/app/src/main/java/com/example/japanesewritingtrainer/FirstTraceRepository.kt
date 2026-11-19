package com.example.japanesewritingtrainer

import android.content.Context

/**
 * Tracks whether a character's one-time first-learning trace has been completed.
 * Kept in SharedPreferences so this does not require a Room schema migration.
 */
class FirstTraceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "first_trace_progress",
        Context.MODE_PRIVATE
    )

    fun hasCompleted(characterId: String): Boolean =
        preferences.getBoolean(keyFor(characterId), false)

    fun markCompleted(characterId: String) {
        preferences.edit().putBoolean(keyFor(characterId), true).apply()
    }

    private fun keyFor(characterId: String): String = "completed:$characterId"
}
