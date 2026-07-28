package com.nebras.mobile.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch
import java.util.Date

private const val CHANNEL_PAGE_SIZE = 60

/**
 * 📺 صفحة قناة ناشر (مثل صفحة قناة YouTube): هويّة القناة + كلّ منشوراتها.
 * إن كانت قناة المستخدم نفسه تظهر أزرار «نشر جديد» و«تعديل القناة».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    navController: NavHostController,
    @Suppress("UNUSED_PARAMETER") authViewModel: AuthViewModel,
) {
    var channel by remember { mutableStateOf<UgcChannel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var postLimit by remember { mutableStateOf(CHANNEL_PAGE_SIZE) }
    var showEdit by remember { mutableStateOf(false) }

    val isMine = UgcService.currentUid == channelId
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channelId) {
        runCatching { UgcService.getChannel(channelId) }
            .onSuccess {
                channel = it
                loading = false
            }
            .onFailure { loading = false }
    }

    val postsFlow = remember(channelId, postLimit) {
        UgcService.watchChannel(channelId, postLimit)
    }
    // `null` = ما زال البثّ في أوّل انتظار (نظير ConnectionState.waiting).
    val postsOrNull by postsFlow.collectAsStateWithLifecycle(initialValue = null)
    val posts = postsOrNull ?: emptyList()

    val current = channel

    if (showEdit && current != null) {
        ChannelSetupScreen(
            onCancel = { showEdit = false },
            onSaved = { updated ->
                channel = updated
                showEdit = false
            },
            existing = current,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "القناة") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                        )
                    }
                },
                actions = {
                    if (!isMine && current != null) {
                        ChannelFollowBell(
                            channelId = current.uid,
                            channelName = current.name,
                            onMessage = { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            },
                        )
                    }
                    if (isMine && current != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "تعديل القناة",
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (isMine && current != null) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.PUBLISH) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("نشر الآن") },
                )
            }
        },
    ) { innerPadding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            current == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("هذه القناة لم تعد متاحة.")
                }
            }

            else -> {
                // تجميع منشورات القناة بأقسام الناشر الخاصّة (مثل أقسام قناة
                // YouTube)؛ «بدون قسم» في الأخير.
                val rows = remember(current, posts) { groupedChannelRows(current, posts) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(20.dp),
                ) {
                    item(key = "channel_header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UgcChannelAvatar(
                                name = current.name,
                                photoUrl = current.photoUrl,
                                radius = 28.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = current.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.W800,
                                    ),
                                )
                                Spacer(Modifier.height(4.dp))
                                UgcBadge()
                            }
                        }
                        if (current.bio.trim().isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = current.bio,
                                style = MaterialTheme.typography.bodyMedium.let {
                                    it.copy(lineHeight = it.fontSize * 1.6f)
                                },
                            )
                        }
                        if (isMine) {
                            Spacer(Modifier.height(14.dp))
                            PublisherStats(posts = posts)
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "منشورات القناة (${posts.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.W700,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    if (postsOrNull == null && posts.isEmpty()) {
                        item(key = "channel_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (posts.isEmpty()) {
                        item(key = "channel_empty") {
                            Text(
                                text = "لا توجد منشورات في هذه القناة بعد.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = NebrasColors.textMuted,
                                ),
                            )
                        }
                    } else {
                        items(
                            count = rows.size,
                            key = { index ->
                                when (val row = rows[index]) {
                                    is ChannelRow.PostRow -> row.post.content.id
                                    is ChannelRow.Header -> "channel_header_$index"
                                }
                            },
                        ) { index ->
                            when (val row = rows[index]) {
                                is ChannelRow.Header -> ChannelSectionHeader(row.name)
                                is ChannelRow.PostRow -> {
                                    UgcPostCard(
                                        post = row.post,
                                        onTap = {
                                            navController.navigate(
                                                Routes.ugcPost(row.post.content.id),
                                            )
                                        },
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (posts.size >= postLimit) {
                        item(key = "channel_load_more") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { postLimit += CHANNEL_PAGE_SIZE }) {
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تحميل منشورات أقدم")
                            }
                        }
                    }

                    item(key = "channel_bottom_gap") {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

/** عنوان قسم داخل القناة. */
@Composable
private fun ChannelSectionHeader(name: String) {
    Row(
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = NebrasColors.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W700),
        )
    }
}

/**
 * ملخّص صغير يجيب الناشر فوراً عن أثر نشاطه في آخر سبعة أيام، من غير
 * شاشة تحليلات معقّدة أو جمع أي معرّفات للمشاهدين.
 */
@Composable
private fun PublisherStats(posts: List<UgcPost>) {
    val cutoff = Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
    val newPosts = posts.count { it.content.createdAt.after(cutoff) }
    val weeklyViews = posts.sumOf { it.viewsLast7Days }
    val reactions = posts.sumOf { it.reactionsCount }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                NebrasColors.primary.copy(alpha = 0.07f),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                NebrasColors.primary.copy(alpha = 0.18f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        PublisherMetric(
            icon = Icons.Outlined.Visibility,
            value = "$weeklyViews",
            label = "مشاهدة هذا الأسبوع",
            modifier = Modifier.weight(1f),
        )
        PublisherMetric(
            icon = Icons.Rounded.PostAdd,
            value = "$newPosts",
            label = "منشور هذا الأسبوع",
            modifier = Modifier.weight(1f),
        )
        PublisherMetric(
            icon = Icons.Rounded.FavoriteBorder,
            value = "$reactions",
            label = "إجمالي الإعجابات",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PublisherMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = NebrasColors.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
        )
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(color = NebrasColors.textMuted),
        )
    }
}

// ── تجميع المنشورات بأقسام القناة ─────────────────────────────────────

private sealed interface ChannelRow {
    data class Header(val name: String) : ChannelRow
    data class PostRow(val post: UgcPost) : ChannelRow
}

/**
 * يبني قائمة المنشورات مجمّعةً بأقسام القناة الخاصّة بالناشر: كلّ قسم
 * عنوانه ثم منشوراته، ثم غير المصنّف بلا عنوان (أو كقائمة مسطّحة إن لم
 * تكن للقناة أقسام أصلاً).
 */
private fun groupedChannelRows(channel: UgcChannel, posts: List<UgcPost>): List<ChannelRow> {
    val bySection = LinkedHashMap<String, MutableList<UgcPost>>()
    for (post in posts) {
        bySection.getOrPut(post.channelSectionId) { mutableListOf() }.add(post)
    }
    val hasAnySection = bySection.keys.any { it.isNotEmpty() } && channel.sections.isNotEmpty()
    if (!hasAnySection) return posts.map { ChannelRow.PostRow(it) }

    val out = mutableListOf<ChannelRow>()
    val seen = mutableSetOf<String>()
    for (section in channel.sections) {
        val list = bySection[section.id]
        if (list.isNullOrEmpty()) continue
        seen.add(section.id)
        out.add(ChannelRow.Header(section.name))
        list.forEach { out.add(ChannelRow.PostRow(it)) }
    }
    // منشورات بقسم محذوف من القناة أو بلا قسم.
    val rest = bySection.entries.filter { it.key !in seen }.flatMap { it.value }
    if (rest.isNotEmpty()) {
        if (out.isNotEmpty()) out.add(ChannelRow.Header("بدون قسم"))
        rest.forEach { out.add(ChannelRow.PostRow(it)) }
    }
    return out
}
