package com.nebras.mobile.feature.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** موضوع البثّ العامّ لكلّ المستخدمين. */
const val BROADCAST_TOPIC = "nebras_all_users"

/**
 * نموذج الإشعار المحفوظ — نقل `notifications/model/notification_model.dart`
 * (كان Hive typeId=2).
 */
data class NotificationModel(
    val id: String,
    val title: String,
    val body: String,
    /** 'video' | 'audio' | 'book' | 'section_created' … */
    val type: String,
    val contentId: String,
    val createdAtMs: Long,
    val isRead: Boolean = false,
    val sourceUrl: String? = null,
    val subSectionId: String? = null,
    val secondarySectionId: String? = null,
    val mainSectionId: String? = null,
    val mediaType: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("type", type)
        put("contentId", contentId)
        put("createdAtMs", createdAtMs)
        put("isRead", isRead)
        sourceUrl?.let { put("sourceUrl", it) }
        subSectionId?.let { put("subSectionId", it) }
        secondarySectionId?.let { put("secondarySectionId", it) }
        mainSectionId?.let { put("mainSectionId", it) }
        mediaType?.let { put("mediaType", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): NotificationModel = NotificationModel(
            id = json.optString("id"),
            title = json.optString("title"),
            body = json.optString("body"),
            type = json.optString("type"),
            contentId = json.optString("contentId"),
            createdAtMs = json.optLong("createdAtMs"),
            isRead = json.optBoolean("isRead", false),
            sourceUrl = json.optString("sourceUrl").takeIf { it.isNotEmpty() },
            subSectionId = json.optString("subSectionId").takeIf { it.isNotEmpty() },
            secondarySectionId = json.optString("secondarySectionId")
                .takeIf { it.isNotEmpty() },
            mainSectionId = json.optString("mainSectionId").takeIf { it.isNotEmpty() },
            mediaType = json.optString("mediaType").takeIf { it.isNotEmpty() },
        )

        /**
         * يبني نموذجاً من حمولة رسالة FCM (data-only) — نفس أولويّة الحقول
         * في `_remoteMessageToModel` بنسخة Flutter.
         */
        fun fromRemoteData(
            data: Map<String, String>,
            messageId: String?,
            sentTimeMs: Long,
        ): NotificationModel {
            fun str(key: String): String? = data[key]?.trim()?.takeIf { it.isNotEmpty() }

            val contentId = data["contentId"].orEmpty()
            val stableId = messageId
                ?: if (contentId.isNotEmpty()) {
                    "cid_$contentId"
                } else {
                    System.currentTimeMillis().toString()
                }

            return NotificationModel(
                id = stableId,
                title = str("contentTitle")
                    ?: str("content_title")
                    ?: str("itemTitle")
                    ?: str("title")
                    ?: "",
                body = str("body").orEmpty(),
                type = data["type"] ?: "video",
                contentId = contentId,
                createdAtMs = if (sentTimeMs > 0) sentTimeMs else System.currentTimeMillis(),
                sourceUrl = str("sourceUrl") ?: str("video_url") ?: str("file_url"),
                subSectionId = str("subSectionId") ?: str("sectionId"),
                secondarySectionId = str("secondarySectionId"),
                mainSectionId = str("mainSectionId"),
                mediaType = str("contentType") ?: str("content_type"),
            )
        }
    }
}

/** تفضيل تشغيل/إيقاف الإشعارات — نقل `notifications_preferences.dart`. */
object NotificationsPreferences {

    private const val KEY_PUSH_ENABLED = "notifications_push_enabled"
    private const val DEFAULT_ENABLED = true

    private lateinit var store: LocalStore

    fun init(localStore: LocalStore) {
        store = localStore
    }

    fun isPushEnabled(): Boolean = store.getBool(KEY_PUSH_ENABLED, DEFAULT_ENABLED)

    fun setPushEnabled(enabled: Boolean) = store.putBool(KEY_PUSH_ENABLED, enabled)
}

/**
 * مستودع الإشعارات المحليّ — يدمج `NotificationRepoImpl` (صندوق Hive
 * `notifications`) وإدارة الاشتراك في موضوع البثّ.
 *
 * الجوهر: اللوحة ترسل رسائل **data-only** فلا يعرضها النظام تلقائياً؛
 * التطبيق وحده يقرّر العرض، فيتوقّف التدفّق فوراً عند إيقاف المستخدم
 * للإشعارات حتى في الخلفية.
 */
class NotificationsRepository(private val store: LocalStore) {

    private companion object {
        const val TAG = "NotificationsRepo"

        /** سقف الإشعارات المحفوظة — نحتفظ بأحدث 20 فقط. */
        const val MAX_NOTIFICATIONS = 20

        /** إشعارات إنشاء الأقسام لا تُعرض في شاشة الإشعارات. */
        fun isContentNotification(n: NotificationModel): Boolean =
            n.type.trim().lowercase() != "section_created"

        val GENERIC_TITLE = Regex(
            "(محتوى جديد|نبراس|new content|nebras)",
            RegexOption.IGNORE_CASE,
        )

        val QUOTED_TITLE = Regex("[\"“«]([^\"”»]+)[\"”»]")
    }

