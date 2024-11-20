package com.example.remembranceagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.remembranceagent.RemembranceAgentService
import com.example.remembranceagent.RetrieverService
import com.example.remembranceagent.retrieval.Indexer


@Composable
fun MainScreen(
    initialApiKey: String = "",
    initialDocumentsPath: String = "",
    getIndexPath: () -> String = {""},
    savePreference: (String, String) -> Unit = {k, v -> },
    indexDocuments: () -> Unit = {},
    startRemembranceAgent: () -> Unit = {},
    stopRemembranceAgent: () -> Unit = {},
    startRetriever: () -> Unit = {},
    stopRetriever: () -> Unit = {},
    saveSettings: () -> Unit = {},
    modifier: Modifier = Modifier
    ) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var documentsPathString by remember { mutableStateOf(initialDocumentsPath) }
    var indexPathString by remember { mutableStateOf(getIndexPath()) }

    var remembranceAgentRunning by remember { mutableStateOf(RemembranceAgentService.isRunning) }
    var retrieverRunning by remember { mutableStateOf(RetrieverService.isRunning) }

    // Register a listener to observe changes
    DisposableEffect(Unit) {
        val remembranceAgentListener: (Boolean) -> Unit = { newValue ->
            remembranceAgentRunning = newValue
        }
        val retrieverListener: (Boolean) -> Unit = { newValue ->
            retrieverRunning = newValue
        }

        val indexPathListener: () -> Unit = {
            indexPathString = getIndexPath()
        }

        RemembranceAgentService.addListener(remembranceAgentListener)
        RetrieverService.addListener(retrieverListener)
        Indexer.addListener(indexPathListener)

        onDispose {
            // Clean up listeners if necessary (not implemented here)
            RemembranceAgentService.removeListener (remembranceAgentListener)
            RetrieverService.removeListener (retrieverListener)
            Indexer.removeListener(indexPathListener)
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

            if (retrieverRunning) {
                Button(onClick = stopRetriever) {
                    Text(text = "Stop Retriever")
                }
            } else {
                Button(onClick = startRetriever) {
                    Text(text = "Start Retriever")
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
