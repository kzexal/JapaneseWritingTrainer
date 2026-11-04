package com.example.japanesewritingtrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.japanesewritingtrainer.ui.theme.JapaneseWritingTrainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JapaneseWritingTrainerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current.applicationContext
                    var characters by remember { mutableStateOf<List<CharacterData>>(emptyList()) }
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    var loadError by remember { mutableStateOf<String?>(null) }

                    // Đọc JSON trên background thread để tránh chặn giao diện lúc khởi động.
                    LaunchedEffect(context) {
                        try {
                            characters = withContext(Dispatchers.IO) {
                                CharacterRepository.load(context, "characters/hiragana.json")
                            }
                            loadError = null
                        } catch (exception: Exception) {
                            loadError = exception.localizedMessage
                                ?: "Không thể tải dữ liệu Hiragana."
                        }
                    }

                    val selectedCharacter = characters.getOrNull(selectedIndex)

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
                                Text("Đang tải dữ liệu Hiragana...", modifier = Modifier.padding(top = 12.dp))
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
                                        enabled = selectedIndex > 0
                                    ) {
                                        Text("Trước")
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${selectedCharacter?.character ?: ""}",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text("${selectedIndex + 1} / ${characters.size}")
                                    }

                                    Button(
                                        onClick = {
                                            selectedIndex = (selectedIndex + 1).coerceAtMost(characters.lastIndex)
                                        },
                                        enabled = selectedIndex < characters.lastIndex
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
                                        character = character
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
