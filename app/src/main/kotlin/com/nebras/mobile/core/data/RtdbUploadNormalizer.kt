package com.nebras.mobile.core.data

/**
 * يطابق حقول رفع لوحة التحكّم (`dashboard_uploads` ومرآة الملفّات) مع ما
 * يتوقّعه `Content.fromJson`. نقل حرفيّ لـ `core/data/rtdb_upload_normalizer.dart`.
 */
object RtdbUploadNormalizer {

    private const val IMPLICIT_SECONDARY_PREFIX = "__implicit__"

    private val NESTED_CONTENT_FRAGMENT_KEYS = setOf(
        "metadata",
        "youtube_video",
        "r2_file",
    )

    /** جذر عقدة RTDB قد يكون خريطة أو قائمة عناصر. */
    fun normalizeDatabaseRoot(value: Any?): Map<String, Any?> = when (value) {
        is List<*> -> mapOf("_root" to value)
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        else -> emptyMap()
    }

    /**
     * يجمع عدّة جذور RTDB في خريطة واحدة معزولة حتى نمرّ عليها كوحدة واحدة
     * بدون تعارض مفاتيح بين المسارات المختلفة.
     */
    fun combineDatabaseRoots(roots: Map<String, Any?>): Map<String, Any?> {
        val combined = LinkedHashMap<String, Any?>()
        roots.forEach { (key, value) ->
            val normalized = normalizeDatabaseRoot(value)
            if (normalized.isNotEmpty()) combined[key] = normalized
        }
        return combined
    }

    fun looksLikeContentMap(map: Map<String, Any?>): Boolean {
        val metadata = coerceMap(map["metadata"])

        val hasTitle = nonEmpty(map["title"]) || nonEmpty(metadata?.get("title"))
        val hasName = nonEmpty(map["name"]) || nonEmpty(metadata?.get("name"))
        val hasType = nonEmpty(map["content_type"]) ||
            nonEmpty(map["type"]) ||
            nonEmpty(metadata?.get("content_type")) ||
            nonEmpty(metadata?.get("type"))

        val hasSource = nonEmpty(map["sourceUrl"]) ||
            nonEmpty(map["source_url"]) ||
            nonEmpty(map["file_url"]) ||
            nonEmpty(map["audio_url"]) ||
            nonEmpty(map["video_url"]) ||
            nonEmpty(map["downloadUrl"]) ||
            nonEmpty(map["youtube_url"]) ||
            nonEmpty(map["youtube_link"]) ||
            nonEmpty(map["youtube"]) ||
            nonEmpty(map["url"]) ||
            nonEmpty(map["link"]) ||
            nestedMediaUrl(map["youtube_video"]) != null ||
            nestedMediaUrl(map["r2_file"]) != null

        val hasSectionHints = nonEmpty(map["subsection"]) ||
            nonEmpty(map["sub_section"]) ||
            nonEmpty(map["subsection_id"]) ||
            nonEmpty(map["secondary_subsection"]) ||
            nonEmpty(map["secondary_section"]) ||
            nonEmpty(map["secondary_subsection_id"]) ||
            nonEmpty(map["main_section"]) ||
            nonEmpty(map["main_section_id"]) ||
            nonEmpty(map["main_section_name"]) ||
            nonEmpty(metadata?.get("subsection")) ||
            nonEmpty(metadata?.get("secondary_subsection")) ||
            nonEmpty(metadata?.get("main_section")) ||
            nonEmpty(metadata?.get("main_section_id")) ||
            nonEmpty(metadata?.get("main_section_name"))

        val hasNestedMediaHints = map["r2_file"] is Map<*, *> ||
            map["youtube_video"] is Map<*, *> ||
            nonEmpty(map["thumbnail"]) ||
            nonEmpty(metadata?.get("thumbnail"))

        val hasUploadIdentity = nonEmpty(map["fileId"]) ||
            nonEmpty(map["filename"]) ||
            nonEmpty(map["storagePath"]) ||
            nonEmpty(map["downloadUrl"]) ||
            map["fileSize"] != null

        // متساهل عمداً مع تنويعات RTDB: بعض الصفوف بلا content_type/title
        // صريحين لكنّها صفوف وسائط صالحة.
        return hasType ||
            hasSource ||
            (hasUploadIdentity && (hasTitle || hasType || hasSectionHints)) ||
            (hasSectionHints && (hasTitle || hasName || hasNestedMediaHints))
    }

