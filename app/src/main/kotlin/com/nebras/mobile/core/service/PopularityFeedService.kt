package com.nebras.mobile.core.service

import android.util.Log
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

/**
 * يقرأ `aggregates_popular/top_weekly` — مستند واحد لتجنّب استعلامات باهظة.
 * كاش محلّي 6 ساعات.
 */
object PopularityFeedService {

    private const val TAG = "PopularityFeed"
    private const val DOC_PATH = "aggregates_popular/top_weekly"
    private const val CACHE_KEY = "popular_weekly_ids_v1"
    private const val CACHE_AT_KEY = "popular_weekly_cached_at_v1"
    private const val CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000

    private lateinit var store: LocalStore

    @Volatile
    private var ids: List<String> = emptyList()

    @Volatile
    private var loadedAt: Long? = null

    fun init(localStore: LocalStore) {
        store = localStore
    }

    val popularIds: List<String>
        get() = ids

    suspend fun loadPopularIds(forceRefresh: Boolean = false): List<String> {
        val now = System.currentTimeMillis()
        val cachedAtMemory = loadedAt
        if (!forceRefresh &&
            cachedAtMemory != null &&
            now - cachedAtMemory < CACHE_TTL_MILLIS &&
            ids.isNotEmpty()
        ) {
            return ids
        }

        if (!forceRefresh) {
            val cachedAt = store.getLong(CACHE_AT_KEY, 0L)
            val cachedRaw = store.getJsonArray(CACHE_KEY)
            if (cachedAt > 0 && cachedRaw != null && now - cachedAt < CACHE_TTL_MILLIS) {
                val list = cachedRaw.toStringList()
                ids = list
                loadedAt = now
                return ids
            }
        }

        runCatching {
            val snapshot = FirestoreSyncConfig.instance.document(DOC_PATH).get().await()
            if (!snapshot.exists()) {
                ids = emptyList()
                loadedAt = now
                return ids
            }
            val rawIds = snapshot.get("ids")
            ids = if (rawIds is List<*>) {
                rawIds.mapNotNull { it?.toString()?.takeIf(String::isNotEmpty) }
            } else {
                emptyList()
            }
            loadedAt = now
            val array = JSONArray()
            ids.forEach { array.put(it) }
            store.putJsonArray(CACHE_KEY, array)
            store.putLong(CACHE_AT_KEY, now)
        }.onFailure {
            Log.d(TAG, "load failed: ${it.message}")
        }

        return ids
    }

    fun clearMemoryCache() {
        ids = emptyList()
        loadedAt = null
    }

    fun clearDiskCache() {
        store.remove(CACHE_KEY)
        store.remove(CACHE_AT_KEY)
        clearMemoryCache()
    }
}

internal fun JSONArray.toStringList(): List<String> {
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        opt(i)?.toString()?.takeIf(String::isNotEmpty)?.let(out::add)
    }
    return out
}
