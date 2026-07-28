package com.nebras.mobile.feature.reader

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.core.widget.contentTypeIcon
import com.nebras.mobile.feature.download.MediaDownloadController
import com.nebras.mobile.feature.download.MediaDownloadStatus

/** رأس صفحة الكتاب: غلاف + عنوان + مؤلّف — نقل `widgets/boook_header.dart`. */
@Composable
fun BookHeader(content: Content, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SmartNetworkImage(
            imageUrl = content.thumbnailUrl,
            title = content.title,
            iconOverride = contentTypeIcon(content.type),
            modifier = Modifier
                .width(110.dp)
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (content.author.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = content.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content.sectionName?.takeIf { it.isNotEmpty() }?.let { section ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = section,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** صفّ معلومات الكتاب (الحجم والنوع) — نقل `widgets/book_info_widget.dart`. */
@Composable
fun BookInfoRow(content: Content, modifier: Modifier = Modifier) {
    val size = content.sizeInBytes?.toLong() ?: 0L
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (size > 0) {
            InfoChip(label = "الحجم", value = MediaDownloadController.humanSize(size))
        }
        InfoChip(label = "النوع", value = "PDF")
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * قسم التنزيل بحالاته الخمس — نقل `widgets/download_section.dart` مع
 * `idle_view` / `downloading_view` / `deownload_view` / `error_view`.
 */
@Composable
fun DownloadSection(
    content: Content,
    status: MediaDownloadStatus,
    progress: Double,
    totalBytes: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSource = !content.sourceUrl.isNullOrEmpty()

    Column(modifier.fillMaxWidth()) {
        when {
            !hasSource -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "لا يوجد مصدر متاح",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            status == MediaDownloadStatus.COMPLETED -> {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("افتح الكتاب")
                }
            }

            status == MediaDownloadStatus.DOWNLOADING -> {
                DownloadProgressRow(
                    progress = progress,
                    totalBytes = totalBytes,
                    trailing = {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "إيقاف مؤقّت")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "إلغاء")
                        }
                    },
                )
            }

            status == MediaDownloadStatus.PAUSED -> {
                DownloadProgressRow(
                    progress = progress,
                    totalBytes = totalBytes,
                    label = "متوقّف مؤقّتاً",
                    trailing = {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "استئناف")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = "إلغاء")
                        }
                    },
                )
            }

            status == MediaDownloadStatus.FAILED -> {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "تعذّر التنزيل",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Text("إعادة المحاولة")
                    }
                }
            }

            else -> {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (totalBytes > 0) {
                            "تنزيل (${MediaDownloadController.humanSize(totalBytes)})"
                        } else {
                            "تنزيل للقراءة"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressRow(
    progress: Double,
    totalBytes: Long,
    label: String? = null,
    trailing: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label ?: "جارٍ التنزيل…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("${(progress * 100).toInt()}%")
                        if (totalBytes > 0) {
                            append(" · ${MediaDownloadController.humanSize(totalBytes)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

/** حالة فارغة موحَّدة داخل شاشات الكتاب — نقل `widgets/error_view.dart`. */
@Composable
fun BookErrorView(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("إعادة المحاولة") }
        }
    }
}
