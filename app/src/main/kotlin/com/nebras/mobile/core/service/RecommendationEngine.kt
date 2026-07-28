package com.nebras.mobile.core.service

import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.util.extractKeywords
import com.nebras.mobile.core.util.keywordOverlapScore
import com.nebras.mobile.core.util.relevanceScore
import com.nebras.mobile.feature.home.model.HomeSection
import kotlin.math.ceil
import kotlin.math.ln

/**
 * محرّك حساب «المحتوى ذي الصلة» للتطبيق كلّه.
 *
 * ── كيف يعمل؟ ──
 * لكلّ مرشّح مجموع نقاط مكوّن من:
 *   * **السياق** (نفس القسم/الفرعيّ): وزن كبير — المستخدم يريد استكمال
 *     ما يقرأه/يسمعه غالباً.
 *   * **النوع**: وزن متوسّط — إن كان يشاهد فيديو نرجّح الفيديو.
 *   * **تطابق الكلمات** (Jaccard): لالتقاط المواضيع المشابهة عبر الأقسام.
 *   * **وزن الاهتمام** من [UserBehaviorTracker].
 *
 * ── لماذا مستقلّ عن [UserBehaviorTracker]؟ ──
 * المتتبّع يحفظ بيانات خام وهذا المحرّك يُفسّرها. الفصل يتيح اختبار منطق
 * التوصية دون قاعدة بيانات وهميّة، ويسمح بتغيير المعادلة دون تغيير التخزين.
 */
object RecommendationEngine {

    /** الحدّ الافتراضيّ لعدد التوصيات المُعادة. */
    const val DEFAULT_LIMIT = 12

    /**
     * يحسب قائمة مرتّبة من المرشّحين الأفضل. [pool] هو الفضاء الذي نستخرج
     * منه المرشّحين — عادةً أقسام الرئيسية. [reference] يُستثنى تلقائيّاً.
     */
    fun relatedFor(
        reference: Content,
        pool: List<HomeSection>,
        limit: Int = DEFAULT_LIMIT,
    ): List<Content> {
        val flat = flatten(pool, excludeId = reference.id)
        if (flat.isEmpty()) return emptyList()

        return flat.map { Scored(it, score(reference, it)) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.content }
    }

    /**
     * ترتيب مجموعة [candidates] مُعطاة مسبقاً (مثلاً نتائج بحث) حسب الصلة
     * بـ [reference]. لا تُفلتر بقوّة — تُرجع كلّ عنصر أُدخل إليها مع وزنه.
     */
    fun rankCandidates(reference: Content, candidates: List<Content>): List<Content> {
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .filter { it.id != reference.id }
            .map { Scored(it, score(reference, it)) }
            .sortedByDescending { it.score }
            .map { it.content }
    }

    /** يستخرج أفضل كلمات مفتاحيّة من العنوان. */
    fun keywordsFrom(reference: Content, max: Int = 3): List<String> {
        val keys = extractKeywords(reference.title, max + 2)
        return if (keys.size <= max) keys else keys.take(max)
    }

