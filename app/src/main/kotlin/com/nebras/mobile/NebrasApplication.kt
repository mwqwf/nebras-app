package com.nebras.mobile

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.Firebase
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.notifications.NotificationChannels

/**
 * نقطة الإقلاع — مقابل الجزء العلويّ من `lib/main.dart`.
 *
 * الترتيب مقصود ومطابق لنسخة Flutter:
 *   1. تهيئة Firebase (google-services.json).
 *   2. تفعيل App Check فور التهيئة (debug provider أثناء التطوير،
 *      Play Integrity في الإصدار) — يمنع استدعاء Firestore/Storage من خارج
 *      التطبيقات الموقَّعة بمفتاحنا.
 *   3. تهيئة التخزين المحلّي (بديل Hive) وقنوات الإشعارات.
 *   4. الخدمات الثقيلة تبقى مؤجَّلة — تُحمَّل كسولاً عبر [ServiceLocator]
 *      حتى لا تؤخّر أوّل إطار (مثل `_warmUpDeferredServices()` في Flutter).
 */
class NebrasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        initializeAppCheck()
        LocalStore.get(this)
        NotificationChannels.ensure(this)
        ServiceLocator.install(this)
    }

    private fun initializeAppCheck() {
        runCatching {
            Firebase.appCheck.installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                },
            )
        }
    }
}
