package com.lyricsstatus.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CustomEndpointDialog(
    initialUrl: String,
    initialModel: String,
    initialAuthHeader: String,
    initialHeadersJson: String,
    onDismiss: () -> Unit,
    onSave: (url: String, model: String, authHeader: String, headersJson: String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var model by remember { mutableStateOf(initialModel) }
    var authHeader by remember { mutableStateOf(initialAuthHeader) }
    var headersJson by remember { mutableStateOf(initialHeadersJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Custom AI Endpoint Config", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Configure self-hosted Ollama, vLLM, LMStudio, LocalAI or custom LLM gateway.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base / Chat URL") },
                    placeholder = { Text("http://10.0.2.2:11434/v1/chat/completions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Name") },
                    placeholder = { Text("llama3.2, mistral, deepseek-r1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = authHeader,
                    onValueChange = { authHeader = it },
                    label = { Text("Auth Header Prefix") },
                    placeholder = { Text("Bearer ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = headersJson,
                    onValueChange = { headersJson = it },
                    label = { Text("Custom Headers (JSON)") },
                    placeholder = { Text("{\"HTTP-Referer\": \"...\"}") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(url, model, authHeader, headersJson)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
