package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun PasteUrlDialog(
    onDismiss: () -> Unit,
    onPlayUrl: (url: String, title: String) -> Unit,
    onSaveUrl: (url: String, title: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Stream from Video URL",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste an MP4, MKV, WebM, HLS (.m3u8), DASH (.mpd), or local Hotspot stream URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Video URL") },
                    placeholder = { Text("https://example.com/video.mp4") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clip = clipboard.getText()?.text?.trim()
                                if (!clip.isNullOrEmpty()) {
                                    urlInput = clip
                                }
                            },
                            modifier = Modifier.testTag("clipboard_paste_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Paste from clipboard"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_dialog_input")
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Title (optional)") },
                    placeholder = { Text("My Stream Video") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_dialog_title_input")
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onSaveUrl(urlInput.trim(), titleInput.trim())
                            onDismiss()
                        }
                    },
                    enabled = urlInput.isNotBlank(),
                    modifier = Modifier.testTag("url_dialog_save_button")
                ) {
                    Text("Save")
                }
                Button(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onPlayUrl(urlInput.trim(), titleInput.trim())
                            onDismiss()
                        }
                    },
                    enabled = urlInput.isNotBlank(),
                    modifier = Modifier.testTag("url_dialog_play_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
