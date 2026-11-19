package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ReviewOverviewCard(
    val character: CharacterData,
    val status: CardStatus,
    val dueAtEpochMillis: Long
)

@Composable
fun ReviewOverviewScreen(
    characters: List<CharacterData>,
    deckType: String,
    newCardsPerDay: Int,
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val progressRepository = remember(context) { CardProgressRepository(context) }
    val logRepository = remember(context) { PracticeLogRepository(context) }
    var dueCards by remember { mutableStateOf<List<ReviewOverviewCard>>(emptyList()) }
    var upcomingCards by remember { mutableStateOf<List<ReviewOverviewCard>>(emptyList()) }
    var remainingNewCards by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(characters, deckType, newCardsPerDay, progressRepository, logRepository) {
        isLoading = true
        try {
            val now = System.currentTimeMillis()
            val catalog = DeckFactory.fromCharacters(characters)
            val deck = catalog.decks.firstOrNull { it.characterType == deckType }
            val deckCards = deck?.let { catalog.cardsForDeck(it.id) }.orEmpty()
            val cardIds = deckCards.map { it.id }
            val progressById = progressRepository.getAll()
                .filter { it.cardId in cardIds }
                .associateBy { it.cardId }
            val charactersById = characters.associateBy { it.id }

            dueCards = deckCards.mapNotNull { card ->
                val progress = progressById[card.id] ?: return@mapNotNull null
                if (!progress.isDue(now)) return@mapNotNull null
                val character = charactersById[card.characterId] ?: return@mapNotNull null
                ReviewOverviewCard(character, progress.status, progress.dueAtEpochMillis ?: now)
            }.sortedBy { it.dueAtEpochMillis }

            val nextWeek = now + 7L * 24L * 60L * 60L * 1000L
            upcomingCards = deckCards.mapNotNull { card ->
                val progress = progressById[card.id] ?: return@mapNotNull null
                val dueAt = progress.dueAtEpochMillis ?: return@mapNotNull null
                if (progress.status == CardStatus.NEW || dueAt <= now || dueAt > nextWeek) {
                    return@mapNotNull null
                }
                val character = charactersById[card.characterId] ?: return@mapNotNull null
                ReviewOverviewCard(character, progress.status, dueAt)
            }.sortedBy { it.dueAtEpochMillis }

            val studiedToday = logRepository.countDistinctNewCardsSince(
                localDayStartEpochMillis(),
                cardIds
            )
            remainingNewCards = (newCardsPerDay - studiedToday).coerceAtLeast(0)
            errorMessage = null
        } catch (exception: Exception) {
            errorMessage = exception.localizedMessage ?: "Không thể tải danh sách ôn tập."
        } finally {
            isLoading = false
        }
    }

    val warningColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFFE6B85C)
    } else {
        Color(0xFFC98A1B)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverviewCountCard(
                label = "Đến hạn hôm nay",
                value = dueCards.size,
                modifier = Modifier.weight(1f),
                valueColor = warningColor
            )
            OverviewCountCard(
                label = "Chữ mới còn lại",
                value = remainingNewCards,
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary
            )
        }

        when {
            isLoading -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Đang tải lịch ôn tập...", modifier = Modifier.padding(top = 8.dp))
                }
            }

            errorMessage != null -> {
                Text(
                    errorMessage.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Đến hạn hôm nay (${dueCards.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (dueCards.isEmpty()) {
                        item {
                            Text(
                                "Bạn không có chữ nào cần ôn ngay lúc này.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(dueCards, key = { it.character.id }) { item ->
                            ReviewOverviewRow(item, showDueTime = false)
                        }
                    }

                    item {
                        Text(
                            "Sắp tới trong 7 ngày (${upcomingCards.size})",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (upcomingCards.isEmpty()) {
                        item {
                            Text(
                                "Chưa có lịch ôn sắp tới.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(upcomingCards, key = { "upcoming-${it.character.id}" }) { item ->
                            ReviewOverviewRow(item, showDueTime = true)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onStartReview,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && errorMessage == null &&
                (dueCards.isNotEmpty() || remainingNewCards > 0)
        ) {
            Text("Bắt đầu ôn")
        }
    }
}

@Composable
private fun OverviewCountCard(
    label: String,
    value: Int,
    modifier: Modifier,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewOverviewRow(
    item: ReviewOverviewCard,
    showDueTime: Boolean
) {
    val dateLabel = remember(item.dueAtEpochMillis) {
        SimpleDateFormat("EEE, dd/MM · HH:mm", Locale.getDefault())
            .format(Date(item.dueAtEpochMillis))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                item.character.character,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.character.reading, style = MaterialTheme.typography.titleSmall)
                Text(
                    when (item.status) {
                        CardStatus.LEARNING -> "Đang học"
                        CardStatus.RELEARNING -> "Học lại"
                        CardStatus.REVIEW -> "Ôn tập"
                        CardStatus.NEW -> "Mới"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showDueTime) {
                Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
