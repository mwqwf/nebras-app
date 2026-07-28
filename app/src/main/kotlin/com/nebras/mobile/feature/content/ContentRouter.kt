package com.nebras.mobile.feature.content

import androidx.navigation.NavController
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.InterestProfileService
import com.nebras.mobile.core.service.UserBehaviorTracker
import com.nebras.mobile.feature.home.model.HomeSection
import com.nebras.mobile.ui.Routes

/**
 * موجّه فتح المحتوى — نقل `content/view/content_router.dart.dart` (فتح
 * العنصر بحسب نوعه) و`core/services/section_router.dart` (فتح الأقسام
 * بالتسلسل الهرميّ). **المدخل الوحيد** الذي يعرف قواعد التفرّع.
 */
object ContentRouter {

    /** يفتح عنصر محتوى في شاشته الصحيحة بحسب النوع. */
    fun open(navController: NavController, content: Content) {
        // تسجيل الإشارات الصامتة مركزيّاً — يعمل لكلّ مصادر التنقّل.
        UserBehaviorTracker.recordContentOpen(content)
        InterestProfileService.onContentConsumed(content)

        when (content.type) {
            ContentType.BOOK -> navController.navigate(Routes.reader(content.id))
            ContentType.AUDIO -> navController.navigate(Routes.audioPlayer(content.id))
            ContentType.VIDEO -> navController.navigate(Routes.videoPlayer(content.id))
            // 🚫 YouTube مقطوع — لو تسلّل عنصر بهذا النوع فلن يمرّ حارس
            // الامتثال أصلاً؛ الدفاع الأخير هنا يتجاهله بصمت.
            ContentType.YOUTUBE -> Unit
            // المقالات والصور تُعرض في القارئ (نصّ/صورة).
            ContentType.ARTICLE, ContentType.IMAGE ->
                navController.navigate(Routes.reader(content.id))
        }
    }

    /** يفتح القسم المناسب بحسب نوعه — منطق `SectionRouter.open` حرفياً. */
    fun openSection(
        navController: NavController,
        section: HomeSection,
        allSections: List<HomeSection>,
    ) {
        UserBehaviorTracker.recordSectionVisit(section.id, section.title)
        InterestProfileService.onSectionView(section.id, section.title)

        when (section.type) {
            "main_section" -> {
                // اختصار ذكيّ: قسم رئيسيّ بلا أبناء لكن مع محتوى مباشر →
                // قائمة المحتوى.
                val hasSubs = allSections.any {
                    it.type == "sub_section" && it.parentId == section.id
                }
                if (!hasSubs && section.items.isNotEmpty()) {
                    navController.navigate(Routes.contentList(section.id))
                    return
                }
                navController.navigate(Routes.subSections(section.id))
            }
            "sub_section" -> {
                // قسم فرعيّ بلا ثانويّات لكن مع محتوى مباشر → قائمة المحتوى.
                val hasSecs = allSections.any {
                    (it.type == "secondary_section" || it.type == "secondary_sub_section") &&
                        it.parentId == section.id
                }
                if (!hasSecs && section.items.isNotEmpty()) {
                    navController.navigate(Routes.contentList(section.id))
                    return
                }
                navController.navigate(Routes.secondarySections(section.id))
            }
            else -> navController.navigate(Routes.contentList(section.id))
        }
    }

    /**
     * يحلّ القسم الأوراق (الأكثر تحديداً) الذي ينتمي إليه [content] —
     * يُرجَّح الثانويّ إن وُجد وإلّا الفرعيّ. null إن تعذّر الحلّ.
     */
    fun resolveLeafSection(content: Content, sections: List<HomeSection>): HomeSection? {
        fun byId(id: String): HomeSection? = sections.firstOrNull { it.id == id }

        val secId = content.subSection?.trim().orEmpty()
        val subId = content.section.trim()
        var leaf: HomeSection? = null
        if (secId.isNotEmpty()) leaf = byId("secondary:$secId")
        if (leaf == null && subId.isNotEmpty()) leaf = byId("sub:$subId")
        return leaf
    }

    /**
     * يفتح قسم المحتوى مع **مسار صعود كامل** في مكدّس التنقّل: «رجوع» يصعد
     * تدريجيّاً (محتوى القسم → الثانويّة → الفرعيّة → الرئيسيّة) بدل القفز
     * مباشرةً للخلف — سلوك «زرّ القسم» في شاشات المحتوى.
     */
    fun openContentTrail(
        navController: NavController,
        content: Content,
        sections: List<HomeSection>,
    ) {
        val leaf = resolveLeafSection(content, sections) ?: return

        fun byId(id: String): HomeSection? = sections.firstOrNull { it.id == id }

        // بناء سلسلة الأجداد من الورقة صعوداً إلى الجذر.
        val chain = mutableListOf<HomeSection>()
        val guard = mutableSetOf<String>()
        var cur: HomeSection? = leaf
        while (cur != null && guard.add(cur.id)) {
            chain.add(cur)
            val pid = cur.parentId
            cur = if (pid.isNullOrEmpty()) null else byId(pid)
        }
        // من الجذر إلى الورقة كي يُبنى المكدّس بالترتيب الصحيح للصعود.
        val ordered = chain.reversed()

        UserBehaviorTracker.recordSectionVisit(leaf.id, leaf.title)
        InterestProfileService.onSectionView(leaf.id, leaf.title)

        // نُعيد المكدّس إلى الجذر (الرئيسيّة) قبل بناء مسار الأقسام.
        navController.popBackStack(Routes.SHELL, inclusive = false)
        for (s in ordered) {
            val isLeaf = s.id == leaf.id
            val route = when {
                // الورقة دائماً قائمة محتوى.
                isLeaf -> Routes.contentList(s.id)
                s.type == "main_section" -> Routes.subSections(s.id)
                // قسم فرعيّ وسيط → قائمة الأقسام الثانويّة تحته.
                else -> Routes.secondarySections(s.id)
            }
            navController.navigate(route)
        }
    }
}
