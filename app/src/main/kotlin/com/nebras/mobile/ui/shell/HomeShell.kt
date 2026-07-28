package com.nebras.mobile.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Groups2
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.service.PersonalizationResetService
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.theme.ThemeMode
import com.nebras.mobile.core.widget.SettingsTile
import com.nebras.mobile.feature.auth.AccountScreen
import com.nebras.mobile.feature.auth.AuthStatus
import com.nebras.mobile.feature.auth.AuthUiState
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.auth.WelcomeScreen
import com.nebras.mobile.feature.community.CommunityScreen
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.home.view.HomeScreen
import com.nebras.mobile.feature.notifications.NotificationsPreferences
import com.nebras.mobile.feature.notifications.NotificationsScreen
import com.nebras.mobile.feature.saved.SavedScreen
import com.nebras.mobile.feature.search.SearchScreen
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch

/** الوجهات الداخليّة التي كانت `Navigator.push` فوق `HomeShell` في Flutter. */
enum class ShellOverlay { ACCOUNT, NOTIFICATIONS, SEARCH, COMMUNITY }

/**
 * مقبض الهيكل — بديل `Scaffold.of(context).openDrawer()` و`Navigator.push`
 * في Flutter: الشاشات الداخليّة (الرئيسية مثلاً) تصل إليه من الشجرة عبر
 * [LocalNebrasShell] فتفتح الدرج أو إحدى الوجهات الداخليّة بلا مسارات جديدة.
 */
class NebrasShellController internal constructor(
    private val onOpenDrawer: () -> Unit,
    private val onCloseDrawer: () -> Unit,
    private val onOpenOverlay: (ShellOverlay) -> Unit,
    private val onCloseOverlay: () -> Unit,
) {
    fun openDrawer() = onOpenDrawer()
    fun closeDrawer() = onCloseDrawer()
    fun openAccount() = onOpenOverlay(ShellOverlay.ACCOUNT)
    fun openNotifications() = onOpenOverlay(ShellOverlay.NOTIFICATIONS)
    fun openSearch() = onOpenOverlay(ShellOverlay.SEARCH)
    fun openCommunity() = onOpenOverlay(ShellOverlay.COMMUNITY)
    fun closeOverlay() = onCloseOverlay()
}

/** المقبض المتاح لكلّ ما تحت `HomeShell` (null خارج الهيكل). */
val LocalNebrasShell = staticCompositionLocalOf<NebrasShellController?> { null }

/**
 * الهيكل الرئيسيّ — نقل `core/widgets/home_shell.dart` مع بوّابة الجلسة
 * (`auth_gate.dart`) التي كانت تسبقه في شجرة Flutter.
 *
 * `NavHost` ينتقل من السبلاش إلى هذه الوجهة مباشرةً، لذلك يبقى قرار عرض
 * شاشة الترحيب أو الهيكل هنا — وهو نفس تقسيم Flutter (`AuthGate` تختار بين
 * `WelcomeScreen` و`HomeShell`).
 */
@Composable
fun HomeShell(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()

    Crossfade(
        targetState = auth.status,
        animationSpec = tween(280),
        label = "authGate",
    ) { status ->
        when (status) {
            AuthStatus.UNKNOWN -> AuthLoading()
            AuthStatus.SIGNED_OUT -> WelcomeScreen(authViewModel = authViewModel)
            AuthStatus.SIGNED_IN -> ShellBody(
                navController = navController,
                authViewModel = authViewModel,
                homeViewModel = homeViewModel,
                auth = auth,
            )
        }
    }
}

/** شاشة لودر خفيفة أثناء تحميل الجلسة — نقل `_AuthLoading`. */
@Composable
private fun AuthLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.4.dp,
            color = NebrasColors.primary,
        )
    }
}

