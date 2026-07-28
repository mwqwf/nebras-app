package com.nebras.mobile.core.model

import android.util.Log
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import com.nebras.mobile.core.data.sortedContentOldestFirst
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * صفّ خفيف من ميتادات المحتوى فقط (بديل `ContentMetadataCacheEntry` في Hive).
 * لا نخزّن ملفّات التشغيل هنا؛ الهدف أن تبقى العناوين والأوصاف والصور
 * المصغّرة متاحة عند انقطاع الشبكة.
 */
data class ContentMetadataCacheEntry(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val type: String,
    val thumbnailUrl: String,
    val sourceUrl: String? = null,
    val sizeInBytes: Int? = null,
    val section: String,
    val subSection: String? = null,
    val sectionName: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val createdBy: String,
    val cachedAtMs: Long,
) {
    fun toContent(): Content = Content(
        id = id,
        title = title,
        author = author,
        description = description,
        type = parseContentType(type),
        thumbnailUrl = thumbnailUrl,
        sourceUrl = sourceUrl,
        sizeInBytes = sizeInBytes,
        section = section,
        subSection = subSection,
        sectionName = sectionName,
        createdAt = Date(createdAtMs),
        updatedAt = Date(updatedAtMs),
        createdBy = createdBy,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("author", author)
        put("description", description)
        put("type", type)
        put("thumbnailUrl", thumbnailUrl)
        sourceUrl?.let { put("sourceUrl", it) }
        sizeInBytes?.let { put("sizeInBytes", it) }
        put("section", section)
        subSection?.let { put("subSection", it) }
        sectionName?.let { put("sectionName", it) }
        put("createdAtMs", createdAtMs)
        put("updatedAtMs", updatedAtMs)
        put("createdBy", createdBy)
        put("cachedAtMs", cachedAtMs)
    }

    companion object {
        fun fromContent(content: Content): ContentMetadataCacheEntry =
            ContentMetadataCacheEntry(
                id = content.id,
                title = content.title,
                author = content.author,
                description = content.description,
                type = content.type.name,
                thumbnailUrl = content.thumbnailUrl,
                sourceUrl = content.sourceUrl,
                sizeInBytes = content.sizeInBytes,
                section = content.section,
                subSection = content.subSection,
                sectionName = content.sectionName,
                createdAtMs = content.createdAt.time,
                updatedAtMs = content.updatedAt.time,
                createdBy = content.createdBy,
                cachedAtMs = System.currentTimeMillis(),
            )

        fun fromJson(json: JSONObject): ContentMetadataCacheEntry =
            ContentMetadataCacheEntry(
                id = json.optString("id"),
                title = json.optString("title"),
                author = json.optString("author"),
                description = json.optString("description"),
                type = json.optString("type"),
                thumbnailUrl = json.optString("thumbnailUrl"),
                sourceUrl = json.optString("sourceUrl").takeIf { it.isNotEmpty() },
                sizeInBytes = if (json.has("sizeInBytes")) json.optInt("sizeInBytes") else null,
                section = json.optString("section"),
                subSection = json.optString("subSection").takeIf { it.isNotEmpty() },
                sectionName = json.optString("sectionName").takeIf { it.isNotEmpty() },
                createdAtMs = json.optLong("createdAtMs"),
                updatedAtMs = json.optLong("updatedAtMs"),
                createdBy = json.optString("createdBy"),
                cachedAtMs = json.optLong("cachedAtMs"),
            )
    }
}

/**
 * كاش الميتادات — بديل صندوق Hive `content_metadata_cache` فوق [LocalStore].
 * الصفّ يحتفظ بالخريطة في الذاكرة ويكتبها كاملةً عند كل تعديل (حجمها
 * ميتادات نصّيّة فقط، والكتابة `apply()` غير حاجبة).
 */
class ContentMetadataCache(private val store: LocalStore) {

