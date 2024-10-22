package com.example.remembranceagent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Composable
fun MainScreen(
    initialApiKey: String = "",
    initialDocumentsPath: String = "",
    initialIndexPath: String = "",
    savePreference: (String, String) -> Unit = {k, v -> },
    indexDocuments: () -> Unit = {},
    modifier: Modifier = Modifier
    ) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var documentsPathString by remember { mutableStateOf(initialDocumentsPath) }
    var indexPathString by remember { mutableStateOf(initialIndexPath) }

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
            TextField(value = apiKey, onValueChange = {savePreference(GOOGLE_CLOUD_API_KEY, apiKey)}, label = { Text("Speech API Key") })
            TextField(value = documentsPathString, onValueChange = { savePreference(DOCUMENTS_PATH_STRING, documentsPathString)}, label = { Text("Documents Path") })
            TextField(value = indexPathString, onValueChange = { savePreference(INDEX_PATH_STRING, indexPathString)}, label = { Text("Index Path") })
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = indexDocuments) {
                Text(text = "Index Documents")
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
