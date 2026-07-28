package com.nebras.mobile.feature.download

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.wire
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.util.ar
import kotlinx.coroutines.delay

/**
 * زرّ تنزيل قابل لإعادة الاستعمال يعكس حالة التنزيل — نقل
 * `download/view/download_button_widget.dart`:
 *  - idle        → أيقونة تحميل
 *  - downloading → دائرة تقدّم مع إيقاف مؤقّت (ضغط مطوّل = إلغاء)
 *  - paused      → أيقونة استئناف
 *  - completed   → أيقونة صحّ (نقرة = حذف الملفّ)
 *  - failed      → أيقونة إعادة المحاولة
 */
@Composable
fun DownloadButton(content: Content, modifier: Modifier = Modifier) {
    val downloads by MediaDownloadController.state.collectAsStateWithLifecycle()
    val lastError by MediaDownloadController.lastError.collectAsStateWithLifecycle()
    val connectivity = ServiceLocator.connectivityController
    val isOnline by connectivity.isOnline.collectAsStateWithLifecycle()
    val isOnMobileData by connectivity.isOnMobileData.collectAsStateWithLifecycle()

    val contentId = content.id
    val item = downloads[contentId]
    val status = item?.downloadStatus ?: MediaDownloadStatus.IDLE
    val progress = (item?.progress ?: 0.0).toFloat()

    var message by remember { mutableStateOf<String?>(null) }
    var showMobileDataDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // أظهر رسالة الخطأ مرّةً واحدةً عند الانتقال إلى failed. `lastError`
    // مفتاح ترجمة صنّفه المتحكّم — نحلّه هنا عبر `.ar`.
    LaunchedEffect(lastError, status) {
        val error = lastError
        if (status == MediaDownloadStatus.FAILED &&
            error != null &&
            error.first == contentId
        ) {
            message = error.second.ar
            MediaDownloadController.clearLastError()
        }
    }

    val beginDownload: () -> Unit = {
        MediaDownloadController.start(
            contentId = contentId,
            url = content.sourceUrl.orEmpty(),
            contentType = content.type.wire,
            title = content.title,
            imageUrl = content.thumbnailUrl.takeIf { it.isNotBlank() },
        )
    }

    // يبدأ التنزيل بعد التحقّق من الشبكة: يمنع عند الانقطاع، ويؤكّد على
    // بيانات الجوّال (تفادي استهلاك الباقة في ملفّ كبير).
    val startDownload: () -> Unit = {
        when {
            !isOnline -> message = "لا يوجد اتصال بالإنترنت"
            isOnMobileData -> showMobileDataDialog = true
            else -> beginDownload()
        }
    }

    Box(modifier = modifier) {
        when (status) {
            MediaDownloadStatus.IDLE -> IconButton(onClick = startDownload) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "تحميل",
                    modifier = Modifier.size(28.dp),
                )
            }

            MediaDownloadStatus.DOWNLOADING -> Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(48.dp),
                        color = NebrasColors.primary,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = NebrasColors.primary,
                        strokeWidth = 2.5.dp,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "إيقاف مؤقّت (اضغط مطوّلاً للإلغاء)",
                    modifier = Modifier
                        .size(20.dp)
                        .pointerInput(contentId) {
                            detectTapGestures(
                                onTap = { MediaDownloadController.pause(contentId) },
                                onLongPress = { showCancelDialog = true },
                            )
                        },
                )
            }

            MediaDownloadStatus.PAUSED -> IconButton(
                onClick = { MediaDownloadController.resume(contentId) },
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "استئناف",
                    modifier = Modifier.size(28.dp),
                    tint = NebrasColors.warning,
                )
            }

            MediaDownloadStatus.COMPLETED -> IconButton(
                onClick = { showDeleteDialog = true },
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "تم التحميل",
                    modifier = Modifier.size(28.dp),
                    tint = NebrasColors.success,
                )
            }

            MediaDownloadStatus.FAILED -> IconButton(
                onClick = { MediaDownloadController.resume(contentId) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "إعادة المحاولة",
                    modifier = Modifier.size(28.dp),
                    tint = NebrasColors.error,
                )
            }
        }
    }

    if (showMobileDataDialog) {
        AlertDialog(
            onDismissRequest = { showMobileDataDialog = false },
            title = { Text("بيانات الجوّال") },
            text = { Text("أنت على بيانات الجوّال — هل تريد التنزيل؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMobileDataDialog = false
                        beginDownload()
                    },
                ) { Text("تحميل") }
            },
            dismissButton = {
                TextButton(onClick = { showMobileDataDialog = false }) { Text("إلغاء") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف التحميل") },
            text = { Text("حذف الملف المحمّل؟ يمكنك إعادة تحميله لاحقاً.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        MediaDownloadController.delete(contentId)
                    },
                ) { Text("حذف", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("إلغاء") }
            },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("إلغاء التحميل") },
            text = { Text("إيقاف هذا التحميل وحذفه؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        MediaDownloadController.cancel(contentId)
                    },
                ) { Text("نعم", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("لا") }
            },
        )
    }

    message?.let { text ->
        DownloadFloatingSnackbar(text) { message = null }
    }
}

/**
 * شريط رسالة عائم — نظير `ScaffoldMessenger.showSnackBar`: الزرّ يُستعمل
 * داخل شاشات لا نملك فيها `SnackbarHostState`، فنعرضه فوق النافذة بأنفسنا.
 */
@Composable
private fun DownloadFloatingSnackbar(message: String, onDismissed: () -> Unit) {
    Popup(alignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Snackbar { Text(message) }
        }
    }
    LaunchedEffect(message) {
        delay(4_000)
        onDismissed()
    }
}