@Composable
private fun ShellBody(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    auth: AuthUiState,
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<ShellOverlay?>(null) }

    // تهيئة كسولة آمنة — نظير `addPostFrameCallback` في Flutter: تحميل
    // الإشعارات والمحفوظات بعد بناء الشجرة لا قبله.
    LaunchedEffect(Unit) {
        ServiceLocator.notificationsRepository
        ServiceLocator.savedRepository.loadSaved()
    }

    val controller = remember(drawerState, scope) {
        NebrasShellController(
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onCloseDrawer = { scope.launch { drawerState.close() } },
            onOpenOverlay = { target ->
                scope.launch { drawerState.close() }
                overlay = target
            },
            onCloseOverlay = { overlay = null },
        )
    }

    // حارس: بعض الشاشات الداخليّة تظنّ نفسها وجهةً مستقلّة فتستدعي
    // `popBackStack()`؛ لو أفرغت المكدّس نُعيد بناء الهيكل بدل شاشة بيضاء.
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry, overlay) {
        if (overlay != null && backStackEntry == null) {
            overlay = null
            navController.navigate(Routes.SHELL) { launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalNebrasShell provides controller) {
        Box(Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = overlay == null,
                drawerContent = {
                    NebrasDrawer(
                        auth = auth,
                        authViewModel = authViewModel,
                        homeViewModel = homeViewModel,
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        onOpenOverlay = { target ->
                            scope.launch { drawerState.close() }
                            overlay = target
                        },
                    )
                },
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    // ✍️ زرّ النشر — بارز دائماً في كلّ التبويبات، بلون التطبيق
                    // الأساسيّ الصريح ونصّ أبيض وظلّ قويّ فلا يذوب في أيّ خلفية.
                    floatingActionButton = {
                        PublishFab(onPressed = { navController.navigate(Routes.PUBLISH) })
                    },
                    bottomBar = {
                        ShellBottomBar(
                            currentIndex = selectedTab,
                            onNavTap = { selectedTab = it },
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        // التبويبات الثلاثة: الرئيسية + المحفوظات + المجتمع.
                        // البحث و«لك» اندمجا داخل الرئيسية، والإعدادات في الدرج.
                        when (selectedTab) {
                            0 -> HomeScreen(
                                navController = navController,
                                homeViewModel = homeViewModel,
                                authViewModel = authViewModel,
                            )
                            1 -> SavedScreen(navController = navController)
                            else -> CommunityScreen(
                                navController = navController,
                                authViewModel = authViewModel,
                                showPublishButton = false,
                            )
                        }
                    }
                }
            }

            // الوجهات الداخليّة تُعرض فوق الهيكل بحالة محليّة بدل مسارات جديدة.
            overlay?.let { current ->
                BackHandler { overlay = null }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (current) {
                        ShellOverlay.ACCOUNT -> AccountScreen(
                            navController = navController,
                            authViewModel = authViewModel,
                        )
                        ShellOverlay.NOTIFICATIONS -> NotificationsScreen(
                            navController = navController,
                        )
                        ShellOverlay.SEARCH -> SearchScreen(
                            navController = navController,
                            homeViewModel = homeViewModel,
                        )
                        ShellOverlay.COMMUNITY -> CommunityScreen(
                            navController = navController,
                            authViewModel = authViewModel,
                            showPublishButton = true,
                        )
                    }
                }
            }
        }
    }
}

// ── زرّ النشر والشريط السفليّ ────────────────────────────────────────

