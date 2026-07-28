package com.nebras.mobile.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.widget.AutoLinkText
import com.nebras.mobile.core.widget.ContentAttribution
import com.nebras.mobile.feature.content.ReportContentButton
import com.nebras.mobile.feature.content.SuggestCorrectionButton
import com.nebras.mobile.feature.download.MediaDownloadController
import com.nebras.mobile.feature.download.MediaDownloadStatus
import com.nebras.mobile.feature.player.shareContent
import com.nebras.mobile.feature.saved.SaveButton

/**
 * تفاصيل الكتاب قبل القراءة — نقل `features/reader/view/book_details_screen.dart`.
 *
 * تُعرض حين لا توجد نسخة محليّة بعد: غلاف + بيانات + قسم التنزيل بحالاته
 * (خامل / يُنزَّل / متوقّف / فشل). عند اكتمال التنزيل يُستدعى [onOpenReader].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    content: Content,
    navController: NavHostController,
    onOpenReader: () -> Unit,
) {
    val context = LocalContext.current
    val downloads by MediaDownloadController.state.collectAsStateWithLifecycle()
    val item = downloads[content.id]
    val status = item?.downloadStatus ?: MediaDownloadStatus.IDLE

    // فور اكتمال التنزيل نفتح القارئ (نفس سلوك الأصل).
    LaunchedEffect(status) {
        if (status == MediaDownloadStatus.COMPLETED) onOpenReader()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { shareContent(context, content) }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            BookHeader(content = content)

            Spacer(Modifier.height(16.dp))

            BookInfoRow(content = content)

            Spacer(Modifier.height(16.dp))

            // ── قسم التنزيل بحالاته ──
            DownloadSection(
                content = content,
                status = status,
                progress = item?.progress ?: 0.0,
                totalBytes = item?.totalBytes ?: 0L,
                onStart = {
                    val url = content.sourceUrl ?: return@DownloadSection
                    MediaDownloadController.start(
                        contentId = content.id,
                        url = url,
                        contentType = "document",
                        title = content.title,
                        imageUrl = content.thumbnailUrl,
                    )
                },
                onPause = { MediaDownloadController.pause(content.id) },
                onResume = { MediaDownloadController.resume(content.id) },
                onCancel = { MediaDownloadController.cancel(content.id) },
                onOpen = onOpenReader,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SaveButton(content = content)
                ReportContentButton(content = content)
                SuggestCorrectionButton(content = content)
            }

            if (content.description.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                BookDescription(text = content.description)
            }

            Spacer(Modifier.height(16.dp))
            ContentAttribution(content = content, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** وصف الكتاب مع «اقرأ المزيد» — نقل `widgets/descripetion_widget.dart`. */
@Composable
private fun BookDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AutoLinkText(
            text = text,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        // زرّ التوسيع يظهر فقط للأوصاف الطويلة (نفس عتبة الأصل).
        if (text.length > 200) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "عرض أقل" else "اقرأ المزيد",
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
