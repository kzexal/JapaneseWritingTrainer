package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
import java.util.Locale

/** Tổng quan tiến độ học và thời gian luyện viết của các bộ ký tự đã nạp. */
@Composable
fun StatisticsScreen(
    characters: List<CharacterData>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val progressRepository = remember(context) { CardProgressRepository(context) }
    val logRepository = remember(context) { PracticeLogRepository(context) }
    val progress by progressRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<PracticeLog>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadLogs() {
        scope.launch {
            isLoadingLogs = true
            try {
                val selectedCardIds = characters.map { it.id }.toSet()
                logs = logRepository.getAll().filter { it.cardId in selectedCardIds }
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể tải thống kê luyện viết."
            } finally {
                isLoadingLogs = false
            }
        }
    }

    LaunchedEffect(logRepository, characters) { loadLogs() }

    val totalCards = characters.size
    val characterIds = remember(characters) { characters.map { it.id }.toSet() }
    val deckProgress = progress.filter { it.cardId in characterIds }
    val learned = deckProgress.count { it.status == CardStatus.REVIEW }
    val learning = deckProgress.count {
        it.status == CardStatus.LEARNING || it.status == CardStatus.RELEARNING
    }
    val newCards = (totalCards - learned - learning).coerceAtLeast(0)
    val dueCards = deckProgress.count { it.isDue(System.currentTimeMillis()) }
    val totalReviews = deckProgress.sumOf { it.reviewCount }
    val correctReviews = deckProgress.sumOf { it.correctCount }
    val accuracy = if (totalReviews == 0) 0 else correctReviews * 100 / totalReviews
    val practicedIds = deckProgress.filter { it.reviewCount > 0 }.map { it.cardId }.toSet() +
        logs.asSequence().map { it.cardId }.filter { it in characterIds }.toSet()
    val practicedCards = practicedIds.size
    val streak = remember(logs) { calculatePracticeStreak(logs) }
    val totalDuration = totalPracticeDurationMs(logs)
    val completion = if (totalCards == 0) 0f else (learned.toFloat() / totalCards).coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Thống kê học tập", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tổng quan tiến độ ${characters.firstOrNull()?.let { characterTypeLabel(it.type) } ?: "bộ chữ"} của bạn",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (errorMessage != null) {
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tiến độ hoàn thành", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${(completion * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { completion },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$learned / $totalCards chữ đã tốt nghiệp sang Review")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatisticValueCard("Đang học", learning.toString(), Modifier.weight(1f))
            StatisticValueCard("Mới", newCards.toString(), Modifier.weight(1f))
            StatisticValueCard("Đến hạn", dueCards.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatisticValueCard("Đã luyện", "$practicedCards/$totalCards", Modifier.weight(1f))
            StatisticValueCard("Lượt ôn", totalReviews.toString(), Modifier.weight(1f))
            StatisticValueCard("Chính xác", "$accuracy%", Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatisticValueCard("Chuỗi hiện tại", "${streak.currentDays} ngày", Modifier.weight(1f))
            StatisticValueCard("Kỷ lục", "${streak.longestDays} ngày", Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Thời gian luyện viết", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatStatisticsDuration(totalDuration),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLoadingLogs) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        "${logs.size} phiên luyện đã được ghi nhận",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text("Tiến độ theo trạng thái", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { StatusProgressRow("Review · Đã tốt nghiệp", learned, totalCards) }
            item { StatusProgressRow("Learning / Relearning", learning, totalCards) }
            item { StatusProgressRow("New · Chưa học", newCards, totalCards) }
            item {
                Text(
                    "Thống kê được tổng hợp từ tiến độ và lịch sử lưu trên thiết bị.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatisticValueCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusProgressRow(label: String, count: Int, total: Int) {
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$count / $total", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatStatisticsDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%d giờ %d phút", hours, minutes)
        totalMinutes > 0 -> "$totalMinutes phút"
        else -> "${durationMs.coerceAtLeast(0L) / 1_000L} giây"
    }
}
