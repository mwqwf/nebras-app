package com.nebras.mobile.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.util.ar
import kotlinx.coroutines.launch

/**
 * شاشة الدخول/التسجيل بحساب Google.
 *
 * نستخدم **نفس الشاشة** لحالتَي «إنشاء حساب» و«تسجيل الدخول»، ونُمرّر
 * [isSignUp] فقط لتغيير النصوص: مُنتقي حسابات Google هو نفسه تقنيّاً
 * (لا فرق في الـ API)، وهذا يمنح تجربة موحّدة ويقلّل التكرار.
 */
@Composable
fun SignInScreen(
    isSignUp: Boolean,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // زرّ الرجوع الماديّ يعادل pop في Flutter.
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
        ) {
            // شريط علويّ شفّاف بزرّ رجوع فقط (مقابل AppBar الفارغ في Flutter).
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isSignUp) "إنشاء حسابك" else "مرحباً بعودتك",
                style = NebrasTextStyles.heading1,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isSignUp) {
                    "اختر حساب Google من هذا الجهاز لإنشاء حساب نبراس الخاص بك " +
                        "والاستمتاع بمحتوى مخصّص."
                } else {
                    "اختر حساب Google المستخدم سابقاً للدخول إلى نبراس واستعادة بياناتك."
                },
                // height: 1.6 → 16sp × 1.6
                style = NebrasTextStyles.body.copy(
                    color = NebrasColors.textSecondary,
                    lineHeight = 25.6.sp,
                ),
            )

            Spacer(Modifier.weight(1f))

            GoogleButton(
                busy = auth.isBusy,
                label = if (isSignUp) "التسجيل عبر Google" else "الدخول عبر Google",
                enabled = !auth.isBusy,
                onPressed = {
                    scope.launch {
                        val success = authViewModel.signInWithGoogle(context)
                        if (success) {
                            // نجاح الدخول: `AuthGate` غيّر حالته إلى signedIn
                            // ويعرض الواجهة الرئيسية، فنُغلق شاشة الدخول كي
                            // لا تبقى معلّقة فوقها.
                            onBack()
                            return@launch
                        }
                        val err = authViewModel.state.value.lastError
                        if (err != null) {
                            // المفاتيح التي يُرجعها AuthViewModel تُحلّ نصّاً
                            // عربيّاً عبر `.ar`؛ وإن لم يُطابق المفتاح أيّ
                            // إدخال يُعاد النصّ نفسه كما هو.
                            snackbarHostState.showSnackbar(err.ar)
                            authViewModel.clearError()
                        }
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "نستخدم حساب Google للتعرّف على هويتك فقط. لن نتلقّى كلمة المرور " +
                    "ولن نشارك أي بيانات شخصية.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                // height: 1.5 → 14sp × 1.5
                style = NebrasTextStyles.greysmallText.copy(lineHeight = 21.sp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GoogleButton(
    busy: Boolean,
    label: String,
    enabled: Boolean,
    onPressed: () -> Unit,
) {
    Button(
        onClick = onPressed,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NebrasColors.border),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = NebrasColors.textPrimary,
            disabledContainerColor = Color.White,
            disabledContentColor = NebrasColors.textPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.5.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.2.dp,
                color = NebrasColors.primary,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoogleLogo()
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = NebrasTextStyles.bodyBold.copy(color = NebrasColors.textPrimary),
                )
            }
        }
    }
}

/**
 * شعار Google مرسوم بخطّ بسيط بدلاً من أيقونة شبكيّة أو أصل إضافي.
 * نعتمد أربع ألوان العلامة الرسمية (نقل `_GooglePainter`).
 */
@Composable
private fun GoogleLogo() {
    val blue = Color(0xFF4285F4)
    val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05)
    val green = Color(0xFF34A853)

    Box(Modifier.size(22.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val center = Offset(w / 2f, size.height / 2f)
            val radius = w / 2f
            val arcRadius = radius * 0.95f
            val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
            val stroke = Stroke(width = w * 0.22f, cap = StrokeCap.Butt)

            // 4 أقواس لتقليد الشعار الرسمي (مبسّطة للبنية الرياضية).
            // زوايا Flutter بالراديان → درجات (Compose يستعمل الدرجات).
            val rad = (180.0 / Math.PI).toFloat()
            drawArc(blue, -1.05f * rad, 1.15f * rad, false, topLeft, arcSize, style = stroke)
            drawArc(green, 0.10f * rad, 1.30f * rad, false, topLeft, arcSize, style = stroke)
            drawArc(yellow, 1.40f * rad, 1.35f * rad, false, topLeft, arcSize, style = stroke)
            drawArc(red, 2.75f * rad, 1.25f * rad, false, topLeft, arcSize, style = stroke)

            // شريط G الداخلي (خطّ أزرق أفقي صغير).
            val barWidth = w * 0.38f
            val barHeight = w * 0.18f
            val barCenter = Offset(center.x + w * 0.18f, center.y)
            drawRect(
                color = blue,
                topLeft = Offset(barCenter.x - barWidth / 2f, barCenter.y - barHeight / 2f),
                size = Size(barWidth, barHeight),
            )
        }
    }
}
