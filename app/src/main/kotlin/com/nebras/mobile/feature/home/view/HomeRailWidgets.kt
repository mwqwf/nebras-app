package com.nebras.mobile.feature.home.view

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.MediaContentCard
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.feature.home.model.HomeRail
import com.nebras.mobile.feature.home.model.HomeRailType
import java.util.Calendar

/**
 * ويدجتات رفوف الرئيسيّة — نقل `home/view/widgets/home_rails_sliver.dart`
 * و`home/view/widgets/daily_pick_card.dart`.
 *
 * في Compose تُضاف الرفوف كعناصر داخل [LazyListScope] بدل الـ Slivers.
 */

/** عتبة إظهار زرّ «اعرض الكلّ» — نفس رقم نسخة Flutter. */
private const val VIEW_ALL_THRESHOLD = 6

/**
 * رفوف أفقيّة للصفحة الرئيسيّة — نقل `HomeRailsSliver`.
 *
 * رفّ فارغ يُتجاهَل، ورفّ «تصفّح الأقسام» السفليّ أُزيل في نسخة Flutter
 * فأيّ رفّ من هذا النوع يُتجاهَل هنا أيضاً.
 */
fun LazyListScope.homeRails(
    rails: List<HomeRail>,
    onItemTap: (Content) -> Unit,
    onViewAll: (HomeRail) -> Unit,
    itemModifier: Modifier = Modifier,
) {
    items(rails, key = { it.type.name }) { rail ->
        if (!rail.isEmpty && rail.type != HomeRailType.BROWSE_SECTIONS) {
            HomeRailRow(
                rail = rail,
                onItemTap = onItemTap,
                onViewAll = onViewAll,
                modifier = itemModifier,
            )
        }
    }
}

/** رفّ أفقيّ واحد — نقل `_ContentHorizontalRail`. */
@Composable
fun HomeRailRow(
    rail: HomeRail,
    onItemTap: (Content) -> Unit,
    onViewAll: (HomeRail) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rail.title,
                modifier = Modifier.weight(1f),
                style = NebrasTextStyles.heading3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rail.items.size > VIEW_ALL_THRESHOLD) {
                TextButton(onClick = { onViewAll(rail) }) {
                    Text("اعرض الكلّ")
                }
            }
        }
        // الارتفاع يكفي للصورة 16:9 (280×157.5) + العنوان سطرَيْن + السطر
        // الفرعيّ، بعد إخفاء الوصف في الرفوف الأفقيّة (نمط YouTube).
        LazyRow(
            modifier = Modifier.height(250.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rail.items, key = { it.id }) { item ->
                MediaContentCard(
                    content = item,
                    onTap = { onItemTap(item) },
                    modifier = Modifier.width(280.dp),
                    showDescription = false,
                )
            }
        }
    }
}

/**
 * «اكتشف اليوم» 🎁 — اختيار **حتميّ** ببذرة التاريخ: نفس اليوم = نفس
 * العنصر للجميع بلا خادم. المرشّحون: أوّل 60 من المعروض مرتَّبين بالمعرّف
 * (ترتيب ثابت مهما تقلّب ترتيب المصدر خلال اليوم).
 */
fun dailyPickFor(pool: List<Content>, dayStartMillis: Long): Content? {
    if (pool.isEmpty()) return null
    val candidates = pool.take(60).sortedBy { it.id }
    val seed = dayStartMillis / 86_400_000L
    val index = (seed % candidates.size).toInt()
    return candidates[if (index < 0) index + candidates.size else index]
}

/** منتصف ليل اليوم المحلّي — نظير `DateTime(y, m, d)` في Dart. */
private fun todayStartMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * بطاقة «اكتشف اليوم» البارزة أعلى الرئيسيّة — نقل `DailyPickCard`.
 * [feed] هو `HomeProvider.effectiveFeed` (المعروض الممزوج شعبيّاً).
 */
@Composable
fun DailyPickCard(
    feed: List<Content>,
    onOpen: (Content) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pick = remember(feed) { dailyPickFor(feed, todayStartMillis()) } ?: return

    val scheme = MaterialTheme.colorScheme
    val thumb = pick.thumbnailUrl
    Box(modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            scheme.primaryContainer,
                            scheme.primaryContainer.copy(alpha = 0.55f),
                        ),
                    ),
                )
                .clickable { onOpen(pick) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "🎁 اكتشف اليوم",
                    style = NebrasTextStyles.smallText.copy(fontWeight = FontWeight.W700),
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = pick.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = NebrasTextStyles.heading3,
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "اضغط للاستكشاف ←",
                    style = NebrasTextStyles.smallText,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            if (thumb.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    SmartNetworkImage(
                        imageUrl = thumb,
                        title = pick.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
