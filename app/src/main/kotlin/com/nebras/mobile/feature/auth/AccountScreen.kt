package com.nebras.mobile.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.service.PersonalizationResetService
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.theme.ThemeMode
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.SettingsTile
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * شاشة «حسابي» — تعرض كلّ بيانات المستخدم داخل التطبيق نفسه (دون أيّ موقع
 * خارجيّ)، وتتيح ضبط المظهر و«نسيان اهتماماتي» وتسجيل الخروج و**حذف الحساب
 * نهائيّاً** باحترافيّة.
 *
 * التطبيق لا يخزّن بيانات شخصيّة في السحابة؛ المفضّلة وسجلّ المشاهدة محليّة
 * على الجهاز. لذا حذف الحساب = حذف هويّة Firebase + مسح كلّ البيانات
 * المحليّة، ويتمّ بالكامل داخل التطبيق.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel,
) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val themeMode by ServiceLocator.themeController.themeMode.collectAsStateWithLifecycle()
    val user = auth.user
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmDeleteVisible by remember { mutableStateOf(false) }
    var themePickerVisible by remember { mutableStateOf(false) }
    var forgetDialogVisible by remember { mutableStateOf(false) }

    /** مقابل `Navigator.popUntil((r) => r.isFirst)` في Flutter. */
    val popToRoot = {
        navController.popBackStack(navController.graph.startDestinationId, false)
        Unit
    }

    Scaffold(
        containerColor = scheme.surface,
        topBar = {
            AppBarWidget(title = "حسابي", onBack = { navController.popBackStack() })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            if (user == null) {
                GuestBody(
                    onSignIn = { scope.launch { authViewModel.signInWithGoogle(context) } },
                    onExitGuest = { authViewModel.exitGuest() },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    item(key = "header") {
                        AccountHeader(
                            name = user.displayName,
                            email = user.email,
                            photoUrl = user.photoUrl,
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("بيانات الحساب", style = NebrasTextStyles.heading3)
                        Spacer(Modifier.height(12.dp))
                    }
                    item(key = "name") { InfoRow(label = "الاسم", value = user.displayName) }
                    item(key = "email") {
                        InfoRow(label = "البريد الإلكتروني", value = user.email)
                    }
                    item(key = "uid") { InfoRow(label = "معرّف الحساب", value = user.id) }
                    item(key = "signedInAt") {
                        InfoRow(
                            label = "تاريخ الدخول",
                            value = formatDate(user.signedInAtMs),
                        )
                    }
                    item(key = "privacy") {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceContainerHighest)
                                .padding(14.dp),
                        ) {
                            Text(
                                text = "يخزّن التطبيق مفضّلتك وسجلّ مشاهدتك على جهازك فقط. " +
                                    "لا نحفظ بيانات شخصيّة على خوادمنا عدا بلاغات المحتوى " +
                                    "التي ترسلها (إن أرسلت)، وتُحذف عند حذف حسابك. حذف " +
                                    "الحساب يزيل هويّتك ويمسح بياناتك المحليّة نهائيّاً.",
                                style = NebrasTextStyles.smallText.copy(
                                    color = scheme.onSurfaceVariant,
                                    lineHeight = 21.sp, // height: 1.5 × 14sp
                                ),
                            )
                        }
                    }
                    // ── تفضيلات محليّة (المظهر + نسيان الاهتمامات) ──────
                    item(key = "theme") {
                        Spacer(Modifier.height(16.dp))
                        SettingsTile(
                            icon = Icons.Rounded.DarkMode,
                            title = "المظهر",
                            onTap = { themePickerVisible = true },
                            trailing = {
                                Text(
                                    themeLabel(themeMode),
                                    style = NebrasTextStyles.greysmallText,
                                )
                            },
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
                    item(key = "actions") {
                        Spacer(Modifier.height(28.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    authViewModel.signOut()
                                    popToRoot()
                                }
                            },
                            enabled = !auth.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("تسجيل الخروج")
                        }
                        Spacer(Modifier.height(12.dp))
                        // حذف الحساب — إجراء مدمّر، بلون تحذيريّ + تأكيد صريح.
                        Button(
                            onClick = { confirmDeleteVisible = true },
                            enabled = !auth.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NebrasColors.error,
                                contentColor = Color.White,
                            ),
                        ) {
                            if (auth.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.DeleteForever,
                                    contentDescription = null,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("حذف الحساب")
                        }
                    }
                }
            }
        }
    }

    // ── حوار تأكيد حذف الحساب ──────────────────────────────────────
    if (confirmDeleteVisible) {
        AlertDialog(
            onDismissRequest = { confirmDeleteVisible = false },
            title = { Text("حذف الحساب نهائيّاً؟") },
            text = {
                Text(
                    "سيُحذف حسابك وتُمسح كل بياناتك المحليّة (المفضّلة، سجلّ المشاهدة، " +
                        "الاهتمامات) نهائيّاً ولا يمكن التراجع.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteVisible = false }) { Text("إلغاء") }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NebrasColors.error,
                        contentColor = Color.White,
                    ),
                    onClick = {
                        confirmDeleteVisible = false
                        scope.launch {
                            // نمسح المفضّلة المحليّة أوّلاً (كامل بيانات
                            // المستخدم على الجهاز).
                            runCatching { ServiceLocator.savedRepository.clearAll() }
                            val ok = authViewModel.deleteAccount(context)
                            if (ok) {
                                snackbarHostState.showSnackbar("تم حذف الحساب")
                                popToRoot()
                            } else {
                                snackbarHostState.showSnackbar(
                                    authViewModel.state.value.lastError
                                        ?: "تعذّر حذف الحساب. حاول مجدّداً.",
                                )
                            }
                        }
                    },
                ) {
                    Text("حذف الحساب")
                }
            },
        )
    }

    // ── مُنتقي المظهر (تلقائيّ/فاتح/داكن) ──────────────────────────
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

    // ── حوار «نسيان اهتماماتي» ─────────────────────────────────────
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
                        scope.launch {
                            snackbarHostState.showSnackbar("تم مسح بيانات التخصيص المحليّة.")
                        }
                    },
                ) {
                    Text("تأكيد")
                }
            },
        )
    }
}

