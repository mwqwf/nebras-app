package com.nebras.mobile.core.service

import android.util.Log
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.util.extractKeywords
import com.nebras.mobile.core.util.keywordOverlapScore
import com.nebras.mobile.core.util.normalizeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * **الملف الشخصي السلوكيّ للمستخدم** (Global).
 *
 * ── الفلسفة ──
 * بنية بسيطة وصامتة تسجّل ثلاثة تدفّقات مستقلّة:
 *   1) **زيارات الأقسام**: كلّ ضغط يزيد عدّاد `sectionId` ويستخرج الكلمات.
 *   2) **فتحات المحتوى**: كلّ فتح يزيد عدّاد نوعه وقسمه ويستخرج كلماته.
 *   3) **استعلامات البحث**: تُغذّي سجلّ الكلمات مباشرة (مع تطبيع عربيّ).
 *
 * ── لماذا خدمة واحدة عامّة؟ ──
 * مكوّنات عدّة تقرأ من نفس النموذج (ترتيب الرئيسية، توصيات "لك"، «ذات
 * صلة»، البحث الذكيّ). خدمة واحدة كمنبع حقيقة تمنع تكرار الكتابة على
 * التخزين، وانقسام المفاتيح، وانحراف التطبيع العربيّ بين الأماكن.
 *
 * ── العزل ──
 * لا يعرف شبكة ولا Firebase — تخزين محلّيّ فقط، و`object` ثابت يمكن
 * استدعاؤه من أيّ مكان بلا `context`.
 */
object UserBehaviorTracker {

    private const val TAG = "UserBehaviorTracker"

    // ── مفاتيح التخزين ──
    private const val KEY_SECTIONS = "ubt_section_counts_v1"
    private const val KEY_TYPES = "ubt_type_counts_v1"
    private const val KEY_KEYWORDS = "ubt_keyword_counts_v1"
    private const val KEY_SECTION_KEYWORD_INDEX = "ubt_section_keywords_v1"
    private const val KEY_LAST_SEARCHES = "ubt_last_searches_v1"
    private const val KEY_BROWSED_SECTIONS = "ubt_browsed_sections_v1"
    private const val KEY_SECTION_TITLES = "ubt_section_titles_v1"
    private const val KEY_SECTION_LAST_VISIT = "ubt_section_last_visit_v1"
    private const val KEY_QUICK_EXIT_STREAK = "ubt_quick_exit_streak_v1"

    // ── الحدود القصوى للتخزين (حماية من التضخّم) ──
    private const val MAX_KEYWORDS = 80
    private const val MAX_LAST_SEARCHES = 30
    private const val MAX_KEYWORDS_PER_SECTION = 8

    /** عتبة اعتبار الزيارة «تصفّحاً حقيقيّاً» بدل «نقرة مرور سريعة». */
    private const val BROWSED_THRESHOLD_MILLIS = 5_000L

    /** أقلّ من هذه المدّة = «خروج سريع» (لا يُحسب تصفّحاً). */
    private const val QUICK_EXIT_THRESHOLD_MILLIS = 2_000L

    private const val VISIT_DECAY_AFTER_MILLIS = 30L * 24 * 60 * 60 * 1000

    private lateinit var store: LocalStore

    // ── الحالة بالذاكرة (تُزامَن مع التخزين) ──

    /** عدد مرّات فتح/زيارة كلّ قسم — المفتاح `sectionId`. */
    private val sectionCountsMap = LinkedHashMap<String, Int>()

    /** عدد مرّات فتح المحتوى لكلّ نوع. */
    private val typeCountsMap = LinkedHashMap<ContentType, Int>()

    /** عدد مرّات تكرار كلّ كلمة مفتاحيّة (مطبَّعة). */
    private val keywordCountsMap = LinkedHashMap<String, Int>()

    /** يربط كلّ `sectionId` بكلماته المفتاحيّة — لمقارنة قسمين موضوعيّاً. */
    private val sectionKeywordsMap = LinkedHashMap<String, List<String>>()

    /** آخر كلمات بحث (FIFO) — تلميحات في "لك" والبحث. */
    private val lastSearchesList = mutableListOf<String>()

