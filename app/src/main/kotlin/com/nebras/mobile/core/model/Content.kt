package com.nebras.mobile.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * نموذج المحتوى — **مصدر الحقيقة** لتحويل مستند Firestore/JSON إلى كائن.
 * نقل حرفيّ لـ `features/content/model/content_model.dart`.
 */
enum class ContentType { VIDEO, AUDIO, BOOK, YOUTUBE, IMAGE, ARTICLE }

data class Content(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val type: ContentType,
    val thumbnailUrl: String,
    // ── الوسائط ──
    /** رابط الفيديو/الصوت/الـ PDF. */
    val sourceUrl: String? = null,
    val sizeInBytes: Int? = null,
    // ── التنظيم ──
    val section: String,
    val subSection: String? = null,
    val sectionName: String? = null,
    // ── بيانات وصفية ──
    val createdAt: Date,
    val updatedAt: Date,
    val createdBy: String,
    val selectionOrder: Int = 0,
    /** عدّاد مشاهدات مجهول من التطبيق (`view_count` في Firestore). */
    val viewCount: Int = 0,
    /** درجة شعبية أسبوعية — تُملأ من تجميع اللوحة أو ترتيب محلي. */
    val popularityScore7d: Double = 0.0,
    /**
     * حالة الترخيص كما تضبطها لوحة التحكّم بعد التحقّق:
     *   ''         → غير محدَّد (يُعرَض، توافقاً مع السلوك القديم).
     *   'approved' → ملكيّة عامّة/مرخّص للتوزيع.
     *   'rejected' → بلاغ حقوق صحيح ⇒ يُحجب عن الجميع.
     */
    val licenseStatus: String = "",
    /** اسم المصدر المعروض للإسناد (Internet Archive، مؤسسة هنداوي…). */
    val sourceName: String = "",
    /** اسم الرخصة المقروء (Public Domain، CC BY 4.0…). */
    val licenseName: String = "",
    /** رابط نصّ الرخصة. */
    val licenseUrl: String = "",
) {
    companion object {

        fun fromFirestore(doc: DocumentSnapshot): Content {
            val data = (doc.data ?: emptyMap<String, Any?>()).toMutableMap()
            data["id"] = doc.id
            return fromJson(data)
        }

        /**
         * ملاحظة: `youtube_video` و`r2_file` قد يصلان ككائن متداخل كامل أو
         * كرقم (مفتاح أجنبيّ فقط) بحسب نقطة النهاية — [asMap] يتعامل مع
         * الحالتين بأمان دون انهيار.
         */
        fun fromJson(json: Map<String, Any?>): Content {
            val metadata = asMap(json["metadata"])
            val youtubeVideo = asMap(json["youtube_video"])
            val r2File = asMap(json["r2_file"])

            val rawUrl = youtubeVideo?.get("video_url")
                ?: youtubeVideo?.get("url")
                ?: youtubeVideo?.get("link")
                ?: r2File?.get("file_url")
                ?: r2File?.get("url")
                ?: json["sourceUrl"]
                ?: json["source_url"]
                ?: json["file_url"]
                ?: json["downloadUrl"]
                ?: json["video_url"]
                ?: json["audio_url"]
                ?: json["youtube_url"]
                ?: json["youtube_link"]
                ?: json["youtube"]
                ?: json["url"]
                ?: json["link"]

            var sourceUrl: String? = null
            if (rawUrl is String && rawUrl.trim().isNotEmpty()) {
                // التطبيق قارئ من قاعدة البيانات فقط — لا علاقة له بـ archive.org.
                sourceUrl = rejectArchiveOrg(rawUrl)
                // 🚫 دفاع عميق: قطع أي رابط YouTube أو منصّة بثّ خارجيّة.
                sourceUrl = rejectExternalPlatform(sourceUrl)
            }

            val rawType = stringValue(json["content_type"])
                ?: stringValue(metadata?.get("content_type"))
                ?: stringValue(json["type"])
                ?: stringValue(metadata?.get("type"))
            val parsedType = parseType(rawType ?: "video")

            // ── تمييز الصوت/الفيديو ──────────────────────────────────
            // اللوحة أحياناً تكتب أصواتاً بـ content_type='video' أو تتركها
            // فارغة، فتُفتح ملفّات `.mp3` في مشغّل الفيديو وتفشل. نستنبط
            // النوع من امتداد الـ URL كحارس أمان — يعمل فقط حين يكون
            // الامتداد قاطعاً ولا يلمس الفيديوهات الشرعيّة.
            val resolvedType: ContentType = when {
                looksLikeYouTubeUrl(sourceUrl) -> ContentType.YOUTUBE

                looksLikeBookUrl(sourceUrl) &&
                    parsedType != ContentType.BOOK &&
                    parsedType != ContentType.AUDIO &&
                    parsedType != ContentType.ARTICLE &&
                    parsedType != ContentType.IMAGE -> ContentType.BOOK

                looksLikeAudioUrl(sourceUrl) &&
                    parsedType != ContentType.AUDIO &&
                    parsedType != ContentType.BOOK &&
                    parsedType != ContentType.ARTICLE &&
                    parsedType != ContentType.IMAGE -> ContentType.AUDIO

                else -> parsedType
            }

            val sizeInBytes = intValue(r2File?.get("file_size"))
                ?: intValue(json["sizeInBytes"])
                ?: intValue(json["fileSize"])

            return Content(
                id = stringValue(json["id"]) ?: stringValue(json["fileId"]) ?: "",
                title = stringValue(json["title"])
                    ?: stringValue(metadata?.get("title"))
                    ?: "",
                author = stringValue(json["author"])
                    ?: stringValue(metadata?.get("author"))
                    ?: stringValue(json["created_by"])
                    ?: "",
                description = cleanDescription(
                    stringValue(json["description"])
                        ?: stringValue(metadata?.get("description"))
                        ?: "",
                ),
                type = resolvedType,
                thumbnailUrl = rejectArchiveOrg(
                    stringValue(json["thumbnail"])
                        ?: stringValue(metadata?.get("thumbnail")),
                ) ?: "",
                section = stringValue(json["subsection"])
                    ?: stringValue(metadata?.get("subsection"))
                    ?: "",
                subSection = stringValue(json["secondary_subsection"])
                    ?: stringValue(metadata?.get("secondary_subsection")),
                sectionName = stringValue(json["subsection_name"])
                    ?: stringValue(metadata?.get("subsection_name")),
                sourceUrl = sourceUrl,
                sizeInBytes = sizeInBytes,
                createdAt = parseDate(
                    json["created_at"],
                    metadata?.get("created_at"),
                    json["createdAt"],
                ),
                updatedAt = parseDate(
                    json["updated_at"],
                    metadata?.get("updated_at"),
                    json["updatedAt"],
                ),
                createdBy = stringValue(json["created_by"])
                    ?: stringValue(metadata?.get("author"))
                    ?: "",
                selectionOrder = intValue(json["selectionOrder"])
                    ?: intValue(metadata?.get("selectionOrder"))
                    ?: 0,
                viewCount = intValue(json["view_count"])
                    ?: intValue(json["viewCount"])
                    ?: intValue(metadata?.get("view_count"))
                    ?: 0,
                popularityScore7d = doubleValue(json["popularity_score_7d"])
                    ?: doubleValue(json["popularityScore7d"])
                    ?: 0.0,
                licenseStatus = (
                    stringValue(json["license_status"])
                        ?: stringValue(json["__license_status"])
                        ?: stringValue(metadata?.get("license_status"))
                        ?: ""
                    ).lowercase(),
                sourceName = friendlySource(
                    stringValue(json["source_name"])
                        ?: stringValue(json["source"])
                        ?: stringValue(json["__source_provider"])
                        ?: stringValue(json["__provider"])
                        ?: stringValue(metadata?.get("source_name"))
                        ?: "",
                ),
                licenseName = friendlyLicense(
                    stringValue(json["license_name"])
                        ?: stringValue(json["license"])
                        ?: stringValue(json["__license"])
                        ?: stringValue(metadata?.get("license_name"))
                        ?: "",
                ),
                licenseUrl = stringValue(json["license_url"])
                    ?: stringValue(json["licenseurl"])
                    ?: stringValue(json["__license_url"])
                    ?: stringValue(metadata?.get("license_url"))
                    ?: "",
            )
        }

        /**
         * يحوّل رمز/اسم المصدر إلى اسم عرض ودود. archive.org تُعرَض كـ
         * "Internet Archive" (لا يُرسَل أيّ طلب إليها — نصّ إسناد فقط).
         */
        private fun friendlySource(raw: String): String {
            val v = raw.trim()
            if (v.isEmpty()) return ""
            return when (v.lowercase()) {
                "archive.org", "internet_archive", "internetarchive" -> "Internet Archive"
                "hindawi" -> "مؤسسة هنداوي"
                "noor-library", "noor", "noorlib" -> "مكتبة نور"
                else -> v
            }
        }

        /** يحوّل رمز الرخصة إلى اسم مقروء. الروابط تبقى كما هي في [licenseUrl]. */
        private fun friendlyLicense(raw: String): String {
            val v = raw.trim()
            if (v.isEmpty()) return ""
            val lower = v.lowercase()
            if (lower.contains("publicdomain") || lower == "cc0" || lower == "pd") {
                return "Public Domain"
            }
            if (lower.startsWith("creative_commons") || lower.startsWith("cc-") ||
                lower.startsWith("cc ") || lower.contains("creativecommons")
            ) {
                return "Creative Commons"
            }
            return v
        }

        /**
         * يقطع أي علاقة بين التطبيق وموقع archive.org. التطبيق "قارئ" من
         * Firestore/Storage فقط. أي رابط أرشيف يُعاد كـ null فلا يُحمَّل.
         */
        fun rejectArchiveOrg(url: String?): String? {
            if (url == null) return null
            val u = url.trim()
            if (u.isEmpty()) return null
            if (u.lowercase().contains("archive.org")) return null
            return u
        }

        /**
         * 🚫 منصّات البثّ الخارجيّة المحظورة. التطبيق لا يبثّ من YouTube ولا
         * أيّ منصّة مملوكة لجهة أخرى — لا عبر قناة شرعيّة ولا غير شرعيّة.
         */
        private val BLOCKED_PLATFORM_HOSTS = listOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "ytimg.com",
            "googlevideo.com",
            "vimeo.com",
            "dailymotion.com",
            "dai.ly",
            "tiktok.com",
            "facebook.com",
            "fb.watch",
            "instagram.com",
            "twitch.tv",
            "soundcloud.com",
        )

        /** يُعيد [url] كما هو، أو null إن كان يشير إلى أيّ منصّة محظورة. */
        fun rejectExternalPlatform(url: String?): String? {
            if (url == null) return null
            val u = url.trim()
            if (u.isEmpty()) return null
            val lower = u.lowercase()
            for (host in BLOCKED_PLATFORM_HOSTS) {
                if (lower.contains(host)) return null
            }
            return u
        }

        private val TAG_REGEX = Regex("<[^>]*>")
        private val WHITESPACE_REGEX = Regex("\\s+")

        private val HTML_ENTITIES = mapOf(
            "&nbsp;" to " ",
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&apos;" to "'",
        )

        /**
         * يُنظّف الوصف من وسوم HTML الخام التي تتسرّب من بعض المصادر فتُعرض
         * حرفيّاً. لا يوجد عارض HTML في التطبيق — الوصف يُعرض دائماً كنصّ عاديّ.
         */
        private fun cleanDescription(raw: String): String {
            var s = raw
            if (s.isEmpty()) return s
            if (!s.contains('<') && !s.contains('&')) return s.trim()
            s = s.replace(TAG_REGEX, " ")
            HTML_ENTITIES.forEach { (k, v) -> s = s.replace(k, v) }
            return s.replace(WHITESPACE_REGEX, " ").trim()
        }

        private fun doubleValue(value: Any?): Double? {
            if (value is Number) return value.toDouble()
            val s = value?.toString() ?: return null
            if (s.trim().isEmpty()) return null
            return s.trim().toDoubleOrNull()
        }

        /**
         * الخادم: video, audio, document, youtube, image, article/text
         * التطبيق: video, audio, book, youtube, image, article
         */
        fun parseType(value: String): ContentType = when (value.lowercase()) {
            "video" -> ContentType.VIDEO
            "youtube" -> ContentType.YOUTUBE
            "audio" -> ContentType.AUDIO
            "document", "book" -> ContentType.BOOK
            "image", "images", "photo", "photos" -> ContentType.IMAGE
            "article", "articles" -> ContentType.ARTICLE
            else -> ContentType.VIDEO
        }

        private fun looksLikeYouTubeUrl(value: String?): Boolean {
            val lower = (value ?: "").trim().lowercase()
            if (lower.isEmpty()) return false
            return lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("youtube")
        }

        private val AUDIO_EXT = listOf(
            ".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac", ".wma",
        )

        private val BOOK_EXT = listOf(
            ".pdf", ".epub", ".doc", ".docx", ".txt", ".rtf",
        )

        /**
         * يكتشف ملفّ الصوت من امتداده بقائمة واضحة تجنّباً لأيّ تخمين خاطئ.
         * نَفصِل الـ query string لأنّ بعض روابط CDN تذيّل توقيعاً بعد الامتداد.
         */
        private fun looksLikeAudioUrl(value: String?): Boolean =
            hasExtension(value, AUDIO_EXT)

        private fun looksLikeBookUrl(value: String?): Boolean =
            hasExtension(value, BOOK_EXT)

        private fun hasExtension(value: String?, extensions: List<String>): Boolean {
            val raw = (value ?: "").trim().lowercase()
            if (raw.isEmpty()) return false
            val pathOnly = raw.substringBefore('?').substringBefore('#')
            return extensions.any { pathOnly.endsWith(it) }
        }

        @Suppress("UNCHECKED_CAST")
        fun asMap(value: Any?): Map<String, Any?>? = when (value) {
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
            else -> null
        }

        fun stringValue(value: Any?): String? {
            if (value == null) return null
            val normalized = value.toString().trim()
            return normalized.ifEmpty { null }
        }

        fun intValue(value: Any?): Int? = when (value) {
            null -> null
            is Int -> value
            is Number -> value.toInt()
            else -> value.toString().trim().toIntOrNull()
        }

        /**
         * عند غياب/تعذّر تحليل التاريخ نُعيد «الحقبة صفر» لا الوقت الحاليّ:
         * الأخير كان يجعل العناصر بلا تاريخ تقفز إلى أحدث ما في القائمة ثمّ
         * تُعيد ترتيب نفسها مع كلّ إعادة بناء فيهتزّ الترتيب.
         */
        fun parseDate(vararg candidates: Any?): Date {
            for (value in candidates) {
                if (value == null) continue
                if (value is Timestamp) return value.toDate()
                if (value is Date) return value
                if (value is Number) {
                    val ms = value.toLong()
                    if (ms > 0) return Date(ms)
                    continue
                }
                val str = value.toString().trim()
                if (str.isEmpty()) continue
                parseIso(str)?.let { return it }
                val ms = str.toLongOrNull()
                if (ms != null && ms > 0) return Date(ms)
            }
            return Date(0L)
        }

        private fun parseIso(value: String): Date? = runCatching {
            Date(java.time.Instant.parse(value).toEpochMilli())
        }.recoverCatching {
            Date(
                java.time.LocalDateTime
                    .parse(value.replace(' ', 'T'))
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli(),
            )
        }.getOrNull()
    }
}

fun parseContentType(value: String): ContentType = Content.parseType(value)
