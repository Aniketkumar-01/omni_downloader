package com.omni.downloader.ui.viewmodel

import com.omni.downloader.data.models.MediaMetadata
import com.omni.downloader.engine.AppUpdateInfo
import java.io.File

/**
 * Immutable UI State representing the complete presentation layer of the OmniDownloader Android app.
 */
data class MainUiState(
    val urlInput: String = "",
    val isFetching: Boolean = false,
    val mediaMetadata: MediaMetadata? = null,
    val selectedQuality: String = "best",
    val selectedPlaylistIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val showDownloadsSheet: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingApk: Boolean = false,
    val downloadApkProgress: Int = 0,
    val downloadApkStatus: String = "",
    val downloadedApkFile: File? = null
)
