package com.nebras.mobile.feature.home.model

import com.nebras.mobile.core.model.Content

/**
 * قسم على الشاشة الرئيسيّة — عنوان ونوع عرض وقائمة محتوى.
 *
 * [remoteSourceApp] / [remoteSourceLevel] / [remoteSourceId]: حقول اختياريّة
 * من لوحة التحكّم لربط قسم بمصدر بيانات خارجيّ عند الحاجة. غيابها يعني
 * قسماً نبراسيّاً عاديّاً.
 *
 * [archiveId] محفوظ في العقد للتوافق مع مستندات قديمة، لكنّ التطبيق **لا
 * يجلب من archive.org إطلاقاً** (انظر `ContentCompliance`).
 */
data class HomeSection(
    val id: String,
    val title: String,
    /** نوع العرض في اللوحة (مثل `videos`, `audios`, `books`). */
    val type: String,
    /** يربط الفرعيّ/الثانويّ بالقسم الأب. */
    val parentId: String? = null,
    val imageUrl: String? = null,
    val archiveId: String? = null,
    val remoteSourceApp: String? = null,
    /** 'main' | 'sub' | 'content' */
    val remoteSourceLevel: String? = null,
    val remoteSourceId: String? = null,
    val items: List<Content> = emptyList(),
    val isOfflineCache: Boolean = false,
) {
    /** هل هذا القسم مرتبط بمصدر خارجيّ؟ يُستعمل لتصفية الأقسام في الجسر. */
    val hasRemoteSource: Boolean
        get() {
            val app = remoteSourceApp?.trim()
            val id = remoteSourceId?.trim()
            return !app.isNullOrEmpty() && !id.isNullOrEmpty()
        }

    companion object {
        fun fromJson(json: Map<String, Any?>): HomeSection = HomeSection(
            id = json["id"]?.toString()
                ?: json["key"]?.toString()
                ?: "${json["type"] ?: ""}:${json["title"] ?: ""}",
            title = json["title"] as? String ?: "",
            type = json["type"] as? String ?: "",
            parentId = json["parent_id"]?.toString(),
            imageUrl = json["image"]?.toString() ?: json["image_url"]?.toString(),
            archiveId = normalize(
                json["archive_id"]?.toString()
                    ?: json["archiveId"]?.toString()
                    ?: json["ia_collection"]?.toString(),
            ),
            remoteSourceApp = normalize(
                json["remote_source_app"]?.toString()
                    ?: json["remoteSourceApp"]?.toString(),
            ),
            remoteSourceLevel = normalize(
                json["remote_source_level"]?.toString()
                    ?: json["remoteSourceLevel"]?.toString(),
            ),
            remoteSourceId = normalize(
                json["remote_source_id"]?.toString()
                    ?: json["remoteSourceId"]?.toString(),
            ),
            items = (json["items"] as? List<*>)
                ?.mapNotNull { element ->
                    Content.asMap(element)?.let(Content::fromJson)
                }
                ?: emptyList(),
            isOfflineCache = json["is_offline_cache"] == true,
        )

        private fun normalize(value: String?): String? {
            val trimmed = value?.trim() ?: return null
            return trimmed.ifEmpty { null }
        }
    }
}
