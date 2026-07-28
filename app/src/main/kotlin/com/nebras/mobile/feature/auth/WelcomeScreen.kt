package com.nebras.mobile.feature.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles

/**
 * شاشة الترحيب — البوّابة الأولى للتطبيق للمستخدمين غير المسجّلين.
 *
 * تصميم هادئ ومركزيّ: شعار التطبيق داخل إطار دائريّ ناعم، عبارة ترحيب
 * محايدة تعكس طابع المعرفة العامّة، ثمّ زرّان متميّزان («إنشاء حساب» بارز
 * و«تسجيل الدخول» ثانويّ) وخيار التصفّح كضيف.
 *
 * في Flutter كان زرّا الحساب يدفعان `SignInScreen` بانتقال تلاشٍ 260ms؛
 * هنا نستبدل الدفع بـ [Crossfade] بنفس المدّة لأنّ شاشة الترحيب ليست
 * وجهةً في `NavHost` (تعيش داخل `AuthGate`).
 */
@Composable
fun WelcomeScreen(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    // null = شاشة الترحيب، true = إنشاء حساب، false = تسجيل الدخول.
    var signUpMode by remember { mutableStateOf<Boolean?>(null) }

    Crossfade(
        targetState = signUpMode,
        animationSpec = tween(260),
        label = "welcomeToSignIn",
    ) { mode ->
        if (mode == null) {
            WelcomeBody(
                authViewModel = authViewModel,
                onGoToSignIn = { isSignUp -> signUpMode = isSignUp },
                modifier = modifier,
            )
        } else {
            SignInScreen(
                isSignUp = mode,
                authViewModel = authViewModel,
                onBack = { signUpMode = null },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun WelcomeBody(
    authViewModel: AuthViewModel,
    onGoToSignIn: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminanceIsDark()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scaffold يوفّر حشو أشرطة النظام (بديل SafeArea).
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(2f))
            AppBadge(isDark = isDark)
            Spacer(Modifier.height(28.dp))
            GreetingBlock(isDark = isDark)
            Spacer(Modifier.weight(3f))

            PrimaryButton(
                label = "إنشاء حساب",
                icon = Icons.Rounded.PersonAddAlt1,
                onPressed = { onGoToSignIn(true) },
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                label = "تسجيل الدخول",
                icon = Icons.AutoMirrored.Rounded.Login,
                onPressed = { onGoToSignIn(false) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "الدخول عبر حساب Google الموجود على هذا الجهاز",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = NebrasTextStyles.greysmallText,
            )
            Spacer(Modifier.height(12.dp))
            // خيار واضح للتصفّح بدون حساب — تسجيل الدخول غير إلزاميّ،
            // ويبقى متاحاً لاحقاً لتخصيص التجربة (المحفوظات/التوصيات).
            TextButton(
                onClick = { authViewModel.continueAsGuest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    Icons.Outlined.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = NebrasColors.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "المتابعة كضيف",
                    style = NebrasTextStyles.button.copy(color = NebrasColors.primary),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** شعار التطبيق داخل دائرة ناعمة بلون أساسيّ شفّاف. */
@Composable
private fun AppBadge(isDark: Boolean) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(
                NebrasColors.primary.copy(alpha = if (isDark) 0.18f else 0.08f),
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            // نفس أصل Flutter: assets/images/app_icon.png (نُسِخ إلى assets/).
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/images/app_icon.png")
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
    }
}

@Composable
private fun GreetingBlock(isDark: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "بوّابتك إلى المعرفة العامة",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            // height: 1.6 في Flutter = مضاعف حجم الخطّ (18 × 1.6).
            style = NebrasTextStyles.heading3.copy(
                color = NebrasColors.primary,
                lineHeight = 28.8.sp,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "مرحباً بك في تطبيق نبراس",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = NebrasTextStyles.heading1.copy(lineHeight = 33.8.sp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "منصّتك الرقمية للمحتوى المصوَّر والمسموع والمكتوب. " +
                "أنشئ حسابك أو سجّل دخولك للبدء.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = NebrasTextStyles.body.copy(
                color = if (isDark) NebrasColors.textMuted else NebrasColors.textSecondary,
                lineHeight = 25.6.sp,
            ),
        )
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    icon: ImageVector,
    onPressed: () -> Unit,
) {
    Button(
        onClick = onPressed,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NebrasColors.primary,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(label, style = NebrasTextStyles.button.copy(color = Color.White))
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    icon: ImageVector,
    onPressed: () -> Unit,
) {
    OutlinedButton(
        onClick = onPressed,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.4.dp,
            color = NebrasColors.primary.copy(alpha = 0.55f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NebrasColors.primary),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = NebrasColors.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = NebrasTextStyles.button.copy(color = NebrasColors.primary))
    }
}

/**
 * بديل `Theme.of(context).brightness == Brightness.dark` — نستنتج الوضع
 * من سطوع لون السطح الحاليّ بدل قراءة إعداد النظام (السمة قد تكون مفروضة
 * يدويّاً من مبدّل المظهر).
 */
private fun Color.luminanceIsDark(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f
