package com.nebras.mobile.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import java.util.Calendar

/** `Colors.red.shade600` — خلفيّة السحب للحذف في بطاقة الإشعار. */
private val notificationDismissBackground = Color(0xFFE53935)

/**
 * بطاقة إشعار واحد — نقل `notifications/view/widget/notification_tile.dart`.
 *
 * [onDelete] زرّ حذف صريح (أيقونة سلّة ظاهرة): كثير من المستخدمين لا
 * يكتشفون السحب للحذف، فنوفّر إجراءً واضحاً بجانبه ينفّذ نفس المنطق.
 * [nowMs] يُمرَّر من الشاشة كي يتحدّث الوقت النسبيّ دوريّاً.
 */
@Composable
fun NotificationTile(
    title: String,
    body: String,
    createdAtMs: Long,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    isRead: Boolean = false,
    onTap: (() -> Unit)? = null,
    nowMs: Long = System.currentTimeMillis(),
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismissed()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { NotificationDismissBackground() },
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isRead) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        NebrasColors.primary.copy(alpha = 0.05f)
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (isRead) {
                        NebrasColors.border
                    } else {
                        NebrasColors.primary.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                .then(
                    if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // الأيقونة الدائريّة + نقطة «غير مقروء».
            Box(Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(NebrasColors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = NebrasColors.whiteColor,
                    )
                }
                if (!isRead) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF44336))
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = NebrasTextStyles.bodyBold,
                    color = if (isRead) NebrasColors.textMuted else NebrasColors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = NebrasTextStyles.smallText,
                    color = NebrasColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatNotificationTime(createdAtMs, nowMs),
                    style = NebrasTextStyles.smallText,
                    color = NebrasColors.textMuted,
                    fontSize = 11.sp,
                )
            }

            // زرّ حذف صريح وواضح بجانب كلّ إشعار (إضافةً إلى السحب) كي
            // يكتشفه المستخدم فوراً بلا حاجة لمعرفة إيماءة السحب.
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "حذف",
                        modifier = Modifier.size(22.dp),
                        tint = NebrasColors.textMuted,
                    )
                }
            }
        }
    }
}

/** خلفيّة السحب: «اسحب للحذف» + أيقونة سلّة على حافّة النهاية. */
@Composable
private fun NotificationDismissBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(notificationDismissBackground)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "اسحب للحذف",
                color = NebrasColors.whiteColor,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                tint = NebrasColors.whiteColor,
            )
        }
    }
}

/**
 * الوقت النسبيّ المعروض — نقل `_formatTime` حرفياً:
 * «الآن» ثمّ الدقائق ثمّ الساعات ثمّ الأيام (أقلّ من أسبوع) ثمّ التاريخ.
 */
internal fun formatNotificationTime(createdAtMs: Long, nowMs: Long): String {
    val diffMs = nowMs - createdAtMs
    val minutes = diffMs / 60_000L
    if (minutes < 1) return "الآن"
    if (minutes < 60) return "منذ $minutes دقيقة"
    val hours = diffMs / 3_600_000L
    if (hours < 24) return "منذ $hours ساعة"
    val days = diffMs / 86_400_000L
    if (days < 7) return "منذ $days يوم"
    val calendar = Calendar.getInstance().apply { timeInMillis = createdAtMs }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR)
    return "$day/$month/$year"
}
