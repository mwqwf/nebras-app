package com.nebras.mobile.core.theme

import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نقل `lib/core/theme/provider/theme_provider.dart` (`ThemeProvider`).
 *
 * `ChangeNotifier` → `StateFlow<ThemeMode>`. المفتاحان محفوظان بنفس الأسماء
 * ونفس المنطق:
 *   • `theme_mode`  : 'system' | 'light' | 'dark' (المصدر الأساسيّ).
 *   • `is_dark_mode`: bool قديم يبقى متزامناً للتوافق الخلفيّ.
 * وعند غياب `theme_mode` ووجود `is_dark_mode` نُرحّل المستخدم القديم إلى
 * فاتح/داكن صريح بدل «تلقائيّ».
 */
class ThemeController(private val store: LocalStore) {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    /** الوضع الحاليّ (تلقائيّ/فاتح/داكن). */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        load()
    }

    /** متوافق مع الشيفرة القديمة: صحيح فقط إن كان الوضع داكناً صراحةً. */
    val isDarkMode: Boolean get() = _themeMode.value == ThemeMode.DARK

    /** هل الوضع الحاليّ «تلقائيّ»؟ */
    val isSystemMode: Boolean get() = _themeMode.value == ThemeMode.SYSTEM

    /** يحسب اللون الفعليّ (داكن/فاتح) بحسب الوضع المختار وسطوع النظام. */
    fun resolveIsDark(platformIsDark: Boolean): Boolean = when (_themeMode.value) {
        ThemeMode.SYSTEM -> platformIsDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    /** تحميل التفضيل المحفوظ (مع ترحيل المفتاح القديم). */
    fun load() {
        val saved = store.getString(LocalStoreKeys.THEME_MODE)
        _themeMode.value = when {
            saved != null -> ThemeMode.parse(saved)
            store.contains(LocalStoreKeys.IS_DARK_MODE) ->
                // ترحيل: المستخدم القديم كان على فاتح/داكن صريح.
                if (store.getBool(LocalStoreKeys.IS_DARK_MODE, false)) {
                    ThemeMode.DARK
                } else {
                    ThemeMode.LIGHT
                }
            else -> ThemeMode.SYSTEM
        }
    }

    /** يبدّل بين فاتح/داكن صراحةً (يُستعمل من مفتاح التبديل البسيط). */
    fun toggleTheme() {
        _themeMode.value = if (isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK
        persist()
    }

    fun setDarkMode(isDark: Boolean) {
        _themeMode.value = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT
        persist()
    }

    /** يضبط الوضع الثلاثيّ (تلقائيّ/فاتح/داكن). */
    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        persist()
    }

    private fun persist() {
        val mode = _themeMode.value
        store.putString(LocalStoreKeys.THEME_MODE, mode.wire)
        // نُبقي المفتاح القديم متزامناً للتوافق مع أيّ كود يقرؤه.
        store.putBool(LocalStoreKeys.IS_DARK_MODE, mode == ThemeMode.DARK)
    }
}
