package com.nebras.mobile.feature.community

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.nebras.mobile.core.service.ContentReportService
import com.nebras.mobile.core.service.DeepLinkService
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.core.service.ReportReason
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.util.ar
import com.nebras.mobile.core.widget.AutoLinkText
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.content.ContentRouter
import com.nebras.mobile.ui.Routes
import kotlinx.coroutines.launch

/**
 * 📄 تفاصيل منشور مجتمع: تشغيل/فتح المحتوى بمشغّلات التطبيق نفسها +
 * التعليقات (متاحة على محتوى المجتمع **فقط**) + إبلاغ/حجب (سياسة UGC).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UgcPostScreen(
    postId: String,
    navController: NavHostController,
    @Suppress("UNUSED_PARAMETER") authViewModel: AuthViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var post by remember { mutableStateOf<UgcPost?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reactionsCount by remember { mutableStateOf(0) }
    var reactionBusy by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<UgcChannel?>(null) }

    LaunchedEffect(postId) {
        runCatching { UgcService.getPost(postId) }
            .onSuccess {
                post = it
                reactionsCount = it?.reactionsCount ?: 0
                loading = false
            }
            .onFailure { loading = false }
    }

    val current = post
    val isMine = current != null && UgcService.currentUid == current.channelId
    val signedIn = FirebaseAuth.getInstance().currentUser.let {
        it != null && !it.isAnonymous
    }

    fun share(target: UgcPost) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "${target.content.title}\n${DeepLinkService.contentLink(target.content.id)}",
            )
            putExtra(Intent.EXTRA_SUBJECT, target.content.title)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    // محرّر المنشور بالكامل — يُعرض مكان الشاشة (بديل Navigator.push).
    val channelForEdit = editingChannel
    if (channelForEdit != null && current != null) {
        PublishFormScreen(
            channel = channelForEdit,
            onClose = { editingChannel = null },
            onDone = { navController.popBackStack() },
            editingPost = current,
            successMessage = "تمّ حفظ تعديلات المنشور.",
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("منشور المجتمع") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                        )
                    }
                },
                actions = {
                    // رابط تقني يفتح صفحة المجتمع المميّزة بوضوح؛ لا ينقل
                    // المنشور إلى المحتوى الرسمي ولا يغيّر مسؤولية ناشره عنه.
                    IconButton(onClick = { current?.let { share(it) } }) {
                        Icon(Icons.Outlined.Share, contentDescription = "مشاركة")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("الإبلاغ عن هذا المحتوى") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Flag, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showReport = true
                                },
                            )
                            if (!isMine) {
                                DropdownMenuItem(
                                    text = { Text("حجب القناة") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Block, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        showBlock = true
                                    },
                                )
                            }
                            if (isMine) {
                                DropdownMenuItem(
                                    text = { Text("تعديل المنشور بالكامل") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            val channel = runCatching { UgcService.getMyChannel() }
                                                .getOrNull()
                                            if (channel == null) {
                                                snackbarHostState.showSnackbar(
                                                    "تعذّر العثور على قناتك لتعديل المنشور.",
                                                )
                                            } else {
                                                editingChannel = channel
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("حذف المنشور") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = Color.Red,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        showDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
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
                // مسار التنقّل يمرّر معرّفاً لا كائناً، فقد يكون المنشور
                // حُذف أو حُجب قبل فتح الشاشة.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("المحتوى غير متاح حالياً")
                }
            }

            else -> {
                UgcPostBody(
                    post = current,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    reactionsCount = reactionsCount,
                    reactionBusy = reactionBusy,
                    signedIn = signedIn,
                    commentText = commentText,
                    sending = sending,
                    onCommentTextChange = { commentText = it },
                    onOpenChannel = {
                        navController.navigate(Routes.channel(current.channelId))
                    },
                    onOpenContent = {
                        scope.launch { UgcService.recordPostOpened(current) }
                        ContentRouter.open(navController, current.content)
                    },
                    onShare = { share(current) },
                    onToggleReaction = {
                        if (!signedIn) {
                            scope.launch {
                                snackbarHostState.showSnackbar("سجّل الدخول للتفاعل مع المنشور.")
                            }
                        } else if (!reactionBusy) {
                            reactionBusy = true
                            scope.launch {
                                runCatching { UgcService.toggleReaction(current.content.id) }
                                    .onSuccess { liked ->
                                        reactionsCount = (
                                            reactionsCount + (if (liked) 1 else -1)
                                            ).coerceIn(0, Int.MAX_VALUE)
                                    }
                                    .onFailure {
                                        snackbarHostState.showSnackbar(
                                            "تعذّر حفظ التفاعل. حاول مجدداً.",
                                        )
                                    }
                                reactionBusy = false
                            }
                        }
                    },
                    onSendComment = {
                        val text = commentText.trim()
                        if (text.isNotEmpty() && !sending) {
                            sending = true
                            scope.launch {
                                runCatching { UgcService.addComment(current.content.id, text) }
                                    .onSuccess { commentText = "" }
                                    .onFailure {
                                        snackbarHostState.showSnackbar(
                                            "تعذّر نشر التعليق. حاول مجدداً.",
                                        )
                                    }
                                sending = false
                            }
                        }
                    },
                    onDeleteComment = { comment ->
                        scope.launch {
                            runCatching { UgcService.deleteComment(current.content.id, comment) }
                        }
                    },
                )
            }
        }
    }

    if (showReport && current != null) {
        ReportReasonSheet(
            onDismiss = { showReport = false },
            onPick = { reason ->
                showReport = false
                scope.launch {
                    runCatching {
                        ContentReportService.submit(content = current.content, reason = reason)
                        HiddenContentService.hide(current.content.id)
                    }.onSuccess {
                        snackbarHostState.showSnackbar(
                            message = "تمّ إرسال البلاغ. شكراً لك.",
                            duration = SnackbarDuration.Short,
                        )
                        navController.popBackStack()
                    }.onFailure {
                        snackbarHostState.showSnackbar("تعذّر إرسال البلاغ. حاول مجدّداً.")
                    }
                }
            },
        )
    }

    if (showBlock && current != null) {
        AlertDialog(
            onDismissRequest = { showBlock = false },
            title = { Text("حجب القناة؟") },
            text = { Text("لن تظهر لك منشورات «${current.channelName}» على هذا الجهاز.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlock = false
                        UgcService.setChannelBlocked(current.channelId, true)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "تم حجب القناة من موجزك.",
                                duration = SnackbarDuration.Short,
                            )
                            navController.popBackStack()
                        }
                    },
                ) {
                    Text("حجب القناة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlock = false }) { Text("إلغاء") }
            },
        )
    }

    if (showDelete && current != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("حذف المنشور؟") },
            text = { Text("سيُحذف المنشور وملفه نهائياً.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDelete = false
                        scope.launch {
                            runCatching { UgcService.deleteMyPost(current) }
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NebrasColors.error,
                    ),
                ) {
                    Text("حذف المنشور")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("إلغاء") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UgcPostBody(
    post: UgcPost,
    reactionsCount: Int,
    reactionBusy: Boolean,
    signedIn: Boolean,
    commentText: String,
    sending: Boolean,
    onCommentTextChange: (String) -> Unit,
    onOpenChannel: () -> Unit,
    onOpenContent: () -> Unit,
    onShare: () -> Unit,
    onToggleReaction: () -> Unit,
    onSendComment: () -> Unit,
    onDeleteComment: (UgcComment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val liked by remember(post.content.id) {
        UgcService.watchMyReaction(post.content.id)
    }.collectAsStateWithLifecycle(initialValue = false)
    val commentsOrNull by remember(post.content.id) {
        UgcService.watchComments(post.content.id)
    }.collectAsStateWithLifecycle(initialValue = null)
    val comments = commentsOrNull ?: emptyList()
    val myUid = UgcService.currentUid

    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
        ) {
            item(key = "ugc_post_head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UgcBadge()
                    if (post.channelSectionName.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = post.channelSectionName,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    fontSize = 11.sp,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = ugcTypeIcon(post.content.type),
                        contentDescription = null,
                        tint = NebrasColors.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = post.content.title,
                    style = MaterialTheme.typography.titleLarge.let {
                        it.copy(fontWeight = FontWeight.W800, lineHeight = it.fontSize * 1.35f)
                    },
                )
                Spacer(Modifier.height(10.dp))
                // صفّ الناشر — الضغط يفتح صفحة القناة (مثل YouTube).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenChannel)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UgcChannelAvatar(
                        name = post.channelName,
                        photoUrl = post.channelPhotoUrl,
                        radius = 16.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.channelName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.W700,
                            ),
                        )
                        Text(
                            text = "منشور من مستخدم مستقل، وليس من إدارة نبراس.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = NebrasColors.textMuted,
                            ),
                        )
                    }
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenContent,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Icon(ugcTypeIcon(post.content.type), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("فتح المحتوى")
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onToggleReaction,
                        enabled = !reactionBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (liked) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = null,
                            tint = if (liked) {
                                Color(0xFFFF5252)
                            } else {
                                androidx.compose.material3.LocalContentColor.current
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (reactionsCount == 0) "إعجاب" else "إعجاب ($reactionsCount)",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("مشاركة")
                    }
                }
                if (
                    post.content.description.trim().isNotEmpty() &&
                    post.content.type.name.lowercase() != "article"
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "الوصف",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.W700,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    AutoLinkText(
                        text = post.content.description,
                        style = MaterialTheme.typography.bodyMedium.let {
                            it.copy(lineHeight = it.fontSize * 1.7f)
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Text(
                    text = "التعليقات",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.W700,
                    ),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (commentsOrNull == null && comments.isEmpty()) {
                item(key = "ugc_comments_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (comments.isEmpty()) {
                item(key = "ugc_comments_empty") {
                    Text(
                        text = "لا توجد تعليقات بعد.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NebrasColors.textMuted,
                        ),
                    )
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    CommentTile(
                        comment = comment,
                        canDelete = myUid != null &&
                            (myUid == comment.uid || myUid == post.channelId),
                        onDelete = { onDeleteComment(comment) },
                    )
                }
            }

            item(key = "ugc_comments_gap") {
                Spacer(Modifier.height(8.dp))
            }
        }

        // مُدخل التعليق — للحسابات الحقيقيّة فقط.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp),
        ) {
            if (signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 500) onCommentTextChange(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("اكتب تعليقاً محترماً…") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = onSendComment,
                        enabled = !sending,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Send, contentDescription = null)
                        }
                    }
                }
            } else {
                Text(
                    text = "سجّل الدخول لإضافة تعليق.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NebrasColors.textMuted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CommentTile(
    comment: UgcComment,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        UgcChannelAvatar(
            name = comment.name,
            photoUrl = comment.photoUrl,
            radius = 13.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.W700,
                    ),
                )
                if (canDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onDelete),
                        tint = NebrasColors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium.let {
                    it.copy(lineHeight = it.fontSize * 1.5f)
                },
            )
        }
    }
}

/** ورقة أسباب الإبلاغ — نفس ترتيب `ReportReason.values` ونصوصها. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportReasonSheet(
    onDismiss: () -> Unit,
    onPick: (ReportReason) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "الإبلاغ عن هذا المحتوى",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            for (reason in ReportReason.values()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(reason) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = reason.labelKey.ar,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
