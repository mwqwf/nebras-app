package com.nebras.mobile.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.theme.NebrasColors
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.BottomSheetHandle
import kotlinx.coroutines.launch

/**
 * ورقة سفليّة لفلاتر البحث — نقل
 * `features/search/view/widget/search_filter_bottom_sheet.dart`:
 * نوع المحتوى، القسم، مسح الفلاتر، ثمّ زرّ التطبيق.
 *
 * ملاحظة نقل: قائمة الأقسام تأتي من `SearchViewModel.loadSections()` التي
 * لا يستدعيها أحد في نسخة Flutter أيضاً، فيبقى قسم «القسم» مخفيّاً ما لم
 * تُحمَّل الخيارات — نفس سلوك الأصل حرفياً.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    state: SearchUiState,
    onTypeSelected: (ContentType?) -> Unit,
    onSectionSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    // `isScrollControlled: true` في Dart ⇒ نسمح بالارتفاع الكامل مباشرةً.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // إغلاق مؤنسَن ثمّ إزالة الورقة — مقابل `Navigator.pop(context)`.
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
        ) {
            // مقبض الورقة
            BottomSheetHandle(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            // العنوان + مسح الكل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("التصفية", style = NebrasTextStyles.heading3)
                if (state.hasActiveFilters) {
                    TextButton(
                        onClick = {
                            onClearFilters()
                            close()
                        },
                    ) {
                        Text(
                            text = "مسح الكل",
                            color = NebrasColors.error,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // نوع المحتوى
            Text("نوع المحتوى", style = NebrasTextStyles.bodyBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilterChip(
                    label = "الكل",
                    isSelected = state.selectedType == null,
                    onClick = { onTypeSelected(null) },
                )
                SearchFilterChip(
                    label = "فيديو",
                    isSelected = state.selectedType == ContentType.VIDEO,
                    onClick = { onTypeSelected(ContentType.VIDEO) },
                )
                SearchFilterChip(
                    label = "صوت",
                    isSelected = state.selectedType == ContentType.AUDIO,
                    onClick = { onTypeSelected(ContentType.AUDIO) },
                )
                SearchFilterChip(
                    label = "كتاب",
                    isSelected = state.selectedType == ContentType.BOOK,
                    onClick = { onTypeSelected(ContentType.BOOK) },
                )
            }
            Spacer(Modifier.height(16.dp))

            // الأقسام
            if (state.sections.isNotEmpty()) {
                Text("القسم", style = NebrasTextStyles.bodyBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchFilterChip(
                        label = "الكل",
                        isSelected = state.selectedSection == null,
                        onClick = { onSectionSelected(null) },
                    )
                    state.sections.forEach { section ->
                        SearchFilterChip(
                            label = section.name,
                            isSelected = state.selectedSection == section.id,
                            onClick = { onSectionSelected(section.id) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // زرّ التطبيق
            Button(
                onClick = close,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NebrasColors.primary,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text("تطبيق")
            }
        }
    }
}

/** رقاقة فلتر واحدة — نفس ألوان وحدود `FilterChip` في نسخة Flutter. */
@Composable
private fun SearchFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = primary.copy(alpha = 0.2f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = primary,
                )
            }
        } else {
            null
        },
    )
}
