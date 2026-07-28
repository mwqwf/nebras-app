@file:Suppress("TooManyFunctions")

package com.nebras.mobile.core.util

/**
 * TextNormalizer — أدوات نصّية عامّة تُستعمل من:
 *   * مُتتبّع السلوك (`UserBehaviorTracker`) لتوحيد مفاتيح الكلمات.
 *   * محرّك التوصيات (`RecommendationEngine`) لمقارنة العناوين بدقّة.
 *   * محرّك البحث الشامل (`GlobalSearchEngine`) لتطبيع الاستعلام وحساب
 *     درجة الصلة (Relevance Score) مع التسامح مع الأخطاء الإملائيّة
 *     البسيطة والبحث بجزء الكلمة (Substring Matching).
 *
 * لماذا أداة مستقلّة ومُجرَّدة؟ لأنّ قواعد تطبيع النصّ العربيّ (همزات،
 * تشكيل، ألف مقصورة، تاء مربوطة) يجب أن تكون **مرجعًا واحدًا** في التطبيق،
 * حتّى لا ينحرف تنفيذها بين الأماكن فينقسم ملف اهتمامات المستخدم أو تختلف
 * نتيجة البحث عن نتيجة التوصية.
 *
 * نقل حرفيّ لـ `lib/core/utils/text_normalizer.dart` — الدوالّ على مستوى
 * الملفّ تماماً كما في Dart.
 */

private val diacritics = listOf(
    // تشكيل عربي كامل + superscript alef + tatweel.
    "\u064B", "\u064C", "\u064D", "\u064E", "\u064F", "\u0650",
    "\u0651", "\u0652", "\u0670", "\u0640",
)

/**
 * رموز لاتيّة لها معادل صوتيّ عربيّ — تُستخدم في [phoneticKey] لتمكين
 * البحث عبر الخطّين (Transliteration matching). مثلاً: كتابة "bukhari"
 * تُطابق "البخاري" بعد تحويل كليهما إلى نفس المفتاح. الخريطة ليست كاملة بل
 * **قاموس فقرة مشترك بين الأبجديّتَين** كافٍ لأسماء المؤلّفين والكتب الأكثر
 * شيوعًا. تجنّبنا خوارزميّات معقّدة مثل Buckwalter الكامل لأنّ التعقيد لا
 * يبرّره العائد.
 */
private val arabicToLatinPhonetic = mapOf(
    'ا' to "a", 'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j", 'ح' to "h",
    'خ' to "kh", 'د' to "d", 'ذ' to "th", 'ر' to "r", 'ز' to "z", 'س' to "s",
    'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "a",
    'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k", 'ل' to "l", 'م' to "m",
    'ن' to "n", 'ه' to "h", 'و' to "w", 'ي' to "y", 'ء' to "a",
)

private val zeroWidthRegex = Regex("[\u200B-\u200F\u202A-\u202E\uFEFF]")
private val letterOrDigitRegex = Regex("[0-9A-Za-z\u0600-\u06FF]")
private val repeatedVowelRegex = Regex("([aeiouy])\\1+")
private val whitespaceRegex = Regex("\\s+")

private const val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"
private const val PERSIAN_INDIC_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/**
 * يُطبِّع نصًّا عربيًّا/لاتينيًّا بطريقة تنتج مفاتيح قابلة للمقارنة.
 *
 * قواعد التطبيع:
 *   * lowercase للاتينية.
 *   * إزالة كامل التشكيل + tatweel + **zero-width joiners** التي تتسرّب من
 *     لصق النصوص.
 *   * توحيد أشكال الهمزة والألف: أ/إ/آ/ٱ → ا.
 *   * توحيد الياء والتاء المربوطة: ى → ي، ة → ه، ؤ → و، ئ → ي.
 *   * **مقابلات فارسيّة شائعة**: ی → ي، ک → ك، گ → ك.
 *   * الأرقام العربيّة/الهنديّة تُحوَّل إلى أرقام لاتينيّة.
 */
