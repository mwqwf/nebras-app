package com.nebras.mobile.feature.content

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.service.PersonalizationResetService
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.theme.ThemeMode
import com.nebras.mobile.core.widget.BottomSheetHandle
import com.nebras.mobile.core.widget.SettingsTile
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.notifications.NotificationsPreferences
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch

/**
 * نقل `core/widgets/botttom_sheet_widget.dart` — ورقة «الإعدادات» السفليّة:
 * المظهر، الإشعارات الفوريّة، حول، نسيان اهتماماتي، وتسجيل الخروج.
 *
 * [ContentOptionsSheet] هو الغلاف المنبثق (`showModalBottomSheet`)، بينما
 * [SettingsBottomSheetWidget] هو المحتوى نفسه ليُعاد استعماله كلوحة جانبيّة
 * (`asDrawerPanel = true`) تماماً كما في نسخة Flutter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentOptionsSheet(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // ارتفاع الورقة 92% من ارتفاع الشاشة (نفس نسبة Flutter) — نحسبه صراحةً
    // كي يبقى `weight(1f)` داخل المحتوى ضمن قيود مقيَّدة الارتفاع.
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        // المقبض مرسوم داخل المحتوى (BuildHandelWidget) كما في Flutter.
        dragHandle = null,
    ) {
        SettingsBottomSheetWidget(
            navController = navController,
            authViewModel = authViewModel,
            homeViewModel = homeViewModel,
            onClose = onDismissRequest,
            modifier = Modifier.height(sheetHeight),
        )
    }
}

/** محتوى ورقة الإعدادات — نقل `SettingsBottomSheetWidget`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheetWidget(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    asDrawerPanel: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val themeMode by ServiceLocator.themeController.themeMode.collectAsStateWithLifecycle()

    var pushEnabled by remember { mutableStateOf(NotificationsPreferences.isPushEnabled()) }
    var isTogglingPush by remember { mutableStateOf(false) }
    var themePickerVisible by remember { mutableStateOf(false) }
    var forgetDialogVisible by remember { mutableStateOf(false) }
    var signOutDialogVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = if (asDrawerPanel) {
                    RoundedCornerShape(0.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!asDrawerPanel) {
            Spacer(Modifier.height(8.dp))
            BottomSheetHandle()
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Text("الإعدادات", style = NebrasTextStyles.heading3)
        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            item(key = "theme") {
                SettingsTile(
                    icon = Icons.Rounded.DarkMode,
                    title = "المظهر",
                    onTap = { themePickerVisible = true },
                    trailing = {
                        Text(themeLabel(themeMode), style = NebrasTextStyles.greysmallText)
                    },
                )
            }
            // مفتاح التحكّم المركزيّ بإشعارات FCM. إيقافه يُلغي الاشتراك من
            // مواضيع البثّ ويحذف رمز الجهاز كي لا تصل أيّ إشعارات.
            item(key = "push") {
                SettingsTile(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "الإشعارات الفوريّة",
                    trailing = {
                        Switch(
                            checked = pushEnabled,
                            enabled = !isTogglingPush,
                            onCheckedChange = { value ->
                                isTogglingPush = true
                                pushEnabled = value
                                scope.launch {
                                    val repo = ServiceLocator.notificationsRepository
                                    runCatching {
                                        if (value) {
                                            repo.enablePushNotifications()
                                        } else {
                                            repo.disablePushNotifications()
                                        }
                                    }
                                    pushEnabled = NotificationsPreferences.isPushEnabled()
                                    isTogglingPush = false
                                }
                            },
                        )
                    },
                )
            }
            item(key = "about") {
                SettingsTile(
                    icon = Icons.Outlined.Info,
                    title = "حول",
                    onTap = { navController.navigate(Routes.ABOUT) },
                )
            }
            item(key = "forget") {
                SettingsTile(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "نسيان اهتماماتي",
                    subtitle = "يمسح التوصيات والبحث المحلي فقط",
                    onTap = { forgetDialogVisible = true },
                )
            }
            // فاصل بصري ثم خيار تسجيل الخروج. نعرضه فقط حين يكون المستخدم
            // مسجّلاً (الحال الطبيعي عند وصوله لهذه الشاشة).
            if (auth.isSignedIn) {
                item(key = "divider") {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                }
                item(key = "signout") {
                    SettingsTile(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "تسجيل الخروج",
                        onTap = { signOutDialogVisible = true },
                    )
                }
            }
        }
    }

    if (themePickerVisible) {
        ModalBottomSheet(
            onDismissRequest = { themePickerVisible = false },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = "المظهر",
                    modifier = Modifier.padding(16.dp),
                    style = NebrasTextStyles.heading3,
                )
                for (mode in listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)) {
                    ListItem(
                        modifier = Modifier.clickable {
                            ServiceLocator.themeController.setThemeMode(mode)
                            themePickerVisible = false
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(themeLabel(mode)) },
                        trailingContent = {
                            if (themeMode == mode) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = NebrasColors.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (forgetDialogVisible) {
        AlertDialog(
            onDismissRequest = { forgetDialogVisible = false },
            title = { Text("نسيان اهتماماتي", style = NebrasTextStyles.heading3) },
            text = {
                Text(
                    "سيتم حذف ملفّ الاهتمامات وسجلّ السلوك و«تابع التصفّح» من هذا " +
                        "الجهاز. العدّادات العامّة على الخادم لا تتأثّر. هل تريد المتابعة؟",
                    style = NebrasTextStyles.body,
                )
            },
            dismissButton = {
                TextButton(onClick = { forgetDialogVisible = false }) { Text("إلغاء") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgetDialogVisible = false
                        PersonalizationResetService.forgetAllLocalSignals()
                        homeViewModel.refreshHomeRails()
                        Toast.makeText(
                            context,
                            "تم مسح بيانات التخصيص المحليّة.",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                ) {
                    Text("تأكيد")
                }
            },
        )
    }

    if (signOutDialogVisible) {
        AlertDialog(
            onDismissRequest = { signOutDialogVisible = false },
            title = { Text("تسجيل الخروج", style = NebrasTextStyles.heading3) },
            text = {
                Text(
                    "هل تريد تسجيل الخروج من حسابك في نبراس؟",
                    style = NebrasTextStyles.body,
                )
            },
            dismissButton = {
                TextButton(onClick = { signOutDialogVisible = false }) { Text("إلغاء") }
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = NebrasColors.error,
                    ),
                    onClick = {
                        signOutDialogVisible = false
                        authViewModel.signOut()
                        // AuthGate يلتقط تغيّر الحالة ويعيد المستخدم لشاشة
                        // الترحيب، لكننا نُغلق ورقة الإعدادات لتجنّب التراكب.
                        onClose()
                    },
                ) {
                    Text("تأكيد")
                }
            },
        )
    }
}

/** نقل `_themeLabel`. */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "تلقائي (النظام)"
    ThemeMode.DARK -> "داكن"
    ThemeMode.LIGHT -> "فاتح"
}
