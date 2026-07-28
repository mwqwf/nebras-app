package com.nebras.mobile.core.service

import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * يحتفظ محليّاً (على الجهاز فقط) بمعرّفات المحتوى الذي أبلغ عنه المستخدم
 * الحاليّ. احترامًا لرأي المُبلِّغ لا يظهر له ذلك المحتوى بعد الآن — سواءٌ
 * ثبتت مخالفته (وحُذف للجميع) أو لم تثبت (يبقى للآخرين ويختفي عنه هو).
 *
 * التخزين محلّيّ بالكامل فيعمل للضيوف أيضاً دون حساب. [isHidden] متزامنة
 * (مجموعة في الذاكرة) كي تُستعمل داخل حُرّاس القوائم بلا تكلفة.
 */
object HiddenContentService {

    private lateinit var store: LocalStore

    @Volatile
    private var ids: Set<String> = emptySet()

    private val _state = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _state.asStateFlow()

    /** يُستدعى في الإقلاع. آمن للاستدعاء أكثر من مرّة. */
    fun init(localStore: LocalStore) {
        store = localStore
        val loaded = localStore.getStringList(LocalStoreKeys.BOX_HIDDEN_CONTENT).toSet()
        ids = loaded
        _state.value = loaded
    }

    /** متزامنة — تُستعمل داخل حُرّاس القوائم لكلّ عنصر. */
    fun isHidden(id: String): Boolean = id.isNotEmpty() && ids.contains(id)

    /** يُخفي محتوى عن المستخدم الحاليّ ويُحدّث الواجهة فوراً ثمّ يُثبّته. */
    fun hide(id: String) {
        val key = id.trim()
        if (key.isEmpty() || ids.contains(key)) return
        val next = ids + key
        ids = next
        _state.value = next
        persist(next)
    }

    /** يمسح القائمة بالكامل — عند حذف الحساب أو «نسيان اهتماماتي». */
    fun clearAll() {
        if (ids.isEmpty()) return
        ids = emptySet()
        _state.value = emptySet()
        persist(emptySet())
    }

    private fun persist(values: Set<String>) {
        runCatching {
            val array = JSONArray()
            values.forEach { array.put(it) }
            store.putJsonArray(LocalStoreKeys.BOX_HIDDEN_CONTENT, array)
        }
    }
}
