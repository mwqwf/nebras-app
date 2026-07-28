package com.nebras.mobile.feature.community

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.ShareIntentService
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.util.ar
import com.nebras.mobile.core.util.smartTitleFromFileName
import com.nebras.mobile.feature.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** مراحل بوّابة النشر قبل عرض النموذج. */
private enum class PublishStage { CHECKING, SIGN_IN_PROMPT, CHANNEL_SETUP, FORM }

/**
 * 📤 وجهة النشر (`Routes.PUBLISH`).
 *
 * تجمع ما كان في نسخة Flutter موزّعاً بين `CommunityPublishLauncher` و
 * `PublishScreen`: لا نشر للضيف (طلب دخول صريح)، ثمّ إنشاء القناة في
 * الزيارة الأولى، ثمّ نموذج النشر للقناة القائمة.
 */
@Composable
fun PublishScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel,
) {
    val context = LocalContext.current
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(PublishStage.CHECKING) }
    var channel by remember { mutableStateOf<UgcChannel?>(null) }
    var sharedFile by remember { mutableStateOf<File?>(null) }
    var sharedName by remember { mutableStateOf("") }
    var sharedMime by remember { mutableStateOf("") }

    // ملفّ وصل من تطبيق آخر عبر «مشاركة» — يُستهلَك مرّة واحدة فقط.
    LaunchedEffect(Unit) {
        val pending = ShareIntentService.pendingFile.value
        if (pending != null) {
            sharedFile = File(pending.path)
            sharedName = pending.name
            sharedMime = pending.mime
            ShareIntentService.consume()
        }
    }

    val signedIn = authState.user != null

    LaunchedEffect(signedIn) {
        if (!signedIn) {
            stage = PublishStage.SIGN_IN_PROMPT
            return@LaunchedEffect
        }
        stage = PublishStage.CHECKING
        runCatching { UgcService.getMyChannel() }
            .onSuccess { existing ->
                when {
                    existing == null -> stage = PublishStage.CHANNEL_SETUP
                    !existing.isActive -> {
                        snackbarHostState.showSnackbar(
                            "هذه القناة موقوفة حالياً ولا يمكن النشر منها.",
                        )
                        navController.popBackStack()
                    }
                    else -> {
                        channel = existing
                        stage = PublishStage.FORM
                    }
                }
            }
            .onFailure {
                snackbarHostState.showSnackbar("تعذّر فتح مساحة النشر. حاول مجدداً.")
                navController.popBackStack()
            }
    }

    when (stage) {
        PublishStage.CHANNEL_SETUP -> {
            ChannelSetupScreen(
                onCancel = { navController.popBackStack() },
                onSaved = { created ->
                    if (!created.isActive) {
                        // نُعيد المرحلة إلى الانتظار كي يعود مضيف الرسائل
                        // إلى التركيب فتظهر الرسالة قبل الرجوع.
                        stage = PublishStage.CHECKING
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "هذه القناة موقوفة حالياً ولا يمكن النشر منها.",
                            )
                            navController.popBackStack()
                        }
                    } else {
                        channel = created
                        stage = PublishStage.FORM
                    }
                },
            )
        }

        PublishStage.FORM -> {
            val active = channel
            if (active != null) {
                PublishFormScreen(
                    channel = active,
                    onClose = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                    initialFile = sharedFile,
                    initialName = sharedName,
                    initialMime = sharedMime,
                    successMessage = "تم نشر محتواك في مجتمع نبراس.",
                )
            }
        }

        PublishStage.CHECKING, PublishStage.SIGN_IN_PROMPT -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (stage == PublishStage.SIGN_IN_PROMPT) {
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("سجّل الدخول للنشر") },
            text = {
                Text(
                    "إنشاء قناة أو نشر محتوى يحتاج حساباً حتى يظهر اسم الناشر وتبقى المنصة آمنة.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        stage = PublishStage.CHECKING
                        scope.launch {
                            val ok = authViewModel.signInWithGoogle(context)
                            if (!ok) {
                                snackbarHostState.showSnackbar("تعذّر تسجيل الدخول. حاول مجدداً.")
                                navController.popBackStack()
                            }
                            // النجاح يُعيد تشغيل جالب القناة عبر تغيّر حالة المصادقة.
                        }
                    },
                ) {
                    Text("الدخول عبر Google")
                }
            },
            dismissButton = {
                TextButton(onClick = { navController.popBackStack() }) { Text("إلغاء") }
            },
        )
    }
}

