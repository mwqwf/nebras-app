package com.nebras.mobile.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * أوضاع السمة الثلاثة — مقابل `ThemeMode` في Flutter.
 * قيم التخزين (`wire`) مطابقة لما تكتبه نسخة Flutter في `theme_mode`
 * حتى يُقرأ التفضيل القديم بلا ترحيل إضافيّ.
 */
enum class ThemeMode(val wire: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun parse(value: String?): ThemeMode = when (value?.trim()?.lowercase()) {
            "dark" -> DARK
            "light" -> LIGHT
            else -> SYSTEM
        }
    }
}

/**
 * أنصاف أقطار موحَّدة — نفس القيم في `apptheme.dart`
 * (`_radius = 14`، الأزرار 12، الرقائق/الأوراق السفليّة 20).
 */
object NebrasDimens {
    val radius = 14.dp
    val buttonRadius = 12.dp
    val chipRadius = 20.dp
    val sheetRadius = 20.dp
}

val NebrasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(NebrasDimens.buttonRadius),
    medium = RoundedCornerShape(NebrasDimens.radius),
    large = RoundedCornerShape(NebrasDimens.sheetRadius),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * السمة الفاتحة — مبنيّة من نفس قيم `AppTheme.lightTheme`.
 */
val NebrasLightColorScheme: ColorScheme = lightColorScheme(
    primary = NebrasColors.primary,
    onPrimary = Color.White,
    primaryContainer = NebrasColors.primary.copy(alpha = 0.12f),
    onPrimaryContainer = NebrasColors.primary,
    secondary = NebrasColors.secondary,
    onSecondary = Color.White,
    tertiary = NebrasColors.accent,
    onTertiary = Color.White,
    error = NebrasColors.error,
    onError = Color.White,
    background = NebrasColors.lightScaffold,
    onBackground = NebrasColors.textPrimary,
    surface = NebrasColors.lightSurface,
    onSurface = NebrasColors.textPrimary,
    surfaceContainerHighest = NebrasColors.lightSurfaceVariant,
    onSurfaceVariant = NebrasColors.textSecondary,
    outline = NebrasColors.lightOutline,
    outlineVariant = NebrasColors.lightOutline,
    surfaceVariant = NebrasColors.lightSurfaceVariant,
)

/**
 * السمة الداكنة — مبنيّة من نفس قيم `AppTheme.darkTheme`.
 * الخلفيّة `darkBackground` (نظير `scaffoldBackgroundColor`) والسطح
 * `darkSurface` النِّيليّ العميق.
 */
val NebrasDarkColorScheme: ColorScheme = darkColorScheme(
    primary = NebrasColors.primaryOnDark,
    onPrimary = Color.White,
    primaryContainer = NebrasColors.primaryOnDark.copy(alpha = 0.12f),
    onPrimaryContainer = NebrasColors.primaryOnDark,
    secondary = NebrasColors.accent,
    onSecondary = Color.White,
    tertiary = NebrasColors.accent,
    onTertiary = Color.White,
    error = NebrasColors.error,
    onError = Color.White,
    background = NebrasColors.darkBackground,
    onBackground = NebrasColors.darkOnSurface,
    surface = NebrasColors.darkSurface,
    onSurface = NebrasColors.darkOnSurface,
    surfaceContainerHighest = NebrasColors.darkSurfaceVariant,
    onSurfaceVariant = NebrasColors.darkOnSurfaceMuted,
    outline = NebrasColors.darkOutline,
    outlineVariant = NebrasColors.darkOutline,
    surfaceVariant = NebrasColors.darkSurfaceVariant,
)

/**
 * جذر السمة — مقابل `MaterialApp(theme:, darkTheme:, themeMode:)`.
 *
 * يفعل ثلاثة أشياء:
 *   1. يحسم الوضع الفعليّ (تلقائيّ يتبع سطوع النظام).
 *   2. يفرض اتجاه RTL على الشجرة كاملة (التطبيق عربيّ خالص).
 *   3. يطبّق `ColorScheme` + خطّ Cairo + أنصاف الأقطار الموحَّدة.
 */
@Composable
fun NebrasTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDark) NebrasDarkColorScheme else NebrasLightColorScheme
    val family: FontFamily = rememberCairoFamily()
    val typography = remember(family) { nebrasTypography(family) }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalNebrasFontFamily provides family,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = NebrasShapes,
            content = content,
        )
    }
}
