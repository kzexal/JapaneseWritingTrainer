package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    characters: List<CharacterData>,
    deckName: String,
    newCardsPerDay: Int,
    onStartLearning: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFreePractice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { CardProgressRepository(context) }
    val practiceLogRepository = remember(context) { PracticeLogRepository(context) }
    var progressById by remember { mutableStateOf<Map<String, CardProgress>>(emptyMap()) }
    var newCardsStudiedToday by remember { mutableIntStateOf(0) }
    var currentStreakDays by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, practiceLogRepository, characters) {
        progressById = repository.getAll().associateBy { it.cardId }
        newCardsStudiedToday = practiceLogRepository
            .countDistinctNewCardsSince(localDayStartEpochMillis(), characters.map { it.id })
        currentStreakDays = calculatePracticeStreak(practiceLogRepository.getAll()).currentDays
    }

    val now = System.currentTimeMillis()
    val learnedCount = characters.count { progressById[it.id]?.status == CardStatus.REVIEW }
    val learningCount = characters.count {
        progressById[it.id]?.status in setOf(CardStatus.LEARNING, CardStatus.RELEARNING)
    }
    val dueCharacters = characters.filter { progressById[it.id]?.isDue(now) == true }
    val newCharacters = characters.filter {
        progressById[it.id] == null ||
            progressById[it.id]?.status !in setOf(CardStatus.REVIEW, CardStatus.LEARNING, CardStatus.RELEARNING)
    }
    val dueCount = dueCharacters.size
    val newCount = newCharacters.size
    val remainingDailyNewCount = (newCardsPerDay - newCardsStudiedToday).coerceAtLeast(0)
    val availableNewCount = minOf(newCount, remainingDailyNewCount)
    val heroCharacter = dueCharacters.firstOrNull() ?: newCharacters.firstOrNull()
    val canStart = dueCount > 0 || availableNewCount > 0
    val fraction = if (characters.isEmpty()) 0f else learnedCount.toFloat() / characters.size
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val streakAccent = if (isDarkTheme) Color(0xFFFFB59A) else Color(0xFFD9693F)
    val streakAccentContainer = if (isDarkTheme) Color(0xFF5C2E1B) else Color(0xFFFFE2D6)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Chào mừng bạn trở lại",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(deckName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Surface(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                shape = RoundedCornerShape(999.dp),
                color = streakAccentContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = streakAccent
                    )
                    Text(
                        "$currentStreakDays ngày",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = streakAccent
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(when {
                        dueCount > 0 -> "Đến lượt ôn tập"
                        availableNewCount > 0 -> "Sẵn sàng học chữ mới"
                        else -> "Bạn đã hoàn thành hôm nay"
                    }, modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(when {
                        dueCount > 0 -> "Còn $dueCount chữ đang chờ bạn."
                        availableNewCount > 0 -> "Khám phá thêm một vài ký tự mới nhé."
                        else -> "Một chút luyện tập mỗi ngày tạo nên khác biệt."
                    }, modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2)
                    Button(onClick = onStartLearning, enabled = canStart,
                        modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(when {
                            dueCount > 0 -> "Ôn $dueCount chữ"
                            availableNewCount > 0 -> "Học chữ mới"
                            else -> "Đã hoàn thành"
                        })
                    }
                    Text("Hôm nay: $newCardsStudiedToday / $newCardsPerDay chữ mới",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (heroCharacter != null) {
                    Column(
                        modifier = Modifier.widthIn(min = 48.dp, max = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            heroCharacter.character,
                            fontSize = if (deckName.equals("Kanji", ignoreCase = true)) 40.sp else 52.sp,
                            lineHeight = if (deckName.equals("Kanji", ignoreCase = true)) 48.sp else 60.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                        if (!deckName.equals("Kanji", ignoreCase = true)) {
                            Text(
                                heroCharacter.reading,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryChip("Đã thuộc", learnedCount.toString(), Modifier.weight(1f))
            SummaryChip("Cần ôn", dueCount.toString(), Modifier.weight(1f))
            SummaryChip("Chữ mới", newCount.toString(), Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tiến độ $deckName", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