    /**
     * توصيات هجينة بنسبة 50/50 — نصفان صريحان:
     *   * **Same Category (50%)**: من نفس القسم/الفرعيّ تماماً، مرتّبون
     *     بتشابه النوع ثمّ بالتداخل الكلماتيّ.
     *   * **Related Topics (50%)**: من أيّ قسم بأعلى درجات تطابق كلمات.
     *
     * يُضفَّر الاثنان [same, related, same, related, …] حتى لا يطغى نصف على
     * آخر. عند نفاد أحد النصفين نملأ الباقي من الآخر (تدهور رشيق).
     */
    fun hybridRelatedFor(
        reference: Content,
        pool: List<HomeSection>,
        externalPool: List<Content> = emptyList(),
        limit: Int = DEFAULT_LIMIT,
    ): List<Content> {
        if (limit <= 0) return emptyList()

        val flat = flatten(pool, excludeId = reference.id)
        val extras = externalPool.filter { it.id.trim().isNotEmpty() && it.id != reference.id }

        val all = mutableListOf<Content>()
        val seen = mutableSetOf(reference.id)
        for (c in flat + extras) {
            if (seen.add(c.id)) all.add(c)
        }
        if (all.isEmpty()) return emptyList()

        // ── 1) نفس القسم/الفرعيّ ──
        val refSection = reference.section.trim()
        val refSub = reference.subSection?.trim()
        val sameBucket = mutableListOf<Scored>()
        for (c in all) {
            val sameSection = refSection.isNotEmpty() && c.section == refSection
            val sameSub = !refSub.isNullOrEmpty() &&
                c.subSection != null &&
                c.subSection == refSub
            if (!sameSection && !sameSub) continue
            // داخل السلّة نُرجّح بتشابه النوع + تداخل الكلمات.
            var s = if (sameSub) 2.0 else 1.0
            if (c.type == reference.type) s += 0.6
            s += keywordOverlapScore(reference.title, c.title) * 1.5
            s += ln(1.0 + UserBehaviorTracker.typeScore(c.type)) * 0.15
            sameBucket.add(Scored(c, s))
        }
        sameBucket.sortByDescending { it.score }

        // ── 2) كلمات مفتاحيّة متشابهة (أيّ قسم) ──
        val refTitle = reference.title
        val sameIds = sameBucket.map { it.content.id }.toSet()
        val relatedBucket = mutableListOf<Scored>()
        for (c in all) {
            if (sameIds.contains(c.id)) continue // لا تُكرّر ما في السلّة الأولى.
            val overlap = keywordOverlapScore(refTitle, c.title)
            val rel = relevanceScore(refTitle, c.title)
            val combined = overlap * 1.8 + rel * 1.2
            if (combined <= 0) continue
            var s = combined
            if (c.type == reference.type) s += 0.4
            s += ln(1.0 + UserBehaviorTracker.sectionScore(c.section)) * 0.15
            relatedBucket.add(Scored(c, s))
        }
        relatedBucket.sortByDescending { it.score }

        // ── 3) تضفير 50/50 مع تعويض ──
        val halfSame = ceil(limit / 2.0).toInt()
        val halfRelated = limit - halfSame
        val pickedSame = sameBucket.take(halfSame).toMutableList()
        val pickedRelated = relatedBucket.take(halfRelated).toMutableList()

        // تعويض: لو نقص أحد النصفين نملأ من الآخر.
        if (pickedSame.size < halfSame) {
            val needed = halfSame - pickedSame.size
            val already = pickedRelated.map { it.content.id }.toMutableSet()
            for (candidate in relatedBucket.drop(pickedRelated.size)) {
                if (pickedRelated.size + pickedSame.size >= limit) break
                if (already.add(candidate.content.id)) {
                    pickedRelated.add(candidate)
                    if (pickedRelated.size >= halfRelated + needed) break
                }
            }
        }
        if (pickedRelated.size < halfRelated) {
            val needed = halfRelated - pickedRelated.size
            val already = pickedSame.map { it.content.id }.toMutableSet()
            for (candidate in sameBucket.drop(pickedSame.size)) {
                if (pickedSame.size + pickedRelated.size >= limit) break
                if (already.add(candidate.content.id)) {
                    pickedSame.add(candidate)
                    if (pickedSame.size >= halfSame + needed) break
                }
            }
        }

        // التضفير [same, related, same, related, …].
        val out = mutableListOf<Content>()
        val addedIds = mutableSetOf<String>()
        val iterLimit = maxOf(pickedSame.size, pickedRelated.size)
        var i = 0
        while (i < iterLimit && out.size < limit) {
            if (i < pickedSame.size) {
                val c = pickedSame[i].content
                if (addedIds.add(c.id)) out.add(c)
            }
            if (out.size >= limit) break
            if (i < pickedRelated.size) {
                val c = pickedRelated[i].content
                if (addedIds.add(c.id)) out.add(c)
            }
            i++
        }
        return ensureMediaMix(out, all, limit)
    }

    /**
     * واجهة صريحة لشاشات المشغّل: مقترحات هجينة تضمن خليطاً من
     * الفيديو/الصوت/الكتب عند التوفّر داخل البيانات.
     */
    fun getRelatedContent(
        reference: Content,
        pool: List<HomeSection>,
        externalPool: List<Content> = emptyList(),
        limit: Int = DEFAULT_LIMIT,
    ): List<Content> = hybridRelatedFor(reference, pool, externalPool, limit)

    // ── داخليّات ────────────────────────────────────────────────────

    private fun score(ref: Content, c: Content): Double {
        // تطابق السياق — أهمّ إشارة.
        var score = 0.0
        if (ref.section.trim().isNotEmpty() && ref.section == c.section) score += 3.0
        if (ref.subSection != null && ref.subSection == c.subSection) score += 1.5
        if (ref.type == c.type) score += 0.7

        // تشابه الكلمات — يلتقط المواضيع المتشابهة حتّى عبر أقسام مختلفة.
        score += keywordOverlapScore(ref.title, c.title) * 2.0

        // تفضيل المستخدم — نُضخّم قليلاً، ليس كثيراً حتّى لا يُكرّر نفسه.
        score += ln(1.0 + UserBehaviorTracker.typeScore(c.type)) * 0.3
        score += ln(1.0 + UserBehaviorTracker.sectionScore(c.section)) * 0.25

        return score
    }

    private fun flatten(sections: List<HomeSection>, excludeId: String): List<Content> {
        val seen = mutableSetOf(excludeId)
        val out = mutableListOf<Content>()
        for (s in sections) {
            for (c in s.items) {
                if (c.id.trim().isEmpty()) continue
                if (seen.add(c.id)) out.add(c)
            }
        }
        return out
    }

    private fun ensureMediaMix(
        ranked: List<Content>,
        sourcePool: List<Content>,
        limit: Int,
    ): List<Content> {
        if (ranked.isEmpty()) return ranked

        val requiredTypes = setOf(
            ContentType.VIDEO,
            ContentType.YOUTUBE,
            ContentType.AUDIO,
            ContentType.BOOK,
        )
        val present = ranked.take(limit).map { it.type }.toSet()
        val missing = requiredTypes.filter { it !in present }
        if (missing.isEmpty()) return ranked.take(limit)

        val out = ranked.take(limit).toMutableList()
        val used = out.mapTo(mutableSetOf()) { it.id }

        for (missingType in missing) {
            val candidate = sourcePool.firstOrNull { it.type == missingType && it.id !in used }
                ?: continue
            if (out.size >= limit) out.removeAt(out.size - 1)
            out.add(candidate)
            used.add(candidate.id)
        }
        return out.take(limit)
    }

    private data class Scored(val content: Content, val score: Double)
}
