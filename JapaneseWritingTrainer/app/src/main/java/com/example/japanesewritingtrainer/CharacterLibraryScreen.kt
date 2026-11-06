package com.example.japanesewritingtrainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    showGrid: Boolean = true,
    showHints: Boolean = true,
    onPracticeFinished: ((PracticeMode, Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val progressRepository = remember(context) { CardProgressRepository(context) }
    var progressById by remember { mutableStateOf<Map<String, CardProgress>>(emptyMap()) }
    var selectedCharacterId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("all") }

    LaunchedEffect(progressRepository, characters) {
        progressById = progressRepository.getAll().associateBy { it.cardId }
    }

    val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId }
    if (selectedCharacter != null) {
        CharacterDetailContent(
            character = selectedCharacter,
            progress = progressById[selectedCharacter.id],
            onBack = { selectedCharacterId = null },
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
    val filteredCharacters = remember(characters, query, selectedGroup) {
        characters.filter { character ->
            val matchesGroup = selectedGroup == "all" || character.group == selectedGroup
            val normalizedQuery = query.trim().lowercase()
            val matchesQuery = normalizedQuery.isBlank() ||
                character.character.lowercase().contains(normalizedQuery) ||
                character.reading.lowercase().contains(normalizedQuery) ||
                character.id.lowercase().contains(normalizedQuery)
            matchesGroup && matchesQuery
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Thư viện Hiragana", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tìm ký tự hoặc romaji") },
            singleLine = true
        )
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
        Text(
            "${filteredCharacters.size} / ${characters.size} ký tự",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (filteredCharacters.isEmpty()) {
            Text(
                "Không tìm thấy ký tự phù hợp.",
                modifier = Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCharacters, key = { it.id }) { character ->
                    val progress = progressById[character.id]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCharacterId = character.id }
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
                                progress.statusLabel(),
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

@Composable
private fun CharacterDetailContent(
    character: CharacterData,
    progress: CardProgress?,
    onBack: () -> Unit,
    showGrid: Boolean,
    showHints: Boolean,
    onPracticeFinished: ((PracticeMode, Long, Long) -> Unit)?,
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
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "Chọn Trace, Guided hoặc Recall để luyện viết. Nút xem thứ tự nét sẽ phát lại animation mẫu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WritingCanvas(
            modifier = Modifier.weight(1f),
            character = character,
            initialMode = WritingMode.TRACE,
            showGrid = showGrid,
            showHints = showHints,
            onPracticeFinished = onPracticeFinished
        )
    }
}

private fun CardProgress?.statusLabel(): String = when (this?.status ?: CardStatus.NEW) {
    CardStatus.NEW -> "Mới"
    CardStatus.LEARNING, CardStatus.RELEARNING -> "Đang học"
    CardStatus.REVIEW -> "Đã học"
}
