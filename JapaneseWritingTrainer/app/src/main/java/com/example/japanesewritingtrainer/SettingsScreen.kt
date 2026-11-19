package com.example.japanesewritingtrainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    hiraganaNewCardsPerDay: Int,
    onHiraganaNewCardsPerDayChange: (Int) -> Unit,
    katakanaNewCardsPerDay: Int,
    onKatakanaNewCardsPerDayChange: (Int) -> Unit,
    kanjiNewCardsPerDay: Int,
    onKanjiNewCardsPerDayChange: (Int) -> Unit,
    showGrid: Boolean,
    onShowGridChange: (Boolean) -> Unit,
    showHints: Boolean,
    onShowHintsChange: (Boolean) -> Unit,
    reminderEnabled: Boolean,
    reminderHour: Int,
    reminderMinute: Int,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    restoreConfirmationVisible: Boolean,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showSourcesDialog by remember { mutableStateOf(false) }
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?: "Không xác định"
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cài đặt",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedButton(onClick = onBackClick) {
                Text("Quay lại")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Giao diện",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Chọn cách ứng dụng hiển thị màu sắc.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AppThemeMode.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == option,
                            onClick = { onThemeModeChange(option) }
                        )
                        Text(
                            text = option.label,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Luyện tập",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Giới hạn được áp dụng riêng cho từng bộ chữ và được tính theo ngày.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Hiragana · chữ mới mỗi ngày: $hiraganaNewCardsPerDay")
                    Slider(
                        value = hiraganaNewCardsPerDay.toFloat(),
                        onValueChange = { onHiraganaNewCardsPerDayChange(it.toInt()) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Katakana · chữ mới mỗi ngày: $katakanaNewCardsPerDay")
                    Slider(
                        value = katakanaNewCardsPerDay.toFloat(),
                        onValueChange = { onKatakanaNewCardsPerDayChange(it.toInt()) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kanji · chữ mới mỗi ngày: $kanjiNewCardsPerDay")
                    Slider(
                        value = kanjiNewCardsPerDay.toFloat(),
                        onValueChange = { onKanjiNewCardsPerDayChange(it.toInt()) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hiển thị lưới viết")
                        Text(
                            "Hiện khung và đường căn giữa trên Canvas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showGrid,
                        onCheckedChange = onShowGridChange,
                        modifier = Modifier.semantics { contentDescription = "Hiển thị lưới viết" }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hiển thị gợi ý")
                        Text(
                            "Cho phép mở nút gợi ý khi gặp khó khăn trong Recall.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showHints,
                        onCheckedChange = onShowHintsChange,
                        modifier = Modifier.semantics { contentDescription = "Hiển thị gợi ý" }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Nhắc học", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ứng dụng sẽ gửi thông báo mỗi ngày vào giờ bạn chọn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bật nhắc học", modifier = Modifier.weight(1f))
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = onReminderEnabledChange,
                        modifier = Modifier.semantics { contentDescription = "Bật nhắc học hằng ngày" }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Giờ nhắc")
                        Text(
                            String.format(java.util.Locale.getDefault(), "%02d:%02d", reminderHour, reminderMinute),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onReminderTimeChange(hour, minute) },
                            reminderHour,
                            reminderMinute,
                            true
                        ).show()
                    }) { Text("Chọn giờ") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Widget màn hình chính", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Widget hiển thị số ngày streak và số chữ cần ôn. Để thêm widget, nhấn giữ vùng trống " +
                        "trên màn hình chính, chọn Widget, tìm Japanese Writing Trainer rồi kéo widget 2×1 vào màn hình.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Dữ liệu và sao lưu",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Sao lưu tiến độ FSRS và lịch sử luyện viết thành một tệp JSON, " +
                        "hoặc khôi phục dữ liệu từ tệp đã tạo trước đó.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onBackupClick
                    ) {
                        Text("Sao lưu")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onRestoreClick
                    ) {
                        Text("Khôi phục")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Nguồn dữ liệu và giấy phép",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Ứng dụng sử dụng dữ liệu Kanji từ OpenJLPT và hình học nét từ KanjiVG. " +
                        "Thông tin ghi công và điều khoản ShareAlike được trình bày riêng bên dưới.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showSourcesDialog = true }) {
                    Text("Xem nguồn và giấy phép")
                }
            }
        }

        Text(
            text = "Japanese Writing Trainer · Phiên bản $versionName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showSourcesDialog) {
        AlertDialog(
            onDismissRequest = { showSourcesDialog = false },
            title = { Text("Nguồn dữ liệu và giấy phép") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Kanji metadata: OpenJLPT, dựa trên KANJIDIC2 (EDRDG) và danh sách JLPT cộng đồng của Jonathan Waller. " +
                            "Dữ liệu OpenJLPT được phân phối theo CC BY-SA 4.0; dữ liệu phái sinh phải giữ điều khoản ShareAlike.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "OpenJLPT: https://github.com/evanclan/OpenJLPT",
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/evanclan/OpenJLPT")
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "EDRDG license: https://www.edrdg.org/edrdg/licence.html",
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://www.edrdg.org/edrdg/licence.html")
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "JLPT levels are community approximations, not official lists published by JLPT.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Stroke geometry: KanjiVG, copyright © Ulrich Apel and contributors, licensed under CC BY-SA 3.0.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "KanjiVG: https://github.com/KanjiVG/kanjivg",
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/KanjiVG/kanjivg")
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/\n" +
                            "CC BY-SA 3.0: https://creativecommons.org/licenses/by-sa/3.0/",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSourcesDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (restoreConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Khôi phục dữ liệu?") },
            text = {
                Text(
                    "Dữ liệu tiến độ và lịch sử hiện tại sẽ bị thay thế bằng dữ liệu trong " +
                        "tệp sao lưu. Bạn nên sao lưu dữ liệu hiện tại trước khi tiếp tục."
                )
            },
            confirmButton = {
                Button(onClick = onConfirmRestore) {
                    Text("Khôi phục")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onCancelRestore) {
                    Text("Hủy")
                }
            }
        )
    }
}