    /**
     * الأقسام التي **تصفّحها** المستخدم فعليّاً. يختلف عن [sectionCountsMap]
     * لأنّ الأخير يرتفع بأيّ نقرة ولو عاد فوراً؛ هذا مؤشّر نيّة أقوى.
     */
    private val browsedSectionCountsMap = LinkedHashMap<String, Int>()

    /** فهرسة عناوين الأقسام — تُقرأ قبل تحميل Firebase أحياناً. */
    private val sectionTitlesMap = LinkedHashMap<String, String>()

    /** آخر زيارة لكلّ قسم (ms) — لتقادم الزيارات >30 يوم. */
    private val sectionLastVisitMap = LinkedHashMap<String, Long>()

    /** خروج سريع متتالٍ (<2 ث) — إن وصل 5 تُطبَّق عقوبة في الترتيب. */
    private val quickExitStreakMap = LinkedHashMap<String, Int>()

    /** ميقات بداية الزيارة الحاليّة — داخليّ، لا يُحفظ. */
    private val sectionEnterAt = LinkedHashMap<String, Long>()

    private var loaded = false

    private val _revision = MutableStateFlow(0L)

    /** يتغيّر عند كلّ تحديث — بديل `notifyListeners()`. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    val isLoaded: Boolean get() = loaded
    val sectionCounts: Map<String, Int> get() = sectionCountsMap.toMap()
    val typeCounts: Map<ContentType, Int> get() = typeCountsMap.toMap()
    val keywordCounts: Map<String, Int> get() = keywordCountsMap.toMap()
    val lastSearches: List<String> get() = lastSearchesList.toList()
    val browsedSectionCounts: Map<String, Int> get() = browsedSectionCountsMap.toMap()
    val sectionTitles: Map<String, String> get() = sectionTitlesMap.toMap()

    // ── تحميل / تفريغ الحالة ────────────────────────────────────────

    /** يُستدعى مرّة واحدة عند الإقلاع. آمن للاستدعاء المتكرّر. */
    fun init(localStore: LocalStore) {
        store = localStore
        if (loaded) return
        runCatching {
            hydrateIntMap(KEY_SECTIONS, sectionCountsMap)
            hydrateTypeMap(KEY_TYPES, typeCountsMap)
            hydrateIntMap(KEY_KEYWORDS, keywordCountsMap)
            hydrateStringListMap(KEY_SECTION_KEYWORD_INDEX, sectionKeywordsMap)
            hydrateIntMap(KEY_BROWSED_SECTIONS, browsedSectionCountsMap)
            hydrateStringMap(KEY_SECTION_TITLES, sectionTitlesMap)
            hydrateLongMap(KEY_SECTION_LAST_VISIT, sectionLastVisitMap)
            hydrateIntMap(KEY_QUICK_EXIT_STREAK, quickExitStreakMap)
            lastSearchesList.clear()
            lastSearchesList.addAll(store.getStringList(KEY_LAST_SEARCHES))
        }.onFailure { Log.d(TAG, "init error: ${it.message}") }
        loaded = true
        bump()
    }

    /** تصفير كامل — «نسيان اهتماماتي». */
    fun clear() {
        sectionCountsMap.clear()
        typeCountsMap.clear()
        keywordCountsMap.clear()
        sectionKeywordsMap.clear()
        lastSearchesList.clear()
        browsedSectionCountsMap.clear()
        sectionTitlesMap.clear()
        sectionLastVisitMap.clear()
        quickExitStreakMap.clear()
        sectionEnterAt.clear()
        bump()
        runCatching {
            listOf(
                KEY_SECTIONS, KEY_TYPES, KEY_KEYWORDS, KEY_SECTION_KEYWORD_INDEX,
                KEY_LAST_SEARCHES, KEY_BROWSED_SECTIONS, KEY_SECTION_TITLES,
                KEY_SECTION_LAST_VISIT, KEY_QUICK_EXIT_STREAK,
            ).forEach(store::remove)
        }.onFailure { Log.d(TAG, "clear error: ${it.message}") }
    }

    // ── التسجيلات ───────────────────────────────────────────────────

