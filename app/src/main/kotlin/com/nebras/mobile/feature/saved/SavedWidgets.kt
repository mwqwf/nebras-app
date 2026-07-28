package com.nebras.mobile.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.wire
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.util.ar
import com.nebras.mobile.core.widget.SmartNetworkImage
import kotlinx.coroutines.delay

/**
 * ويدجتات المحفوظات — نقل `saved/view/widget/saved_card_widget.dart` و
 * `saved/view/widget/save_button.dart`.
 */

/** أيقونة النوع — نقل `_typeIcon`. */
private fun savedTypeIcon(type: SavedContentType): ImageVector = when (type) {
    SavedContentType.VIDEO -> Icons.Filled.PlayCircleOutline
    SavedContentType.AUDIO -> Icons.Filled.Headphones
    SavedContentType.BOOK -> Icons.AutoMirrored.Filled.MenuBook
}

/** مفتاح النصّ (يُحلّ عبر `.ar`) — نقل `_typeLabel`. */
private fun savedTypeLabel(type: SavedContentType): String = when (type) {
    SavedContentType.VIDEO -> "Video"
    SavedContentType.AUDIO -> "Audio"
    SavedContentType.BOOK -> "Book"
}

/**
 * بطاقة عنصر محفوظ — تخطيط أفقيّ: صورة مصغّرة ثمّ المعلومات ثمّ زرّ الإزالة.
 *
 * [isDownloaded] هل العنصر متاح للتشغيل دون إنترنت (محمَّل بالكامل)؟
 * [downloadedSize] حجم الملفّ المنزَّل (نصّ مقروء) بجوار شارة «متاح دون إنترنت».
 */
@Composable
fun SavedCardWidget(
    item: SavedItem,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    downloadedSize: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // `margin: bottom 12` في Dart.
            .padding(bottom = 12.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // الصورة المصغّرة
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))) {
            SmartNetworkImage(
                imageUrl = item.imageUrl,
                title = item.title,
                modifier = Modifier.size(80.dp),
                iconOverride = savedTypeIcon(item.type),
            )
        }

        Spacer(Modifier.width(12.dp))

        // المعلومات
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = NebrasTextStyles.bodyBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))

            // شارة النوع
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NebrasColors.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = savedTypeIcon(item.type),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = NebrasColors.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = savedTypeLabel(item.type).ar,
                    fontSize = 11.sp,
                    color = NebrasColors.primary,
                    fontWeight = FontWeight.W500,
                )
            }

            Spacer(Modifier.size(6.dp))

            // تمييز واضح: «متاح دون إنترنت» للمنزَّل، و«مُفضّلة» لِما أُضيف
            // للحفظ فقط دون تنزيل.
            if (isDownloaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = NebrasColors.success,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (!downloadedSize.isNullOrEmpty()) {
                            "متاح دون إنترنت · $downloadedSize"
                        } else {
                            "متاح دون إنترنت"
                        },
                        fontSize = 10.sp,
                        color = NebrasColors.success,
                        fontWeight = FontWeight.W500,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = NebrasColors.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "مُفضّلة",
                        fontSize = 10.sp,
                        color = NebrasColors.primary,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
        }

        // زرّ الإزالة
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.BookmarkRemove,
                contentDescription = "Remove from saved".ar,
                modifier = Modifier.size(24.dp),
                tint = NebrasColors.error,
            )
        }
    }
}

/**
 * زرّ حفظ المحتوى في «المحفوظات» (إضافة/إزالة). يعتمد [SavedRepository]
 * المفرد فيظهر التغيير فوراً في تبويب المكتبة. الحفظ ≠ التنزيل: هذا الزرّ
 * يضيف العنصر للمفضّلة فقط، دون تنزيله على الجهاز.
 *
 * [showLabel] = true يعرضه كزرّ بارز مُسمّى (أيقونة + نص) للأماكن الظاهرة
 * كشاشة تفاصيل الكتاب؛ false (افتراضي) يعرضه أيقونةً مدمجة بجوار أزرار أخرى.
 */
@Composable
fun SaveButton(
    content: Content,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    showLabel: Boolean = false,
) {
    val repository = ServiceLocator.savedRepository
    val savedItems by repository.state.collectAsStateWithLifecycle()
    val isSaved = savedItems.any { it.id == content.id }

    // بديل SnackBar: الزرّ يُستعمل داخل شاشات لا نملك فيها Scaffold، فنعرض
    // الشريط بنفسه فوق النافذة بنفس النصّ والمدّة.
    var message by remember { mutableStateOf<String?>(null) }

    val color = if (isSaved) NebrasColors.primary else MaterialTheme.colorScheme.onSurface
    val icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder

    val toggle: () -> Unit = {
        repository.toggle(
            SavedItem(
                id = content.id,
                title = content.title,
                imageUrl = content.thumbnailUrl.takeIf { it.isNotBlank() },
                contentType = content.type.wire,
                savedAtMs = System.currentTimeMillis(),
            ),
        )
        val nowSaved = repository.isSaved(content.id)
        message = (if (nowSaved) "Added to saved" else "Removed from saved").ar
    }

    if (showLabel) {
        OutlinedButton(onClick = toggle, modifier = modifier) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isSaved) "محفوظ" else "حفظ",
                color = color,
                fontWeight = FontWeight.W600,
            )
        }
    } else {
        IconButton(onClick = toggle, modifier = modifier) {
            Icon(
                imageVector = icon,
                contentDescription = if (isSaved) "إزالة من المحفوظات" else "حفظ",
                modifier = Modifier.size(size ?: 28.dp),
                tint = color,
            )
        }
    }

    message?.let { text ->
        SavedFloatingSnackbar(text) { message = null }
    }
}

/** شريط رسالة عائم بمدّة ثانيتين — نظير `SnackBar` خارج Scaffold. */
@Composable
private fun SavedFloatingSnackbar(message: String, onDismissed: () -> Unit) {
    Popup(alignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Snackbar { Text(message) }
        }
    }
    LaunchedEffect(message) {
        delay(2_000)
        onDismissed()
    }
}

/** لون خلفيّة خلف بطاقة السحب للحذف — `Colors.red.shade100` في Flutter. */
internal val savedDismissBackground = Color(0xFFFFCDD2)

/** `Colors.red` — أيقونة الحذف على خلفيّة السحب. */
internal val savedDismissIconColor = Color(0xFFF44336)
