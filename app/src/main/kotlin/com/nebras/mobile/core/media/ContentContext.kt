package com.nebras.mobile.core.media

/**
 * سياق تشغيل المحتوى (Content Context).
 *
 * يُستخدم من قِبَل مُشغّلات الصوت والفيديو ليفهما "من أين جاء المستخدم":
 * - [FOR_YOU] : من صفحة "لك" (موجز التوصيات) — نُفعِّل Smart Auto-Play.
 * - [SEARCH]  : من نتائج البحث — نُفعِّل Smart Auto-Play أيضاً.
 * - [SECTION] : من تصفّح قسم (قائمة تشغيل مبنيّة مسبقاً) — لا نحتاج auto-fill.
 * - [SAVED]   : من المحفوظات — نلتزم بالقائمة ولا نُضيف عشوائيّاً.
 * - [HOME]    : من الشاشة الرئيسيّة — Smart Auto-Play بسياسة متحفّظة.
 * - [DIRECT]  : فتح مباشر (إشعار/رابط) — سلوك "مقطع واحد" ثمّ انتهاء.
 *
 * ⚠ عند وجود قائمة تشغيل حقيقيّة نتنقّل داخلها أولاً (Next/Prev). فقط إذا
 * لم يتبقَّ عنصر تالٍ نُطلق `onExhaustedAutoFill` — ولا يحدث ذلك إلّا في
 * السياقات التي تسمح به (انظر [allowsSmartAutoPlay]).
 */
enum class ContentContext {
    FOR_YOU,
    SEARCH,
    SECTION,
    SAVED,
    HOME,
    DIRECT,
    ;

    /**
     * هل يسمح هذا السياق بتشغيل اقتراحات ذكيّة عند نفاد القائمة؟
     * القاعدة: نعم في "لك / بحث / رئيسية"، ولا في "قسم / محفوظ / مباشر".
     */
    val allowsSmartAutoPlay: Boolean
        get() = when (this) {
            FOR_YOU, SEARCH, HOME -> true
            SECTION, SAVED, DIRECT -> false
        }
}