    /**
     * يُسجَّل عند الضغط على قسم في الرئيسية أو البحث. **صامت تماماً**:
     * لا يرمي أخطاء ولا يُشوّش المستخدم.
     */
    fun recordSectionVisit(sectionId: String, sectionTitle: String) {
        val id = sectionId.trim()
        if (id.isEmpty()) return
        val now = System.currentTimeMillis()
        sectionCountsMap[id] = (sectionCountsMap[id] ?: 0) + 1
        sectionEnterAt[id] = now
        sectionLastVisitMap[id] = now
        sectionTitle.trim().takeIf { it.isNotEmpty() }?.let { sectionTitlesMap[id] = it }

        val keywords = extractKeywords(sectionTitle, MAX_KEYWORDS_PER_SECTION)
        if (keywords.isNotEmpty()) {
            sectionKeywordsMap[id] = keywords
            for (k in keywords) {
                keywordCountsMap[k] = (keywordCountsMap[k] ?: 0) + 1
            }
            pruneKeywords()
        }
        bump()
        persistSections()
        persistKeywords()
        persistSectionTitles()
        persistSectionLastVisit()
    }

    /**
     * يُستدعى عند مغادرة شاشة القسم. إن قضى المستخدم [BROWSED_THRESHOLD_MILLIS]
     * أو أكثر نعتبره «تصفّحاً جدّيّاً» — إشارة نيّة أقوى من نقرة واحدة.
     */
    fun recordSectionExit(sectionId: String) {
        val id = sectionId.trim()
        if (id.isEmpty()) return
        val enteredAt = sectionEnterAt.remove(id) ?: return
        val spent = System.currentTimeMillis() - enteredAt
        if (spent < QUICK_EXIT_THRESHOLD_MILLIS) {
            quickExitStreakMap[id] = (quickExitStreakMap[id] ?: 0) + 1
            bump()
            persistQuickExitStreak()
            return
        }
        quickExitStreakMap[id] = 0
        persistQuickExitStreak()
        if (spent < BROWSED_THRESHOLD_MILLIS) return
        browsedSectionCountsMap[id] = (browsedSectionCountsMap[id] ?: 0) + 1
        bump()
        persistBrowsedSections()
    }

    /** مضاعف زيارة القسم: 1.0 حديثاً، 0.3 إن كانت آخر زيارة قبل >30 يوماً. */
    fun visitDecayMultiplier(sectionId: String): Double {
        val at = sectionLastVisitMap[sectionId] ?: return 1.0
        if (System.currentTimeMillis() - at > VISIT_DECAY_AFTER_MILLIS) return 0.3
        return 1.0
    }

    /** عقوبة إن خرج المستخدم 5 مرّات متتالية خلال <2 ثانية. */
    fun quickExitPenalty(sectionId: String): Double =
        if ((quickExitStreakMap[sectionId] ?: 0) >= 5) -1.5 else 0.0

    /**
     * تسجيل فوريّ لـ«تصفّح كامل» حين يوجد مؤشّر صريح (الضغط على عنصر أو
     * التمرير لنهاية القائمة). يتجاوز عتبة الوقت.
     */
    fun recordSectionBrowsed(sectionId: String, title: String? = null) {
        val id = sectionId.trim()
        if (id.isEmpty()) return
        browsedSectionCountsMap[id] = (browsedSectionCountsMap[id] ?: 0) + 1
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { sectionTitlesMap[id] = it }
        sectionEnterAt.remove(id)
        bump()
        persistBrowsedSections()
        persistSectionTitles()
    }

    /** يُسجَّل عند فتح عنصر محتوى من أيّ مكان (الرئيسية، لك، البحث). */
    fun recordContentOpen(content: Content) {
        if (content.id.trim().isEmpty()) return
        typeCountsMap[content.type] = (typeCountsMap[content.type] ?: 0) + 1

        if (content.section.trim().isNotEmpty()) {
            sectionCountsMap[content.section] = (sectionCountsMap[content.section] ?: 0) + 1
        }
        val keywords = buildSet {
            addAll(extractKeywords(content.title, MAX_KEYWORDS_PER_SECTION))
            addAll(extractKeywords(content.sectionName, 3))
        }
        for (k in keywords) {
            keywordCountsMap[k] = (keywordCountsMap[k] ?: 0) + 1
        }
        pruneKeywords()
        bump()
        persistSections()
        persistTypes()
        persistKeywords()
    }

