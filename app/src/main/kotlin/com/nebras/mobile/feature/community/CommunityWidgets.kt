package com.nebras.mobile.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Groups2
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch

/**
 * 🏷️ شارة «مجتمع نبراس» — التمييز البصريّ الصارخ (قرار المالك) بين محتوى
 * المستخدمين والمحتوى الرسميّ: كلّ بطاقة/شاشة مجتمع تحملها في مكان بارز،
 * فلا يُنسب محتوى الناشرين إلى إدارة التطبيق أبداً.
 */
@Composable
fun UgcBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .background(NebrasColors.warning.copy(alpha = 0.15f), shape)
            .border(1.dp, NebrasColors.warning.copy(alpha = 0.6f), shape)
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 2.dp else 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Groups2,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp),
            tint = NebrasColors.warning,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "من المجتمع",
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.W700,
            color = NebrasColors.warning,
        )
    }
}

/** أيقونة نوع محتوى المجتمع — نقل `ugcTypeIcon`. */
fun ugcTypeIcon(type: ContentType): ImageVector = when (type) {
    ContentType.AUDIO -> Icons.Rounded.Headphones
    ContentType.VIDEO, ContentType.YOUTUBE -> Icons.Rounded.PlayCircleOutline
    ContentType.BOOK -> Icons.Rounded.MenuBook
    ContentType.ARTICLE -> Icons.Outlined.Article
    ContentType.IMAGE -> Icons.Outlined.Image
}

/** صورة القناة الدائريّة (أو حرفها الأوّل عند غياب الصورة). */
@Composable
fun UgcChannelAvatar(
    name: String,
    photoUrl: String,
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
) {
    val diameter = radius * 2
    if (photoUrl.trim().isNotEmpty()) {
        SubcomposeAsyncImage(
            model = photoUrl.trim(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(diameter)
                .clip(CircleShape)
                .background(NebrasColors.primary.copy(alpha = 0.15f)),
            loading = { UgcAvatarLetter(name, radius) },
            error = { UgcAvatarLetter(name, radius) },
        )
        return
    }
    Box(modifier = modifier.size(diameter)) {
        UgcAvatarLetter(name, radius)
    }
}

@Composable
private fun UgcAvatarLetter(name: String, radius: Dp) {
    // حرف واحد فقط من الاسم — «؟» إن كان الاسم فارغاً (نفس منطق Dart).
    val letter = name.trim().let { if (it.isEmpty()) "؟" else it.take(1) }
    Box(
        modifier = Modifier
            .size(radius * 2)
            .clip(CircleShape)
            .background(NebrasColors.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            fontSize = (radius.value * 0.9f).sp,
            fontWeight = FontWeight.Bold,
            color = NebrasColors.primary,
        )
    }
}

/**
 * غلاف مجتمع تلقائي: يعرض الصورة المستخرجة من الملف إن وُجدت، وإلا يولّد
 * غلافاً بصرياً غنيّاً بحسب النوع بدلاً من البطاقات الرمادية الفارغة.
 */
@Composable
fun UgcPostThumbnail(
    post: UgcPost,
    modifier: Modifier = Modifier,
) {
    val url = post.content.thumbnailUrl.trim()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        if (url.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = post.content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { GeneratedCommunityCover(post) },
                error = { GeneratedCommunityCover(post) },
            )
        } else {
            GeneratedCommunityCover(post)
        }
    }
}

@Composable
private fun GeneratedCommunityCover(post: UgcPost) {
    // بذرة ثابتة من العنوان لتوليد أعمدة موجة صوتية متفاوتة الارتفاع.
    val seed = post.content.title.sumOf { it.code }
    val isAudio = post.content.type == ContentType.AUDIO

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF075E54),
                        Color(0xFF0B8F75),
                        Color(0xFFD5A93C),
                    ),
                    start = Offset(Float.POSITIVE_INFINITY, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .absoluteOffset(x = (-24).dp, y = (-30).dp)
                .size(110.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.09f)),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isAudio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until 13) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(3.dp)
                                .height((14.0 + ((seed + i * 17) % 30)).dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFFFDF7A)),
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = ugcTypeIcon(post.content.type),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFFFFE08A),
                )
            }
        }
        Text(
            text = post.channelName,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.W700,
        )
    }
}

