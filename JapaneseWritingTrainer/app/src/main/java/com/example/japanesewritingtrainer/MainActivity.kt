package com.example.japanesewritingtrainer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.japanesewritingtrainer.ui.theme.JapaneseWritingTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current.applicationContext
            val appSettings = remember(context) { AppSettings(context) }
            var themeMode by remember { mutableStateOf(appSettings.getThemeMode()) }
            var hiraganaNewCardsPerDay by remember {
                mutableIntStateOf(appSettings.getNewCardsPerDay("hiragana"))
            }
            var katakanaNewCardsPerDay by remember {
                mutableIntStateOf(appSettings.getNewCardsPerDay("katakana"))
            }
            var showGrid by remember { mutableStateOf(appSettings.getShowGrid()) }
            var showHints by remember { mutableStateOf(appSettings.getShowHints()) }
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDarkTheme
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            JapaneseWritingTrainerTheme(darkTheme = darkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current.applicationContext
                    val practiceLogRepository = remember(context) { PracticeLogRepository(context) }
                    val practiceLogScope = rememberCoroutineScope()
                    var characters by remember { mutableStateOf<List<CharacterData>>(emptyList()) }
                    var selectedDeckType by remember {
                        mutableStateOf(appSettings.getSelectedCharacterType())
                    }
                    val selectedCharacters = remember(characters, selectedDeckType) {
                        characters.filter { it.type.equals(selectedDeckType, ignoreCase = true) }
                    }
                    val selectedNewCardsPerDay = if (selectedDeckType == "katakana") {
                        katakanaNewCardsPerDay
                    } else {
                        hiraganaNewCardsPerDay
                    }
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    var loadError by remember { mutableStateOf<String?>(null) }
                    var selectedScreen by remember { mutableStateOf(MainScreen.HOME) }
                    val backupRestoreManager = remember(context) { BackupRestoreManager(context) }
                    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

                    val backupLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        if (uri != null) {
                            practiceLogScope.launch {
                                try {
                                    val json = withContext(Dispatchers.IO) {
                                        backupRestoreManager.createBackupJson()
                                    }
                                    withContext(Dispatchers.IO) {
                                        val output = context.contentResolver.openOutputStream(uri)
                                            ?: error("Không thể mở tệp để ghi.")
                                        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                                    }
                                    Toast.makeText(context, "Đã sao lưu dữ liệu.", Toast.LENGTH_SHORT).show()
                                } catch (exception: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Sao lưu thất bại: ${exception.localizedMessage ?: "Lỗi không xác định"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                    val restoreLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            practiceLogScope.launch {
                                try {
                                    val json = withContext(Dispatchers.IO) {
                                        val input = context.contentResolver.openInputStream(uri)
                                            ?: error("Không thể mở tệp đã chọn.")
                                        val bytes = input.use { it.readBytes() }
                                        require(bytes.size <= MAX_BACKUP_FILE_BYTES) {
                                            "Tệp sao lưu vượt quá giới hạn 20 MB."
                                        }
                                        String(bytes, Charsets.UTF_8)
                                    }
                                    pendingRestoreJson = json
                                } catch (exception: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Không đọc được bản sao lưu: ${exception.localizedMessage ?: "Tệp không hợp lệ"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                    // Đọc JSON trên background thread để tránh chặn giao diện lúc khởi động.
                    LaunchedEffect(context) {
                        try {
                            characters = withContext(Dispatchers.IO) {
                                val hiragana = CharacterRepository.load(
                                    context,
                                    "characters/hiragana.json"
                                )
                                val katakana = CharacterRepository.load(
                                    context,
                                    "characters/katakana.json"
                                )
                                require(
                                    (hiragana + katakana).map { it.id }.distinct().size ==
                                        hiragana.size + katakana.size
                                ) {
                                    "Character IDs must be unique across datasets."
                                }
                                hiragana + katakana
                            }
                            loadError = null
                        } catch (exception: Exception) {
                            loadError = exception.localizedMessage
                                ?: "Không thể tải dữ liệu Hiragana và Katakana."
                        }
                    }

                    when {
                        loadError != null -> {
                            Text(
                                text = "Không tải được dữ liệu: $loadError",
                                modifier = Modifier.padding(innerPadding).padding(16.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        characters.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Đang tải dữ liệu Hiragana và Katakana...",
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Hai deck là hai tab chính; Cài đặt dùng chung nằm ở góc phải.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TabRow(
                                        selectedTabIndex = when (selectedDeckType) {
                                            "katakana" -> 1
                                            "kanji" -> 2
                                            else -> 0
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Tab(
                                            selected = selectedDeckType == "hiragana",
                                            onClick = {
                                                if (selectedDeckType != "hiragana") {
                                                    selectedDeckType = "hiragana"
                                                    appSettings.setSelectedCharacterType("hiragana")
                                                    selectedIndex = 0
                                                }
                                                selectedScreen = MainScreen.HOME
                                            },
                                            text = { Text("Hiragana") }
                                        )
                                        Tab(
                                            selected = selectedDeckType == "katakana",
                                            onClick = {
                                                if (selectedDeckType != "katakana") {
                                                    selectedDeckType = "katakana"
                                                    appSettings.setSelectedCharacterType("katakana")
                                                    selectedIndex = 0
                                                }
                                                selectedScreen = MainScreen.HOME
                                            },
                                            text = { Text("Katakana") }
                                        )
                                        Tab(
                                            selected = selectedDeckType == "kanji",
                                            onClick = {
                                                if (selectedDeckType != "kanji") {
                                                    selectedDeckType = "kanji"
                                                    appSettings.setSelectedCharacterType("kanji")
                                                    selectedIndex = 0
                                                }
                                                selectedScreen = MainScreen.HOME
                                            },
                                            text = { Text("Kanji") }
                                        )
                                    }
                                    IconButton(onClick = { selectedScreen = MainScreen.SETTINGS }) {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Cài đặt"
                                        )
                                    }
                                }

                                // Kanji là deck độc lập. Guided thủ công đã có; chức năng Learning/FSRS
                                // sẽ được mở khi Kanji có card và policy học tương ứng.
                                if (selectedDeckType != "kanji") {
                                    // Điều hướng chức năng cuộn ngang trên một hàng để nhường chiều cao
                                    // cho Canvas và các nút thao tác trong màn hình luyện viết.
                                    ScrollableTabRow(
                                        selectedTabIndex = MainScreen.entries
                                            .filter { it != MainScreen.SETTINGS }
                                            .indexOf(selectedScreen).coerceAtLeast(0),
                                        modifier = Modifier.fillMaxWidth(),
                                        edgePadding = 8.dp
                                    ) {
                                        MainScreen.entries.filter { it != MainScreen.SETTINGS }.forEach { screen ->
                                            Tab(
                                                selected = selectedScreen == screen,
                                                onClick = { selectedScreen = screen },
                                                text = { Text(screen.label, maxLines = 1, softWrap = false) }
                                            )
                                        }
                                    }
                                }

                                when (selectedScreen) {
                                    MainScreen.GUIDE -> LearningGuideScreen(
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.HOME -> {
                                        if (selectedDeckType == "kanji") {
                                            KanjiLibraryScreen(
                                                modifier = Modifier.weight(1f),
                                                onPracticeFinished = { characterId, mode, durationMs, finishedAt ->
                                                    practiceLogScope.launch {
                                                        practiceLogRepository.add(
                                                            PracticeLog(
                                                                cardId = characterId,
                                                                mode = mode,
                                                                rating = null,
                                                                durationMs = durationMs,
                                                                timestampEpochMillis = finishedAt
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        } else {
                                            HomeScreen(
                                                characters = selectedCharacters,
                                                deckName = characterTypeLabel(selectedDeckType),
                                                newCardsPerDay = selectedNewCardsPerDay,
                                                onStartLearning = { selectedScreen = MainScreen.LEARNING },
                                                onOpenLibrary = { selectedScreen = MainScreen.LIBRARY },
                                                onOpenFreePractice = { selectedScreen = MainScreen.FREE },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    MainScreen.LEARNING -> LearningSessionScreen(
                                        characters = selectedCharacters,
                                        newCardsPerDay = selectedNewCardsPerDay,
                                        showGrid = showGrid,
                                        showHints = showHints,
                                        deckType = selectedDeckType,
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.LIBRARY -> CharacterLibraryScreen(
                                        characters = selectedCharacters,
                                        deckName = characterTypeLabel(selectedDeckType),
                                        showGrid = showGrid,
                                        showHints = showHints,
                                        onPracticeFinished = { characterId, mode, durationMs, finishedAt ->
                                            practiceLogScope.launch {
                                                practiceLogRepository.add(
                                                    PracticeLog(
                                                        cardId = characterId,
                                                        mode = mode,
                                                        rating = null,
                                                        durationMs = durationMs,
                                                        timestampEpochMillis = finishedAt
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.HISTORY -> PracticeHistoryScreen(
                                        characters = selectedCharacters,
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.STATISTICS -> StatisticsScreen(
                                        characters = selectedCharacters,
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.SETTINGS -> SettingsScreen(
                                        onBackClick = { selectedScreen = MainScreen.HOME },
                                        themeMode = themeMode,
                                        onThemeModeChange = { mode ->
                                            themeMode = mode
                                            appSettings.setThemeMode(mode)
                                        },
                                        hiraganaNewCardsPerDay = hiraganaNewCardsPerDay,
                                        onHiraganaNewCardsPerDayChange = { value ->
                                            hiraganaNewCardsPerDay = value.coerceIn(0, 50)
                                            appSettings.setNewCardsPerDay("hiragana", hiraganaNewCardsPerDay)
                                        },
                                        katakanaNewCardsPerDay = katakanaNewCardsPerDay,
                                        onKatakanaNewCardsPerDayChange = { value ->
                                            katakanaNewCardsPerDay = value.coerceIn(0, 50)
                                            appSettings.setNewCardsPerDay("katakana", katakanaNewCardsPerDay)
                                        },
                                        showGrid = showGrid,
                                        onShowGridChange = { enabled ->
                                            showGrid = enabled
                                            appSettings.setShowGrid(enabled)
                                        },
                                        showHints = showHints,
                                        onShowHintsChange = { enabled ->
                                            showHints = enabled
                                            appSettings.setShowHints(enabled)
                                        },
                                        onBackupClick = {
                                            backupLauncher.launch("JapaneseWritingTrainer-backup.json")
                                        },
                                        onRestoreClick = {
                                            restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                                        },
                                        restoreConfirmationVisible = pendingRestoreJson != null,
                                        onConfirmRestore = {
                                            val json = pendingRestoreJson
                                            pendingRestoreJson = null
                                            if (json != null) {
                                                practiceLogScope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            backupRestoreManager.restoreFromJson(json)
                                                        }
                                                        Toast.makeText(
                                                            context,
                                                            "Khôi phục dữ liệu thành công.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } catch (exception: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "Khôi phục thất bại: ${exception.localizedMessage ?: "Tệp không hợp lệ"}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        onCancelRestore = { pendingRestoreJson = null },
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.FREE -> {
                                    val selectedCharacter = selectedCharacters.getOrNull(selectedIndex)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(
                                                horizontal = 16.dp,
                                                vertical = 8.dp
                                            ),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                                },
                                                enabled = selectedIndex > 0
                                            ) {
                                                Text("Trước")
                                            }

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = selectedCharacter?.character.orEmpty(),
                                                    style = MaterialTheme.typography.titleLarge
                                                )
                                                Text("${selectedIndex + 1} / ${selectedCharacters.size}")
                                            }

                                            Button(
                                                onClick = {
                                                    selectedIndex = (selectedIndex + 1)
                                                        .coerceAtMost(selectedCharacters.lastIndex)
                                                },
                                                enabled = selectedIndex < selectedCharacters.lastIndex
                                            ) {
                                                Text("Tiếp")
                                            }
                                        }

                                        selectedCharacter?.let { character ->
                                            Text(
                                                text = "Nhóm: ${character.group.uppercase()} · ${character.reading}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            WritingCanvas(
                                                modifier = Modifier.weight(1f),
                                                character = character,
                                                initialMode = WritingMode.GUIDED,
                                                showGrid = showGrid,
                                                showHints = showHints,
                                                onPracticeFinished = { mode, durationMs, finishedAt ->
                                                    practiceLogScope.launch {
                                                        practiceLogRepository.add(
                                                            PracticeLog(
                                                                cardId = character.id,
                                                                mode = mode,
                                                                rating = null,
                                                                durationMs = durationMs,
                                                                timestampEpochMillis = finishedAt
                                                            )
                                                        )
                                                    }
                                                }
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
    }
}

private const val MAX_BACKUP_FILE_BYTES = 20 * 1024 * 1024

private enum class MainScreen(val label: String) {
    HOME("Trang chủ"),
    GUIDE("Hướng dẫn"),
    FREE("Luyện tự do"),
    LIBRARY("Thư viện"),
    LEARNING("Ôn tập"),
    HISTORY("Lịch sử"),
    STATISTICS("Thống kê"),
    SETTINGS("Cài đặt")
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JapaneseWritingTrainerTheme {
        Greeting("Android")
    }
}