    /**
     * المتجزّآت المضمَّنة (metadata / youtube_video / r2_file) ليست عقداً
     * مستقلّة للمحتوى حتى لو كان فيها id/fileId. استثناؤها دائماً يمنع إنشاء
     * صفوف وهمية مزدوجة تسبّب «مصدر غير متاح» عند الضغط على نسخة الشبح.
     */
    fun shouldTreatAsContentNode(map: Map<String, Any?>, keyHint: String? = null): Boolean {
        val normalizedKey = (keyHint ?: "").trim().lowercase()
        if (NESTED_CONTENT_FRAGMENT_KEYS.contains(normalizedKey)) return false
        return looksLikeContentMap(map)
    }

    /**
     * يملأ `subsection` / `secondary_subsection` بأسماء بديلة شائعة في RTDB.
     * إن وُجد فرعيّ فقط بلا ثانويّ يُنشئ معرّفاً ثابتاً حتى يمرّ فلتر التسلسل
     * في الواجهة.
     */
    fun normalizeRow(row: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>(row)
        val metadata = coerceMap(out["metadata"])

        out.putIfAbsentNonNull(
            "id",
            firstNonEmpty(coerceString(out["fileId"]), coerceString(metadata?.get("id"))),
        )
        out.putIfAbsentNonNull("title", metadata?.get("title"))
        out.putIfAbsentNonNull("description", metadata?.get("description"))
        out.putIfAbsentNonNull("author", metadata?.get("author"))
        out.putIfAbsentNonNull("thumbnail", metadata?.get("thumbnail"))
        out.putIfAbsentNonNull(
            "content_type",
            metadata?.get("content_type") ?: metadata?.get("type"),
        )
        out.putIfAbsentNonNull("main_section", metadata?.get("main_section"))
        out.putIfAbsentNonNull("main_section_id", metadata?.get("main_section_id"))
        out.putIfAbsentNonNull("main_section_name", metadata?.get("main_section_name"))
        out.putIfAbsentNonNull("subsection", metadata?.get("subsection"))
        out.putIfAbsentNonNull("secondary_subsection", metadata?.get("secondary_subsection"))
        out.putIfAbsentNonNull(
            "created_at",
            metadata?.get("created_at") ?: out["createdAt"],
        )
        out.putIfAbsentNonNull(
            "updated_at",
            metadata?.get("updated_at") ?: out["updatedAt"],
        )
        out.putIfAbsentNonNull(
            "selectionOrder",
            out["selectionIndex"]
                ?: metadata?.get("selectionOrder")
                ?: metadata?.get("selectionIndex"),
        )
        out.putIfAbsentNonNull("view_count", out["viewCount"] ?: metadata?.get("view_count"))
        out.putIfAbsentNonNull(
            "popularity_score_7d",
            out["popularityScore7d"] ?: metadata?.get("popularity_score_7d"),
        )
        out.putIfAbsentNonNull("created_by", metadata?.get("author"))
        out.putIfAbsentNonNull("sizeInBytes", out["fileSize"])

        val subsection = firstNonEmpty(
            coerceString(out["subsection"]),
            coerceString(out["sub_section"]),
            nestedId(out["subsection"]),
            coerceString(out["subsection_id"]),
        )
        if (subsection.isNotEmpty()) out["subsection"] = subsection

        val secondary = firstNonEmpty(
            coerceString(out["secondary_subsection"]),
            coerceString(out["secondary_section"]),
            nestedId(out["secondary_subsection"]),
            coerceString(out["secondary_subsection_id"]),
        )
        if (secondary.isNotEmpty()) out["secondary_subsection"] = secondary

        val subFinal = out["subsection"]?.toString()?.trim().orEmpty()
        var secFinal = out["secondary_subsection"]?.toString()?.trim().orEmpty()
        if (subFinal.isNotEmpty() && secFinal.isEmpty()) {
            secFinal = "$IMPLICIT_SECONDARY_PREFIX$subFinal"
            out["secondary_subsection"] = secFinal
        }

        val subName = firstNonEmptyMany(
            listOf(
                coerceString(out["subsection_name"]),
                coerceString(out["subsection_title"]),
                coerceString(out["subsection_label"]),
                coerceString(metadata?.get("subsection_name")),
                coerceString(metadata?.get("subsection_title")),
            ),
        )
        if (subName.isNotEmpty() && out["subsection_name"]?.toString()?.trim().isNullOrEmpty()) {
            out["subsection_name"] = subName
        }

        // توحيد تنويعات رابط الوسائط في صفوف RTDB.
        val sourceUrl = firstNonEmptyMany(
            listOf(
                coerceString(out["sourceUrl"]),
                coerceString(out["source_url"]),
                coerceString(out["file_url"]),
                coerceString(out["audio_url"]),
                coerceString(out["video_url"]),
                coerceString(out["downloadUrl"]),
                coerceString(out["youtube_url"]),
                coerceString(out["youtube_link"]),
                coerceString(out["youtube"]),
                coerceString(out["url"]),
                coerceString(out["link"]),
                nestedMediaUrl(out["youtube_video"]),
                nestedMediaUrl(out["r2_file"]),
            ),
        )
        if (sourceUrl.isNotEmpty() && out["sourceUrl"]?.toString()?.trim().isNullOrEmpty()) {
            out["sourceUrl"] = sourceUrl
        }
        if (sourceUrl.isNotEmpty() && out["file_url"]?.toString()?.trim().isNullOrEmpty()) {
            out["file_url"] = sourceUrl
        }

        val contentType = coerceString(out["content_type"])?.lowercase().orEmpty()
        if (sourceUrl.isNotEmpty() &&
            contentType == "audio" &&
            out["audio_url"]?.toString()?.trim().isNullOrEmpty()
        ) {
            out["audio_url"] = sourceUrl
        }
        if (sourceUrl.isNotEmpty() &&
            (contentType == "video" || contentType == "youtube") &&
            out["video_url"]?.toString()?.trim().isNullOrEmpty()
        ) {
            out["video_url"] = sourceUrl
        }

        return out
    }