/**
 * 📤 نموذج النشر — يفتحه الزرّ العائم لأيّ مستخدم لديه قناة.
 * النشر فوريّ بلا موافقة مسبقة (سياسة نبراس)، مع تذكير دائم بالشروط.
 *
 * عند تمرير [editingPost] تتحوّل الشاشة إلى محرّر كامل له بدلاً من إنشاء
 * منشور. يبقى كل شيء داخل قناة صاحبه؛ لا صلة بأقسام نبراس الرسمية.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PublishFormScreen(
    channel: UgcChannel,
    onClose: () -> Unit,
    onDone: () -> Unit,
    editingPost: UgcPost? = null,
    initialFile: File? = null,
    initialName: String = "",
    initialMime: String = "",
    /**
     * رسالة النجاح التي كانت تظهر في نسخة Flutter على الشاشة السابقة بعد
     * `pop(true)`. في Compose يملك كلّ مسار مضيف رسائله، فنعرضها هنا قبل
     * استدعاء [onDone] كي لا تضيع مع تفكيك الشاشة.
     */
    successMessage: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentChannel by remember { mutableStateOf(channel) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var articleBody by remember { mutableStateOf("") }

    // 'audio' | 'video' | 'document' | 'article' — بأسماء يفهمها
    // Content.fromJson فتعمل كلّ مشغّلات التطبيق الحاليّة.
    var type by remember { mutableStateOf("audio") }
    var originalType by remember { mutableStateOf("") }
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var pickedName by remember { mutableStateOf("") }

    // مقاطع صوتية متعددة (MP3) لدمجها في منشور واحد بالترتيب المختار.
    val mergeItems = remember { mutableStateListOf<UgcMergeItem>() }
    var merging by remember { mutableStateOf(false) }
    var mergeProgress by remember { mutableStateOf(0.0) }

    // قسم القناة المختار لهذا المنشور — فارغ = «بدون قسم» (مسموح تماماً).
    var section by remember { mutableStateOf<ChannelSection?>(null) }

    var progress by remember { mutableStateOf(0.0) }
    var publishing by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var showNewSection by remember { mutableStateOf(false) }

    val isEditing = editingPost != null

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(Unit) {
        val post = editingPost
        if (post != null) {
            type = storedTypeForPost(post)
            originalType = type
            title = post.content.title
            if (type == "article") {
                articleBody = post.content.description
            } else {
                description = post.content.description
            }
            section = currentChannel.sections.firstOrNull { it.id == post.channelSectionId }
            return@LaunchedEffect
        }
        val incoming = initialFile ?: return@LaunchedEffect
        val exists = withContext(Dispatchers.IO) { incoming.exists() }
        if (!exists) return@LaunchedEffect
        val size = withContext(Dispatchers.IO) { incoming.length() }
        if (size > UgcService.MAX_FILE_SIZE_BYTES) {
            snackbarHostState.showSnackbar("حجم الملف المشترك أكبر من 300 م.ب.")
            return@LaunchedEffect
        }
        val name = initialName.trim().ifEmpty { incoming.name }
        val resolved = typeFromSharedFile(name, initialMime)
        if (resolved == "article") {
            val body = withContext(Dispatchers.IO) {
                runCatching { incoming.readText() }.getOrNull()
            } ?: return@LaunchedEffect
            type = "article"
            articleBody = if (body.length > 20000) body.substring(0, 20000) else body
            title = smartTitleFromFileName(name)
            return@LaunchedEffect
        }
        type = resolved
        pickedFile = incoming
        pickedName = name
        title = smartTitleFromFileName(name)
    }

    val singleFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // إذن الـ Uri قد يُلغى بعد لحظات ⇒ ننسخ الملفّ إلى الكاش فوراً.
            val picked = withContext(Dispatchers.IO) { copyPickedUriToCache(context, uri) }
                ?: return@launch
            if (picked.file.length() > UgcService.MAX_FILE_SIZE_BYTES) {
                snackbarHostState.showSnackbar("حجم الملف أكبر من الحد المسموح (300 م.ب).")
                return@launch
            }
            pickedFile = picked.file
            pickedName = picked.name
            mergeItems.clear()
            if (title.trim().isEmpty()) {
                // عنوان مقترح ذكيّ من اسم الملفّ — الاسم الآليّ البحت يُترك
                // فارغاً ليكتبه الناشر بدل اقتراح خردة.
                title = smartTitleFromFileName(picked.name)
            }
        }
    }

    val multiAudioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { copyPickedUriToCache(context, it) }
                    .map { UgcMergeItem(it.file.absolutePath, it.name) }
            }
            if (picked.isEmpty()) return@launch

            val current = pickedFile
            val seed = buildList {
                addAll(mergeItems)
                if (mergeItems.isEmpty() && current != null && UgcAudioMerger.isMp3(pickedName)) {
                    add(UgcMergeItem(current.absolutePath, pickedName))
                }
            }
            val existing = seed.map { it.path }.toSet()
            val combined = seed + picked.filter { it.path !in existing }
            // اللصق المباشر للإطارات لا يصحّ إلا لملفّات MP3.
            val bad = combined.filter { !UgcAudioMerger.isMp3(it.name) }
            if (bad.isNotEmpty()) {
                snackbarHostState.showSnackbar(
                    "لدمج عدة مقاطع يجب أن تكون كلّها MP3 — «${bad.first().name}» ليس كذلك.",
                )
                return@launch
            }
            val accepted = combined.take(UgcAudioMerger.MAX_FILES)
            if (accepted.size == 1) {
                val only = accepted.single()
                pickedFile = File(only.path)
                pickedName = only.name
                mergeItems.clear()
            } else {
                pickedFile = null
                pickedName = ""
                mergeItems.clear()
                mergeItems.addAll(accepted)
            }
            if (title.trim().isEmpty() && accepted.isNotEmpty()) {
                title = smartTitleFromFileName(accepted.first().name)
            }
            if (combined.size > UgcAudioMerger.MAX_FILES) {
                snackbarHostState.showSnackbar(
                    "الحدّ الأقصى ${UgcAudioMerger.MAX_FILES} مقاطع للمنشور الواحد.",
                )
            }
        }
    }

    val canPublish = run {
        when {
            title.trim().length < 3 -> false
            // لا شرط أقسام: النشر بلا قسم مسموح تماماً (قرار المالك).
            type == "article" -> articleBody.trim().length >= 30
            // مقاطع الدمج (اثنان فأكثر) طريق نشر صالح للصوت.
            mergeItems.size >= 2 -> true
            // عند تعديل ملف من النوع نفسه لا يلزم رفعه مرة أخرى. أمّا تغيير
            // النوع فيتطلب ملفاً جديداً حتى لا يصبح وصف المنشور مخالفاً للملف.
            else -> pickedFile != null || (isEditing && type == originalType)
        }
    }

    fun publish() {
        publishing = true
        progress = 0.0
        scope.launch {
            var mergedTemp: File? = null
            try {
                var uploadFile = pickedFile
                // مقاطع متعددة: تُدمج محلياً أولاً ثم يُرفع الناتج كملف واحد.
                if (mergeItems.size >= 2) {
                    merging = true
                    mergeProgress = 0.0
                    val timestamp = System.currentTimeMillis()
                    val snapshot = mergeItems.toList()
                    mergedTemp = withContext(Dispatchers.IO) {
                        UgcAudioMerger.mergeMp3(
                            inputs = snapshot.map { File(it.path) },
                            outputPath = File(
                                context.cacheDir,
                                "ugc_merged_$timestamp.mp3",
                            ).absolutePath,
                            maxTotalBytes = UgcService.MAX_FILE_SIZE_BYTES,
                            onProgress = { percent -> mergeProgress = percent },
                        )
                    }
                    merging = false
                    uploadFile = mergedTemp
                }

                if (editingPost != null) {
                    UgcService.updateMyPost(
                        context = context,
                        post = editingPost,
                        title = title,
                        contentType = type,
                        description = description,
                        articleBody = if (type == "article") articleBody else null,
                        replacementFile = uploadFile,
                        channelSectionId = section?.id ?: "",
                        channelSectionName = section?.name ?: "",
                        onProgress = { value -> progress = value },
                    )
                } else {
                    UgcService.publish(
                        context = context,
                        channel = currentChannel,
                        title = title,
                        contentType = type,
                        description = description,
                        file = uploadFile,
                        articleBody = if (type == "article") articleBody else null,
                        channelSectionId = section?.id ?: "",
                        channelSectionName = section?.name ?: "",
                        onProgress = { value -> progress = value },
                    )
                }
                if (successMessage != null) {
                    snackbarHostState.showSnackbar(
                        message = successMessage,
                        duration = SnackbarDuration.Short,
                    )
                }
                onDone()
            } catch (error: Throwable) {
                publishing = false
                merging = false
                val text = error.toString()
                snackbarHostState.showSnackbar(
                    when {
                        text.contains("ugc_file_too_large") ->
                            "حجم الملف أكبر من الحد المسموح (300 م.ب)."
                        text.contains("الحجم الكلي للدمج") ->
                            "حجم المقاطع بعد جمعها أكبر من الحد المسموح (300 م.ب)."
                        text.contains("ترميز صوتي متوافق") ->
                            "لا يمكن دمج هذه المقاطع مباشرة لاختلاف ترميزها الصوتي."
                        text.contains("MPEG") ->
                            "أحد المقاطع ليس ملف MP3 سليماً أو لا يحتوي صوتاً صالحاً."
                        else -> "تعذّر نشر المحتوى. تحقق من الاتصال وحاول مجدداً."
                    },
                )
            } finally {
                // تنظيف الملفّ المدموج المؤقّت بعد الرفع (نجاحاً أو فشلاً).
                mergedTemp?.let { temp -> runCatching { temp.delete() } }
            }
        }
    }

    BackHandler(enabled = !publishing, onBack = onClose)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "تعديل المنشور" else "نشر في المجتمع") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                        )
                    }
                },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .absorbPointer(publishing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // هويّة الناشر — تذكير دائم أنّ المحتوى يُنشر باسم قناته.
            Row(verticalAlignment = Alignment.CenterVertically) {
                UgcChannelAvatar(
                    name = currentChannel.name,
                    photoUrl = currentChannel.photoUrl,
                    radius = 16.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${if (isEditing) "تعدّل منشورك باسم" else "تنشر باسم"}: ${currentChannel.name}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.W600,
                    ),
                )
                TextButton(onClick = { showTerms = true }) { Text("اقرأ الشروط") }
            }
            Spacer(Modifier.height(12.dp))

            // نوع المحتوى.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in publishTypeOptions) {
                    FilterChip(
                        selected = type == option.key,
                        onClick = {
                            type = option.key
                            pickedFile = null
                            pickedName = ""
                            mergeItems.clear()
                        },
                        label = { Text(option.labelKey.ar) },
                        leadingIcon = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (type != "article") {
                OutlinedButton(
                    onClick = {
                        singleFilePicker.launch(pickerMimeTypes(type))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            pickedName.isNotEmpty() -> pickedName
                            isEditing && type == originalType -> "استبدال الملف (اختياري)"
                            isEditing -> "اختر الملف الجديد (مطلوب)"
                            else -> "اختر ملفاً"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 🔗 دمج عدة مقاطع صوتية في منشور واحد (MP3 فقط). يعمل كذلك
                // عند التعديل ليستطيع الناشر استبدال ملفه بالكامل.
                if (type == "audio") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { multiAudioPicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryAdd,
                            contentDescription = null,
                            tint = NebrasColors.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (mergeItems.isEmpty()) {
                                "أو ادمج عدة مقاطع MP3 في منشور واحد"
                            } else {
                                "إضافة مقاطع أخرى للدمج (${mergeItems.size})"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = NebrasColors.primary,
                        )
                    }
                    if (mergeItems.isNotEmpty()) {
                        MergeItemsList(
                            items = mergeItems,
                            onRemove = { index ->
                                mergeItems.removeAt(index)
                                if (mergeItems.size == 1) {
                                    val only = mergeItems.single()
                                    pickedFile = File(only.path)
                                    pickedName = only.name
                                    mergeItems.clear()
                                }
                            },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = articleBody,
                    onValueChange = { if (it.length <= 20000) articleBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("نص المقال") },
                    supportingText = { Text("${articleBody.length}/20000") },
                    minLines = 8,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 120) title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("عنوان المحتوى") },
                supportingText = { Text("${title.length}/120") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            if (type != "article") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1000) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("وصف مختصر (اختياري)") },
                    supportingText = { Text("${description.length}/1000") },
                    minLines = 3,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // 🗂️ قسم القناة — خاصّ بالناشر بالكامل (لا علاقة له بأقسام
            // نبراس الرسميّة). اختياريّ: «بدون قسم» مقبول تماماً.
            Text(
                text = "أقسام قناتك",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W700),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "اختر قسماً من أقسام قناتك أو انشر بدون قسم — الأمر لك بالكامل.",
                style = MaterialTheme.typography.bodySmall.let {
                    it.copy(color = NebrasColors.textMuted, lineHeight = it.fontSize * 1.5f)
                },
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = section == null,
                    onClick = { section = null },
                    label = { Text("بدون قسم") },
                )
                for (option in currentChannel.sections) {
                    FilterChip(
                        selected = section?.id == option.id,
                        onClick = { section = option },
                        label = { Text(option.name) },
                    )
                }
                AssistChip(
                    onClick = { showNewSection = true },
                    label = { Text("إضافة قسم") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            if (publishing) {
                if (merging || progress <= 0.0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (merging) {
                        "جارٍ دمج المقاطع… ${mergeProgress.roundToInt()}%"
                    } else {
                        "جارٍ الرفع… ${(progress * 100).roundToInt()}%"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { if (canPublish && !publishing) publish() },
                enabled = canPublish && !publishing,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(
                    imageVector = if (isEditing) {
                        Icons.Outlined.Save
                    } else {
                        Icons.Rounded.RocketLaunch
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isEditing) "حفظ التعديلات" else "نشر الآن")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ينشر المحتوى فوراً باسم قناتك. يمكن حذف المنشور أو القناة عند مخالفة شروط النشر.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.let {
                    it.copy(color = NebrasColors.textMuted, lineHeight = it.fontSize * 1.5f)
                },
            )
        }
    }

    if (showNewSection) {
        // إنشاء قسم قناة جديد أثناء النشر مباشرة (مثل قوائم YouTube).
        NewChannelSectionDialog(
            onDismiss = { showNewSection = false },
            onConfirm = { raw ->
                showNewSection = false
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@NewChannelSectionDialog
                scope.launch {
                    runCatching { UgcService.addChannelSection(trimmed) }
                        .onSuccess { (updated, created) ->
                            currentChannel = updated
                            section = created
                        }
                        .onFailure { notify("تعذّر حفظ القناة. حاول مجدداً.") }
                }
            },
        )
    }

    if (showTerms) {
        UgcTermsSheet(onResult = { showTerms = false })
    }
}

/** قائمة مقاطع الدمج قابلة لإعادة الترتيب بالسحب مع حذف كلّ مقطع. */
@Composable
private fun MergeItemsList(
    items: MutableList<UgcMergeItem>,
    onRemove: (Int) -> Unit,
) {
    val rowHeight = 52.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ترتيب المقاطع (اسحب لإعادة الترتيب) — تُدمج بهذا الترتيب:",
            style = MaterialTheme.typography.bodySmall.copy(color = NebrasColors.textMuted),
        )
        Spacer(Modifier.height(6.dp))
        for (index in items.indices) {
            val item = items[index]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(NebrasColors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NebrasColors.primary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = item.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                    )
                    // مقبض السحب — بديل `ReorderableListView` بلا أيّ تبعية.
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "إعادة الترتيب",
                        modifier = Modifier
                            .size(20.dp)
                            .pointerInput(items.size) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (draggingIndex >= 0) {
                                            dragOffset += amount.y
                                            while (
                                                dragOffset > rowHeightPx / 2 &&
                                                draggingIndex < items.size - 1
                                            ) {
                                                val moved = items.removeAt(draggingIndex)
                                                items.add(draggingIndex + 1, moved)
                                                draggingIndex += 1
                                                dragOffset -= rowHeightPx
                                            }
                                            while (
                                                dragOffset < -rowHeightPx / 2 &&
                                                draggingIndex > 0
                                            ) {
                                                val moved = items.removeAt(draggingIndex)
                                                items.add(draggingIndex - 1, moved)
                                                draggingIndex -= 1
                                                dragOffset += rowHeightPx
                                            }
                                        }
                                    },
                                )
                            },
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NebrasColors.error,
                        )
                    }
                }
            }
        }
        if (items.size == 1) {
            Text(
                text = "أضف مقطعاً آخر على الأقل ليُدمجا، أو استخدم «اختر ملفاً» لمقطع واحد.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(color = NebrasColors.warning),
            )
        }
    }
}

