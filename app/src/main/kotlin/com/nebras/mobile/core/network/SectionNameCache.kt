package com.nebras.mobile.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * كاش مفرد لحلّ أسماء الأقسام: يحوّل معرّف القسم الفرعيّ إلى اسم مقروء عبر
 * `/api/public/sections/sub/{id}/` ويحتفظ بالنتيجة في الذاكرة.
 */
object SectionNameCache {

    private const val TAG = "SectionNameCache"

    /** كاش الذاكرة: معرّف القسم الفرعيّ → الاسم المحلول. */
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * يحلّ معرّف قسم فرعيّ إلى اسم العرض. يُعيد القيمة المخزّنة إن وُجدت،
     * وإلا يجلبها من الـ API. عند أيّ خطأ يعود بالمعرّف الخام.
     */
    suspend fun resolve(id: String): String {
        if (id.isEmpty()) return id
        cache[id]?.let { return it }

        return withContext(Dispatchers.IO) {
            val resolved = runCatching {
                val json = NebrasHttpClient.getJsonObject("${ApiConstants.SUB_SECTIONS}$id/")
                json.optString("name").takeIf { it.isNotEmpty() } ?: id
            }.getOrElse {
                Log.d(TAG, "failed to resolve \"$id\": ${it.message}")
                // نُخزّن المعرّف نفسه لتفادي تكرار الطلبات الفاشلة.
                id
            }
            cache[id] = resolved
            resolved
        }
    }

    /** يحلّ عدّة معرّفات بالتوازي — مفيد عند معالجة دفعة أقسام مجمّعة. */
    suspend fun resolveAll(ids: Iterable<String>) {
        val unresolved = ids.filter { it.isNotEmpty() && !cache.containsKey(it) }.toSet()
        if (unresolved.isEmpty()) return
        coroutineScope {
            unresolved.map { async { resolve(it) } }.awaitAll()
        }
    }

    /** يُفرِغ الكاش (عند تسجيل الخروج مثلاً). */
    fun clear() = cache.clear()
}
