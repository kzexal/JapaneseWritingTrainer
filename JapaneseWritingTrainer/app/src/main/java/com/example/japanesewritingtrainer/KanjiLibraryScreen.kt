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

/** Metadata-only Kanji browser. Writing practice is intentionally unavailable until stroke paths exist. */
@Composable
fun KanjiLibraryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var records by remember { mutableStateOf<List<KanjiData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedJlpt by remember { mutableStateOf<String?>(null) }
    var selectedRadical by remember { mutableStateOf<String?>(null) }
    var selectedKanjiId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        try {
            records = withContext(Dispatchers.IO) { KanjiRepository.load(context) }
            loadError = null
        } catch (exception: Exception) {
            loadError = exception.localizedMessage ?: "Không thể tải dữ liệu Kanji."
        } finally {
            isLoading = false
        }
    }

    val selectedKanji = records.firstOrNull { it.id == selectedKanjiId }
    when {
        isLoading -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("Đang tải dữ liệu Kanji...", modifier = Modifier.padding(top = 12.dp))
        }

        loadError != null -> Text(
            "Không tải được dữ liệu Kanji: $loadError",
            modifier = modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error
        )

        selectedKanji != null -> KanjiDetail(
            kanji = selectedKanji,
            onBack = { selectedKanjiId = null },
            modifier = modifier
        )

        else -> {
            val jlptLevels = remember(records) { KanjiLibrary.availableJlptLevels(records) }
            val radicals = remember(records) { KanjiLibrary.availableRadicals(records) }
            val filtered = remember(records, query, selectedJlpt, selectedRadical) {
                KanjiLibrary.filter(records, query, selectedJlpt, selectedRadical)
            }

            Column(
                modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Thư viện Kanji", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tìm Kanji, âm đọc hoặc nghĩa") },
                    singleLine = true
                )

                Text("JLPT", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedJlpt == null,
                            onClick = { selectedJlpt = null },
                            label = { Text("Tất cả") }
                        )
                    }
                    items(jlptLevels.size) { index ->
                        val level = jlptLevels[index]
                        FilterChip(
                            selected = selectedJlpt == level,
                            onClick = { selectedJlpt = if (selectedJlpt == level) null else level },
                            label = { Text(level) }
                        )
                    }
                }

                Text("Bộ thủ", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedRadical == null,
                            onClick = { selectedRadical = null },
                            label = { Text("Tất cả") }
                        )
                    }
                    items(radicals.size) { index ->
                        val radical = radicals[index]
                        FilterChip(
                            selected = selectedRadical == radical,
                            onClick = {
                                selectedRadical = if (selectedRadical == radical) null else radical
                            },
                            label = { Text(radical) }
                        )
                    }
                }

                Text(
                    "${filtered.size} / ${records.size} Kanji",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Không tìm thấy Kanji phù hợp.",
                        modifier = Modifier.padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { kanji ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedKanjiId = kanji.id }
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
}

@Composable
private fun KanjiDetail(
    kanji: KanjiData,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("Quay lại thư viện") }
        Text(kanji.character, fontSize = 76.sp, style = MaterialTheme.typography.displayLarge)
        Text(
            kanji.meaning ?: "Chưa có nghĩa",
            style = MaterialTheme.typography.headlineSmall
        )
        KanjiDetailRow("Số nét", kanji.strokeCount.toString())
        KanjiDetailRow("Onyomi", kanji.onyomi)
        KanjiDetailRow("Kunyomi", kanji.kunyomi)
        KanjiDetailRow("Bộ thủ", kanji.radical)
        KanjiDetailRow("JLPT", kanji.jlpt)
        KanjiDetailRow("Từ ví dụ", kanji.exampleWords)
        KanjiDetailRow("Ghi chú", kanji.notes)
        KanjiDetailRow("Tags", kanji.tags)
        Text(
            "Dữ liệu hiện tại chỉ gồm thông tin Kanji, chưa có dữ liệu thứ tự và hình học nét viết nên chưa hỗ trợ luyện viết.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
