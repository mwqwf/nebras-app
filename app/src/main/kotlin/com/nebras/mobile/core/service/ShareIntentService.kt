package com.nebras.mobile.core.service

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class SharedIncomingFile(
    val path: String,
    val name: String,
    val mime: String,
)

/**
 * جسر Android `ACTION_SEND`. يلتقط الملفّ سواء فُتح نبراس من الإغلاق الكامل
 * أو وصل أثناء التشغيل، ثمّ يمرّره إلى شاشة نشر المجتمع.
 *
 * بديل MethodChannel في نسخة Flutter: نقرأ الـ Intent مباشرة وننسخ الـ
 * `content://` إلى ملفّ في الكاش (المُرسِل قد يُلغي الإذن بعد لحظات).
 */
object ShareIntentService {

    private val _pendingFile = MutableStateFlow<SharedIncomingFile?>(null)

    /** آخر ملفّ مشترَك وصل — تستهلكه شاشة النشر ثمّ تصفّره. */
    val pendingFile: StateFlow<SharedIncomingFile?> = _pendingFile.asStateFlow()

    /** يُستدعى من `MainActivity.onCreate` و`onNewIntent`. */
    fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return

        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        val resolved = copyToCache(context, uri, intent.type.orEmpty()) ?: return
        _pendingFile.value = resolved
    }

    fun consume() {
        _pendingFile.value = null
    }

    private fun copyToCache(context: Context, uri: Uri, mime: String): SharedIncomingFile? =
        runCatching {
            val resolver = context.contentResolver
            val name = queryDisplayName(resolver, uri) ?: "ملف مشترك"
            val target = File(context.cacheDir, "shared_${System.currentTimeMillis()}_$name")
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use(input::copyTo)
            } ?: return@runCatching null
            if (!target.exists() || target.length() == 0L) return@runCatching null
            SharedIncomingFile(
                path = target.absolutePath,
                name = name,
                mime = mime.ifEmpty { resolver.getType(uri).orEmpty() },
            )
        }.getOrNull()

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index < 0) null else cursor.getString(index)
                }
        }.getOrNull()
}
