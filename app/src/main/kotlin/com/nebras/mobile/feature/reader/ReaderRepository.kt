package com.nebras.mobile.feature.reader

import android.content.Context
import com.nebras.mobile.core.data.LocalStore
import java.io.File

/**
 * بيانات القارئ — يدمج `book_reading_progress_datasource` (آخر صفحة)
 * و`book_progress_datasource` (تقدّم التنزيل) و`book_local_datasource`
 * (ملفّات PDF المحليّة) في طبقة واحدة. المفاتيح مطابقة لنسخة Flutter
 * حرفياً كي يعمل ترحيل `LocalStore` بلا ترجمة.
 */
class ReaderRepository(
    context: Context,
    private val store: LocalStore,
) {

    private companion object {
        const val PAGE_PREFIX = "reading_page_"
        const val TOTAL_PAGES_PREFIX = "reading_total_pages_"
        const val DL_PROGRESS_PREFIX = "download_progress_"
        const val DL_TOTAL_PREFIX = "download_total_"
    }

    private val appContext = context.applicationContext

    // ── آخر صفحة قراءة ──────────────────────────────────────────────

    fun saveLastPage(bookId: String, currentPage: Int, totalPages: Int) {
        store.putInt("$PAGE_PREFIX$bookId", currentPage)
        store.putInt("$TOTAL_PAGES_PREFIX$bookId", totalPages)
    }

    fun getLastPage(bookId: String): Int? =
        if (store.contains("$PAGE_PREFIX$bookId")) {
            store.getInt("$PAGE_PREFIX$bookId")
        } else {
            null
        }

    fun getTotalPages(bookId: String): Int? =
        if (store.contains("$TOTAL_PAGES_PREFIX$bookId")) {
            store.getInt("$TOTAL_PAGES_PREFIX$bookId")
        } else {
            null
        }

    fun clearReadingProgress(bookId: String) {
        store.remove("$PAGE_PREFIX$bookId")
        store.remove("$TOTAL_PAGES_PREFIX$bookId")
    }

    fun hasReadingProgress(bookId: String): Boolean =
        store.contains("$PAGE_PREFIX$bookId")

    // ── تقدّم تنزيل الكتاب ──────────────────────────────────────────

    fun saveDownloadProgress(fileName: String, downloaded: Long, total: Long) {
        store.putLong("$DL_PROGRESS_PREFIX$fileName", downloaded)
        store.putLong("$DL_TOTAL_PREFIX$fileName", total)
    }

    fun getDownloadProgress(fileName: String): Pair<Long, Long>? {
        if (!store.contains("$DL_PROGRESS_PREFIX$fileName")) return null
        if (!store.contains("$DL_TOTAL_PREFIX$fileName")) return null
        return store.getLong("$DL_PROGRESS_PREFIX$fileName") to
            store.getLong("$DL_TOTAL_PREFIX$fileName")
    }

    fun clearDownloadProgress(fileName: String) {
        store.remove("$DL_PROGRESS_PREFIX$fileName")
        store.remove("$DL_TOTAL_PREFIX$fileName")
    }

    // ── ملفّات PDF المحليّة ─────────────────────────────────────────

    private fun docsDir(): File = appContext.filesDir

    fun isDownloaded(fileName: String): Boolean = File(docsDir(), "$fileName.pdf").exists()

    fun getFilePath(fileName: String): String = File(docsDir(), "$fileName.pdf").absolutePath

    fun getPartialFileSize(fileName: String): Long {
        val file = File(docsDir(), "$fileName.pdf.part")
        return if (file.exists()) file.length() else 0L
    }

    fun hasPartialDownload(fileName: String): Boolean =
        File(docsDir(), "$fileName.pdf.part").exists()

    fun finalizeDownload(fileName: String) {
        val partFile = File(docsDir(), "$fileName.pdf.part")
        if (partFile.exists()) partFile.renameTo(File(docsDir(), "$fileName.pdf"))
    }

    fun deletePartialFile(fileName: String) {
        runCatching { File(docsDir(), "$fileName.pdf.part").takeIf { it.exists() }?.delete() }
    }

    fun validateFileSize(fileName: String, expectedSize: Long): Boolean {
        val file = File(docsDir(), "$fileName.pdf")
        return file.exists() && file.length() == expectedSize
    }
}
