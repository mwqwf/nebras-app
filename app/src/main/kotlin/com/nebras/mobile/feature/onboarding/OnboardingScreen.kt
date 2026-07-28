package com.nebras.mobile.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.util.ar
import kotlinx.coroutines.launch

/**
 * مفتاح «شوهدت شاشة التعريف» في التخزين المحلّي — تقرؤه شاشة الإقلاع لتقرير
 * عرض التعريف مرّةً واحدةً فقط. (نفس اسم المفتاح في نسخة Flutter كي يعمل
 * ترحيل `shared_preferences` بلا ترجمة.)
 */
const val ONBOARDING_SEEN_KEY = "onboarding_seen"

/** شريحة تعريف واحدة — أيقونة + مفتاح عنوان + مفتاح وصف. */
private data class OnboardingSlide(
    val icon: ImageVector,
    val titleKey: String,
    val descKey: String,
)

private val onboardingSlides = listOf(
    OnboardingSlide(
        Icons.Outlined.AutoStories,
        "onboarding_title_1",
        "onboarding_desc_1",
    ),
    OnboardingSlide(
        Icons.Outlined.DownloadForOffline,
        "onboarding_title_2",
        "onboarding_desc_2",
    ),
    OnboardingSlide(
        Icons.Outlined.BookmarkAdded,
        "onboarding_title_3",
        "onboarding_desc_3",
    ),
)

/**
 * شاشة تعريف بسيطة (3 شرائح) تُعرض لأوّل تشغيل فقط — نقل
 * `features/onboarding/onboarding_screen.dart`.
 *
 * مكتفية ذاتياً ولا تمسّ تدفّق الجلسة: عند الانتهاء/التخطّي تحفظ
 * [ONBOARDING_SEEN_KEY] ثمّ تستدعي [onFinished] كما كانت تنتقل إلى `AuthGate`.
 *
 * بديل `smooth_page_indicator` + `concentric_transition`: [HorizontalPager]
 * مع مؤشّر نقاط مرسوم يدوياً بنفس أبعاد النسخة الأصليّة.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingSlides.size })
    val scope = rememberCoroutineScope()

    val finish: () -> Unit = remember(onFinished) {
        {
            // فشل الحفظ غير قاتل — أسوأ حالة: تظهر الشاشة مرّةً أخرى.
            runCatching { ServiceLocator.localStore.putBool(ONBOARDING_SEEN_KEY, true) }
            onFinished()
        }
    }

    val isLast = pagerState.currentPage == onboardingSlides.size - 1

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scaffold يوفّر حشو أشرطة النظام (بديل SafeArea).
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(onClick = finish) {
                    Text("تخطٍّ")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val slide = onboardingSlides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        slide.icon,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = NebrasColors.primary,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = slide.titleKey.ar,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = slide.descKey.ar,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        // height: 1.5 في Flutter = مضاعف حجم الخطّ.
                        lineHeight = 22.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onboardingSlides.indices.forEach { index ->
                    val active = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 22.dp else 8.dp,
                        animationSpec = tween(250),
                        label = "onboardingDot$index",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(dotWidth)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) {
                                    NebrasColors.primary
                                } else {
                                    NebrasColors.primary.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }

            Box(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = {
                        if (isLast) {
                            finish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLast) "ابدأ الآن" else "التالي")
                }
            }
        }
    }
}