    /**
     * يُسجَّل عند كلّ بحث ناجح. نُرجّح وزن الكلمة أكثر من مجرّد زيارة
     * لأنّها نيّة صريحة من المستخدم (+2).
     */
    fun recordSearch(query: String) {
        val normalized = normalizeText(query)
        if (normalized.isEmpty() || normalized.length < 2) return

        lastSearchesList.remove(normalized)
        lastSearchesList.add(0, normalized)
        while (lastSearchesList.size > MAX_LAST_SEARCHES) {
            lastSearchesList.removeAt(lastSearchesList.size - 1)
        }

        for (k in extractKeywords(query, 6)) {
            keywordCountsMap[k] = (keywordCountsMap[k] ?: 0) + 2
        }
        pruneKeywords()
        bump()
        persistKeywords()
        persistLastSearches()
    }

    /**
     * إزالة كلمة بحث واحدة من السجلّ (لا تؤثّر على عدّادات الاهتمام —
     * الاهتمامات تُبنى عبر الوقت، نحن ننظّف عرض السجلّ فقط).
     */
    fun removeSearch(query: String) {
        val normalized = normalizeText(query)
        if (normalized.isEmpty()) return
        if (!lastSearchesList.remove(normalized)) return
        bump()
        persistLastSearches()
    }

    /** تفريغ سجلّ البحث المعروض، مع إبقاء الاهتمامات العامّة. */
    fun clearSearchHistory() {
        if (lastSearchesList.isEmpty()) return
        lastSearchesList.clear()
        bump()
        persistLastSearches()
    }

    /** واجهة صريحة: عند دخول قسم. */
    fun recordSectionEnter(sectionId: String, sectionTitle: String) =
        recordSectionVisit(sectionId, sectionTitle)

    /** واجهة صريحة: عند تشغيل/عرض محتوى فعليّاً. */
    fun recordContentPlayed(content: Content) = recordContentOpen(content)

    /** واجهة صريحة: عند كتابة المستخدم كلمة بحث. */
    fun recordSearchKeyword(query: String) = recordSearch(query)

    // ── الاستعلام (Scoring / Querying) ──────────────────────────────

    /**
     * وزن قسم مقارنةً بغيره. يُضمَّن التصفّح الفعليّ بوزن مضاعف لأنّه
     * مؤشّر نيّة أقوى: تصفّح حقيقيّ ≈ نقرتان عابرتان.
     */
    fun sectionScore(sectionId: String): Int {
        val clicks = sectionCountsMap[sectionId] ?: 0
        val browsed = browsedSectionCountsMap[sectionId] ?: 0
        return clicks + browsed * 2
    }

    /** وزن التصفّح الفعليّ — يميّز «قسم مرّ به خطأً» عن «قسم قضى فيه وقتاً». */
    fun browsedSectionScore(sectionId: String): Int =
        browsedSectionCountsMap[sectionId] ?: 0

