package com.nebras.mobile.core.service

import com.google.firebase.messaging.FirebaseMessaging
import com.nebras.mobile.core.data.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * متابعة الأقسام 🔔 — اشتراك FCM topic لكلّ قسم رئيسيّ يتابعه المستخدم
 * (`nebras_section_{id}`)، فيصله إشعار فور نشر اللوحة محتوًى جديداً فيه.
 * التخزين محلّي بصيغة `id|name` لكلّ مُدخل (نفس صيغة نسخة Flutter).
 */
object SectionFollowService {

    private const val PREF_KEY = "nebras_followed_sections"

    private lateinit var store: LocalStore

    private val _followedIds = MutableStateFlow<Set<String>>(emptySet())

    /** المعرّفات المتابَعة — تتحدّث أزرار الجرس فوراً عند التغيّر. */
    val followedIds: StateFlow<Set<String>> = _followedIds.asStateFlow()

    private val names = mutableMapOf<String, String>()
    private var loaded = false

    fun init(localStore: LocalStore) {
        store = localStore
        if (loaded) return
        loaded = true
        runCatching {
            val raw = store.getStringList(PREF_KEY)
            val ids = mutableSetOf<String>()
            for (entry in raw) {
                val i = entry.indexOf('|')
                val id = if (i > 0) entry.substring(0, i) else entry
                val name = if (i > 0) entry.substring(i + 1) else ""
                if (id.isNotEmpty()) {
                    ids.add(id)
                    names[id] = name
                }
            }
            _followedIds.value = ids
        }
    }

    fun isFollowed(sectionId: String): Boolean = _followedIds.value.contains(sectionId)

    fun nameOf(sectionId: String): String = names[sectionId] ?: ""

    private fun topic(sectionId: String): String = "nebras_section_$sectionId"

    /** تبديل المتابعة. يُرجع الحالة الجديدة (true = صار متابِعاً). */
    suspend fun toggle(sectionId: String, sectionName: String): Boolean {
        val ids = _followedIds.value.toMutableSet()
        val nowFollowing = !ids.contains(sectionId)
        if (nowFollowing) {
            ids.add(sectionId)
            names[sectionId] = sectionName
            runCatching { FirebaseMessaging.getInstance().subscribeToTopic(topic(sectionId)).await() }
        } else {
            ids.remove(sectionId)
            runCatching {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic(sectionId)).await()
            }
        }
        _followedIds.value = ids
        runCatching {
            store.putStringList(PREF_KEY, ids.map { "$it|${names[it] ?: ""}" })
        }
        return nowFollowing
    }
}
