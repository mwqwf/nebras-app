package com.nebras.mobile.feature.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nebras.mobile.BuildConfig
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.openExternalUrlSafely
import kotlinx.coroutines.launch

/** رابط سياسة الخصوصية العامّ (بدون تسجيل دخول — متوافق مع Google Play). */
const val PRIVACY_POLICY_URL = "https://nibras-app-website.vercel.app/privacy"

/**
 * شاشة «حول» — نقل `features/home/view/about_screen.dart`.
 * تعرض اسم التطبيق ورسالته وسياسة المحتوى ورابط الخصوصية ورقم الإصدار.
 */
@Composable
fun AboutScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // يُقرأ من حزمة التطبيق وقت التشغيل بدل رقم ثابت قد يتعارض مع الإصدار
    // الفعليّ (بديل `PackageInfo.fromPlatform()`).
    val version = remember { BuildConfig.VERSION_NAME }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppBarWidget(
                title = "حول",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            AppIcon()
            Spacer(Modifier.height(24.dp))
            // اسم التطبيق
            Text("نبراس", style = NebrasTextStyles.heading1)
            Spacer(Modifier.height(8.dp))
            // العبارة التعريفيّة
            Text(
                text = "بيئة معرفية متكاملة للقارئ العربي",
                modifier = Modifier.fillMaxWidth(),
                style = NebrasTextStyles.body,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            // رسالتنا
            Text(
                text = "رسالتنا",
                modifier = Modifier.fillMaxWidth(),
                style = NebrasTextStyles.heading3,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "نبراس منصّة معرفية عربية حديثة، غايتنا أن نقدّم للقارئ العربي " +
                    "محتوى جميلاً وراقياً في بيئة واحدة متكاملة: كتب ومواد صوتية " +
                    "ومرئية ومقالات تنمو وتتجدّد يوماً بعد يوم، لتيسير الوصول إلى " +
                    "المعرفة بلغة عربية خالصة. محتوانا معرفيّ عام لا يقتصر على دين " +
                    "أو طائفة أو توجّه — فالمعرفة هنا للجميع.\n\n" +
                    "والنشر في نبراس ليس حكراً علينا: عبر ميزة «القنوات» يمكن لأيّ " +
                    "مستخدم أن ينشئ قناته الخاصة ويشارك الجمهور ما لديه من محتوى " +
                    "ومعرفة من داخل التطبيق مباشرة.",
                modifier = Modifier.fillMaxWidth(),
                style = NebrasTextStyles.body,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(32.dp))
            // سياسة المحتوى — تصريح متوافق مع Google Play بأنّ التطبيق لا ينشر
            // عمداً محتوًى مخالفاً، مع الإشارة لإمكانيّة الإبلاغ.
            Text(
                text = "سياسة المحتوى",
                modifier = Modifier.fillMaxWidth(),
                style = NebrasTextStyles.heading3,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "نبراس منصّة محتوى موسوعيّ معرفيّ عامّ. لا ننشر عمداً أيّ محتوى " +
                    "ينتهك حقوق الملكية الفكرية أو حقوق النشر، ولا أيّ محتوى جنسيّ صريح " +
                    "أو يضرّ بالأطفال أو يحضّ على العنف أو الإرهاب. كلّ المحتوى يُفترض أن " +
                    "يكون عامّاً أو مرخّصاً. إن صادفت مخالفة، استخدم زرّ «إبلاغ» على " +
                    "المحتوى وسنراجعها ونزيلها فوراً عند ثبوتها.",
                modifier = Modifier.fillMaxWidth(),
                style = NebrasTextStyles.body,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(32.dp))
            // سياسة الخصوصية — رابط عامّ يُفتح في المتصفّح الخارجيّ.
            OutlinedButton(
                onClick = {
                    val ok = openExternalUrlSafely(context, PRIVACY_POLICY_URL)
                    if (!ok) {
                        scope.launch { snackbarHostState.showSnackbar("سياسة الخصوصية") }
                    }
                },
            ) {
                Icon(Icons.Outlined.PrivacyTip, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("سياسة الخصوصية")
            }
            Spacer(Modifier.height(24.dp))
            // الإصدار — يُعرض فقط بعد قراءته من الحزمة (لا رقم ثابت).
            if (version.isNotEmpty()) {
                Text(
                    text = "الإصدار $version",
                    style = NebrasTextStyles.greysmallText,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * أيقونة التطبيق من الأصول، ومع تعذّر تحميلها مربّع بلون أساسيّ شفّاف يحمل
 * أيقونة كتاب (نظير `errorBuilder` في Flutter).
 */
@Composable
private fun AppIcon() {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    if (failed) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    AsyncImage(
        // نفس أصل Flutter: assets/images/app_icon.png (نُسِخ إلى assets/).
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/images/app_icon.png")
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier
            .size(96.dp)
            .clip(shape),
    )
}
