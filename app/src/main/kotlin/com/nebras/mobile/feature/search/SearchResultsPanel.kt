package com.nebras.mobile.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.SearchFallbackReason
import com.nebras.mobile.core.service.SearchItemType
import com.nebras.mobile.core.service.SearchResultItem
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.FadeInListItem
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.feature.community.UgcService
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.feature.home.model.HomeSection
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch

/**
 * نقل `features/search/view/widget/search_results_panel.dart`.
 *
 * يحوي: لوحة سجلّ البحث، لوحة النتائج (نتائج + اقتراحات + سبب الاحتياط)،
 * حالة «لا نتائج»، وصفّ رقائق الأقسام.
 */

// ── توجيه نتائج المجتمع ─────────────────────────────────────────────

/**
 * نقل `features/community/view/community_content_router.dart`.
 * يمنع فتح نتيجة المجتمع كأنها مادة رسمية: يعيد `true` إن كانت النتيجة
 * مجتمعيّة حتى لو أصبحت غير متاحة، كي لا يسقط المسار إلى مشغّل الرسميات.
 */
internal fun searchIsCommunityContent(content: Content): Boolean =
    content.id.trim().startsWith("ugc_")

internal suspend fun searchOpenIfCommunity(
    navController: NavController,
    content: Content,
    onMessage: (String) -> Unit,
): Boolean {
    if (!searchIsCommunityContent(content)) return false
    runCatching { UgcService.getPost(content.id) }
        .onSuccess { post ->
            if (post != null) {
                navController.navigate(Routes.ugcPost(post.content.id))
            } else {
                onMessage("هذا المنشور لم يعد متاحاً.")
            }
        }
        .onFailure { onMessage("تعذّر فتح منشور المجتمع.") }
    return true
}

/** فتح نتيجة بحث واحدة: قسم → موجّه الأقسام، محتوى → المجتمع ثمّ الرسميّ. */
internal suspend fun openSearchHit(
    hit: SearchResultItem,
    navController: NavController,
    allSections: List<HomeSection>,
    onMessage: (String) -> Unit,
) {
    val section = hit.section
    if (hit.type == SearchItemType.SECTION && section != null) {
        ContentRouter.openSection(navController, section, allSections)
        return
    }
    val content = hit.content ?: return
    if (searchOpenIfCommunity(navController, content, onMessage)) return
    ContentRouter.open(navController, content)
}

// ── سجلّ البحث ──────────────────────────────────────────────────────

/**
 * لوحة سجلّ البحث على نمط YouTube/Google: قائمة عموديّة نظيفة، كلّ صفّ
 * يحوي أيقونة ساعة + الكلمة + زرّ إزالة (×). تظهر فقط حين يكون شريط
 * البحث في حالة تركيز والاستعلام فارغ — تماماً كاقتراحات YouTube عند
 * النقر على الشريط دون كتابة. لا توجد رؤوس "Recent searches" أو زرّ
 * "Clear" لأنّ التصميم يعتمد البساطة كما في التطبيقات الكبرى.
 */
