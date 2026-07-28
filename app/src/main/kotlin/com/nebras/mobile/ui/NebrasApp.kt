package com.nebras.mobile.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.theme.NebrasTheme
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.community.ChannelScreen
import com.nebras.mobile.feature.community.PublishScreen
import com.nebras.mobile.feature.community.UgcPostScreen
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.home.view.AboutScreen
import com.nebras.mobile.feature.home.view.ContentListScreen
import com.nebras.mobile.feature.home.view.SecondarySectionsScreen
import com.nebras.mobile.feature.home.view.SubSectionsScreen
import com.nebras.mobile.feature.player.AudioPlayerScreen
import com.nebras.mobile.feature.player.VideoPlayerScreen
import com.nebras.mobile.feature.reader.ReaderScreen
import com.nebras.mobile.feature.splash.SplashScreen
import com.nebras.mobile.ui.shell.HomeShell

/**
 * مسارات التطبيق — بديل مزيج Navigator/go_router في نسخة Flutter.
 * كلّ شاشة وجهةٌ باسم ثابت + وسيطات في المسار.
 */
object Routes {
    const val SPLASH = "splash"
    const val SHELL = "shell"
    const val SUB_SECTIONS = "sub_sections/{mainSectionId}"
    const val SECONDARY_SECTIONS = "secondary_sections/{subSectionId}"
    const val CONTENT_LIST = "content_list/{sectionId}"
    const val READER = "reader/{contentId}"
    const val AUDIO_PLAYER = "audio_player/{contentId}"
    const val VIDEO_PLAYER = "video_player/{contentId}"
    const val ABOUT = "about"
    const val CHANNEL = "channel/{channelId}"
    const val UGC_POST = "ugc_post/{postId}"
    const val PUBLISH = "publish"

    fun subSections(mainSectionId: String) = "sub_sections/$mainSectionId"
    fun secondarySections(subSectionId: String) = "secondary_sections/$subSectionId"
    fun contentList(sectionId: String) = "content_list/$sectionId"
    fun reader(contentId: String) = "reader/$contentId"
    fun audioPlayer(contentId: String) = "audio_player/$contentId"
    fun videoPlayer(contentId: String) = "video_player/$contentId"
    fun channel(channelId: String) = "channel/$channelId"
    fun ugcPost(postId: String) = "ugc_post/$postId"
}

/**
 * جذر التطبيق — مقابل `MyApp` في `main.dart`: السمة، الاتجاه RTL،
 * شريط «لا يوجد اتصال» العامّ، وشجرة التنقّل.
 */
@Composable
fun NebrasApp(
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
) {
    val themeMode by ServiceLocator.themeController.themeMode.collectAsStateWithLifecycle()

    NebrasTheme(mode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                // شريط علويّ رفيع يُعلِم بانقطاع الاتصال: ارتفاعه صفر عند
                // الاتصال فلا يغيّر تخطيط أيّ شاشة؛ يظهر فقط عند الانقطاع
                // ويختفي تلقائيّاً عند العودة.
                ConnectivityBanner()
                Box(Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NebrasNavHost(navController, authViewModel, homeViewModel)
                }
            }
        }
    }
}

@Composable
private fun ConnectivityBanner() {
    val isOnline by ServiceLocator.connectivityController.isOnline
        .collectAsStateWithLifecycle()
    Box(Modifier.animateContentSize()) {
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB00020))
                    .statusBarsPadding()
                    .padding(vertical = 6.dp, horizontal = 12.dp),
            ) {
                Text(
                    text = "لا يوجد اتصال بالإنترنت",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NebrasNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.SHELL) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SHELL) {
            HomeShell(
                navController = navController,
                authViewModel = authViewModel,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.SUB_SECTIONS) { entry ->
            SubSectionsScreen(
                mainSectionId = entry.arguments?.getString("mainSectionId").orEmpty(),
                navController = navController,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.SECONDARY_SECTIONS) { entry ->
            SecondarySectionsScreen(
                subSectionId = entry.arguments?.getString("subSectionId").orEmpty(),
                navController = navController,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.CONTENT_LIST) { entry ->
            ContentListScreen(
                sectionId = entry.arguments?.getString("sectionId").orEmpty(),
                navController = navController,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.READER) { entry ->
            ReaderScreen(
                contentId = entry.arguments?.getString("contentId").orEmpty(),
                navController = navController,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.AUDIO_PLAYER) { entry ->
            AudioPlayerScreen(
                contentId = entry.arguments?.getString("contentId").orEmpty(),
                navController = navController,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.VIDEO_PLAYER) { entry ->
            VideoPlayerScreen(
                contentId = entry.arguments?.getString("contentId").orEmpty(),
                navController = navController,
                homeViewModel = homeViewModel,
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(navController = navController)
        }

        composable(Routes.CHANNEL) { entry ->
            ChannelScreen(
                channelId = entry.arguments?.getString("channelId").orEmpty(),
                navController = navController,
                authViewModel = authViewModel,
            )
        }

        composable(Routes.UGC_POST) { entry ->
            UgcPostScreen(
                postId = entry.arguments?.getString("postId").orEmpty(),
                navController = navController,
                authViewModel = authViewModel,
            )
        }

        composable(Routes.PUBLISH) {
            PublishScreen(
                navController = navController,
                authViewModel = authViewModel,
            )
        }
    }
}
