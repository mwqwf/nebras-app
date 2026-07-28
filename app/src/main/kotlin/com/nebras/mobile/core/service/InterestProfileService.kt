package com.nebras.mobile.core.service

import android.util.Log
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.util.extractKeywords
import com.nebras.mobile.core.util.normalizeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * **محرّك حساب اهتمامات المستخدم (Affinity)** — مستقلّ تماماً عن
 * [UserBehaviorTracker]، لتغذية [RecommendationService] لصفحة "لك".
 *
 * ── لماذا خدمة منفصلة؟ ──
 * [UserBehaviorTracker] يُعبّر عن مسارات استخدام متعدّدة وأوزانه متشعّبة
 * داخل نفس العدّاد. لإنجاز أوزان دقيقة ومنع أيّ تداخل مع المنطق القائم،
 * نُبقي المتتبّع القديم كما هو ونُنشئ هذه الخدمة كطبقة **إضافيّة**
 * (مفاتيح تخزين جديدة، وتُستدعى جنباً إلى جنب معه لا بدلاً عنه).
 *
 * ── الأوزان الصارمة ──
 *   * **Section View** ⇒ `+1` لرصيد القسم و`+1` لكلّ كلمة من عنوانه.
 *   * **Content Consumption** ⇒ `+3` لرصيد القسم المضيف و`+3` لكلّ كلمة.
 *   * **Search Query** ⇒ `+5` لكلّ كلمة من الاستعلام (نيّة صريحة).
 *
 * ── حدود الأداء ──
 * سقف 120 كلمة مفتاحيّة نشطة حتى لا ينتفخ التخزين، والكتابات غير حاجبة.
 *
 * ── العزل ──
 * لا يعرف شبكة ولا Firebase، ولا يعدّل حالة [UserBehaviorTracker].
 */
object InterestProfileService {

    private const val TAG = "InterestProfileService"

    // ── أوزان النموذج (ثابتة، مرئيّة لاختبارات الوحدة) ──
    const val WEIGHT_SECTION_VIEW = 1
    const val WEIGHT_CONSUMPTION = 3
    const val WEIGHT_SEARCH = 5

    // ── حدود التخزين ──
    private const val MAX_KEYWORDS = 120
    private const val MAX_KEYWORDS_PER_TITLE = 8

    // ── مفاتيح التخزين (جديدة — لا تتعارض مع UBT) ──
    private const val KEY_SECTION_AFFINITY = "ips_section_affinity_v1"
    private const val KEY_KEYWORD_AFFINITY = "ips_keyword_affinity_v1"
    private const val KEY_TYPE_AFFINITY = "ips_type_affinity_v1"

    private lateinit var store: LocalStore

    /**
     * إجماليّ النقاط لكلّ قسم: مجموع مرجَّح لزيارات القسم + استهلاك محتواه
     * + إشارات البحث المطابقة.
     */
    private val sectionScoresMap = LinkedHashMap<String, Int>()

    /** إجماليّ النقاط لكلّ كلمة مفتاحيّة (مطبَّعة عربيّاً). */
    private val keywordScoresMap = LinkedHashMap<String, Int>()

    /** وزن تفضيل نوع المحتوى — يتدرّج مع كلّ استهلاك. */
    private val typeScoresMap = LinkedHashMap<ContentType, Int>()

    private var loaded = false

    private val _revision = MutableStateFlow(0L)

    /** يتغيّر عند كلّ تحديث — بديل `notifyListeners()`. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    val isLoaded: Boolean get() = loaded

    /** هل لدينا أيّ إشارة فعلاً؟ يقرّر التحوّل إلى المنطق المُشخصَن. */
    val hasSignals: Boolean
        get() = sectionScoresMap.isNotEmpty() ||
            keywordScoresMap.isNotEmpty() ||
            typeScoresMap.isNotEmpty()

    val sectionScores: Map<String, Int> get() = sectionScoresMap.toMap()
    val keywordScores: Map<String, Int> get() = keywordScoresMap.toMap()
    val typeScores: Map<ContentType, Int> get() = typeScoresMap.toMap()

    // ── دورة الحياة ─────────────────────────────────────────────────

    /** يُستدعى مرّة واحدة عند الإقلاع. آمن للاستدعاء المتكرّر. */
    fun init(localStore: LocalStore) {
        store = localStore
        if (loaded) return
        runCatching {
            hydrateIntMap(KEY_SECTION_AFFINITY, sectionScoresMap)
            hydrateIntMap(KEY_KEYWORD_AFFINITY, keywordScoresMap)
            hydrateTypeMap(KEY_TYPE_AFFINITY, typeScoresMap)
        }.onFailure { Log.d(TAG, "init error: ${it.message}") }
        loaded = true
        bump()
    }

    /** إعادة ضبط كاملة — خيار «نسيان اهتماماتي». */
    fun clearProfile() {
        sectionScoresMap.clear()
        keywordScoresMap.clear()
        typeScoresMap.clear()
        bump()
        runCatching {
            store.remove(KEY_SECTION_AFFINITY)
            store.remove(KEY_KEYWORD_AFFINITY)
            store.remove(KEY_TYPE_AFFINITY)
        }.onFailure { Log.d(TAG, "clear error: ${it.message}") }
    }

    // ── التسجيلات (+1 / +3 / +5) ────────────────────────────────────

