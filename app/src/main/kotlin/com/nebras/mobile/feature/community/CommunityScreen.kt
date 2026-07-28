package com.nebras.mobile.feature.community

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups2
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.ui.Routes

private const val COMMUNITY_PAGE_SIZE = 60

/**
 * 🌍 «مجتمع نبراس» — صفحة مستقلّة لأحدث ما نشره المستخدمون (قرار المالك:
 * فصل واضح عن المحتوى الرسميّ). كلّ عنصر يحمل شارة المجتمع واسم ناشره.
 *
 * [showPublishButton]: نُخفي الزرّ الداخلي عند عرضه كتَبويب؛ زر النشر العام
 * في HomeShell يبقى متاحاً في كل التبويبات فلا يظهر زران فوق بعضهما.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    navController: NavHostController,
    @Suppress("UNUSED_PARAMETER") authViewModel: AuthViewModel,
    showPublishButton: Boolean = false,
) {
    var limit by remember { mutableStateOf(COMMUNITY_PAGE_SIZE) }

    // إعادة بناء البثّ عند تغيّر قائمة الحجب كي تختفي منشورات القناة فوراً.
    val blocked by UgcService.blockedChannels.collectAsStateWithLifecycle()
    val postsFlow = remember(limit, blocked) { UgcService.watchLatest(limit) }
    val postsOrNull by postsFlow.collectAsStateWithLifecycle(initialValue = null)
    val posts = postsOrNull ?: emptyList()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("مجتمع نبراس") },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UgcBadge()
                    }
                },
            )
        },
        floatingActionButton = {
            if (showPublishButton) {
                ExtendedFloatingActionButton(
                    onClick = { CommunityPublishLauncher.open(navController) },
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    text = { Text("انشر") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // تنويه دائم: هذا المحتوى من مستخدمين عاديّين لا من إدارة نبراس.
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 10.dp, end = 16.dp)
                    .fillMaxWidth()
                    .background(
                        NebrasColors.warning.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = NebrasColors.warning,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "محتوى ينشره مستخدمون مستقلون في قنواتهم الخاصة، ولا يمثّل إدارة نبراس ولا تتحمّل نبراس مسؤوليته. الإبلاغ متاح على كل منشور.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.let {
                        it.copy(lineHeight = it.fontSize * 1.5f)
                    },
                )
            }

            when {
                postsOrNull == null && posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Groups2,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = NebrasColors.textMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "لا توجد منشورات من المجتمع بعد. كن أول من يشارك معرفة نافعة.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium.let {
                                    it.copy(
                                        color = NebrasColors.textMuted,
                                        lineHeight = it.fontSize * 1.6f,
                                    )
                                },
                            )
                        }
                    }
                }

                else -> {
                    val canLoadMore = posts.size >= limit
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 90.dp,
                        ),
                    ) {
                        items(
                            count = posts.size,
                            key = { index -> posts[index].content.id },
                        ) { index ->
                            UgcPostCard(
                                post = posts[index],
                                onTap = {
                                    navController.navigate(
                                        Routes.ugcPost(posts[index].content.id),
                                    )
                                },
                                modifier = Modifier.padding(
                                    bottom = if (index == posts.size - 1) 0.dp else 10.dp,
                                ),
                            )
                        }
                        if (canLoadMore) {
                            item(key = "community_load_more") {
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { limit += COMMUNITY_PAGE_SIZE },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                ) {
                                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("تحميل منشورات أقدم")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