    private val items = LinkedHashMap<String, NotificationModel>()

    private val _state = MutableStateFlow<List<NotificationModel>>(emptyList())

    /** إشعارات المحتوى مرتّبة بالأحدث أولاً، بعنوان عرض محسَّن. */
    val state: StateFlow<List<NotificationModel>> = _state.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    init {
        load()
        pruneToCap()
    }

    fun getAllNotifications(): List<NotificationModel> = _state.value

    fun saveNotification(notification: NotificationModel) {
        if (!isContentNotification(notification)) return
        if (items.containsKey(notification.id)) return
        items[notification.id] = notification
        pruneToCap()
        publishAndPersist()
    }

    fun markAsRead(id: String) {
        val item = items[id] ?: return
        if (item.isRead) return
        items[id] = item.copy(isRead = true)
        publishAndPersist()
    }

    fun markAllAsRead() {
        var changed = false
        for ((key, item) in items) {
            if (!item.isRead) {
                items[key] = item.copy(isRead = true)
                changed = true
            }
        }
        if (changed) publishAndPersist()
    }

    fun deleteNotification(id: String) {
        if (items.remove(id) != null) publishAndPersist()
    }

    fun clearAll() {
        if (items.isEmpty()) return
        items.clear()
        publishAndPersist()
    }

    // ── تشغيل/إيقاف الإشعارات (اشتراك الموضوع + الرمز) ──────────────

    suspend fun enablePushNotifications() {
        NotificationsPreferences.setPushEnabled(true)
        val messaging = FirebaseMessaging.getInstance()
        runCatching { messaging.isAutoInitEnabled = true }
        runCatching {
            messaging.subscribeToTopic(BROADCAST_TOPIC).await()
            Log.d(TAG, "Enabled — subscribed to $BROADCAST_TOPIC")
        }.onFailure { Log.d(TAG, "subscribe failed: ${it.message}") }
    }

    suspend fun disablePushNotifications() {
        NotificationsPreferences.setPushEnabled(false)
        val messaging = FirebaseMessaging.getInstance()
        runCatching { messaging.isAutoInitEnabled = false }
        runCatching {
            messaging.unsubscribeFromTopic(BROADCAST_TOPIC).await()
            Log.d(TAG, "Disabled — unsubscribed from $BROADCAST_TOPIC")
        }.onFailure { Log.d(TAG, "unsubscribe failed: ${it.message}") }
        runCatching {
            messaging.deleteToken().await()
            Log.d(TAG, "Token deleted.")
        }.onFailure { Log.d(TAG, "deleteToken failed: ${it.message}") }
    }

    /** يزامن الاشتراك مع التفضيل المحفوظ — يُستدعى عند الإقلاع. */
    suspend fun syncSubscriptionWithPreference() {
        if (NotificationsPreferences.isPushEnabled()) {
            enablePushNotifications()
        } else {
            disablePushNotifications()
        }
    }

    suspend fun getDeviceToken(): String? = runCatching {
        FirebaseMessaging.getInstance().token.await()
    }.onFailure { Log.d(TAG, "Error getting Firebase token: ${it.message}") }
        .getOrNull()

    // ── داخليّات ────────────────────────────────────────────────────

    private fun load() {
        val root = store.getJsonObject(LocalStoreKeys.BOX_NOTIFICATIONS) ?: run {
            publish()
            return
        }
        for (key in root.keys()) {
            val obj = root.optJSONObject(key) ?: continue
            runCatching { NotificationModel.fromJson(obj) }
                .onSuccess { items[key] = it }
        }
        publish()
    }

    private fun pruneToCap() {
        if (items.size <= MAX_NOTIFICATIONS) return
        val keep = items.values
            .sortedByDescending { it.createdAtMs }
            .take(MAX_NOTIFICATIONS)
            .associateBy { it.id }
        items.clear()
        items.putAll(keep)
    }

    private fun publishAndPersist() {
        publish()
        runCatching {
            val root = JSONObject()
            items.forEach { (key, item) -> root.put(key, item.toJson()) }
            store.putJsonObject(LocalStoreKeys.BOX_NOTIFICATIONS, root)
        }
    }

    private fun publish() {
        _state.value = items.values
            .filter(::isContentNotification)
            .map(::withDisplayTitle)
            .sortedByDescending { it.createdAtMs }
        _unreadCount.value = items.values
            .filter(::isContentNotification)
            .count { !it.isRead }
    }

    /**
     * عنوان العرض: إن كان العنوان عامّاً («محتوى جديد»/«نبراس») نستخرج اسم
     * المحتوى المقتبس من نصّ الرسالة ليظهر في القائمة بدل العنوان العامّ.
     */
    private fun withDisplayTitle(n: NotificationModel): NotificationModel {
        val title = n.title.trim()
        val looksGeneric = title.isEmpty() || GENERIC_TITLE.containsMatchIn(title)
        if (!looksGeneric) return n
        val extracted = titleFromBody(n.body) ?: return n
        if (extracted == title) return n
        return n.copy(title = extracted)
    }

    private fun titleFromBody(body: String): String? {
        if (body.trim().isEmpty()) return null
        return QUOTED_TITLE.find(body)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
