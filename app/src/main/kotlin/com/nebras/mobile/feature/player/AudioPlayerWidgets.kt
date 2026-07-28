package com.nebras.mobile.feature.player

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.DeepLinkService
import java.util.Locale

/**
 * شريط تقدّم الصوت — نقل
 * `features/audio_player/view/widgets/audioo_progressbar_widget.dart`.
 *
 * أثناء السحب نعرض قيمة السحب المحلّيّة (لا موضع المشغّل) كي لا يقفز
 * المؤشّر تحت إصبع المستخدم، ونطبّق التغيير عند الإفلات فقط.
 */
@Composable
fun AudioProgressBar(
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
    val shownRatio = if (isDragging) dragValue else liveRatio
    val shownPositionMs = if (isDragging) (dragValue * duration).toLong() else positionMs

    Column(modifier.fillMaxWidth()) {
        Slider(
            value = shownRatio,
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
                activeTrackColor = MaterialTheme.colorScheme.primary,
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            Text(
                text = formatDuration(shownPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(duration),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/** `mm:ss` أو `h:mm:ss` — نفس تنسيق نسخة Flutter. */
fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * مشاركة عنصر عبر رابط نبراس العميق — بديل `share_plus`.
 * الرابط `nebras://content/{id}` يفتح العنصر مباشرة عند من لديه التطبيق.
 */
fun shareContent(context: Context, content: Content) {
    val link = DeepLinkService.contentLink(content.id)
    val text = buildString {
        append(content.title)
        if (content.author.isNotEmpty()) append("\n${content.author}")
        append("\n\n$link")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, content.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
