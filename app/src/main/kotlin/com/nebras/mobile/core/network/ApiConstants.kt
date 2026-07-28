package com.nebras.mobile.core.network

/**
 * كلّ عناوين الـ API ونقاط النهاية مركزيّة هنا — لا رابط مكتوب يدويّاً في
 * أيّ مكان آخر من التطبيق.
 */
object ApiConstants {

    // ── Base URL — قابل للتجاوز عند التشغيل ─────────────────────────────
    //
    // **لماذا لا يكون ثابتاً صلباً؟**
    //   * خادم Render Free يدخل Sleep بعد 15 دقيقة، وأوّل طلب يستغرق
    //     30–60 ثانية (cold start). نريد نقل الخدمة لاحقاً إلى Cloud Run
    //     بدون إصدار جديد ومراجعة متجر.
    //   * عند الترقية إلى Firebase Remote Config مرّر النتيجة إلى
    //     [setOverride] أثناء الـ bootstrap؛ كلّ الـ datasources تقرأ من
    //     [baseUrl] بلا حاجة لتعديل.
    private const val DEFAULT_BASE_URL = "https://nebras-backend.onrender.com/"

    @Volatile
    private var runtimeOverride: String? = null

    /**
     * يُستخدم في الـ bootstrap (مثلاً بعد قراءة Remote Config) لضبط عنوان
     * الخادم وقت التشغيل دون إعادة بناء. مرّر `null` للعودة إلى الافتراضيّ.
     */
    fun setOverride(url: String?) {
        runtimeOverride = url?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * عنوان الـ API الحاليّ بحسب الأولويّة:
     *   1. runtime override (مثلاً من Remote Config)
     *   2. الافتراضيّ المضمَّن في الكود
     */
    val baseUrl: String
        get() = runtimeOverride ?: DEFAULT_BASE_URL

    // ── المهلات ─────────────────────────────────────────────────────────
    // للعميل العامّ (طلبات JSON فقط). مسارات التنزيل/الوسائط الكبيرة
    // (audio/video/reader/download) تضبط مهلاتها الخاصّة (دقائق) ولا تعتمد
    // على هذه القيم.
    //
    // connectTimeout = 12s: على إنترنت ضعيف يفشل ربط TCP/TLS بسرعة فيبدأ
    // [RetryInterceptor] المحاولة بدل تعليق المستخدم 30 ثانية. إقلاع الخادم
    // البطيء يُغطّى بإعادة المحاولة لا بمهلة ربط طويلة.
    const val CONNECT_TIMEOUT_SECONDS = 12L
    const val RECEIVE_TIMEOUT_SECONDS = 30L
    const val SEND_TIMEOUT_SECONDS = 30L

    // ── إعادة المحاولة ──────────────────────────────────────────────────
    // RETRY_DELAY يُستعمل كـ«قاعدة» للتراجع الأُسّي مع jitter
    // (≈2s ثمّ 4s ثمّ 8s) بدل تأخير ثابت.
    const val MAX_RETRIES = 3
    const val RETRY_DELAY_MILLIS = 2_000L

    // ── المحتوى ─────────────────────────────────────────────────────────
    const val CONTENT = "/api/public/all/"
    fun contentDetail(id: Int): String = "$CONTENT$id/"

    // ── الأقسام ─────────────────────────────────────────────────────────
    const val MAIN_SECTIONS = "/api/public/sections/main/"
    fun mainSectionDetail(name: String): String = "$MAIN_SECTIONS$name/"

    const val SUB_SECTIONS = "/api/public/sections/sub/"
    fun subSectionsByMain(mainName: String): String = "$SUB_SECTIONS?main_section=$mainName"
    fun subSectionDetail(name: String): String = "$SUB_SECTIONS$name/"

    const val SECONDARY_SECTIONS = "/api/public/sections/secondary/"
    fun secondarySectionsBySub(subId: String): String = "$SECONDARY_SECTIONS?sub_section=$subId"

    // ── بيانات وصفيّة ───────────────────────────────────────────────────
    const val METADATA = "/api/public/metadata/"

    // ⛔ نقاط YouTube بقيت في العقد الخادميّ لكنّ التطبيق لا يستدعيها إطلاقاً
    // (YouTube مقطوع نهائياً — انظر ContentCompliance).

    // ── الملفّات (R2) ───────────────────────────────────────────────────
    const val FILES = "/api/public/files/"
    fun fileDetail(id: Int): String = "$FILES$id/"

    // ── الإشعارات ───────────────────────────────────────────────────────
    const val NOTIFICATIONS = "/api/notifications/devices/"
    fun notificationDetail(id: Int): String = "$NOTIFICATIONS$id/"
}
