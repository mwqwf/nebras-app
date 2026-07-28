package com.nebras.mobile.feature.player

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.RecommendationEngine
import com.nebras.mobile.core.widget.AutoLinkText
import com.nebras.mobile.core.widget.ContentAttribution
import com.nebras.mobile.core.widget.ContentSectionButton
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.core.widget.contentTypeIcon
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.feature.content.RelatedContentList
import com.nebras.mobile.feature.content.ReportContentButton
import com.nebras.mobile.feature.content.SuggestCorrectionButton
import com.nebras.mobile.feature.download.DownloadButton
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.saved.SaveButton
import com.nebras.mobile.media.PlaybackController

/**
 * شاشة مشغّل الصوت — نقل `features/audio_player/view/audio_player_screen.dart`.
 *
 * المشغّل نفسه في [PlaybackController] (Media3 + خدمة أماميّة)، فالشاشة
 * عرضٌ وتحكّم فقط: تشتغل الخلفيّة وتستمرّ بعد إغلاقها، ويظهر المشغّل
 * المصغّر تلقائياً عند الخروج.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    contentId: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val playback by PlaybackController.state.collectAsStateWithLifecycle()

    // العنصر المطلوب: من الطابور الحاليّ إن كان يُشغَّل، وإلّا من الأقسام.
    val content: Content? = remember(contentId, playback.current, homeState.sections) {
        playback.queue.firstOrNull { it.id == contentId }
            ?: homeState.sections.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.id == contentId }
    }

    // إخفاء المصغّر أثناء فتح الشاشة الكاملة، وإظهاره عند الخروج.
    DisposableEffect(Unit) {
        PlaybackController.setMiniPlayerVisible(false)
        onDispose { PlaybackController.setMiniPlayerVisible(true) }
    }

    // بدء التشغيل إن لم يكن هذا العنصر قيد التشغيل أصلاً.
    LaunchedEffect(contentId, content) {
        val c = content ?: return@LaunchedEffect
        PlaybackController.ensureConnected(context)
        if (playback.current?.id != c.id) {
            // قائمة التشغيل = أصوات القسم نفسه (السياق الذي جاء منه المستخدم).
            val playlist = homeState.sections
                .firstOrNull { s -> s.items.any { it.id == c.id } }
                ?.items
                ?.filter { it.type == ContentType.AUDIO }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(c)
            PlaybackController.play(c, playlist)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الاستماع", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    content?.let { c ->
                        IconButton(onClick = { shareContent(context, c) }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (content == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("لا يوجد مصدر متاح", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            // ── الغلاف ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SmartNetworkImage(
                    imageUrl = content.thumbnailUrl,
                    title = content.title,
                    iconOverride = contentTypeIcon(content.type),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }

            // ── العنوان والمؤلّف ──
            Text(
                text = content.title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.author.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = content.author,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── شريط التقدّم ──
            AudioProgressBar(
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                bufferedMs = playback.bufferedMs,
                onSeek = PlaybackController::seekTo,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(12.dp))

            // ── أزرار التحكّم ──
            AudioControls(
                isPlaying = playback.isPlaying,
                isBuffering = playback.isBuffering,
                hasNext = playback.hasNext,
                hasPrevious = playback.hasPrevious,
                speed = playback.speed,
                onPlayPause = PlaybackController::togglePlayPause,
                onRewind = PlaybackController::rewind,
                onForward = PlaybackController::fastForward,
                onNext = PlaybackController::skipToNext,
                onPrevious = PlaybackController::skipToPrevious,
                onSpeedChange = PlaybackController::setSpeed,
            )

            Spacer(Modifier.height(16.dp))

            // ── إجراءات العنصر ──
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

            // ── زرّ القسم ──
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

            // ── الوصف ──
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

            // ── ذات صلة ──
            RelatedContentList(
                reference = content,
                sections = homeState.sections,
                onItemTap = { ContentRouter.open(navController, it) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** أزرار التحكّم الكاملة: السابق / إرجاع 10 / تشغيل-إيقاف / تقديم 10 / التالي. */
@Composable
private fun AudioControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    speed: Float,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedMenu(speed = speed, onSpeedChange = onSpeedChange)

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "السابق",
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onRewind) {
            Icon(
                Icons.Default.Replay10,
                contentDescription = "إرجاع 10 ثوانٍ",
                modifier = Modifier.size(30.dp),
            )
        }

        // زرّ التشغيل الرئيسيّ — دائرة بلون التطبيق.
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
            } else {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp)) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) "إيقاف مؤقّت" else "تشغيل",
                        modifier = Modifier.size(38.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        IconButton(onClick = onForward) {
            Icon(
                Icons.Default.Forward10,
                contentDescription = "تقديم 10 ثوانٍ",
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "التالي",
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** قائمة سرعة التشغيل — نفس القيم في نسخة Flutter. */
@Composable
private fun SpeedMenu(speed: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Speed, contentDescription = "سرعة التشغيل")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${formatSpeed(value)}×",
                            fontWeight = if (value == speed) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSpeedChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatSpeed(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
