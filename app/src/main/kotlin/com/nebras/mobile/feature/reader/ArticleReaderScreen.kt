package com.nebras.mobile.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.network.NebrasHttpClient
import com.nebras.mobile.core.widget.AutoLinkText
import com.nebras.mobile.core.widget.ContentAttribution
import com.nebras.mobile.feature.player.shareContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * قارئ المقالات — نقل `features/content/view/article_reader_screen.dart`.
 *
 * نصّ المقال يعيش في وصف المستند غالباً؛ وإن كان مرفوعاً كملفّ نصّيّ
 * (منشورات المجتمع من نوع `article`) نجلبه من `sourceUrl`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    content: Content,
    navController: NavHostController,
) {
    val context = LocalContext.current
    var fontScale by remember { mutableFloatStateOf(1f) }

    // الوصف هو المصدر الأساسيّ؛ نجلب الملفّ النصّيّ فقط إن كان الوصف فارغاً.
    val bodyState = produceState(initialValue = content.description, content.id) {
        if (content.description.isNotBlank()) return@produceState
        val url = content.sourceUrl
        if (url.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { NebrasHttpClient.getString(url) }.getOrDefault("")
        }
    }
    val body by bodyState

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
                    IconButton(onClick = { fontScale = (fontScale - 0.1f).coerceAtLeast(0.8f) }) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "تصغير الخط")
                    }
                    IconButton(onClick = { fontScale = (fontScale + 0.1f).coerceAtMost(1.8f) }) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "تكبير الخط")
                    }
                    IconButton(onClick = { shareContent(context, content) }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                    }
                },
            )
        },
    ) { padding ->
        if (body.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (24 * fontScale).sp,
                ),
            )
            if (content.author.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = content.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            AutoLinkText(
                text = body,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (16 * fontScale).sp,
                    lineHeight = (28 * fontScale).sp,
                ),
            )

            Spacer(Modifier.height(20.dp))
            ContentAttribution(content = content)
            Spacer(Modifier.height(24.dp))
        }
    }
}
