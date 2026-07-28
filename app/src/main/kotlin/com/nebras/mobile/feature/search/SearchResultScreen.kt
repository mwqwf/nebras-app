package com.nebras.mobile.feature.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nebras.mobile.core.data.sortedContentOldestFirst
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.wire
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.util.ar
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.EmptyStateWidget
import com.nebras.mobile.core.widget.SmartNetworkImage
import com.nebras.mobile.core.widget.contentTypeIcon
import com.nebras.mobile.feature.content.ContentRouter
import kotlinx.coroutines.launch

/**
 * شاشة نتائج قسم — نقل `features/search/view/search_result_screen.dart`.
 * تعرض محتوى قسم بعينه، ويُفتح إليها عند اختيار رقاقة قسم.
 */
@Composable
fun SearchResultScreen(
    sectionId: String,
    title: String,
    navController: NavHostController,
    searchViewModel: SearchViewModel,
) {
    var contentItems by remember { mutableStateOf<List<Content>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sectionId, reloadToken) {
        isLoading = true
        error = null
        runCatching { searchViewModel.loadSectionContent(sectionId) }
            .onSuccess { loaded ->
                // الأقدم أولاً — انظر core/data/ContentOrdering.kt
                contentItems = loaded.sortedContentOldestFirst()
                isLoading = false
            }
            .onFailure { e ->
                error = e.toString()
                isLoading = false
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppBarWidget(title = title, onBack = { navController.popBackStack() })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator()

                error != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = NebrasColors.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error.orEmpty(),
                        style = NebrasTextStyles.body,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { reloadToken++ }) {
                        Text("إعادة المحاولة")
                    }
                }

                contentItems.isEmpty() -> EmptyStateWidget(
                    message = "لا يوجد محتوى داخل هذا القسم",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    items(contentItems, key = { it.id }) { content ->
                        SectionContentRow(
                            content = content,
                            onTap = {
                                scope.launch {
                                    val handled = searchOpenIfCommunity(
                                        navController = navController,
                                        content = content,
                                        onMessage = { message ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        },
                                    )
                                    if (!handled) {
                                        ContentRouter.open(navController, content)
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

/** صفّ محتوى داخل قسم: صورة مصغّرة + عنوان + نوع + سهم. */
@Composable
private fun SectionContentRow(content: Content, onTap: () -> Unit) {
    val isCommunity = searchIsCommunityContent(content)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clip(RoundedCornerShape(8.dp))) {
                SmartNetworkImage(
                    imageUrl = content.thumbnailUrl,
                    title = content.title,
                    iconOverride = contentTypeIcon(content.type),
                    width = 64.dp,
                    height = 64.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    style = NebrasTextStyles.bodyBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isCommunity) {
                        "من المجتمع • ${content.type.wire.ar}"
                    } else {
                        content.type.wire.ar
                    },
                    style = NebrasTextStyles.greysmallText,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = NebrasColors.greycolor,
            )
        }
    }
}
