package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vocabulary session: write each glyph in sequence, then submit one FSRS
 * rating for the whole vocabulary card.
 */
@Composable
fun VocabularyLearningSessionScreen(
    availableCharacters: List<CharacterData>,
    newCardsPerDay: Int = 10,
    showGrid: Boolean = true,
    showHints: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) { LearningSessionCoordinator(context) }
    val logs = remember(context) { PracticeLogRepository(context) }
    val scope = rememberCoroutineScope()

    var target by remember { mutableStateOf(VocabularyWritingTarget.JAPANESE) }
    var vocabulary by remember { mutableStateOf<List<VocabularyData>>(emptyList()) }
    var catalog by remember { mutableStateOf<VocabularyDeckCatalog?>(null) }
    var snapshot by remember { mutableStateOf<LearningSessionSnapshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var awaitingRating by remember { mutableStateOf(false) }
    var newCardsStudiedToday by remember { mutableIntStateOf(0) }
    var attemptVersion by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        try {
            vocabulary = withContext(Dispatchers.IO) {
                VocabularyRepository.load(context)
            }
            errorMessage = null
        } catch (exception: Exception) {
            errorMessage = exception.localizedMessage ?: "Không thể tải dữ liệu từ vựng."
        }
    }

    fun startSession(selectedTarget: VocabularyWritingTarget) {
        val records = vocabulary
        if (records.isEmpty()) return
        scope.launch {
            attemptVersion++
            isLoading = true
            snapshot = null
            awaitingRating = false
            try {
                val nextCatalog = withContext(Dispatchers.Default) {
                    VocabularyDeckFactory.fromVocabulary(
                        vocabulary = records,
                        availableCharacters = availableCharacters,
                        target = selectedTarget
                    )
                }
                catalog = nextCatalog
                val todayStart = localDayStartEpochMillis()
                newCardsStudiedToday = logs.countDistinctNewCardsSince(
                    todayStart,
                    nextCatalog.cards.map { it.id }
                )
                val remainingNew = (newCardsPerDay - newCardsStudiedToday).coerceAtLeast(0)
                snapshot = coordinator.start(
                    deck = nextCatalog.deck,
                    cards = nextCatalog.cards,
                    nowEpochMillis = System.currentTimeMillis(),
                    newCardLimit = remainingNew
                )
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.localizedMessage ?: "Không thể bắt đầu phiên học từ vựng."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(vocabulary, target, availableCharacters) {
        if (vocabulary.isNotEmpty()) startSession(target)
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
                catalog?.let { currentCatalog ->
                    newCardsStudiedToday = logs.countDistinctNewCardsSince(
                        localDayStartEpochMillis(),
                        currentCatalog.cards.map { it.id }
                    )
                }
                if (rating == ReviewRating.AGAIN) attemptVersion++
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
        Text("Luyện viết từ vựng", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = target == VocabularyWritingTarget.JAPANESE,
                onClick = { target = VocabularyWritingTarget.JAPANESE },
                label = { Text("Japanese") }
            )
            FilterChip(
                selected = target == VocabularyWritingTarget.READING,
                onClick = { target = VocabularyWritingTarget.READING },
                label = { Text("Reading") }
            )
        }

        when {
            isLoading -> {
                CircularProgressIndicator()
                Text("Đang chuẩn bị bộ thẻ từ vựng...")
            }

            errorMessage != null && snapshot == null -> {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { startSession(target) }) { Text("Thử lại") }
            }

            catalog == null || snapshot == null -> Text("Chưa có phiên học.")
            else -> {
                val activeCatalog = catalog!!
                val session = snapshot!!
                Text(
                    "Đã học ${session.completedCount}/${session.totalCount} · " +
                        "Chữ mới hôm nay: $newCardsStudiedToday/$newCardsPerDay",
                    style = MaterialTheme.typography.labelMedium
                )
                if (activeCatalog.unsupportedEntries.isNotEmpty()) {
                    Text(
                        "${activeCatalog.unsupportedEntries.size} từ chưa thể luyện viết vì thiếu dữ liệu nét; chúng vẫn được giữ trong thư viện.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val current = session.current
                if (current != null) {
                    val sequence = activeCatalog.sequencesByCardId[current.card.id]
                    if (sequence == null) {
                        Text(
                            "Không tìm thấy chuỗi ký tự cho thẻ ${current.card.id}.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        var progress by remember(current.card.id, attemptVersion) {
                            mutableStateOf(VocabularyWritingProgress(sequence))
                        }
                        Text(
                            when (current.kind) {
                                SessionCardKind.REVIEW -> "Ôn tập đến hạn"
                                SessionCardKind.LEARNING -> "Học tiếp"
                                SessionCardKind.RELEARNING -> "Học lại sau khi quên"
                                SessionCardKind.NEW -> "Học từ mới"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "${current.card.character} · ${current.card.reading}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (!awaitingRating) {
                            val glyph = progress.currentCharacter
                            if (glyph != null) {
                                Text(
                                    "Ký tự ${progress.completedCharacterCount + 1}/${sequence.characters.size}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                key(current.card.id, progress.completedCharacterCount) {
                                    WritingCanvas(
                                        modifier = Modifier.weight(1f),
                                        character = glyph,
                                        initialMode = WritingMode.RECALL,
                                        allowEditingAfterCompletion = false,
                                        showGrid = showGrid,
                                        showHints = showHints,
                                        onCompleted = {
                                            progress = progress.completeCurrentCharacter()
                                            if (progress.isComplete) awaitingRating = true
                                        }
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    current.card.character,
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(current.card.reading, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Bạn đã viết xong từ này. Bạn nhớ từ này tốt đến mức nào?",
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (isSaving) {
                                    CircularProgressIndicator()
                                    Text("Đang lưu kết quả...")
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 12.dp),
                                                onClick = { submitRating(ReviewRating.AGAIN) }
                                            ) { Text("Again · Học lại", maxLines = 1, softWrap = false) }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 12.dp),
                                                onClick = { submitRating(ReviewRating.HARD) }
                                            ) { Text("Hard · Khó", maxLines = 1, softWrap = false) }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 12.dp),
                                                onClick = { submitRating(ReviewRating.GOOD) }
                                            ) { Text("Good · Tốt", maxLines = 1, softWrap = false) }
                                            Button(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 12.dp),
                                                onClick = { submitRating(ReviewRating.EASY) }
                                            ) { Text("Easy · Dễ", maxLines = 1, softWrap = false) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (session.difficultCards.isNotEmpty()) {
                        Text(
                            "Có ${session.difficultCards.size} thẻ cần luyện thêm.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = {
                            attemptVersion++
                            snapshot = coordinator.retryDifficultCards()
                        }) { Text("Luyện lại các thẻ khó") }
                    } else {
                        Text(
                            if (session.plan.remainingNewCount > 0 && newCardsStudiedToday >= newCardsPerDay) {
                                "Bạn đã đạt giới hạn từ mới hôm nay."
                            } else {
                                "Không còn từ mới hoặc từ đến hạn trong phiên này."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { startSession(target) }) {
                            Text("Bắt đầu phiên mới")
                        }
                    }
                }
            }
        }
        if (errorMessage != null && snapshot != null) {
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