// ── أدوات ونماذج مساعدة ───────────────────────────────────────────────

/** مقطع ضمن قائمة الدمج (مسار محلّيّ + اسم للعرض). */
internal data class UgcMergeItem(val path: String, val name: String)

/** ملفّ منسوخ إلى الكاش مع اسمه الأصليّ. */
internal data class UgcPickedFile(val file: File, val name: String)

private data class PublishTypeOption(
    val key: String,
    val labelKey: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val publishTypeOptions = listOf(
    PublishTypeOption("audio", "ugc_type_audio", Icons.Rounded.Headphones),
    PublishTypeOption("video", "ugc_type_video", Icons.Filled.PlayCircleOutline),
    PublishTypeOption("document", "ugc_type_book", Icons.Rounded.MenuBook),
    PublishTypeOption("article", "ugc_type_article", Icons.Outlined.Article),
)

private fun pickerMimeTypes(type: String): Array<String> = when (type) {
    "audio" -> arrayOf("audio/*")
    "video" -> arrayOf("video/*")
    else -> arrayOf("application/pdf")
}

/** نوع النشر المكافئ لنوع منشور قائم — نقل `_typeFor`. */
internal fun storedTypeForPost(post: UgcPost): String = when (post.content.type) {
    ContentType.AUDIO -> "audio"
    ContentType.VIDEO -> "video"
    ContentType.ARTICLE -> "article"
    ContentType.BOOK, ContentType.YOUTUBE, ContentType.IMAGE -> "document"
}

/** استنتاج النوع من ملفّ وصل بالمشاركة — نقل `_typeFromSharedFile`. */
internal fun typeFromSharedFile(name: String, mime: String): String {
    val lowerName = name.lowercase()
    val lowerMime = mime.lowercase()
    if (
        lowerMime.startsWith("audio/") ||
        lowerName.endsWith(".mp3") ||
        lowerName.endsWith(".m4a") ||
        lowerName.endsWith(".wav")
    ) {
        return "audio"
    }
    if (
        lowerMime.startsWith("video/") ||
        lowerName.endsWith(".mp4") ||
        lowerName.endsWith(".mkv") ||
        lowerName.endsWith(".webm")
    ) {
        return "video"
    }
    if (lowerMime == "text/plain" || lowerName.endsWith(".txt")) return "article"
    return "document"
}

/**
 * ينسخ الـ `content://` المختار إلى ملفّ في الكاش محتفظاً باسمه الأصليّ
 * (الإذن الممنوح للـ Uri قد يُلغى بعد لحظات، ولا مسار حقيقيّاً له).
 */
internal fun copyPickedUriToCache(context: Context, uri: Uri): UgcPickedFile? = runCatching {
    val resolver = context.contentResolver
    val name = queryPickedName(resolver, uri) ?: "ملف"
    val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "ملف" }
    val folder = File(context.cacheDir, "ugc_picks/${System.nanoTime()}")
    if (!folder.exists() && !folder.mkdirs()) return@runCatching null
    val target = File(folder, safeName)
    resolver.openInputStream(uri)?.use { input ->
        target.outputStream().use(input::copyTo)
    } ?: return@runCatching null
    if (!target.exists() || target.length() == 0L) return@runCatching null
    UgcPickedFile(target, name)
}.getOrNull()

