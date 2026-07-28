package com.nebras.mobile.feature.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.EmptyStateWidget
import com.nebras.mobile.core.widget.FadeInListItem
import com.nebras.mobile.core.widget.SettingsTile
import com.nebras.mobile.feature.content.ContentRouter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * شاشة الإشعارات — نقل `notifications/view/notification_screen.dart`.
 *
 * تحوي: مفتاح تشغيل/إيقاف الإشعارات الفوريّة، شريط تنبيه عند تعطيل إذن
 * النظام، «تعليم الكل مقروءاً»، «مسح الكل»، حذفاً فرديّاً (سحب أو زرّ) مع
 * تراجع، وفتح المحتوى عند النقر.
 */
@Composable
fun NotificationsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val repository = ServiceLocator.notificationsRepository
    val notifications by repository.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // مؤقّت تحديث دوريّ للوقت النسبيّ المعروض ("منذ 5 دقائق") كي لا يتجمّد
    // حتى إعادة التحميل. لا يلمس البيانات — يُعيد الحساب فقط.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // حالة إذن النظام — يُعاد فحصها مع كلّ نبضة دقيقة وبعد نتيجة الطلب.
    var systemNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
            systemNotificationsEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    // إذن POST_NOTIFICATIONS مطلوب من Android 13 فصاعداً.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        systemNotificationsEnabled = granted ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // تفضيل استقبال الإشعارات (اشتراك موضوع البثّ + رمز الجهاز).
    var pushEnabled by remember { mutableStateOf(NotificationsPreferences.isPushEnabled()) }
    var isTogglingPush by remember { mutableStateOf(false) }

    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppBarWidget(
                title = "الإشعارات",
                trailingIcon = if (notifications.isEmpty()) null else Icons.Filled.DoneAll,
                onTrailingIconPressed = {
                    repository.markAllAsRead()
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar("عُلّمت كل الإشعارات كمقروءة")
                    }
                },
                actions = if (notifications.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "مسح الكل",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
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
                            if (!isTogglingPush && pushEnabled != value) {
                                isTogglingPush = true
                                // ائتمان التفضيل أوّلاً: تستجيب الواجهة فوراً
                                // ثمّ يُنفَّذ الاشتراك/الإلغاء في الخلفية.
                                pushEnabled = value
                                scope.launch {
                                    val ok = runCatching {
                                        if (value) {
                                            repository.enablePushNotifications()
                                        } else {
                                            repository.disablePushNotifications()
                                        }
                                    }.isSuccess
                                    // نُرجع الواجهة إلى حالتها السابقة عند
                                    // الفشل حتى لا يظنّ المستخدم أنّ الأمر نُفِّذ.
                                    if (!ok) pushEnabled = !value
                                    isTogglingPush = false
                                }
                            }
                        },
                    )
                },
            )

            if (!systemNotificationsEnabled) {
                PermissionBanner(
                    onEnable = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            systemNotificationsEnabled =
                                NotificationManagerCompat.from(context).areNotificationsEnabled()
                        }
                    },
                )
            }

            Box(Modifier.weight(1f)) {
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateWidget(message = "لا توجد إشعارات")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(notifications, key = { _, it -> it.id }) { index, item ->
                            // منطق حذف واحد يُشارَك بين السحب وزرّ الحذف
                            // الصريح، مع إتاحة التراجع عبر SnackBar. الحذف في
                            // التخزين المحليّ فوريّ، لذا نحتفظ بنسخة كاملة من
                            // الإشعار المحذوف حتى ينتهي شريط التراجع.
                            val deleteWithUndo: () -> Unit = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val deleted = item
                                repository.deleteNotification(deleted.id)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "تم حذف الإشعار",
                                        actionLabel = "تراجع",
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        repository.saveNotification(deleted)
                                    }
                                }
                            }

                            FadeInListItem(index = index) {
                                NotificationTile(
                                    title = item.title,
                                    body = item.body,
                                    createdAtMs = item.createdAtMs,
                                    onDismissed = deleteWithUndo,
                                    onDelete = deleteWithUndo,
                                    isRead = item.isRead,
                                    nowMs = nowMs,
                                    onTap = {
                                        repository.markAsRead(item.id)
                                        val content = resolveNotificationContent(item)
                                        if (content == null) {
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    "المحتوى غير متاح بعد",
                                                )
                                            }
                                        } else {
                                            ContentRouter.open(navController, content)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("مسح الكل") },
            text = { Text("حذف جميع الإشعارات؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        repository.clearAll()
                    },
                ) { Text("مسح الكل") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("إلغاء") }
            },
        )
    }
}

/** شريط تنبيه عند تعطيل الإشعارات في إعدادات النظام. */
@Composable
private fun PermissionBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsOff,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "الإشعارات معطّلة في إعدادات النظام",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 12.sp,
        )
        TextButton(onClick = onEnable) { Text("تفعيل") }
    }
}

/**
 * يحلّ محتوى الإشعار: كاش الميتاداتا المحلّيّ أوّلاً (فوريّ ويحمل النوع
 * والرابط الصحيحين)، ثمّ بناء من حمولة الإشعار نفسها.
 *
 * ⚠️ `type` في الإشعار هو **نوع الإشعار** (`content_added`) لا نوع الوسائط؛
 * نوع الوسائط الحقيقيّ في `mediaType` كي يُفتح المحتوى بمشغّله الصحيح
 * (PDF/صوت/فيديو) لا بمشغّل الفيديو دوماً.
 */
private fun resolveNotificationContent(item: NotificationModel): Content? {
    val contentId = item.contentId.trim()
    if (contentId.isEmpty()) return null

    ServiceLocator.contentMetadataCache.getById(contentId)?.let { return it }

    val sourceUrl = item.sourceUrl?.trim().orEmpty()
    if (sourceUrl.isEmpty()) return null

    val content = Content.fromJson(
        mapOf(
            "id" to contentId,
            "title" to item.title,
            "description" to "",
            "content_type" to (item.mediaType ?: ""),
            "sourceUrl" to sourceUrl,
            "thumbnail" to "",
            "subsection" to (item.subSectionId ?: ""),
            "secondary_subsection" to (item.secondarySectionId ?: ""),
        ),
    )
    // حارس: لا نُرجع كائناً بلا مصدر صالح (يطابق منطق ContentRouter).
    return if (content.sourceUrl.isNullOrBlank()) null else content
}
