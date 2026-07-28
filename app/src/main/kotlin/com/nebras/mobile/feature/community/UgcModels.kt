package com.nebras.mobile.feature.community

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.nebras.mobile.core.model.Content
import java.util.Calendar
import java.util.Date

/** قسم داخل قناة UGC — نقل `community/model/ugc_models.dart`. */
data class ChannelSection(val id: String, val name: String) {

    fun toMap(): Map<String, Any> = mapOf("id" to id, "name" to name)

    companion object {
        fun fromMap(m: Any?): ChannelSection? {
            if (m !is Map<*, *>) return null
            val id = m["id"]?.toString()?.trim().orEmpty()
            val name = m["name"]?.toString()?.trim().orEmpty()
            if (id.isEmpty() || name.isEmpty()) return null
            return ChannelSection(id, name)
        }
    }
}

/** قناة مجتمع نبراس. */
data class UgcChannel(
    val uid: String,
    val name: String,
    val bio: String = "",
    val photoUrl: String = "",
    val sections: List<ChannelSection> = emptyList(),
    val createdAt: Date? = null,
    val termsAcceptedAt: Date? = null,
    val status: String = "active",
) {
    val isActive: Boolean get() = status != "suspended" && status != "deleted"

    companion object {
        fun fromDoc(doc: DocumentSnapshot): UgcChannel {
            val d = doc.data ?: emptyMap()
            fun ts(v: Any?): Date? = (v as? Timestamp)?.toDate()
            fun s(v: Any?): String = (v ?: "").toString().trim()

            val rawSections = d["sections"]
            val sections = if (rawSections is List<*>) {
                rawSections.mapNotNull(ChannelSection::fromMap)
            } else {
                emptyList()
            }

            return UgcChannel(
                uid = doc.id,
                name = s(d["name"]),
                bio = s(d["bio"]),
                photoUrl = s(d["photoUrl"]),
                sections = sections,
                createdAt = ts(d["createdAt"]),
                termsAcceptedAt = ts(d["termsAcceptedAt"]),
                status = s(d["status"]).ifEmpty { "active" },
            )
        }
    }
}

/** تعليق على منشور مجتمع. */
data class UgcComment(
    val id: String,
    val uid: String,
    val name: String,
    val photoUrl: String,
    val text: String,
    val createdAt: Date? = null,
) {
    companion object {
        fun fromDoc(doc: DocumentSnapshot): UgcComment {
            val d = doc.data ?: emptyMap()
            return UgcComment(
                id = doc.id,
                uid = (d["uid"] ?: "").toString(),
                name = (d["name"] ?: "").toString(),
                photoUrl = (d["photoUrl"] ?: "").toString(),
                text = (d["text"] ?: "").toString(),
                createdAt = (d["createdAt"] as? Timestamp)?.toDate(),
            )
        }
    }
}

/** منشور مجتمع — يلفّ [Content] مع بيانات القناة والتفاعل. */
data class UgcPost(
    val content: Content,
    val channelId: String,
    val channelName: String,
    val channelPhotoUrl: String = "",
    val channelSectionId: String = "",
    val channelSectionName: String = "",
    val commentsCount: Int = 0,
    val reactionsCount: Int = 0,
    val viewsLast7Days: Int = 0,
    val storagePath: String = "",
    val status: String = "published",
    val channelStatus: String = "active",
) {
    val isPublished: Boolean get() = status == "published"

    val isChannelActive: Boolean
        get() = channelStatus != "suspended" && channelStatus != "deleted"

    companion object {
        fun fromDoc(doc: DocumentSnapshot): UgcPost {
            val d = doc.data ?: emptyMap()
            return UgcPost(
                content = Content.fromJson(d + ("id" to doc.id)),
                channelId = (d["channelId"] ?: "").toString(),
                channelName = (d["channelName"] ?: "").toString(),
                channelPhotoUrl = (d["channelPhotoUrl"] ?: "").toString(),
                channelSectionId = (d["channel_section_id"] ?: "").toString(),
                channelSectionName = (d["channel_section_name"] ?: "").toString(),
                commentsCount = (d["commentsCount"] as? Number)?.toInt() ?: 0,
                reactionsCount = (d["reactionsCount"] as? Number)?.toInt() ?: 0,
                viewsLast7Days = sumRecentViews(d["view_days"]),
                storagePath = (d["storage_path"] ?: "").toString(),
                status = (d["status"] ?: "published").toString(),
                channelStatus = (d["channelStatus"] ?: "active").toString(),
            )
        }

        /** مجموع مشاهدات آخر 7 أيّام من خريطة `view_days` (مفاتيح yyyyMMdd). */
        private fun sumRecentViews(raw: Any?): Int {
            if (raw !is Map<*, *>) return 0
            val cutoff = dayKey(daysAgo(6))
            var total = 0
            for ((key, value) in raw) {
                if (key.toString() >= cutoff && value is Number) {
                    total += value.toInt()
                }
            }
            return total
        }

        internal fun dayKey(value: Date): String {
            val cal = Calendar.getInstance().apply { time = value }
            return "%04d%02d%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }

        internal fun daysAgo(days: Int): Date =
            Date(System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000)
    }
}