private fun queryPickedName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }
}.getOrNull()

/** يبتلع كلّ اللمسات أثناء الرفع — نظير `AbsorbPointer` في Flutter. */
private fun Modifier.absorbPointer(absorbing: Boolean): Modifier =
    if (!absorbing) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    }

/**
 * 🔗 دمج عدة ملفات MP3 في ملف واحد بلا إعادة ترميز (Kotlin خالص، بلا ffmpeg)
 * — نقل `core/utils/audio_merge.dart`.
 *
 * الفكرة: ملف MP3 تيّار إطارات MPEG مستقلّة، فيكفي قصّ الأجزاء غير الصوتية
 * من كل ملف — وسم ID3v2 في البداية، وسم ID3v1 في النهاية، وإطار
 * Xing/Info/VBRI الوصفي (يصف ملفاً واحداً فقط، وإبقاؤه يجعل المدة الظاهرة
 * بعد الدمج خاطئة) — ثم لصق الإطارات تباعاً بالترتيب المطلوب.
 */
internal object UgcAudioMerger {

    /** الحد الأقصى لعدد الملفات القابلة للدمج في منشور واحد. */
    const val MAX_FILES = 10

    fun isMp3(fileName: String): Boolean =
        fileName.lowercase().trim().endsWith(".mp3")

