package com.nebras.mobile.feature.download

import android.util.Log
import com.nebras.mobile.feature.saved.SavedItem
import com.nebras.mobile.feature.saved.SavedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.floor

/**
 * حالة التنزيلات للواجهة — نقل `MediaDownloadProvider` بنفس الدلالات:
 * حالة تفاؤليّة فوريّة، إشعار عند تغيّر النسبة المئويّة فقط (لا كلّ نبضة)،
 * وتصنيف مفاتيح الأخطاء.
 */
object MediaDownloadController {

    private const val TAG = "MediaDownload"

    private lateinit var repository: DownloadRepository
    private lateinit var savedRepository: SavedRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloads = LinkedHashMap<String, DownloadItem>()
    private val lastNotifiedPercent = HashMap<String, Int>()

    private val _state = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val state: StateFlow<Map<String, DownloadItem>> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<Pair<String, String>?>(null)

    /** آخر خطأ: (contentId، مفتاح الرسالة) — تعرضه الواجهة ثمّ تصفّره. */
    val lastError: StateFlow<Pair<String, String>?> = _lastError.asStateFlow()

    fun init(downloadRepository: DownloadRepository, saved: SavedRepository) {
        repository = downloadRepository
        savedRepository = saved
        restoreDownloads()
    }

    fun clearLastError() {
        _lastError.value = null
    }

    fun hasDownload(contentId: String): Boolean = downloads.containsKey(contentId)

    val totalDownloadedBytes: Long
        get() = downloads.values
            .filter { it.downloadStatus == MediaDownloadStatus.COMPLETED }
            .sumOf { it.totalBytes }

