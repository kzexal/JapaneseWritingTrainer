package com.example.japanesewritingtrainer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Epoch millis tại 00:00 theo múi giờ thiết bị, dùng để tính giới hạn học theo ngày. */
fun localDayStartEpochMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(nowEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
