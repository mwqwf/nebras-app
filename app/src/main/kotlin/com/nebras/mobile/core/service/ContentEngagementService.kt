package com.nebras.mobile.core.service

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import com.nebras.mobile.core.model.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * عدّادات المشاهدة على Firestore — **مجهولة** (بدون UID).
 *
 * - `view_count`: عند فتح المحتوى (debounce دقيقة/عنصر).
 * - `play_count`: عند تجاوز 25% من المدّة (مرّة واحدة/جلسة).
 * - `complete_count`: عند تجاوز 90% (مرّة واحدة/جلسة).
 */
object ContentEngagementService {

    private const val TAG = "ContentEngagement"
    private const val FILES_COLLECTION = "content_unified_files"

    // 🚫 YouTube مقطوع: لا نكتب أيّ عدّاد تفاعل في `content_unified_youtube`.
    private const val VIEW_DEBOUNCE_MILLIS = 60_000L
    private const val FLUSH_EVERY_MILLIS = 5_000L

    private val db get() = FirestoreSyncConfig.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lastViewAt = ConcurrentHashMap<String, Long>()
    private val pendingViewIncrements = ConcurrentHashMap<String, Int>()
    private val playRecordedThisSession = ConcurrentHashMap.newKeySet<String>()
    private val completeRecordedThisSession = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var flushJob: Job? = null

    fun recordContentOpened(content: Content) = recordView(content)

    fun recordView(content: Content) {
        val id = content.id.trim()
        if (id.isEmpty()) return
        // منشورات المجتمع تملك عدّاداً أسبوعياً مستقلاً في UgcService. منعها
        // هنا مهمّ حتى لا ننشئ وثائق شبحيّة في content_unified_files بنفس المعرّف.
        if (id.startsWith("ugc_")) return

        val now = System.currentTimeMillis()
        val last = lastViewAt[id]
        if (last != null && now - last < VIEW_DEBOUNCE_MILLIS) return
        lastViewAt[id] = now
        pendingViewIncrements.merge(id, 1, Int::plus)
        scheduleFlush()
    }

    fun recordPlayMilestone(content: Content, ratio: Double) {
        val id = content.id.trim()
        if (id.isEmpty()) return
        if (id.startsWith("ugc_")) return

        if (ratio >= 0.9 && completeRecordedThisSession.add(id)) {
            scope.launch {
                runCatching {
                    docRef(content).set(
                        mapOf(
                            "complete_count" to FieldValue.increment(1),
                            "last_played_at" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge(),
                    ).await()
                }.onFailure { Log.d(TAG, "complete_count failed: ${it.message}") }
            }
        }

        if (ratio < 0.25 || !playRecordedThisSession.add(id)) return
        scope.launch {
            runCatching {
                docRef(content).set(
                    mapOf(
                        "play_count" to FieldValue.increment(1),
                        "last_played_at" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
            }.onFailure { Log.d(TAG, "play_count failed: ${it.message}") }
        }
    }

    private fun scheduleFlush() {
        if (flushJob != null) return
        flushJob = scope.launch {
            delay(FLUSH_EVERY_MILLIS)
            flushJob = null
            flushPendingViews()
        }
    }

    private suspend fun flushPendingViews() {
        if (pendingViewIncrements.isEmpty()) return
        val batch = HashMap(pendingViewIncrements)
        pendingViewIncrements.clear()

        for ((id, increment) in batch) {
            runCatching {
                // كلّ المحتوى المعروض يعيش في `content_unified_files` (مع مرآة
                // dashboard_uploads بنفس المعرّف) — YouTube مقطوع فلا مسار احتياطيّ.
                db.collection(FILES_COLLECTION).document(id).set(
                    mapOf(
                        "view_count" to FieldValue.increment(increment.toLong()),
                        "last_played_at" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
            }.onFailure { Log.d(TAG, "view_count failed $id: ${it.message}") }
        }
    }

    /** 🚫 YouTube مقطوع: كلّ العدّادات تذهب لمجموعة الملفّات الموحَّدة. */
    private fun docRef(content: Content): DocumentReference =
        db.collection(FILES_COLLECTION).document(content.id)
}
