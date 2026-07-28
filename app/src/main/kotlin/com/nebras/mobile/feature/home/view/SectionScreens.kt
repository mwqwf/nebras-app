package com.nebras.mobile.feature.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.data.sortedContentOldestFirst
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.provider.SectionsLayout
import com.nebras.mobile.core.service.InterestProfileService
import com.nebras.mobile.core.service.SectionFollowService
import com.nebras.mobile.core.service.UserBehaviorTracker
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.util.ar
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.EmptyStateWidget
import com.nebras.mobile.core.widget.MediaContentCard
import com.nebras.mobile.core.widget.SectionFollowBell
import com.nebras.mobile.core.widget.SectionGridTile
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.home.model.HomeSection
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch

/**
 * شاشات تصفّح الأقسام — نقل الأصناف العامّة الثلاثة من
 * `features/home/view/home_screen.dart`:
 * `SubSectionsScreen` / `SecondarySectionsScreen` / `ContentListScreen`.
 *
 * كلّها تقرأ من [HomeViewModel] بالمعرّف (لا بالكائن) كي تبقى متزامنة مع
 * البثّ الحيّ: قسم أو ملفّ جديد من اللوحة يظهر فوراً دون رجوع للخلف.
 */

// ── تتبّع دورة حياة القسم ────────────────────────────────────────────

/**
 * نقل `core/widgets/section_lifecycle_tracker.dart`:
 * دخول القسم يسجّل زيارة (idempotent، يفيد الوصول من إشعار)، والخروج
 * يسجّل `recordSectionExit` الذي يحسم إن كان تصفّحاً جدّيّاً بحسب المدّة.
 */
@Composable
internal fun SectionLifecycleEffect(sectionId: String, sectionTitle: String) {
    DisposableEffect(sectionId) {
        UserBehaviorTracker.recordSectionVisit(sectionId, sectionTitle)
        InterestProfileService.onSectionView(sectionId, sectionTitle)
        onDispose { UserBehaviorTracker.recordSectionExit(sectionId) }
    }
}

// ── بطاقة القسم الموحَّدة ────────────────────────────────────────────

/**
 * نقل `core/widgets/youtube_section_card.dart` — صورة 16:9 في الأعلى ثمّ
 * أيقونة + عنوان عريض ثمّ سطر فرعيّ اختياريّ. (ليست في عقد الواجهة
 * المشترك، لذلك تُعرَّف هنا داخل ملفّات هذه الميزة.)
 */
