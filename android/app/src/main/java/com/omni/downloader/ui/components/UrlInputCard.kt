package com.omni.downloader.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.downloader.ui.theme.*

/**
 * Reusable Card component for media URL input, clipboard pasting, and fetch submission.
 */
@Composable
fun UrlInputCard(
    urlInput: String,
    isFetching: Boolean,
    onUrlChange: (String) -> Unit,
    onClearUrl: () -> Unit,
    onPasteUrl: (String) -> Unit,
    onFetchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Paste Media URL",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                placeholder = {
                    Text(
                        "Paste video, playlist, or batch links (one per line)",
                        color = TextSecondary.copy(alpha = 0.5f)
                    )
                },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Uri
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (urlInput.isNotBlank() && !isFetching) {
                            onFetchClick()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCobalt,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceInput,
                    unfocusedContainerColor = SurfaceInput
                ),
                trailingIcon = {
                    if (urlInput.isNotBlank()) {
                        IconButton(onClick = onClearUrl) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        if (clip.hasPrimaryClip() && clip.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                            val text = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotBlank()) {
                                onPasteUrl(text.trim())
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceCard,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste", color = TextPrimary, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        if (urlInput.isBlank()) {
                            Toast.makeText(context, "Please paste a URL first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onFetchClick()
                    },
                    enabled = !isFetching,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCobalt,
                        disabledContainerColor = SurfaceCard
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(44.dp)
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
