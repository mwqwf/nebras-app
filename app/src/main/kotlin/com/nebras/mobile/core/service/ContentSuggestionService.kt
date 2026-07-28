package com.nebras.mobile.core.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import com.nebras.mobile.core.model.Content
import kotlinx.coroutines.tasks.await

/**
 * «اقترح تصحيحاً» ✏️ — يكتب اقتراح المستخدم في `content_suggestions`؛
 * جسر خادميّ (suggestions-bridge) يوصله لدردشة إدارة نبراس خلال دقائق.
 *
 * ⚠️ قواعد الأمان صارمة: الحقول أدناه **حصراً** (أيّ حقل زائد يرفض
 * الإنشاء)، note ≤ 1000 حرف، والضيف لا يرسل reporterUid إطلاقاً.
 */
object ContentSuggestionService {

    private const val COLLECTION = "content_suggestions"

    suspend fun submit(content: Content, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        val payload = mutableMapOf<String, Any>(
            "contentId" to content.id,
            "contentType" to content.type.wire,
            "note" to if (trimmed.length > 1000) trimmed.substring(0, 1000) else trimmed,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (uid.isNotEmpty()) payload["reporterUid"] = uid

        FirestoreSyncConfig.instance.collection(COLLECTION).add(payload).await()
    }
}
