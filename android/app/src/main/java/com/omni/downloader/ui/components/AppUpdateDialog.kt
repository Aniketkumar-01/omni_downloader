package com.omni.downloader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.downloader.engine.AppUpdateInfo
import com.omni.downloader.ui.theme.*
import java.io.File

/**
 * Reusable Dialog component for In-App APK update checking, release notes display, and installation.
 */
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    isDownloadingApk: Boolean,
    downloadApkProgress: Int,
    downloadApkStatus: String,
    downloadedApkFile: File?,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onInstallApk: () -> Unit,
    onUpdateCoreEngine: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                            .heightIn(max = 140.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(updateInfo.releaseNotes, fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }

                    if (isDownloadingApk) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = if (downloadApkProgress >= 0) downloadApkProgress / 100f else 0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentTeal,
                            trackColor = SurfaceCard
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            downloadApkStatus,
                            fontSize = 11.sp,
                            color = AccentCyan
                        )
                    } else if (downloadedApkFile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "✓ Update downloaded and ready to install.",
                            fontSize = 11.sp,
                            color = StatusSuccess
                        )
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
            if (updateInfo.isUpdateAvailable && updateInfo.apkDownloadUrl != null) {
                if (isDownloadingApk) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentTeal,
                        strokeWidth = 2.dp
                    )
                } else if (downloadedApkFile != null) {
                    Button(
                        onClick = onInstallApk,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = BgDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install APK Now", color = BgDark, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = BgDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download & Install", color = BgDark, fontWeight = FontWeight.Bold)
                    }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (updateInfo.isUpdateAvailable && updateInfo.apkDownloadUrl != null) {
                    TextButton(onClick = onOpenBrowser) {
                        Text("Browser", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                TextButton(onClick = {
                    if (!isDownloadingApk) {
                        onDismiss()
                    }
                }) {
                    Text("Close", color = TextSecondary)
                }
            }
        }
    )
}
