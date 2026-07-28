package com.nebras.mobile.core.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import kotlin.math.min

/**
 * النظام الذكيّ الشامل لعرض الصور (Global Smart Thumbnail System).
 *
 * ── الفلسفة ──
 * * **أداة واحدة على مستوى التطبيق**: طبقة البيانات **لا تلمس** قيمة
 *   `imageUrl` ولا تُبدّلها ببدائل ثابتة — تمرّ كما هي (حتى لو `null`).
 * * **التذكية في طبقة العرض فقط**: إن وُجدت صورة صالحة تُعرض، وإلّا (أو عند
 *   فشل التحميل) يُعرض عنصر نائب أنيق: تدرّج لونيّ + أيقونة.
 * * **لا اعتماد على أصول ثنائيّة**: العنصر النائب مبنيّ من تدرّج + أيقونة
 *   فيعمل أوفلاين بالكامل وبأداء ثابت.
 *
 * ⚠️ **حماية الذاكرة**: نمرّر حدّ أبعاد لفكّ التشفير — بدونه كانت صور
 * Firebase Storage تُفكّ بدقّتها الأصليّة (عدّة ميغابكسل) فتنهار أجهزة 2GB
 * بـ OOM على شاشات القوائم الطويلة. Coil يتولّى ذلك تلقائياً من قياس
 * العنصر، ونضيف `crossfade` بنفس مدّة نسخة Flutter (200ms).
 */
@Composable
fun SmartNetworkImage(
    imageUrl: String?,
    title: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    width: Dp? = null,
    height: Dp? = null,
    /** يغلب على أيقونة التصنيف التلقائيّ (كتاب/صوت/فيديو لبطاقات المحتوى). */
    iconOverride: ImageVector? = null,
    /**
     * إن كان `true` يُعرض مؤشّر تحميل أثناء الجلب. افتراضيّاً نُظهر العنصر
     * النائب مباشرة لأنّه أخفّ بصريّاً ومطابق للوجه النهائيّ.
     */
    showLoadingIndicator: Boolean = false,
) {
    val url = imageUrl?.trim().orEmpty()
    val category = SmartThumbnailClassifier.classify(title)

    val sized = modifier
        .let { if (width != null) it.then(Modifier.size(width, height ?: width)) else it }

    if (url.isEmpty()) {
        SmartPlaceholder(category, iconOverride, sized)
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(200)
            .build(),
        contentDescription = title,
        contentScale = contentScale,
        modifier = sized,
        loading = {
            if (showLoadingIndicator) {
                LoadingTile(category)
            } else {
                SmartPlaceholder(category, iconOverride)
            }
        },
        error = { SmartPlaceholder(category, iconOverride) },
    )
}

// ── المصنِّف ─────────────────────────────────────────────────────────

/**
 * فئات العنصر النائب. يمكن توسيعها لاحقاً دون كسر أيّ مستدعٍ.
 */
enum class SmartCategory { QURAN, CREED, FIQH, HADITH, HISTORY, LANGUAGE, GENERAL }

/**
 * مصنِّف **محايد** للعنصر النائب — نقيّ بلا أيّ وصول للشبكة.
 *
 * نبراس منصّة معرفيّة **عامّة** لا دينيّة، لذا أُزيل التصنيف القديم القائم
 * على كلمات إسلاميّة الذي كان يحيّز ألوان البطاقات وأيقوناتها نحو فئة
 * واحدة. بدلاً منه نوزّع العنوان على لوحات ألوان محايدة عبر تجزئة ثابتة —
 * تنوّع بصريّ بلا أيّ دلالة فئويّة.
 */
object SmartThumbnailClassifier {

    private val BUCKETS = listOf(
        SmartCategory.QURAN,
        SmartCategory.CREED,
        SmartCategory.FIQH,
        SmartCategory.HADITH,
        SmartCategory.HISTORY,
        SmartCategory.LANGUAGE,
    )

    fun classify(title: String?): SmartCategory {
        val text = title?.trim().orEmpty()
        if (text.isEmpty()) return SmartCategory.GENERAL
        val index = Math.abs(text.hashCode()) % BUCKETS.size
        return BUCKETS[index]
    }
}

