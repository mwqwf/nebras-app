package com.nebras.mobile.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.data.LocalStoreKeys
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.feature.download.MediaDownloadController
import com.nebras.mobile.feature.home.HomeViewModel
import com.nebras.mobile.feature.player.shareContent
import java.io.File

/**
 * شاشة القراءة — نقل `features/reader/view/reader_screen.dart`.
 *
 * تتفرّع بحسب نوع المحتوى: كتاب (PDF) → [PdfViewer] أو [BookDetailsScreen]
 * إن لم يكن منزَّلاً بعد، مقال → [ArticleReaderScreen]، صورة →
 * [ImageViewerScreen].
 *
 * ⚠️ آخر صفحة تُحفظ في [ReaderRepository] بنفس مفاتيح نسخة Flutter
 * (`reading_page_*`) فيبقى تقدّم المستخدم القديم بعد الترحيل.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    contentId: String,
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val store = ServiceLocator.localStore
    val repository = remember { ReaderRepository(context, store) }

    val content: Content? = remember(contentId, homeState.sections) {
        homeState.sections.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.id == contentId }
            ?: ServiceLocator.contentMetadataCache.getById(contentId)
    }

    // الوضع الليليّ للقارئ — مفتاح مستقلّ عن سمة التطبيق (كما في الأصل).
    var nightMode by remember {
        mutableStateOf(store.getBool(LocalStoreKeys.READER_NIGHT_MODE, false))
    }
    DisposableEffect(nightMode) {
        store.putBool(LocalStoreKeys.READER_NIGHT_MODE, nightMode)
        onDispose { }
    }

    if (content == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // المقالات والصور لها عارض خاصّ لا يحتاج شريط القارئ.
    when (content.type) {
        ContentType.ARTICLE -> {
            ArticleReaderScreen(content = content, navController = navController)
            return
        }
        ContentType.IMAGE -> {
            ImageViewerScreen(content = content, navController = navController)
            return
        }
        else -> Unit
    }

    // الكتاب: نحتاج نسخة محليّة. المسار من التنزيلات إن وُجد، وإلّا من
    // مجلّد القارئ القديم، وإلّا نعرض شاشة التفاصيل/التنزيل.
    val downloads by MediaDownloadController.state.collectAsStateWithLifecycle()
    val localPath = remember(downloads, content.id) {
        MediaDownloadController.getLocalPath(content.id)
            ?: repository.getFilePath(content.id).takeIf { repository.isDownloaded(content.id) }
    }

    if (localPath == null) {
        BookDetailsScreen(
            content = content,
            navController = navController,
            onOpenReader = { /* يُعاد التركيب تلقائياً فور اكتمال التنزيل. */ },
        )
        return
    }

    val lastPage = remember(content.id) { repository.getLastPage(content.id) ?: 1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(content.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { nightMode = !nightMode }) {
                        Icon(
                            imageVector = if (nightMode) {
                                Icons.Default.LightMode
                            } else {
                                Icons.Default.DarkMode
                            },
                            contentDescription = if (nightMode) "وضع نهاري" else "وضع ليلي",
                        )
                    }
                    IconButton(onClick = { shareContent(context, content) }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                    }
                },
            )
        },
    ) { padding ->
        PdfViewer(
            file = File(localPath),
            initialPage = lastPage,
            onPageChanged = { page, total ->
                repository.saveLastPage(content.id, page, total)
            },
            nightMode = nightMode,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
