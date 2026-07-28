package com.nebras.mobile.feature.home.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.provider.SectionsLayout
import com.nebras.mobile.core.service.ContentEngagementService
import com.nebras.mobile.core.service.DeepLinkService
import com.nebras.mobile.core.service.GuestAccountPromptService
import com.nebras.mobile.core.service.ShareIntentService
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.EmptyStateWidget
import com.nebras.mobile.core.widget.HomeSkeletonLoader
import com.nebras.mobile.core.widget.SectionsLayoutToggle
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.community.UgcPost
import com.nebras.mobile.feature.community.UgcPostCard
import com.nebras.mobile.feature.community.UgcService
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.feature.home.HomeUiState
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.home.model.HomeRail
import com.nebras.mobile.feature.home.model.HomeSection
import com.nebras.mobile.ui.Routes
import com.nebras.mobile.ui.shell.LocalNebrasShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * الشاشة الرئيسيّة — نقل `features/home/view/home_screen.dart`.
 *
 * شاشة **اكتشاف**: شريط علويّ (درج/إشعارات/بحث) ثمّ «اكتشف اليوم» ثمّ رفوف
 * أفقيّة (الجديد/الأكثر مشاهدة/تابع التصفّح/مقترَح لك) ثمّ رفّ المجتمع ثمّ
 * تصفّح الأقسام. لا ترقيم صفحات (`loadMore` لا-عمل مقصود) — الكتالوج الكامل
 * عبر البحث وتصفّح الأقسام.
 *
 * ملاحظات نقل:
 *  • `RefreshIndicator` ⟵ [PullToRefreshBox] من Material 3.
 *  • `CustomScrollView/Sliver*` ⟵ [LazyColumn] بعناصر مكافئة.
 *  • `Scaffold.of(context).openDrawer()` و`Navigator.push` للإشعارات/البحث
 *    ⟵ مقبض الهيكل [LocalNebrasShell] الذي يوفّره `HomeShell`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    authViewModel: AuthViewModel,
) {
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val layout by ServiceLocator.sectionsLayoutController.layout.collectAsStateWithLifecycle()
    val unreadCount by ServiceLocator.notificationsRepository.unreadCount
        .collectAsStateWithLifecycle()
    val notifications by ServiceLocator.notificationsRepository.state
        .collectAsStateWithLifecycle()
    val pendingDeepLinkId by DeepLinkService.pendingContentId.collectAsStateWithLifecycle()
    val pendingSharedFile by ShareIntentService.pendingFile.collectAsStateWithLifecycle()

    val shell = LocalNebrasShell.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // «اعرض الكلّ» لرفّ: حالة محليّة لا مسار تنقّل مستقلّ.
    var viewAllRail by remember { mutableStateOf<HomeRail?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showGuestPrompt by remember { mutableStateOf(false) }

    // اشتراك مباشر مع Firestore — أيّ محتوى جديد من لوحة التحكّم يصل فوراً
    // بلا مسح بيانات ولا إعادة تشغيل. ومؤقّت الخمول يبدأ من جاهزيّة الشاشة.
    LaunchedEffect(Unit) {
        homeViewModel.startWatching()
        homeViewModel.resetIdleTimer()
    }

    // التخصيص مفعَّل لأصحاب الحسابات فقط (user != null). الضيف يرى
    // الأحدث/الأكثر مشاهدة دون أيّ رفوف شخصيّة. ودخول الضيف بحساب حقيقيّ
    // لاحقاً يُفعّل الرفوف الشخصيّة ويبدأ الاشتراك فوراً.
    var firstAuthSync by remember { mutableStateOf(true) }
    LaunchedEffect(auth.status, auth.user?.id) {
        homeViewModel.setPersonalizationEnabled(auth.user != null)
        if (firstAuthSync) {
            firstAuthSync = false
            return@LaunchedEffect
        }
        if (auth.isSignedIn) homeViewModel.startWatching()
    }

    // عند عودة التطبيق إلى المقدّمة نُعيد الاشتراك: listeners تعمل غالباً،
    // لكنّ الاشتراك الجديد يرسل snapshot كاملاً دفعة واحدة.
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) {
            firstResume = false
            return@LifecycleEventEffect
        }
        if (authViewModel.state.value.isSignedIn) homeViewModel.startWatching()
    }

    // أيّ تمرير يُعدّ تفاعلاً — يُلغي الاقتراحات ويُعيد عدّ مؤقّت الخمول.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { homeViewModel.resetIdleTimer() }
    }

    // حزام أمان FCM: إشعار "content_added" أو "section_created" يُجبر
    // الرئيسية على التحديث حتى لو تأخّر push من Firebase.
    var lastNotificationAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(notifications) {
        val newest = notifications.maxByOrNull { it.createdAtMs } ?: return@LaunchedEffect
        if (lastNotificationAt == 0L) {
            lastNotificationAt = newest.createdAtMs
            return@LaunchedEffect
        }
        if (newest.createdAtMs <= lastNotificationAt) return@LaunchedEffect
        lastNotificationAt = newest.createdAtMs
        val type = newest.type.lowercase()
        if (type == "content_added" || type == "section_created") homeViewModel.refresh()
    }

    // رسالة دعوة الضيف لإنشاء حساب (مقنَّنة 2–3 مرّات يوميّاً).
    LaunchedEffect(auth.status, auth.user?.id) {
        if (auth.user != null) {
            showGuestPrompt = false
            return@LaunchedEffect
        }
        if (!GuestAccountPromptService.shouldShow()) return@LaunchedEffect
        GuestAccountPromptService.recordShown()
        showGuestPrompt = true
    }

    // الروابط العميقة nebras://content/{id}: المحتوى الرسميّ من الكاش،
    // وإلّا منشور مجتمع (نفس صيغة الرابط)، وإلّا رسالة توضيح.
    LaunchedEffect(pendingDeepLinkId) {
        val contentId = pendingDeepLinkId?.trim().orEmpty()
        if (contentId.isEmpty()) return@LaunchedEffect
        DeepLinkService.consume()
        val cached = ServiceLocator.contentMetadataCache.getById(contentId)
        if (cached != null) {
            ContentRouter.open(navController, cached)
            return@LaunchedEffect
        }
        val post = runCatching { UgcService.getPost(contentId) }.getOrNull()
        if (post != null) {
            navController.navigate(Routes.ugcPost(post.content.id))
            return@LaunchedEffect
        }
        snackbarHostState.showSnackbar("المحتوى غير متاح حالياً — افتح التطبيق وحدّث الرئيسية.")
    }

    // ملفّ وارد من تطبيق آخر (مشاركة) → شاشة النشر. الحارس يمنع تكرار
    // الفتح ما دام نفس الملفّ معلّقاً.
    var handledSharePath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingSharedFile) {
        val shared = pendingSharedFile ?: return@LaunchedEffect
        if (handledSharePath == shared.path) return@LaunchedEffect
        handledSharePath = shared.path
        navController.navigate(Routes.PUBLISH)
    }

    val openContent: (Content) -> Unit = { item ->
        ContentEngagementService.recordContentOpened(item)
        ContentRouter.open(navController, item)
    }

    Box(Modifier.fillMaxSize()) {
        // RefreshIndicator كحزام أمان — حتى عند انقطاع مؤقّت في الشبكة،
        // بإمكان المستخدم السحب للأسفل لإجبار إعادة الاشتراك فوراً.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    homeViewModel.refresh()
                    delay(800)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item(key = "top_bar") {
                    HomeTopBar(
                        unreadCount = unreadCount,
                        layout = layout,
                        onOpenDrawer = { shell?.openDrawer() },
                        onOpenNotifications = { shell?.openNotifications() },
                        onToggleLayout = { ServiceLocator.sectionsLayoutController.toggle() },
                        onOpenSearch = { shell?.openSearch() },
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    )
                }

                if (showGuestPrompt) {
                    item(key = "guest_prompt") {
                        GuestAccountBanner(
                            onDismiss = { showGuestPrompt = false },
                            onCreateAccount = {
                                showGuestPrompt = false
                                scope.launch { authViewModel.signInWithGoogle(context) }
                            },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }

                homeBody(
                    state = state,
                    layout = layout,
                    feed = homeViewModel.effectiveFeed(),
                    onOpenContent = openContent,
                    onOpenSection = { section ->
                        ContentRouter.openSection(navController, section, state.sections)
                    },
                    onOpenPost = { post -> navController.navigate(Routes.ugcPost(post.content.id)) },
                    onOpenCommunity = { shell?.openCommunity() },
                    onViewAllRail = { viewAllRail = it },
                    onRetry = { homeViewModel.retry() },
                    onRefresh = { homeViewModel.refresh() },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // «اعرض الكلّ» يُعرض فوق الرئيسية بحالة محليّة (لا مسار جديد).
        val rail = viewAllRail
        if (rail != null) {
            RailViewAllScreen(
                rail = rail,
                onBack = { viewAllRail = null },
                onItemTap = openContent,
            )
        }
    }
}

// ── جسم الرئيسية بحسب الحالة ────────────────────────────────────────

/**
 * منطقة المحتوى المرتبطة بالحالة — نفس تسلسل الشروط في نسخة Flutter:
 * تحميل ⟵ خطأ ⟵ رفوف قيد البناء ⟵ فراغ ⟵ المحتوى.
 */
private fun LazyListScope.homeBody(
    state: HomeUiState,
    layout: SectionsLayout,
    feed: List<Content>,
    onOpenContent: (Content) -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    onOpenPost: (UgcPost) -> Unit,
    onOpenCommunity: () -> Unit,
    onViewAllRail: (HomeRail) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    val horizontal = Modifier.padding(horizontal = 24.dp)

    // Loading
    if (state.isLoading && state.sections.isEmpty()) {
        item(key = "skeleton") { HomeSkeletonLoader(horizontal) }
        return
    }

    // Error
    if (state.errorMessage != null && state.sections.isEmpty()) {
        item(key = "error") {
            HomeErrorState(rawError = state.errorMessage.orEmpty(), onRetry = onRetry)
        }
        return
    }

    // 🐞 H3: نعرض skeleton فقط ما دامت الرفوف **قيد البناء**. إن اكتمل
    // البناء وخرجت القائمة فارغة نعرض حالة فراغ بدل skeleton أبديّ.
    if (!state.isLoading && state.sections.isNotEmpty() && state.homeRails.isEmpty()) {
        if (!state.railsBuilt) {
            item(key = "rails_skeleton") { HomeSkeletonLoader(Modifier.padding(24.dp)) }
            return
        }
        item(key = "rails_empty") {
            EmptyStateWidget(
                message = "لا يوجد محتوى بعد. اسحب للأسفل للتحديث.",
                actionLabel = "تحديث",
                onAction = onRefresh,
            )
        }
        return
    }

    if (state.sections.isEmpty() && !state.isLoading) {
        item(key = "empty") {
            EmptyStateWidget(
                message = "لا يوجد محتوى بعد. اسحب للأسفل للتحديث.",
                actionLabel = "تحديث",
                onAction = onRefresh,
            )
        }
        return
    }

    // شريط "غير متصل" — يظهر فقط حين يفشل البثّ الحيّ بينما يوجد محتوى
    // محفوظ سابقاً (نعرضه بدل شاشة خطأ).
    if (state.errorMessage != null) {
        item(key = "offline_banner") { OfflineBanner(horizontal) }
    }

    item(key = "home_title") {
        Text(
            text = "الرئيسية",
            modifier = horizontal.padding(top = 12.dp, bottom = 8.dp),
            style = NebrasTextStyles.heading3,
        )
    }

    // «اكتشف اليوم» 🎁 — عنصر يتغيّر يوميّاً (حتميّ بلا خادم).
    item(key = "daily_pick") {
        DailyPickCard(feed = feed, onOpen = onOpenContent, modifier = horizontal)
    }

    homeRails(
        rails = state.homeRails,
        onItemTap = onOpenContent,
        onViewAll = onViewAllRail,
        itemModifier = horizontal,
    )

    item(key = "community_rail") {
        CommunityHomeRail(
            onOpenPost = onOpenPost,
            onOpenCommunity = onOpenCommunity,
            modifier = horizontal,
        )
    }

    // تصفّح الأقسام — بطاقات الأقسام الرئيسيّة بنمط العرض المحفوظ
    // (قائمة/شبكة) الذي يبدّله زرّ الشريط العلويّ.
    val mainSections = state.sections.filter { it.type == "main_section" }
    if (mainSections.isNotEmpty()) {
        item(key = "sections_title") {
            Text(
                text = "تصفّح الأقسام",
                modifier = horizontal.padding(top = 20.dp, bottom = 8.dp),
                style = NebrasTextStyles.heading3,
            )
        }
        sectionTiles(
            sections = mainSections,
            isGrid = layout == SectionsLayout.GRID,
            icon = Icons.Outlined.FolderOpen,
            onTap = onOpenSection,
            horizontalPadding = 24.dp,
        )
    }

    if (state.isFetchingMore) {
        item(key = "fetching_more") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// ── الشريط العلويّ ──────────────────────────────────────────────────

/**
 * الشريط العلويّ للرئيسية على طراز YouTube:
 *   • زرّ القائمة (الدرج) — الإعدادات وما إليها.
 *   • اسم التطبيق في الوسط.
 *   • جرس الإشعارات مع عدّاد غير المقروء.
 *   • مبدّل نمط عرض الأقسام (قائمة/شبكة).
 *   • أيقونة بحث تفتح شاشة البحث بكامل وظائفها.
 */
@Composable
private fun HomeTopBar(
    unreadCount: Int,
    layout: SectionsLayout,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onToggleLayout: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Rounded.Menu, contentDescription = "القائمة", tint = onSurface)
        }
        Text(
            text = "نبراس",
            modifier = Modifier.weight(1f),
            style = NebrasTextStyles.heading2,
        )
        // جرس الإشعارات مع عدّاد غير المقروء.
        IconButton(onClick = onOpenNotifications) {
            if (unreadCount > 0) {
                BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                    Icon(
                        Icons.Rounded.NotificationsNone,
                        contentDescription = "الإشعارات",
                        tint = onSurface,
                    )
                }
            } else {
                Icon(
                    Icons.Rounded.NotificationsNone,
                    contentDescription = "الإشعارات",
                    tint = onSurface,
                )
            }
        }
        SectionsLayoutToggle(layout = layout, onToggle = onToggleLayout, tint = onSurface)
        // أيقونة البحث — بنفس حجم جرس الإشعارات تماماً.
        IconButton(onClick = onOpenSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "ابحث عن محتوى...",
                tint = onSurface,
            )
        }
    }
}

