package com.nebras.mobile.core.service

import com.nebras.mobile.core.model.Content
import com.nebras.mobile.feature.home.model.HomeSection

enum class SearchHitKind { SECTION, CONTENT }

/** نتيجة بحث موحَّدة (قسم أو محتوى) — نقل `core/services/search_hit.dart`. */
data class SearchHit private constructor(
    val kind: SearchHitKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val score: Double,
    val sourcePriority: Int,
    val section: HomeSection? = null,
    val content: Content? = null,
) {
    val dedupKey: String get() = "${kind.wire}:$id"

    val rankingScore: Double get() = score * 10 + sourcePriority

    companion object {
        fun fromSection(
            section: HomeSection,
            score: Double,
            subtitleOverride: String? = null,
        ): SearchHit = SearchHit(
            kind = SearchHitKind.SECTION,
            id = section.id,
            title = section.title,
            subtitle = subtitleOverride ?: sectionSubtitle(section),
            imageUrl = section.imageUrl,
            score = score,
            sourcePriority = sectionPriority(section),
            section = section,
        )

        fun fromContent(
            content: Content,
            score: Double,
            sourcePriority: Int = 0,
        ): SearchHit = SearchHit(
            kind = SearchHitKind.CONTENT,
            id = content.id,
            title = content.title,
            subtitle = contentSubtitle(content),
            imageUrl = content.thumbnailUrl.ifEmpty { null },
            score = score,
            sourcePriority = sourcePriority,
            content = content,
        )

        private fun sectionSubtitle(s: HomeSection): String = when (s.type) {
            "main_section" -> "Main section"
            "sub_section" -> "Sub section"
            "secondary_section" -> "Secondary section"
            else -> s.type
        }

        private fun contentSubtitle(c: Content): String {
            val sectionName = c.sectionName?.trim()
            if (!sectionName.isNullOrEmpty()) return sectionName
            if (c.author.trim().isNotEmpty()) return c.author.trim()
            return c.type.wire
        }

        private fun sectionPriority(s: HomeSection): Int = when (s.type) {
            "main_section" -> 5
            "sub_section" -> 4
            "secondary_section" -> 3
            else -> 2
        }
    }
}

/** الاسم المخزَّن/المعروض — يطابق `enum.name` في Dart (حروف صغيرة). */
val SearchHitKind.wire: String get() = name.lowercase()

val com.nebras.mobile.core.model.ContentType.wire: String get() = name.lowercase()
