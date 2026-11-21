package com.example.japanesewritingtrainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tổng quan tiến độ học và thời gian luyện viết của các bộ ký tự đã nạp. */
@Composable
fun StatisticsScreen(
    characters: List<CharacterData>,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0
) {
    val context = LocalContext.current.applicationContext
    val progressRepository = remember(context) { CardProgressRepository(context) }
    val logRepository = remember(context) { PracticeLogRepository(context) }
    val progress by progressRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<PracticeLog>>(emptyList()) }
    var allLogs by remember { mutableStateOf<List<PracticeLog>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadLogs() {
        scope.launch {
            isLoadingLogs = true
            try {
                val storedLogs = logRepository.getAll()
                allLogs = storedLogs
                val selectedCardIds = characters.map { it.id }.toSet()
                logs = storedLogs.filter { it.cardId in selectedCardIds }
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể tải thống kê luyện viết."
            } finally {
                isLoadingLogs = false
            }
        }
    }

    LaunchedEffect(logRepository, characters, refreshKey) { loadLogs() }

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
    // Streak is app-wide: Vocabulary and character decks share the same local-day sequence.
    val streak = remember(allLogs) { calculatePracticeStreak(allLogs) }
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
                Text("$learned / $totalCards chữ đã thuộc")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatisticValueCard(
                "Mới", newCards.toString(), Modifier.weight(1f),
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer
            )
            StatisticValueCard(
                "Đang học", learning.toString(), Modifier.weight(1f),
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer
            )
            StatisticValueCard(
                "Đã thuộc", learned.toString(), Modifier.weight(1f),
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatisticValueCard("Đã luyện", "$practicedCards/$totalCards", Modifier.weight(1f))
            StatisticValueCard("Lượt ôn", totalReviews.toString(), Modifier.weight(1f))
            StatisticValueCard("Chính xác", "$accuracy%", Modifier.weight(1f))
        }

        Text(
            "Chuỗi ngày được tính trên toàn bộ deck.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            item { StatisticsActivityHeatmap(logs = logs) }
            item { StatisticsWeeklyTrends(logs = logs) }
            item { StatusProgressRow("Mới", newCards, totalCards, MaterialTheme.colorScheme.primary) }
            item { StatusProgressRow("Đang học · Học lại", learning, totalCards, MaterialTheme.colorScheme.error) }
            item { StatusProgressRow("Đã thuộc", learned, totalCards, MaterialTheme.colorScheme.tertiary) }
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
private fun StatisticValueCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
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
private fun StatusProgressRow(label: String, count: Int, total: Int, indicatorColor: Color) {
    val fraction = if (total == 0) 0f else count.toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$count / $total", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = indicatorColor
        )
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


@Composable
private fun StatisticsActivityHeatmap(logs: List<PracticeLog>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startDate = today.minusDays(83)
    val counts = remember(logs, today) {
        logs.groupBy {
            Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate()
        }.mapValues { (_, dayLogs) -> dayLogs.map { it.cardId }.distinct().size }
    }
    val maxCount = (counts.filterKeys { it >= startDate && it <= today }.values.maxOrNull() ?: 1).coerceAtLeast(1)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Hoạt động 12 tuần", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(12) { week ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(7) { day ->
                            val date = startDate.plusDays((week * 7 + day).toLong())
                            val count = counts[date] ?: 0
                            val intensity = if (count == 0) 0f else (count.toFloat() / maxCount).coerceIn(0.18f, 1f)
                            val color = if (count == 0) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                androidx.compose.ui.graphics.lerp(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary,
                                    intensity
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(color, RoundedCornerShape(3.dp))
                                    .semantics {
                                        val formattedDate = date.format(
                                            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                                        )
                                        contentDescription = if (count == 0) {
                                            "$formattedDate: không có hoạt động luyện tập"
                                        } else {
                                            "$formattedDate: đã luyện $count chữ khác nhau"
                                        }
                                    }
                            )
                        }
                    }
                }
            }
            Text(
                "Mỗi ô là một ngày; màu đậm hơn biểu thị nhiều chữ khác nhau được luyện hơn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatisticsWeeklyTrends(logs: List<PracticeLog>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val thisMonday = today.with(java.time.DayOfWeek.MONDAY)
    val weeks = remember(logs, thisMonday) {
        (5 downTo 0).map { offset ->
            val start = thisMonday.minusWeeks(offset.toLong())
            val end = start.plusDays(7)
            val weekLogs = logs.filter {
                val date = Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate()
                date >= start && date < end
            }
            val firstSuccessfulCards = logs
                .filter { it.rating == ReviewRating.GOOD || it.rating == ReviewRating.EASY }
                .groupBy { it.cardId }
                .count { (cardId, successfulLogs) ->
                    val firstSuccess = successfulLogs.minOf { it.timestampEpochMillis }
                    val firstSuccessDate = Instant.ofEpochMilli(firstSuccess).atZone(zone).toLocalDate()
                    firstSuccessDate >= start && firstSuccessDate < end
                }
            val rated = weekLogs.filter { it.rating != null }
            val successRate = if (rated.isEmpty()) 0 else {
                rated.count { it.rating == ReviewRating.GOOD || it.rating == ReviewRating.EASY } * 100 / rated.size
            }
            WeeklyTrend(start, firstSuccessfulCards, successRate)
        }
    }
    val maxLearned = (weeks.maxOfOrNull { it.newlySuccessfulCards } ?: 0).coerceAtLeast(1)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Xu hướng 6 tuần", style = MaterialTheme.typography.titleMedium)
            Text("Chữ lần đầu đạt Good/Easy", style = MaterialTheme.typography.bodySmall)
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${week.start.dayOfMonth}/${week.start.monthValue}", style = MaterialTheme.typography.labelSmall)
                        Text(week.newlySuccessfulCards.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { week.newlySuccessfulCards.toFloat() / maxLearned },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Text("Tỷ lệ Good/Easy", style = MaterialTheme.typography.bodySmall)
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${week.start.dayOfMonth}/${week.start.monthValue}", style = MaterialTheme.typography.labelSmall)
                    Text("${week.successRate}%", style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { week.successRate / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Số chữ được tính theo lần đầu có đánh giá Good/Easy trong lịch sử; tỷ lệ chỉ tính các lượt có tự đánh giá.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class WeeklyTrend(
    val start: LocalDate,
    val newlySuccessfulCards: Int,
    val successRate: Int
)
