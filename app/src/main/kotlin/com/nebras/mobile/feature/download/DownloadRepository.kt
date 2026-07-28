package com.nebras.mobile.feature.download

import android.content.Context
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import com.nebras.mobile.core.network.NebrasHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class MediaDownloadStatus { IDLE, DOWNLOADING, PAUSED, COMPLETED, FAILED }

/**
 * عنصر تنزيل — نقل `download/data/download_item_model.dart` (Hive typeId=1).
 * الحالة تُخزَّن نصّاً بنفس القيم للتوافق مع صيغة نسخة Flutter.
 */
data class DownloadItem(
    val contentId: String,
    val url: String,
    val localPath: String,
    val progress: Double = 0.0,
    /** 'idle' | 'downloading' | 'paused' | 'completed' | 'failed' */
    val status: String = "idle",
    val totalBytes: Long = 0,
    /** 'video' | 'audio' | 'book' | 'document' */
    val contentType: String,
    val title: String = "",
    val imageUrl: String? = null,
    /** ETag/Last-Modified للتحقّق عند استئناف التنزيل (If-Range). */
    val etag: String? = null,
) {
    val downloadStatus: MediaDownloadStatus
        get() = when (status) {
            "downloading" -> MediaDownloadStatus.DOWNLOADING
            "paused" -> MediaDownloadStatus.PAUSED
            "completed" -> MediaDownloadStatus.COMPLETED
            "failed" -> MediaDownloadStatus.FAILED
            else -> MediaDownloadStatus.IDLE
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("contentId", contentId)
        put("url", url)
        put("localPath", localPath)
        put("progress", progress)
        put("status", status)
        put("totalBytes", totalBytes)
        put("contentType", contentType)
        put("title", title)
        imageUrl?.let { put("imageUrl", it) }
        etag?.let { put("etag", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): DownloadItem = DownloadItem(
            contentId = json.optString("contentId"),
            url = json.optString("url"),
            localPath = json.optString("localPath"),
            progress = json.optDouble("progress", 0.0),
            status = json.optString("status", "idle"),
            totalBytes = json.optLong("totalBytes", 0),
            contentType = json.optString("contentType"),
            title = json.optString("title"),
            imageUrl = json.optString("imageUrl").takeIf { it.isNotEmpty() },
            etag = json.optString("etag").takeIf { it.isNotEmpty() },
        )
    }
}

/**
 * محرّك التنزيل — يدمج `DownloadDatasource` (كان فوق Dio) و
 * `DownloadRepositoryImpl` (فوق صندوق Hive `downloads`) في طبقة واحدة
 * فوق OkHttp + [LocalStore].
 *
 * الاستئناف عبر `Range` + `If-Range` بنفس بروتوكول نسخة Flutter:
 *   • التنزيل يذهب لملفّ `.part`؛ الاستئناف يذهب لملفّ `.seg` منفصل.
 *   • ردّ 206 → يُلحق `.seg` بذيل `.part`؛ ردّ 200 (تغيّر الملفّ على
 *     الخادم) → يُستبدل `.part` بـ`.seg` كاملاً.
 *   • عند الاكتمال يُعاد تسمية `.part` إلى المسار النهائيّ.
 */
class DownloadRepository(
    context: Context,
    private val store: LocalStore,
) {

    private val appContext = context.applicationContext
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val items = ConcurrentHashMap<String, DownloadItem>()

    /** عميل تنزيل بمهلة قراءة طويلة (ملفّات كبيرة تستغرق دقائق). */
    private val client = NebrasHttpClient.downloadClient.newBuilder()
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    init {
        load()
    }

    // ── مسارات الملفّات ─────────────────────────────────────────────

    private fun downloadDir(): File =
        File(appContext.filesDir, "media_downloads").apply { if (!exists()) mkdirs() }

    fun getFilePath(contentId: String, extension: String): String =
        File(downloadDir(), "$contentId.$extension").absolutePath

    /** يستنبط الامتداد من الرابط ثمّ من نوع المحتوى — نفس القوائم حرفياً. */
    fun extensionFor(url: String, contentType: String): String {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        val audioExts = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav", ".wma")
        val videoExts = listOf(".mp4", ".mkv", ".mov", ".webm", ".m4v", ".avi", ".3gp", ".ogv")
        for (e in audioExts) if (path.endsWith(e)) return e.substring(1)
        for (e in videoExts) if (path.endsWith(e)) return e.substring(1)
        if (path.endsWith(".pdf")) return "pdf"
        if (path.endsWith(".epub")) return "epub"
        if (contentType == "audio") return "mp3"
        if (contentType == "document") return "pdf"
        return "mp4"
    }

    // ── واجهة المستودع ──────────────────────────────────────────────

    fun getDownloadItem(contentId: String): DownloadItem? = items[contentId]

    fun getAllDownloads(): Map<String, DownloadItem> = HashMap(items)

    fun saveDownloadItem(item: DownloadItem) {
        items[item.contentId] = item
        persist()
    }

    suspend fun startDownload(
        contentId: String,
        url: String,
        contentType: String,
        onProgress: (progress: Double, totalBytes: Long) -> Unit,
    ) {
        val ext = extensionFor(url, contentType)
        val localPath = getFilePath(contentId, ext)
        val etag = getValidator(url)
        saveDownloadItem(
            DownloadItem(
                contentId = contentId,
                url = url,
                localPath = localPath,
                status = "downloading",
                contentType = contentType,
                etag = etag,
            ),
        )
        download(
            contentId = contentId,
            url = url,
            savePath = localPath,
            startByte = 0,
            ifRange = null,
            onProgress = { progress, total -> persistProgress(contentId, progress, total, onProgress) },
        )
        items[contentId]?.let {
            saveDownloadItem(it.copy(status = "completed", progress = 1.0))
        }
    }

    fun pauseDownload(contentId: String) {
        cancelCall(contentId) // إلغاء طلب HTTP — الملفّ الجزئيّ يبقى للاستئناف.
        items[contentId]?.let { saveDownloadItem(it.copy(status = "paused")) }
    }

    suspend fun resumeDownload(
        contentId: String,
        onProgress: (progress: Double, totalBytes: Long) -> Unit,
    ) {
        val item = items[contentId] ?: return
        val startByte = getPartialFileSize(item.localPath)
        saveDownloadItem(item.copy(status = "downloading"))
        download(
            contentId = contentId,
            url = item.url,
            savePath = item.localPath,
            startByte = startByte,
            ifRange = item.etag,
            onProgress = { progress, total -> persistProgress(contentId, progress, total, onProgress) },
        )
        items[contentId]?.let {
            saveDownloadItem(it.copy(status = "completed", progress = 1.0))
        }
    }

    fun cancelDownload(contentId: String) {
        cancelCall(contentId)
        items[contentId]?.let { deleteFiles(it.localPath) }
        items.remove(contentId)
        persist()
    }

    fun deleteDownload(contentId: String) {
        items[contentId]?.let { deleteFiles(it.localPath) }
        items.remove(contentId)
        persist()
    }

    fun fileExists(path: String): Boolean = File(path).exists()

    fun getPartialFileSize(path: String): Long {
        val partFile = File("$path.part")
        return if (partFile.exists()) partFile.length() else 0L
    }

    // ── محرّك HTTP ──────────────────────────────────────────────────

    private suspend fun download(
        contentId: String,
        url: String,
        savePath: String,
        startByte: Long,
        ifRange: String?,
        onProgress: (progress: Double, totalBytes: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val partPath = "$savePath.part"
        val segPath = "$savePath.seg"
        val resuming = startByte > 0 && File(partPath).exists()
        val downloadTarget = File(if (resuming) segPath else partPath)

        val builder = Request.Builder().url(url)
        if (resuming) {
            builder.header("Range", "bytes=$startByte-")
            if (!ifRange.isNullOrEmpty()) builder.header("If-Range", ifRange)
        }

        val call = client.newCall(builder.build())
        activeCalls[contentId] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Download failed: empty body")
                val contentLength = body.contentLength()

                FileOutputStream(downloadTarget).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            received += read
                            if (contentLength > 0) {
                                val totalReceived = startByte + received
                                val totalSize = startByte + contentLength
                                onProgress(
                                    (totalReceived.toDouble() / totalSize).coerceIn(0.0, 1.0),
                                    totalSize,
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (resuming) {
                    val seg = File(segPath)
                    if (response.code == 206) {
                        // استئناف ناجح: ألحق الجزء الجديد بذيل `.part`.
                        FileOutputStream(File(partPath), true).use { sink ->
                            seg.inputStream().use { it.copyTo(sink) }
                        }
                        if (seg.exists()) seg.delete()
                    } else {
                        // الخادم أعاد الملفّ كاملاً (تغيّر منذ آخر مرّة) —
                        // استبدل الجزئيّ القديم بالجديد.
                        File(partPath).takeIf { it.exists() }?.delete()
                        seg.renameTo(File(partPath))
                    }
                }

                val partFile = File(partPath)
                if (partFile.exists()) partFile.renameTo(File(savePath))
            }
        } catch (e: Throwable) {
            safeDelete(segPath)
            when {
                e is CancellationException -> throw e
                call.isCanceled() -> throw IOException("Download cancelled")
                e is java.net.SocketTimeoutException ->
                    throw IOException("Download timeout. Please check your connection.")
                e is java.net.UnknownHostException || e is java.net.ConnectException ->
                    throw IOException("Network error. Please check your internet connection.")
                e is java.io.FileNotFoundException || e.message?.contains("ENOSPC") == true ->
                    throw IOException("Storage full or unavailable. Free up space and try again.")
                else -> throw IOException("Download failed: ${e.message}")
            }
        } finally {
            activeCalls.remove(contentId)
        }
    }

    private fun cancelCall(contentId: String) {
        activeCalls.remove(contentId)?.cancel()
    }

    private fun deleteFiles(path: String) {
        safeDelete(path)
        safeDelete("$path.part")
        safeDelete("$path.seg")
    }

    private fun safeDelete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** ETag أو Last-Modified — التحقّق عند الاستئناف بـ If-Range. */
    private suspend fun getValidator(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
                resp.header("etag")?.takeIf { it.isNotEmpty() }
                    ?: resp.header("last-modified")?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    suspend fun getRemoteFileSize(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
                resp.header("content-length")?.toLongOrNull()
            }
        }.getOrNull()
    }

    // ── التثبيت (بديل صندوق Hive `downloads`) ───────────────────────

    /** لا نكتب كلّ نبضة تقدّم — فرق ≥5% أو الاكتمال (نفس عتبة Flutter). */
    private fun persistProgress(
        contentId: String,
        progress: Double,
        totalBytes: Long,
        onProgress: (Double, Long) -> Unit,
    ) {
        val current = items[contentId]
        if (current != null) {
            val diff = progress - current.progress
            if (diff >= 0.05 || progress >= 1.0) {
                items[contentId] = current.copy(progress = progress, totalBytes = totalBytes)
                persist()
            } else {
                items[contentId] = current.copy(progress = progress, totalBytes = totalBytes)
            }
        }
        onProgress(progress, totalBytes)
    }

    private fun load() {
        val root = store.getJsonObject(LocalStoreKeys.BOX_DOWNLOADS) ?: return
        for (key in root.keys()) {
            val obj = root.optJSONObject(key) ?: continue
            runCatching { DownloadItem.fromJson(obj) }
                .onSuccess { items[key] = it }
        }
    }

    private fun persist() {
        runCatching {
            val root = JSONObject()
            items.forEach { (key, item) -> root.put(key, item.toJson()) }
            store.putJsonObject(LocalStoreKeys.BOX_DOWNLOADS, root)
        }
    }
}
