package com.nebras.mobile.feature.content

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NoAdultContent
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.ReportGmailerrorred
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.ContentReportService
import com.nebras.mobile.core.service.ContentSuggestionService
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.core.service.ReportReason
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.util.ar
import kotlinx.coroutines.launch

/**
 * نقل `core/widgets/report_content_button.dart` + `suggest_correction_button.dart`.
 *
 * الزرّان صغيران ويُدمجان أسفل شاشات المحتوى، لذلك لا يملكان `Scaffold`
 * خاصّاً بهما: رسائل الحالة تُعرض بـ `Toast` بدل `ScaffoldMessenger`
 * (نفس الدور: رسالة عابرة لا تحتاج تركيب مضيف في كلّ شاشة مُستدعية).
 */

// ── زرّ الإبلاغ ─────────────────────────────────────────────────────

/**
 * زرّ «إبلاغ» أنيق بنمط YouTube: أيقونة علَم صغيرة عند الضغط عليها تفتح
 * ورقة سفليّة بخيارات تصنيف البلاغ (ملكية فكرية، جنسيّ، أطفال، عنف،
 * إرهاب، أخرى). عند الإرسال يُكتب البلاغ في `content_reports` ليظهر
 * للمالك في لوحة الإشراف.
 *
 * [onReported] بديل `navigator.maybePop()` في Dart — تُستدعى بعد إغلاق
 * حوار التأكيد كي تُغادر الشاشة المُستدعية (المحتوى لم يَعُد يُعرَض له).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContentButton(
    content: Content,
    modifier: Modifier = Modifier,
    onReported: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var sheetVisible by remember { mutableStateOf(false) }
    var thanksVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val submit: (ReportReason) -> Unit = { reason ->
        scope.launch {
            runCatching {
                ContentReportService.submit(content = content, reason = reason)
                // احترامًا لرأي المُبلِّغ: نُخفي المحتوى عنه فوراً في كلّ
                // الشاشات، سواءٌ ثبتت مخالفته (يُحذف للجميع لاحقاً) أو لم تثبت.
                HiddenContentService.hide(content.id)
            }.onSuccess {
                thanksVisible = true
            }.onFailure {
                Toast.makeText(
                    context,
                    "تعذّر إرسال البلاغ. حاول مجدّداً.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    TextButton(
        onClick = { sheetVisible = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            Icons.Rounded.OutlinedFlag,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text("إبلاغ", style = NebrasTextStyles.smallText.copy(color = onSurfaceVariant))
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "الإبلاغ عن هذا المحتوى",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = NebrasTextStyles.heading3,
                )
                for (reason in ReportReason.values()) {
                    ListItem(
                        modifier = Modifier.clickable {
                            sheetVisible = false
                            submit(reason)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(reportReasonIcon(reason), contentDescription = null)
                        },
                        headlineContent = { Text(reason.labelKey.ar) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (thanksVisible) {
        // إشعار صريح بسياسة المراجعة خلال 24 ساعة.
        val dismiss: () -> Unit = {
            thanksVisible = false
            // نغادر شاشة التفاصيل لأنّ المحتوى لم يَعُد يُعرَض لهذا المستخدم.
            onReported?.invoke()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("تمّ استلام بلاغك") },
            text = {
                Text(
                    "شكراً لك. سنراجع البلاغ يدوياً، وإذا ثبتت مخالفة المحتوى فسنزيله " +
                        "نهائيّاً من قاعدة بياناتنا خلال 24 ساعة كحدّ أقصى. وفي جميع " +
                        "الأحوال، لن يظهر لك هذا المحتوى بعد الآن.",
                )
            },
            confirmButton = {
                TextButton(onClick = dismiss) { Text("حسناً") }
            },
        )
    }
}

/** أيقونة كلّ سبب بلاغ — نقل `_iconFor`. */
private fun reportReasonIcon(reason: ReportReason): ImageVector = when (reason) {
    ReportReason.COPYRIGHT -> Icons.Rounded.Copyright
    ReportReason.SEXUAL -> Icons.Rounded.NoAdultContent
    ReportReason.CHILD_SAFETY -> Icons.Rounded.ChildCare
    ReportReason.VIOLENCE -> Icons.Rounded.ReportGmailerrorred
    ReportReason.TERRORISM -> Icons.Rounded.Dangerous
    ReportReason.OTHER -> Icons.Rounded.MoreHoriz
}

// ── زرّ اقتراح التصحيح ──────────────────────────────────────────────

/**
 * زرّ «اقترح تصحيحاً» ✏️ بجوار زرّ الإبلاغ: عنوان خاطئ، ملفّ لا يعمل،
 * تصنيف غير مناسب… يصل الاقتراح لمشرفي نبراس في دردشتهم خلال دقائق.
 */
@Composable
fun SuggestCorrectionButton(
    content: Content,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var dialogVisible by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    TextButton(
        onClick = {
            note = ""
            dialogVisible = true
        },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            Icons.Rounded.EditNote,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "اقترح تصحيحاً",
            style = NebrasTextStyles.smallText.copy(color = onSurfaceVariant),
        )
    }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text("اقترح تصحيحاً ✏️") },
            text = {
                OutlinedTextField(
                    value = note,
                    // maxLength: 1000 في Flutter — نقصّ الإدخال عند الحدّ نفسه.
                    onValueChange = { if (it.length <= 1000) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("عنوان خاطئ؟ ملف لا يعمل؟ تصنيف غير مناسب؟ اكتب ملاحظتك…")
                    },
                    minLines = 2,
                    maxLines = 4,
                    supportingText = { Text("${note.length}/1000") },
                )
            },
            dismissButton = {
                TextButton(onClick = { dialogVisible = false }) { Text("إلغاء") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = note.trim()
                        dialogVisible = false
                        if (trimmed.isEmpty()) return@Button
                        scope.launch {
                            runCatching {
                                ContentSuggestionService.submit(content = content, note = trimmed)
                            }.onSuccess {
                                Toast.makeText(
                                    context,
                                    "شكراً! وصل اقتراحك للمشرفين 💚",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "تعذّر الإرسال — حاول مجدداً.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text("إرسال")
                }
            },
        )
    }
}