    /** تصفّح قسم. وزنه **+1** للقسم ولكلّ كلمة من عنوانه. */
    fun onSectionView(sectionId: String, sectionTitle: String) {
        val id = sectionId.trim()
        if (id.isEmpty()) return
        sectionScoresMap[id] = (sectionScoresMap[id] ?: 0) + WEIGHT_SECTION_VIEW

        for (k in extractKeywords(sectionTitle, MAX_KEYWORDS_PER_TITLE)) {
            keywordScoresMap[k] = (keywordScoresMap[k] ?: 0) + WEIGHT_SECTION_VIEW
        }
        pruneKeywords()
        bump()
        persistAll()
    }

    /** استهلاك فعليّ للمحتوى (فتح قارئ، تشغيل فيديو/صوت). وزنه **+3**. */
    fun onContentConsumed(content: Content) {
        if (content.id.trim().isEmpty()) return

        // وزن القسم المضيف للمحتوى.
        val sectionId = content.section.trim()
        if (sectionId.isNotEmpty()) {
            sectionScoresMap[sectionId] =
                (sectionScoresMap[sectionId] ?: 0) + WEIGHT_CONSUMPTION
        }

        // وزن النوع — يدخل في حساب تفضيل المستخدم لنوع المحتوى.
        typeScoresMap[content.type] = (typeScoresMap[content.type] ?: 0) + WEIGHT_CONSUMPTION

        // كلمات من عنوان المحتوى + اسم القسم (للتسلسل الدلاليّ).
        val keys = buildSet {
            addAll(extractKeywords(content.title, MAX_KEYWORDS_PER_TITLE))
            addAll(extractKeywords(content.sectionName, 3))
        }
        for (k in keys) {
            keywordScoresMap[k] = (keywordScoresMap[k] ?: 0) + WEIGHT_CONSUMPTION
        }
        pruneKeywords()
        bump()
        persistAll()
    }

    /** استعلام بحث ناجح. وزنه **+5** — أعلى إشارة لأنّها نيّة صريحة. */
    fun onSearchQuery(query: String) {
        val keys = extractKeywords(query, 6)
        if (keys.isEmpty()) return
        for (k in keys) {
            keywordScoresMap[k] = (keywordScoresMap[k] ?: 0) + WEIGHT_SEARCH
        }
        pruneKeywords()
        bump()
        persistAll()
    }

    // ── استعلامات الأفنيتي ──────────────────────────────────────────

    /** أفنيتي قسم — مجموع نقاطه (0 إن لم يُسجَّل). */
    fun sectionAffinity(sectionId: String): Int = sectionScoresMap[sectionId] ?: 0

    /** أفنيتي كلمة مفتاحيّة — الإدخال يُطبَّع قبل البحث. */
    fun keywordAffinity(keyword: String): Int {
        val norm = normalizeText(keyword)
        if (norm.isEmpty()) return 0
        return keywordScoresMap[norm] ?: 0
    }

    fun typeAffinity(type: ContentType): Int = typeScoresMap[type] ?: 0

    /**
     * تقدير **رصيد كلّيّ** لعنصر محتوى:
     *   * نقاط قسمه، + نصف نقاط نوعه (عامل مُساند لا يطغى على القسم)،
     *   * + مجموع نقاط كلمات عنوانه المطابِقة للاهتمامات.
     *
     * النتيجة 0 لمحتوى لم يلامس أيّ اهتمام — وهو ما يسمح لطبقة الفرز
     * بترك نسبة استكشافيّة في الخلاصة.
     */
    fun contentAffinity(content: Content): Int {
        var score = 0
        if (content.section.trim().isNotEmpty()) {
            score += sectionScoresMap[content.section] ?: 0
        }
        score += ((typeScoresMap[content.type] ?: 0) * 0.5).roundToInt()
        for (k in extractKeywords(content.title, MAX_KEYWORDS_PER_TITLE)) {
            score += keywordScoresMap[k] ?: 0
        }
        return score
    }

    /** أعلى N قسم بحسب الأفنيتي. */
    fun topSections(limit: Int = 10): List<String> =
        sectionScoresMap.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    /** أعلى N كلمة بحسب الأفنيتي. */
    fun topKeywords(limit: Int = 15): List<String> =
        keywordScoresMap.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    // ── أدوات داخليّة ───────────────────────────────────────────────

    private fun pruneKeywords() {
        if (keywordScoresMap.size <= MAX_KEYWORDS) return
        val keep = keywordScoresMap.entries
            .sortedByDescending { it.value }
            .take(MAX_KEYWORDS)
            .associate { it.key to it.value }
        keywordScoresMap.clear()
        keywordScoresMap.putAll(keep)
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun persistAll() {
        runCatching {
            store.putJsonObject(KEY_SECTION_AFFINITY, JSONObject(sectionScoresMap as Map<*, *>))
            store.putJsonObject(KEY_KEYWORD_AFFINITY, JSONObject(keywordScoresMap as Map<*, *>))
            val encodedTypes = typeScoresMap.entries.associate { it.key.wire to it.value }
            store.putJsonObject(KEY_TYPE_AFFINITY, JSONObject(encodedTypes as Map<*, *>))
        }.onFailure { Log.d(TAG, "persist error: ${it.message}") }
    }

    private fun hydrateIntMap(key: String, target: MutableMap<String, Int>) {
        val json = store.getJsonObject(key) ?: return
        for (k in json.keys()) {
            val count = json.optInt(k, 0)
            if (count > 0) target[k] = count
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

    private fun parseType(raw: String): ContentType? =
        ContentType.entries.firstOrNull { it.wire == raw || it.name == raw }
}
