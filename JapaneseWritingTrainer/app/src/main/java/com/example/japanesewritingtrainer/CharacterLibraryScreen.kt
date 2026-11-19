package com.example.japanesewritingtrainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Thư viện ký tự: tìm theo chữ/romaji, lọc theo hàng và xem trạng thái học.
 * Chạm vào một ký tự để mở trang chi tiết và xem animation thứ tự nét.
 */
@Composable
fun CharacterLibraryScreen(
    characters: List<CharacterData>,
    deckName: String = characters.firstOrNull()?.type?.let(::characterTypeLabel) ?: "ký tự",
    showGrid: Boolean = true,
    showHints: Boolean = true,
    onPracticeFinished: ((String, PracticeMode, Long, Long) -> Unit)? = null,
    onDetailStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val progressRepository = remember(context) { CardProgressRepository(context) }
    var progressById by remember { mutableStateOf<Map<String, CardProgress>>(emptyMap()) }
    var selectedCharacterId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("all") }
    var selectedStatus by remember { mutableStateOf("all") }

    LaunchedEffect(progressRepository, characters) {
        progressById = progressRepository.getAll().associateBy { it.cardId }
    }

    val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId }
    BackHandler(enabled = selectedCharacter != null) {
        selectedCharacterId = null
        onDetailStateChanged(false)
    }
    if (selectedCharacter != null) {
        CharacterDetailContent(
            character = selectedCharacter,
            progress = progressById[selectedCharacter.id],
            onBack = {
                selectedCharacterId = null
                onDetailStateChanged(false)
            },
            showGrid = showGrid,
            showHints = showHints,
            onPracticeFinished = onPracticeFinished,
            modifier = modifier
        )
        return
    }

    val groups = remember(characters) {
        characters.map { it.group }.distinct().sorted()
    }
    val filteredCharacters = remember(characters, query, selectedGroup, selectedStatus, progressById) {
        characters.filter { character ->
            val matchesGroup = selectedGroup == "all" || character.group == selectedGroup
            val normalizedQuery = query.trim().lowercase()
            val matchesQuery = normalizedQuery.isBlank() ||
                character.character.lowercase().contains(normalizedQuery) ||
                character.reading.lowercase().contains(normalizedQuery) ||
                character.id.lowercase().contains(normalizedQuery)
            val status = progressById[character.id]?.status ?: CardStatus.NEW
            val matchesStatus = when (selectedStatus) {
                "new" -> status == CardStatus.NEW
                "learning" -> status == CardStatus.LEARNING || status == CardStatus.RELEARNING
                "learned" -> status == CardStatus.REVIEW
                else -> true
            }
            matchesGroup && matchesQuery && matchesStatus
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 88.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Thư viện $deckName", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tìm ký tự hoặc romaji") },
                    singleLine = true
                )
                Text("Nhóm ký tự", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedGroup == "all",
                            onClick = { selectedGroup = "all" },
                            label = { Text("Tất cả") }
                        )
                    }
                    items(groups.size) { index ->
                        val group = groups[index]
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
                            label = { Text(group.uppercase()) }
                        )
                    }
                }
                Text("Trạng thái học", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to "Tất cả",
                        "new" to "Mới",
                        "learning" to "Đang học",
                        "learned" to "Đã thuộc"
                    ).forEach { (value, label) ->
                        item {
                            FilterChip(
                                selected = selectedStatus == value,
                                onClick = { selectedStatus = value },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Text(
                    "${filteredCharacters.size} / ${characters.size} ký tự",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (filteredCharacters.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                Text(
                    "Không tìm thấy ký tự phù hợp.",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = filteredCharacters,
                key = { it.id },
                contentType = { "character" }
            ) { character ->
                val progress = progressById[character.id]
                val status = progress?.status ?: CardStatus.NEW
                val statusContainerColor = when (status) {
                    CardStatus.NEW -> MaterialTheme.colorScheme.surfaceContainerHigh
                    CardStatus.LEARNING, CardStatus.RELEARNING -> MaterialTheme.colorScheme.primaryContainer
                    CardStatus.REVIEW -> MaterialTheme.colorScheme.tertiaryContainer
                }
                val statusContentColor = when (status) {
                    CardStatus.NEW -> MaterialTheme.colorScheme.onSurface
                    CardStatus.LEARNING, CardStatus.RELEARNING -> MaterialTheme.colorScheme.onPrimaryContainer
                    CardStatus.REVIEW -> MaterialTheme.colorScheme.onTertiaryContainer
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedCharacterId = character.id
                            onDetailStateChanged(true)
                        },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = statusContainerColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(character.character, fontSize = 32.sp)
                        Text(
                            character.reading,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (status == CardStatus.REVIEW) "✓ ${progress.statusLabel()}" else progress.statusLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusContentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailContent(
    character: CharacterData,
    progress: CardProgress?,
    onBack: () -> Unit,
    showGrid: Boolean,
    showHints: Boolean,
    onPracticeFinished: ((String, PracticeMode, Long, Long) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Quay lại") }
            Text(
                "Chi tiết ký tự",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleLarge
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${character.character}  ·  ${character.reading}",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "${character.strokeCount} nét  ·  Nhóm ${character.group.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                progress.statusLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = progress.statusColor()
            )
        }
        WritingCanvas(
            modifier = Modifier.weight(1f),
            character = character,
            initialMode = WritingMode.GUIDED,
            showGrid = showGrid,
            showHints = showHints,
            onPracticeFinished = { mode, durationMs, finishedAt ->
                onPracticeFinished?.invoke(character.id, mode, durationMs, finishedAt)
            }
        )
    }
}

private fun CardProgress?.statusLabel(): String = when (this?.status ?: CardStatus.NEW) {
    CardStatus.NEW -> "Mới"
    CardStatus.LEARNING, CardStatus.RELEARNING -> "Đang học"
    CardStatus.REVIEW -> "Đã thuộc"
}


@Composable
private fun CardProgress?.statusColor(): Color = when (this?.status ?: CardStatus.NEW) {
    CardStatus.NEW -> if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF246BB2)
    CardStatus.LEARNING, CardStatus.RELEARNING -> MaterialTheme.colorScheme.error
    CardStatus.REVIEW -> MaterialTheme.colorScheme.tertiary
}
