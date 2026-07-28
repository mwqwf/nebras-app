package com.nebras.mobile.feature.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.widget.AutoLinkText
import com.nebras.mobile.core.widget.ContentAttribution
import com.nebras.mobile.core.widget.ContentSectionButton
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.feature.content.RelatedContentList
import com.nebras.mobile.feature.content.ReportContentButton
import com.nebras.mobile.feature.content.SuggestCorrectionButton
import com.nebras.mobile.feature.download.DownloadButton
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.saved.SaveButton
import com.nebras.mobile.media.PlaybackController

/**
 * شاشة مشغّل الفيديو — نقل `features/video_player/view/video_player_screen.dart`.
 *
 * العرض عبر `PlayerView` من media3 بأزرار مُعطَّلة (`useController = false`)،
 * وطبقة التحكّم مرسومة بـ Compose ([VideoControlOverlay]) كما في الأصل.
 * ⛔ لا مسار YouTube — المشغّل للملفّات المباشرة فقط.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    contentId: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val playback by PlaybackController.state.collectAsStateWithLifecycle()

    var isFullscreen by remember { mutableStateOf(false) }

    val content: Content? = remember(contentId, playback.current, homeState.sections) {
        playback.queue.firstOrNull { it.id == contentId }
            ?: homeState.sections.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.id == contentId }
    }

    DisposableEffect(Unit) {
        PlaybackController.setMiniPlayerVisible(false)
        onDispose {
            PlaybackController.setMiniPlayerVisible(true)
            // إعادة الاتّجاه والسطوع وأشرطة النظام إلى وضعها الطبيعيّ.
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                it.resetScreenBrightness()
                WindowInsetsControllerCompat(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // تبديل ملء الشاشة: دوران أفقيّ + إخفاء أشرطة النظام.
    LaunchedEffect(isFullscreen) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
        if (isFullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(contentId, content) {
        val c = content ?: return@LaunchedEffect
        PlaybackController.ensureConnected(context)
        if (playback.current?.id != c.id) {
            val playlist = homeState.sections
                .firstOrNull { s -> s.items.any { it.id == c.id } }
                ?.items
                ?.filter { it.type == ContentType.VIDEO }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(c)
            PlaybackController.play(c, playlist)
        }
    }

    if (content == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا يوجد مصدر متاح", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val enterPip = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── مسرح الفيديو ──
        Box(
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            },
        ) {
            VideoSurface()
            // طبقة الإيماءات تحت طبقة الأزرار كي لا تبتلع نقراتها.
            VideoGestureLayer()
            VideoControlOverlay(
                isPlaying = playback.isPlaying,
                isBuffering = playback.isBuffering,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                bufferedMs = playback.bufferedMs,
                speed = playback.speed,
                hasNext = playback.hasNext,
                hasPrevious = playback.hasPrevious,
                isFullscreen = isFullscreen,
                title = content.title,
                onPlayPause = PlaybackController::togglePlayPause,
                onSeek = PlaybackController::seekTo,
                onRewind = PlaybackController::rewind,
                onForward = PlaybackController::fastForward,
                onNext = PlaybackController::skipToNext,
                onPrevious = PlaybackController::skipToPrevious,
                onSpeedChange = PlaybackController::setSpeed,
                onToggleFullscreen = { isFullscreen = !isFullscreen },
                onEnterPip = enterPip,
                onBack = {
                    if (isFullscreen) isFullscreen = false else navController.popBackStack()
                },
            )
        }

        if (isFullscreen) return@Column

        // ── التفاصيل أسفل المشغّل ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = content.title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.author.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = content.author,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaveButton(content = content)
                DownloadButton(content = content)
                ReportContentButton(content = content)
                SuggestCorrectionButton(content = content)
            }

            Spacer(Modifier.height(12.dp))

            val leaf = ContentRouter.resolveLeafSection(content, homeState.sections)
            if (leaf != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ContentSectionButton(
                        label = leaf.title,
                        onTap = {
                            ContentRouter.openContentTrail(
                                navController,
                                content,
                                homeState.sections,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (content.description.isNotEmpty()) {
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                AutoLinkText(
                    text = content.description,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }

            ContentAttribution(content = content, modifier = Modifier.padding(horizontal = 8.dp))

            Spacer(Modifier.height(16.dp))

            RelatedContentList(
                reference = content,
                sections = homeState.sections,
                onItemTap = { ContentRouter.open(navController, it) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * سطح العرض — `PlayerView` بلا أزرار، مربوط بمشغّل [PlaybackController]
 * (نفس مثيل Media3 الذي يعمل في الخدمة الأماميّة).
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(modifier: Modifier = Modifier) {
    val controller: MediaController? = PlaybackController.mediaController
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view -> view.player = controller },
        onRelease = { view -> view.player = null },
    )
}
