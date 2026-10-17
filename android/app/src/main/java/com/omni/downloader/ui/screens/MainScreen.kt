package com.omni.downloader.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omni.downloader.data.models.TaskStatus
import com.omni.downloader.engine.UpdateManager
import com.omni.downloader.ui.components.*
import com.omni.downloader.ui.theme.*
import com.omni.downloader.ui.viewmodel.MainViewModel

/**
 * Main application screen for OmniDownloader Android.
 * Orchestrates modular composable components with reactive ViewModel state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activeDownload by viewModel.activeDownload.collectAsState()
    val queuedTasks by viewModel.queuedTasks.collectAsState()
    val downloadHistory by viewModel.downloadHistory.collectAsState()

    // Handle shared link sent from Android system share sheet
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.fetchMedia(context, sharedUrl) {
                onSharedUrlConsumed()
            }
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = TextPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentCobalt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "OmniDownloader",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Universal Media Downloader",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Check Updates Button
                    IconButton(onClick = {
                        viewModel.checkForUpdates { errorMsg ->
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        if (uiState.isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCobalt,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Check for Updates",
                                tint = TextSecondary
                            )
                        }
                    }

                    // Downloads Center Button (with active & queued badge)
                    val hasActive = activeDownload != null && (activeDownload?.status == TaskStatus.DOWNLOADING || activeDownload?.status == TaskStatus.PAUSED)
                    val hasPendingDownloads = hasActive || queuedTasks.isNotEmpty()
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { viewModel.setDownloadsSheetVisible(true) }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloads Center",
                                tint = if (hasPendingDownloads) AccentCobalt else TextPrimary
                            )
                        }
                        if (hasPendingDownloads) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .clip(CircleShape)
                                    .background(AccentCobalt)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            activeDownload?.let { task ->
                if (task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.PAUSED) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDownloadsSheetVisible(true) },
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentCobaltContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (task.status == TaskStatus.DOWNLOADING) Icons.Default.Download else Icons.Default.Pause,
                                            contentDescription = null,
                                            tint = AccentCobaltLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = task.currentTitle.ifBlank { "Downloading media..." },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${task.progressPercent.toInt()}% • ${task.speedFormatted.ifBlank { "Calculating..." }} • Tap to view",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (task.status == TaskStatus.DOWNLOADING) {
                                            viewModel.pauseDownload(context)
                                        } else {
                                            viewModel.resumeDownload(context)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (task.status == TaskStatus.DOWNLOADING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (task.status == TaskStatus.DOWNLOADING) "Pause" else "Resume",
                                        tint = AccentCobaltLight
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = (task.progressPercent / 100f).coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AccentCobalt,
                                trackColor = BorderSubtle
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // URL Ingestion Card
            item {
                UrlInputCard(
                    urlInput = uiState.urlInput,
                    isFetching = uiState.isFetching,
                    onUrlChange = { viewModel.onUrlChanged(it) },
                    onClearUrl = { viewModel.onClearUrl() },
                    onPasteUrl = { viewModel.onPasteUrl(it) },
                    onFetchClick = { viewModel.fetchMedia(context) }
                )
            }

            // Error Notice Banner
            uiState.errorMessage?.let { errorMsg ->
                item {
                    ErrorBanner(
                        errorMessage = errorMsg,
                        onDismiss = { viewModel.dismissError() },
                        onUpdateEngine = {
                            Toast.makeText(context, "Updating yt-dlp core engine...", Toast.LENGTH_SHORT).show()
                            viewModel.updateEngineCore(context) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            // Empty State: Supported Sources & Live Detection Hub
            if (uiState.mediaMetadata == null && uiState.errorMessage == null) {
                item {
                    SupportedPlatformsCard(
                        currentUrl = uiState.urlInput,
                        onPlatformClick = { platform ->
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = if (clip.hasPrimaryClip() && clip.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                                clip.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                            } else ""

                            if (clipText.isNotBlank() && platform.domains.any { clipText.lowercase().contains(it) }) {
                                viewModel.onPasteUrl(clipText)
                                Toast.makeText(context, "Pasted ${platform.name} link from clipboard", Toast.LENGTH_SHORT).show()
                            } else if (platform.domains.isNotEmpty()) {
                                Toast.makeText(context, "Paste any ${platform.name} link to start downloading", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Media Preview Card
            uiState.mediaMetadata?.let { meta ->
                item {
                    val isCurrentlyDownloading = activeDownload != null && (activeDownload?.status == TaskStatus.DOWNLOADING || activeDownload?.status == TaskStatus.PAUSED)
                    MediaPreviewCard(
                        meta = meta,
                        selectedQuality = uiState.selectedQuality,
                        selectedPlaylistCount = uiState.selectedPlaylistIds.size,
                        hasActiveDownload = isCurrentlyDownloading,
                        onQualitySelect = { viewModel.onQualitySelected(it) },
                        onDownloadClick = {
                            viewModel.startDownload(context) { toastMsg ->
                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Interactive Playlist Items Section
                if (meta.isPlaylist && meta.playlistItems.isNotEmpty()) {
                    item {
                        PlaylistVideosCard(
                            items = meta.playlistItems,
                            selectedIds = uiState.selectedPlaylistIds,
                            onToggleItem = { viewModel.onTogglePlaylistItem(it) },
                            onSelectAll = { viewModel.onSelectAllPlaylistItems() },
                            onDeselectAll = { viewModel.onDeselectAllPlaylistItems() }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Downloads Center Bottom Sheet
    if (uiState.showDownloadsSheet) {
        DownloadsSheet(
            activeDownload = activeDownload,
            queuedTasks = queuedTasks,
            downloadHistory = downloadHistory,
            onDismiss = { viewModel.setDownloadsSheetVisible(false) },
            onPauseDownload = { viewModel.pauseDownload(context) },
            onResumeDownload = { viewModel.resumeDownload(context) },
            onCancelDownload = { viewModel.cancelDownload(context) },
            onRemoveQueued = { viewModel.removeQueuedTask(it) },
            onClearQueue = { viewModel.clearQueue() },
            onOpenMedia = { viewModel.openMedia(context, it) },
            onRemoveHistoryItem = { viewModel.removeHistoryItem(it) },
            onClearHistory = { viewModel.clearHistory() }
        )
    }

    // App Update Dialog
    if (uiState.showUpdateDialog && uiState.updateInfo != null) {
        AppUpdateDialog(
            updateInfo = uiState.updateInfo!!,
            onDismiss = { viewModel.setUpdateDialogVisible(false) },
            onOpenDownloadLink = { url ->
                UpdateManager.openDownloadUrl(context, url)
            },
            onUpdateCoreEngine = {
                Toast.makeText(context, "Updating yt-dlp core...", Toast.LENGTH_SHORT).show()
                viewModel.updateEngineCore(context) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                viewModel.setUpdateDialogVisible(false)
            }
        )
    }
}
