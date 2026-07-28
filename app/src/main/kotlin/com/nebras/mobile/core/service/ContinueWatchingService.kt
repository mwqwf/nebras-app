package com.nebras.mobile.core.service

import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.model.ContentType
import org.json.JSONObject
import kotlin.math.min

/**
 * تقدّم غير مكتمل — **محليّ فقط** (لا يُرفع إلى Firebase).
 *
 * يُغذّي رفّ «تابع التصفّح» في الصفحة الرئيسيّة.
 * يُخفى العنصر إذا اكتمل (>90%) أو مرّ عليه >14 يوماً.
 */
object ContinueWatchingService {

    private const val KEY_ENTRIES = "continue_watching_v1"
    private const val MAX_ENTRIES = 24
    private const val STALE_AFTER_MILLIS = 14L * 24 * 60 * 60 * 1000

    private lateinit var store: LocalStore

    fun init(localStore: LocalStore) {
        store = localStore
    }

    fun saveProgress(
        contentId: String,
        positionMs: Long,
        durationMs: Long,
        title: String,
        thumbnailUrl: String,
        type: ContentType,
    ) {
        if (contentId.trim().isEmpty() || durationMs <= 0) return
        val ratio = positionMs.toDouble() / durationMs
        if (ratio >= 0.9) {
            remove(contentId)
            return
        }
        if (ratio < 0.02) return

        val map = readMap()
        map.put(
            contentId,
            JSONObject().apply {
                put("contentId", contentId)
                put("positionMs", positionMs)
                put("durationMs", durationMs)
                put("title", title)
                put("thumbnailUrl", thumbnailUrl)
                put("type", type.name)
                put("savedAt", System.currentTimeMillis())
            },
        )

        // نُبقي الأحدث حفظاً فقط ضمن السقف.
        val sorted = map.keys().asSequence().toList()
            .mapNotNull { key -> map.optJSONObject(key)?.let { key to it } }
            .sortedByDescending { it.second.optLong("savedAt", 0L) }
            .take(MAX_ENTRIES)

        val trimmed = JSONObject()
        sorted.forEach { (key, value) -> trimmed.put(key, value) }
        store.putJsonObject(KEY_ENTRIES, trimmed)
    }

    fun remove(contentId: String) {
        val map = readMap()
        if (map.has(contentId)) {
            map.remove(contentId)
            store.putJsonObject(KEY_ENTRIES, map)
        }
    }

    /** مسح كامل — تسجيل الخروج أو «نسيان اهتماماتي». */
    fun clearAll() = store.remove(KEY_ENTRIES)

    fun activeEntries(): List<ContinueWatchingEntry> {
        val map = readMap()
        val now = System.currentTimeMillis()
        val out = mutableListOf<ContinueWatchingEntry>()
        val staleKeys = mutableListOf<String>()

        for (id in map.keys().asSequence().toList()) {
            val raw = map.optJSONObject(id) ?: run { staleKeys.add(id); continue }
            val savedAt = raw.optLong("savedAt", 0L)
            if (savedAt <= 0L || now - savedAt > STALE_AFTER_MILLIS) {
                staleKeys.add(id)
                continue
            }
            val durationMs = raw.optLong("durationMs", 0L)
            val positionMs = raw.optLong("positionMs", 0L)
            if (durationMs <= 0) {
                staleKeys.add(id)
                continue
            }
            if (positionMs.toDouble() / durationMs >= 0.9) {
                staleKeys.add(id)
                continue
            }
            out.add(
                ContinueWatchingEntry(
                    contentId = id,
                    title = raw.optString("title"),
                    thumbnailUrl = raw.optString("thumbnailUrl"),
                    type = parseType(raw.optString("type")),
                    positionMs = positionMs,
                    durationMs = durationMs,
                    savedAt = savedAt,
                ),
            )
        }

        if (staleKeys.isNotEmpty()) {
            staleKeys.forEach(map::remove)
            store.putJsonObject(KEY_ENTRIES, map)
        }

        return out.sortedByDescending { it.savedAt }
    }

    private fun readMap(): JSONObject = store.getJsonObject(KEY_ENTRIES) ?: JSONObject()

    private fun parseType(name: String): ContentType =
        ContentType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: ContentType.VIDEO
}

data class ContinueWatchingEntry(
    val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val type: ContentType,
    val positionMs: Long,
    val durationMs: Long,
    val savedAt: Long,
) {
    val progressRatio: Float
        get() = if (durationMs > 0) {
            min(1.0, positionMs.toDouble() / durationMs).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }
}
