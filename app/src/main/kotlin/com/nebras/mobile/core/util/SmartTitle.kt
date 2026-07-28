package com.nebras.mobile.core.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.text.Charsets

/**
 * 🧠 استخراج عنوان نظيف من اسم ملف — بلا تدخل بشري.
 *
 * يعالج الأنماط الشائعة في الملفات القادمة من المسجّلات وواتساب ومواقع
 * التنزيل: يزيل الامتداد والترقيم التسلسلي وبصمات المواقع والوسوم الدعائية
 * والفواصل الآلية، ويعيد عنواناً بشرياً مقروءاً.
 *
 * إن كان الاسم آلياً بحتاً لا يحمل معنى (مثل `AUD-20240101-WA0001`) يعيد
 * `""` — فالأصح ترك الحقل فارغاً ليكتبه الإنسان بدل اقتراح خردة.
 *
 * نسخ متطابقة من هذا الملف في مشاريع منبر — عدّلها جميعاً معاً.
 */

private val pathSeparatorRegex = Regex("""[/\\]""")

/** أسماء آلية بحتة — لا شيء ذا معنى يُستخرج منها. */
private val autoPatterns = listOf(
    // واتساب: AUD-20240101-WA0001 وأخواتها + "WhatsApp Audio 2024-01-01 at…"
    Regex("""^(AUD|VID|PTT|IMG|DOC|GIF)[-_]\d{8}[-_]WA\d+""", RegexOption.IGNORE_CASE),
    Regex("""^WhatsApp\s+(Audio|Video|Ptt|Image)\b""", RegexOption.IGNORE_CASE),
    // مسجّلات: Recording 12 / New Recording / record_20240101_123456
    Regex("""^(new\s+)?record(ing)?[-_\s]*[\d_.\-\s]*$""", RegexOption.IGNORE_CASE),
    // أسماء عامة مرقّمة: Voice 003 / Audio 1 / Track05 / ملف 2 / تسجيل 7
    Regex(
        """^(voice|audio|video|sound|note|memo|track|file|item|untitled""" +
            """|تسجيل|مقطع|ملف|صوت|بدون\s*عنوان)[-_\s]*[\d\u0660-\u0669_.\-\s]*$""",
        RegexOption.IGNORE_CASE,
    ),
    // أرقام/رموز فقط (طوابع زمنية 20240101_123456 وأمثالها)
    Regex("""^[\d\u0660-\u0669\s_.\-()~]+$"""),
    // أجهزة تسجيل: REC001 / MIC_12 / ZOOM0004 / DS300012
    Regex("""^(REC|MIC|ZOOM|DS|DM|VN)[-_]?\d+$""", RegexOption.IGNORE_CASE),
)

/** محتوى دعائي/تقني داخل الأقواس ⇒ يُحذف القوس كاملاً. */
private val junkInsideRegex = Regex(
    """(www\.|https?:|\.com|\.net|\.org|\.info|kbps|kb/s|\d{3,4}p\b""" +
        """|mp3|mp4|m4a|wav|flac|\bhd\b|\bhq\b|\b4k\b|official|lyrics""" +
        """|audio\s*only|youtube|download|free|copy|نسخة|تحميل|موقع|بجودة|حصري)""",
    RegexOption.IGNORE_CASE,
)

private val bracketRegex = Regex("""[\[({]([^\])}]*)[\])}]""")

private val siteStampRegex = Regex(
    """(^|\s)(www\.)?[\w-]+\.(com|net|org|info|me|tv|cc)(?=\s|$)""",
    RegexOption.IGNORE_CASE,
)

/** ترقيم تسلسلي في البداية: "01 - " / "(12) " / "003." / "٧ ـ " */
private val leadingIndexRegex = Regex(
    """^[\s\-–—ـ_.]*[(\[]?\s*[0-9\u0660-\u0669]{1,4}\s*[)\]]?[\s\-–—ـ_.]+""",
)

private val decorativeSeparatorRegex = Regex("""[_~•·]+""")
private val innerDotRegex = Regex("""(?<=\S)\.(?=\S)""")
private val bitrateTagRegex = Regex("""\b(64|96|128|192|256|320)\s?kbps\b""", RegexOption.IGNORE_CASE)
private val multiSpaceRegex = Regex("""\s+""")
private val edgeSymbolsRegex = Regex(
    """^[\s\-–—ـ_.,،؛;:]+|[\s\-–—ـ_.,،؛;:]+$""",
)
private val hasLetterRegex = Regex("""[A-Za-z\u0600-\u06FF]""")
private val underscoreOrDashRegex = Regex("""[_\-]+""")

