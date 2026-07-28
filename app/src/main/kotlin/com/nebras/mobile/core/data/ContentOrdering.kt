package com.nebras.mobile.core.data

import com.nebras.mobile.core.model.Content

/**
 * ترتيب عرض المحتوى داخل الأقسام (فرعية / ثانوية / رئيسية).
 *
 * **القاعدة الثابتة:** الأقدم أولاً، الأحدث في الأسفل.
 *
 * 1. [Content.createdAt] تصاعدياً (المفتاح الأساسي).
 * 2. عند تساوي التاريخ (دفعة رفع متعدد من اللوحة): [Content.selectionOrder]
 *    تصاعدياً — يعكس ترتيب اختيار الملف في الطابور.
 * 3. الطابع الزمني المضمّن في المعرّف `fb_<epoch>_…` ثم المعرّف النصي.
 */
val CONTENT_ID_EPOCH_PATTERN = Regex("^fb_(\\d{10,})")

fun contentIdEmbeddedEpoch(id: String): Long {
    val match = CONTENT_ID_EPOCH_PATTERN.find(id) ?: return 0L
    return match.groupValues[1].toLongOrNull() ?: 0L
}

/** مقارن موحّد: الأقدم أولاً (تصاعدي). */
val ContentOldestFirstComparator: Comparator<Content> = Comparator { a, b ->
    val byDate = a.createdAt.compareTo(b.createdAt)
    if (byDate != 0) return@Comparator byDate

    val aOrd = a.selectionOrder
    val bOrd = b.selectionOrder
    if (aOrd != bOrd && (aOrd > 0 || bOrd > 0)) {
        return@Comparator aOrd.compareTo(bOrd)
    }

    val byIdEpoch = contentIdEmbeddedEpoch(a.id).compareTo(contentIdEmbeddedEpoch(b.id))
    if (byIdEpoch != 0) return@Comparator byIdEpoch

    a.id.compareTo(b.id)
}

fun compareContentOldestFirst(a: Content, b: Content): Int =
    ContentOldestFirstComparator.compare(a, b)

/** يفرز قائمة المحتوى في المكان: الأقدم أولاً. */
fun MutableList<Content>.sortContentOldestFirst() {
    sortWith(ContentOldestFirstComparator)
}

/** نسخة غير مُدمِّرة تُعيد قائمة جديدة مرتَّبة: الأقدم أولاً. */
fun List<Content>.sortedContentOldestFirst(): List<Content> =
    sortedWith(ContentOldestFirstComparator)
