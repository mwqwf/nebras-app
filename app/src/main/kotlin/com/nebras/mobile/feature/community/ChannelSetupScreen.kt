package com.nebras.mobile.feature.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.util.ar
import kotlinx.coroutines.launch

/**
 * 📡 إنشاء/تعديل قناة الناشر — أوّل خطوة قبل النشر (مثل إنشاء قناة
 * YouTube). المستخدم يسمّي قناته بحرّيّة («الحسّاني»، اسمه الشخصيّ…).
 *
 * قرار المالك: القناة **مستقلّة تماماً عن أقسام نبراس** — لا يختار الناشر
 * شيئاً من شجرة التطبيق؛ إن أراد تنظيم محتواه أنشأ أقساماً خاصّة به
 * بأسمائه هو (اختياريّ كلياً — النشر بلا أقسام مسموح).
 *
 * تُعرض داخل شاشة أخرى (نشر/قناة) بدل مسار مستقلّ، فتُعيد نتيجتها عبر
 * [onSaved] بدل `Navigator.pop(channel)`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChannelSetupScreen(
    onCancel: () -> Unit,
    onSaved: (UgcChannel) -> Unit,
    existing: UgcChannel? = null,
) {
    val isNew = existing == null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var bio by remember { mutableStateOf(existing?.bio ?: "") }
    val sections = remember {
        mutableStateListOf<ChannelSection>().apply { addAll(existing?.sections ?: emptyList()) }
    }
    var saving by remember { mutableStateOf(false) }
    var showAddSection by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var termsRequireAccept by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun doSave(acceptedNow: Boolean) {
        saving = true
        scope.launch {
            runCatching {
                UgcService.upsertMyChannel(
                    name = name.trim(),
                    bio = bio,
                    sections = sections.toList(),
                    acceptedTerms = acceptedNow,
                )
            }.onSuccess { channel ->
                onSaved(channel)
            }.onFailure {
                saving = false
                notify("تعذّر حفظ القناة. حاول مجدداً.")
            }
        }
    }

    fun requestSave() {
        if (name.trim().length < 3) {
            notify("اكتب اسماً للقناة من 3 أحرف على الأقل.")
            return
        }
        // الموافقة على شروط النشر إلزاميّة عند الإنشاء الأوّل.
        if (isNew || existing?.termsAcceptedAt == null) {
            termsRequireAccept = true
            showTerms = true
            return
        }
        doSave(false)
    }

    BackHandler(onBack = onCancel)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "أنشئ قناتك" else "تعديل القناة") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "ugc_create_channel_intro".ar,
                style = MaterialTheme.typography.bodyMedium.let {
                    it.copy(lineHeight = it.fontSize * 1.6f)
                },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم القناة") },
                placeholder = { Text("مثل: الحسّاني أو اسمك") },
                leadingIcon = {
                    Icon(Icons.Outlined.Campaign, contentDescription = null)
                },
                supportingText = { Text("${name.length}/50") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 200) bio = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("نبذة عن القناة (اختياري)") },
                leadingIcon = {
                    Icon(Icons.Rounded.Notes, contentDescription = null)
                },
                supportingText = { Text("${bio.length}/200") },
                shape = RoundedCornerShape(12.dp),
                maxLines = 2,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "أقسام قناتك",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W700),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "نظّم قناتك بأقسام تسمّيها بنفسك (اختياري تماماً) — لا علاقة لها بأقسام نبراس الرسمية.",
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
                for (section in sections.toList()) {
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(section.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { sections.remove(section) },
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = { showAddSection = true },
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
            TextButton(
                onClick = {
                    termsRequireAccept = false
                    showTerms = true
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gavel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("شروط النشر والخصوصية")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (!saving) requestSave() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = (if (isNew) "ugc_create_channel_cta" else "ugc_save_channel_cta").ar,
                )
            }
        }
    }

    if (showAddSection) {
        NewChannelSectionDialog(
            onDismiss = { showAddSection = false },
            onConfirm = { raw ->
                showAddSection = false
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@NewChannelSectionDialog
                if (sections.any { it.name == trimmed }) return@NewChannelSectionDialog
                if (sections.size >= UgcService.MAX_CHANNEL_SECTIONS) {
                    return@NewChannelSectionDialog
                }
                sections.add(
                    ChannelSection(
                        id = "cs_${System.currentTimeMillis()}",
                        name = trimmed,
                    ),
                )
            },
        )
    }

    if (showTerms) {
        UgcTermsSheet(
            requireAccept = termsRequireAccept,
            onResult = { accepted ->
                val needsAccept = termsRequireAccept
                showTerms = false
                if (needsAccept && accepted) doSave(true)
            },
        )
    }
}

/** حوار «قسم جديد في قناتك» — مشترك بين إعداد القناة وشاشة النشر. */
@Composable
internal fun NewChannelSectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قسم جديد في قناتك") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= 60) value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم القسم") },
                supportingText = { Text("${value.length}/60") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text("إضافة قسم") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