/**
 * بطاقة منشور مجتمع — تصميم مقصود مختلف عن بطاقات المحتوى الرسميّ:
 * إطار بلون التحذير، شارة «مجتمع»، واسم الناشر ظاهر دائماً (مثل YouTube).
 */
@Composable
fun UgcPostCard(
    post: UgcPost,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val titleStyle = MaterialTheme.typography.titleSmall
    val bodySmall = MaterialTheme.typography.bodySmall

    Column(
        modifier = modifier
            .then(if (horizontal) Modifier.width(210.dp) else Modifier)
            .clip(shape)
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.35f))
            .border(1.dp, NebrasColors.warning.copy(alpha = 0.35f), shape)
            .clickable(onClick = onTap)
            .padding(10.dp),
    ) {
        UgcPostThumbnail(post = post)
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ugcTypeIcon(post.content.type),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NebrasColors.primary,
            )
            Spacer(Modifier.weight(1f))
            UgcBadge(compact = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = post.content.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = titleStyle.copy(
                fontWeight = FontWeight.W700,
                lineHeight = titleStyle.fontSize * 1.3f,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            UgcChannelAvatar(
                name = post.channelName,
                photoUrl = post.channelPhotoUrl,
                radius = 10.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = post.channelName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = bodySmall.copy(color = scheme.onSurface.copy(alpha = 0.7f)),
            )
            if (post.commentsCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.ModeComment,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = scheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${post.commentsCount}",
                    style = bodySmall.copy(color = scheme.onSurface.copy(alpha = 0.55f)),
                )
            }
            if (post.reactionsCount > 0) {
                Spacer(Modifier.width(7.dp))
                Icon(
                    imageVector = Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = scheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${post.reactionsCount}",
                    style = bodySmall.copy(color = scheme.onSurface.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

/**
 * متابعة قناة ناشر: تحفظ الاختيار على الجهاز وتشترك في موضوع FCM خاصّ بها.
 *
 * [onMessage] يعرض رسالة النتيجة (SnackBar في نسخة Flutter) — الشاشة
 * المضيفة هي التي تملك `SnackbarHostState` في Compose.
 */
@Composable
fun ChannelFollowBell(
    channelId: String,
    channelName: String,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ids by UgcService.followedChannels.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val following = ids.contains(channelId)

    IconButton(
        onClick = {
            scope.launch {
                val nowFollowing = UgcService.toggleChannelFollow(channelId)
                onMessage(
                    if (nowFollowing) {
                        "ستصلك منشورات «$channelName» الجديدة."
                    } else {
                        "أُلغيت متابعة «$channelName»."
                    },
                )
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (following) {
                Icons.Filled.NotificationsActive
            } else {
                Icons.Filled.NotificationsNone
            },
            contentDescription = if (following) "إلغاء متابعة القناة" else "تابع هذه القناة",
            tint = if (following) {
                MaterialTheme.colorScheme.primary
            } else {
                androidx.compose.material3.LocalContentColor.current
            },
        )
    }
}

/**
 * نقطة دخول موحّدة لزرّ النشر العائم.
 *
 * في نسخة Compose صار النشر **وجهة تنقّل** (`Routes.PUBLISH`)، ولذلك انتقل
 * فحص الدخول وإنشاء القناة إلى داخل [PublishScreen] نفسها بدل تنفيذه هنا
 * قبل الدفع؛ السلوك النهائيّ للمستخدم واحد.
 */
object CommunityPublishLauncher {

    fun open(navController: NavController) {
        navController.navigate(Routes.PUBLISH)
    }
}