    private val entries = ConcurrentHashMap<String, ContentMetadataCacheEntry>()
    private val offlinePreviewIds = java.util.Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>(),
    )

    private val _revision = MutableStateFlow(0L)

    /** يتغيّر عند كل تعديل — بديل `notifyListeners()`. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val root = store.getJsonObject(LocalStoreKeys.BOX_CONTENT_METADATA_CACHE) ?: return
        for (key in root.keys()) {
            val obj = root.optJSONObject(key) ?: continue
            runCatching { ContentMetadataCacheEntry.fromJson(obj) }
                .onSuccess { entries[key] = it }
        }
    }

    private fun persist() {
        runCatching {
            val root = JSONObject()
            entries.forEach { (key, entry) -> root.put(key, entry.toJson()) }
            store.putJsonObject(LocalStoreKeys.BOX_CONTENT_METADATA_CACHE, root)
        }
        _revision.value = _revision.value + 1
    }

    fun getAll(): List<Content> =
        entries.values.map { it.toContent() }.sortedContentOldestFirst()

    fun getById(id: String): Content? = entries[id]?.toContent()

    fun isOfflinePreview(id: String): Boolean = offlinePreviewIds.contains(id)

    fun upsertAll(contents: Iterable<Content>) {
        var changed = false
        for (content in contents) {
            val id = content.id.trim()
            if (id.isEmpty()) continue
            entries[id] = ContentMetadataCacheEntry.fromContent(content)
            changed = true
        }
        if (changed) persist()
    }

    /**
     * نخزّن لقطة حيّة ونزيل عنها وسم «معاينة أوفلاين». لا نحذف كلّ الكاش هنا
     * لأنّ الصفحة قد تكون محدودة pagination؛ الواجهة الحيّة نفسها هي التي
     * تنظّف المحذوفات فور عودة الاتصال.
     */
    fun cacheLiveItems(contents: Iterable<Content>) {
        val ids = contents.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
        upsertAll(contents)
        offlinePreviewIds.removeAll(ids)
    }

    /**
     * يحذف من الكاش العناصر التي لم تَعُد في اللقطة الحيّة (محتوى حذفته
     * اللوحة) كي لا يبقى ظاهراً أوفلاين.
     *
     * 🛡️ **حارسان ضدّ اختفاء المحتوى نهائياً**: لقطة Firestore قد تصل
     * **مبتورة** (`.limit(500)`) أو **جزئيّة** لحظة انقطاع. الاعتماد عليها
     * لحذف كلّ ما ليس فيها كان يمسح الكتالوج المحفوظ بالكامل:
     *   1) لا نحذف شيئاً إذا كانت اللقطة فارغة.
     *   2) لا نحذف دفعة كبيرة (> ثلث الكاش) — مؤشّر لقطة مبتورة/جزئيّة.
     */
    fun reconcileWithLiveIds(liveIds: Iterable<String>) {
        val keep = liveIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (keep.isEmpty()) return

        val total = entries.size
        val staleKeys = entries.keys.filter { !keep.contains(it) }
        if (staleKeys.isEmpty()) return

        if (total > 0 && staleKeys.size > total / 3) {
            Log.d(
                TAG,
                "reconcile skipped — would prune ${staleKeys.size}/$total " +
                    "(suspected truncated/partial snapshot)",
            )
            return
        }

        staleKeys.forEach(entries::remove)
        persist()
    }

    fun hasMetadata(id: String): Boolean = entries.containsKey(id)

    fun has(id: String): Boolean = hasMetadata(id)

    fun cacheMany(contents: Iterable<Content>) = upsertAll(contents)

    /**
     * عند فشل الشبكة نعرض الميتادات المحفوظة فقط ونوسمها كي تُعطَّل أزرار
     * تشغيل الوسائط غير المحمّلة مع بقاء النصّ والصورة قابلَين للتصفّح.
     */
    fun offlineContents(): List<Content> {
        val contents = getAll()
        offlinePreviewIds.clear()
        offlinePreviewIds.addAll(contents.map { it.id })
        return contents
    }

    fun markOfflinePreview(content: Content): Content {
        val id = content.id.trim()
        offlinePreviewIds.add(id)
        return content.copy(
            id = if (isOfflinePreviewId(id)) id else "$OFFLINE_PREVIEW_PREFIX$id",
        )
    }

    fun offlineGroups(): List<HomeSectionCacheGroup> = groupBySection(offlineContents())

    companion object {
        private const val TAG = "ContentMetadataCache"
        private const val OFFLINE_PREVIEW_PREFIX = "offline_preview:"

        fun isOfflinePreviewId(id: String): Boolean = id.startsWith(OFFLINE_PREVIEW_PREFIX)

        fun offlineSectionsFromEntries(
            entries: Iterable<ContentMetadataCacheEntry>,
        ): List<HomeSectionCacheGroup> = groupBySection(entries.map { it.toContent() })

        private fun groupBySection(contents: List<Content>): List<HomeSectionCacheGroup> {
            val grouped = LinkedHashMap<String, MutableList<Content>>()
            for (content in contents) {
                val key = content.section.trim().ifEmpty { "offline_metadata" }
                grouped.getOrPut(key) { mutableListOf() }.add(content)
            }
            return grouped.map { (key, items) ->
                val first = items.first()
                val title = first.sectionName?.trim()?.takeIf { it.isNotEmpty() } ?: key
                HomeSectionCacheGroup(id = "offline:$key", title = title, items = items)
            }
        }
    }
}

/** يحوّل عنصراً إلى «معاينة أوفلاين» بمعرّف موسوم. */
fun Content.asOfflinePreview(): Content {
    val previewId = if (ContentMetadataCache.isOfflinePreviewId(id)) {
        id
    } else {
        "offline_preview:$id"
    }
    return copy(id = previewId)
}

object OfflineContentPolicy {
    fun canOpen(content: Content, hasLocalFile: Boolean): Boolean {
        if (!ContentMetadataCache.isOfflinePreviewId(content.id)) return true
        if (content.type != ContentType.VIDEO &&
            content.type != ContentType.YOUTUBE &&
            content.type != ContentType.AUDIO
        ) {
            return true
        }
        return hasLocalFile
    }
}

data class HomeSectionCacheGroup(
    val id: String,
    val title: String,
    val items: List<Content>,
)