fun normalizeText(input: String?): String {
    if (input == null) return ""
    var s = input.trim().lowercase()
    if (s.isEmpty()) return ""
    for (d in diacritics) {
        s = s.replace(d, "")
    }
    // Zero-width characters + LRM/RLM marks.
    s = zeroWidthRegex.replace(s, "")
    s = s
        // هَمَزات الألف.
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ٱ", "ا")
        // ياء / تاء مربوطة / ؤ / ئ.
        .replace("ى", "ي")
        .replace("ؤ", "و")
        .replace("ئ", "ي")
        .replace("ة", "ه")
        // فارسيّة.
        .replace("ی", "ي")
        .replace("ک", "ك")
        .replace("گ", "ك")
    // أرقام عربيّة/هنديّة ⇒ لاتينيّة (٠-٩ و ۰-۹).
    val buf = StringBuilder()
    var lastSpace = false
    for (c in s) {
        var out = c
        val iAr = ARABIC_INDIC_DIGITS.indexOf(c)
        if (iAr >= 0) out = (0x30 + iAr).toChar()
        val iPe = PERSIAN_INDIC_DIGITS.indexOf(c)
        if (iPe >= 0) out = (0x30 + iPe).toChar()
        val isLetterOrDigit = letterOrDigitRegex.containsMatchIn(out.toString())
        if (isLetterOrDigit) {
            buf.append(out)
            lastSpace = false
        } else if (!lastSpace) {
            buf.append(' ')
            lastSpace = true
        }
    }
    return buf.toString().trim()
}

/**
 * يُنتج **مفتاحًا صوتيًّا مشتركًا** بين العربيّة واللاتينيّة بحيث يتساوى عند
 * كتابة الاسم نفسه بأيّ خطّ. لا يُستعمل وحده للمطابقة؛ بل تُضيفه
 * [relevanceScore] كإشارة إضافيّة.
 */
fun phoneticKey(input: String?): String {
    val normalized = normalizeText(input)
    if (normalized.isEmpty()) return ""
    val buf = StringBuilder()
    for (c in normalized) {
        val mapped = arabicToLatinPhonetic[c]
        buf.append(mapped ?: c.toString())
    }
    var phonetic = buf.toString()
    // طيّ أحرف العلّة اللاتينيّة المتكرّرة (aa→a، ee→e، oo→o، uu→u).
    phonetic = repeatedVowelRegex.replace(phonetic, "$1")
    // تبسيط المسافات.
    phonetic = whitespaceRegex.replace(phonetic, " ").trim()
    return phonetic
}

/**
 * يُقسّم النصّ إلى **رموز دلاليّة** (tokens) قابلة للتخزين كاهتمامات.
 * يُلغي الكلمات القصيرة جدًّا (<3 حروف) وكلمات الوقف الشائعة.
 */
fun extractKeywords(input: String?, maxTokens: Int = 12): List<String> {
    val normalized = normalizeText(input)
    if (normalized.isEmpty()) return emptyList()
    val parts = normalized.split(" ")
    val out = ArrayList<String>()
    for (raw in parts) {
        val w = raw.trim()
        if (w.length < 3) continue
        if (stopWords.contains(w)) continue
        if (out.contains(w)) continue
        out.add(w)
        if (out.size >= maxTokens) break
    }
    return out
}

/** Jaccard overlap على الرموز الدلاليّة — نتيجة بين 0 و 1. */
fun keywordOverlapScore(a: String?, b: String?): Double {
    val setA = extractKeywords(a).toSet()
    val setB = extractKeywords(b).toSet()
    if (setA.isEmpty() || setB.isEmpty()) return 0.0
    val intersect = setA.intersect(setB).size
    val union = setA.union(setB).size
    return if (union == 0) 0.0 else intersect.toDouble() / union.toDouble()
}

/**
 * يفحص إن كان [haystack] يحوي [needle] بعد التطبيع على الطرفين.
 * مفيد لبحث جزئيّ يتجاهل التشكيل/الهمزات.
 */
fun fuzzyContains(haystack: String?, needle: String?): Boolean {
    val h = normalizeText(haystack)
    val n = normalizeText(needle)
    if (n.isEmpty() || h.isEmpty()) return false
    if (h.contains(n)) return true
    for (tk in extractKeywords(needle, maxTokens = 6)) {
        if (tk.length < 3) continue
        if (h.contains(tk)) return true
    }
    return false
}

