package com.nebras.mobile.core.config

import com.nebras.mobile.BuildConfig

/**
 * التحكم في استدعاءات الـ HTTP القديمة (Render / nebras-backend).
 *
 * - في **التطوير (debug)** لا تُستدعى افتراضيًا حتى لا يعلق التطبيق على timeouts.
 * - في **الإصدار (release)** تبقى مفعّلة كالسابق.
 *
 * بديل `--dart-define=USE_RENDER_BACKEND`: متغيّر بيئة/خاصيّة نظام يُقرأ عند
 * التشغيل، فيمكن تجربة Render أثناء التطوير بلا إعادة بناء:
 * `adb shell setprop debug.nebras.render true`
 */
val isLegacyRenderBackendEnabled: Boolean
    get() {
        val v = runCatching {
            System.getProperty("debug.nebras.render").orEmpty()
        }.getOrDefault("")
        if (v == "true" || v == "1") return true
        if (v == "false" || v == "0") return false
        return !BuildConfig.DEBUG
    }