@Composable
internal fun NebrasSectionCard(
    title: String,
    icon: ImageVector,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    subtitle: String? = null,
) {
    val hasSubtitle = !subtitle.isNullOrBlank()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onTap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            SmartNetworkImage(
                imageUrl = imageUrl,
                title = title,
                modifier = Modifier.fillMaxSize(),
                iconOverride = icon,
            )
        }
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = NebrasTextStyles.bodyBold.copy(fontSize = 15.sp, lineHeight = 20.sp),
                )
            }
            if (hasSubtitle) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle.orEmpty(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = NebrasTextStyles.greysmallText.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

/**
 * بطاقات أقسام داخل قائمة كسولة، بنمط العرض المحفوظ:
 * قائمة (بطاقة عريضة لكلّ قسم) أو شبكة ثلاثيّة الأعمدة.
 */
internal fun LazyListScope.sectionTiles(
    sections: List<HomeSection>,
    isGrid: Boolean,
    icon: ImageVector,
    onTap: (HomeSection) -> Unit,
    horizontalPadding: Dp = 16.dp,
) {
    if (isGrid) {
        // شبكة ثلاث أعمدة — نبنيها بصفوف داخل القائمة الكسولة لأنّ شبكة
        // كسولة داخل قائمة كسولة غير ممكنة (ارتفاع غير محدود).
        items(sections.chunked(3), key = { row -> "grid:${row.first().id}" }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (section in row) {
                    SectionGridTile(
                        title = section.title,
                        onTap = { onTap(section) },
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp),
                        imageUrl = section.imageUrl,
                    )
                }
                // نُكمل الصفّ الناقص بفراغات كي تبقى الأعمدة بعرض واحد.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        items(sections, key = { it.id }) { section ->
            NebrasSectionCard(
                title = section.title,
                icon = icon,
                onTap = { onTap(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                imageUrl = section.imageUrl,
            )
        }
    }
}

// ── الأقسام الفرعيّة ────────────────────────────────────────────────

/**
 * شاشة الأقسام الفرعية — تقرأ بالمعرّف من [HomeViewModel] حتى تبقى متزامنة
 * مع البثّ الحيّ (يظهر قسم فرعي جديد فور إنشائه من اللوحة).
 */
@Composable
fun SubSectionsScreen(
    mainSectionId: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val layout by ServiceLocator.sectionsLayoutController.layout.collectAsStateWithLifecycle()
    val followed by SectionFollowService.followedIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val mainSection = state.sections.firstOrNull { it.id == mainSectionId }
        ?: HomeSection(id = mainSectionId, title = "", type = "main_section")
    val subSections = state.sections.filter {
        it.type == "sub_section" && it.parentId == mainSectionId
    }

    // 🐞 إصلاح «الصفحة الفارغة» من إشعار القسم: قسم رئيسيّ بلا أقسام فرعيّة
    // لكنّه يحوي محتوًى مباشراً → نعرض قائمة المحتوى بدل رسالة الفراغ.
    if (subSections.isEmpty() && mainSection.items.isNotEmpty()) {
        ContentListScreen(
            sectionId = mainSectionId,
            navController = navController,
            homeViewModel = homeViewModel,
        )
        return
    }

    SectionLifecycleEffect(mainSectionId, mainSection.title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBarWidget(
                title = mainSection.title,
                onBack = { navController.popBackStack() },
                actions = {
                    // متابعة القسم 🔔: إشعار عند نشر محتوى جديد فيه.
                    SectionFollowBell(
                        isFollowed = followed.contains(mainSectionId),
                        onToggle = {
                            scope.launch {
                                SectionFollowService.toggle(mainSectionId, mainSection.title)
                            }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        if (subSections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateWidget(message = "No subsections available".ar)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                sectionTiles(
                    sections = subSections,
                    isGrid = layout == SectionsLayout.GRID,
                    icon = Icons.Outlined.AccountTree,
                    onTap = { subSection ->
                        // نسجّل بمعرّف القسم الفرعيّ المضغوط نفسه (لا الأب) كي
                        // يعكس التصفّح اهتمام المستخدم بالقسم الصحيح.
                        UserBehaviorTracker.recordSectionBrowsed(subSection.id, subSection.title)
                        ContentRouter.openSection(navController, subSection, state.sections)
                    },
                )
            }
        }
    }
}

// ── الأقسام الثانويّة ───────────────────────────────────────────────

/**
 * شاشة الأقسام الثانوية داخل قسم فرعيّ، ومعها **المحتوى المباشر** للقسم
 * الفرعيّ (العناصر بلا قسم ثانويّ) أسفل البطاقات كي يبقى كلّ محتوى القسم
 * قابلاً للوصول. الترتيب: الأقدم أولاً كبقيّة القوائم.
 */
@Composable
fun SecondarySectionsScreen(
    subSectionId: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val layout by ServiceLocator.sectionsLayoutController.layout.collectAsStateWithLifecycle()

    val subSection = state.sections.firstOrNull { it.id == subSectionId }
        ?: HomeSection(id = subSectionId, title = "", type = "sub_section")
    val secondarySections = state.sections.filter {
        (it.type == "secondary_section" || it.type == "secondary_sub_section") &&
            it.parentId == subSectionId
    }

    // العناصر التي لها قسم ثانويّ تظهر داخل بطاقته فلا تُكرَّر هنا
    // (نُبقي ما حقل `secondary_subsection` فيه فارغ فقط).
    val directItems = subSection.items
        .filter { it.subSection?.trim().isNullOrEmpty() }
        .sortedContentOldestFirst()

    val hasSecondaries = secondarySections.isNotEmpty()
    val hasDirect = directItems.isNotEmpty()

    SectionLifecycleEffect(subSectionId, subSection.title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBarWidget(
                title = subSection.title,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        if (!hasSecondaries && !hasDirect) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateWidget(message = "لا توجد أقسام ثانوية داخل هذا القسم الفرعي")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (hasSecondaries) {
                    sectionTiles(
                        sections = secondarySections,
                        isGrid = layout == SectionsLayout.GRID,
                        icon = Icons.Outlined.DeviceHub,
                        onTap = { secondary ->
                            // نسجّل بمعرّف القسم الثانويّ المضغوط نفسه (لا الأب).
                            UserBehaviorTracker.recordSectionBrowsed(secondary.id, secondary.title)
                            InterestProfileService.onSectionView(secondary.id, secondary.title)
                            navController.navigate(Routes.contentList(secondary.id))
                        },
                    )
                }
                // محتوى القسم الفرعيّ المباشر (بطاقات وسائط، نفس عرض
                // ContentListScreen) أسفل بطاقات الأقسام الثانويّة.
                if (hasDirect) {
                    items(directItems, key = { it.id }) { item ->
                        MediaContentCard(
                            content = item,
                            onTap = { ContentRouter.open(navController, item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── قائمة المحتوى ───────────────────────────────────────────────────

/**
 * قائمة المحتوى داخل قسم معيّن — تقرأ بالمعرّف من [HomeViewModel] فيظهر
 * الملفّ المرفوع من اللوحة فوراً دون الرجوع للخلف.
 *
 * ملاحظة للمطوّر — منطق الترتيب المزدوج:
 *   • الأقسام: ترتيب **ديناميكيّ** بحسب سلوك المستخدم (في HomeViewModel).
 *   • المحتوى داخل الأقسام: ترتيب **زمنيّ** الأقدم أولاً دائماً
 *     (`compareContentOldestFirst`). اختيار تصميميّ مقصود — لا تُعدّله.
 */
@Composable
fun ContentListScreen(
    sectionId: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val state by homeViewModel.state.collectAsStateWithLifecycle()

    val section = state.sections.firstOrNull { it.id == sectionId }
        ?: HomeSection(id = sectionId, title = "", type = "fallback")
    val sortedItems = section.items.sortedContentOldestFirst()

    SectionLifecycleEffect(sectionId, section.title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppBarWidget(
                title = section.title,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        if (sortedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateWidget(message = "No content in this section".ar)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(sortedItems, key = { it.id }) { item ->
                    MediaContentCard(
                        content = item,
                        onTap = { ContentRouter.open(navController, item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