// ── العنصر النائب البصريّ ───────────────────────────────────────────

/**
 * بطاقة افتراضيّة ذكيّة: تدرّج لونيّ + أيقونة. الألوان الأساسيّة مختارة
 * لتبقى جميلة في الوضعين الفاتح والداكن.
 */
@Composable
private fun SmartPlaceholder(
    category: SmartCategory,
    iconOverride: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(category)
    val icon = iconOverride ?: iconFor(category)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    BoxWithConstraints(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = if (isDark) {
                    listOf(palette.dark1, palette.dark2)
                } else {
                    listOf(palette.light1, palette.light2)
                },
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        // الأيقونة تتناسب مع حجم الويدجت: ~42% من أقصر ضلع، بحدود معقولة
        // حتى لا تختفي في الأفاتار الصغيرة ولا تضخم في البطاقات.
        val side = min(maxWidth.value, maxHeight.value)
        val iconSize = (side * 0.42f).coerceIn(18f, 64f).dp
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.White.copy(alpha = 0.92f),
        )
    }
}

/** نفس التصميم لكن مع مؤشّر تحميل — اختياريّ. */
@Composable
private fun LoadingTile(category: SmartCategory, modifier: Modifier = Modifier) {
    val palette = paletteFor(category)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(palette.light1, palette.light2)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = Color.White,
        )
    }
}

private data class Palette(
    val light1: Color,
    val light2: Color,
    val dark1: Color,
    val dark2: Color,
)

private fun paletteFor(c: SmartCategory): Palette = when (c) {
    // أخضر زاهٍ → زمرّدي.
    SmartCategory.QURAN -> Palette(
        Color(0xFF2E7D5B), Color(0xFF4FAE80), Color(0xFF1F5C42), Color(0xFF2E7D5B),
    )
    // نيليّ → أزرق.
    SmartCategory.CREED -> Palette(
        Color(0xFF2C3E7A), Color(0xFF5A6DB5), Color(0xFF1A2450), Color(0xFF2C3E7A),
    )
    // تركوازي → فيروزي.
    SmartCategory.FIQH -> Palette(
        Color(0xFF117A8B), Color(0xFF3FAEC2), Color(0xFF0B4F5C), Color(0xFF117A8B),
    )
    // عنبري → ذهبيّ.
    SmartCategory.HADITH -> Palette(
        Color(0xFFB3701F), Color(0xFFE2A552), Color(0xFF7F4B12), Color(0xFFB3701F),
    )
    // بنّي → نحاسيّ.
    SmartCategory.HISTORY -> Palette(
        Color(0xFF7A4B2A), Color(0xFFB57B4F), Color(0xFF4E2F1B), Color(0xFF7A4B2A),
    )
    // بنفسجيّ → ورديّ.
    SmartCategory.LANGUAGE -> Palette(
        Color(0xFF6A3A8C), Color(0xFFA567C4), Color(0xFF44255B), Color(0xFF6A3A8C),
    )
    // رماديّ أردوازيّ محايد.
    SmartCategory.GENERAL -> Palette(
        Color(0xFF455A64), Color(0xFF78909C), Color(0xFF263238), Color(0xFF455A64),
    )
}

/**
 * أيقونات معرفيّة محايدة (لا دلالة دينيّة) — رموز معرفة عامّة متنوّعة
 * بصريّاً تتبع لوحة اللون المُجزّأة.
 */
private fun iconFor(c: SmartCategory): ImageVector = when (c) {
    SmartCategory.QURAN -> Icons.Rounded.AutoStories
    SmartCategory.CREED -> Icons.Rounded.Lightbulb
    SmartCategory.FIQH -> Icons.Rounded.Science
    SmartCategory.HADITH -> Icons.Rounded.MenuBook
    SmartCategory.HISTORY -> Icons.Rounded.HistoryEdu
    SmartCategory.LANGUAGE -> Icons.Rounded.Translate
    SmartCategory.GENERAL -> Icons.Rounded.CollectionsBookmark
}
