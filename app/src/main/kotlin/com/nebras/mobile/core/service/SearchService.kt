package com.nebras.mobile.core.service

import android.util.Log
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.util.keywordOverlapScore
import com.nebras.mobile.core.util.relevanceScore
import com.nebras.mobile.core.util.substringMatches
import com.nebras.mobile.feature.home.model.HomeSection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class SearchFallbackReason { NONE, CLOSEST_SECTION, POPULAR }

data class SearchServiceResult(
    val items: List<SearchResultItem>,
    val suggestions: List<SearchResultItem> = emptyList(),
    val fallbackReason: SearchFallbackReason = SearchFallbackReason.NONE,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val isNotEmpty: Boolean get() = !isEmpty
    val usedFallback: Boolean get() = fallbackReason != SearchFallbackReason.NONE
}

/**
 * محرّك البحث الموحَّد — نقل `core/services/search_service.dart`.
 *
 * يبحث في الأقسام والمحتوى معاً (تطابق حرفيّ ثمّ ضبابيّ)، ومع خلوّ النتائج
 * يتدرّج: أقرب قسم → الشائع محليّاً. عند وجود نتائج يبني «اقتراحات» من قسم
 * النتيجة الأولى والأقسام المشابهة.
 */
class SearchService(
    /** بحث الكتالوج الكامل (Firestore) — مقابل `SearchContentUseCase`. */
    private val firebaseSearch: suspend (
        query: String,
        type: ContentType?,
        section: String?,
        subSection: String?,
    ) -> List<Content>,
) {

    private companion object {
        const val TAG = "SearchService"
    }

    suspend fun search(
        query: String,
        sections: List<HomeSection>,
        type: ContentType? = null,
        section: String? = null,
        subSection: String? = null,
        maxSections: Int = 30,
        maxContentPerSource: Int = 40,
        maxSuggestions: Int = 12,
    ): SearchServiceResult {
        val q = query.trim()
        if (q.isEmpty()) return SearchServiceResult(emptyList())

        val (sectionHits, contentHits) = coroutineScope {
            val sectionsDeferred = async { searchSections(q, sections, maxSections) }
            val contentDeferred = async {
                searchContent(q, type, section, subSection, sections)
            }
            sectionsDeferred.await() to contentDeferred.await()
        }

        val sectionExact = sectionHits.filter { !it.fuzzy }
        val sectionFuzzy = sectionHits.filter { it.fuzzy }
        val contentExact = contentHits.filter { !it.fuzzy }
        val contentFuzzy = contentHits.filter { it.fuzzy }

        // وجود تطابق حرفيّ واحد يُقصي النتائج الضبابيّة كلّها.
        val hasExact = sectionExact.isNotEmpty() || contentExact.isNotEmpty()
        val merged = if (hasExact) {
            sortByScore(sectionExact + contentExact)
        } else {
            sortByScore(sectionFuzzy + contentFuzzy)
        }
        val primaryItems = dedup(merged)

        var fallbackReason = SearchFallbackReason.NONE
        var finalItems = primaryItems
        if (primaryItems.isEmpty()) {
            val closest = closestSectionFallback(q, sections)
            if (closest.isNotEmpty()) {
                finalItems = closest
                fallbackReason = SearchFallbackReason.CLOSEST_SECTION
            } else {
                val popular = popularFallback(sections, maxItems = 20)
                if (popular.isNotEmpty()) {
                    finalItems = popular
                    fallbackReason = SearchFallbackReason.POPULAR
                }
            }
        }

        val suggestions = if (fallbackReason == SearchFallbackReason.NONE) {
            buildSuggestions(q, primaryItems, sections, maxSuggestions)
        } else {
            emptyList()
        }

        return SearchServiceResult(finalItems, suggestions, fallbackReason)
    }

    private fun searchSections(
        query: String,
        sections: List<HomeSection>,
        maxSections: Int,
    ): List<SearchResultItem> {
        val out = mutableListOf<SearchResultItem>()
        val seenIds = mutableSetOf<String>()
        for (section in sections) {
            if (!seenIds.add(section.id)) continue
            if (section.items.isEmpty()) continue
            val hay = sectionHaystack(section)
            if (hay.isEmpty()) continue
            val exact = substringMatches(hay, query)
            val score = if (exact) 0.95 else relevanceScore(query, hay)
            if (!exact && score < 0.20) continue
            out.add(
                SearchResultItem.section(
                    section = section,
                    source = SearchItemSource.FIREBASE,
                    score = score,
                    fuzzy = !exact,
                ),
            )
        }
        return sortByScore(out).take(maxSections)
    }

    private suspend fun searchContent(
        query: String,
        type: ContentType?,
        section: String?,
        subSection: String?,
        localSections: List<HomeSection>,
    ): List<SearchResultItem> {
        val firebaseItems = searchFirebase(query, type, section, subSection)

        // المحتوى المحمَّل محليّاً في الأقسام يُبحث فيه أيضاً — مع قسم أبيه
        // كسياق للـ haystack.
        val localContent = localSections.flatMap { sec ->
            sec.items.map { SourceContent(it, sec) }
        }

        val out = mutableListOf<SearchResultItem>()
        val seen = mutableSetOf<String>()
        for (source in firebaseItems + localContent) {
            if (!seen.add(source.content.id)) continue
            val hay = contentHaystack(source.content, source.parentSection)
            val exact = substringMatches(hay, query)
            val score = if (exact) 0.95 else relevanceScore(query, hay)
            if (!exact && score < 0.20) continue
            out.add(
                SearchResultItem.item(
                    content = source.content,
                    source = SearchItemSource.FIREBASE,
                    score = score,
                    fuzzy = !exact,
                ),
            )
        }
        return out
    }

    private fun sectionHaystack(section: HomeSection): String {
        val title = section.title.trim()
        val type = section.type.trim()
        if (title.isEmpty()) return type
        if (type.isEmpty()) return title
        return "$title $type"
    }

    private fun contentHaystack(c: Content, parent: HomeSection?): String {
        val parts = buildList {
            add(c.title)
            add(c.description)
            add(c.author)
            c.sectionName?.takeIf { it.isNotEmpty() }?.let(::add)
            c.subSection?.takeIf { it.isNotEmpty() }?.let(::add)
            parent?.title?.takeIf { it.isNotEmpty() }?.let(::add)
        }
        return parts.filter { it.trim().isNotEmpty() }.joinToString(" ")
    }

    /** بديل «لم نجد شيئاً»: أقرب 3 أقسام صلةً بعنوان البحث + عيّناتها. */
    private fun closestSectionFallback(
        query: String,
        sections: List<HomeSection>,
    ): List<SearchResultItem> {
        if (sections.isEmpty()) return emptyList()
        val scored = sections
            .filter { it.items.isNotEmpty() }
            .mapNotNull { sec ->
                val hay = sectionHaystack(sec)
                if (hay.isEmpty()) return@mapNotNull null
                val s = relevanceScore(query, hay)
                if (s <= 0.0) null else sec to s
            }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return emptyList()

        val out = mutableListOf<SearchResultItem>()
        val seen = mutableSetOf<String>()
        for ((sec, score) in scored.take(3)) {
            out.add(
                SearchResultItem.section(
                    section = sec,
                    source = SearchItemSource.FIREBASE,
                    score = score,
                    fuzzy = true,
                ),
            )
            val items = sec.items.sortedBy { it.createdAt }
            for (item in items.take(6)) {
                val key = "item:${SearchItemSource.FIREBASE}:${item.id}"
                if (!seen.add(key)) continue
                out.add(
                    SearchResultItem.item(
                        content = item,
                        source = SearchItemSource.FIREBASE,
                        score = score * 0.9,
                        fuzzy = true,
                    ),
                )
            }
        }
        return out
    }

    /** بديل أخير: عيّنات من الأقسام الأكثر زيارة، ثمّ الأحدث عموماً. */
    private fun popularFallback(
        sections: List<HomeSection>,
        maxItems: Int = 20,
    ): List<SearchResultItem> {
        if (sections.isEmpty()) return emptyList()
        val byId = sections.associateBy { it.id }
        val out = mutableListOf<SearchResultItem>()
        val seen = mutableSetOf<String>()

        for (id in UserBehaviorTracker.topSectionIds(12)) {
            val sec = byId[id] ?: continue
            val items = sec.items.sortedByDescending { it.createdAt }
            for (item in items.take(4)) {
                val key = "item:${SearchItemSource.FIREBASE}:${item.id}"
                if (!seen.add(key)) continue
                out.add(
                    SearchResultItem.item(
                        content = item,
                        source = SearchItemSource.FIREBASE,
                        score = 0.1,
                        fuzzy = true,
                    ),
                )
                if (out.size >= maxItems) return out
            }
        }

        if (out.isEmpty()) {
            val allItems = sections.flatMap { it.items }.sortedByDescending { it.createdAt }
            for (item in allItems.take(maxItems)) {
                out.add(
                    SearchResultItem.item(
                        content = item,
                        source = SearchItemSource.FIREBASE,
                        score = 0.05,
                        fuzzy = true,
                    ),
                )
            }
        }
        return out
    }

    /**
     * اقتراحات «قد يهمّك»: بقيّة قسم النتيجة الأولى، ثمّ الأقسام المشابهة،
     * ثمّ إكمال من الشائع.
     */
    private fun buildSuggestions(
        query: String,
        primary: List<SearchResultItem>,
        sections: List<HomeSection>,
        maxSuggestions: Int,
    ): List<SearchResultItem> {
        if (primary.isEmpty() || sections.isEmpty() || maxSuggestions <= 0) {
            return emptyList()
        }
        val seenKeys = primary.mapTo(mutableSetOf()) { it.dedupKey }
        val out = mutableListOf<SearchResultItem>()
        val byId = sections.associateBy { it.id }

        val top = primary.first()
        val anchorSection: HomeSection? = when {
            top.type == SearchItemType.SECTION && top.section != null -> top.section
            top.content != null -> byId[top.content.section]
            else -> null
        }

        if (anchorSection != null) {
            val items = anchorSection.items.sortedBy { it.createdAt }
            for (item in items) {
                val candidate = SearchResultItem.item(
                    content = item,
                    source = SearchItemSource.FIREBASE,
                    score = 0.6,
                    fuzzy = true,
                )
                if (seenKeys.add(candidate.dedupKey)) {
                    out.add(candidate)
                    if (out.size >= maxSuggestions) return out
                }
            }
        }

        val similar = sections
            .filter { it.id != anchorSection?.id && it.items.isNotEmpty() }
            .mapNotNull { sec ->
                val overlap = keywordOverlapScore(query, sectionHaystack(sec))
                val score = if (overlap > 0) overlap else relevanceScore(query, sec.title)
                if (score <= 0.0) null else sec to score
            }
            .sortedByDescending { it.second }

        for ((sec, score) in similar.take(5)) {
            val items = sec.items.sortedBy { it.createdAt }
            for (item in items.take(3)) {
                val candidate = SearchResultItem.item(
                    content = item,
                    source = SearchItemSource.FIREBASE,
                    score = 0.4 * score,
                    fuzzy = true,
                )
                if (seenKeys.add(candidate.dedupKey)) {
                    out.add(candidate)
                    if (out.size >= maxSuggestions) return out
                }
            }
        }

        if (out.size < maxSuggestions) {
            for (p in popularFallback(sections, maxSuggestions - out.size)) {
                if (seenKeys.add(p.dedupKey)) {
                    out.add(p)
                    if (out.size >= maxSuggestions) break
                }
            }
        }
        return out
    }

    private suspend fun searchFirebase(
        query: String,
        type: ContentType?,
        section: String?,
        subSection: String?,
    ): List<SourceContent> = runCatching {
        firebaseSearch(query, type, section, subSection)
            .map { SourceContent(it, null) }
    }.getOrElse {
        Log.d(TAG, "firebase error: ${it.message}")
        emptyList()
    }

    private fun sortByScore(input: List<SearchResultItem>): List<SearchResultItem> =
        input.sortedWith(
            compareByDescending<SearchResultItem> { it.score }
                // الأقسام قبل المحتوى عند تساوي الدرجة.
                .thenBy { if (it.type == SearchItemType.SECTION) 0 else 1 }
                .thenBy { it.title },
        )

    private fun dedup(input: List<SearchResultItem>): List<SearchResultItem> {
        val out = mutableListOf<SearchResultItem>()
        val seen = mutableSetOf<String>()
        for (item in input) {
            if (seen.add(item.dedupKey)) out.add(item)
        }
        return out
    }

    private data class SourceContent(
        val content: Content,
        val parentSection: HomeSection?,
    )
}
