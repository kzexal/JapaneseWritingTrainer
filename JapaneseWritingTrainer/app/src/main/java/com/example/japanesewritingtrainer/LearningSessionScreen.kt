package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DEFAULT_NEW_CARD_LIMIT = 10

/**
 * Phiên ôn tập Hiragana:
 * 1. Planner chọn thẻ đến hạn trước, sau đó thêm thẻ mới.
 * 2. Người học viết ở Recall Writing.
 * 3. Sau khi hoàn thành và xem mẫu, người học tự chọn Again/Hard/Good/Easy.
 * 4. Coordinator lưu tiến độ qua Room rồi mới chuyển sang thẻ tiếp theo.
 */
@Composable
fun LearningSessionScreen(
    characters: List<CharacterData>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) { LearningSessionCoordinator(context) }
    val catalog = remember(characters) { DeckFactory.fromCharacters(characters) }
    val deck = catalog.decks.firstOrNull { it.characterType == "hiragana" }
    val deckCards = remember(catalog, deck) {
        deck?.let { catalog.cardsForDeck(it.id) }.orEmpty()
    }
    val charactersById = remember(characters) { characters.associateBy { it.id } }
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<LearningSessionSnapshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var awaitingRating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(coordinator, deck, deckCards) {
        if (deck == null) {
            isLoading = false
            errorMessage = "Không tìm thấy bộ thẻ Hiragana."
            return@LaunchedEffect
        }

        try {
            snapshot = coordinator.start(
                deck = deck,
                cards = deckCards,
                nowEpochMillis = System.currentTimeMillis(),
                newCardLimit = DEFAULT_NEW_CARD_LIMIT
            )
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
                snapshot = coordinator.start(
                    deck = selectedDeck,
                    cards = deckCards,
                    nowEpochMillis = System.currentTimeMillis(),
                    newCardLimit = DEFAULT_NEW_CARD_LIMIT
                )
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể tải phiên học."
            } finally {
                isLoading = false
            }
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ôn tập Hiragana", style = MaterialTheme.typography.titleLarge)

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
                Text(
                    "Tiến độ: ${session.completedCount} / ${session.totalCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Thẻ mới còn lại trong bộ: ${session.plan.remainingNewCount}",
                    style = MaterialTheme.typography.bodySmall
                )

                val current = session.current
                if (current != null) {
                    val character = charactersById[current.card.characterId]
                    Text(
                        text = if (current.kind == SessionCardKind.REVIEW) {
                            "Ôn tập đến hạn"
                        } else {
                            "Học thẻ mới"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )

                    if (character == null) {
                        Text(
                            "Không tìm thấy dữ liệu ký tự ${current.card.characterId}.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        WritingCanvas(
                            modifier = Modifier.weight(1f),
                            character = character,
                            initialMode = WritingMode.RECALL,
                            showModeSelector = false,
                            allowEditingAfterCompletion = false,
                            onCompleted = { awaitingRating = true }
                        )
                    }

                    if (awaitingRating && character != null) {
                        Text(
                            "Bạn nhớ ký tự này tốt đến mức nào?",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (errorMessage != null) {
                            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                        if (isSaving) {
                            CircularProgressIndicator()
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { submitRating(ReviewRating.AGAIN) }
                                ) { Text("Again") }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = { submitRating(ReviewRating.HARD) }
                                ) { Text("Hard") }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { submitRating(ReviewRating.GOOD) }
                                ) { Text("Good") }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { submitRating(ReviewRating.EASY) }
                                ) { Text("Easy") }
                            }
                        }
                    } else if (errorMessage != null) {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(
                        if (session.plan.remainingNewCount > 0) {
                            "Bạn đã hoàn thành nhóm thẻ này. Có thể tiếp tục học các thẻ mới."
                        } else {
                            "Không còn thẻ mới hoặc thẻ đến hạn trong phiên này."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { startAnotherSession() }) {
                        Text(
                            if (session.plan.remainingNewCount > 0) {
                                "Học tiếp"
                            } else {
                                "Kiểm tra lịch ôn"
                            }
                        )
                    }
                }

                if (errorMessage != null && snapshot != null && !awaitingRating) {
                    Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
