package com.nebras.mobile.core.service

import com.nebras.mobile.core.model.Content
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * **طبقة الفرز الذكيّة** لصفحة "لك". نقطة الدخول الوحيدة هي [rank].
 * الخدمة بلا حالة (تقرأ من [InterestProfileService] كمنبع حقيقة) وبلا شبكة.
 *
 * ── الفكرة ──
 * لكلّ عنصر: `score = contentAffinity(content) + jitter`، ثمّ ترتيب تنازليّ،
 * مع حجز نسبة **Discovery** (افتراضيّاً 15%) للعناصر ذات الأفنيتي الصفر —
 * كي لا يُحبس المستخدم في فقاعة اهتماماته.
 */
object RecommendationService {

    /**
     * الحدّ الأدنى لحجم القائمة الذي تستحقّ معه تطبيق منطق Discovery.
     * تحته (< 10 عناصر) نعيد القائمة مرتّبة فقط — إقحام «عشوائيّ» في خلاصة
     * قصيرة يبدو كخلل لا كاكتشاف.
     */
    private const val DISCOVERY_MIN_POOL = 10

    /**
     * يرتّب [pool] تنازليّاً وفق أفنيتي المستخدم، مع حجز [discoveryPercent]
     * للمحتوى الاستكشافيّ.
     *
     * [seed]: لتثبيت الـ jitter خلال عرض واحد وتجنّب اهتزاز الترتيب بين
     * إعادات البناء.
     *
     * **ضمانات**: لا تكرار، وحجم المُخرَج == حجم المدخَل، وإن كان ملفّ
     * الاهتمامات فارغاً تُعاد خلاصة البداية الباردة.
     */
    fun rank(
        pool: Iterable<Content>,
        discoveryPercent: Int = 15,
        seed: Long? = null,
    ): List<Content> {
        val items = pool.toList()
        if (items.isEmpty()) return emptyList()

        val profile = InterestProfileService
        if (!profile.isLoaded || !profile.hasSignals) {
            // Cold start: مزيج أحدث + شعبي — لا صفحة فارغة.
            return coldStartFeed(items)
        }

        val rng = Random(seed ?: System.currentTimeMillis())

        // 1) احسب درجة كلّ عنصر.
        val scored = items.map { c ->
            val base = profile.contentAffinity(c).toDouble()
            // jitter صغير نسبة إلى الدرجة ذاتها كي لا يُهمّش العناصر العالية.
            val jitter = rng.nextDouble() * (if (base < 1) 0.5 else base * 0.05)
            Scored(c, base + jitter, base)
        }

        // 2) افصل Discovery (أفنيتي <= 0) عن العناصر المُشخصَنة.
        val personalized = scored.filter { it.baseScore > 0 }
            .sortedByDescending { it.score }
            .toMutableList()
        val discovery = scored.filter { it.baseScore <= 0 }
            .shuffled(rng)
            .toMutableList()

        // 3) قائمة قصيرة أو بلا Discovery ⇒ المُشخصَن ثمّ البقيّة بلا إقحام.
        if (items.size < DISCOVERY_MIN_POOL || discovery.isEmpty() || personalized.isEmpty()) {
            return personalized.map { it.content } + discovery.map { it.content }
        }

        // 4) احجز نسبة Discovery من الحجم الكلّيّ (بحدّ أدنى 1).
        val cappedPct = discoveryPercent.coerceIn(0, 50)
        val discoveryQuota = ((items.size * cappedPct) / 100.0)
            .roundToInt()
            .coerceIn(1, discovery.size)

        // 5) إدراج Discovery على فواصل منتظمة. نترك أوّل موضع دائماً لعنصر
        //    مُشخصَن (أعلى درجة) كي يشعر المستخدم أنّ الصفحة تتكلّم معه من
        //    السطر الأوّل.
        val stride = max(3, floor(items.size.toDouble() / (discoveryQuota + 1)).toInt())
        val output = ArrayList<Content>(items.size)
        var pIdx = 0
        var dIdx = 0
        for (i in items.indices) {
            val isDiscoverySlot = i > 0 && i % stride == 0 && dIdx < discoveryQuota
            when {
                isDiscoverySlot && dIdx < discovery.size -> output.add(discovery[dIdx++].content)
                pIdx < personalized.size -> output.add(personalized[pIdx++].content)
                dIdx < discovery.size -> output.add(discovery[dIdx++].content)
            }
        }

        // 6) أيّ فائض يُلحق بالذيل دون تكرار.
        if (output.size < items.size) {
            val seen = output.mapTo(HashSet()) { it.id }
            while (pIdx < personalized.size) {
                val c = personalized[pIdx++].content
                if (seen.add(c.id)) output.add(c)
            }
            while (dIdx < discovery.size) {
                val c = discovery[dIdx++].content
                if (seen.add(c.id)) output.add(c)
            }
        }

        return output
    }

    /** مستخدم جديد: 50% أحدث + 50% الأكثر مشاهدة/شعبيّة محلياً. */
    private fun coldStartFeed(items: List<Content>): List<Content> {
        if (items.isEmpty()) return emptyList()
        val newest = items.sortedByDescending { it.createdAt }
        val popular = items.sortedWith(
            compareByDescending<Content> { it.viewCount }.thenByDescending { it.createdAt },
        )
        val half = Math.ceil(items.size / 2.0).toInt().coerceIn(1, items.size)
        val seen = HashSet<String>()
        val out = ArrayList<Content>(items.size)
        var ni = 0
        var pi = 0
        while (out.size < items.size && (ni < newest.size || pi < popular.size)) {
            if (out.size % 2 == 0 && ni < newest.size) {
                val c = newest[ni++]
                if (seen.add(c.id)) out.add(c)
            } else if (pi < popular.size) {
                val c = popular[pi++]
                if (seen.add(c.id)) out.add(c)
            } else if (ni < newest.size) {
                val c = newest[ni++]
                if (seen.add(c.id)) out.add(c)
            } else {
                break
            }
            if (out.size >= half * 2) break
        }
        for (c in newest + popular) {
            if (out.size >= items.size) break
            if (seen.add(c.id)) out.add(c)
        }
        return out
    }

    /** تقرير مختصر — **للتصحيح فقط**: توزّع الأفنيتي عبر قائمة معروضة. */
    fun describe(pool: Iterable<Content>): Map<String, Int> {
        var personalized = 0
        var discovery = 0
        var totalAffinity = 0
        for (c in pool) {
            val a = InterestProfileService.contentAffinity(c)
            totalAffinity += a
            if (a > 0) personalized++ else discovery++
        }
        return mapOf(
            "personalized" to personalized,
            "discovery" to discovery,
            "totalAffinity" to totalAffinity,
        )
    }

    private data class Scored(
        val content: Content,
        val score: Double,
        val baseScore: Double,
    )
}
