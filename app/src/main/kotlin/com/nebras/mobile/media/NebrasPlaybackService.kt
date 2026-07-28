package com.nebras.mobile.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nebras.mobile.MainActivity
import com.nebras.mobile.core.network.NebrasHttpClient

/**
 * خدمة التشغيل الخلفيّة — بديل `audio_service` + `NebrasAudioHandler`.
 *
 * تُبقي المشغّل حيّاً بعد إغلاق الشاشة وتعرض إشعار الوسائط بالطاقم الكامل
 * (السابق / إرجاع 10ث / تشغيل-إيقاف / تقديم 10ث / التالي) وعلى شاشة القفل.
 *
 * Media3 يتولّى ما كان يفعله المعالِج اليدويّ في نسخة Flutter:
 *   • بثّ الحالة إلى `MediaSession` و`MediaStyle` notification.
 *   • إدارة **التركيز الصوتيّ** (بديل `audio_session`) عبر
 *     [ExoPlayer.Builder.setAudioAttributes] مع `handleAudioFocus = true`،
 *     فيتوقّف التشغيل تلقائياً عند المكالمات والتنبيهات ويستأنف بعدها.
 *   • أزرار الوسائط (سمّاعات/سيارة/ساعة) عبر `MediaSession` مباشرةً — لا
 *     حاجة لـ `MediaButtonReceiver` منفصل.
 */
@OptIn(UnstableApi::class)
class NebrasPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(NebrasHttpClient.instance),
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // إيقاف التشغيل عند نزع السمّاعة — سلوك متوقَّع لمشغّلات الصوت.
            .setHandleAudioBecomingNoisy(true)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MILLIS)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MILLIS)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivityIntent())
            .build()
    }

    /** النقر على الإشعار يفتح التطبيق على الشاشة الحاليّة. */
    private fun buildSessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * إيقاف الخدمة عند إزالة التطبيق من قائمة المهام **وهو متوقّف** — لو
     * تُرك يعمل بلا تشغيل لبقي إشعار الوسائط عالقاً.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        /** يطابق `fastForwardInterval`/`rewindInterval` في نسخة Flutter. */
        const val SEEK_INCREMENT_MILLIS = 10_000L
    }
}