// ── حالات الشاشة ────────────────────────────────────────────────────

/** شاشة خطأ الشبكة مع زرّ إعادة المحاولة — نقل فرع الخطأ في Flutter. */
@Composable
private fun HomeErrorState(rawError: String, onRetry: () -> Unit) {
    val isPermissionDenied = rawError.contains("Permission denied")
    val userMessage = if (isPermissionDenied) {
        "تعذر الوصول إلى المحتوى. تأكد من تسجيل الدخول."
    } else {
        rawError
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = NebrasColors.primary.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = userMessage.trim().ifEmpty { "عذراً، حدث خطأ في الاتصال بالشبكة." },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            textAlign = TextAlign.Center,
            style = NebrasTextStyles.bodyBold.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = NebrasColors.primary,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("إعادة المحاولة", style = NebrasTextStyles.bodyBold, color = Color.White)
        }
    }
}

/**
 * شريط تنبيه خفيف يُعرض أعلى المحتوى عند انقطاع الاتصال بينما توجد نسخة
 * محفوظة من الأقسام — يطمئن المستخدم بدل إظهار شاشة خطأ فارغة.
 */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHighest)
            .border(1.dp, scheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "أنت غير متصل — تُعرض نسخة محفوظة من المحتوى.",
            modifier = Modifier.weight(1f),
            style = NebrasTextStyles.smallText,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * دعوة الضيف لإنشاء حساب — نظير `MaterialBanner` في Flutter: يدفع المحتوى
 * للأسفل ولا يغطّي أزرار التنقّل، ويبقى حتى يتفاعل المستخدم.
 */
@Composable
private fun GuestAccountBanner(
    onDismiss: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NebrasColors.primary.copy(alpha = 0.12f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.PersonAdd,
                contentDescription = null,
                tint = scheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "أنشئ حساباً ليُخصِّص لك التطبيق تجربته باحتراف: توصيات تناسبك، " +
                    "ومتابعة ما بدأته، ومزامنة مفضّلاتك.",
                modifier = Modifier.weight(1f),
                style = NebrasTextStyles.body,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("لاحقاً") }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onCreateAccount) { Text("إنشاء حساب") }
        }
    }
}

// ── رفّ المجتمع ─────────────────────────────────────────────────────

/**
 * رفّ خفيف من أحدث منشورات المجتمع. لا يخلطها مع المحتوى الرسميّ؛ كلّ
 * بطاقة تحمل شارة المجتمع وتفتح شاشة المنشور الخاصّة.
 */
@Composable
private fun CommunityHomeRail(
    onOpenPost: (UgcPost) -> Unit,
    onOpenCommunity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val postsFlow = remember { UgcService.watchLatest(limit = 12) }
    val posts by postsFlow.collectAsStateWithLifecycle(initialValue = emptyList<UgcPost>())
    if (posts.isEmpty()) return

    Column(modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "جديد المجتمع",
                modifier = Modifier.weight(1f),
                style = NebrasTextStyles.heading3,
            )
            TextButton(onClick = onOpenCommunity) { Text("عرض الكل") }
        }
        LazyRow(
            modifier = Modifier.height(258.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(posts, key = { it.content.id }) { post ->
                UgcPostCard(
                    post = post,
                    onTap = { onOpenPost(post) },
                    horizontal = true,
                )
            }
        }
    }
}
