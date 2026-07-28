package com.nebras.mobile.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.ContentEngagementService
import com.nebras.mobile.core.service.ContinueWatchingService
import com.nebras.mobile.core.service.InterestProfileService
import com.nebras.mobile.core.service.UserBehaviorTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * حالة التشغيل المعروضة للواجهة — مقابل ما كان يبثّه `AudioPlayerProvider`.
 */
data class PlaybackUiState(
    val current: Content? = null,
    val queue: List<Content> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** المشغّل المصغّر يظهر متى وُجد عنصر حاليّ ولم تكن شاشة المشغّل مفتوحة. */
    val miniPlayerVisible: Boolean = false,
) {
    val progressRatio: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * جسر الواجهة إلى [NebrasPlaybackService] — بديل `AudioPlayerProvider`
 * و`nebrasAudioPlayer` في نسخة Flutter.
 *
 * يحتفظ بـ [MediaController] واحد للتطبيق كلّه، ويبثّ الحالة عبر
 * [state] لتستهلكها شاشة المشغّل والمشغّل المصغّر معاً.
 *
 * يتولّى أيضاً التسجيل الصامت للإشارات:
 *   • `view_count` عند بدء التشغيل، و`play_count`/`complete_count` عند
 *     تجاوز 25% و90% (عبر [ContentEngagementService]).
 *   • تقدّم «تابع التصفّح» محليّاً (عبر [ContinueWatchingService]).
 *   • أوزان الاهتمام والسلوك.
 */
object PlaybackController {

    private const val POSITION_POLL_MILLIS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controller: MediaController? = null

    /**
     * المشغّل الحيّ — تحتاجه شاشة الفيديو لربط `PlayerView` بسطح العرض.
     * لا تستعمله للتحكّم؛ استعمل دوالّ هذا الصفّ كي تبقى الحالة متّسقة.
     */
    val mediaController: MediaController? get() = controller

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /** قائمة التشغيل الحاليّة بالمحتوى الأصليّ (Media3 يحمل المعرّفات فقط). */
    private var queue: List<Content> = emptyList()

    private var initialized = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState()
    }

    /** يُستدعى من أوّل شاشة تحتاج التشغيل. آمن للاستدعاء المتكرّر. */
    fun ensureConnected(context: Context) {
        if (initialized) return
        initialized = true

        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, NebrasPlaybackService::class.java),
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    controller = future.get().also { it.addListener(listener) }
                    syncState()
                    startPositionPolling()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * يبدأ تشغيل [content] ضمن [playlist]. القائمة تُبنى من نفس السياق الذي
     * جاء منه المستخدم (قسم/بحث/محفوظات) كما في نسخة Flutter.
     */
    fun play(content: Content, playlist: List<Content> = listOf(content)) {
        val c = controller ?: return
        // نُبقي الوسائط القابلة للتشغيل فقط (صوت/فيديو) في الطابور.
        val playable = playlist.filter { it.sourceUrl?.isNotEmpty() == true }
            .ifEmpty { listOf(content) }
        queue = playable

        val startIndex = playable.indexOfFirst { it.id == content.id }.coerceAtLeast(0)
        c.setMediaItems(playable.map(::toMediaItem), startIndex, 0L)
        c.prepare()
        c.play()

        recordOpenSignals(content)
        syncState()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() = controller?.pause() ?: Unit

    fun resume() = controller?.play() ?: Unit

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        syncState()
    }

    /** إرجاع 10 ثوانٍ — نفس فاصل نسخة Flutter. */
    fun rewind() = controller?.seekBack() ?: Unit

    /** تقديم 10 ثوانٍ. */
    fun fastForward() = controller?.seekForward() ?: Unit

    fun skipToNext() = controller?.seekToNextMediaItem() ?: Unit

    fun skipToPrevious() = controller?.seekToPreviousMediaItem() ?: Unit

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        syncState()
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        queue = emptyList()
        _state.value = PlaybackUiState()
    }

    /** تُستدعى من شاشة المشغّل لإخفاء المصغّر أثناء فتحها. */
    fun setMiniPlayerVisible(visible: Boolean) {
        _state.value = _state.value.copy(miniPlayerVisible = visible)
    }

    // ── داخليّات ────────────────────────────────────────────────────

    private fun toMediaItem(content: Content): MediaItem = MediaItem.Builder()
        .setMediaId(content.id)
        .setUri(content.sourceUrl.orEmpty())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(content.title)
                .setArtist(content.author.ifEmpty { content.sectionName.orEmpty() })
                .setArtworkUri(
                    content.thumbnailUrl.takeIf { it.isNotEmpty() }?.let(Uri::parse),
                )
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun startPositionPolling() {
        scope.launch {
            while (true) {
                delay(POSITION_POLL_MILLIS)
                val c = controller ?: continue
                if (!c.isPlaying) continue
                syncState()
                reportProgress()
            }
        }
    }

    private fun syncState() {
        val c = controller
        if (c == null) {
            _state.value = PlaybackUiState()
            return
        }
        val currentId = c.currentMediaItem?.mediaId
        val current = queue.firstOrNull { it.id == currentId }
        _state.value = _state.value.copy(
            current = current,
            queue = queue,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            bufferedMs = c.bufferedPosition.coerceAtLeast(0),
            speed = c.playbackParameters.speed,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
        )
    }

    /** يسجّل الإشارات الصامتة عند فتح المحتوى (لا PII، لا UID). */
    private fun recordOpenSignals(content: Content) {
        ContentEngagementService.recordContentOpened(content)
        UserBehaviorTracker.recordContentOpen(content)
        InterestProfileService.onContentConsumed(content)
    }

    /**
     * يبلّغ التقدّم دوريّاً: معالم التشغيل (25%/90%) + حفظ «تابع التصفّح»
     * محليّاً. كلاهما محميّ داخليّاً من التكرار.
     */
    private fun reportProgress() {
        val s = _state.value
        val content = s.current ?: return
        if (s.durationMs <= 0) return

        val ratio = s.positionMs.toDouble() / s.durationMs
        ContentEngagementService.recordPlayMilestone(content, ratio)

        ContinueWatchingService.saveProgress(
            contentId = content.id,
            positionMs = s.positionMs,
            durationMs = s.durationMs,
            title = content.title,
            thumbnailUrl = content.thumbnailUrl,
            type = content.type,
        )
    }

    /** هل هذا النوع يُشغَّل عبر هذه الطبقة؟ (صوت وفيديو فقط). */
    fun isPlayableType(type: ContentType): Boolean =
        type == ContentType.AUDIO || type == ContentType.VIDEO
}