    /**
     * يدمج [inputs] بترتيبها في ملف جديد على [outputPath] ويعيده.
     * يرمي استثناءً إن لم يُعثر على صوت MPEG داخل أحد الملفات.
     */
    fun mergeMp3(
        inputs: List<File>,
        outputPath: String,
        maxTotalBytes: Long? = null,
        onProgress: ((Double) -> Unit)? = null,
    ): File {
        require(inputs.isNotEmpty()) { "لا توجد ملفات للدمج" }
        require(inputs.size <= MAX_FILES) { "الحد الأقصى $MAX_FILES ملفات" }
        if (maxTotalBytes != null) {
            var totalBytes = 0L
            for (input in inputs) totalBytes += input.length()
            if (totalBytes > maxTotalBytes) {
                throw IllegalStateException("الحجم الكلي للدمج كبير جداً")
            }
        }
        val out = File(outputPath)
        var streamSpec: Int? = null
        try {
            out.outputStream().use { sink ->
                for ((index, input) in inputs.withIndex()) {
                    val bytes = input.readBytes()
                    val frames = audioFramesOnly(bytes)
                    if (streamSpec == null) streamSpec = frames.spec
                    if (frames.spec != streamSpec) {
                        throw IllegalStateException(
                            "ملفات MP3 المختارة ليست بترميز صوتي متوافق",
                        )
                    }
                    sink.write(bytes, frames.start, frames.end - frames.start)
                    onProgress?.invoke((index + 1).toDouble() / inputs.size * 100)
                }
                sink.flush()
            }
        } catch (error: Throwable) {
            runCatching { if (out.exists()) out.delete() }
            throw error
        }
        return out
    }

