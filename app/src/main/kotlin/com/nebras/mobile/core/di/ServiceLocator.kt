package com.nebras.mobile.core.di

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import com.nebras.mobile.core.provider.ConnectivityController
import com.nebras.mobile.core.provider.SectionsLayoutController
import com.nebras.mobile.core.model.ContentMetadataCache
import com.nebras.mobile.core.service.ContinueWatchingService
import com.nebras.mobile.core.service.GuestAccountPromptService
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.core.service.InterestProfileService
import com.nebras.mobile.core.service.PendingTakedownService
import com.nebras.mobile.core.service.PopularityFeedService
import com.nebras.mobile.core.service.SectionFollowService
import com.nebras.mobile.core.service.UserBehaviorTracker
import com.nebras.mobile.core.theme.ThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * مُحدِّد الخدمات — بديل `get_it` في `core/di/setup_locator.dart`.
 *
 * كلّ شيء `by lazy` فلا يُنشَأ إلّا عند أوّل استعمال: هذا يحقّق نفس أثر
 * `_warmUpDeferredServices()` في نسخة Flutter (لا تُحمَّل الخدمات الثقيلة
 * قبل أوّل إطار). [install] يُستدعى مرّة واحدة من `NebrasApplication`.
 */
object ServiceLocator {

    lateinit var appContext: Context
        private set

    /** نطاق للمهام الخلفيّة القصيرة التي لا تتبع دورة حياة شاشة. */
    val appScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val localStore: LocalStore by lazy { LocalStore.get(appContext) }

    val firestore: FirebaseFirestore by lazy { FirestoreSyncConfig.instance }

    val themeController: ThemeController by lazy { ThemeController(localStore) }

    val sectionsLayoutController: SectionsLayoutController by lazy {
        SectionsLayoutController(localStore)
    }

    val connectivityController: ConnectivityController by lazy {
        ConnectivityController(appContext)
    }

    /** كاش ميتادات المحتوى (بديل صندوق Hive) — يخدم العرض أوفلاين. */
    val contentMetadataCache: ContentMetadataCache by lazy {
        ContentMetadataCache(localStore)
    }

    val savedRepository: com.nebras.mobile.feature.saved.SavedRepository by lazy {
        com.nebras.mobile.feature.saved.SavedRepository(localStore)
    }

    val notificationsRepository:
        com.nebras.mobile.feature.notifications.NotificationsRepository by lazy {
            com.nebras.mobile.feature.notifications.NotificationsRepository(localStore)
        }

    val homeDatasource: com.nebras.mobile.feature.home.data.HomeDatasource by lazy {
        com.nebras.mobile.feature.home.data.HomeDatasource(firestore, contentMetadataCache)
    }

    val homeRepository: com.nebras.mobile.feature.home.data.HomeRepository by lazy {
        com.nebras.mobile.feature.home.data.HomeRepositoryImpl(homeDatasource)
    }

    val getHomeDataUseCase: com.nebras.mobile.feature.home.data.GetHomeDataUseCase by lazy {
        com.nebras.mobile.feature.home.data.GetHomeDataUseCase(homeRepository)
    }

    val searchDatasource: com.nebras.mobile.feature.search.SearchDatasource by lazy {
        com.nebras.mobile.feature.search.SearchDatasource(firestore, contentMetadataCache)
    }

    val downloadRepository: com.nebras.mobile.feature.download.DownloadRepository by lazy {
        com.nebras.mobile.feature.download.DownloadRepository(appContext, localStore)
    }

    val authService: com.nebras.mobile.feature.auth.AuthService by lazy {
        com.nebras.mobile.feature.auth.AuthService(appContext)
    }

    fun install(context: Context) {
        appContext = context.applicationContext

        // الخدمات المفردة التي تحتاج تخزيناً محلّياً تُهيَّأ فوراً — كلفتها
        // قراءة SharedPreferences واحدة، وتُستعمل داخل حُرّاس القوائم بشكل
        // متزامن فلا يمكن تأجيلها إلى أوّل استدعاء غير متزامن.
        HiddenContentService.init(localStore)
        ContinueWatchingService.init(localStore)
        PopularityFeedService.init(localStore)
        UserBehaviorTracker.init(localStore)
        InterestProfileService.init(localStore)
        SectionFollowService.init(localStore)
        GuestAccountPromptService.init(localStore)
        com.nebras.mobile.feature.notifications.NotificationsPreferences.init(localStore)
        com.nebras.mobile.feature.community.UgcService.init(localStore)
        com.nebras.mobile.feature.download.MediaDownloadController.init(
            downloadRepository,
            savedRepository,
        )

        // مؤجَّلة: اشتراك Firestore الحيّ + تسخين معرّفات الشعبيّة. تعمل
        // بالتوازي مع بناء الواجهة ولا تحجب أوّل إطار.
        appScope.launch {
            runCatching { PendingTakedownService.init() }
            runCatching { PopularityFeedService.loadPopularIds() }
        }
    }
}
