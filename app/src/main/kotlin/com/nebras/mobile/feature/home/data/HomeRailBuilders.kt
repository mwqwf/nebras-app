package com.nebras.mobile.feature.home.data

import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.ContinueWatchingService
import com.nebras.mobile.core.service.InterestProfileService
import com.nebras.mobile.core.service.RecommendationService
import com.nebras.mobile.core.util.extractKeywords
import com.nebras.mobile.feature.home.model.HomeRail
import com.nebras.mobile.feature.home.model.HomeRailType
import com.nebras.mobile.feature.home.model.HomeSection

/**
 * يبني رفوف الصفحة الرئيسيّة من المحتوى والأقسام المحمّلة.
 *
 * **مهم:** ترتيب العناصر *داخل* أيّ قسم Firestore يبقى «الأقدم أولاً» عبر
 * `ContentOrdering` — الرفوف هنا تستخدم `createdAt` تنازلياً أو درجات
 * الشعبيّة فقط لعرض الاكتشاف في الصفحة الرئيسيّة.
 */
object HomeRailBuilders {

    private const val RAIL_LIMIT = 12

    fun allContentFlat(sections: Iterable<HomeSection>): List<Content> {
        val byId = LinkedHashMap<String, Content>()
        for (s in sections) {
            for (item in s.items) byId[item.id] = item
        }
        return byId.values.toList()
    }

    /**
     * تضفير حسب النوع (round-robin): يأخذ قائمة **مرتّبة مسبقاً** بأيّ معيار
     * (زمنيّ/شعبيّة/أفنيتي) ويُعيد توزيع المقاعد بين الأنواع بالتناوب مع
     * الحفاظ التامّ على الترتيب النسبيّ داخل كلّ نوع.
     *
     * لماذا: الرفوف كانت تستعمل معياراً أحاديّاً ثمّ قصّاً، فإن غلب نوعٌ على
     * رأس القائمة (مثلاً الكتب لأنّها الأكثر عدداً) اكتسح الرفّ كلّه.
     * التضفير يضمن ظهور بقيّة الأنواع دون كسر معيار الترتيب. إن لم يوجد
     * سوى نوع واحد يبقى الرفّ كما هو (تدهور سلس) — لا نُفرّغه أبداً.
     *
     * دالّة نقيّة حتميّة (بلا عشوائيّة/حالة) كي تبقى الاختبارات مستقرّة.
     */
    private fun interleaveByType(ranked: List<Content>, limit: Int): List<Content> {
        if (ranked.size <= 1) return ranked.take(limit)

        // طوابير لكلّ نوع تحافظ على ترتيب الإدخال (المعيار الأصليّ).
        val queues = LinkedHashMap<ContentType, MutableList<Content>>()
        val typeOrder = mutableListOf<ContentType>() // ترتيب أوّل ظهور (حتميّ).
        for (c in ranked) {
            queues.getOrPut(c.type) { mutableListOf() }.add(c)
            if (!typeOrder.contains(c.type)) typeOrder.add(c.type)
        }
        // نوع واحد فقط → لا فائدة من التضفير، أعِد الترتيب الأصليّ.
        if (typeOrder.size <= 1) return ranked.take(limit)

        val out = mutableListOf<Content>()
        val cursors = typeOrder.associateWith { 0 }.toMutableMap()
        var exhausted = false
        while (out.size < limit && !exhausted) {
            exhausted = true
            for (t in typeOrder) {
                val q = queues.getValue(t)
                val i = cursors.getValue(t)
                if (i < q.size) {
                    out.add(q[i])
                    cursors[t] = i + 1
                    exhausted = false
                    if (out.size >= limit) break
                }
            }
        }
        return out
    }

    fun buildNewestRail(pool: List<Content>, limit: Int = RAIL_LIMIT): List<Content> {
        // الأحدث يحكم الاختيار داخل كلّ نوع، والتضفير يمنع أن تكون الـ12 كتباً.
        val sorted = pool.sortedByDescending { it.createdAt }
        return interleaveByType(sorted, limit)
    }

    fun buildPopularRail(
        pool: List<Content>,
        popularIds: List<String> = emptyList(),
        limit: Int = RAIL_LIMIT,
    ): List<Content> {
        if (popularIds.isNotEmpty()) {
            val byId = pool.associateBy { it.id }
            val ordered = popularIds.mapNotNull { byId[it] }
            // نُضفّر مع الحفاظ على رتبة الشعبيّة داخل كلّ نوع.
            if (ordered.isNotEmpty()) return interleaveByType(ordered, limit)
        }
        val sorted = pool.sortedByDescending { it.viewCount }
        return interleaveByType(sorted, limit)
    }

    fun buildForYouRail(
        pool: List<Content>,
        excludeIds: Set<String> = emptySet(),
        limit: Int = RAIL_LIMIT,
    ): List<Content> {
        val filtered = pool.filterNot { excludeIds.contains(it.id) }
        val ranked = RecommendationService.rank(filtered, discoveryPercent = 15)
        // نُبقي ترتيب الأفنيتي لكن نكسر احتكار نوعٍ واحد للرفّ على مستوى
        // العرض — دون تعطيل التعلّم.
        return interleaveByType(ranked, limit)
    }

