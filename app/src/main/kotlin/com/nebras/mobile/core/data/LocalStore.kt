package com.nebras.mobile.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * التخزين المحلّي الموحَّد — **بديل كامل لـ Hive + shared_preferences**.
 *
 * نسخة Flutter كانت تستعمل مصدرَين:
 *   • `shared_preferences` للتفضيلات القياسيّة (السمة، نمط الأقسام، آخر
 *     صفحة PDF، مواضع التشغيل، وضع القراءة…).
 *   • صناديق Hive: `content_metadata_cache`, `downloads`, `saved_items`,
 *     `notifications`, `hidden_content`.
 *
 * هنا نوحّدهما في ملفّ `SharedPreferences` واحد اسمه [PREFS_NAME] مع تسلسل
 * JSON يدويّ (`org.json`) لما كان كائناً في Hive. لماذا لا نقرأ صناديق Hive؟
 * صيغتها ثنائيّة خاصّة، والبيانات القابلة لإعادة البناء (كاش المحتوى) تُجلب
 * من Firestore عند أوّل تشغيل تماماً كتركيب جديد — انظر §5.1 من عقد التحويل.
 *
 * **الترحيل**: عند أوّل إنشاء نقرأ `FlutterSharedPreferences.xml` وننسخ كلّ
 * مفتاح يبدأ بـ `flutter.` بعد إسقاط البادئة (بلا حذف الأصل، للتراجع). نفكّ
 * أيضاً ترميزات إضافة Flutter الخاصّة للأعداد العشريّة والقوائم.
 *
 * **[revision]**: عدّاد يتغيّر عند كلّ كتابة — يتيح للواجهات مراقبة التخزين
 * بـ `collectAsStateWithLifecycle()` بدل `ValueListenableBuilder` في Hive.
 */
class LocalStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0L)

    /** يتغيّر عند كلّ عمليّة كتابة/حذف — مصدر إعادة بناء الواجهات. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        migrateFromFlutterIfNeeded(context)
    }

    // ── قراءة/كتابة الأنواع الأوّليّة ────────────────────────────────

    fun getString(key: String, default: String? = null): String? =
        runCatching { prefs.getString(key, default) }.getOrDefault(default)

    fun putString(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        prefs.edit().putString(key, value).apply()
        bump()
    }

    fun getBool(key: String, default: Boolean = false): Boolean =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    fun putBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        bump()
    }

    fun getLong(key: String, default: Long = 0L): Long =
        runCatching { prefs.getLong(key, default) }.getOrElse {
            // مفتاح كُتب كـ Int في ترحيل قديم.
            runCatching { prefs.getInt(key, default.toInt()).toLong() }.getOrDefault(default)
        }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
        bump()
    }

    fun getInt(key: String, default: Int = 0): Int = getLong(key, default.toLong()).toInt()

    fun putInt(key: String, value: Int) = putLong(key, value.toLong())

    /**
     * الأعداد العشريّة تُخزَّن كنصّ (`Double.toString`) لأنّ
     * `SharedPreferences` لا يعرف `double`، والتحويل إلى `float` يفقد الدقّة
     * المطلوبة في مواضع التشغيل ونسب التقدّم.
     */
    fun getDouble(key: String, default: Double = 0.0): Double {
        val raw = getString(key) ?: return default
        return raw.trim().toDoubleOrNull() ?: default
    }

    fun putDouble(key: String, value: Double) = putString(key, value.toString())

    // ── JSON (بديل كائنات Hive) ─────────────────────────────────────

    fun getJsonObject(key: String): JSONObject? {
        val raw = getString(key) ?: return null
        if (raw.isBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun putJsonObject(key: String, value: JSONObject?) {
        if (value == null) {
            remove(key)
            return
        }
        putString(key, value.toString())
    }

    fun getJsonArray(key: String): JSONArray? {
        val raw = getString(key) ?: return null
        if (raw.isBlank()) return null
        return runCatching { JSONArray(raw) }.getOrNull()
    }

    fun putJsonArray(key: String, value: JSONArray?) {
        if (value == null) {
            remove(key)
            return
        }
        putString(key, value.toString())
    }

    /** قائمة نصوص فوق [getJsonArray] — بديل `prefs.getStringList` في Dart. */
    fun getStringList(key: String): List<String> {
        val array = getJsonArray(key) ?: return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val item = array.opt(i) ?: continue
            out.add(item.toString())
        }
        return out
    }

    fun putStringList(key: String, values: Iterable<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        putJsonArray(key, array)
    }

    // ── إدارة المفاتيح ──────────────────────────────────────────────

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
        bump()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun keys(): Set<String> = prefs.all.keys.toSet()

    /** يحذف كلّ المفاتيح التي تبدأ بـ [prefix] (تنظيف كاش ميزة كاملة). */
    fun removeByPrefix(prefix: String) {
        val editor = prefs.edit()
        var removed = false
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith(prefix)) {
                editor.remove(key)
                removed = true
            }
        }
        if (removed) {
            editor.apply()
            bump()
        }
    }

    /** يمسح كلّ التخزين المحلّي (مسح بيانات التخصيص / حذف الحساب). */
    fun clear() {
        prefs.edit().clear().apply()
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    // ── ترحيل مفاتيح نسخة Flutter ───────────────────────────────────

    /**
     * ينسخ مفاتيح `flutter.*` مرّة واحدة فقط (يُعلَّم بـ [MIGRATION_FLAG]).
     * إضافة `shared_preferences` تُرمّز بعض الأنواع بنصّ ذي بادئة:
     *   • عدد عشريّ  → بادئة العشريّ ثمّ النصّ الرقميّ.
     *   • قائمة نصوص → بادئة القائمة ثمّ JSON.
     * نفكّها هنا إلى صيغتنا. الأصل يبقى كما هو للتراجع.
     */
    private fun migrateFromFlutterIfNeeded(context: Context) {
        if (prefs.getBoolean(MIGRATION_FLAG, false)) return
        runCatching {
            val flutterPrefs = context.getSharedPreferences(
                FLUTTER_PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            val all = flutterPrefs.all
            val editor = prefs.edit()
            var moved = 0
            for ((rawKey, value) in all) {
                if (!rawKey.startsWith(FLUTTER_KEY_PREFIX)) continue
                val key = rawKey.substring(FLUTTER_KEY_PREFIX.length)
                if (key.isEmpty()) continue
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putLong(key, value.toLong())
                    is Float -> editor.putString(key, value.toDouble().toString())
                    is Set<*> -> {
                        val array = JSONArray()
                        value.forEach { item -> item?.let { array.put(it.toString()) } }
                        editor.putString(key, array.toString())
                    }
                    is String -> editor.putString(key, decodeFlutterString(value))
                    else -> continue
                }
                moved++
            }
            editor.putBoolean(MIGRATION_FLAG, true)
            editor.apply()
            Log.d(TAG, "migrated $moved keys from FlutterSharedPreferences")
        }.onFailure {
            // الترحيل رفاهيّة: فشله لا يمنع الإقلاع — نعلّمه منجزاً كي لا يتكرّر.
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
            Log.d(TAG, "flutter prefs migration skipped: ${it.javaClass.simpleName}")
        }
        bump()
    }

    private fun decodeFlutterString(value: String): String = when {
        value.startsWith(FLUTTER_DOUBLE_PREFIX) ->
            value.substring(FLUTTER_DOUBLE_PREFIX.length)
        value.startsWith(FLUTTER_LIST_PREFIX) ->
            value.substring(FLUTTER_LIST_PREFIX.length)
        else -> value
    }

    companion object {
        private const val TAG = "LocalStore"

        /** ملفّ التفضيلات الخاصّ بالنسخة الأصليّة (Kotlin). */
        const val PREFS_NAME = "nebras_native_prefs"

        /** ملفّ إضافة `shared_preferences` في نسخة Flutter. */
        private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
        private const val FLUTTER_KEY_PREFIX = "flutter."
        private const val MIGRATION_FLAG = "__flutter_prefs_migrated_v1"

        // بادئات ترميز إضافة shared_preferences (ثابتة عبر إصداراتها).
        private const val FLUTTER_DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
        private const val FLUTTER_LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

        @Volatile
        private var instance: LocalStore? = null

        fun get(context: Context): LocalStore = instance ?: synchronized(this) {
            instance ?: LocalStore(context.applicationContext).also { instance = it }
        }
    }
}

