package com.nebras.mobile.feature.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * طبقة تحكّم مشغّل الفيديو — نقل
 * `features/video_player/view/widgets/control_overlay_widget.dart`.
 *
 * تظهر بالنقر وتختفي تلقائياً بعد 3 ثوانٍ أثناء التشغيل (نفس مدّة الأصل).
 */
@Composable
fun VideoControlOverlay(
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    speed: Float,
    hasNext: Boolean,
    hasPrevious: Boolean,
    isFullscreen: Boolean,
    title: String,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }

    // إخفاء تلقائيّ بعد 3 ثوانٍ من آخر تفاعل — وأثناء التشغيل فقط.
    LaunchedEffect(visible, isPlaying, lastInteraction) {
        if (!visible || !isPlaying) return@LaunchedEffect
        delay(3_000)
        if (System.currentTimeMillis() - lastInteraction >= 3_000) visible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        visible = !visible
                        lastInteraction = System.currentTimeMillis()
                    },
                    // نقرة مزدوجة: يمين = تقديم، يسار = إرجاع (سلوك الأصل).
                    onDoubleTap = { offset ->
                        if (offset.x > size.width / 2f) onForward() else onRewind()
                        lastInteraction = System.currentTimeMillis()
                    },
                )
            },
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                color = Color.White,
            )
        }

        if (!visible) return@Box

        // تعتيم خفيف يُظهر الأزرار فوق أيّ لقطة.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        // ── الشريط العلويّ ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color.White,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            SpeedMenuDark(speed = speed, onSpeedChange = {
                onSpeedChange(it)
                lastInteraction = System.currentTimeMillis()
            })
            IconButton(onClick = onEnterPip) {
                Icon(
                    Icons.Default.PictureInPictureAlt,
                    contentDescription = "صورة داخل صورة",
                    tint = Color.White,
                )
            }
        }

        // ── أزرار الوسط ──
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "السابق",
                    modifier = Modifier.size(34.dp),
                    tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            IconButton(onClick = onRewind) {
                Icon(
                    Icons.Default.Replay10,
                    contentDescription = "إرجاع 10 ثوانٍ",
                    modifier = Modifier.size(38.dp),
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = {
                    onPlayPause()
                    lastInteraction = System.currentTimeMillis()
                },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف مؤقّت" else "تشغيل",
                    modifier = Modifier.size(52.dp),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onForward) {
                Icon(
                    Icons.Default.Forward10,
                    contentDescription = "تقديم 10 ثوانٍ",
                    modifier = Modifier.size(38.dp),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "التالي",
                    modifier = Modifier.size(34.dp),
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }

        // ── الشريط السفليّ ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            VideoProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onSeek = {
                    onSeek(it)
                    lastInteraction = System.currentTimeMillis()
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                VideoTimeIndicator(positionMs = positionMs, durationMs = durationMs)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) {
                            Icons.Default.FullscreenExit
                        } else {
                            Icons.Default.Fullscreen
                        },
                        contentDescription = if (isFullscreen) "إنهاء ملء الشاشة" else "ملء الشاشة",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** شريط تقدّم الفيديو — نقل `video_progress_baar_widget.dart`. */
@Composable
fun VideoProgressBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val duration = durationMs.coerceAtLeast(0L)
    val liveRatio = if (duration > 0) {
        (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedRatio = if (duration > 0) {
        (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(modifier.fillMaxWidth()) {
        // مؤشّر التخزين المؤقّت خلف شريط التقدّم.
        LinearProgressIndicator(
            progress = { bufferedRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center)
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color.White.copy(alpha = 0.45f),
            trackColor = Color.White.copy(alpha = 0.18f),
        )
        Slider(
            value = if (isDragging) dragValue else liveRatio,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * duration).toLong())
                isDragging = false
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                activeTrackColor = Color.White,
                thumbColor = Color.White,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

/** مؤشّر الزمن `الحالي / الكلّي` — نقل `video_time_indicator.dart`. */
@Composable
fun VideoTimeIndicator(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    Text(
        text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
        modifier = modifier.padding(start = 10.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
    )
}

/**
 * طبقة إيماءات السطوع والصوت — بديل `screen_brightness` و
 * `flutter_volume_controller`.
 *
 * السحب العموديّ على النصف **الأيسر** يضبط سطوع الشاشة، وعلى النصف
 * **الأيمن** يضبط صوت الوسائط. يُعرض مؤشّر مؤقّت أثناء السحب.
 */
@Composable
fun VideoGestureLayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var indicator by remember { mutableStateOf<GestureIndicator?>(null) }

    // إخفاء المؤشّر بعد لحظة من انتهاء السحب.
    LaunchedEffect(indicator) {
        if (indicator == null) return@LaunchedEffect
        delay(900)
        indicator = null
    }

    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // النصف الأيسر: السطوع.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(activity) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val window = activity?.window ?: return@detectVerticalDragGestures
                            val attrs = window.attributes
                            val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
                            // السحب لأعلى يزيد السطوع (dragAmount سالب لأعلى).
                            val next = (current - dragAmount / size.height).coerceIn(0.01f, 1f)
                            attrs.screenBrightness = next
                            window.attributes = attrs
                            indicator = GestureIndicator(isBrightness = true, value = next)
                        }
                    },
            )
            // النصف الأيمن: الصوت.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(maxVolume) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (maxVolume <= 0) return@detectVerticalDragGestures
                            val current =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val ratio = current.toFloat() / maxVolume
                            val next = (ratio - dragAmount / size.height).coerceIn(0f, 1f)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (next * maxVolume).toInt(),
                                0,
                            )
                            indicator = GestureIndicator(isBrightness = false, value = next)
                        }
                    },
            )
        }

        indicator?.let { ind ->
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (ind.isBrightness) {
                        Icons.Default.BrightnessHigh
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(90.dp)) {
                    LinearProgressIndicator(
                        progress = { ind.value },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(ind.value * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private data class GestureIndicator(val isBrightness: Boolean, val value: Float)

@Composable
private fun SpeedMenuDark(speed: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Speed, contentDescription = "سرعة التشغيل", tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${if (value == value.toInt().toFloat()) {
                                value.toInt().toString()
                            } else {
                                value.toString()
                            }}×",
                            fontWeight = if (abs(value - speed) < 0.001f) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
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

/** يستخرج الـ Activity من أيّ Context مُغلَّف — لازم للنافذة والدوران وPiP. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** يُعيد سطوع النافذة إلى الافتراضيّ عند مغادرة شاشة الفيديو. */
fun Activity.resetScreenBrightness() {
    val attrs = window.attributes
    attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = attrs
}
