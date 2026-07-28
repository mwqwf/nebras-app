package com.nebras.mobile.core.data

import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.PendingTakedownService

/**
 * 🛡️ حارس الامتثال للحقوق (Intellectual Property / Google Play / DMCA).
 *
 * طبقة واحدة موحَّدة يستدعيها كلّ حُرّاس القوائم (`HomeDatasource`،
 * `SearchDatasource`) بدل تكرار المنطق في مكانين والانحراف بينهما.
 *
 * السياسة (متوافقة مع كون ~90% من المحتوى من مصادر حرّة):
 *   • **السماح الافتراضيّ**: محتوى بلا `license_status` صريح يُعرَض.
 *   • **حجب صريح** لأيّ عنصر تضع اللوحة حالته ضمن كلمات تدلّ على قيد حقوق.
 *   • **حجب المصادر الخارجيّة** (archive.org + منصّات البثّ المملوكة لغير
 *     التطبيق مثل YouTube) — دفاع عميق فوق ما يفعله `Content.fromJson`.
 *   • **حجب الروابط غير الصالحة** (فارغة أو ليست http/https).
 */
object ContentCompliance {

    /**
     * كلمات مفتاحيّة **حاسمة** في `license_status` تعني أنّ العنصر مرفوض/
     * منتهَك ويجب حجبه عن الجميع (تنفيذ بلاغ صحيح أو DMCA takedown).
     *
     * مقصودة دقيقة لا واسعة: بوّابة الترخيص في اللوحة هي المرجع الذي يقرّر
     * القبول عند الإدخال؛ أمّا هذا الحارس فهو **شبكة أمان للإزالة** فقط.
     * لذا لا نُدرج كلمات عامّة مثل `copyright` أو `restricted` التي قد ترد
     * ضمن وصف ترخيص مشروع (مثل «no known copyright») فتحجبه خطأً.
     */
    private val BLOCKED_LICENSE_KEYWORDS = listOf(
        "rejected",
        "dmca",
        "takedown",
        "infring", // infringing / infringement
        "copyright_claim",
        "copyrighted_explicit",
    )

    /**
     * مضيفات محظورة: الأرشيف + منصّات بثّ خارجيّة لا يملكها التطبيق.
     * متطابقة مع قائمة `Content` لكنها هنا حارس مستقلّ يعمل حتى لو وصل
     * العنصر من مسار لم يمرّ عبر `Content.fromJson`.
     */
    private val BLOCKED_HOSTS = listOf(
        "archive.org",
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "googlevideo.com",
        "vimeo.com",
        "dailymotion.com",
        "dai.ly",
        "tiktok.com",
        "facebook.com",
        "fb.watch",
        "instagram.com",
        "twitch.tv",
        "soundcloud.com",
    )

    /**
     * هل العنصر متوافق مع الحقوق وقابل للتشغيل؟
     * (لا يتضمّن فحص `HiddenContentService` — ذاك تفضيل عرض خاصّ بالمستخدم
     * يبقى في حُرّاس الـ datasource.)
     */
    fun isRightsCompliant(item: Content): Boolean {
        val url = item.sourceUrl?.trim().orEmpty()
        // 1) رابط صالح إلزاميّ — وإلا "No source available".
        if (url.isEmpty()) return false
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false

        val lowerUrl = url.lowercase()
        // 2) حجب المصادر الخارجيّة (أرشيف + منصّات بثّ).
        for (host in BLOCKED_HOSTS) {
            if (lowerUrl.contains(host)) return false
        }

        // 3) حجب صريح بحسب حالة الترخيص (DMCA / حقوق نشر).
        val status = item.licenseStatus.trim().lowercase()
        if (status.isNotEmpty()) {
            for (keyword in BLOCKED_LICENSE_KEYWORDS) {
                if (status.contains(keyword)) return false
            }
        }

        // 4) إخفاء عالميّ مؤقّت لكلّ محتوى ورد عليه بلاغ حقوق نشر — حتى
        //    مراجعة المالك. يختفي تلقائياً من رؤية كلّ المستخدمين فور وصول
        //    أوّل بلاغ، ويعود إن قرّر المالك أنّ البلاغ كاذب.
        if (PendingTakedownService.isPending(item.id)) return false

        return true
    }
}
