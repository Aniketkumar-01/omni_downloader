package com.omni.downloader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                                .background(Brush.linearGradient(listOf(AccentTeal, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = BgDark,
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
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Check for Updates",
                                tint = AccentCyan
                            )
                        }
                    }

                    // Downloads Center Button (with active badge dot)
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { viewModel.setDownloadsSheetVisible(true) }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloads Center",
                                tint = if (activeDownload != null) AccentTeal else TextPrimary
                            )
                        }
                        if (activeDownload != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .clip(CircleShape)
                                    .background(AccentTeal)
                            )
                        }
                    }
                }
            )
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

            // Media Preview Card
            uiState.mediaMetadata?.let { meta ->
                item {
                    MediaPreviewCard(
                        meta = meta,
                        selectedQuality = uiState.selectedQuality,
                        selectedPlaylistCount = uiState.selectedPlaylistIds.size,
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
            downloadHistory = downloadHistory,
            onDismiss = { viewModel.setDownloadsSheetVisible(false) },
            onPauseDownload = { viewModel.pauseDownload(context) },
            onResumeDownload = { viewModel.resumeDownload(context) },
            onCancelDownload = { viewModel.cancelDownload(context) },
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