@Composable
private fun PublishFab(onPressed: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    FloatingActionButton(
        onClick = onPressed,
        shape = shape,
        containerColor = NebrasColors.primary,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
        modifier = Modifier.border(
            width = 1.6.dp,
            color = Color.White.copy(alpha = 0.85f),
            shape = shape,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "انشر",
                fontWeight = FontWeight.W800,
                fontSize = 15.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ShellBottomBar(currentIndex: Int, onNavTap: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentIndex == 0,
            onClick = { onNavTap(0) },
            icon = {
                Icon(
                    if (currentIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = null,
                )
            },
            label = { Text("الرئيسية") },
        )
        NavigationBarItem(
            selected = currentIndex == 1,
            onClick = { onNavTap(1) },
            icon = {
                Icon(
                    if (currentIndex == 1) {
                        Icons.Filled.Bookmark
                    } else {
                        Icons.Filled.BookmarkBorder
                    },
                    contentDescription = null,
                )
            },
            label = { Text("المحفوظات") },
        )
        NavigationBarItem(
            selected = currentIndex == 2,
            onClick = { onNavTap(2) },
            icon = {
                Icon(
                    if (currentIndex == 2) Icons.Filled.Groups2 else Icons.Outlined.Groups2,
                    contentDescription = null,
                )
            },
            label = { Text("المجتمع") },
        )
    }
}

// ── الدرج ───────────────────────────────────────────────────────────

@Composable
private fun NebrasDrawer(
    auth: AuthUiState,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onCloseDrawer: () -> Unit,
    onOpenOverlay: (ShellOverlay) -> Unit,
) {
    var settingsExpanded by remember { mutableStateOf(false) }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // رأس الحساب — نقرة تفتح شاشة الحساب بعد إغلاق الدرج.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenOverlay(ShellOverlay.ACCOUNT) }
                    .padding(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    DrawerAccountHeader(auth = auth)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            ListItem(
                modifier = Modifier.clickable { onOpenOverlay(ShellOverlay.COMMUNITY) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Outlined.Groups2, contentDescription = null) },
                headlineContent = { Text("مجتمع نبراس") },
            )

            // ملاحظة: اختصار «الإشعارات» نُقل إلى جرس في الشريط العلويّ
            // للرئيسية (طراز YouTube). الشاشة نفسها لم تتغيّر وتُفتح من الجرس.

            // «الإعدادات» قائمة قابلة للطيّ تحوي نفس لوحة الإعدادات الأصليّة.
            ListItem(
                modifier = Modifier.clickable { settingsExpanded = !settingsExpanded },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                headlineContent = { Text("الإعدادات") },
                trailingContent = {
                    Icon(
                        if (settingsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                },
            )
            AnimatedVisibility(visible = settingsExpanded) {
                Box(Modifier.height(560.dp)) {
                    SettingsPanel(
                        auth = auth,
                        authViewModel = authViewModel,
                        homeViewModel = homeViewModel,
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                        onCloseDrawer = onCloseDrawer,
                    )
                }
            }
        }
    }
}

/**
 * رأس الدرج الذي يوضّح **بجلاء** هويّة الجلسة الحاليّة:
 *   • مستخدم حقيقيّ (Google) → صورته (أو أوّل حرف من اسمه) + الاسم + البريد
 *     مع شارة تحقّق صغيرة.
 *   • ضيف → أيقونة ضيف مميّزة + كلمة «ضيف» + «تصفّح بدون حساب».
 * المرجع الوحيد للهويّة هو حالة المصادقة (الضيف = signedIn مع user == null).
 */
@Composable
private fun DrawerAccountHeader(auth: AuthUiState) {
    val scheme = MaterialTheme.colorScheme
    val user = auth.user
    val isGuest = user == null

    val displayName: String = if (user == null) {
        "ضيف"
    } else if (user.displayName.trim().isNotEmpty()) {
        user.displayName.trim()
    } else if (user.email.trim().isNotEmpty()) {
        user.email.trim()
    } else {
        "ضيف"
    }

    val subtitle: String = if (user == null) "تصفّح بدون حساب" else user.email.trim()

    Row(verticalAlignment = Alignment.CenterVertically) {
        AccountAvatar(
            photoUrl = user?.photoUrl,
            fallbackInitial = if (user == null) {
                null
            } else {
                displayName.firstOrNull()?.toString()
            },
            isGuest = isGuest,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (!isGuest) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // شارة الحالة: تميّز الضيف بصرياً عن المستخدم الحقيقيّ.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        (if (isGuest) scheme.tertiary else scheme.primary).copy(alpha = 0.12f),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isGuest) Icons.Rounded.NoAccounts else Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (isGuest) scheme.tertiary else scheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isGuest) "وضع الضيف" else "مسجّل الدخول",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isGuest) scheme.tertiary else scheme.primary,
                        fontWeight = FontWeight.W600,
                    ),
                )
            }
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = scheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/**
 * أفاتار الحساب: صورة المستخدم إن وُجدت، وإلّا أوّل حرف من اسمه، وإلّا
 * أيقونة ضيف مميّزة بإطار ملوّن كي يدرك المستخدم فوراً أنّه يتصفّح كضيف.
 */
@Composable
private fun AccountAvatar(
    photoUrl: String?,
    fallbackInitial: String?,
    isGuest: Boolean,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val accent = if (isGuest) scheme.tertiary else scheme.primary
    val radius = 30.dp
    val diameter = radius * 2

    var imageFailed by remember(photoUrl) { mutableStateOf(false) }
    val hasPhoto = !isGuest && !photoUrl.isNullOrBlank() && !imageFailed

    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .border(width = 2.dp, color = accent.copy(alpha = 0.5f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPhoto) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photoUrl!!.trim()).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .size(diameter)
                    .clip(CircleShape),
            )
        } else if (!isGuest && !fallbackInitial.isNullOrBlank()) {
            Text(
                text = fallbackInitial.trim().uppercase(),
                fontSize = 24.sp, // radius * 0.8 = 24
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        } else {
            Icon(
                if (isGuest) Icons.Outlined.PersonOff else Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(radius),
                tint = accent,
            )
        }
    }
}