/**
 * يفحص إن كان [haystack] يحوي أيًّا من كلمات [needle] بعد التطبيع.
 * يختلف عن [fuzzyContains] بأنّه يكتفي بجزء (substring) داخل أيّ كلمة من
 * الـ haystack، ولو كان طوله 2 فقط — مفيد لـ autocomplete حيّ.
 */
fun substringMatches(haystack: String?, needle: String?): Boolean {
    val h = normalizeText(haystack)
    val n = normalizeText(needle)
    if (n.isEmpty() || h.isEmpty()) return false
    // تطابق كامل (أسرع).
    if (h.contains(n)) return true
    // لو كان needle قصيرًا جدًّا لا نسمح بالمطابقة على كلمة منفصلة
    // لتجنّب ضوضاء (حرف واحد يُطابق كلّ شيء).
    if (n.length < 2) return false

    val haystackTokens = h.split(" ")
    val needleTokens = n.split(" ").filter { it.length >= 2 }
    if (needleTokens.isEmpty()) return false

    // كلّ كلمة من الاستعلام يجب أن توجد ضمن إحدى كلمات الـ haystack
    // (على الأقل كـ substring من 2 حروف).
    for (nTok in needleTokens) {
        var found = false
        for (hTok in haystackTokens) {
            if (hTok.contains(nTok)) {
                found = true
                break
            }
        }
        if (!found) return false
    }
    return true
}

/**
 * نسبة تطابق جزئيّ **طرفيّ** بين استعلام ومرشّح على مستوى الكلمات.
 * تُرجع عددًا بين 0 و 1 يمثّل *كسر* كلمات الاستعلام التي وُجدت ضمن إحدى
 * كلمات المرشَّح كـ substring بطول ≥ 2 حروف.
 */
fun partialTokenScore(query: String?, candidate: String?): Double {
    val q = normalizeText(query)
    val c = normalizeText(candidate)
    if (q.isEmpty() || c.isEmpty()) return 0.0

    val qTokens = q.split(" ").filter { it.length >= 2 }
    if (qTokens.isEmpty()) return 0.0
    val cTokens = c.split(" ").filter { it.isNotEmpty() }
    if (cTokens.isEmpty()) return 0.0

    var matched = 0
    for (qt in qTokens) {
        for (ct in cTokens) {
            if (ct.contains(qt)) {
                matched++
                break
            }
        }
    }
    if (matched == 0) return 0.0
    return matched.toDouble() / qTokens.size.toDouble()
}

/**
 * مسافة تحرير (Levenshtein) بين سلسلتَين — بعد التطبيع. تنفيذ O(m×n) بسيط
 * ومناسب للنصوص القصيرة (عناوين وكلمات بحث) بلا مكتبات خارجيّة.
 */
fun levenshteinDistance(a: String, b: String): Int {
    val s = normalizeText(a)
    val t = normalizeText(b)
    if (s == t) return 0
    if (s.isEmpty()) return t.length
    if (t.isEmpty()) return s.length

    val m = s.length
    val n = t.length
    val prev = IntArray(n + 1)
    val curr = IntArray(n + 1)
    for (j in 0..n) {
        prev[j] = j
    }
    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (s[i - 1] == t[j - 1]) 0 else 1
            val del = prev[j] + 1
            val ins = curr[j - 1] + 1
            val sub = prev[j - 1] + cost
            var min = if (del < ins) del else ins
            if (sub < min) min = sub
            curr[j] = min
        }
        for (j in 0..n) {
            prev[j] = curr[j]
        }
    }
    return prev[n]
}

/**
 * حساب درجة الصلة بين استعلام [query] ومرشّح [candidate] — النتيجة مُعيَّرة
 * بين 0 و 1 (كلّما ارتفعت زادت الصلة).
 *
 * ترتيب الإشارات التي يزنها المحرّك:
 *   * **تطابق تامّ** بعد التطبيع → 1.0.
 *   * **بداية تطابق** (startsWith) → 0.90.
 *   * **احتواء كامل** → 0.78.
 *   * **تطابق جميع رموز الاستعلام كـ tokens** → 0.72.
 *   * **Jaccard overlap عالي** → حتى 0.6.
 *   * **Substring على مستوى الكلمة** → 0.52.
 *   * **تشابه تحريريّ (Levenshtein)** قريب جدًّا → ≈ 0.45.
 */
