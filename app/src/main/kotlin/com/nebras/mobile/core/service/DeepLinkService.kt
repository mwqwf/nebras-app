package com.nebras.mobile.core.service

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * روابط نبراس العميقة — `nebras://content/{id}` تفتح العنصر مباشرة.
 *
 * في نسخة Flutter كان جسر MethodChannel في MainActivity يمرّر الرابط؛ هنا
 * نقرأ `Intent` مباشرة من الـ Activity (عند الإقلاع وفي `onNewIntent`)
 * ونبثّه عبر [pendingContentId] لتلتقطه طبقة التنقّل.
 */
object DeepLinkService {

    private const val SCHEME = "nebras"
    private const val HOST = "content"

    private val _pendingContentId = MutableStateFlow<String?>(null)

    /** آخر معرّف محتوى وصل من رابط عميق — تستهلكه طبقة التنقّل ثم تصفّره. */
    val pendingContentId: StateFlow<String?> = _pendingContentId.asStateFlow()

    /** يُستدعى من `MainActivity.onCreate` و`onNewIntent`. */
    fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val id = parseContentId(intent.dataString ?: return) ?: return
        _pendingContentId.value = id
    }

    /** تستدعيها طبقة التنقّل بعد فتح الشاشة كي لا يتكرّر الفتح. */
    fun consume() {
        _pendingContentId.value = null
    }

    /** يستخرج المعرّف من `nebras://content/{id}` (يقبل مقاطع إضافيّة بعده). */
    fun parseContentId(url: String): String? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME) return null
        if (uri.host != HOST) return null
        val seg = uri.pathSegments.firstOrNull().orEmpty()
        return seg.ifEmpty { null }
    }

    /** رابط مشاركة لعنصر. */
    fun contentLink(contentId: String): String = "$SCHEME://$HOST/$contentId"
}