    /**
     * تلميح لربط المحتوى بقسم رئيسيّ عند غياب شجرة فرعية كاملة في
     * `sections_unified`.
     */
    fun mainSectionHint(row: Map<String, Any?>): String = firstNonEmpty(
        coerceString(row["main_section_id"]),
        coerceString(row["main_section"]),
        coerceString(row["main_section_slug"]),
        coerceString(row["main_section_name"]),
    )

    fun isImplicitSecondaryId(id: String): Boolean = id.startsWith(IMPLICIT_SECONDARY_PREFIX)

    fun implicitSecondaryDisplayName(id: String, subsectionName: String?): String {
        if (!isImplicitSecondaryId(id)) return id
        val name = subsectionName?.trim()
        if (!name.isNullOrEmpty()) return name
        return "المحتوى"
    }

    /** يبني خريطة بحث تربط المعرّف **والاسم** بمعرّف القسم الرئيسيّ. */
    fun mainIdLookup(mains: Iterable<Pair<String, String>>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for ((rawId, rawName) in mains) {
            val id = rawId.trim()
            if (id.isEmpty()) continue
            map[id] = id
            val n = rawName.trim()
            if (n.isNotEmpty()) map[n] = id
        }
        return map
    }

    fun resolveMainId(hint: String?, lookup: Map<String, String>): String? {
        val k = (hint ?: "").trim()
        if (k.isEmpty()) return null
        return lookup[k]
    }

    // ── مساعدات ─────────────────────────────────────────────────────────

    private fun MutableMap<String, Any?>.putIfAbsentNonNull(key: String, value: Any?) {
        // مقابل `map[key] ??= value` في Dart: لا يكتب إن كان المفتاح موجوداً
        // بقيمة غير فارغة.
        if (this[key] == null && value != null) {
            if (value is String && value.isEmpty()) return
            this[key] = value
        }
    }

    private fun nonEmpty(v: Any?): Boolean = coerceString(v) != null

    private fun coerceString(v: Any?): String? {
        if (v == null) return null
        val s = v.toString().trim()
        return s.ifEmpty { null }
    }

    private fun nestedId(v: Any?): String? {
        if (v is Map<*, *>) {
            val m = v.entries.associate { (k, value) -> k.toString() to value }
            return firstNonEmpty(coerceString(m["id"]), coerceString(m["slug"]))
                .takeIf(String::isNotEmpty)
        }
        return null
    }

    private fun nestedMediaUrl(v: Any?): String? {
        if (v is Map<*, *>) {
            val m = v.entries.associate { (k, value) -> k.toString() to value }
            return firstNonEmpty(
                coerceString(m["video_url"]),
                coerceString(m["file_url"]),
                coerceString(m["url"]),
                coerceString(m["link"]),
            ).takeIf(String::isNotEmpty)
        }
        return coerceString(v)
    }

    private fun coerceMap(value: Any?): Map<String, Any?>? = when (value) {
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        else -> null
    }

    private fun firstNonEmpty(vararg values: String?): String {
        for (value in values) {
            val normalized = (value ?: "").trim()
            if (normalized.isNotEmpty()) return normalized
        }
        return ""
    }

    private fun firstNonEmptyMany(values: Iterable<String?>): String {
        for (value in values) {
            val normalized = (value ?: "").trim()
            if (normalized.isNotEmpty()) return normalized
        }
        return ""
    }
}