fun relevanceScore(query: String?, candidate: String?): Double {
    val q = normalizeText(query)
    val c = normalizeText(candidate)
    if (q.isEmpty() || c.isEmpty()) return 0.0
    if (q == c) return 1.0
    if (c.startsWith(q)) return 0.90
    if (c.contains(q)) return 0.78

    // تطابق كلّ رموز الاستعلام داخل المرشّح.
    val qTokens = extractKeywords(query, maxTokens = 5)
    if (qTokens.isNotEmpty()) {
        var allFound = true
        for (t in qTokens) {
            if (!c.contains(t)) {
                allFound = false
                break
            }
        }
        if (allFound) return 0.72
    }

    // Jaccard على tokens — نقلّص المساهمة إلى 0.6 كحدّ أقصى.
    val overlap = keywordOverlapScore(query, candidate)
    if (overlap >= 0.5) return 0.60 + (overlap - 0.5) * 0.2 // 0.60..0.70
    if (overlap >= 0.25) return 0.50 + (overlap - 0.25) * 0.4 // 0.50..0.60

    // Substring على مستوى الكلمة — يمسك "فقه" مع "الفقه الأكبر".
    if (substringMatches(c, q)) return 0.52

    // مسافة تحرير — نتسامح بشكل مقنّن لتجنّب المطابقات الخاطئة.
    if (q.length >= 4 && c.length >= q.length) {
        val dist = levenshteinDistance(q, c)
        val maxLen = if (q.length > c.length) q.length else c.length
        val ratio = 1 - (dist.toDouble() / maxLen.toDouble())
        if (ratio >= 0.75) return 0.40 + (ratio - 0.75) * 0.2 // 0.40..0.45
    }

    // مطابقة جزئيّة مرنة — درجة منخفضة (0.20..0.38) لتظهر في أسفل النتائج.
    val partial = partialTokenScore(query, candidate)
    if (partial >= 0.5) return 0.30 + (partial - 0.5) * 0.16 // 0.30..0.38
    if (partial > 0) return 0.20 + partial * 0.10 // 0.20..0.30

    // إشارة أخيرة: مطابقة صوتيّة عابرة للخطَّين — نخفّضها عمدًا (≤ 0.28).
    val pq = phoneticKey(query)
    val pc = phoneticKey(candidate)
    if (pq.isNotEmpty() && pc.isNotEmpty() && pq != pc) {
        if (pc.contains(pq)) return 0.28
        if (pq.length >= 4) {
            val dist = levenshteinDistance(pq, pc)
            val maxLen = if (pq.length > pc.length) pq.length else pc.length
            val ratio = 1 - (dist.toDouble() / maxLen.toDouble())
            if (ratio >= 0.70) return 0.18 + (ratio - 0.70) * 0.25 // 0.18..0.26
        }
    }

    return 0.0
}

/**
 * قائمة كلمات وقف مُختصرة — عربيّة وإنجليزيّة وفرنسيّة.
 * ليست شاملة عن قصد؛ الهدف تصفية أعلى تردّد فقط.
 */
private val stopWords = setOf(
    // عربي
    "في", "من", "على", "الى", "او", "ثم", "هذا", "هذه", "ذلك", "تلك",
    "عن", "انا", "نحن", "هو", "هي", "هم", "كل", "كذلك", "ما",
    "لم", "لا", "ليس", "كان", "قد", "ولا", "ولم", "بعد", "قبل",
    // إنجليزي
    "the", "and", "for", "you", "with", "from", "this", "that",
    "are", "was", "were", "have", "has", "but", "not", "any",
    "all", "one", "two", "its", "our", "their",
    // فرنسي
    "les", "des", "une", "que", "qui", "pour", "avec", "dans",
    "sur", "par", "son", "ses", "est", "sont", "aux",
)
