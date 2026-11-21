package com.example.japanesewritingtrainer

import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.japanesewritingtrainer.ui.theme.JapaneseWritingTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PracticeStreakWidgetProvider.requestUpdate(applicationContext)
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
            var kanjiNewCardsPerDay by remember {
                mutableIntStateOf(appSettings.getNewCardsPerDay("kanji"))
            }
            var showGrid by remember { mutableStateOf(appSettings.getShowGrid()) }
            var showHints by remember { mutableStateOf(appSettings.getShowHints()) }
            var reminderEnabled by remember { mutableStateOf(appSettings.getReminderEnabled()) }
            var reminderHour by remember { mutableIntStateOf(appSettings.getReminderHour()) }
            var reminderMinute by remember { mutableIntStateOf(appSettings.getReminderMinute()) }
            val reminderPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    reminderEnabled = true
                    appSettings.setReminderEnabled(true)
                    ReminderScheduler.schedule(context, reminderHour, reminderMinute)
                } else {
                    Toast.makeText(context, "Bạn chưa cấp quyền thông báo nên nhắc học chưa được bật.", Toast.LENGTH_LONG).show()
                }
            }
            LaunchedEffect(reminderEnabled, reminderHour, reminderMinute) {
                if (reminderEnabled) {
                    ReminderScheduler.schedule(context, reminderHour, reminderMinute)
                } else {
                    ReminderScheduler.cancel(context)
                }
            }
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
                    var kanjiCharacters by remember { mutableStateOf<List<CharacterData>>(emptyList()) }
                    var kanjiMetadata by remember { mutableStateOf<List<KanjiData>>(emptyList()) }
                    var kanjiMetadataLoaded by remember { mutableStateOf(false) }
                    var kanjiLoaded by remember { mutableStateOf(false) }
                    var kanjiLoading by remember { mutableStateOf(false) }
                    var kanjiLoadError by remember { mutableStateOf<String?>(null) }
                    var kanjiLoadAttempt by remember { mutableIntStateOf(0) }
                    var kanjiWritingLoading by remember { mutableStateOf(false) }
                    var kanjiWritingError by remember { mutableStateOf<String?>(null) }
                    var kanjiWritingAttempt by remember { mutableIntStateOf(0) }
                    var selectedDeckType by remember {
                        mutableStateOf(appSettings.getSelectedCharacterType())
                    }
                    val selectedCharacters = remember(characters, kanjiCharacters, kanjiMetadata, selectedDeckType) {
                        if (selectedDeckType == "kanji") {
                            if (kanjiLoaded) kanjiCharacters else kanjiMetadata.map { item ->
                                CharacterData(
                                    id = item.id,
                                    character = item.character,
                                    type = "kanji",
                                    group = item.jlpt ?: "unclassified",
                                    reading = listOfNotNull(item.onyomi, item.kunyomi)
                                        .joinToString(" · ").ifBlank { "Chưa có âm đọc" },
                                    strokeCount = item.strokeCount,
                                    viewBoxSize = 109f,
                                    strokes = emptyList()
                                )
                            }
                        } else characters.filter { it.type.equals(selectedDeckType, ignoreCase = true) }
                    }
                    val selectedNewCardsPerDay = when (selectedDeckType) {
                        "katakana" -> katakanaNewCardsPerDay
                        "kanji" -> kanjiNewCardsPerDay
                        else -> hiraganaNewCardsPerDay
                    }
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    var loadError by remember { mutableStateOf<String?>(null) }
                    var selectedScreen by remember { mutableStateOf(MainScreen.HOME) }
                    var showActiveReviewSession by remember { mutableStateOf(false) }
                    var isLibraryDetail by remember { mutableStateOf(false) }
                    var navigationHistory by remember { mutableStateOf(listOf<MainScreen>()) }

                    fun navigateTo(screen: MainScreen) {
                        if (screen != selectedScreen) {
                            navigationHistory = navigationHistory + selectedScreen
                            selectedScreen = screen
                            showActiveReviewSession = false
                            isLibraryDetail = false
                        }
                    }

                    BackHandler(enabled = navigationHistory.isNotEmpty()) {
                        selectedScreen = navigationHistory.last()
                        navigationHistory = navigationHistory.dropLast(1)
                        showActiveReviewSession = false
                        isLibraryDetail = false
                    }
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

                    // Kana datasets are small and are loaded first. Kanji is loaded only
                    // when the user selects that deck, so its large SVG dataset does not
                    // delay the initial screen or the Kana decks.
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
                                val allKana = hiragana + katakana
                                require(allKana.map { it.id }.distinct().size == allKana.size) {
                                    "Character IDs must be unique across Kana datasets."
                                }
                                allKana
                            }
                            loadError = null
                        } catch (exception: Exception) {
                            loadError = exception.localizedMessage
                                ?: "Không thể tải dữ liệu Hiragana và Katakana."
                        }
                    }

                    // The library needs metadata only; do not parse the large stroke asset here.
                    LaunchedEffect(context, selectedDeckType, kanjiLoadAttempt) {
                        if (selectedDeckType != "kanji" || kanjiMetadataLoaded) return@LaunchedEffect
                        kanjiLoading = true
                        kanjiLoadError = null
                        try {
                            kanjiMetadata = withContext(Dispatchers.IO) {
                                KanjiStrokeRepository.loadMetadata(context)
                            }
                            kanjiMetadataLoaded = true
                        } catch (exception: Exception) {
                            kanjiLoadError = exception.localizedMessage
                                ?: "Không thể tải dữ liệu Kanji."
                        } finally {
                            kanjiLoading = false
                        }
                    }

                    // Full geometry is needed for a learning session, not for browsing the library.
                    LaunchedEffect(context, selectedDeckType, selectedScreen, kanjiWritingAttempt) {
                        if (selectedDeckType != "kanji" ||
                            selectedScreen !in setOf(MainScreen.LEARNING, MainScreen.FREE) ||
                            kanjiLoaded
                        ) return@LaunchedEffect
                        kanjiWritingLoading = true
                        kanjiWritingError = null
                        try {
                            kanjiCharacters = withContext(Dispatchers.IO) {
                                KanjiStrokeRepository.loadWritingCharacters(context, kanjiMetadata)
                            }
                            kanjiLoaded = true
                        } catch (exception: Exception) {
                            kanjiWritingError = exception.localizedMessage
                                ?: "Không thể tải dữ liệu nét viết Kanji."
                        } finally {
                            kanjiWritingLoading = false
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

                        selectedDeckType == "kanji" && !kanjiMetadataLoaded -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (kanjiLoading) {
                                    CircularProgressIndicator()
                                    Text(
                                        "Đang tải danh sách Kanji...",
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                } else {
                                    Text(
                                        kanjiLoadError ?: "Chưa thể tải dữ liệu Kanji.",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    OutlinedButton(onClick = { kanjiLoadAttempt++ }) {
                                        Text("Thử tải lại")
                                    }
                                }
                            }
                        }

                        selectedDeckType == "kanji" &&
                            selectedScreen in setOf(MainScreen.LEARNING, MainScreen.FREE) &&
                            !kanjiLoaded -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (kanjiWritingLoading) {
                                    CircularProgressIndicator()
                                    Text("Đang tải dữ liệu luyện viết Kanji...", modifier = Modifier.padding(top = 12.dp))
                                } else {
                                    Text(
                                        kanjiWritingError ?: "Chưa thể tải dữ liệu nét viết Kanji.",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    OutlinedButton(onClick = { kanjiWritingAttempt++ }) {
                                        Text("Thử tải lại")
                                    }
                                }
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top bar gọn, bộ chọn bộ chữ tách biệt khỏi điều hướng chính.
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (selectedScreen) {
                                            MainScreen.HOME -> "Học"
                                            MainScreen.LEARNING -> "Ôn tập"
                                            MainScreen.LIBRARY, MainScreen.VOCABULARY -> "Thư viện"
                                            MainScreen.STATISTICS, MainScreen.HISTORY -> "Thống kê"
                                            MainScreen.SETTINGS -> "Cài đặt"
                                            else -> "Luyện viết"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { navigateTo(MainScreen.SETTINGS) }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
                                    }
                                }

                                if (!isLibraryDetail && (selectedScreen == MainScreen.HOME ||
                                    selectedScreen == MainScreen.LEARNING ||
                                    selectedScreen == MainScreen.LIBRARY ||
                                    selectedScreen == MainScreen.VOCABULARY)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("hiragana", "katakana", "kanji").forEach { type ->
                                            val selected = selectedDeckType == type
                                            androidx.compose.material3.FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    selectedDeckType = type
                                                    appSettings.setSelectedCharacterType(type)
                                                    selectedIndex = 0
                                                    if (selectedScreen == MainScreen.LEARNING) {
                                                        // Keep the user on the Review tab and refresh its deck overview.
                                                        showActiveReviewSession = false
                                                    }
                                                },
                                                label = { Text(characterTypeLabel(type)) }
                                            )
                                        }
                                    }
                                }

                                if (!isLibraryDetail && (selectedScreen == MainScreen.LIBRARY || selectedScreen == MainScreen.VOCABULARY)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        androidx.compose.material3.FilterChip(
                                            selected = selectedScreen == MainScreen.LIBRARY,
                                            onClick = { navigateTo(MainScreen.LIBRARY) },
                                            label = { Text("Chữ cái") }
                                        )
                                        androidx.compose.material3.FilterChip(
                                            selected = selectedScreen == MainScreen.VOCABULARY,
                                            onClick = { navigateTo(MainScreen.VOCABULARY) },
                                            label = { Text("Từ vựng") }
                                        )
                                    }
                                }
                                if (selectedScreen == MainScreen.STATISTICS || selectedScreen == MainScreen.HISTORY) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        androidx.compose.material3.FilterChip(
                                            selected = selectedScreen == MainScreen.STATISTICS,
                                            onClick = { navigateTo(MainScreen.STATISTICS) },
                                            label = { Text("Tổng quan") }
                                        )
                                        androidx.compose.material3.FilterChip(
                                            selected = selectedScreen == MainScreen.HISTORY,
                                            onClick = { navigateTo(MainScreen.HISTORY) },
                                            label = { Text("Lịch sử") }
                                        )
                                    }
                                }

                                // Nội dung từng màn hình giữ nguyên các ViewModel/repository hiện có.
                                when (selectedScreen) {
                                    MainScreen.GUIDE -> LearningGuideScreen(
                                        modifier = Modifier.weight(1f)
                                    )

                                    MainScreen.HOME -> {
                                        HomeScreen(
                                                characters = selectedCharacters,
                                                deckName = characterTypeLabel(selectedDeckType),
                                                newCardsPerDay = selectedNewCardsPerDay,
                                                onStartLearning = { navigateTo(MainScreen.LEARNING) },
                                                onOpenLibrary = { navigateTo(MainScreen.LIBRARY) },
                                                onOpenFreePractice = { characterId ->
                                                    selectedIndex = selectedCharacters.indexOfFirst { it.id == characterId }.coerceAtLeast(0)
                                                    navigateTo(MainScreen.FREE)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                    }

                                    MainScreen.LEARNING -> {
                                        if (showActiveReviewSession) {
                                            LearningSessionScreen(
                                                characters = selectedCharacters,
                                                newCardsPerDay = selectedNewCardsPerDay,
                                                showGrid = showGrid,
                                                showHints = showHints,
                                                deckType = selectedDeckType,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            ReviewOverviewScreen(
                                                characters = selectedCharacters,
                                                deckType = selectedDeckType,
                                                newCardsPerDay = selectedNewCardsPerDay,
                                                onStartReview = { showActiveReviewSession = true },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    MainScreen.LIBRARY -> if (selectedDeckType == "kanji") {
                                        KanjiLibraryScreen(
                                            records = kanjiMetadata,
                                            writingCharacters = kanjiCharacters,
                                            modifier = Modifier.weight(1f),
                                            onDetailStateChanged = { isLibraryDetail = it },
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
                                    } else CharacterLibraryScreen(
                                        characters = selectedCharacters,
                                        deckName = characterTypeLabel(selectedDeckType),
                                        showGrid = showGrid,
                                        showHints = showHints,
                                        onDetailStateChanged = { isLibraryDetail = it },
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

                                    MainScreen.VOCABULARY -> VocabularyLibraryScreen(
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
                                        onBackClick = { navigateTo(MainScreen.HOME) },
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
                                        kanjiNewCardsPerDay = kanjiNewCardsPerDay,
                                        onKanjiNewCardsPerDayChange = { value ->
                                            kanjiNewCardsPerDay = value.coerceIn(0, 50)
                                            appSettings.setNewCardsPerDay("kanji", kanjiNewCardsPerDay)
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
                                        reminderEnabled = reminderEnabled,
                                        reminderHour = reminderHour,
                                        reminderMinute = reminderMinute,
                                        onReminderEnabledChange = { enabled ->
                                            if (!enabled) {
                                                reminderEnabled = false
                                                appSettings.setReminderEnabled(false)
                                                ReminderScheduler.cancel(context)
                                            } else if (
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                reminderPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                reminderEnabled = true
                                                appSettings.setReminderEnabled(true)
                                                ReminderScheduler.schedule(context, reminderHour, reminderMinute)
                                            }
                                        },
                                        onReminderTimeChange = { hour, minute ->
                                            reminderHour = hour
                                            reminderMinute = minute
                                            appSettings.setReminderTime(hour, minute)
                                            if (reminderEnabled) ReminderScheduler.schedule(context, hour, minute)
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
                                                        PracticeStreakWidgetProvider.requestUpdate(context)
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

                                if (!isLibraryDetail && selectedScreen in setOf(MainScreen.HOME, MainScreen.LEARNING, MainScreen.LIBRARY, MainScreen.VOCABULARY, MainScreen.STATISTICS, MainScreen.HISTORY)) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(64.dp),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BottomNavIcon(Icons.Filled.Home, "Học", selectedScreen == MainScreen.HOME) { navigateTo(MainScreen.HOME) }
                                            BottomNavIcon(Icons.Filled.Edit, "Ôn tập", selectedScreen == MainScreen.LEARNING) { navigateTo(MainScreen.LEARNING) }
                                            BottomNavIcon(Icons.Filled.MenuBook, "Thư viện", selectedScreen == MainScreen.LIBRARY || selectedScreen == MainScreen.VOCABULARY) { navigateTo(MainScreen.LIBRARY) }
                                            BottomNavIcon(Icons.Filled.BarChart, "Thống kê", selectedScreen == MainScreen.STATISTICS || selectedScreen == MainScreen.HISTORY) { navigateTo(MainScreen.STATISTICS) }
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

@Composable
private fun BottomNavIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.semantics {
                    stateDescription = if (selected) "Đang chọn" else "Chưa chọn"
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val MAX_BACKUP_FILE_BYTES = 20 * 1024 * 1024

private enum class MainScreen(val label: String) {
    HOME("Trang chủ"),
    GUIDE("Hướng dẫn"),
    FREE("Luyện Guided"),
    LIBRARY("Thư viện"),
    VOCABULARY("Từ vựng"),
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
