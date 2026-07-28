package com.nebras.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.service.DeepLinkService
import com.nebras.mobile.core.service.ShareIntentService
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.media.PlaybackController
import com.nebras.mobile.ui.NebrasApp

/**
 * المضيف الوحيد لواجهة Compose — مقابل `MyApp` في `main.dart`.
 *
 * شاشة الإقلاع الأصليّة تُزال فور جاهزيّة أوّل إطار (بديل
 * `FlutterNativeSplash.preserve/remove`)، ثمّ تتولّى `SplashScreen` في Compose
 * التحكّم في مدّة العرض.
 */
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels {
        viewModelFactory {
            AuthViewModel(ServiceLocator.authService, ServiceLocator.localStore)
        }
    }

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory { HomeViewModel(ServiceLocator.getHomeDataUseCase) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // يجب أن يسبق `super.onCreate` — نظير `preserve(widgetsBinding)`.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // الروابط العميقة والملفّات المشتركة قد تصل مع Intent الإقلاع.
        DeepLinkService.handleIntent(intent)
        ShareIntentService.handleIntent(this, intent)

        // ربط المشغّل بخدمة التشغيل الخلفيّة مبكّراً كي يكون المشغّل المصغّر
        // جاهزاً فور فتح أوّل عنصر (نظير `initNebrasAudio()` قبل `runApp`).
        PlaybackController.ensureConnected(this)

        authViewModel.bootstrap()

        setContent {
            NebrasApp(
                authViewModel = authViewModel,
                homeViewModel = homeViewModel,
            )
        }
    }

    /** التطبيق `singleTop`: الروابط والمشاركات تصل هنا وهو مفتوح. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkService.handleIntent(intent)
        ShareIntentService.handleIntent(this, intent)
    }

    /**
     * وضع «صورة داخل صورة» عند مغادرة التطبيق أثناء تشغيل فيديو — بديل
     * حزمة `floating`. شاشة الفيديو هي من تطلبه صراحةً؛ هنا نُبقي السلوك
     * الافتراضيّ للنظام دون فرضه على كلّ الشاشات.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // لا نُفعّله تلقائياً: شاشة الفيديو تستدعي enterPictureInPictureMode
        // بنفسها عند الحاجة، فلا نُقحم PiP على شاشات القراءة والتصفّح.
    }
}

/** مصنع ViewModel خفيف — بديل حقن التبعيّات بلا مكتبة DI. */
private inline fun <VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
