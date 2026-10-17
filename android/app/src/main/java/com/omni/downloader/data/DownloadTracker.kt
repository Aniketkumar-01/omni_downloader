package com.omni.downloader.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.omni.downloader.data.models.DownloadTaskProgress
import com.omni.downloader.data.models.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.omni.downloader.data.models.QueuedDownloadTask
import org.json.JSONArray
import org.json.JSONObject

object DownloadTracker {
    private const val TAG = "DownloadTracker"
    private const val PREFS_NAME = "omni_download_history"
    private const val KEY_HISTORY = "history_json"
    private const val KEY_ACTIVE_TASK = "active_task_json"
    private const val KEY_QUEUE = "queue_json"

    private var prefs: SharedPreferences? = null

    private val _activeTask = MutableStateFlow<DownloadTaskProgress?>(null)
    val activeTask = _activeTask.asStateFlow()

    private val _history = MutableStateFlow<List<DownloadTaskProgress>>(emptyList())
    val history = _history.asStateFlow()

    private val _queue = MutableStateFlow<List<QueuedDownloadTask>>(emptyList())
    val queue = _queue.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadHistory()
            loadActiveTask()
            loadQueue()
        }
    }

    private fun loadActiveTask() {
        val jsonStr = prefs?.getString(KEY_ACTIVE_TASK, null) ?: return
        try {
            val obj = JSONObject(jsonStr)
            val statusStr = obj.optString("status", TaskStatus.DOWNLOADING.name)
            val status = try { TaskStatus.valueOf(statusStr) } catch (e: Exception) { TaskStatus.DOWNLOADING }
            if (status == TaskStatus.DOWNLOADING || status == TaskStatus.PAUSED) {
                _activeTask.value = DownloadTaskProgress(
                    taskId = obj.optString("taskId", ""),
                    status = status,
                    progressPercent = obj.optDouble("progressPercent", 0.0).toFloat(),
                    currentTitle = obj.optString("currentTitle", ""),
                    speedFormatted = obj.optString("speedFormatted", "--"),
                    etaFormatted = obj.optString("etaFormatted", "--"),
                    completedItems = obj.optInt("completedItems", 0),
                    totalItems = obj.optInt("totalItems", 1),
                    isAudio = obj.optBoolean("isAudio", false)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load active task: ${e.message}")
        }
    }

    private fun loadHistory() {
        val jsonStr = prefs?.getString(KEY_HISTORY, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<DownloadTaskProgress>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val statusStr = obj.optString("status", TaskStatus.COMPLETED.name)
                val status = try { TaskStatus.valueOf(statusStr) } catch (e: Exception) { TaskStatus.COMPLETED }
                list.add(
                    DownloadTaskProgress(
                        taskId = obj.optString("taskId", ""),
                        status = status,
                        progressPercent = obj.optDouble("progressPercent", 100.0).toFloat(),
                        currentTitle = obj.optString("currentTitle", ""),
                        savedFilePath = obj.optString("savedFilePath").takeIf { it.isNotBlank() },
                        errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                        isAudio = obj.optBoolean("isAudio", false),
                        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() }
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load saved history: ${e.message}")
        }
    }

    private fun saveHistory() {
        val p = prefs ?: return
        try {
            val jsonArray = JSONArray()
            for (item in _history.value.take(50)) {
                val obj = JSONObject().apply {
                    put("taskId", item.taskId)
                    put("status", item.status.name)
                    put("progressPercent", item.progressPercent.toDouble())
                    put("currentTitle", item.currentTitle)
                    item.savedFilePath?.let { put("savedFilePath", it) }
                    item.errorMessage?.let { put("errorMessage", it) }
                    put("isAudio", item.isAudio)
                    item.mimeType?.let { put("mimeType", it) }
                }
                jsonArray.put(obj)
            }
            p.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist history: ${e.message}")
        }
    }

    fun updateProgress(task: DownloadTaskProgress) {
        _activeTask.value = task
        val p = prefs
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELLED) {
            _history.value = listOf(task) + _history.value.filter { it.taskId != task.taskId }
            if (_activeTask.value?.taskId == task.taskId) {
                _activeTask.value = null
            }
            saveHistory()
            p?.edit()?.remove(KEY_ACTIVE_TASK)?.apply()
        } else {
            try {
                val obj = JSONObject().apply {
                    put("taskId", task.taskId)
                    put("status", task.status.name)
                    put("progressPercent", task.progressPercent.toDouble())
                    put("currentTitle", task.currentTitle)
                    put("speedFormatted", task.speedFormatted)
                    put("etaFormatted", task.etaFormatted)
                    put("completedItems", task.completedItems)
                    put("totalItems", task.totalItems)
                    put("isAudio", task.isAudio)
                }
                p?.edit()?.putString(KEY_ACTIVE_TASK, obj.toString())?.apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist active task: ${e.message}")
            }
        }
    }

    fun removeHistoryItem(taskId: String) {
        _history.value = _history.value.filter { it.taskId != taskId }
        saveHistory()
    }

    fun clearAllHistory() {
        _history.value = emptyList()
        saveHistory()
    }

    private fun loadQueue() {
        val jsonStr = prefs?.getString(KEY_QUEUE, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<QueuedDownloadTask>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    QueuedDownloadTask(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        url = obj.optString("url", ""),
                        quality = obj.optString("quality", "best"),
                        title = obj.optString("title", "Media Download"),
                        isAudio = obj.optBoolean("isAudio", false),
                        isPlaylist = obj.optBoolean("isPlaylist", false),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
            _queue.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load saved queue: ${e.message}")
        }
    }

    private fun saveQueue() {
        val p = prefs ?: return
        try {
            val jsonArray = JSONArray()
            for (item in _queue.value) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("quality", item.quality)
                    put("title", item.title)
                    put("isAudio", item.isAudio)
                    put("isPlaylist", item.isPlaylist)
                    put("addedAt", item.addedAt)
                }
                jsonArray.put(obj)
            }
            p.edit().putString(KEY_QUEUE, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist queue: ${e.message}")
        }
    }

    fun enqueueTask(task: QueuedDownloadTask): Int {
        val current = _queue.value
        if (current.any { it.url == task.url && it.quality == task.quality }) {
            return current.indexOfFirst { it.url == task.url && it.quality == task.quality } + 1
        }
        val updated = current + task
        _queue.value = updated
        saveQueue()
        return updated.size
    }

    fun pollNextQueuedTask(): QueuedDownloadTask? {
        val current = _queue.value
        if (current.isEmpty()) return null
        val next = current.first()
        _queue.value = current.drop(1)
        saveQueue()
        return next
    }

    fun removeQueuedTask(id: String): Boolean {
        val current = _queue.value
        val updated = current.filter { it.id != id }
        val changed = updated.size != current.size
        if (changed) {
            _queue.value = updated
            saveQueue()
        }
        return changed
    }

    fun clearQueue() {
        _queue.value = emptyList()
        saveQueue()
    }
}
