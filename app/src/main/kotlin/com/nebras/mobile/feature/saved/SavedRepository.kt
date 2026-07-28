package com.nebras.mobile.feature.saved

import android.util.Log
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import com.nebras.mobile.core.service.HiddenContentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** أنواع المحفوظات المعروضة في تبويبات شاشة المحفوظات. */
enum class SavedContentType { VIDEO, AUDIO, BOOK }

/**
 * عنصر محفوظ — نقل `saved/model/saved_item_model.dart` (كان Hive typeId=0).
 */
data class SavedItem(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    /** 'video' | 'audio' | 'book' — نفس السلسلة المخزَّنة في نسخة Flutter. */
    val contentType: String,
    val savedAtMs: Long,
) {
    val type: SavedContentType
        get() = when (contentType) {
            "video", "youtube" -> SavedContentType.VIDEO
            "audio" -> SavedContentType.AUDIO
            "book", "document" -> SavedContentType.BOOK
            else -> SavedContentType.VIDEO
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        imageUrl?.let { put("imageUrl", it) }
        put("contentType", contentType)
        put("savedAtMs", savedAtMs)
    }

    companion object {
        fun fromJson(json: JSONObject): SavedItem = SavedItem(
            id = json.optString("id"),
            title = json.optString("title"),
            imageUrl = json.optString("imageUrl").takeIf { it.isNotEmpty() },
            contentType = json.optString("contentType"),
            savedAtMs = json.optLong("savedAtMs"),
        )
    }
}

/**
 * مستودع المحفوظات — يدمج `SavedLocalDatasource` (صندوق Hive `saved_items`)
 * وحالات الاستخدام الخمس و`SavedProvider` في طبقة واحدة فوق [LocalStore].
 *
 * كلّ التعديلات **تفاؤليّة**: تُطبَّق على الحالة فوراً ثمّ تُثبَّت؛ وعند فشل
 * التثبيت يُتراجَع عنها — نفس دلالات نسخة Flutter حرفياً.
 */
class SavedRepository(private val store: LocalStore) {

    private companion object {
        const val TAG = "SavedRepository"
    }

    private val items = LinkedHashMap<String, SavedItem>()

    private val _state = MutableStateFlow<List<SavedItem>>(emptyList())

    /** القائمة مرتّبة بالأحدث حفظاً أولاً، **بعد** استبعاد المحتوى المُخفى. */
    val state: StateFlow<List<SavedItem>> = _state.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    val savedIds: Set<String> get() = items.keys.toSet()

    fun isSaved(id: String): Boolean = items.containsKey(id)

    fun getFiltered(type: SavedContentType?): List<SavedItem> =
        _state.value.filter { type == null || it.type == type }

    fun loadSaved() {
        _isLoading.value = true
        runCatching { load() }
            .onFailure { Log.d(TAG, "Failed to load saved items: ${it.message}") }
        _isLoading.value = false
    }

    /** إضافة صامتة من مسار التنزيل (المحتوى المنزَّل يُحفظ تلقائياً). */
    fun addFromDownload(item: SavedItem) {
        if (items.containsKey(item.id)) return
        items[item.id] = item
        publish()
        persist()
    }

    /** استرجاع عنصر (تراجع عن حذف عبر SnackBar). */
    fun restoreSaved(item: SavedItem) {
        if (items.containsKey(item.id)) return
        items[item.id] = item
        publish()
        persist()
    }

    /** تبديل الحفظ — تفاؤليّ مع تراجع عند فشل التثبيت. */
    fun toggle(item: SavedItem) {
        val wasSaved = items.containsKey(item.id)
        if (wasSaved) items.remove(item.id) else items[item.id] = item
        publish()
        runCatching { persist() }.onFailure {
            // تراجع: أعد الحالة السابقة.
            if (wasSaved) items[item.id] = item else items.remove(item.id)
            publish()
        }
    }

    fun remove(id: String) {
        val removed = items.remove(id) ?: return
        publish()
        runCatching { persist() }.onFailure {
            items[id] = removed
            publish()
        }
    }

    fun clearAll() {
        items.clear()
        publish()
        persist()
    }

    /** يضمن حفظ العنصر (يُستعمل عند التنزيل) — لا يبدّل إن كان محفوظاً. */
    fun ensureSaved(item: SavedItem) {
        if (items.containsKey(item.id)) return
        items[item.id] = item
        publish()
        persist()
    }

    // ── داخليّات ────────────────────────────────────────────────────

    private fun load() {
        items.clear()
        val root = store.getJsonObject(LocalStoreKeys.BOX_SAVED_ITEMS) ?: run {
            publish()
            return
        }
        for (key in root.keys()) {
            val obj = root.optJSONObject(key) ?: continue
            runCatching { SavedItem.fromJson(obj) }
                .onSuccess { items[key] = it }
        }
        publish()
    }

    private fun publish() {
        _state.value = items.values
            .sortedByDescending { it.savedAtMs }
            .filterNot { HiddenContentService.isHidden(it.id) }
    }

    private fun persist() {
        val root = JSONObject()
        items.forEach { (key, item) -> root.put(key, item.toJson()) }
        store.putJsonObject(LocalStoreKeys.BOX_SAVED_ITEMS, root)
    }
}
