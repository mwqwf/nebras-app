package com.nebras.mobile.core.service

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * 🛡️ خدمة الإخفاء التلقائي العالميّ للمحتوى المُبلَّغ عنه بسبب حقوق نشر.
 *
 * عند ورود أوّل بلاغ من نوع `copyright` يُكتَب مستند في
 * `content_takedown_pending/{contentId}`. تشترك هذه الخدمة في المجموعة عند
 * الإقلاع وتحتفظ بالمعرّفات المعلَّقة في الذاكرة.
 *
 * حُرّاس القوائم (`ContentCompliance.isRightsCompliant`) يستعملون [isPending]
 * لاستبعاد العنصر من العرض على **جميع** المستخدمين فوراً حتى مراجعة المالك.
 *
 * الفرق عن [HiddenContentService]:
 *   • Hidden = محلّيّ على جهاز المُبلِّغ فقط.
 *   • PendingTakedown = عالميّ يُخفي عن كلّ المستخدمين.
 */
object PendingTakedownService {

    private const val TAG = "PendingTakedown"
    private const val COLLECTION = "content_takedown_pending"

    @Volatile
    private var ids: Set<String> = emptySet()

    private val _state = MutableStateFlow<Set<String>>(emptySet())

    /** يتغيّر فور وصول/زوال علامة المنع — تراقبه الواجهات. */
    val pendingIds: StateFlow<Set<String>> = _state.asStateFlow()

    private var registration: ListenerRegistration? = null
    private var initialized = false

    /** يُستدعى بعد تهيئة Firebase. آمن للاستدعاء أكثر من مرّة. */
    fun init() {
        if (initialized) return
        initialized = true
        runCatching {
            // اشتراك مستمرّ — يحدّث المجموعة فور كتابة/حذف أيّ مستند. حدّ 500
            // مستند كافٍ تماماً (تجاوزه يعني تراكم مراجعات، لا مشكلة عرض).
            registration = FirestoreSyncConfig.instance
                .collection(COLLECTION)
                .limit(500)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.d(TAG, "listen failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    val next = snapshot?.documents
                        ?.mapNotNull { it.id.trim().takeIf(String::isNotEmpty) }
                        ?.toSet()
                        .orEmpty()
                    // تحديث ذرّيّ + بثّ للواجهة فوراً.
                    ids = next
                    _state.value = next
                }
        }.onFailure {
            Log.d(TAG, "init failed: ${it.message}")
        }
    }

    /** متزامن — يُستعمل داخل حُرّاس القوائم لكلّ عنصر. */
    fun isPending(id: String): Boolean = id.isNotEmpty() && ids.contains(id)

    /**
     * يكتب علامة الإخفاء العالميّ عند ورود بلاغ حقوق نشر.
     * المستند idempotent (نفس المعرّف يُكتَب مرّة واحدة) فلا تكاثر.
     */
    suspend fun markPending(
        contentId: String,
        reasonCode: String,
        reporterUid: String? = null,
        contentType: String? = null,
    ) {
        val id = contentId.trim()
        if (id.isEmpty()) return
        runCatching {
            val payload = mutableMapOf<String, Any>(
                "contentId" to id,
                "reasonCode" to reasonCode,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            contentType?.takeIf(String::isNotEmpty)?.let { payload["contentType"] = it }
            reporterUid?.takeIf(String::isNotEmpty)?.let { payload["reporterUid"] = it }

            FirestoreSyncConfig.instance
                .collection(COLLECTION)
                .document(id)
                .set(payload, SetOptions.merge())
                .await()
        }.onFailure {
            // لا نُفشل عمليّة البلاغ بسبب فشل العلامة العالميّة — البلاغ نفسه
            // كُتب أصلاً، والمالك سيراجعه ويحذف يدوياً.
            Log.d(TAG, "mark failed: ${it.message}")
        }
    }

    fun dispose() {
        registration?.remove()
        registration = null
        initialized = false
    }
}
