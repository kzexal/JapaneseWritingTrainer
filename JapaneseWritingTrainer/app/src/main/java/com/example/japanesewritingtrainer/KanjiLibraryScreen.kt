package com.example.japanesewritingtrainer

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kanji browser backed by workbook metadata and validated KanjiVG stroke geometry. */
@Composable
fun KanjiLibraryScreen(
    records: List<KanjiData>,
    writingCharacters: List<CharacterData>,
    resetKey: Int = 0,
    modifier: Modifier = Modifier,
    onPracticeFinished: ((String, PracticeMode, Long, Long) -> Unit)? = null,
    onDetailStateChanged: (Boolean) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var selectedRadicalNumber by remember { mutableStateOf<Int?>(null) }
    var selectedKanjiId by remember { mutableStateOf<String?>(null) }
    var loadedWritingCharacter by remember { mutableStateOf<CharacterData?>(null) }
    var geometryLoading by remember { mutableStateOf(false) }
    var geometryError by remember { mutableStateOf<String?>(null) }
    var geometryRetry by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()
    val context = LocalContext.current.applicationContext
    val recordsById = remember(records) { records.associateBy { it.id } }
    val writingCharactersById = remember(writingCharacters) {
        writingCharacters.associateBy { it.id }
    }
    val selectedKanji = recordsById[selectedKanjiId]
    val selectedWritingCharacter = selectedKanji?.let {
        writingCharactersById[it.id] ?: loadedWritingCharacter?.takeIf { loaded -> loaded.id == it.id }
    }

    LaunchedEffect(selectedKanjiId, writingCharacters, geometryRetry) {
        val kanjiId = selectedKanjiId ?: return@LaunchedEffect
        if (writingCharactersById.containsKey(kanjiId)) {
            loadedWritingCharacter = null
            geometryError = null
            return@LaunchedEffect
        }
        val record = recordsById[kanjiId] ?: return@LaunchedEffect
        loadedWritingCharacter = null
        geometryLoading = true
        geometryError = null
        try {
            loadedWritingCharacter = withContext(Dispatchers.IO) {
                KanjiStrokeRepository.loadCharacter(context, kanjiId, record)
            }
        } catch (exception: Exception) {
            geometryError = exception.localizedMessage ?: "Không thể tải dữ liệu nét viết."
        } finally {
            geometryLoading = false
        }
    }
    LaunchedEffect(resetKey) {
        selectedKanjiId = null
        loadedWritingCharacter = null
        geometryError = null
        onDetailStateChanged(false)
    }

    val libraryIndex = remember(records) { KanjiLibrary.createIndex(records) }
    val radicals = remember(libraryIndex) {
        KanjiLibrary.availableRadicalGroups(libraryIndex)
    }
    val filtered = remember(libraryIndex, query, selectedRadicalNumber) {
        KanjiLibrary.filter(
            index = libraryIndex,
            query = query,
            radical = selectedRadicalNumber?.let { "($it)" }
        )
    }

    BackHandler(enabled = selectedKanji != null) {
        selectedKanjiId = null
        onDetailStateChanged(false)
    }
    when {
        selectedKanji != null -> KanjiDetail(
            kanji = selectedKanji,
            character = selectedWritingCharacter,
            onPracticeFinished = onPracticeFinished,
            onBack = {
                selectedKanjiId = null
                loadedWritingCharacter = null
                geometryError = null
                onDetailStateChanged(false)
            },
            isLoading = geometryLoading,
            loadError = geometryError,
            onRetry = { geometryRetry++ },
            modifier = modifier
        )

        else -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Thư viện Kanji", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Tìm Kanji, âm đọc, Romaji hoặc nghĩa") },
                            singleLine = true
                        )
                        Text("Bộ thủ", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedRadicalNumber == null,
                                    onClick = { selectedRadicalNumber = null },
                                    label = { Text("Tất cả") }
                                )
                            }
                            items(radicals.size) { index ->
                                val radical = radicals[index]
                                FilterChip(
                                    selected = selectedRadicalNumber == radical.number,
                                    onClick = {
                                        selectedRadicalNumber =
                                            if (selectedRadicalNumber == radical.number) null else radical.number
                                    },
                                    label = {
                                        Text("${radical.label} · ${radical.count}")
                                    }
                                )
                            }
                        }
                        Text(
                            "${filtered.size} / ${records.size} Kanji",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        Text(
                            "Không tìm thấy Kanji phù hợp.",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(
                        items = filtered,
                        key = { it.id },
                        contentType = { "kanji" }
                    ) { kanji ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedKanjiId = kanji.id
                                    loadedWritingCharacter = writingCharactersById[kanji.id]
                                    geometryError = null
                                    onDetailStateChanged(true)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(kanji.character, fontSize = 34.sp)
                                Text(
                                    kanji.meaning ?: "Chưa có nghĩa",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2
                                )
                                Text(
                                    "${kanji.strokeCount} nét · ${kanji.jlpt ?: "Chưa phân loại"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanjiDetail(
    kanji: KanjiData,
    character: CharacterData?,
    onPracticeFinished: ((String, PracticeMode, Long, Long) -> Unit)?,
    onBack: () -> Unit,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Quay lại") }
            Text(
                "${kanji.character} · ${kanji.meaning ?: "Chưa có nghĩa"}",
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1
            )
        }
        Text(
            "${kanji.strokeCount} nét · ${kanji.jlpt ?: "Chưa phân loại"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "On: ${kanji.onyomi ?: "—"}   Kun: ${kanji.kunyomi ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (character == null && isLoading) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text("Đang tải dữ liệu nét viết...")
            }
        } else if (character == null) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(loadError ?: "Không có dữ liệu nét viết cho Kanji này.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Thử tải lại") }
            }
        } else {
            WritingCanvas(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                character = character,
                initialMode = WritingMode.GUIDED,
                onPracticeFinished = { mode, durationMs, finishedAt ->
                    onPracticeFinished?.invoke(kanji.id, mode, durationMs, finishedAt)
                }
            )
        }
    }
}

@Composable
private fun KanjiDetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
