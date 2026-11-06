package com.example.japanesewritingtrainer

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sao lưu/khôi phục dữ liệu người học. Dữ liệu ký tự tĩnh trong assets không nằm
 * trong bản sao lưu; chỉ tiến độ FSRS và lịch sử luyện viết được xuất.
 */
class BackupRestoreManager(context: Context) {
    private val database = WritingTrainerDatabase.getInstance(context.applicationContext)

    suspend fun createBackupJson(): String = database.withTransaction {
        val progress = database.cardProgressDao().getAll()
        val logs = database.practiceLogDao().getAll()

        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("schemaVersion", BACKUP_SCHEMA_VERSION)
            .put("createdAtEpochMillis", System.currentTimeMillis())

        val progressArray = JSONArray()
        progress.forEach { item ->
            progressArray.put(
                JSONObject()
                    .put("cardId", item.cardId)
                    .put("status", item.status)
                    .putNullable("dueAtEpochMillis", item.dueAtEpochMillis)
                    .putNullable("stabilityDays", item.stabilityDays)
                    .putNullable("difficulty", item.difficulty)
                    .put("scheduledDays", item.scheduledDays)
                    .put("elapsedDays", item.elapsedDays)
                    .put("reps", item.reps)
                    .put("lapses", item.lapses)
                    .put("reviewCount", item.reviewCount)
                    .put("correctCount", item.correctCount)
                    .putNullable("lastReviewedAtEpochMillis", item.lastReviewedAtEpochMillis)
            )
        }

        val logArray = JSONArray()
        logs.forEach { item ->
            logArray.put(
                JSONObject()
                    .put("id", item.id)
                    .put("cardId", item.cardId)
                    .put("mode", item.mode)
                    .putNullable("rating", item.rating)
                    .put("durationMs", item.durationMs)
                    .put("timestampEpochMillis", item.timestampEpochMillis)
            )
        }

        root.put("cardProgress", progressArray)
        root.put("practiceLogs", logArray)
        root.toString(2)
    }

    /**
     * Kiểm tra toàn bộ JSON trước, sau đó thay thế dữ liệu trong một Room transaction.
     * Nếu parse hoặc ghi dữ liệu thất bại, transaction không để lại dữ liệu dở dang.
     */
    suspend fun restoreFromJson(json: String) {
        val root = JSONObject(json)
        require(root.getString("format") == BACKUP_FORMAT) {
            "Tệp không phải bản sao lưu Japanese Writing Trainer."
        }
        require(root.getInt("schemaVersion") == BACKUP_SCHEMA_VERSION) {
            "Phiên bản bản sao lưu không được hỗ trợ."
        }

        val progressArray = root.getJSONArray("cardProgress")
        val logArray = root.getJSONArray("practiceLogs")

        val progress = buildList {
            for (index in 0 until progressArray.length()) {
                val item = progressArray.getJSONObject(index)
                val cardId = item.getString("cardId")
                val status = item.getString("status")
                require(cardId.isNotBlank()) { "Bản sao lưu chứa cardId rỗng." }
                CardStatus.valueOf(status)

                add(
                    CardProgressEntity(
                        cardId = cardId,
                        status = status,
                        dueAtEpochMillis = item.optNullableLong("dueAtEpochMillis"),
                        stabilityDays = item.optNullableDouble("stabilityDays"),
                        difficulty = item.optNullableDouble("difficulty"),
                        scheduledDays = item.getDouble("scheduledDays"),
                        elapsedDays = item.getDouble("elapsedDays"),
                        reps = item.getInt("reps"),
                        lapses = item.getInt("lapses"),
                        reviewCount = item.getInt("reviewCount"),
                        correctCount = item.getInt("correctCount"),
                        lastReviewedAtEpochMillis = item.optNullableLong("lastReviewedAtEpochMillis")
                    )
                )
            }
        }

        val logs = buildList {
            for (index in 0 until logArray.length()) {
                val item = logArray.getJSONObject(index)
                val cardId = item.getString("cardId")
                val mode = item.getString("mode")
                val rating = item.optNullableString("rating")
                require(cardId.isNotBlank()) { "Bản sao lưu chứa cardId rỗng." }
                PracticeMode.valueOf(mode)
                rating?.let(ReviewRating::valueOf)

                add(
                    PracticeLogEntity(
                        id = item.getLong("id"),
                        cardId = cardId,
                        mode = mode,
                        rating = rating,
                        durationMs = item.getLong("durationMs"),
                        timestampEpochMillis = item.getLong("timestampEpochMillis")
                    )
                )
            }
        }

        require(progress.map { it.cardId }.distinct().size == progress.size) {
            "Bản sao lưu chứa card tiến độ trùng lặp."
        }
        require(logs.map { it.id }.distinct().size == logs.size) {
            "Bản sao lưu chứa mã lịch sử trùng lặp."
        }
        require(progress.all {
            (it.stabilityDays == null || it.stabilityDays > 0.0) &&
                (it.difficulty == null || it.difficulty in 1.0..10.0) &&
                it.scheduledDays >= 0.0 && it.elapsedDays >= 0.0 &&
                it.reps >= 0 && it.lapses >= 0 &&
                it.reviewCount >= 0 && it.correctCount in 0..it.reviewCount
        }) {
            "Bản sao lưu chứa tiến độ FSRS không hợp lệ."
        }
        require(logs.all {
            it.id >= 0 && it.durationMs >= 0 && it.timestampEpochMillis >= 0
        }) {
            "Bản sao lưu chứa dữ liệu lịch sử không hợp lệ."
        }

        database.withTransaction {
            database.cardProgressDao().deleteAll()
            database.practiceLogDao().deleteAll()
            database.cardProgressDao().upsertAll(progress)
            database.practiceLogDao().insertAll(logs)
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else getLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private companion object {
        const val BACKUP_FORMAT = "japanese-writing-trainer"
        const val BACKUP_SCHEMA_VERSION = 1
    }
}
