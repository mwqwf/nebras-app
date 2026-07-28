package com.nebras.mobile.feature.search

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nebras.mobile.core.data.ContentCompliance
import com.nebras.mobile.core.data.RtdbUploadNormalizer
import com.nebras.mobile.core.data.sortedContentOldestFirst
import com.nebras.mobile.core.error.ErrorHandler
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentMetadataCache
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.feature.home.data.toException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** خيار قسم في فلتر البحث. */
data class SearchSectionOption(val id: String, val name: String)

/**
 * مصدر بيانات البحث — نقل `features/search/data/search_datasource.dart`.
 *
 * البحث يقرأ **الكتالوج كاملاً** (بخلاف الرئيسية المحدودة بأحدث 300) عبر
 * ترقيم صفحات بمؤشّر، مع كاش 90 ثانية وكتالوج محلّيّ فوريّ من الميتادات.
 */
class SearchDatasource(
    private val firestore: FirebaseFirestore,
    private val metadataCache: ContentMetadataCache? = null,
) {

    companion object {
        private const val TAG = "SearchDatasource"

        private const val PATH_SECTIONS = "sections_unified"
        private const val PATH_CONTENT_FILES = "content_unified_files"
        private const val PATH_COMMUNITY_CONTENT = "ugc_contents"

        /** سقف مسح الكتالوج + حجم الصفحة الواحدة. */
        private const val SEARCH_SCAN_LIMIT = 4000
        private const val CATALOG_PAGE_SIZE = 300L

        private const val CATALOG_TTL_MILLIS = 90_000L
        private const val FETCH_TIMEOUT_MILLIS = 30_000L

        // الكاش ثابت على مستوى العمليّة (مثل static في Dart) — البحث
        // والشاشات الفرعيّة تتشاركه.
        @Volatile
        private var catalogCache: List<Content>? = null

        @Volatile
        private var catalogCachedAt: Long? = null

        @Volatile
        private var communityCatalogCache: List<Content>? = null

        @Volatile
        private var communityCatalogCachedAt: Long? = null

        @Volatile
        private var isRefreshing = false

        @Volatile
        private var mainToSubIdsCache: Map<String, Set<String>>? = null

        private fun isPlayableAndCompliant(item: Content): Boolean {
            if (!ContentCompliance.isRightsCompliant(item)) return false
            if (HiddenContentService.isHidden(item.id)) return false
            return true
        }

        private fun firstNonEmpty(vararg values: String?): String {
            for (value in values) {
                val normalized = (value ?: "").trim()
                if (normalized.isNotEmpty()) return normalized
            }
            return ""
        }
    }

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun search(
        query: String,
        type: ContentType? = null,
        section: String? = null,
        subSection: String? = null,
    ): List<Content> = try {
        val items = loadAllContent()
        val normalizedQuery = query.trim().lowercase()

        val acceptedSectionIds: Set<String>? =
            section?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveSectionFilterIds(it) }

        val filtered = items.filter { item ->
            if (normalizedQuery.isNotEmpty()) {
                val inTitle = item.title.lowercase().contains(normalizedQuery)
                val inDesc = item.description.lowercase().contains(normalizedQuery)
                val inAuthor = item.author.lowercase().contains(normalizedQuery)
                val inSectionName = item.sectionName.orEmpty().lowercase()
                    .contains(normalizedQuery)
                if (!inTitle && !inDesc && !inAuthor && !inSectionName) return@filter false
            }
            if (type != null && item.type != type) return@filter false
            if (acceptedSectionIds != null && !acceptedSectionIds.contains(item.section)) {
                return@filter false
            }
            if (!subSection.isNullOrEmpty() && item.subSection != subSection) {
                return@filter false
            }
            true
        }
        Log.d(TAG, "firestore search results: ${filtered.size}")
        filtered
    } catch (e: Throwable) {
        throw ErrorHandler.handleException(e).toException()
    }

    /** فلتر القسم الرئيسيّ يشمل كلّ أقسامه الفرعيّة. */
    private suspend fun resolveSectionFilterIds(selectedId: String): Set<String> {
        val map = loadMainToSubIds()
        val subs = map[selectedId]
        return if (!subs.isNullOrEmpty()) setOf(selectedId) + subs else setOf(selectedId)
    }

    private suspend fun loadMainToSubIds(): Map<String, Set<String>> {
        mainToSubIdsCache?.let { return it }
        val map = LinkedHashMap<String, MutableSet<String>>()

        fun addSub(keyHint: String?, value: Any?) {
            if (value is List<*>) {
                value.forEachIndexed { i, e ->
                    addSub(if (keyHint != null) "$keyHint:$i" else "$i", e)
                }
                return
            }
            val record = asMap(value)
            if (record.isEmpty()) return
            val subId = firstNonEmpty(
                record["id"]?.toString(),
                record["slug"]?.toString(),
                keyHint,
            )
            val mainId = firstNonEmpty(
                record["main_section"]?.toString(),
                record["main_section_id"]?.toString(),
                record["main_section_name"]?.toString(),
            )
            if (subId.isEmpty() || mainId.isEmpty()) return
            map.getOrPut(mainId) { mutableSetOf() }.add(subId)
        }

        runCatching {
            val subDoc = firestore.collection(PATH_SECTIONS).document("sub").get().await()
            subDoc.data?.forEach { (k, v) -> addSub(k, v) }
        }.onFailure { Log.d(TAG, "main→sub map load failed: ${it.message}") }

        val result: Map<String, Set<String>> = map
        mainToSubIdsCache = result
        return result
    }

    suspend fun getSections(): List<SearchSectionOption> = try {
        val rows = mutableListOf<SearchSectionOption>()

        fun addMainOption(keyHint: String?, value: Any?) {
            if (value is List<*>) {
                value.forEachIndexed { i, e ->
                    addMainOption(if (keyHint != null) "$keyHint:$i" else "$i", e)
                }
                return
            }
            val map = asMap(value)
            // الأقسام المخفيّة صراحةً (`is_listed=false`) لا تظهر في الفلتر.
            val listed = map["is_listed"]
            if (listed != null) {
                val asBool = when (listed) {
                    is Boolean -> listed
                    else -> listed.toString().lowercase() != "false" &&
                        listed.toString() != "0"
                }
                if (!asBool) return
            }
            val id = firstNonEmpty(
                map["id"]?.toString(),
                map["slug"]?.toString(),
                keyHint,
            )
            val name = firstNonEmpty(
                map["name"]?.toString(),
                map["title"]?.toString(),
                id,
            )
            if (id.isNotEmpty()) rows.add(SearchSectionOption(id, name))
        }

        val mainDoc = firestore.collection(PATH_SECTIONS).document("main").get().await()
        mainDoc.data?.forEach { (k, v) -> addMainOption(k, v) }

        if (rows.isEmpty()) {
            val sectionsRoot = readSectionsRoot()
            sectionsRoot.forEach { (key, value) ->
                if (key == "main" || key == "sub" || key == "secondary") return@forEach
                addMainOption(key, value)
            }
        }
        rows
    } catch (e: Throwable) {
        throw ErrorHandler.handleException(e).toException()
    }

    suspend fun getSectionContent(sectionId: String): List<Content> = try {
        val items = loadAllContent()
        val filtered = items.filter { it.section == sectionId }.sortedContentOldestFirst()
        Log.d(TAG, "firestore section \"$sectionId\": ${filtered.size} items")
        filtered
    } catch (e: Throwable) {
        throw ErrorHandler.handleException(e).toException()
    }

    // ── الكتالوج ────────────────────────────────────────────────────

    private suspend fun loadAllContent(): List<Content> {
        val now = System.currentTimeMillis()
        val cached = catalogCache
        val cachedAt = catalogCachedAt
        if (cached != null && cachedAt != null && now - cachedAt < CATALOG_TTL_MILLIS) {
            return cached
        }

        // كتالوج محلّيّ فوريّ من الميتادات المحفوظة + تحديث شبكيّ بالخلفيّة.
        val local = localCatalog()
        if (local.isNotEmpty()) {
            if (!isRefreshing) {
                isRefreshing = true
                catalogCachedAt = now // امنع إطلاق تحديثات متزامنة.
                refreshScope.launch {
                    runCatching { fetchAndCacheCatalog() }
                    isRefreshing = false
                }
            }
            val community = loadCommunityCatalog()
            val merged = mergeCatalogs(local, community)
            catalogCache = merged
            return merged
        }

        return fetchAndCacheCatalog()
    }

    private fun localCatalog(): List<Content> {
        val all = metadataCache?.getAll() ?: return emptyList()
        if (all.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return all.filter(::isPlayableAndCompliant).filter { seen.add(it.id) }
    }

    private suspend fun fetchAndCacheCatalog(): List<Content> {
        val idMap = LinkedHashMap<String, Any?>()
        var cursor: DocumentSnapshot? = null

        // ترقيم بمؤشّر حتى سقف المسح — أيّ فشل جزئيّ يستعمل ما جُمِع.
        while (idMap.size < SEARCH_SCAN_LIMIT) {
            var query: Query = firestore.collection(PATH_CONTENT_FILES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(CATALOG_PAGE_SIZE)
            cursor?.let { query = query.startAfter(it) }

            val page: QuerySnapshot = try {
                withTimeout(FETCH_TIMEOUT_MILLIS) { query.get().await() }
            } catch (e: Throwable) {
                Log.d(TAG, "catalog page fetch failed at ${idMap.size}: ${e.message}")
                break
            }
            if (page.isEmpty) break
            for (doc in page.documents) idMap[doc.id] = doc.data
            cursor = page.documents.last()
            if (page.documents.size < CATALOG_PAGE_SIZE) break
        }

        if (idMap.isEmpty()) return catalogCache ?: emptyList()

        val items = withContext(Dispatchers.Default) {
            val contentRoot = RtdbUploadNormalizer.combineDatabaseRoots(
                mapOf("content_unified_files" to idMap),
            )
            val rows = parseAndNormalizeRows(contentRoot)
            val seenIds = mutableSetOf<String>()
            rows.map { Content.fromJson(it + ("id" to (it["id"]?.toString() ?: ""))) }
                .filter(::isPlayableAndCompliant)
                .filter { seenIds.add(it.id) }
        }

        val communityItems = loadCommunityCatalog()
        val merged = mergeCatalogs(items, communityItems)
        catalogCache = merged
        catalogCachedAt = System.currentTimeMillis()
        Log.d(
            TAG,
            "catalog refreshed (paged x$CATALOG_PAGE_SIZE): " +
                "${idMap.size} docs → ${merged.size} playable items",
        )
        metadataCache?.cacheLiveItems(items)
        return merged
    }

    private fun mergeCatalogs(
        official: Iterable<Content>,
        community: Iterable<Content>,
    ): List<Content> {
        val seen = mutableSetOf<String>()
        return (official + community)
            .filter(::isPlayableAndCompliant)
            .filter { seen.add(it.id) }
    }

    /** منشورات المجتمع المنشورة من قنوات نشطة — تُدمج في نتائج البحث. */
    private suspend fun loadCommunityCatalog(): List<Content> {
        val now = System.currentTimeMillis()
        val cached = communityCatalogCache
        val cachedAt = communityCatalogCachedAt
        if (cached != null && cachedAt != null && now - cachedAt < CATALOG_TTL_MILLIS) {
            return cached
        }

        return runCatching {
            val snap = withTimeout(FETCH_TIMEOUT_MILLIS) {
                firestore.collection(PATH_COMMUNITY_CONTENT)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .limit(1000)
                    .get()
                    .await()
            }
            val seen = mutableSetOf<String>()
            val items = snap.documents
                .filter { doc ->
                    val d = doc.data ?: return@filter false
                    (d["status"] ?: "published") == "published" &&
                        (d["channelStatus"] ?: "active") == "active"
                }
                .map { doc ->
                    Content.fromJson((doc.data ?: emptyMap()) + ("id" to doc.id))
                }
                .filter(::isPlayableAndCompliant)
                .filter { seen.add(it.id) }
            communityCatalogCache = items
            communityCatalogCachedAt = now
            items
        }.getOrElse {
            Log.d(TAG, "community catalog load failed: ${it.message}")
            communityCatalogCache ?: emptyList()
        }
    }

    private suspend fun readSectionsRoot(): Map<String, Any?> {
        val snapshot = firestore.collection(PATH_SECTIONS).get().await()
        val map = LinkedHashMap<String, Any?>()
        for (doc in snapshot.documents) map[doc.id] = doc.data
        return map
    }

    private fun parseAndNormalizeRows(root: Map<String, Any?>): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()

        fun walk(value: Any?, keyHint: String? = null) {
            when (value) {
                is Map<*, *> -> {
                    val map = asMap(value)
                    if (RtdbUploadNormalizer.shouldTreatAsContentNode(map, keyHint)) {
                        rows.add(
                            mapOf("id" to firstNonEmpty(map["id"]?.toString(), keyHint ?: "")) +
                                map,
                        )
                    }
                    map.forEach { (k, v) -> walk(v, k) }
                }
                is List<*> -> value.forEachIndexed { i, e -> walk(e, "$i") }
            }
        }

        walk(root)
        return rows.map(RtdbUploadNormalizer::normalizeRow)
    }

    private fun asMap(value: Any?): Map<String, Any?> = when (value) {
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        else -> emptyMap()
    }
}
