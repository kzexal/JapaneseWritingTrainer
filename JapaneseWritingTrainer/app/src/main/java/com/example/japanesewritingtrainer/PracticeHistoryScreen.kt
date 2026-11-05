package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Màn hình xem các lần luyện đã được lưu trong Room. */
@Composable
fun PracticeHistoryScreen(
    characters: List<CharacterData>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { PracticeLogRepository(context) }
    val charactersById = remember(characters) { characters.associateBy { it.id } }
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf<List<PracticeLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val characterStats = remember(logs) { buildCharacterPracticeStats(logs) }
    val needsPractice = remember(characterStats) {
        characterStats.filter { it.ratedAttempts >= 2 }
            .sortedByDescending { it.againRate }
            .take(3)
    }
    val rememberedWell = remember(characterStats) {
        characterStats.filter { it.ratedAttempts >= 2 && it.successfulAttempts > 0 }
            .sortedByDescending { it.successRate }
            .take(3)
    }

    fun loadHistory() {
        scope.launch {
            isLoading = true
            try {
                logs = repository.getAll()
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể tải lịch sử luyện viết."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(repository) {
        loadHistory()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Lịch sử luyện viết", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Đã lưu ${logs.size} lần luyện",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = { loadHistory() }, enabled = !isLoading) {
                Text("Làm mới")
            }
        }

        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Đang tải lịch sử...", modifier = Modifier.padding(top = 8.dp))
                }
            }

            errorMessage != null -> {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { loadHistory() }) {
                    Text("Thử lại")
                }
            }

            logs.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Chưa có lịch sử luyện viết.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Luyện viết ở chế độ tự do hoặc hoàn thành một thẻ ôn tập để lưu lịch sử tại đây.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                Text(
                    "Tổng thời gian luyện: ${formatDuration(totalPracticeDurationMs(logs))}",
                    style = MaterialTheme.typography.bodySmall
                )

                if (needsPractice.isNotEmpty()) {
                    Text(
                        "Những chữ cần luyện thêm",
                        style = MaterialTheme.typography.titleMedium
                    )
                    needsPractice.forEach { stats ->
                        val character = charactersById[stats.cardId]
                        Text(
                            "${character?.character ?: stats.cardId} · ${character?.reading.orEmpty()}  —  Again ${stats.againCount}/${stats.ratedAttempts} (${formatPercent(stats.againRate)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (rememberedWell.isNotEmpty()) {
                    Text(
                        "Những chữ bạn đã nhớ tốt",
                        style = MaterialTheme.typography.titleMedium
                    )
                    rememberedWell.forEach { stats ->
                        val character = charactersById[stats.cardId]
                        Text(
                            "${character?.character ?: stats.cardId} · ${character?.reading.orEmpty()}  —  Good/Easy ${stats.successfulAttempts}/${stats.ratedAttempts} (${formatPercent(stats.successRate)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Text("Các lần luyện gần đây", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        val character = charactersById[log.cardId]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    character?.character ?: log.cardId,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        character?.let { "${it.character} · ${it.reading}" }
                                            ?: log.cardId,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        if (log.rating == null) {
                                            log.mode.toVietnameseLabel()
                                        } else {
                                            "${log.mode.toVietnameseLabel()} · ${log.rating.toVietnameseLabel()}"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${formatDuration(log.durationMs)} · ${formatTimestamp(log.timestampEpochMillis)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PracticeMode.toVietnameseLabel(): String = when (this) {
    PracticeMode.TRACE -> "Tô theo mẫu"
    PracticeMode.GUIDED -> "Viết có hướng dẫn"
    PracticeMode.RECALL -> "Tự nhớ và viết"
    PracticeMode.REVIEW -> "Ôn tập"
    PracticeMode.FREE -> "Luyện tự do"
}

private fun ReviewRating?.toVietnameseLabel(): String = when (this) {
    ReviewRating.AGAIN -> "Again · Học lại"
    ReviewRating.HARD -> "Hard · Khó"
    ReviewRating.GOOD -> "Good · Tốt"
    ReviewRating.EASY -> "Easy · Dễ"
    null -> "Chưa đánh giá"
}

private fun formatPercent(value: Double): String =
    "${(value * 100).toInt()}%"

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes} phút ${seconds} giây" else "${seconds} giây"
}

private fun formatTimestamp(timestampEpochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
        .format(Date(timestampEpochMillis))
