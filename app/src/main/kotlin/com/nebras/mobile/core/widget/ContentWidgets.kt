package com.nebras.mobile.core.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.provider.SectionsLayout

/** أيقونة نوع المحتوى الموحَّدة عبر التطبيق. */
fun contentTypeIcon(type: ContentType): ImageVector = when (type) {
    ContentType.BOOK -> Icons.AutoMirrored.Outlined.MenuBook
    ContentType.AUDIO -> Icons.Outlined.Headphones
    ContentType.IMAGE -> Icons.Outlined.Image
    ContentType.ARTICLE -> Icons.AutoMirrored.Outlined.Article
    ContentType.VIDEO, ContentType.YOUTUBE -> Icons.Outlined.PlayCircle
}

/**
 * بطاقة محتوى بصورة 16:9 وعنوان ووصف — نقل `core/widgets/media_content_card.dart`.
 */
@Composable
fun MediaContentCard(
    content: Content,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val author = content.author.trim()
    val description = content.description.trim()
    val sectionLabel = content.sectionName?.trim().orEmpty()
    val subtitleParts = buildList {
        if (sectionLabel.isNotEmpty()) add(sectionLabel)
        if (author.isNotEmpty()) add(author)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onTap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            SmartNetworkImage(
                imageUrl = content.thumbnailUrl,
                title = content.title,
                modifier = Modifier.fillMaxSize(),
                iconOverride = contentTypeIcon(content.type),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    contentTypeIcon(content.type),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            }
        }

        Column(Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 12.dp)) {
            Text(
                text = content.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                ),
            )
            if (subtitleParts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitleParts.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showDescription && description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AutoLinkText(
                    text = description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = onSurface.copy(alpha = 0.65f),
                    ),
                )
            }
        }
    }
}

/**
 * قائمة محتوى أفقيّة — نقل `core/widgets/horizental_contentlist_widget.dart`.
 *
 * ملاحظة: القائمة تُعرض باتّجاه LTR داخل واجهة RTL (كما في نسخة Flutter)
 * كي يبقى التمرير الأفقيّ متوافقاً مع ترتيب البطاقات.
 */
@Composable
fun HorizontalContentList(
    items: List<Content>,
    onItemTap: (Content) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 230.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    /** يُخفي عنصراً أبلغ عنه المستخدم أو غير متاح أوفلاين. */
    isHidden: (Content) -> Boolean = { false },
    /** هل يمكن فتح العنصر الآن؟ (يمنع فتح وسائط غير محمّلة أوفلاين). */
    canOpen: (Content) -> Boolean = { true },
    onBlockedTap: (() -> Unit)? = null,
) {
    val visible = items.filterNot(isHidden)
    LazyRow(
        modifier = modifier.height(height),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(visible, key = { it.id }) { content ->
            MediaContentCard(
                content = content,
                modifier = Modifier.width(260.dp),
                onTap = {
                    if (canOpen(content)) onItemTap(content) else onBlockedTap?.invoke()
                },
            )
        }
    }
}

/**
 * إسناد المصدر والرخصة — شفافيّة تُطمئن المراجع والمستخدم.
 * نقل `core/widgets/content_attribution.dart`.
 */
@Composable
fun ContentAttribution(content: Content, modifier: Modifier = Modifier) {
    val source = content.sourceName.trim()
    val license = content.licenseName.trim()
    val licenseUrl = content.licenseUrl.trim()
    if (source.isEmpty() && license.isEmpty()) return

    val context = LocalContext.current
    val parts = buildList {
        if (source.isNotEmpty()) add("المصدر: $source")
        if (license.isNotEmpty()) add("الرخصة: $license")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(
                if (licenseUrl.isNotEmpty()) {
                    Modifier.clickable { openExternalUrlSafely(context, licenseUrl) }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Verified,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * زرّ «القسم: …» أسفل المحتوى — يفتح مسار القسم كاملاً بصعود متدرّج.
 * نقل `core/widgets/content_section_button.dart`.
 */
@Composable
fun ContentSectionButton(
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (label.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = 0.08f))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "القسم: $label",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = primary,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = primary,
        )
    }
}

/** مبدّل نمط عرض الأقسام (قائمة/شبكة) — نقل `sections_layout_toggle.dart`. */
@Composable
fun SectionsLayoutToggle(
    layout: SectionsLayout,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val isGrid = layout == SectionsLayout.GRID
    IconButton(onClick = onToggle, modifier = modifier) {
        AnimatedContent(targetState = isGrid, label = "layoutToggle") { grid ->
            Icon(
                imageVector = if (grid) {
                    Icons.AutoMirrored.Filled.List
                } else {
                    Icons.Default.GridView
                },
                contentDescription = if (grid) "عرض قائمة" else "عرض شبكة",
                tint = tint ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** جرس متابعة القسم — نقل `core/widgets/section_follow_bell.dart`. */
@Composable
fun SectionFollowBell(
    isFollowed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFollowed) {
                Icons.Default.Notifications
            } else {
                Icons.Default.NotificationsNone
            },
            contentDescription = if (isFollowed) "إلغاء متابعة القسم" else "متابعة القسم",
            tint = if (isFollowed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * مسار التنقّل بين الأقسام (breadcrumb) — نقل
 * `core/widgets/content_section_breadcrumb_nav.dart`.
 */
@Composable
fun ContentSectionBreadcrumb(
    trail: List<Pair<String, String>>,
    onSegmentTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (trail.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(trail) { (id, title) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.clickable { onSegmentTap(id) },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (id != trail.last().first) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
