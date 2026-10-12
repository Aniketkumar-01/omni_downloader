package com.omni.downloader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.downloader.data.models.DownloadTaskProgress
import com.omni.downloader.data.models.TaskStatus
import com.omni.downloader.data.models.isAudioMedia
import com.omni.downloader.ui.theme.*

/**
 * Reusable Bottom Sheet component for the Downloads Center:
 * Real-time active progress gauge and clickable completed history with system chooser media launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    activeDownload: DownloadTaskProgress?,
    downloadHistory: List<DownloadTaskProgress>,
    onDismiss: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenMedia: (DownloadTaskProgress) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = AccentTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Downloads Center",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Download Card
            activeDownload?.let { task ->
                val isPaused = task.status == TaskStatus.PAUSED
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                task.currentTitle.ifBlank { "Active Download" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isPaused) AccentCyan.copy(alpha = 0.2f) else AccentTeal.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    if (isPaused) "PAUSED" else "DOWNLOADING",
                                    color = if (isPaused) AccentCyan else AccentTeal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = (task.progressPercent / 100f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isPaused) AccentCyan else AccentTeal,
                            trackColor = BorderSubtle
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val itemProgress = if (task.totalItems > 1) "Item ${task.completedItems} of ${task.totalItems} • " else ""
                            Text(
                                "$itemProgress${task.progressPercent.toInt()}% • Speed: ${task.speedFormatted}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                if (task.etaFormatted.isNotBlank()) "ETA: ${task.etaFormatted}" else "",
                                fontSize = 11.sp,
                                color = AccentCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isPaused) {
                                Button(
                                    onClick = onResumeDownload,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = BgDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BgDark)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onPauseDownload,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onCancelDownload,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                border = BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Recent Downloads Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Downloads (${downloadHistory.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                if (downloadHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear All", fontSize = 11.sp, color = AccentCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeDownload == null && downloadHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active or recent downloads", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloadHistory, key = { it.taskId }) { historyItem ->
                        val isCompleted = historyItem.status == TaskStatus.COMPLETED
                        val isAudio = historyItem.isAudioMedia

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isCompleted) {
                                        Modifier.clickable { onOpenMedia(historyItem) }
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        !isCompleted -> StatusError.copy(alpha = 0.15f)
                                        isAudio -> AccentTeal.copy(alpha = 0.15f)
                                        else -> AccentCyan.copy(alpha = 0.15f)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when {
                                                !isCompleted -> Icons.Default.Warning
                                                isAudio -> Icons.Default.Audiotrack
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = if (isAudio) "Audio" else "Video",
                                            tint = when {
                                                !isCompleted -> StatusError
                                                isAudio -> AccentTeal
                                                else -> AccentCyan
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        historyItem.currentTitle.ifBlank { "Media Item" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCompleted) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isAudio) AccentTeal.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    if (isAudio) "AUDIO" else "VIDEO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAudio) AccentTeal else AccentCyan,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (isAudio) "Music • Tap to play" else "Movies • Tap to watch",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                historyItem.errorMessage ?: "Failed",
                                                fontSize = 11.sp,
                                                color = StatusError,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                if (isCompleted) {
                                    Button(
                                        onClick = { onOpenMedia(historyItem) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAudio) AccentTeal else AccentCyan
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAudio) Icons.Default.PlayArrow else Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            tint = BgDark,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isAudio) "Play" else "Open",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BgDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                }

                                IconButton(
                                    onClick = { onRemoveHistoryItem(historyItem.taskId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = TextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