@Composable
fun RecentSearchesPanel(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentSearches.isEmpty()) return
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier.fillMaxWidth()) {
        for (term in recentSearches) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(term) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = term,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = NebrasTextStyles.body,
                )
                IconButton(
                    onClick = { onRemove(term) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "إزالة",
                        modifier = Modifier.size(18.dp),
                        tint = onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

// ── لوحة النتائج ────────────────────────────────────────────────────

/**
 * لوحة النتائج: ترويسة سبب الاحتياط (إن وُجد) ثمّ النتائج، ثمّ ترويسة
 * «قد يعجبك أيضًا» واقتراحاتها. القائمة كسولة (LazyColumn) كي تُبنى
 * الصفوف عند ظهورها فقط، مع الحفاظ على ترتيب `FadeInListItem` وفهارسه.
 */
@Composable
fun SearchResultsPanel(
    hits: List<SearchResultItem>,
    suggestions: List<SearchResultItem>,
    fallbackReason: SearchFallbackReason,
    navController: NavController,
    allSections: List<HomeSection>,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    val fallbackTitle = when (fallbackReason) {
        SearchFallbackReason.NONE -> null
        SearchFallbackReason.CLOSEST_SECTION -> "أقرب النتائج"
        SearchFallbackReason.POPULAR -> "اختيارات شائعة"
    }

    // 🐞 H7: لا نتائج ولا اقتراحات → كانت اللوحة تُرسَم فارغة تماماً فتبدو
    // كعطل. نعرض حالة فراغ واضحة باسم الاستعلام بدل بياض صامت.
    if (hits.isEmpty() && suggestions.isEmpty()) {
        NoResultsState(query = query, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (fallbackTitle != null) {
            item(key = "fallback_header") {
                ResultsSectionHeader(icon = Icons.Outlined.AutoAwesome, title = fallbackTitle)
            }
            item(key = "fallback_gap") { Spacer(Modifier.height(8.dp)) }
        }

        itemsIndexed(hits, key = { index, hit -> "hit:$index:${hit.dedupKey}" }) { index, hit ->
            FadeInListItem(index = index) {
                SearchHitTile(
                    hit = hit,
                    query = query,
                    navController = navController,
                    allSections = allSections,
                    onMessage = onMessage,
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            item(key = "suggestions_gap") { Spacer(Modifier.height(12.dp)) }
            item(key = "suggestions_header") {
                ResultsSectionHeader(icon = Icons.Outlined.Recommend, title = "قد يعجبك أيضًا")
            }
            item(key = "suggestions_gap2") { Spacer(Modifier.height(8.dp)) }
            itemsIndexed(
                suggestions,
                key = { index, hit -> "suggestion:$index:${hit.dedupKey}" },
            ) { index, hit ->
                FadeInListItem(index = hits.size + index) {
                    SearchHitTile(
                        hit = hit,
                        query = query,
                        navController = navController,
                        allSections = allSections,
                        onMessage = onMessage,
                    )
                }
            }
        }
    }
}

/**
 * حالة «لا توجد نتائج» — تُعرض حين يخلو البحث من أيّ تطابق أو اقتراح.
 * نص ودود يذكر كلمة الاستعلام (إن وُجدت) لتوضيح أنّ البحث جرى فعلاً.
 * تبقى قائمة كسولة كي يعمل السحب للتحديث حتى حين لا توجد نتائج.
 */
@Composable
private fun NoResultsState(query: String, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val q = query.trim()
    val message = if (q.isEmpty()) "لم يتم العثور على نتائج" else "لا نتائج عن «$q»"
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 48.dp, horizontal = 16.dp),
    ) {
        item(key = "no_results") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = onSurface.copy(alpha = 0.35f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = NebrasTextStyles.body.copy(color = onSurface.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

@Composable
private fun ResultsSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = NebrasTextStyles.bodyBold)
    }
}

@Composable
private fun SearchHitTile(
    hit: SearchResultItem,
    query: String,
    navController: NavController,
    allSections: List<HomeSection>,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isSection = hit.type == SearchItemType.SECTION
    val content = hit.content
    val isCommunity = content != null && searchIsCommunityContent(content)
    val imageUrl = hit.imageUrl

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        ListItem(
            modifier = Modifier.clickable {
                scope.launch { openSearchHit(hit, navController, allSections, onMessage) }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSection || imageUrl == null) {
                        Icon(
                            imageVector = if (isSection) {
                                Icons.Outlined.Folder
                            } else {
                                Icons.Outlined.PlayCircle
                            },
                            contentDescription = null,
                            tint = NebrasColors.primary,
                        )
                    } else {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                            SmartNetworkImage(
                                imageUrl = imageUrl,
                                title = hit.title,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            },
            headlineContent = { HighlightedHitTitle(title = hit.title, query = query) },
            supportingContent = {
                Text(
                    text = if (isCommunity) "من المجتمع • ${hit.subtitle}" else hit.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

/**
 * عنوان مع تظليل المقطع المطابق لكلمة البحث (بحث غير حسّاس لحالة
 * الأحرف). إن لم توجد مطابقة يعرض العنوان عاديّاً.
 */
@Composable
private fun HighlightedHitTitle(title: String, query: String) {
    val q = query.trim()
    if (q.isEmpty()) {
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }
    val start = title.lowercase().indexOf(q.lowercase())
    if (start < 0) {
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }
    val end = start + q.length
    val annotated = buildAnnotatedString {
        append(title.substring(0, start))
        withStyle(
            SpanStyle(fontWeight = FontWeight.Bold, color = NebrasColors.primary),
        ) {
            append(title.substring(start, end))
        }
        append(title.substring(end))
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── صفّ رقائق الأقسام ───────────────────────────────────────────────

/** صفّ أفقيّ من رقائق الأقسام — نقل `SearchSectionChipRow`. */
@Composable
fun SearchSectionChipRow(
    sections: List<SearchSectionOption>,
    onSelect: (SearchSectionOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sections.size, key = { index -> sections[index].id }) { index ->
            val section = sections[index]
            AssistChip(
                onClick = { onSelect(section) },
                label = { Text(section.name, style = NebrasTextStyles.smallText) },
            )
        }
    }
}
