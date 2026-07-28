package com.nebras.mobile.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.core.widget.contentTypeIcon
import com.nebras.mobile.ui.Routes
import com.nebras.mobile.media.PlaybackController

/**
 * المشغّل المصغّر العائم — نقل `audio_mini_player.dart` و`video_mini_player.dart`
 * موحَّدَين (كلاهما يقرأ من نفس [PlaybackController]).
 *
 * يُرسَم فوق كلّ الشاشات فيبقى ظاهراً أثناء التصفّح بعد تصغير المشغّل، ولا
 * يعترض اللمس إلّا داخل حدوده. يختفي تلقائياً حين تُفتح شاشة المشغّل الكاملة
 * (`setMiniPlayerVisible(false)`) أو حين لا يوجد عنصر قيد التشغيل.
 */
@Composable
fun NebrasMiniPlayer(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val playback by PlaybackController.state.collectAsStateWithLifecycle()
    val content = playback.current

    AnimatedVisibility(
        visible = content != null && playback.miniPlayerVisible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val c = content ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                // شريط تقدّم رفيع فوق البطاقة.
                LinearProgressIndicator(
                    progress = { playback.progressRatio },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // النقر يفتح الشاشة الكاملة المناسبة للنوع.
                            val route = if (c.type == ContentType.VIDEO) {
                                Routes.videoPlayer(c.id)
                            } else {
                                Routes.audioPlayer(c.id)
                            }
                            navController.navigate(route)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmartNetworkImage(
                        imageUrl = c.thumbnailUrl,
                        title = c.title,
                        iconOverride = contentTypeIcon(c.type),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = c.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (c.author.isNotEmpty()) {
                            Text(
                                text = c.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = PlaybackController::togglePlayPause) {
                        Icon(
                            imageVector = if (playback.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (playback.isPlaying) {
                                "إيقاف مؤقّت"
                            } else {
                                "تشغيل"
                            },
                        )
                    }
                    IconButton(onClick = PlaybackController::stop) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق المشغّل")
                    }
                }
            }
        }
    }
}