// ── لوحة الإعدادات (نقل `botttom_sheet_widget.dart`) ─────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    auth: AuthUiState,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    onCloseDrawer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val themeController = ServiceLocator.themeController
    val themeMode by themeController.themeMode.collectAsStateWithLifecycle()

    var showThemePicker by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    var pushEnabled by remember { mutableStateOf(NotificationsPreferences.isPushEnabled()) }
    var isTogglingPush by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text("الإعدادات", style = NebrasTextStyles.heading3)
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            SettingsTile(
                icon = Icons.Rounded.DarkMode,
                title = "المظهر",
                onTap = { showThemePicker = true },
                trailing = {
                    Text(themeLabel(themeMode), style = NebrasTextStyles.greysmallText)
                },
            )
            // مفتاح التحكّم المركزيّ بإشعارات FCM. إيقافه يُلغي الاشتراك من
            // مواضيع البثّ ويحذف رمز الجهاز كي لا تصل أيّ إشعارات.
            SettingsTile(
                icon = Icons.Outlined.NotificationsActive,
                title = "الإشعارات الفوريّة",
                trailing = {
                    Switch(
                        checked = pushEnabled,
                        enabled = !isTogglingPush,
                        onCheckedChange = { value ->
                            pushEnabled = value
                            isTogglingPush = true
                            scope.launch {
                                val repo = ServiceLocator.notificationsRepository
                                if (value) {
                                    repo.enablePushNotifications()
                                } else {
                                    repo.disablePushNotifications()
                                }
                                pushEnabled = NotificationsPreferences.isPushEnabled()
                                isTogglingPush = false
                            }
                        },
                    )
                },
            )
            SettingsTile(
                icon = Icons.Outlined.Info,
                title = "حول",
                onTap = {
                    onCloseDrawer()
                    navController.navigate(Routes.ABOUT)
                },
            )
            SettingsTile(
                icon = Icons.Outlined.PrivacyTip,
                title = "نسيان اهتماماتي",
                subtitle = "يمسح التوصيات والبحث المحلي فقط",
                onTap = { showForgetDialog = true },
            )
            // فاصل بصري ثم خيار تسجيل الخروج — يُعرض فقط حين يكون المستخدم
            // مسجّلاً، ونتركه أحمر ليُميّز طبيعته بوضوح.
            if (auth.isSignedIn) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SettingsTile(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "تسجيل الخروج",
                    onTap = { showSignOutDialog = true },
                )
            }
        }
    }

    if (showThemePicker) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showThemePicker = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(16.dp)) {
                    Text("المظهر", style = NebrasTextStyles.heading3)
                }
                listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                    ListItem(
                        modifier = Modifier.clickable {
                            themeController.setThemeMode(mode)
                            showThemePicker = false
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(themeLabel(mode)) },
                        trailingContent = {
                            if (themeMode == mode) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = NebrasColors.primary,
                                )
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text("نسيان اهتماماتي", style = NebrasTextStyles.heading3) },
            text = {
                Text(
                    text = "سيتم حذف ملفّ الاهتمامات وسجلّ السلوك و«تابع التصفّح» من " +
                        "هذا الجهاز. العدّادات العامّة على الخادم لا تتأثّر. هل تريد المتابعة؟",
                    style = NebrasTextStyles.body,
                )
            },
            dismissButton = {
                TextButton(onClick = { showForgetDialog = false }) { Text("إلغاء") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgetDialog = false
                        PersonalizationResetService.forgetAllLocalSignals()
                        homeViewModel.refreshHomeRails()
                        scope.launch {
                            snackbarHostState.showSnackbar("تم مسح بيانات التخصيص المحليّة.")
                        }
                    },
                ) { Text("تأكيد") }
            },
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("تسجيل الخروج", style = NebrasTextStyles.heading3) },
            text = {
                Text(
                    text = "هل تريد تسجيل الخروج من حسابك في نبراس؟",
                    style = NebrasTextStyles.body,
                )
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("إلغاء") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        authViewModel.signOut()
                        // بوّابة الجلسة تلتقط تغيّر الحالة تلقائيًّا وتعيد
                        // المستخدم لشاشة الترحيب؛ نُغلق الدرج تجنّباً للتراكب.
                        onCloseDrawer()
                    },
                ) {
                    Text("تأكيد", color = NebrasColors.error)
                }
            },
        )
    }
}

/** تسمية وضع المظهر — نفس نصوص `_themeLabel` في Flutter. */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "تلقائي (النظام)"
    ThemeMode.DARK -> "داكن"
    ThemeMode.LIGHT -> "فاتح"
}