    /** يقصّ الوسوم ويعيد نطاق الإطارات الصوتية فقط. */
    private fun audioFramesOnly(b: ByteArray): Mp3Frames {
        fun u(i: Int): Int = b[i].toInt() and 0xFF

        var start = 0
        var end = b.size

        // ID3v2: "ID3" + إصدار(2) + أعلام(1) + حجم syncsafe(4)
        if (end >= 10 && u(0) == 0x49 && u(1) == 0x44 && u(2) == 0x33) {
            val size = ((u(6) and 0x7F) shl 21) or
                ((u(7) and 0x7F) shl 14) or
                ((u(8) and 0x7F) shl 7) or
                (u(9) and 0x7F)
            var s = 10 + size
            if ((u(5) and 0x10) != 0) s += 10 // ذيل ID3v2 الاختياري
            if (s < end) start = s
        }

        // ID3v1: "TAG" في آخر 128 بايت
        if (
            end - start > 128 &&
            u(end - 128) == 0x54 &&
            u(end - 127) == 0x41 &&
            u(end - 126) == 0x47
        ) {
            end -= 128
        }

        // أول إطار MPEG صالح (نتحقق أن ما يليه إطار صالح أيضاً لتفادي
        // بايتات صدفة تشبه رأس إطار داخل بيانات عشوائية).
        var p = start
        while (p + 4 <= end) {
            if (u(p) == 0xFF && (u(p + 1) and 0xE0) == 0xE0) {
                val len = frameLength(b, p)
                if (len > 0) {
                    val next = p + len
                    if (next + 4 > end || (u(next) == 0xFF && (u(next + 1) and 0xE0) == 0xE0)) {
                        break
                    }
                }
            }
            p++
        }
        if (p + 4 > end) throw IllegalStateException("لم يُعثر على صوت MPEG في الملف")
        start = p

        // إطار Xing/Info/VBRI الوصفي — يُحذف كي لا تظهر مدة خاطئة بعد الدمج.
        val firstLen = frameLength(b, start)
        if (firstLen > 0 && start + firstLen <= end) {
            if (hasVbrMarker(b, start, start + firstLen)) start += firstLen
        }

        if (start + 4 > end || frameLength(b, start) <= 0) {
            throw IllegalStateException("لم يُعثر على صوت MPEG صالح بعد الوسوم")
        }
        val h1 = u(start + 1)
        val h2 = u(start + 2)
        val h3 = u(start + 3)
        // اختلاف إصدار MPEG أو الطبقة أو معدل العينة أو نمط القنوات يجعل
        // اللصق الخام غير موثوق على بعض مشغلات Android.
        val spec = (((h1 shr 3) and 0x03) shl 8) or
            (((h1 shr 1) and 0x03) shl 6) or
            (((h2 shr 2) and 0x03) shl 4) or
            ((h3 shr 6) and 0x03)
        return Mp3Frames(start, end, spec)
    }

