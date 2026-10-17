package com.omni.downloader.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omni.downloader.OmniApplication
import com.omni.downloader.data.DownloadTracker
import com.omni.downloader.data.models.DownloadTaskProgress
import com.omni.downloader.data.models.QueuedDownloadTask
import com.omni.downloader.data.models.TaskStatus
import com.omni.downloader.data.repository.DefaultMediaRepository
import com.omni.downloader.data.repository.MediaRepository
import com.omni.downloader.engine.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main ViewModel for OmniDownloader Android.
 * Orchestrates business logic, async repository calls, and state emissions.
 */
class MainViewModel(
    private val repository: MediaRepository = DefaultMediaRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val activeDownload: StateFlow<DownloadTaskProgress?> = DownloadTracker.activeTask
    val queuedTasks: StateFlow<List<QueuedDownloadTask>> = DownloadTracker.queue
    val downloadHistory: StateFlow<List<DownloadTaskProgress>> = DownloadTracker.history

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(urlInput = url) }
    }

    fun onClearUrl() {
        _uiState.update { it.copy(urlInput = "") }
    }

    fun onPasteUrl(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            _uiState.update { it.copy(urlInput = trimmed) }
        }
    }

    fun fetchMedia(
        context: Context,
        urlOverride: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val targetUrl = (urlOverride ?: _uiState.value.urlInput).trim()
        if (targetUrl.isBlank()) return

        _uiState.update { 
            it.copy(
                isFetching = true, 
                errorMessage = null,
                urlInput = targetUrl
            ) 
        }

        viewModelScope.launch {
            val result = repository.fetchMetadata(context, targetUrl)
            result.onSuccess { metadata ->
                _uiState.update {
                    it.copy(
                        isFetching = false,
                        mediaMetadata = metadata,
                        selectedQuality = "best",
                        selectedPlaylistIds = metadata.playlistItems.map { item -> item.id }.toSet()
                    )
                }
                onComplete?.invoke()
            }.onFailure { error ->
                val msg = if (error.message?.contains("instance not initialized") == true && OmniApplication.initError != null) {
                    "Engine initialization error: ${OmniApplication.initError}"
                } else {
                    error.message ?: "Could not fetch details. Please check the URL and internet connection."
                }
                _uiState.update {
                    it.copy(
                        isFetching = false,
                        errorMessage = msg
                    )
                }
                onComplete?.invoke()
            }
        }
    }

    fun onQualitySelected(qualityId: String) {
        _uiState.update { it.copy(selectedQuality = qualityId) }
    }

    fun onTogglePlaylistItem(id: String) {
        _uiState.update { current ->
            val updated = if (current.selectedPlaylistIds.contains(id)) {
                current.selectedPlaylistIds - id
            } else {
                current.selectedPlaylistIds + id
            }
            current.copy(selectedPlaylistIds = updated)
        }
    }

    fun onSelectAllPlaylistItems() {
        _uiState.update { current ->
            val allIds = current.mediaMetadata?.playlistItems?.map { it.id }?.toSet() ?: emptySet()
            current.copy(selectedPlaylistIds = allIds)
        }
    }

    fun onDeselectAllPlaylistItems() {
        _uiState.update { it.copy(selectedPlaylistIds = emptySet()) }
    }

    fun startDownload(context: Context, onToast: (String) -> Unit) {
        val meta = _uiState.value.mediaMetadata ?: return
        val selectedQuality = _uiState.value.selectedQuality
        val selectedPlaylistIds = _uiState.value.selectedPlaylistIds

        val isPlaylist = meta.isPlaylist
        val hasItems = meta.playlistItems.isNotEmpty()
        val selectedCount = if (hasItems) selectedPlaylistIds.size else 0

        val isAudio = selectedQuality == "audio_mp3"
        val isSubset = isPlaylist && hasItems && selectedCount < meta.playlistItems.size

        val downloadUrl = if (isSubset) {
            meta.playlistItems.filter { selectedPlaylistIds.contains(it.id) }.joinToString("\n") { it.url }
        } else {
            meta.url
        }

        val isBusy = DownloadTracker.activeTask.value != null && 
            (DownloadTracker.activeTask.value?.status == TaskStatus.DOWNLOADING || 
             DownloadTracker.activeTask.value?.status == TaskStatus.PAUSED)

        repository.startDownload(
            context = context,
            url = downloadUrl,
            quality = selectedQuality,
            title = if (isSubset) "${meta.title} (${selectedCount} videos)" else meta.title,
            isAudio = isAudio,
            isPlaylist = if (isSubset) false else meta.isPlaylist
        )

        val toastMsg = if (isBusy) {
            val queuePos = DownloadTracker.queue.value.size + 1
            "Added to queue (Position #$queuePos) • Will auto-start"
        } else if (isPlaylist) {
            "Starting download for $selectedCount items! Tap Downloads icon to view progress."
        } else {
            "Download started! Tap Downloads icon above to view progress."
        }
        onToast(toastMsg)
    }

    fun pauseDownload(context: Context) {
        repository.pauseDownload(context)
    }

    fun resumeDownload(context: Context) {
        repository.resumeDownload(context)
    }

    fun cancelDownload(context: Context) {
        repository.cancelDownload(context)
    }

    fun cancelAllDownloads(context: Context) {
        repository.cancelAllDownloads(context)
    }

    fun removeQueuedTask(id: String) {
        DownloadTracker.removeQueuedTask(id)
    }

    fun clearQueue() {
        DownloadTracker.clearQueue()
    }

    fun openMedia(context: Context, item: DownloadTaskProgress) {
        StorageHelper.openMedia(context, item)
    }

    fun removeHistoryItem(taskId: String) {
        DownloadTracker.removeHistoryItem(taskId)
    }

    fun clearHistory() {
        DownloadTracker.clearAllHistory()
    }

    fun checkForUpdates(onErrorToast: (String) -> Unit) {
        _uiState.update { it.copy(isCheckingUpdate = true) }
        viewModelScope.launch {
            val result = repository.checkForUpdates()
            result.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateInfo = info,
                        showUpdateDialog = true
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isCheckingUpdate = false) }
                onErrorToast("Update check: ${err.message}")
            }
        }
    }

    fun updateEngineCore(context: Context, onResult: (isSuccess: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val result = repository.updateEngineCore(context)
            result.onSuccess { msg ->
                onResult(true, "Engine updated successfully! Please retry fetching.")
            }.onFailure { err ->
                onResult(false, "Update failed: ${err.message}")
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setDownloadsSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showDownloadsSheet = visible) }
    }

    fun setUpdateDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showUpdateDialog = visible) }
    }
}
