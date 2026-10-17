package com.omni.downloader.data.models

enum class TaskStatus {
    IDLE,
    FETCHING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class QueuedDownloadTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val quality: String,
    val title: String,
    val isAudio: Boolean = false,
    val isPlaylist: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

data class DownloadTaskProgress(
    val taskId: String,
    val status: TaskStatus = TaskStatus.IDLE,
    val progressPercent: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedFormatted: String = "--",
    val etaFormatted: String = "--",
    val currentTitle: String = "",
    val completedItems: Int = 0,
    val totalItems: Int = 1,
    val savedFilePath: String? = null,
    val errorMessage: String? = null,
    val isAudio: Boolean = false,
    val mimeType: String? = null
)

val DownloadTaskProgress.isAudioMedia: Boolean
    get() = isAudio ||
            savedFilePath?.contains("/audio", ignoreCase = true) == true ||
            savedFilePath?.endsWith(".mp3", ignoreCase = true) == true ||
            savedFilePath?.endsWith(".m4a", ignoreCase = true) == true ||
            savedFilePath?.endsWith(".opus", ignoreCase = true) == true ||
            savedFilePath?.endsWith(".flac", ignoreCase = true) == true ||
            savedFilePath?.endsWith(".wav", ignoreCase = true) == true ||
            mimeType?.startsWith("audio/") == true