    fun sizeOf(contentId: String): Long = downloads[contentId]?.totalBytes ?: 0

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = listOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.size - 1) {
            size /= 1024
            unit++
        }
        val formatted = if (size >= 10 || unit == 0) {
            size.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", size)
        }
        return "$formatted ${units[unit]}"
    }

    /**
     * استرجاع التنزيلات المحفوظة عند الإقلاع. ما كان «قيد التنزيل» عند
     * إغلاق التطبيق يتحوّل إلى «متوقّف» (العمليّة ماتت مع العمليّة).
     */
    fun restoreDownloads() {
        runCatching {
            val persisted = repository.getAllDownloads()
            downloads.clear()
            for ((key, item) in persisted) {
                if (item.downloadStatus == MediaDownloadStatus.DOWNLOADING) {
                    val paused = item.copy(status = "paused")
                    repository.saveDownloadItem(paused)
                    downloads[key] = paused
                } else {
                    downloads[key] = item
                }
            }
            publish()
        }.onFailure { Log.d(TAG, "Restore downloads failed: ${it.message}") }
    }

    fun getStatus(contentId: String): MediaDownloadStatus =
        downloads[contentId]?.downloadStatus ?: MediaDownloadStatus.IDLE

    fun getProgress(contentId: String): Double = downloads[contentId]?.progress ?: 0.0

    /** المسار المحلّيّ للملفّ المكتمل — أو null إن غاب/حُذف من القرص. */
    fun getLocalPath(contentId: String): String? {
        val item = downloads[contentId] ?: return null
        if (item.downloadStatus == MediaDownloadStatus.COMPLETED &&
            item.localPath.isNotEmpty()
        ) {
            if (File(item.localPath).exists()) return item.localPath
            Log.d(TAG, "Downloaded file missing: ${item.localPath}")
        }
        return null
    }

    fun start(
        contentId: String,
        url: String,
        contentType: String,
        title: String = "",
        imageUrl: String? = null,
    ) {
        val current = downloads[contentId]
        if (current != null &&
            (current.downloadStatus == MediaDownloadStatus.DOWNLOADING ||
                current.downloadStatus == MediaDownloadStatus.COMPLETED)
        ) {
            return
        }
        val displayTitle = title.ifEmpty { contentId }
        downloads[contentId] = DownloadItem(
            contentId = contentId,
            url = url,
            localPath = "",
            progress = 0.0,
            status = "downloading",
            totalBytes = 0,
            contentType = contentType,
            title = displayTitle,
            imageUrl = imageUrl,
        )
        publish()

        // المحتوى المنزَّل يُحفظ تلقائياً في المحفوظات (نفس سلوك Flutter
        // عبر EnsureSavedUseCase).
        savedRepository.ensureSaved(
            SavedItem(
                id = contentId,
                title = displayTitle,
                imageUrl = imageUrl,
                contentType = contentType,
                savedAtMs = System.currentTimeMillis(),
            ),
        )

        scope.launch {
            runCatching {
                repository.startDownload(contentId, url, contentType) { progress, total ->
                    handleProgress(contentId, progress, total)
                }
                if (!downloads.containsKey(contentId)) return@launch
                repository.getDownloadItem(contentId)?.let { downloads[contentId] = it }
                publish()
            }.onFailure { e ->
                val msg = e.toString()
                if (!msg.contains("cancelled")) {
                    val base = repository.getDownloadItem(contentId) ?: downloads[contentId]
                    base?.let { downloads[contentId] = it.copy(status = "failed") }
                    setError(contentId, msg)
                    publish()
                }
            }
        }
    }

    fun pause(contentId: String) {
        if (getStatus(contentId) != MediaDownloadStatus.DOWNLOADING) return
        repository.pauseDownload(contentId)
        downloads[contentId]?.let { downloads[contentId] = it.copy(status = "paused") }
        lastNotifiedPercent.remove(contentId)
        publish()
    }

    fun resume(contentId: String) {
        val current = downloads[contentId] ?: return
        if (current.downloadStatus != MediaDownloadStatus.PAUSED &&
            current.downloadStatus != MediaDownloadStatus.FAILED
        ) {
            return
        }
        downloads[contentId] = current.copy(status = "downloading")
        publish()

        scope.launch {
            runCatching {
                repository.resumeDownload(contentId) { progress, total ->
                    handleProgress(contentId, progress, total)
                }
                if (!downloads.containsKey(contentId)) return@launch
                val persisted = repository.getDownloadItem(contentId)
                downloads[contentId] = persisted
                    ?: downloads[contentId]?.copy(status = "completed", progress = 1.0)
                    ?: current.copy(status = "completed", progress = 1.0)
                publish()
            }.onFailure { e ->
                val msg = e.toString()
                if (!msg.contains("cancelled")) {
                    val base = repository.getDownloadItem(contentId) ?: downloads[contentId]
                    base?.let { downloads[contentId] = it.copy(status = "failed") }
                    setError(contentId, msg)
                    publish()
                }
            }
        }
    }

    fun cancel(contentId: String) {
        repository.cancelDownload(contentId)
        downloads.remove(contentId)
        lastNotifiedPercent.remove(contentId)
        publish()
    }

    fun delete(contentId: String) {
        repository.deleteDownload(contentId)
        downloads.remove(contentId)
        lastNotifiedPercent.remove(contentId)
        publish()
    }

    // ── داخليّات ────────────────────────────────────────────────────

    private fun setError(contentId: String, rawMessage: String) {
        _lastError.value = contentId to classifyErrorKey(rawMessage)
    }

    /** مفاتيح `ArStrings` — تُحلّ للنصّ العربيّ في الواجهة عبر `.ar`. */
    private fun classifyErrorKey(rawMessage: String): String {
        val msg = rawMessage.lowercase()
        return when {
            msg.contains("timeout") -> "download_error_timeout"
            msg.contains("network") || msg.contains("connection") -> "download_error_network"
            msg.contains("storage") || msg.contains("space") ||
                msg.contains("filesystem") -> "download_error_storage"
            else -> "download_error_generic"
        }
    }

    /** إشعار الواجهة عند تغيّر النسبة المئويّة الصحيحة فقط — لا كلّ نبضة. */
    private fun handleProgress(contentId: String, progress: Double, totalBytes: Long) {
        val existing = downloads[contentId] ?: return // أُلغي/حُذف أثناء التحميل.
        downloads[contentId] = existing.copy(
            progress = progress,
            totalBytes = totalBytes,
            status = "downloading",
        )
        val pct = floor((progress * 100).coerceIn(0.0, 100.0)).toInt()
        if (pct != (lastNotifiedPercent[contentId] ?: -1)) {
            lastNotifiedPercent[contentId] = pct
            publish()
        }
    }

    private fun publish() {
        _state.value = LinkedHashMap(downloads)
    }
}
