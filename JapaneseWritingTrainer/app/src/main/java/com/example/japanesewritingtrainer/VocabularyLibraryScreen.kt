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
import androidx.compose.material3.CircularProgressIndicator
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

/** Workbook-backed Vocabulary browser. It is read-only and does not create learner cards. */
@Composable
fun VocabularyLibraryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    var records by remember { mutableStateOf<List<VocabularyData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedVocabularyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        try {
            records = withContext(Dispatchers.IO) {
                VocabularyRepository.load(context)
            }
            loadError = null
        } catch (exception: Exception) {
            loadError = exception.localizedMessage ?: "Không thể tải dữ liệu từ vựng."
        } finally {
            isLoading = false
        }
    }

    val selectedVocabulary = records.firstOrNull { it.id == selectedVocabularyId }
    when {
        isLoading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("Đang tải dữ liệu từ vựng...", modifier = Modifier.padding(top = 12.dp))
        }

        loadError != null -> Text(
            "Không tải được dữ liệu từ vựng: $loadError",
            modifier = modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error
        )

        selectedVocabulary != null -> VocabularyDetail(
            vocabulary = selectedVocabulary,
            onBack = { selectedVocabularyId = null },
            modifier = modifier
        )

        else -> {
            val groups = remember(records) { VocabularyLibrary.availableGroups(records) }
            val filtered = remember(records, query, selectedGroup) {
                VocabularyLibrary.filter(records, query, selectedGroup)
            }

            Column(
                modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Thư viện từ vựng", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tìm từ tiếng Nhật, cách đọc hoặc nghĩa") },
                    singleLine = true
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedGroup == null,
                            onClick = { selectedGroup = null },
                            label = { Text("Tất cả") }
                        )
                    }
                    items(groups.size) { index ->
                        val group = groups[index]
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = {
                                selectedGroup = if (selectedGroup == group) null else group
                            },
                            label = { Text(group) }
                        )
                    }
                }
                Text(
                    "${filtered.size} / ${records.size} từ vựng",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Không tìm thấy từ vựng phù hợp.",
                        modifier = Modifier.padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedVocabularyId = item.id }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        item.japanese,
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        item.reading,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        item.vietnamese,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    item.group?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabularyDetail(
    vocabulary: VocabularyData,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Quay lại") }
            Text(
                "Chi tiết từ vựng",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(vocabulary.japanese, style = MaterialTheme.typography.headlineLarge)
            Text(vocabulary.reading, style = MaterialTheme.typography.titleMedium)
            Text(
                vocabulary.vietnamese,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        VocabularyDetailRow("Ví dụ", vocabulary.example)
        VocabularyDetailRow("Nghĩa ví dụ", vocabulary.exampleMeaning)
        VocabularyDetailRow("Nhóm", vocabulary.group)
        VocabularyDetailRow("Bộ thủ", vocabulary.radicals)
        VocabularyDetailRow("Gợi nhớ", vocabulary.mnemonics)
        VocabularyDetailRow("JLPT", vocabulary.jlpt)
        VocabularyDetailRow("Ghi chú", vocabulary.notes)
        VocabularyDetailRow("Tags", vocabulary.tags)
    }
}

@Composable
private fun VocabularyDetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
