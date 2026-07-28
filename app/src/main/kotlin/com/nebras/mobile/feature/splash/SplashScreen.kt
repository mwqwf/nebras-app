package com.nebras.mobile.feature.splash

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.feature.onboarding.ONBOARDING_SEEN_KEY
import com.nebras.mobile.feature.onboarding.OnboardingScreen
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SplashScreen"

/** أصل مقدّمة الإقلاع — نفس مسار `assets/videos/splash_intro.mp4` في Flutter. */
private const val SPLASH_VIDEO_URI = "asset:///videos/splash_intro.mp4"

/** منحنى `Curves.easeOutCubic` في Flutter بنفس معاملاته. */
private val EaseOutCubicFlutter = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * شاشة الإقلاع — نقل `features/splash/splash_screen.dart`.
 *
 * السلوك حرفيّ: مقدّمة فيديو تُشغَّل مرّة واحدة، واسم «نبراس» يظهر بعد 1.5s
 * بتلاشٍ + انزلاق طفيف + تكبير دقيق. الانتقال يحدث فور انتهاء الفيديو، مع
 * شبكة أمان عند 3500ms حتى لا يعلق المستخدم إن تعثّر التشغيل، وانتقال سريع
 * (400ms) عند فشل التهيئة.
 *
 * قرار التوجيه ليس من شأن السبلاش: أوّل تشغيل فقط يعرض شاشة التعريف، وبعدها
 * [onFinished] الذي يقود إلى بوّابة الجلسة (`HomeShell`).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var showOnboarding by remember { mutableStateOf(false) }

    // تسخين الخدمات المؤجَّلة أثناء عرض المقدّمة — نظير
    // `_warmUpDeferredServices()` في `main.dart`: كلّها كسولة في
    // `ServiceLocator` فيكفي لمسها هنا لتُبنى بالتوازي مع أوّل إطار.
    LaunchedEffect(Unit) {
        runCatching {
            ServiceLocator.contentMetadataCache
            ServiceLocator.notificationsRepository
            ServiceLocator.savedRepository
            ServiceLocator.homeDatasource
        }.onFailure { Log.d(TAG, "warm-up skipped: ${it.message}") }
    }

    if (showOnboarding) {
        OnboardingScreen(onFinished = onFinished)
        return
    }

    SplashBody(
        onDone = {
            // أوّل تشغيل فقط: نعرض شاشة التعريف ثمّ نُكمل، وإلّا نُكمل مباشرة.
            val seen = runCatching {
                ServiceLocator.localStore.getBool(ONBOARDING_SEEN_KEY, false)
            }.getOrDefault(false)
            if (seen) onFinished() else showOnboarding = true
        },
    )
}

@Composable
private fun SplashBody(onDone: () -> Unit) {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)

    var isVideoInitialized by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var videoFailed by remember { mutableStateOf(false) }
    var videoEnded by remember { mutableStateOf(false) }

    // بديل `_hasNavigated`: يمنع تكرار الانتقال من أكثر من مصدر (نهاية
    // الفيديو / الفشل / شبكة الأمان).
    val hasNavigated = remember { AtomicBoolean(false) }
    val navigateOnce = remember {
        {
            if (hasNavigated.compareAndSet(false, true)) currentOnDone()
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(SPLASH_VIDEO_URI))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> isVideoInitialized = true
                    // الانتقال بمجرّد انتهاء الفيديو بدلاً من تأخير ثابت.
                    Player.STATE_ENDED -> videoEnded = true
                    else -> Unit
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    videoAspectRatio =
                        (width * videoSize.pixelWidthHeightRatio) / height.toFloat()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.d(TAG, "Video initialization error: ${error.message}")
                isVideoInitialized = false
                // إن فشل الفيديو لا نعلّق المستخدم: ننتقل سريعاً.
                videoFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(videoEnded) {
        if (videoEnded) navigateOnce()
    }

    LaunchedEffect(videoFailed) {
        if (!videoFailed) return@LaunchedEffect
        delay(400)
        navigateOnce()
    }

    // جدولة انتقال «حدّ أعلى» (safety net): لو تأخّر حدث النهاية لأيّ سبب
    // لا نترك المستخدم محبوساً على السبلاش إلى الأبد.
    LaunchedEffect(Unit) {
        delay(3500)
        navigateOnce()
    }

    // حركة الاسم تبدأ عند 1.5s وتستغرق 600ms.
    var animateBrand by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        animateBrand = true
    }
    val brandProgress by animateFloatAsState(
        targetValue = if (animateBrand) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubicFlutter),
        label = "splashBrand",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NebrasColors.whiteColor)
            .safeDrawingPadding(),
    ) {
        // طبقة الفيديو (في الوسط، بعرض الشاشة).
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isVideoInitialized) {
                val ratio = if (videoAspectRatio.isFinite() && videoAspectRatio > 0f) {
                    videoAspectRatio
                } else {
                    16f / 9f
                }
                SplashVideoSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = NebrasColors.primary,
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }

        // طبقة اسم العلامة (أسفل الوسط، متحرّكة).
        Text(
            text = "نبراس",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = brandProgress
                    scaleX = 0.96f + (0.04f * brandProgress)
                    scaleY = 0.96f + (0.04f * brandProgress)
                    // انزلاق طفيف للأعلى: 3% من ارتفاع النصّ كما في Flutter.
                    translationY = (1f - brandProgress) * 0.03f * size.height
                },
            textAlign = TextAlign.Center,
            style = NebrasTextStyles.heading2,
        )
    }
}

/**
 * سطح عرض الفيديو — `PlayerView` بلا أزرار تحكّم (بديل `VideoPlayer`).
 * نسبة العرض مضبوطة من الخارج، لذلك نملأ الإطار كاملاً هنا.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SplashVideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        modifier = modifier,
    )
}
