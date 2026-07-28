package com.nebras.mobile.core.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import com.nebras.mobile.core.model.Content
import kotlinx.coroutines.tasks.await

/**
 * تصنيفات البلاغ المتاحة للمستخدم. القيمة النصّيّة ([code]) تُخزَّن في
 * Firestore وتظهر للمالك في لوحة الإشراف.
 */
enum class ReportReason(val code: String, val labelKey: String) {
    /** ملكية فكرية / حقوق نشر (الأهمّ). */
    COPYRIGHT("copyright", "report_reason_copyright"),

    /** محتوى جنسيّ صريح. */
    SEXUAL("sexual", "report_reason_sexual"),

    /** محتوى يضرّ بالأطفال. */
    CHILD_SAFETY("child_safety", "report_reason_child_safety"),

    /** عنف. */
    VIOLENCE("violence", "report_reason_violence"),

    /** إرهاب / تطرّف. */
    TERRORISM("terrorism", "report_reason_terrorism"),

    /** أخرى. */
    OTHER("other", "report_reason_other"),
}

/**
 * يكتب بلاغات المستخدمين عن المحتوى إلى `content_reports`. القراءة والحذف
 * محصوران بالمالك عبر قواعد الأمان؛ الإنشاء متاح لأيّ مستخدم (موقَّع أو ضيف).
 */
object ContentReportService {

    private const val COLLECTION = "content_reports"

    suspend fun submit(content: Content, reason: ReportReason, note: String? = null) {
        val db = FirestoreSyncConfig.instance
        val reporterUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        // 1) كتابة البلاغ في content_reports (للمالك للمراجعة).
        db.collection(COLLECTION).add(
            mapOf(
                "contentId" to content.id,
                "contentTitle" to content.title,
                "contentType" to content.type.wire,
                "sourceUrl" to (content.sourceUrl ?: ""),
                "section" to content.section,
                "subSection" to (content.subSection ?: ""),
                "reasonCode" to reason.code,
                "note" to (note ?: "").trim(),
                "reporterUid" to reporterUid,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        // 2) عند بلاغ حقوق نشر تحديداً: اكتب علامة الإخفاء العالميّ كي يختفي
        //    المحتوى عن كلّ المستخدمين فوراً حتى مراجعة المالك — أسلوب
        //    «احتياطيّ صارم» يتجنّب الدعاوى أثناء انتظار المراجعة البشريّة.
        //    باقي البلاغات يكفيها الإخفاء المحلّيّ عن المُبلِّغ + مراجعة المالك.
        if (reason == ReportReason.COPYRIGHT) {
            PendingTakedownService.markPending(
                contentId = content.id,
                reasonCode = reason.code,
                reporterUid = reporterUid.takeIf { it.isNotEmpty() },
                contentType = content.type.wire,
            )
        }
    }

    /**
     * يحذف كلّ بلاغات المستخدم الحاليّ من السحابة — يُستدعى عند حذف الحساب
     * (متطلَّب سياسة حذف بيانات Google Play). يجب استدعاؤه **قبل** حذف حساب
     * Firebase Auth لأنّ القاعدة تسمح بالحذف فقط حين
     * `reporterUid == request.auth.uid`. أيّ فشل يُبتلع بهدوء كي لا يُعطّل
     * حذف الحساب نفسه.
     */
    suspend fun deleteReportsForCurrentUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) return
        val db = FirestoreSyncConfig.instance
        val snapshot = db.collection(COLLECTION)
            .whereEqualTo("reporterUid", uid)
            .get()
            .await()
        if (snapshot.isEmpty) return
        val batch = db.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
