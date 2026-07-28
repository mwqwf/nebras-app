package com.nebras.mobile.core.service

import com.nebras.mobile.core.model.Content
import com.nebras.mobile.feature.home.model.HomeSection

enum class SearchItemType { SECTION, ITEM }

enum class SearchItemSource { FIREBASE }

/** نقل `core/services/search_result_item.dart`. */
data class SearchResultItem private constructor(
    val id: String,
    val title: String,
    val type: SearchItemType,
    val source: SearchItemSource,
    val subtitle: String,
    val imageUrl: String?,
    val score: Double,
    val fuzzy: Boolean,
    val section: HomeSection? = null,
    val content: Content? = null,
) {
    val dedupKey: String
        get() = "${type.name.lowercase()}:${source.name.lowercase()}:$id"

    companion object {
        fun section(
            section: HomeSection,
            source: SearchItemSource,
            score: Double,
            fuzzy: Boolean,
        ): SearchResultItem = SearchResultItem(
            id = section.id,
            title = section.title,
            type = SearchItemType.SECTION,
            source = source,
            subtitle = section.type,
            imageUrl = section.imageUrl,
            score = score,
            fuzzy = fuzzy,
            section = section,
        )

        fun item(
            content: Content,
            source: SearchItemSource,
            score: Double,
            fuzzy: Boolean,
        ): SearchResultItem = SearchResultItem(
            id = content.id,
            title = content.title,
            type = SearchItemType.ITEM,
            source = source,
            subtitle = contentSubtitle(content),
            imageUrl = content.thumbnailUrl.trim().ifEmpty { null },
            score = score,
            fuzzy = fuzzy,
            content = content,
        )

        private fun contentSubtitle(content: Content): String {
            val name = content.sectionName?.trim()
            if (!name.isNullOrEmpty()) return name
            if (content.author.trim().isNotEmpty()) return content.author.trim()
            return content.type.wire
        }
    }
}
