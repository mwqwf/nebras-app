package com.nebras.mobile.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.core.util.ar
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.EmptyStateWidget
import com.nebras.mobile.core.widget.FadeInListItem
import com.nebras.mobile.core.widget.SavedSkeletonLoader
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.feature.download.MediaDownloadController
import com.nebras.mobile.feature.download.MediaDownloadStatus
import kotlinx.coroutines.launch
import java.util.Date

/**
 * شاشة المحفوظات — نقل `saved/view/saved_view.dart`.
 *
 * فصل تامّ بين مفهومين: «المحفوظات» = المفضّلة، و«التحميلات» = ما هو على
 * الجهاز فعلاً. مفتاح علويّ يبدّل بينهما، وتبويبات تصفية بحسب النوع.
 */
@Composable
fun SavedScreen(navController: NavHostController) {
    val savedRepository = ServiceLocator.savedRepository
    val savedItems by savedRepository.state.collectAsStateWithLifecycle()
    val isLoading by savedRepository.isLoading.collectAsStateWithLifecycle()
    val downloads by MediaDownloadController.state.collectAsStateWithLifecycle()

    // إعادة بناء القائمة فور إخفاء محتوى مُبلَّغ عنه (التصفية تعتمد على
    // HiddenContentService) — نظير `context.watch<HiddenContentService>()`.
    val hiddenIds by HiddenContentService.hiddenIds.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf<SavedContentType?>(null) }
    // false = المحفوظات (الكل) ، true = التنزيلات فقط
    var downloadedOnly by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // تحميل المحفوظات عند فتح الشاشة.
    LaunchedEffect(Unit) { savedRepository.loadSaved() }

    // فصل تامّ: «التنزيلات» مصدرها مزوّد التنزيل (ما هو على الجهاز فعلاً)،
    // و«المحفوظات» المفضّلة فقط (نستبعد المنزَّل كي لا يتكرّر العنصر بين
    // القائمتين).
    val items: List<SavedItem> = if (downloadedOnly) {
        val dls = downloads.values
            .filter { it.downloadStatus == MediaDownloadStatus.COMPLETED }
            .map {
                SavedItem(
                    id = it.contentId,
                    title = it.title.ifEmpty { it.contentId },
                    imageUrl = it.imageUrl,
                    contentType = it.contentType,
                    savedAtMs = System.currentTimeMillis(),
                )
            }
        if (selectedFilter != null) dls.filter { it.type == selectedFilter } else dls
    } else {
        savedItems
            .filter { selectedFilter == null || it.type == selectedFilter }
            .filter { !hiddenIds.contains(it.id) }
            .filter { MediaDownloadController.getLocalPath(it.id) == null }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBarWidget(title = "المحفوظات") },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LibrarySegments(
                downloadedOnly = downloadedOnly,
                onSelect = { downloadedOnly = it },
            )

            // تبويبات التصفية
            SavedFilterTabs(
                selectedFilter = selectedFilter,
                onSelect = { selectedFilter = it },
            )

            Spacer(Modifier.height(8.dp))

            DownloadsTotal()

            // المحتوى
            Box(Modifier.weight(1f)) {
                when {
                    isLoading -> SavedSkeletonLoader()

                    items.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateWidget(
                            message = (
                                if (downloadedOnly) {
                                    "No downloads yet"
                                } else {
                                    "No saved content yet"
                                }
                                ).ar,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                            FadeInListItem(index = index) {
                                SwipeableSavedRow(
                                    item = item,
                                    isDownloaded = MediaDownloadController
                                        .getLocalPath(item.id) != null,
                                    downloadedSize = MediaDownloadController
                                        .humanSize(MediaDownloadController.sizeOf(item.id)),
                                    onTap = {
                                        openSavedContent(navController, item)
                                    },
                                    onRemove = {
                                        if (downloadedOnly) {
                                            MediaDownloadController.delete(item.id)
                                        } else {
                                            savedRepository.remove(item.id)
                                        }
                                    },
                                    onDismissed = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                        val removed = item
                                        if (downloadedOnly) {
                                            // حذف التنزيل من الجهاز.
                                            MediaDownloadController.delete(removed.id)
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(
                                                    message = "حُذف التنزيل",
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        } else {
                                            // إزالة من المفضّلة (مع تراجع).
                                            savedRepository.remove(removed.id)
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "تمت الإزالة من المحفوظات",
                                                    actionLabel = "تراجع",
                                                    duration = SnackbarDuration.Long,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    savedRepository.restoreSaved(removed)
                                                }
                                            }
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
}

/**
 * مفتاح علويّ يفصل بوضوح بين «المحفوظات» (كلّ المفضّلة) و«التحميلات»
 * (الموجود فعلاً على الجهاز) — يزيل اللبس بين المفهومين.
 */
@Composable
private fun LibrarySegments(
    downloadedOnly: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(4.dp),
    ) {
        LibrarySegment(
            label = "المحفوظات",
            icon = Icons.Filled.Bookmark,
            active = !downloadedOnly,
            onTap = { onSelect(false) },
        )
        LibrarySegment(
            label = "التحميلات",
            icon = Icons.Filled.DownloadDone,
            active = downloadedOnly,
            onTap = { onSelect(true) },
        )
    }
}

@Composable
private fun RowScope.LibrarySegment(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onTap: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) primary else Color.Transparent)
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) Color.White else primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontWeight = FontWeight.W600,
            color = if (active) Color.White else primary,
        )
    }
}