    /** أعلى N قسم — مرتّب تنازليّاً بـ [sectionScore] (نقرات + تصفّح). */
    fun topSectionIds(limit: Int = 10): List<String> {
        if (sectionCountsMap.isEmpty() && browsedSectionCountsMap.isEmpty()) {
            return emptyList()
        }
        val ids = sectionCountsMap.keys + browsedSectionCountsMap.keys
        return ids.map { it to sectionScore(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** وزن نوع محتوى — يرجّح النوع المفضَّل في قائمة «ذات صلة». */
    fun typeScore(type: ContentType): Int = typeCountsMap[type] ?: 0

    /** وزن كلمة مفتاحيّة. المدخل يُطبَّع قبل البحث. */
    fun keywordScore(keyword: String): Int {
        val norm = normalizeText(keyword)
        if (norm.isEmpty()) return 0
        return keywordCountsMap[norm] ?: 0
    }

    /** أعلى N كلمة مفتاحيّة — تعكس اهتمامات المستخدم المُجمَّعة. */
    fun topKeywords(limit: Int = 20): List<String> =
        keywordCountsMap.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    /** الكلمات المفتاحيّة المسجَّلة لقسم — لحساب تشابه قسمين. */
    fun keywordsForSection(sectionId: String): List<String> =
        sectionKeywordsMap[sectionId] ?: emptyList()

    /**
     * لقطة شاملة لملفّ السلوك — تحوّل كلّ الإشارات إلى استعلامات صامتة:
     * كلمات تبحث بها، وأقسام تتصفّحها، ونوع محتوى يُرجَّح.
     */
    fun profileSnapshot(maxKeywords: Int = 12, maxSections: Int = 8): BehaviorProfile {
        val keywords = topKeywords(maxKeywords)
        val sections = topSectionIds(maxSections)
        val preferredType = typeCountsMap.entries.maxByOrNull { it.value }?.key
        return BehaviorProfile(
            keywords = keywords,
            sectionIds = sections,
            sectionTitles = sections.mapNotNull { id ->
                sectionTitlesMap[id]?.let { id to it }
            }.toMap(),
            recentSearches = lastSearchesList.take(maxKeywords),
            preferredType = preferredType,
        )
    }

    /**
     * نسبة تشابه سياقيّ بين عنصرَي محتوى (القسم + النوع + الكلمات).
     * النتيجة بين 0 و1 تقريباً — تستخدمها `RecommendationEngine`.
     */
    fun similarityBetween(reference: Content, candidate: Content): Double {
        if (reference.id == candidate.id) return 0.0

        var score = 0.0
        if (reference.section == candidate.section && reference.section.trim().isNotEmpty()) {
            score += 0.55
        }
        if (reference.subSection != null && reference.subSection == candidate.subSection) {
            score += 0.15
        }
        if (reference.type == candidate.type) score += 0.10

        score += keywordOverlapScore(reference.title, candidate.title) * 0.20
        return score.coerceIn(0.0, 1.0)
    }

    // ── أدوات داخليّة ───────────────────────────────────────────────

    private fun pruneKeywords() {
        if (keywordCountsMap.size <= MAX_KEYWORDS) return
        val keep = keywordCountsMap.entries
            .sortedByDescending { it.value }
            .take(MAX_KEYWORDS)
            .associate { it.key to it.value }
        keywordCountsMap.clear()
        keywordCountsMap.putAll(keep)
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun persistSections() = runCatching {
        store.putJsonObject(KEY_SECTIONS, intMapToJson(sectionCountsMap))
        val index = JSONObject()
        sectionKeywordsMap.forEach { (k, v) ->
            index.put(k, JSONArray().apply { v.forEach(::put) })
        }
        store.putJsonObject(KEY_SECTION_KEYWORD_INDEX, index)
    }.onFailure { Log.d(TAG, "persist sections error: ${it.message}") }.let { }

    private fun persistTypes() = runCatching {
        val encoded = JSONObject()
        typeCountsMap.forEach { (k, v) -> encoded.put(k.wire, v) }
        store.putJsonObject(KEY_TYPES, encoded)
    }.onFailure { Log.d(TAG, "persist types error: ${it.message}") }.let { }

    private fun persistKeywords() = runCatching {
        store.putJsonObject(KEY_KEYWORDS, intMapToJson(keywordCountsMap))
    }.onFailure { Log.d(TAG, "persist keywords error: ${it.message}") }.let { }

    private fun persistLastSearches() = runCatching {
        store.putStringList(KEY_LAST_SEARCHES, lastSearchesList)
    }.onFailure { Log.d(TAG, "persist searches error: ${it.message}") }.let { }

    private fun persistBrowsedSections() = runCatching {
        store.putJsonObject(KEY_BROWSED_SECTIONS, intMapToJson(browsedSectionCountsMap))
    }.onFailure { Log.d(TAG, "persist browsed error: ${it.message}") }.let { }

    private fun persistSectionTitles() = runCatching {
        val json = JSONObject()
        sectionTitlesMap.forEach { (k, v) -> json.put(k, v) }
        store.putJsonObject(KEY_SECTION_TITLES, json)
    }.onFailure { Log.d(TAG, "persist titles error: ${it.message}") }.let { }

    private fun persistSectionLastVisit() = runCatching {
        val json = JSONObject()
        sectionLastVisitMap.forEach { (k, v) -> json.put(k, v) }
        store.putJsonObject(KEY_SECTION_LAST_VISIT, json)
    }.onFailure { Log.d(TAG, "persist last visit error: ${it.message}") }.let { }

    private fun persistQuickExitStreak() = runCatching {
        store.putJsonObject(KEY_QUICK_EXIT_STREAK, intMapToJson(quickExitStreakMap))
    }.onFailure { Log.d(TAG, "persist quick exit error: ${it.message}") }.let { }

    private fun intMapToJson(map: Map<String, Int>): JSONObject {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json
    }

    private fun hydrateIntMap(key: String, target: MutableMap<String, Int>) {
        val json = store.getJsonObject(key) ?: return
        for (k in json.keys()) {
            val count = json.optInt(k, 0)
            if (count > 0) target[k] = count
        }
    }

    private fun hydrateLongMap(key: String, target: MutableMap<String, Long>) {
        val json = store.getJsonObject(key) ?: return
        for (k in json.keys()) {
            // التوافق الخلفيّ: نسخة Flutter كانت تخزّن ISO نصّاً.
            val value = json.opt(k)
            val ms = when (value) {
                is Number -> value.toLong()
                is String -> runCatching {
                    java.time.Instant.parse(value).toEpochMilli()
                }.getOrElse { value.toLongOrNull() ?: 0L }
                else -> 0L
            }
            if (ms > 0) target[k] = ms
        }
    }

    private fun hydrateTypeMap(key: String, target: MutableMap<ContentType, Int>) {
        val json = store.getJsonObject(key) ?: return
        for (k in json.keys()) {
            val type = parseType(k) ?: continue
            val count = json.optInt(k, 0)
            if (count > 0) target[type] = count
        }
    }

    private fun hydrateStringListMap(key: String, target: MutableMap<String, List<String>>) {
        val json = store.getJsonObject(key) ?: return
        for (k in json.keys()) {
            val array = json.optJSONArray(k) ?: continue
            target[k] = array.toStringList()
        }
    }

    private fun hydrateStringMap(key: String, target: MutableMap<String, String>) {
        val json = store.getJsonObject(key) ?: return
        for (k in json.keys()) {
            target[k] = json.opt(k)?.toString().orEmpty()
        }
    }

    private fun parseType(raw: String): ContentType? =
        ContentType.entries.firstOrNull { it.wire == raw || it.name == raw }
}

/**
 * لقطة مضغوطة لاهتمامات المستخدم — **غير قابلة للتغيير**. لإعادة حسابها
 * استدعِ [UserBehaviorTracker.profileSnapshot] مجدّداً.
 *
 * **الاستخدام المتوقَّع**:
 *   * [keywords]: تُرسل لمحرّك البحث لجلب محتوى جديد يطابق الاهتمام.
 *   * [sectionIds]: تُلتقط من أقسام الرئيسية وتُفتح ضمنها عناصر جديدة.
 *   * [preferredType]: فلتر **فرز** (ليس حذفاً) يقدّم النوع المفضَّل.
 */
data class BehaviorProfile(
    val keywords: List<String>,
    val sectionIds: List<String>,
    val sectionTitles: Map<String, String>,
    val recentSearches: List<String>,
    val preferredType: ContentType?,
) {
    val isEmpty: Boolean
        get() = keywords.isEmpty() && sectionIds.isEmpty() && recentSearches.isEmpty()

    val isNotEmpty: Boolean get() = !isEmpty

    /**
     * مفتاح بحث مدموج — يجمع الكلمات المميَّزة وآخر بحث لم يُفهرس بعد.
     * مفيد للخدمات التي تريد استعلاماً واحداً فقط.
     */
    fun primaryQuery(maxWords: Int = 4): String {
        val words = LinkedHashSet<String>()
        for (k in recentSearches) {
            if (words.size >= maxWords) break
            words.add(k)
        }
        for (k in keywords) {
            if (words.size >= maxWords) break
            words.add(k)
        }
        return words.joinToString(" ").trim()
    }
}
