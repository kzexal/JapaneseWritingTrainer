package com.example.japanesewritingtrainer

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    showGrid: Boolean,
    onShowGridChange: (Boolean) -> Unit,
    showHints: Boolean,
    onShowHintsChange: (Boolean) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    restoreConfirmationVisible: Boolean,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                    Switch(checked = showGrid, onCheckedChange = onShowGridChange)
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
                    Switch(checked = showHints, onCheckedChange = onShowHintsChange)
                }
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Ghi công dữ liệu",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Dữ liệu thứ tự nét Hiragana và Katakana được chuyển đổi từ KanjiVG. " +
                        "Copyright © Ulrich Apel. Licensed under Creative Commons " +
                        "Attribution-ShareAlike 3.0 (CC BY-SA 3.0). " +
                        "Nguồn: https://github.com/KanjiVG/kanjivg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Japanese Writing Trainer · Phiên bản $versionName",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
