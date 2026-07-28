package com.nebras.mobile.core.service

import com.nebras.mobile.core.data.LocalStore
import org.json.JSONArray
import java.util.Calendar

/**
 * يقنّن إظهار رسالة دعوة الضيف لإنشاء حساب: ثلاث مرّات يوميّاً كحدّ أقصى
 * بفاصل زمنيّ أدنى بين الرسائل، حتى لا تتحوّل إلى إزعاج.
 *
 * **محليّ فقط** — لا علاقة له بـ Firebase. يخصّ الضيف (بلا حساب) وحده.
 */
object GuestAccountPromptService {

    private const val KEY_SHOWN_TIMES = "guest_prompt_shown_ms_v1"
    private const val MAX_PER_DAY = 3
    private const val MIN_GAP_MILLIS = 3L * 60 * 60 * 1000

    private lateinit var store: LocalStore

    fun init(localStore: LocalStore) {
        store = localStore
    }

    private fun todayTimes(): List<Long> {
        val array = store.getJsonArray(KEY_SHOWN_TIMES) ?: return emptyList()
        val now = Calendar.getInstance()
        val out = mutableListOf<Long>()
        for (i in 0 until array.length()) {
            val ms = array.optLong(i, 0L)
            if (ms <= 0L) continue
            val dt = Calendar.getInstance().apply { timeInMillis = ms }
            if (dt.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                dt.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                dt.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
            ) {
                out.add(ms)
            }
        }
        return out
    }

    /** هل نعرض الرسالة الآن؟ يُحترم الحدّ اليوميّ والفاصل الأدنى. */
    fun shouldShow(): Boolean {
        val times = todayTimes()
        if (times.size >= MAX_PER_DAY) return false
        val last = times.maxOrNull()
        if (last != null && System.currentTimeMillis() - last < MIN_GAP_MILLIS) return false
        return true
    }

    /** يسجّل أنّ الرسالة عُرضت الآن (بعد العرض الفعليّ فقط). */
    fun recordShown() {
        val array = JSONArray()
        todayTimes().forEach { array.put(it) }
        array.put(System.currentTimeMillis())
        store.putJsonArray(KEY_SHOWN_TIMES, array)
    }
}
