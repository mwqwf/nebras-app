package com.nebras.mobile.feature.home.model

import com.nebras.mobile.core.model.Content

/**
 * نوع رفّ الصفحة الرئيسيّة — **ليس** قسم Firestore.
 * الرفوف عروض مشتقّة من المحتوى المحمّل + الإشارات المحليّة.
 */
enum class HomeRailType {
    /** أحدث 12 عنصراً عبر كلّ نبراس (`createdAt` تنازلي). */
    NEWEST,

    /** الأكثر شعبية — من `aggregates_popular/top_weekly` أو `view_count`. */
    POPULAR,

    /** مقترَح لك — `RecommendationService` بعد بناء ملفّ اهتمام. */
    FOR_YOU,

    /** من كلمات بحثك/اهتماماتك — يُخفى للمستخدم الجديد. */
    SEARCH_AFFINITY,

    /** تابع المشاهدة/القراءة — `ContinueWatchingService` محلّي فقط. */
    CONTINUE_WATCHING,

    /** تصفّح الأقسام — بطاقات أقسام رئيسية مرتّبة ذكياً. */
    BROWSE_SECTIONS,
}

/** رفّ أفقيّ واحد في الصفحة الرئيسيّة. */
data class HomeRail(
    val type: HomeRailType,
    val title: String,
    val subtitle: String? = null,
    val items: List<Content> = emptyList(),
    val sectionRefs: List<HomeRailSectionRef> = emptyList(),
) {
    val isEmpty: Boolean
        get() = if (type == HomeRailType.BROWSE_SECTIONS) {
            sectionRefs.isEmpty()
        } else {
            items.isEmpty()
        }
}

/** مرجع قسم لرفّ «تصفّح الأقسام». */
data class HomeRailSectionRef(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
)