/**
 * ترويسة الحجم الإجمالي للمحتوى المنزَّل (تظهر فقط عند وجود تنزيلات)
 * لإعطاء المستخدم رؤية للمساحة المستهلكة.
 */
@Composable
private fun DownloadsTotal() {
    val downloads by MediaDownloadController.state.collectAsStateWithLifecycle()
    val total = downloads.values
        .filter { it.downloadStatus == MediaDownloadStatus.COMPLETED }
        .sumOf { it.totalBytes }
    if (total <= 0) return

    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.SdStorage,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "التحميلات: ${MediaDownloadController.humanSize(total)}",
            fontSize = 12.sp,
            color = color,
        )
    }
}

/** تبويبات تصفية النوع — «الكل / فيديو / صوت / كتاب». */
@Composable
private fun SavedFilterTabs(
    selectedFilter: SavedContentType?,
    onSelect: (SavedContentType?) -> Unit,
) {
    val filters: List<Pair<SavedContentType?, String>> = listOf(
        null to "الكل",
        SavedContentType.VIDEO to "فيديو",
        SavedContentType.AUDIO to "صوت",
        SavedContentType.BOOK to "كتاب",
    )
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { (key, label) ->
            val isSelected = selectedFilter == key
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp),
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = primary,
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = primary.copy(alpha = 0.2f),
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

/** بطاقة محفوظة قابلة للسحب للحذف (سحب من النهاية إلى البداية فقط). */
@Composable
private fun SwipeableSavedRow(
    item: SavedItem,
    isDownloaded: Boolean,
    downloadedSize: String,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    onDismissed: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismissed()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(savedDismissBackground),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .size(28.dp),
                    tint = savedDismissIconColor,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
    ) {
        SavedCardWidget(
            item = item,
            onTap = onTap,
            onRemove = onRemove,
            isDownloaded = isDownloaded,
            downloadedSize = downloadedSize,
        )
    }
}

/** يحوّل [SavedItem] إلى [Content] ثمّ يفتحه عبر [ContentRouter]. */
private fun openSavedContent(navController: NavHostController, item: SavedItem) {
    // ربط سلسلة نوع المحتوى المحفوظ بقيمة ContentType.
    val contentType = when (item.contentType) {
        "audio" -> ContentType.AUDIO
        "book", "document" -> ContentType.BOOK
        "youtube" -> ContentType.YOUTUBE
        else -> ContentType.VIDEO
    }

    val savedAt = Date(item.savedAtMs)
    val content = Content(
        id = item.id,
        title = item.title,
        author = "",
        description = "",
        type = contentType,
        thumbnailUrl = item.imageUrl ?: "",
        section = "",
        createdAt = savedAt,
        updatedAt = savedAt,
        createdBy = "",
        // للمحتوى المحفوظ/المنزَّل نحلّ الرابط من مزوّد التنزيل.
        sourceUrl = resolveSavedSourceUrl(item),
    )

    ContentRouter.open(navController, content)
}

/** المسار المحلّي أولاً، وإلّا رابط سجلّ التنزيل. */
private fun resolveSavedSourceUrl(item: SavedItem): String? {
    val localPath = MediaDownloadController.getLocalPath(item.id)
    if (localPath != null) return localPath
    return MediaDownloadController.state.value[item.id]?.url
}
