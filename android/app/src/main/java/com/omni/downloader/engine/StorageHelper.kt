package com.omni.downloader.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.omni.downloader.data.models.DownloadTaskProgress
import com.omni.downloader.data.models.isAudioMedia
import java.io.File
import java.io.FileInputStream

object StorageHelper {
    private const val TAG = "StorageHelper"

    fun getTempDownloadDir(context: Context): File {
        val dir = File(context.cacheDir, "omni_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Publishes a completed temporary media file into Android's public MediaStore
     * so it immediately appears in the user's Gallery, VLC, Files, and Music apps.
     */
    fun saveToPublicMedia(
        context: Context,
        sourceFile: File,
        title: String,
        isAudio: Boolean
    ): Uri? {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return null
        }

        val resolver = context.contentResolver
        val fileName = sourceFile.name

        val ext = sourceFile.extension.lowercase()
        val isAudioActual = isAudio || ext in setOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg")
        val mimeType = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "webm" -> if (isAudioActual) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            else -> if (isAudioActual) "audio/mpeg" else "video/mp4"
        }

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.TITLE, title)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativeDir = if (isAudioActual) {
                        "${Environment.DIRECTORY_MUSIC}/OmniDownloader"
                    } else {
                        "${Environment.DIRECTORY_MOVIES}/OmniDownloader"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }

            val collectionUri = if (isAudioActual) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }

            val destinationUri = resolver.insert(collectionUri, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore entry")

            // Copy file stream
            resolver.openOutputStream(destinationUri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            // Mark pending false on Android 10+ so other apps can immediately see it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(destinationUri, contentValues, null, null)
            }

            Log.i(TAG, "Successfully exported media to MediaStore: $destinationUri")
            destinationUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media to MediaStore: ${e.message}", e)
            null
        }
    }

    /**
     * Finds a media item in MediaStore by its title if direct URI is lost or expired.
     */
    fun findMediaStoreUriByTitle(context: Context, title: String, isAudio: Boolean): Uri? {
        val collection = if (isAudio) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val cleanTitle = title.replace(Regex("""\.[a-zA-Z0-9]+$"""), "").trim()
        if (cleanTitle.isBlank()) return null

        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.TITLE} LIKE ?"
        val selectionArgs = arrayOf("%$cleanTitle%", "%$cleanTitle%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        return try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    android.content.ContentUris.withAppendedId(collection, id)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying MediaStore for $title: ${e.message}")
            null
        }
    }

    /**
     * Resolves the playable Content Uri for a downloaded file, either from stored URI
     * or by searching the MediaStore library.
     */
    fun resolveMediaUri(
        context: Context,
        uriString: String?,
        title: String,
        isAudio: Boolean
    ): Uri? {
        if (!uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        return uri
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct content URI verify notice: ${e.message}")
                    // Return uri as external media players may still have authority access
                    return uri
                }
            } else if (uri.scheme == "file" || uriString.startsWith("/")) {
                val filePath = if (uri.scheme == "file") uri.path ?: uriString else uriString
                val file = File(filePath)
                if (file.exists()) {
                    return try {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                    } catch (e: Exception) {
                        Uri.fromFile(file)
                    }
                }
            }
        }

        return findMediaStoreUriByTitle(context, title, isAudio)
    }

    /**
     * Resolves MIME type appropriate for audio vs video playback.
     */
    fun resolveMimeType(context: Context, uri: Uri, isAudio: Boolean): String {
        try {
            val detected = context.contentResolver.getType(uri)
            if (!detected.isNullOrBlank()) {
                if (detected.startsWith("audio/") || detected.startsWith("video/")) {
                    return detected
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MIME resolution notice: ${e.message}")
        }
        return if (isAudio) "audio/*" else "video/*"
    }

    /**
     * Generates a chooser Intent for opening the media file in video or audio players.
     */
    fun getOpenMediaIntent(
        context: Context,
        uriString: String?,
        title: String,
        isAudio: Boolean
    ): Intent? {
        val targetUri = resolveMediaUri(context, uriString, title, isAudio) ?: return null
        val mimeType = resolveMimeType(context, targetUri, isAudio)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri(title, targetUri)
        }

        val chooserPrompt = if (isAudio) "Open audio with..." else "Open video with..."
        return Intent.createChooser(viewIntent, chooserPrompt).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Directly triggers the Android system chooser dialog so the user can select
     * which media player to open the audio or video with.
     */
    fun openMedia(
        context: Context,
        uriString: String?,
        title: String,
        isAudio: Boolean
    ) {
        val chooserIntent = getOpenMediaIntent(context, uriString, title, isAudio)
        if (chooserIntent != null) {
            try {
                context.startActivity(chooserIntent)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "ActivityNotFound for media chooser: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching media chooser: ${e.message}", e)
            }
        }

        // Fallback: Show location info and try opening media collection
        val typeName = if (isAudio) "Audio" else "Video"
        val folderName = if (isAudio) "Music/OmniDownloader" else "Movies/OmniDownloader"
        android.widget.Toast.makeText(
            context,
            "$typeName saved in $folderName. Launching media browser...",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        try {
            val fallbackUri = if (isAudio) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }
            val intent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with..."))
        } catch (e: Exception) {
            Log.w(TAG, "Fallback collection open error: ${e.message}")
        }
    }

    fun openMedia(context: Context, task: DownloadTaskProgress) {
        openMedia(
            context = context,
            uriString = task.savedFilePath,
            title = task.currentTitle,
            isAudio = task.isAudioMedia
        )
    }
}
