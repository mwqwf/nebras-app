package com.nebras.mobile.core.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * نقل `lib/core/theme/text_helper.dart` — أوزان الخطّ المسمّاة.
 */
object FontWeightHelper {
    val thin = FontWeight.W100
    val extralight = FontWeight.W200
    val light = FontWeight.W300
    val normal = FontWeight.W400
    val medium = FontWeight.W500
    val semibold = FontWeight.W600
    val bold = FontWeight.W700
    val extrabold = FontWeight.W800
}

/**
 * نقل `lib/core/theme/text_keys_maanger.dart` — مفاتيح نصوص قديمة.
 * أُبقيت كما هي (لا تُحذف ما يبدو ميتاً).
 */
object TextKeysManager {
    const val welcomeMessage = "welcome_message"
    const val homeTitle = "home_title"
    const val profileTitle = "profile_title"
    const val settingsTitle = "settings_title"
    const val searchHint = "search_hint"
    const val notificationsTitle = "notifications_title"
    const val messagesTitle = "messages_title"
}

/**
 * خطّ **Cairo** عبر Downloadable Fonts (بديل `google_fonts` في Flutter).
 *
 * التطبيق عربيّ خالص، لذلك سقط الفرع اللاتينيّ (`GoogleFonts.poppins`) من
 * `text_styles.dart`: كلّ الأنماط تُبنى بـ Cairo دائماً.
 *
 * ⚠️ مزوّد خطوط Google يتطلّب مصفوفة شهادات
 * `R.array.com_google_android_gms_fonts_certs` في `res/values/`. لا نُشير
 * إليها وقت التجميع (ملفّات `res/` خارج نطاق هذا الوكيل)، بل نبحث عنها
 * وقت التشغيل بالاسم؛ فإن غابت نسقط بأمان إلى خطّ النظام (يعرض العربية
 * جيّداً) بدل تعطيل الواجهة.
 */
object NebrasFonts {
    private const val PROVIDER_AUTHORITY = "com.google.android.gms.fonts"
    private const val PROVIDER_PACKAGE = "com.google.android.gms"
    private const val CERTS_RES_NAME = "com_google_android_gms_fonts_certs"

    private val cairoFont = GoogleFont("Cairo")

    private fun provider(context: Context): GoogleFont.Provider? {
        val certsRes = context.resources.getIdentifier(
            CERTS_RES_NAME,
            "array",
            context.packageName,
        )
        if (certsRes == 0) return null
        return GoogleFont.Provider(PROVIDER_AUTHORITY, PROVIDER_PACKAGE, certsRes)
    }

    /** عائلة Cairo بالأوزان المستعملة فعليّاً، أو خطّ النظام عند تعذّر المزوّد. */
    fun cairoFamily(context: Context): FontFamily {
        val provider = provider(context) ?: return FontFamily.Default
        return runCatching {
            FontFamily(
                Font(googleFont = cairoFont, fontProvider = provider, weight = FontWeight.W400),
                Font(googleFont = cairoFont, fontProvider = provider, weight = FontWeight.W500),
                Font(googleFont = cairoFont, fontProvider = provider, weight = FontWeight.W600),
                Font(googleFont = cairoFont, fontProvider = provider, weight = FontWeight.W700),
            )
        }.getOrElse { FontFamily.Default }
    }
}

/** عائلة الخطّ الفعّالة داخل الشجرة — يوفّرها [NebrasTheme]. */
val LocalNebrasFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * `Typography` بأحجام مطابقة لـ `text_styles.dart`:
 * 26 / 22 / 18 للعناوين، 16 للنصّ والأزرار، 15 للحالات، 14 للنصّ الصغير.
 */
fun nebrasTypography(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = family,
            fontSize = 26.sp,
            fontWeight = FontWeightHelper.bold,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = family,
            fontSize = 22.sp,
            fontWeight = FontWeightHelper.semibold,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = family,
            fontSize = 18.sp,
            fontWeight = FontWeightHelper.semibold,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = family,
            fontSize = 20.sp,
            fontWeight = FontWeightHelper.bold,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeightHelper.semibold,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = family,
            fontSize = 14.sp,
            fontWeight = FontWeightHelper.semibold,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeightHelper.normal,
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = family,
            fontSize = 15.sp,
            fontWeight = FontWeightHelper.normal,
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = family,
            fontSize = 14.sp,
            fontWeight = FontWeightHelper.normal,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeightHelper.semibold,
        ),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/** يبني الـ Typography من سياق Android مباشرة (خارج الـ Composition). */
fun nebrasTypography(context: Context): Typography =
    nebrasTypography(NebrasFonts.cairoFamily(context))

/**
 * مقابل `TextStyles` في Dart — نفس الأسماء ونفس الأحجام والأوزان والألوان.
 * كلّها `@Composable` لأنّها تقرأ عائلة الخطّ من الشجرة (بدل `BuildContext`).
 */
object NebrasTextStyles {
    @Composable
    @ReadOnlyComposable
    private fun style(
        size: TextUnit,
        weight: FontWeight,
        color: Color = Color.Unspecified,
    ): TextStyle = TextStyle(
        fontFamily = LocalNebrasFontFamily.current,
        fontSize = size,
        fontWeight = weight,
        color = color,
    )

    // HEADINGS
    val heading1: TextStyle
        @Composable @ReadOnlyComposable get() = style(26.sp, FontWeightHelper.bold)

    val heading2: TextStyle
        @Composable @ReadOnlyComposable get() = style(22.sp, FontWeightHelper.semibold)

    val heading3: TextStyle
        @Composable @ReadOnlyComposable get() = style(18.sp, FontWeightHelper.semibold)

    // BODY
    val body: TextStyle
        @Composable @ReadOnlyComposable get() = style(16.sp, FontWeightHelper.normal)

    val bodyPrimary: TextStyle
        @Composable @ReadOnlyComposable get() =
            style(16.sp, FontWeightHelper.normal, NebrasColors.primary)

    val bodyBold: TextStyle
        @Composable @ReadOnlyComposable get() = style(16.sp, FontWeightHelper.semibold)

    val smallText: TextStyle
        @Composable @ReadOnlyComposable get() = style(14.sp, FontWeightHelper.normal)

    val hintText: TextStyle
        @Composable @ReadOnlyComposable get() =
            style(14.sp, FontWeightHelper.normal, NebrasColors.greycolor)

    val greysmallText: TextStyle
        @Composable @ReadOnlyComposable get() =
            style(14.sp, FontWeightHelper.normal, NebrasColors.greycolor)

    // BUTTONS
    val button: TextStyle
        @Composable @ReadOnlyComposable get() = style(16.sp, FontWeightHelper.semibold)

    // STATES
    val success: TextStyle
        @Composable @ReadOnlyComposable get() =
            style(15.sp, FontWeightHelper.medium, NebrasColors.materialGreen)

    val error: TextStyle
        @Composable @ReadOnlyComposable get() =
            style(15.sp, FontWeightHelper.medium, NebrasColors.materialRedAccent)

    val warning: TextStyle
        @Composable @ReadOnlyComposable get() =
            style(15.sp, FontWeightHelper.medium, NebrasColors.materialOrange)
}

/** اختصار للوصول إلى typography الحاليّة بلا استيراد Material في كلّ ملفّ. */
val currentNebrasTypography: Typography
    @Composable @ReadOnlyComposable get() = MaterialTheme.typography

/** يُنشئ عائلة Cairo مرّة واحدة لسياق الشاشة الحاليّ. */
@Composable
fun rememberCairoFamily(): FontFamily {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) { NebrasFonts.cairoFamily(context) }
}
