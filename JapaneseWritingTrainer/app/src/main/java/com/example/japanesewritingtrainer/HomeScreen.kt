package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Trang chủ tổng quan tiến độ và lối tắt tới các hoạt động học chính. */
@Composable
fun HomeScreen(
    characters: List<CharacterData>,
    deckName: String,
    newCardsPerDay: Int,
    onStartLearning: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFreePractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { CardProgressRepository(context) }
    val practiceLogRepository = remember(context) { PracticeLogRepository(context) }
    var progressById by remember { mutableStateOf<Map<String, CardProgress>>(emptyMap()) }
    var newCardsStudiedToday by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, practiceLogRepository, characters) {
        progressById = repository.getAll().associateBy { it.cardId }
        newCardsStudiedToday = practiceLogRepository
            .countDistinctNewCardsSince(localDayStartEpochMillis())
    }

    val now = System.currentTimeMillis()
    val learnedCount = characters.count {
        progressById[it.id]?.status == CardStatus.REVIEW
    }
    val learningCount = characters.count {
        progressById[it.id]?.status in setOf(CardStatus.LEARNING, CardStatus.RELEARNING)
    }
    val dueCount = characters.count {
        progressById[it.id]?.isDue(now) == true
    }
    val newCount = (characters.size - learnedCount - learningCount).coerceAtLeast(0)
    val remainingDailyNewCount = (newCardsPerDay - newCardsStudiedToday).coerceAtLeast(0)
    val availableNewCount = minOf(newCount, remainingDailyNewCount)

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(deckName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Luyện viết và ghi nhớ mặt chữ qua các phiên học ngắn.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Chào mừng bạn quay lại. Hãy dành vài phút để luyện viết hôm nay.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tiến độ $deckName", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryValue("Đã thuộc", "$learnedCount / ${characters.size}")
                    SummaryValue("Cần ôn", dueCount.toString())
                    SummaryValue("Chữ mới", newCount.toString())
                }
                Text(
                    "$learningCount chữ đang trong Learning / Relearning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val fraction = if (characters.isEmpty()) 0f
                    else learnedCount.toFloat() / characters.size
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${(fraction * 100).toInt()}% hoàn thành",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Bài học hôm nay", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (dueCount > 0) {
                        "Bạn có $dueCount ký tự đến hạn ôn tập."
                    } else {
                        "Không có ký tự đến hạn. Bạn có thể học thêm chữ mới."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Đã học mới hôm nay: $newCardsStudiedToday / $newCardsPerDay. " +
                        "Còn có thể học $availableNewCount chữ mới.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onStartLearning,
                    enabled = dueCount > 0 || availableNewCount > 0,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        if (dueCount > 0) "Ôn tập và học bài mới" else "Học bài mới",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f)
            ) {
                Text("Thư viện")
            }
            OutlinedButton(
                onClick = onOpenFreePractice,
                modifier = Modifier.weight(1f)
            ) {
                Text("Luyện tự do")
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
