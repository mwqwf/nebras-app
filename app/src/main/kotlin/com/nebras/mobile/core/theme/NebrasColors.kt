package com.nebras.mobile.core.theme

import androidx.compose.ui.graphics.Color

/**
 * نقل حرفيّ لـ `lib/core/theme/color_manager.dart` (`ColorsManager`).
 *
 * الأسماء مطابقة تماماً لنظيرتها في Dart حتى تبقى ترجمة شيفرة الميزات
 * ميكانيكيّة بلا تخمين (`ColorsManager.greycolor` → `NebrasColors.greycolor`).
 * قيم `Colors.*` من Flutter حُوِّلت إلى ما يقابلها في Material حرفيّاً.
 */
object NebrasColors {
    // 🔵 Primary Colors
    val primary = Color(0xFF1E3A8A) // Deep Indigo
    val primaryLight = Color(0xFF3B82F6)
    val primaryDark = Color(0xFF1E293B)
    val primaryblue = Color(0xFF2196F3) // Colors.blue — Bright Blue

    // 🟢 Secondary / Accent
    val secondary = Color(0xFF0F766E) // Muted Teal
    val accent = Color(0xFF10B981) // Emerald Accent

    // ⚪ Backgrounds
    val background = Color(0xFFFFFFFF) // Colors.white
    val whiteColor = Color(0xFFFFFFFF)
    val greycolor = Color(0xFF64748B)
    val blackcolor = Color(0xFF000000)
    val darkBackground = Color(0xFF121212)
    val transparent = Color(0x00000000) // Colors.transparent

    // ⚫ Text Colors
    val textPrimary = Color(0xFF0F172A) // Near Black
    val textSecondary = Color(0xFF475569)
    val textMuted = Color(0xFF94A3B8)
    val textOnPrimary = Color(0xFFFFFFFF)
    val white70 = Color(0xB3FFFFFF) // Colors.white70

    // 🔴 Status Colors
    val success = Color(0xFF16A34A)
    val warning = Color(0xFFF59E0B)
    val error = Color(0xFFDC2626)
    val info = Color(0xFF2563EB)

    // 🧱 Borders & Dividers
    val border = Color(0xFFE2E8F0)
    val divider = Color(0xFFCBD5E1)

    // neutrals
    val textfieldcolor = Color(0xFFFDFDFF)
    val textfieldbordercolor = Color(0xFFEDEDED)
    val textlightgrey = Color(0xFFC2C2C2)

    // 🌫 Overlays
    val overlay = Color(0x99000000)

    // 🔘 Disabled
    val disabled = Color(0xFFCBD5E1)

    // ──────────────────────────────────────────────────────────────
    // 🎨 رموز سطح/خلفية احترافية للسمة (إضافيّة — لا تُزيل ما سبق)
    // تُستعمل في NebrasTheme.kt لبناء ColorScheme متناسق للوضعين.
    // ──────────────────────────────────────────────────────────────
    // فاتح
    val lightScaffold = Color(0xFFF5F6F9) // أبيض مائل للرمادي
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceVariant = Color(0xFFEDF0F5)
    val lightOutline = Color(0xFFE2E6EC)

    // داكن (نِيلِيّ عميق راقٍ بدل الأسود الصريح)
    val darkSurface = Color(0xFF181B21)
    val darkSurfaceVariant = Color(0xFF232830)
    val darkOutline = Color(0xFF323844)
    val darkOnSurface = Color(0xFFE7EAF0)
    val darkOnSurfaceMuted = Color(0xFF9AA3B2)

    // لون أساسيّ مُفتَّح للوضع الداكن (تباين أفضل من النِّيليّ العميق)
    val primaryOnDark = Color(0xFF6691F4)

    // ── ألوان Flutter المستعملة مباشرة في `text_styles.dart` ──────────
    /** `Colors.green` — نصّ حالة النجاح. */
    val materialGreen = Color(0xFF4CAF50)

    /** `Colors.redAccent` — نصّ حالة الخطأ. */
    val materialRedAccent = Color(0xFFFF5252)

    /** `Colors.orange` — نصّ حالة التحذير. */
    val materialOrange = Color(0xFFFF9800)
}
