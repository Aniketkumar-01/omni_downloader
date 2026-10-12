package com.omni.downloader.ui.components

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.downloader.engine.AppUpdateInfo
import com.omni.downloader.ui.theme.*

/**
 * Dialog displaying app update availability, uninstallation notice,
 * changelog release notes, and external browser download links.
 */
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenDownloadLink: (String) -> Unit,
    onUpdateCoreEngine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val targetDownloadUrl = updateInfo.apkDownloadUrl ?: updateInfo.releasePageUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (updateInfo.isUpdateAvailable) Icons.Default.SystemUpdate else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (updateInfo.isUpdateAvailable) AccentTeal else StatusSuccess
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (updateInfo.isUpdateAvailable) "Update Available" else "OmniDownloader is Up to Date",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column {
                Text(
                    "Installed: v${updateInfo.currentVersion} • Latest: v${updateInfo.latestVersion}",
                    fontSize = 12.sp,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (updateInfo.isUpdateAvailable) {
                    // Important guidance notice on uninstalling prior to update
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF281F12)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE5A100).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Important Installation Step",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB74D)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    "Please uninstall your current OmniDownloader app first before installing the new version to avoid package signature conflict errors.",
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "What's New:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(updateInfo.releaseNotes, fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Download Link presentation with copy button
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Download Link:",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    targetDownloadUrl,
                                    fontSize = 11.sp,
                                    color = AccentCyan,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(targetDownloadUrl))
                                    Toast.makeText(context, "Download link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Link",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "You are running the newest release of OmniDownloader. You can also update the embedded yt-dlp core engine for latest platform fixes.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            if (updateInfo.isUpdateAvailable) {
                Button(
                    onClick = { onOpenDownloadLink(targetDownloadUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = BgDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download Update", color = BgDark, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onUpdateCoreEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Update yt-dlp Core", color = BgDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

