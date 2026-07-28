package com.nebras.mobile.feature.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.AutoLinkText
import com.nebras.mobile.core.widget.SmartNetworkImage

/**
 * نقل `features/content/view/content_card_widget.dart` — بطاقة عنصر داخل
 * القوائم الأفقيّة (عرض 160dp، مصغَّرة + عنوان + سطر وصف).
 *
 * ── Layout هيكليّ مُحصَّن ضدّ الـ overflow ──
 * المصغَّرة تأخذ ما يتبقّى من الارتفاع (`weight(1f)`) بعد النصوص الفعليّة،
 * والعمود يملأ الارتفاع المفروض من الأعلى. لذلك **يجب** أن تُستعمل البطاقة
 * داخل حاوية محدَّدة الارتفاع (قائمة أفقيّة) تماماً كما في نسخة Flutter.
 *
 * ⚠️ لا مقابل لـ `Hero` في Compose بلا تبعية إضافيّة؛ [heroTag] يبقى في
 * التوقيع للتوافق مع المستدعين ولا يؤثّر بصريّاً.
 */
@Composable
fun ContentCardWidget(
    icon: ImageVector,
    title: String,
    imagePath: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    heroTag: String? = null,
    /**
     * وصف اختياريّ يُعرض تحت العنوان — حتى يظهر الوصف الذي يكتبه المشرف في
     * لوحة التحكّم داخل بطاقات المحتوى بدل بقائه في شاشة التفاصيل فقط.
     */
    description: String? = null,
    isAvailableOffline: Boolean = true,
    isMetadataCached: Boolean = false,
) {
    val isDark = isDarkTheme()
    val shape = RoundedCornerShape(16.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .width(160.dp)
            .fillMaxHeight()
            // مؤشر بصري خفيف فقط: المحتوى غير المحمّل يبقى مرئياً للمعاينة
            // النصية، لكن يخفّ قليلاً كي لا يبدو جاهزاً للتشغيل أوفلاين.
            .alpha(if (isAvailableOffline) 1.0f else 0.72f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Colors.black12 blurRadius 4 offset (0,2) في الوضع الفاتح فقط.
                    if (isDark) Modifier else Modifier.shadow(2.dp, shape),
                )
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (isDark) {
                        Modifier.border(
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            ),
                            shape,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onTap)
                .padding(12.dp),
        ) {
            // Thumbnail — يأخذ المساحة المتبقّية تلقائيّاً.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                // بلا `iconOverride`: أيقونة النوع تُرسم فوق الصورة في طبقة
                // مستقلّة (كما في Flutter) فلا نُكرّرها داخل العنصر النائب.
                SmartNetworkImage(
                    imageUrl = imagePath,
                    title = title,
                    modifier = Modifier.fillMaxSize(),
                )
                // أيقونة نوع الوسيط (كتاب/صوت/فيديو) فوق الصورة الفعليّة أو
                // فوق العنصر النائب الذكيّ — مقصود بصريّاً.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(8.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White,
                    )
                }
                if (isAvailableOffline || isMetadataCached) {
                    Box(
                        modifier = Modifier
                            // PositionedDirectional(top, end) — محاذاة واعية
                            // بالاتّجاه فتقع على اليسار داخل واجهة RTL.
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(4.dp),
                    ) {
                        Icon(
                            if (isAvailableOffline) {
                                Icons.Rounded.DownloadDone
                            } else {
                                Icons.Rounded.CloudQueue
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                style = NebrasTextStyles.bodyBold.copy(fontSize = 14.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val trimmedDescription = description?.trim().orEmpty()
            if (trimmedDescription.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                AutoLinkText(
                    text = trimmedDescription,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = onSurface.copy(alpha = 0.6f),
                        lineHeight = 14.3.sp, // height: 1.3 × 11sp
                    ),
                    // سطر واحد في البطاقة (معاينة) — الوصف الكامل في شاشة
                    // التفاصيل. سطران كانا يجعلان ارتفاع البطاقة متغيّراً.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * بديل `Theme.of(context).brightness == Brightness.dark` — نستنتج الوضع من
 * سطوع لون السطح الحاليّ لا من إعداد النظام (السمة قد تكون مفروضة يدويّاً).
 */
@Composable
@ReadOnlyComposable
private fun isDarkTheme(): Boolean {
    val c = MaterialTheme.colorScheme.surface
    return (c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f) < 0.5f
}