    data class SearchAffinityRail(val items: List<Content>, val subtitle: String?)

    fun buildSearchAffinityRail(
        pool: List<Content>,
        excludeIds: Set<String> = emptySet(),
        limit: Int = RAIL_LIMIT,
    ): SearchAffinityRail {
        val keywords = InterestProfileService.topKeywords(5)
        if (keywords.isEmpty()) return SearchAffinityRail(emptyList(), null)

        val scored = mutableListOf<Pair<Content, Double>>()
        for (item in pool) {
            if (excludeIds.contains(item.id)) continue
            val titleKeys = extractKeywords(item.title, 12)
            val descKeys = extractKeywords(item.description, 8)
            var score = 0.0
            for (k in keywords) {
                if (titleKeys.contains(k)) score += InterestProfileService.keywordAffinity(k) * 2
                if (descKeys.contains(k)) score += InterestProfileService.keywordAffinity(k)
            }
            if (score > 0) scored.add(item to score)
        }
        val items = scored.sortedByDescending { it.second }.take(limit).map { it.first }
        val subtitle = if (keywords.size >= 2) {
            "${keywords[0]}، ${keywords[1]}"
        } else {
            keywords.first()
        }
        return SearchAffinityRail(items, subtitle)
    }

    fun buildContinueRail(pool: List<Content>, limit: Int = RAIL_LIMIT): List<Content> {
        val entries = ContinueWatchingService.activeEntries()
        if (entries.isEmpty()) return emptyList()
        val byId = pool.associateBy { it.id }
        val out = mutableListOf<Content>()
        for (e in entries) {
            byId[e.contentId]?.let(out::add)
            if (out.size >= limit) break
        }
        return out
    }

    fun idsOf(items: Iterable<Content>): Set<String> = items.mapTo(mutableSetOf()) { it.id }

    /**
     * يبني الرفوف بالترتيب المحدَّد.
     *
     * [personalized]: عندما تكون `false` (ضيف بلا حساب حقيقيّ) لا نبني أيّ
     * رفّ يعتمد على سلوك المستخدم (لك / من اهتماماتك). الضيف يرى الأحدث
     * والأكثر مشاهدة فقط — التخصيص الكامل محجوز لأصحاب الحسابات.
     */
    fun buildAll(
        sections: List<HomeSection>,
        popularIds: List<String> = emptyList(),
        personalized: Boolean = false,
    ): List<HomeRail> {
        val pool = allContentFlat(sections)
        val rails = mutableListOf<HomeRail>()

        val newest = buildNewestRail(pool)
        rails.add(
            HomeRail(
                type = HomeRailType.NEWEST,
                title = "الجديد في نبراس",
                items = newest,
            ),
        )

        val popular = buildPopularRail(pool, popularIds)
        if (popular.isNotEmpty()) {
            rails.add(
                HomeRail(
                    type = HomeRailType.POPULAR,
                    title = "الأكثر مشاهدة",
                    items = popular,
                ),
            )
        }

        // رفّ «تابع المشاهدة» متاح للجميع (بمن فيهم الضيوف): التقدّم يُحفظ
        // محليّاً بصرف النظر عن وجود حساب، فمن غير المنطقيّ إخفاؤه عن الضيف
        // الذي بدأ مشاهدة فعليّة.
        val continueItems = buildContinueRail(pool)
        if (continueItems.isNotEmpty()) {
            rails.add(
                HomeRail(
                    type = HomeRailType.CONTINUE_WATCHING,
                    title = "تابع التصفّح",
                    items = continueItems,
                ),
            )
        }

        // باقي الرفوف الشخصيّة محجوزة لأصحاب الحسابات فقط.
        if (!personalized) return rails

        val excludeForLater = (idsOf(newest) + idsOf(popular)).toMutableSet()

        if (InterestProfileService.isLoaded && InterestProfileService.hasSignals) {
            val forYou = buildForYouRail(pool, excludeForLater)
            if (forYou.isNotEmpty()) {
                rails.add(
                    HomeRail(
                        type = HomeRailType.FOR_YOU,
                        title = "مقترَح لك",
                        items = forYou,
                    ),
                )
                excludeForLater.addAll(idsOf(forYou))
            }
        }

        val searchResult = buildSearchAffinityRail(pool, excludeForLater)
        if (searchResult.items.isNotEmpty()) {
            val title = searchResult.subtitle
                ?.let { "من اهتماماتك: $it" }
                ?: "من اهتماماتك في البحث"
            rails.add(
                HomeRail(
                    type = HomeRailType.SEARCH_AFFINITY,
                    title = title,
                    items = searchResult.items,
                ),
            )
            excludeForLater.addAll(idsOf(searchResult.items))
        }

        // رفّ «تصفّح الأقسام» السفليّ أُزيل عمداً — التنقّل بين الأقسام يتمّ
        // هرميّاً انطلاقاً من المحتوى (زرّ القسم) لا من شبكة سفليّة.

        return rails
    }
}
