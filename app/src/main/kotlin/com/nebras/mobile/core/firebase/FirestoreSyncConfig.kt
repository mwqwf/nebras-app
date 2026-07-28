package com.nebras.mobile.core.firebase

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * تفعيل كاش Firestore على القرص.
 *
 * ⚠️ **قاعدة Firestore المسمّاة**: مشروع `nebras-9118c` يستعمل قاعدة اسمها
 * `default` (بدون أقواس). اللوحة والـ Admin SDK يستخدمان
 * `getFirestore(app, 'default')`. النسخة الافتراضية على الجوّال تتّصل بـ
 * `(default)` (مع أقواس) — قاعدة مختلفة غير موجودة، فيرجع NOT_FOUND وتبقى
 * الشاشة فارغة بينما يعمل FCM لأنّه لا يمرّ عبر Firestore.
 */
object FirestoreSyncConfig {

    private const val TAG = "FirestoreSyncConfig"

    /** قاعدة Firestore المسمّاة في مشروع nebras-9118c (بدون أقواس). */
    const val NEBRAS_DATABASE_ID = "default"

    private const val CACHE_SIZE_BYTES = 100L * 1024 * 1024

    val instance: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), NEBRAS_DATABASE_ID)
            .also(::configure)
    }

    /** يجب استدعاؤه قبل أوّل قراءة — الإعدادات تُقفل بعد أول استعمال. */
    fun configure(firestore: FirebaseFirestore) {
        runCatching {
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(CACHE_SIZE_BYTES)
                        .build(),
                )
                .build()
        }.onFailure {
            Log.d(TAG, "persistence already locked: ${it.message}")
        }
    }

    /**
     * قراءة تستفيد من الكاش ثم تتزامن مع الخادم حتى لا تبقى وثائق محذوفة
     * في الواجهة عند عودة الشبكة (مقابل `Source.serverAndCache` في Dart).
     */
    suspend fun cacheFirstFresh(query: Query): QuerySnapshot =
        query.get(Source.DEFAULT).await()
}