fun smartTitleFromFileName(fileName: String): String {
    var s = fileName.trim()
    if (s.isEmpty()) return ""

    // اسم الملف فقط (بلا مسار) ثم بلا امتداد.
    s = s.split(pathSeparatorRegex).last()
    val dot = s.lastIndexOf('.')
    if (dot > 0) s = s.substring(0, dot)

    // فكّ ترميز الروابط (%20 → مسافة) إن وُجد. الفكّ يرمي على خليط عربي غير
    // مرمَّز + %20، فنسقط إلى استبدال المسافة المرمّزة فقط.
    if (s.contains("%")) {
        s = try {
            decodeUriComponent(s)
        } catch (_: Exception) {
            s.replace("%20", " ")
        }
    }

    for (p in autoPatterns) {
        if (p.containsMatchIn(s)) return ""
    }

    // أقواس بمحتوى دعائي/تقني تُحذف كاملة؛ الأقواس ذات النص العادي تبقى.
    s = bracketRegex.replace(s) { m ->
        val inner = m.groupValues.getOrNull(1) ?: ""
        if (junkInsideRegex.containsMatchIn(inner)) " " else m.value
    }

    // بصمة موقع طليقة في الاسم: "site.com - العنوان" أو العكس.
    s = siteStampRegex.replace(s, " ")

    // ترقيم تسلسلي في البداية.
    var i = 0
    while (i < 2 && leadingIndexRegex.containsMatchIn(s)) {
        s = leadingIndexRegex.replaceFirst(s, "")
        i++
    }

    // فواصل آلية إلى مسافات: الشرطة السفلية والنقاط الداخلية والرموز الزخرفية.
    s = decorativeSeparatorRegex.replace(s, " ")
    s = innerDotRegex.replace(s, " ")

    // وسوم جودة علقت وسط الاسم.
    s = bitrateTagRegex.replace(s, " ")

    // اسم بلا مسافات إطلاقاً وفيه شرطات؟ فالشرطات فواصل لا جزء من العنوان.
    if (!s.contains(" ") && s.contains("-")) {
        s = s.replace("-", " ")
    }

    // تنظيف نهائي: مسافات مكرّرة وحواف رموز.
    s = multiSpaceRegex.replace(s, " ").trim()
    s = edgeSymbolsRegex.replace(s, "").trim()

    // لا حروف عربية أو لاتينية باقية؟ إذن ليس عنواناً.
    if (!hasLetterRegex.containsMatchIn(s)) return ""
    if (s.length < 2) return ""
    return s
}

/**
 * مثل [smartTitleFromFileName] لكن لا يعيد `""` أبداً — يسقط إلى قصّ
 * الامتداد فقط. للمسارات الجماعية (رفع متعدد) حيث العنوان الفارغ يعطّل.
 */
fun smartTitleOrBasename(fileName: String): String {
    val smart = smartTitleFromFileName(fileName)
    if (smart.isNotEmpty()) return smart
    var s = fileName.split(pathSeparatorRegex).last()
    val dot = s.lastIndexOf('.')
    if (dot > 0) s = s.substring(0, dot)
    return multiSpaceRegex.replace(underscoreOrDashRegex.replace(s, " "), " ").trim()
}

/**
 * نظير `Uri.decodeComponent` في Dart: يفكّ `%XX` فقط (لا يحوّل `+` إلى
 * مسافة كما يفعل `URLDecoder`)، ويرمي عند ترميز فاسد أو UTF-8 غير صالح —
 * وهو ما يعتمد عليه المستدعي للسقوط إلى استبدال `%20` وحدها.
 */
private fun decodeUriComponent(input: String): String {
    val out = ByteArrayOutputStream(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '%') {
            if (i + 2 >= input.length) throw IllegalArgumentException("bad percent escape")
            val value = input.substring(i + 1, i + 3).toInt(16) // يرمي على hex فاسد
            out.write(value)
            i += 3
        } else {
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
    }
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(out.toByteArray())).toString()
}