/**
 * مفاتيح التخزين المشتركة — مصدر واحد يمنع انحراف التسمية بين الميزات.
 * أسماء التفضيلات مطابقة لما تستعمله نسخة Flutter كي يعمل الترحيل بلا ترجمة.
 */
object LocalStoreKeys {
    // السمة (`theme_provider.dart`)
    const val THEME_MODE = "theme_mode"
    const val IS_DARK_MODE = "is_dark_mode"

    // نمط عرض الأقسام (`sections_layout_provider.dart`)
    const val SECTIONS_LAYOUT = "sections_layout_v1"

    // القارئ (`reader`)
    const val READER_NIGHT_MODE = "reader_night_mode"
    const val READING_MODE = "reading_mode"

    // بادئات المفاتيح الديناميكيّة (يستعملها وكلاء الميزات).
    const val PDF_PAGE_PREFIX = "book_page_"
    const val PDF_TOTAL_PAGES_PREFIX = "book_total_pages_"
    const val AUDIO_POSITION_PREFIX = "audio_position_"
    const val AUDIO_DURATION_PREFIX = "audio_duration_"
    const val VIDEO_POSITION_PREFIX = "video_position_"
    const val DOWNLOAD_PROGRESS_PREFIX = "download_progress_"
    const val DOWNLOAD_TOTAL_PREFIX = "download_total_"

    // صناديق Hive السابقة → مفاتيح JSON هنا.
    const val BOX_CONTENT_METADATA_CACHE = "content_metadata_cache"
    const val BOX_DOWNLOADS = "downloads"
    const val BOX_SAVED_ITEMS = "saved_items"
    const val BOX_NOTIFICATIONS = "notifications"
    const val BOX_HIDDEN_CONTENT = "hidden_content"

    // المجتمع (`ugc_service.dart`)
    const val UGC_BLOCKED_CHANNELS = "ugc_blocked_channels"
    const val UGC_FOLLOWED_CHANNELS = "ugc_followed_channels"
}