// ── مكوّنات الشاشة ──────────────────────────────────────────────────

/** رأس الحساب: الصورة (أو الحرف الأوّل) + الاسم + البريد. */
@Composable
private fun AccountHeader(name: String, email: String, photoUrl: String?) {
    val scheme = MaterialTheme.colorScheme
    val trimmedName = name.trim()
    val initial = if (trimmedName.isNotEmpty()) {
        trimmedName.substring(0, 1).uppercase()
    } else {
        "؟"
    }
    val url = photoUrl?.trim().orEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp) // radius 34
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            // الحرف الأوّل يبقى تحت الصورة: عند فشل تحميل صورة Google لا
            // يُرسم شيء فوقه فيظهر بديلاً بصمت (نفس `onBackgroundImageError`).
            Text(
                text = initial,
                style = NebrasTextStyles.heading2.copy(color = scheme.primary),
            )
            if (url.isNotEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = NebrasTextStyles.heading3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = email,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = NebrasTextStyles.greysmallText,
            )
        }
    }
}

/** سطر «الوسم: القيمة» — نقل `_InfoRow`. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            style = NebrasTextStyles.bodyBold,
        )
        Text(
            text = value.ifEmpty { "—" },
            modifier = Modifier.weight(1f),
            style = NebrasTextStyles.body,
        )
    }
}

/** جسم الشاشة للضيف — نقل `_GuestBody`. */
@Composable
private fun GuestBody(onSignIn: () -> Unit, onExitGuest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.NoAccounts,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "أنت تتصفّح كضيف بلا حساب. أنشئ حساباً للحصول على تجربة " +
                    "مخصّصة ومزامنة.",
                textAlign = TextAlign.Center,
                style = NebrasTextStyles.body,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onSignIn) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إنشاء حساب")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onExitGuest) {
                Text("العودة إلى شاشة الترحيب")
            }
        }
    }
}

// ── مساعدات ─────────────────────────────────────────────────────────

/** نقل `_formatDate` — `yyyy-MM-dd` بأصفار بادئة. */
private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "${cal.get(Calendar.YEAR)}-$month-$day"
}

/** أسماء أوضاع المظهر الثلاثة — نفس نصوص ورقة الإعدادات. */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "تلقائي (النظام)"
    ThemeMode.DARK -> "داكن"
    ThemeMode.LIGHT -> "فاتح"
}