    /** هل يحوي النطاق علامة "Xing" أو "Info" أو "VBRI"؟ */
    private fun hasVbrMarker(b: ByteArray, from: Int, to: Int): Boolean {
        fun u(i: Int): Int = b[i].toInt() and 0xFF
        var i = from
        while (i + 4 <= to) {
            val c = u(i)
            if (c == 0x58 && u(i + 1) == 0x69 && u(i + 2) == 0x6E && u(i + 3) == 0x67) {
                return true // Xing
            }
            if (c == 0x49 && u(i + 1) == 0x6E && u(i + 2) == 0x66 && u(i + 3) == 0x6F) {
                return true // Info
            }
            if (c == 0x56 && u(i + 1) == 0x42 && u(i + 2) == 0x52 && u(i + 3) == 0x49) {
                return true // VBRI
            }
            i++
        }
        return false
    }

    /** طول إطار MPEG بالبايتات انطلاقاً من رأسه في [p]، أو -1 إن كان فاسداً. */
    private fun frameLength(b: ByteArray, p: Int): Int {
        if (p + 4 > b.size) return -1
        val h1 = b[p + 1].toInt() and 0xFF
        val h2 = b[p + 2].toInt() and 0xFF
        val version = (h1 shr 3) and 0x03 // 0=MPEG2.5 1=محجوز 2=MPEG2 3=MPEG1
        val layer = (h1 shr 1) and 0x03 // 1=III 2=II 3=I
        val brIdx = (h2 shr 4) and 0x0F
        val srIdx = (h2 shr 2) and 0x03
        val padding = (h2 shr 1) and 0x01
        if (version == 1 || layer == 0 || brIdx == 0 || brIdx == 15 || srIdx == 3) return -1

        val base = SAMPLE_RATES_V1[srIdx]
        val sampleRate = when (version) {
            3 -> base
            2 -> base / 2
            else -> base / 4
        }

        val isV1 = version == 3
        val table = when (layer) {
            3 -> if (isV1) BITRATES_V1_L1 else BITRATES_V2_L1 // Layer I
            2 -> if (isV1) BITRATES_V1_L2 else BITRATES_V2_L23 // Layer II
            else -> if (isV1) BITRATES_V1_L3 else BITRATES_V2_L23 // Layer III
        }
        val bitrate = table[brIdx] * 1000
        if (bitrate == 0) return -1

        if (layer == 3) {
            // Layer I
            return (12 * bitrate / sampleRate + padding) * 4
        }
        // Layer II دائماً 144؛ Layer III: للـ MPEG2/2.5 المعامل 72
        val coef = if (layer == 1 && !isV1) 72 else 144
        return coef * bitrate / sampleRate + padding
    }

    private data class Mp3Frames(val start: Int, val end: Int, val spec: Int)

    private val SAMPLE_RATES_V1 = intArrayOf(44100, 48000, 32000)

    private val BITRATES_V1_L1 = intArrayOf(
        0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448,
    )
    private val BITRATES_V1_L2 = intArrayOf(
        0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384,
    )
    private val BITRATES_V1_L3 = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320,
    )
    private val BITRATES_V2_L1 = intArrayOf(
        0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256,
    )
    private val BITRATES_V2_L23 = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160,
    )
}
