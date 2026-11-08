package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Phiên ôn tập của bộ ký tự đang được chọn:
 * 1. Planner chọn thẻ đến hạn trước, sau đó thêm thẻ mới.
 * 2. Người học viết ở Recall Writing.
 * 3. Khi hoàn thành, chuyển sang màn hình đánh giá riêng, gọn và dễ chọn.
 * 4. Coordinator lưu tiến độ qua Room rồi mới chuyển sang thẻ tiếp theo.
 */
@Composable
fun LearningSessionScreen(
    characters: List<CharacterData>,
    newCardsPerDay: Int = 10,
    showGrid: Boolean = true,
    showHints: Boolean = true,
    deckType: String = "hiragana",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) { LearningSessionCoordinator(context) }
    val practiceLogRepository = remember(context) { PracticeLogRepository(context) }
    val catalog = remember(characters) { DeckFactory.fromCharacters(characters) }
    val deck = catalog.decks.firstOrNull { it.characterType == deckType }
    val deckCards = remember(catalog, deck) {
        deck?.let { catalog.cardsForDeck(it.id) }.orEmpty()
    }
    val charactersById = remember(characters) { characters.associateBy { it.id } }
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<LearningSessionSnapshot?>(null) }
    var deckProgress by remember { mutableStateOf<DeckProgressSummary?>(null) }
    var newCardsStudiedToday by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var awaitingRating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(coordinator, deck, deckCards, newCardsPerDay) {
        isLoading = true
        snapshot = null
        deckProgress = null
        awaitingRating = false
        if (deck == null) {
            isLoading = false
            errorMessage = "Không tìm thấy bộ thẻ ${characterTypeLabel(deckType)}."
            return@LaunchedEffect
        }

        try {
            val todayStart = localDayStartEpochMillis()
            newCardsStudiedToday = practiceLogRepository
                .countDistinctNewCardsSince(todayStart)
            val remainingNewCards = (newCardsPerDay - newCardsStudiedToday).coerceAtLeast(0)
            snapshot = coordinator.start(
                deck = deck,
                cards = deckCards,
                nowEpochMillis = System.currentTimeMillis(),
                newCardLimit = remainingNewCards
            )
            deckProgress = coordinator.getDeckProgressSummary(deck, deckCards)
            errorMessage = null
        } catch (exception: Exception) {
            errorMessage = exception.localizedMessage ?: "Không thể bắt đầu phiên học."
        } finally {
            isLoading = false
        }
    }

    fun startAnotherSession() {
        val selectedDeck = deck ?: return
        scope.launch {
            isLoading = true
            awaitingRating = false
            try {
                val todayStart = localDayStartEpochMillis()
                newCardsStudiedToday = practiceLogRepository
                    .countDistinctNewCardsSince(todayStart)
                val remainingNewCards = (newCardsPerDay - newCardsStudiedToday).coerceAtLeast(0)
                snapshot = coordinator.start(
                    deck = selectedDeck,
                    cards = deckCards,
                    nowEpochMillis = System.currentTimeMillis(),
                    newCardLimit = remainingNewCards
                )
                deckProgress = coordinator.getDeckProgressSummary(selectedDeck, deckCards)
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể tải phiên học."
            } finally {
                isLoading = false
            }
        }
    }

    fun retryDifficultCards() {
        if (isLoading || isSaving) return
        try {
            snapshot = coordinator.retryDifficultCards()
            awaitingRating = false
            errorMessage = null
        } catch (exception: Exception) {
            errorMessage = exception.localizedMessage ?: "Không thể đưa thẻ khó vào luyện lại."
        }
    }

    fun submitRating(rating: ReviewRating) {
        if (isSaving || !awaitingRating) return
        scope.launch {
            isSaving = true
            try {
                snapshot = coordinator.rateCurrent(
                    rating = rating,
                    nowEpochMillis = System.currentTimeMillis()
                )
                newCardsStudiedToday = practiceLogRepository
                    .countDistinctNewCardsSince(localDayStartEpochMillis())
                deck?.let {
                    deckProgress = coordinator.getDeckProgressSummary(it, deckCards)
                }
                awaitingRating = false
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể lưu kết quả ôn tập."
            } finally {
                isSaving = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Ôn tập ${characterTypeLabel(deckType)}", style = MaterialTheme.typography.titleLarge)

        when {
            isLoading -> {
                CircularProgressIndicator()
                Text("Đang chuẩn bị phiên học...")
            }

            errorMessage != null && snapshot == null -> {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { startAnotherSession() }) {
                    Text("Thử lại")
                }
            }

            snapshot == null -> {
                Text("Chưa có phiên học.")
            }

            else -> {
                val session = snapshot!!
                deckProgress?.let { progress ->
                    Text(
                        "Đã học ${progress.learnedCards}/${progress.totalCards}  ·  Mới ${progress.newCards}  ·  Đang học ${progress.learningCards}  ·  Ôn tập ${progress.reviewCards}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Phiên này ${session.completedCount}/${session.totalCount}  ·  " +
                        "Chữ mới hôm nay: $newCardsStudiedToday/$newCardsPerDay",
                    style = MaterialTheme.typography.labelMedium
                )

                val current = session.current
                if (current != null) {
                    val character = charactersById[current.card.characterId]
                    Text(
                        when (current.kind) {
                            SessionCardKind.REVIEW -> "Ôn tập đến hạn"
                            SessionCardKind.LEARNING -> "Học tiếp"
                            SessionCardKind.RELEARNING -> "Học lại sau khi quên"
                            SessionCardKind.NEW -> "Học thẻ mới"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )

                    if (character == null) {
                        Text(
                            "Không tìm thấy dữ liệu ký tự ${current.card.characterId}.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!awaitingRating) {
                        WritingCanvas(
                            modifier = Modifier.weight(1f),
                            character = character,
                            initialMode = WritingMode.RECALL,
                            showModeSelector = false,
                            allowEditingAfterCompletion = false,
                            showGrid = showGrid,
                            showHints = showHints,
                            onCompleted = { awaitingRating = true }
                        )
                        if (errorMessage != null) {
                            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Tách bước đánh giá khỏi Canvas để không bị chồng nút
                        // lên vùng viết, đặc biệt trên màn hình ngang.
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                character.character,
                                fontSize = 76.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                character.reading,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Hoàn thành! Bạn nhớ chữ này tốt đến mức nào?",
                                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (errorMessage != null) {
                                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                            }

                            if (isSaving) {
                                CircularProgressIndicator()
                                Text("Đang lưu kết quả...")
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            onClick = { submitRating(ReviewRating.AGAIN) }
                                        ) {
                                            Text("Again · Học lại", maxLines = 1, softWrap = false)
                                        }
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            onClick = { submitRating(ReviewRating.HARD) }
                                        ) {
                                            Text("Hard · Khó", maxLines = 1, softWrap = false)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            onClick = { submitRating(ReviewRating.GOOD) }
                                        ) {
                                            Text("Good · Tốt", maxLines = 1, softWrap = false)
                                        }
                                        Button(
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            onClick = { submitRating(ReviewRating.EASY) }
                                        ) {
                                            Text("Easy · Dễ", maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (session.difficultCards.isNotEmpty()) {
                        Text(
                            "Có ${session.difficultCards.size} thẻ cần luyện thêm. Những thẻ này đã được tách khỏi lượt học chính để bạn không phải lặp lại liên tục.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { retryDifficultCards() }) {
                            Text("Luyện lại các thẻ khó")
                        }
                    } else {
                        val dailyLimitReached = newCardsStudiedToday >= newCardsPerDay
                        Text(
                            when {
                                session.plan.remainingNewCount > 0 && dailyLimitReached ->
                                    "Bạn đã đạt giới hạn chữ mới hôm nay. Tiến độ sẽ được làm mới vào ngày mai."
                                session.plan.remainingNewCount > 0 ->
                                    "Bạn đã hoàn thành lượt hiện tại. Có thể tiếp tục học các thẻ mới."
                                else ->
                                    "Không còn thẻ mới hoặc thẻ đến hạn trong phiên này."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    val dailyLimitReached = newCardsStudiedToday >= newCardsPerDay
                    if (session.plan.remainingNewCount == 0 || !dailyLimitReached) {
                        OutlinedButton(onClick = { startAnotherSession() }) {
                            Text(
                                if (session.plan.remainingNewCount > 0) "Học tiếp" else "Bắt đầu phiên mới"
                            )
                        }
                    }
                }
            }
        }
    }
}
