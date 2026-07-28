package com.nebras.mobile.feature.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.RecommendationEngine
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.core.widget.HorizontalContentList
import com.nebras.mobile.feature.home.model.HomeSection

/**
 * نقل `core/widgets/related_content_list.dart` — قسم «قد يعجبك أيضاً».
 *
 * ── الهدف ──
 * يُدمج في أيّ شاشة عرض محتوى (فيديو/صوت/كتاب/صورة/مقال)، فيُظهر للمستخدم
 * قائمة أفقيّة من المحتوى المشابه ليبقى داخل التطبيق ويستكشف أكثر.
 *
 * ── مصدر التوصيات ──
 * أقسام الرئيسية ([sections]) تمرّ عبر [RecommendationEngine] بالخوارزميّة
 * الهجينة 50/50: نصف من نفس القسم والنصف الآخر من كلمات مفتاحيّة متشابهة.
 * في Flutter كان الويدجت يقرأ `HomeProvider` بنفسه؛ هنا نمرّرها صراحةً
 * لأنّ الشاشات تملك `HomeViewModel` أصلاً (ولا حاجة لالتقاط الحالة أثناء
 * البناء ثمّ تأجيلها لما بعد الإطار كما كان يفعل الأصل).
 *
 * ملاحظة: تسجيل «فتح المحتوى» في `UserBehaviorTracker`/`InterestProfileService`
 * يتمّ في `ContentRouter.open` — فلا نُكرّره هنا (كما في نسخة Flutter حيث
 * كان التسجيل في الامتداد `relatedList()` لا في الويدجت نفسه).
 */
@Composable
fun RelatedContentList(
    reference: Content,
    sections: List<HomeSection>,
    onItemTap: (Content) -> Unit,
    modifier: Modifier = Modifier,
    /** عنوان القسم كما يظهر. افتراضيًّا «قد يعجبك أيضًا». */
    titleOverride: String? = null,
    /** ارتفاع القائمة الأفقيّة — يناسب التصميم الحاليّ. */
    height: Dp = 190.dp,
    /** الحدّ الأقصى لعدد التوصيات المعروضة. */
    maxItems: Int = 12,
) {
    val merged = remember(reference.id, sections, maxItems) {
        RecommendationEngine.getRelatedContent(
            reference = reference,
            pool = sections,
            limit = maxItems,
        ).take(maxItems)
    }
    // لا نُلحق بعد الخليط الهجين أيّ شيء إضافيّ — وإن كان فارغاً لا نرسم شيئاً.
    if (merged.isEmpty()) return

    val title = titleOverride ?: "قد يعجبك أيضًا"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Recommend,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = NebrasTextStyles.heading3,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalContentList(
            items = merged,
            onItemTap = onItemTap,
            height = height,
            contentPadding = PaddingValues(horizontal = 24.dp),
        )
    }
}
