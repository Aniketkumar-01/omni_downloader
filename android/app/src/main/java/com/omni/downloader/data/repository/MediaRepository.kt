package com.omni.downloader.data.repository

import android.content.Context
import com.omni.downloader.data.models.MediaMetadata
import com.omni.downloader.engine.AppUpdateInfo
import com.omni.downloader.engine.UpdateManager
import com.omni.downloader.engine.YoutubeDLEngine
import com.omni.downloader.service.DownloadForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository interface defining data and engine operations for OmniDownloader Android.
 */
interface MediaRepository {
    suspend fun fetchMetadata(context: Context, url: String): Result<MediaMetadata>
    suspend fun updateEngineCore(context: Context): Result<String>
    suspend fun checkForUpdates(): Result<AppUpdateInfo>
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<File>
    fun startDownload(
        context: Context,
        url: String,
        quality: String,
        title: String,
        isAudio: Boolean,
        isPlaylist: Boolean
    )
    fun pauseDownload(context: Context)
    fun resumeDownload(context: Context)
    fun cancelDownload(context: Context)
}

/**
 * Default implementation delegating to YoutubeDLEngine, UpdateManager, and DownloadForegroundService.
 */
class DefaultMediaRepository : MediaRepository {

    override suspend fun fetchMetadata(context: Context, url: String): Result<MediaMetadata> =
        withContext(Dispatchers.IO) {
            YoutubeDLEngine.fetchMetadata(context, url)
        }

    override suspend fun updateEngineCore(context: Context): Result<String> =
        withContext(Dispatchers.IO) {
            YoutubeDLEngine.updateYtDlpCore(context)
        }

    override suspend fun checkForUpdates(): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            UpdateManager.checkForUpdates()
        }

    override suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        UpdateManager.downloadApk(context, apkUrl, onProgress)
    }

    override fun startDownload(
        context: Context,
        url: String,
        quality: String,
        title: String,
        isAudio: Boolean,
        isPlaylist: Boolean
    ) {
        DownloadForegroundService.startDownload(
            context = context,
            url = url,
            quality = quality,
            title = title,
            isAudio = isAudio,
            isPlaylist = isPlaylist
        )
    }

    override fun pauseDownload(context: Context) {
        DownloadForegroundService.pauseDownload(context)
    }

    override fun resumeDownload(context: Context) {
        DownloadForegroundService.resumeDownload(context)
    }

    override fun cancelDownload(context: Context) {
        DownloadForegroundService.cancelDownload(context)
    }
}
