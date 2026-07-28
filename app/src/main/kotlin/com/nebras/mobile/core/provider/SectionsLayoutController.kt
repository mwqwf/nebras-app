package com.nebras.mobile.core.provider

import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نمطا عرض الأقسام داخل الشاشات الرئيسية/الفرعية/الثانوية.
 *
 * [LIST] : النمط التقليدي — قائمة عمودية، عنصر واحد لكلّ صفّ.
 * [GRID] : شبكة ثلاث أعمدة — لرؤية أكثر من قسم دفعة واحدة.
 */
enum class SectionsLayout { LIST, GRID }

/**
 * يحفظ تفضيل المستخدم لطريقة عرض الأقسام بين الجلسات.
 * تُحمَّل القيمة عند الإنشاء فتظهر الواجهة بالنمط الصحيح منذ أوّل إطار.
 */
class SectionsLayoutController(private val store: LocalStore) {

    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<SectionsLayout> = _layout.asStateFlow()

    val isGrid: Boolean get() = _layout.value == SectionsLayout.GRID
    val isList: Boolean get() = _layout.value == SectionsLayout.LIST

    /** يقلب النمط بين قائمة وشبكة ويحفظ التفضيل. */
    fun toggle() {
        setLayout(if (isGrid) SectionsLayout.LIST else SectionsLayout.GRID)
    }

    fun setLayout(layout: SectionsLayout) {
        if (_layout.value == layout) return
        _layout.value = layout
        persist(layout)
    }

    /**
     * القيمة المحفوظة تُكتب بالاسم الصغير (`grid`/`list`) تماماً كما كانت
     * `SectionsLayout.grid.name` في Dart، فيعمل الترحيل من نسخة Flutter.
     */
    private fun load(): SectionsLayout {
        val raw = store.getString(LocalStoreKeys.SECTIONS_LAYOUT)
        return if (raw == SectionsLayout.GRID.storageName) {
            SectionsLayout.GRID
        } else {
            SectionsLayout.LIST
        }
    }

    private fun persist(layout: SectionsLayout) {
        runCatching { store.putString(LocalStoreKeys.SECTIONS_LAYOUT, layout.storageName) }
    }
}

/** الاسم المخزَّن — يطابق `enum.name` في Dart (حروف صغيرة). */
val SectionsLayout.storageName: String
    get() = name.lowercase()
