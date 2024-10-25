package com.example.remembranceagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.remembranceagent.RemembranceAgentService


@Composable
fun MainScreen(
    initialApiKey: String = "",
    initialDocumentsPath: String = "",
    initialIndexPath: String = "",
    savePreference: (String, String) -> Unit = {k, v -> },
    indexDocuments: () -> Unit = {},
    startRemembranceAgent: () -> Unit = {},
    stopRemembranceAgent: () -> Unit = {},
    saveSettings: () -> Unit = {},
    modifier: Modifier = Modifier
    ) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var documentsPathString by remember { mutableStateOf(initialDocumentsPath) }
    var indexPathString by remember { mutableStateOf(initialIndexPath) }

    var remembranceAgentRunning by remember { mutableStateOf(RemembranceAgentService.isRunning) }

    // Register a listener to observe changes
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { newValue ->
            remembranceAgentRunning = newValue
        }
        RemembranceAgentService.addListener(listener)

        onDispose {
            // Clean up listeners if necessary (not implemented here)
            RemembranceAgentService.removeListener (listener)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Customize", style = MaterialTheme.typography.headlineLarge)
            TextField(value = apiKey, label = { Text("Speech API Key") }, onValueChange = { newValue ->
                    apiKey = newValue
            })
            TextField(value = documentsPathString, label = { Text("Documents Path") }, onValueChange = { newValue ->
                documentsPathString = newValue
            })
            TextField(value = indexPathString, label = { Text("Index Path") }, onValueChange = { newValue ->
                indexPathString = newValue
            })
            Button(onClick = {
                savePreference(GOOGLE_CLOUD_API_KEY, apiKey)
                savePreference(DOCUMENTS_PATH_STRING_KEY, documentsPathString)
                savePreference(INDEX_PATH_STRING_KEY, indexPathString)
                saveSettings()
            }) {
                Text(text = "Save settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = indexDocuments) {
                Text(text = "Index Documents")
            }
            if (remembranceAgentRunning) {
                Button(onClick = stopRemembranceAgent) {
                    Text(text = "Stop Remembrance Agent")
                }
            } else {
                Button(onClick = startRemembranceAgent) {
                    Text(text = "Start Remembrance Agent")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme() {
        MainScreen(

        )
    }
}
