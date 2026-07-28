package com.nebras.mobile.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.util.ar

/**
 * 📜 شروط النشر في «مجتمع نبراس».
 *
 * تُعرض إلزاميّاً قبل إنشاء القناة (مع زرّ موافقة صريح تُسجَّل لحظته في
 * وثيقة القناة)، وتبقى متاحة من شاشة النشر. مطلب مباشر من سياسة
 * المحتوى المُنشأ من المستخدمين في Google Play + قرار المالك: من يخالف
 * يُحذف محتواه، وقناته عرضة للحذف أيضاً.
 *
 * [onResult] يعيد `true` إذا وافق المستخدم (وضع الموافقة)، و`false` في كلّ
 * حالات الإغلاق الأخرى — نظير `Future<bool>` في نسخة Flutter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UgcTermsSheet(
    onResult: (Boolean) -> Unit,
    requireAccept: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onResult(false) },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.95f)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Gavel,
                        contentDescription = null,
                        tint = NebrasColors.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "شروط النشر والخصوصية",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.W800,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "قبل النشر، تأكد أنك تملك حق مشاركة المحتوى وأنه مناسب لمجتمع نبراس.",
                    style = MaterialTheme.typography.bodyMedium.let {
                        it.copy(lineHeight = it.fontSize * 1.6f)
                    },
                )
                Spacer(Modifier.height(12.dp))
                for (i in 1..8) {
                    TermRow(index = i)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            NebrasColors.warning.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = "المحتوى المخالف قد يُحذف بلا إشعار، وقد تُوقَف القناة عند تكرار المخالفة.",
                        style = MaterialTheme.typography.bodyMedium.let {
                            it.copy(lineHeight = it.fontSize * 1.6f, fontWeight = FontWeight.W600)
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 12.dp),
            ) {
                if (requireAccept) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onResult(false) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("إلغاء")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onResult(true) },
                            modifier = Modifier.weight(2f),
                        ) {
                            Text("أوافق وأتابع")
                        }
                    }
                } else {
                    Button(
                        onClick = { onResult(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}

/** بند مرقَّم من بنود الشروط — نصّه من `ArStrings` بمفتاح `ugc_terms_pN`. */
@Composable
private fun TermRow(index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(NebrasColors.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NebrasColors.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "ugc_terms_p$index".ar,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.let {
                it.copy(lineHeight = it.fontSize * 1.6f)
            },
        )
    }
}
