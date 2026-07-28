package com.nebras.mobile.feature.home.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nebras.mobile.core.data.ContentCompliance
import com.nebras.mobile.core.data.RtdbUploadNormalizer
import com.nebras.mobile.core.data.compareContentOldestFirst
import com.nebras.mobile.core.data.sortContentOldestFirst
import com.nebras.mobile.core.data.sortedContentOldestFirst
import com.nebras.mobile.core.error.ErrorHandler
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentMetadataCache
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.core.service.wire
import com.nebras.mobile.feature.home.model.HomeSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * مصدر بيانات الشاشة الرئيسية — يجلب المحتوى ويضمن عرض شجرة الأقسام حتى
 * حين لا يكون فيها محتوى حاليّاً. نقل `features/home/data/home_datasource.dart`.
 */
class HomeDatasource(
    private val firestore: FirebaseFirestore,
    private val metadataCache: ContentMetadataCache? = null,
) {

    companion object {
        private const val TAG = "HomeDatasource"

        // مسارات المجموعات في Firestore.
        private const val PATH_SECTIONS = "sections_unified"
        private const val PATH_CONTENT_FILES = "content_unified_files"
        // 🚫 YouTube مقطوع نهائياً: لم نَعُد نقرأ `content_unified_youtube`
        // إطلاقاً. حتى لو تسلّل رابط YouTube داخل مجموعة أخرى يرفضه
        // `Content.fromJson` (sourceUrl=null) فيُسقطه حارس الامتثال.

        /**
         * عدد أحدث الوثائق المجلوبة للرئيسية. الرئيسية تعرض **رفوفاً**
         * (أحدث/شعبي/لك) لا الكتالوج كاملاً، فـ 300 وثيقة حديثة تكفي
         * للاكتشاف مع أمان من استهلاك الذاكرة.
         */
        private const val PAGE_SIZE = 300L

        /** فترة تجميع اللقطات المتتابعة في إعادة بناء واحدة. */
        private const val DEBOUNCE_MILLIS = 350L

        /**
         * بادئات المعرّفات التي تُنتجها جسور المصادر الخارجيّة داخل اللوحة.
         * أيّ معرّف يبدأ بإحداها إمّا مُسجَّل صراحةً في `sections_unified`
         * بحقول `remote_source_*` (قسم حقيقيّ مرتبط بمصدر)، وإمّا بقايا
         * محتوى يتيم لمصدر حُذف — فلا نسمح بتحويله لقسم وهميّ باسم خام.
         */
        private val EXTERNAL_ID_PREFIXES = listOf("mshcat:", "oldapp:", "old:")

        private val RESERVED_MAIN_TREE_KEYS = setOf(
            "id", "name", "title", "slug", "description", "order", "icon", "type",
            "created_at", "updated_at", "createdAt", "updatedAt",
            "sub_sections", "subsections", "subSections", "children",
        )

        private val RESERVED_SUB_TREE_KEYS = setOf(
            "id", "name", "title", "slug", "description", "order", "icon", "type",
            "created_at", "updated_at", "createdAt", "updatedAt",
            "main_section", "main_section_id", "main_section_name",
            "secondary_sections", "secondary_subsections", "secondarySections",
            "children", "sub_section", "subsection",
        )

        private fun looksLikeExternalRef(id: String): Boolean {
            if (id.isEmpty()) return false
            return EXTERNAL_ID_PREFIXES.any { id.startsWith(it) }
        }

        private fun firstNonEmpty(vararg values: String?): String {
            for (value in values) {
                val normalized = (value ?: "").trim()
                if (normalized.isNotEmpty()) return normalized
            }
            return ""
        }

        private fun firstNonEmptyNullable(values: Iterable<String?>): String? {
            for (value in values) {
                val normalized = (value ?: "").trim()
                if (normalized.isNotEmpty()) return normalized
            }
            return null
        }

        private fun typeFallbackTitle(type: String): String? = when (type) {
            "video" -> "Videos"
            "audio" -> "Audio"
            "book" -> "Books"
            "youtube" -> "Videos"
            else -> null
        }

        private fun dedupeById(items: List<Content>): List<Content> {
            val map = LinkedHashMap<String, Content>()
            for (item in items) map[item.id] = item
            return map.values.toList()
        }
    }

    /**
     * استعلام «أحدث [PAGE_SIZE] وثيقة» من مجموعة محتوى.
     *
     * ⚠️ نرتّب بـ `createdAt` (موجود 100% كـ Timestamp) لا بمعرّف الوثيقة:
     * المعرّفات ببادئات مزوّدين مختلفة (`hindawi_`/`fb_`/`ia_`…) تُرتَّب
     * أبجدياً لا زمنياً فيطغى مزوّد على الصفحة ويُخفي أحدث محتوى مزوّد آخر.
     * `createdAt` ترتيب زمنيّ موحَّد عبر كلّ المصادر.
     *
     * ملاحظة: هذا يحدّد *أيّ* وثائق تُحمَّل فقط — ترتيب العرض داخل الأقسام
     * يبقى «الأقدم أولاً» عبر `ContentOrdering`.
     */
    private fun newestContentQuery(path: String): Query =
        firestore.collection(path)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)

    suspend fun getHomeData(page: Int = 1): List<HomeSection> = try {
        val sectionsSnapshot = firestore.collection(PATH_SECTIONS).get().await()
        val filesSnapshot = newestContentQuery(PATH_CONTENT_FILES).get().await()

        buildFromRawValues(
            sectionsRaw = docsToMap(sectionsSnapshot),
            // 🚫 لا نقرأ `dashboard_uploads`: هي **مرآة متطابقة** لـ
            // `content_unified_files` (تُكتبان معاً في نفس الـ batch). قراءة
            // الاثنتين كانت تُضاعف حجم البيانات المحمّلة بلا فائدة.
            uploadsRaw = emptyMap<String, Any?>(),
            filesRaw = docsToMap(filesSnapshot),
            // 🚫 YouTube مقطوع: نمرّر خريطة فارغة بدل قراءة المجموعة.
            youtubeRaw = emptyMap<String, Any?>(),
            page = page,
        )
    } catch (e: Throwable) {
        val cached = cachedOfflineSections()
        if (cached.isNotEmpty()) cached else throw ErrorHandler.handleException(e).toException()
    }

    /** تحويل المستندات إلى شكل Map يطابق بنية RTDB القديمة. */
    private fun docsToMap(snapshot: QuerySnapshot): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for (doc in snapshot.documents) {
            map[doc.id] = doc.data
        }
        return map
    }

    /**
     * يراقب Firestore على نفس المسارات التي يقرأها [getHomeData]. نحتفظ
     * بآخر قيمة لكلّ مسار، ومع وصول أيّ حدث نُعيد بناء الشجرة — فيظهر
     * المحتوى المضاف من اللوحة خلال ميلّي ثانية بلا إعادة تشغيل.
     *
     * 🛠️ **تجميع اللقطات (debounce)**: إضافة اللوحة المستمرّة تُطلق عشرات
     * اللقطات في ثوانٍ. إعادة البناء الكاملة لكلّ لقطة كانت تُراكم تخصيص
     * ذاكرة أسرع من الـGC. نُجمّعها في إعادة بناء واحدة بعد فترة هدوء قصيرة.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watchHomeData(page: Int = 1): Flow<List<HomeSection>> = callbackFlow {
        val registrations = mutableListOf<ListenerRegistration>()

        var sectionsRaw: Map<String, Any?>? = null
        var filesRaw: Map<String, Any?>? = null
        // 🚫 لا نشترك في `dashboard_uploads` (مرآة متطابقة) ولا في
        // `content_unified_youtube` (YouTube مقطوع) — خرائط فارغة ثابتة.
        val uploadsRaw = emptyMap<String, Any?>()
        val youtubeRaw = emptyMap<String, Any?>()
        var gotSections = false
        var gotFiles = false

        // حارس ضدّ إرسال نتائج متأخّرة بعد وصول قيم أحدث.
        var buildSeq = 0
        var debounceJob: Job? = null

        suspend fun rebuild() {
            if (!gotSections || !gotFiles) return
            val mySeq = ++buildSeq
            runCatching {
                val list = buildFromRawValues(
                    sectionsRaw = sectionsRaw,
                    uploadsRaw = uploadsRaw,
                    filesRaw = filesRaw,
                    youtubeRaw = youtubeRaw,
                    page = page,
                )
                reconcileLiveContent(list)
                if (mySeq != buildSeq) return // قيم أحدث وصلت أثناء البناء.
                trySend(list)
            }.onFailure { close(it) }
        }

        fun scheduleRebuild() {
            debounceJob?.cancel()
            debounceJob = launch {
                delay(DEBOUNCE_MILLIS)
                rebuild()
            }
        }

        fun listenOn(query: Query, assign: (Map<String, Any?>) -> Unit, markArrived: () -> Unit) {
            registrations.add(
                query.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val cached = cachedOfflineSections()
                        if (cached.isNotEmpty()) trySend(cached) else close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    assign(docsToMap(snapshot))
                    markArrived()
                    scheduleRebuild()
                },
            )
        }

        listenOn(
            firestore.collection(PATH_SECTIONS),
            { sectionsRaw = it },
            { gotSections = true },
        )
        listenOn(
            newestContentQuery(PATH_CONTENT_FILES),
            { filesRaw = it },
            { gotFiles = true },
        )
        // 🚫 YouTube مقطوع: لا listener لمجموعة `content_unified_youtube`.
        Log.d(TAG, "watchHomeData: listeners attached")

        awaitClose {
            Log.d(TAG, "watchHomeData: cancelling listeners")
            debounceJob?.cancel()
            registrations.forEach(ListenerRegistration::remove)
            registrations.clear()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * نفس [watchHomeData] لكن يبثّ لقطة الكاش المحلّيّ إن فشلت الشبكة قبل
     * وصول أوّل لقطة حيّة.
     */
    fun watchHomeDataWithOfflineFallback(page: Int = 1): Flow<List<HomeSection>> = callbackFlow {
        var emittedLive = false
        val job = launch {
            watchHomeData(page).collect { sections ->
                emittedLive = true
                reconcileLiveContent(sections)
                trySend(sections)
            }
        }
        job.invokeOnCompletion { error ->
            if (error != null && !emittedLive) {
                val cached = cachedOfflineSections()
                if (cached.isNotEmpty()) {
                    trySend(cached)
                    return@invokeOnCompletion
                }
            }
            close(error)
        }
        awaitClose { job.cancel() }
    }

    /**
     * لقطة فوريّة من الميتادات المحفوظة محليّاً — تُعرض لحظة الإقلاع قبل
     * وصول أوّل لقطة حيّة، حتى لا يرى المستخدم العائد شاشة تحميل لثوانٍ.
     */
    fun cachedHomeSections(): List<HomeSection> = cachedOfflineSections()

    /**
     * يبني قسماً افتراضياً من الميتادات المحفوظة عند فشل الشبكة. لا يخلط
     * بيانات قديمة مع لقطات الخادم: بمجرّد وصول أيّ لقطة حيّة نعود لمسار
     * الخادم ونصالح الكاش لإزالة المحذوفات.
     */
    private fun cachedOfflineSections(): List<HomeSection> {
        val cache = metadataCache ?: return emptyList()
        val groups = cache.offlineGroups()
        if (groups.isEmpty()) return emptyList()
        return groups.map { group ->
            HomeSection(
                id = group.id,
                title = group.title,
                type = "offline_metadata",
                // حتى في وضع عدم الاتصال نحترم إخفاء المحتوى المُبلَّغ عنه.
                items = group.items.filterNot { HiddenContentService.isHidden(it.id) },
                isOfflineCache = true,
            )
        }
    }

    private fun reconcileLiveContent(liveSections: List<HomeSection>) {
        val cache = metadataCache ?: return
        val liveIds = buildSet {
            for (section in liveSections) {
                if (section.isOfflineCache) continue
                for (item in section.items) add(item.id)
            }
        }
        if (liveIds.isNotEmpty()) cache.reconcileWithLiveIds(liveIds)
    }

    /** منطق البناء المشترك بين [getHomeData] و[watchHomeData]. */
    private suspend fun buildFromRawValues(
        sectionsRaw: Any?,
        uploadsRaw: Any?,
        filesRaw: Any?,
        youtubeRaw: Any?,
        page: Int = 1,
    ): List<HomeSection> = withContext(Dispatchers.Default) {
        val sectionsRoot = RtdbUploadNormalizer.normalizeDatabaseRoot(sectionsRaw)
        val contentRoot = RtdbUploadNormalizer.combineDatabaseRoots(
            mapOf(
                "dashboard_uploads" to uploadsRaw,
                "content_unified_files" to filesRaw,
                "content_unified_youtube" to youtubeRaw,
            ),
        )
        val contentRows = parseAndNormalizeContentRows(contentRoot)
        val parsedSections = parseSectionsTree(sectionsRoot)

        // إزالة التكرار: الـ walker قد يمرّ على مرآتين لنفس الوثيقة، فنحتفظ
        // بأوّل ظهور فقط حتى لا يرى المستخدم كلّ بطاقة مرّتين في كلّ قسم.
        val seenIds = mutableSetOf<String>()
        val allItems = contentRows
            .map(::mapContentFromRtdb)
            // ⛓️ حارس «لا يوجد مصدر متاح» — استبعاد كلّ وثيقة بلا sourceUrl صالح.
            .filter(::isPlayableAndCompliant)
            .filter { seenIds.add(it.id) }

        // 🛠️ نستعمل `cacheLiveItems` (لا `cacheMany`) لأنّ الأولى تُزيل وسم
        // «معاينة أوفلاين» عن العناصر الحيّة. الثانية كانت تُحدِّث الكاش دون
        // مسح الوسم، فتبقى عناصر حيّة موسومة أوفلاين فيُمنع فتحها خطأً.
        metadataCache?.cacheLiveItems(allItems)

        Log.d(TAG, "page=$page raw contents: ${allItems.size}")
        Log.d(
            TAG,
            "sections main=${parsedSections.main.size}, " +
                "sub=${parsedSections.sub.size}, secondary=${parsedSections.secondary.size}",
        )

        val dynamicSections = buildDynamicSections(
            allItems,
            contentRows,
            parsedSections.main,
            parsedSections.sub,
            parsedSections.secondary,
        )
        if (dynamicSections.isNotEmpty()) {
            return@withContext appendFallbackSections(dynamicSections, allItems)
        }

        if (allItems.isEmpty()) return@withContext emptyList()
        buildGroupedFallback(allItems)
    }

    private fun buildDynamicSections(
        allItems: List<Content>,
        contentRows: List<Map<String, Any?>>,
        mainRows: List<Map<String, Any?>>,
        subRows: List<Map<String, Any?>>,
        secondaryRows: List<Map<String, Any?>>,
    ): List<HomeSection> {
        if (mainRows.isEmpty() && subRows.isEmpty() && secondaryRows.isEmpty()) {
            return emptyList()
        }

        val mainSections = mainRows.mapNotNull(::parseMainSection)
        val mainLookup = RtdbUploadNormalizer.mainIdLookup(
            mainSections.map { it.id to it.name },
        )

        var subSections = resolveSubParentsAgainstMains(
            subRows.mapNotNull(::parseSubSection),
            mainLookup,
        )
        var secondarySections = secondaryRows.mapNotNull(::parseSecondarySection)

        val subById = subSections.associateBy { it.id }.toMutableMap()
        val secondaryById = secondarySections.associateBy { it.id }.toMutableMap()

        // إذا وُجدت في المحتوى أقسام ثانوية غير معرّفة في الشجرة، نُنشئها
        // ونربطها بالقسم الفرعيّ.
        //
        // حماية من الأقسام الوهمية: لا نُنشئ قسماً ضمنيّاً من معرّف يبدو
        // مرجعاً لمصدر خارجيّ. المعرّفات الخارجيّة محجوزة للأقسام الحقيقيّة
        // المسجّلة صراحةً في `sections_unified` بحقول `remote_source_*`؛
        // وصولنا هنا بمعرّف خارجيّ غير مُسجَّل يعني محتوًى يتيماً لمصدر حُذف.
        for (item in allItems) {
            val secId = item.subSection?.trim().orEmpty()
            if (secId.isEmpty()) continue
            if (secondaryById.containsKey(secId)) continue
            val subId = item.section.trim()
            if (subId.isEmpty()) continue
            if (looksLikeExternalRef(secId) || looksLikeExternalRef(subId)) continue
            secondaryById[secId] = SectionMeta(
                id = secId,
                name = RtdbUploadNormalizer.implicitSecondaryDisplayName(
                    secId,
                    item.sectionName,
                ),
                parentId = subId,
                imageUrl = null,
            )
        }
        secondarySections = secondaryById.values.toList()

        // إذا وُجد في المحتوى subsection غير مُعرّف في الشجرة، نُنشئه تحت قسم
        // رئيسيّ (من التلميح أو الأوّل).
        if (mainSections.isNotEmpty()) {
            val fallbackMainId = mainSections.first().id
            for (i in allItems.indices) {
                val item = allItems[i]
                val subId = item.section.trim()
                if (subId.isEmpty()) continue
                if (subById.containsKey(subId)) continue
                // نفس الحماية في المستوى الفرعيّ: لا phantom من معرّف خارجيّ.
                if (looksLikeExternalRef(subId)) continue
                val row = contentRows.getOrElse(i) { emptyMap() }
                val hint = RtdbUploadNormalizer.mainSectionHint(row)
                val resolvedMain = RtdbUploadNormalizer.resolveMainId(hint, mainLookup)
                    ?: fallbackMainId
                subById[subId] = SectionMeta(
                    id = subId,
                    name = item.sectionName?.trim()?.takeIf { it.isNotEmpty() } ?: subId,
                    parentId = resolvedMain,
                    imageUrl = null,
                )
            }
            subSections = subById.values.toList()
        }

        // نسمح للمحتوى متى كان مرتبطاً بقسم فرعيّ معروف وقسم رئيسيّ حقيقيّ.
        // وجود القسم الثانويّ **اختياريّ** — اللوحة تسمح برفع محتوى تحت
        // (رئيسيّ + فرعيّ) فقط بدون مستوى ثالث. اشتراط الثانويّ كان يُسقط
        // هذا النوع كاملاً فتظهر الأقسام فارغة.
        val mainIds = mainSections.mapTo(mutableSetOf()) { it.id }

        val filteredItems = allItems.filter { item ->
            val subId = item.section
            val secondaryId = item.subSection.orEmpty()
            if (subId.isEmpty()) return@filter false
            val sub = subById[subId] ?: return@filter false
            // إن وُجد قسم ثانويّ مُعلَن في العنصر نتحقّق من اتساقه مع الشجرة.
            if (secondaryId.isNotEmpty()) {
                val secondary = secondaryById[secondaryId] ?: return@filter false
                if (secondary.parentId.isNotEmpty() && secondary.parentId != subId) {
                    return@filter false
                }
            }
            // تحقّق أنّ القسم الفرعيّ مربوط بقسم رئيسيّ فعليّ.
            if (sub.parentId.isEmpty()) return@filter false
            mainIds.contains(sub.parentId)
        }

        Log.d(TAG, "filtered contents with full hierarchy: ${filteredItems.size}")

        val bySub = LinkedHashMap<String, MutableList<Content>>()
        val bySecondary = LinkedHashMap<String, MutableList<Content>>()
        for (item in filteredItems) {
            if (item.section.isNotEmpty()) {
                bySub.getOrPut(item.section) { mutableListOf() }.add(item)
            }
            val secondaryId = item.subSection.orEmpty()
            if (secondaryId.isNotEmpty()) {
                bySecondary.getOrPut(secondaryId) { mutableListOf() }.add(item)
            }
        }

        // ترتيب المحتوى داخل كلّ قسم فرعيّ/ثانويّ: الأقدم أولاً.
        bySub.values.forEach { it.sortContentOldestFirst() }
        bySecondary.values.forEach { it.sortContentOldestFirst() }

        val subByMain = LinkedHashMap<String, MutableList<SectionMeta>>()
        for (sub in subSections) {
            if (sub.parentId.isNotEmpty()) {
                subByMain.getOrPut(sub.parentId) { mutableListOf() }.add(sub)
            }
        }

        val sections = mutableListOf<HomeSection>()

        for (main in mainSections) {
            val linkedSubs = subByMain[main.id].orEmpty()
            val mainItems = linkedSubs.flatMap { bySub[it.id].orEmpty() }
            // dedupe ثمّ إعادة الترتيب — dedupe قد يكسر الترتيب المكتسب.
            val deduped = dedupeById(mainItems)
                .sortedWith(::compareContentOldestFirst)
            sections.add(
                HomeSection(
                    id = "main:${main.id}",
                    title = main.name,
                    type = "main_section",
                    parentId = null,
                    imageUrl = main.imageUrl,
                    archiveId = main.archiveId,
                    items = deduped,
                ),
            )
        }

        for (sub in subSections) {
            sections.add(
                HomeSection(
                    id = "sub:${sub.id}",
                    title = sub.name,
                    type = "sub_section",
                    parentId = if (mainIds.contains(sub.parentId)) "main:${sub.parentId}" else null,
                    imageUrl = sub.imageUrl,
                    archiveId = sub.archiveId,
                    remoteSourceApp = sub.remoteSourceApp,
                    remoteSourceLevel = sub.remoteSourceLevel,
                    remoteSourceId = sub.remoteSourceId,
                    items = bySub[sub.id].orEmpty(),
                ),
            )
        }

        for (secondary in secondarySections) {
            sections.add(
                HomeSection(
                    id = "secondary:${secondary.id}",
                    title = secondary.name,
                    type = "secondary_section",
                    parentId = if (secondary.parentId.isNotEmpty()) {
                        "sub:${secondary.parentId}"
                    } else {
                        null
                    },
                    imageUrl = secondary.imageUrl,
                    archiveId = secondary.archiveId,
                    remoteSourceApp = secondary.remoteSourceApp,
                    remoteSourceLevel = secondary.remoteSourceLevel,
                    remoteSourceId = secondary.remoteSourceId,
                    items = bySecondary[secondary.id].orEmpty(),
                ),
            )
        }

        return sections
    }

    // ── تحليل شجرة الأقسام ──────────────────────────────────────────

    /**
     * اللوحة تحفظ الأقسام في بنية مسطّحة تحت
     * `sections_unified/{main|sub|secondary}/{id}`. إن اكتشفنا هذه البنية
     * نفسّر كلّ مستوى كقائمة مستقلّة بمفاتيح FK صريحة بدل المشي شجريّاً
     * (المشي الشجريّ يعتبر المفاتيح "main"/"sub"/"secondary" نفسها أقساماً
     * رئيسية ويضع المحتوى تحت شجرة خاطئة تماماً).
     */
    private fun parseSectionsTree(root: Map<String, Any?>): SectionsParseResult =
        if (looksLikeFlatSectionsRoot(root)) {
            parseFlatSections(root)
        } else {
            parseLegacyTreeSections(root)
        }

    private fun looksLikeFlatSectionsRoot(root: Map<String, Any?>): Boolean =
        containerHasSectionRecords(root["main"]) ||
            containerHasSectionRecords(root["sub"]) ||
            containerHasSectionRecords(root["secondary"])

    private fun containerHasSectionRecords(container: Any?): Boolean {
        val entries: List<Any?> = when (container) {
            is Map<*, *> -> container.values.toList()
            is List<*> -> container
            else -> return false
        }
        for (value in entries) {
            val m = asMap(value)
            if (m.isEmpty()) continue
            val hasName = m["name"]?.toString()?.trim()?.isNotEmpty() == true ||
                m["title"]?.toString()?.trim()?.isNotEmpty() == true
            val hasId = m["id"]?.toString()?.trim()?.isNotEmpty() == true
            val hasFk = m["main_section"]?.toString()?.trim()?.isNotEmpty() == true ||
                m["sub_section"]?.toString()?.trim()?.isNotEmpty() == true
            if ((hasName && hasId) || hasFk) return true
        }
        return false
    }

    private fun parseFlatSections(root: Map<String, Any?>): SectionsParseResult {
        val main = mutableListOf<Map<String, Any?>>()
        val sub = mutableListOf<Map<String, Any?>>()
        val secondary = mutableListOf<Map<String, Any?>>()

        fun walkContainer(container: Any?, onRecord: (String, Map<String, Any?>) -> Unit) {
            when (container) {
                is Map<*, *> -> container.forEach { (key, value) ->
                    if (value is Map<*, *>) onRecord(key.toString(), asMap(value))
                }
                is List<*> -> container.forEachIndexed { i, value ->
                    if (value is Map<*, *>) onRecord("$i", asMap(value))
                }
            }
        }

        fun pickThumbnail(m: Map<String, Any?>): String? = firstNonEmptyNullable(
            listOf(
                m["thumbnail"]?.toString(),
                m["thumbnail_url"]?.toString(),
                m["image_url"]?.toString(),
                m["image"]?.toString(),
            ),
        )

        fun isExplicitlyHidden(m: Map<String, Any?>): Boolean {
            val listed = m["is_listed"] ?: return false
            if (listed is Boolean) return !listed
            val s = listed.toString().trim().lowercase()
            return s == "false" || s == "0"
        }

        fun pickArchiveId(m: Map<String, Any?>): String? = firstNonEmptyNullable(
            listOf(
                m["archive_id"]?.toString(),
                m["archiveId"]?.toString(),
                m["ia_collection"]?.toString(),
            ),
        )

        walkContainer(root["main"]) { key, record ->
            if (isExplicitlyHidden(record)) return@walkContainer
            val id = firstNonEmpty(record["id"]?.toString(), key)
            if (id.isEmpty()) return@walkContainer
            val name = firstNonEmpty(
                record["name"]?.toString(),
                record["title"]?.toString(),
                id,
            )
            main.add(
                mapOf(
                    "id" to id,
                    "name" to name,
                    "image_url" to pickThumbnail(record),
                    "archive_id" to pickArchiveId(record),
                    "order_index" to record["order_index"],
                ),
            )
        }

        walkContainer(root["sub"]) { key, record ->
            if (isExplicitlyHidden(record)) return@walkContainer
            val id = firstNonEmpty(record["id"]?.toString(), key)
            if (id.isEmpty()) return@walkContainer
            val name = firstNonEmpty(
                record["name"]?.toString(),
                record["title"]?.toString(),
                id,
            )
            val mainSection = firstNonEmpty(
                record["main_section"]?.toString(),
                record["main_section_id"]?.toString(),
                record["main_section_name"]?.toString(),
            )
            sub.add(
                buildMap {
                    put("id", id)
                    put("name", name)
                    put("main_section", mainSection)
                    put("image_url", pickThumbnail(record))
                    put("archive_id", pickArchiveId(record))
                    putAll(pickRemoteSource(record))
                },
            )
        }

        walkContainer(root["secondary"]) { key, record ->
            if (isExplicitlyHidden(record)) return@walkContainer
            val id = firstNonEmpty(record["id"]?.toString(), key)
            if (id.isEmpty()) return@walkContainer
            val name = firstNonEmpty(
                record["name"]?.toString(),
                record["title"]?.toString(),
                id,
            )
            val subSection = firstNonEmpty(
                record["sub_section"]?.toString(),
                record["sub_section_id"]?.toString(),
                record["subsection"]?.toString(),
            )
            secondary.add(
                buildMap {
                    put("id", id)
                    put("name", name)
                    put("sub_section", subSection)
                    put("image_url", pickThumbnail(record))
                    put("archive_id", pickArchiveId(record))
                    putAll(pickRemoteSource(record))
                },
            )
        }

        Log.d(
            TAG,
            "flat sections parsed: main=${main.size}, sub=${sub.size}, " +
                "secondary=${secondary.size}",
        )
        return SectionsParseResult(main, sub, secondary)
    }

    private fun parseLegacyTreeSections(root: Map<String, Any?>): SectionsParseResult {
        val main = mutableListOf<Map<String, Any?>>()
        val sub = mutableListOf<Map<String, Any?>>()
        val secondary = mutableListOf<Map<String, Any?>>()

        fun appendSecondary(subId: String, secKey: String, rawSec: Any?) {
            val secMap = asMap(rawSec)
            val secId = firstNonEmpty(secMap["id"]?.toString(), secKey)
            val secName = firstNonEmpty(
                secMap["name"]?.toString(),
                secMap["title"]?.toString(),
                secId,
            )
            val secImage = firstNonEmpty(
                secMap["thumbnail"]?.toString(),
                secMap["image"]?.toString(),
                secMap["image_url"]?.toString(),
            )
            val secArchive = firstNonEmpty(
                secMap["archive_id"]?.toString(),
                secMap["archiveId"]?.toString(),
                secMap["ia_collection"]?.toString(),
            )
            if (secId.isEmpty()) return
            secondary.add(
                mapOf(
                    "id" to secId,
                    "name" to secName,
                    "sub_section" to subId,
                    "image_url" to secImage,
                    "archive_id" to secArchive.ifEmpty { null },
                ),
            )
        }

        fun mapOrListToKeyedMap(raw: Any?): Map<String, Any?> = when (raw) {
            is List<*> -> raw.mapIndexed { i, v -> "$i" to v }.toMap()
            else -> asMap(raw)
        }

        fun appendSubSecondaries(subId: String, subMap: Map<String, Any?>) {
            val secondaryContainer = mapOrListToKeyedMap(
                subMap["secondary_sections"]
                    ?: subMap["secondary_subsections"]
                    ?: subMap["secondarySections"]
                    ?: subMap["children"],
            )
            if (secondaryContainer.isNotEmpty()) {
                secondaryContainer.forEach { (secKey, rawSec) ->
                    appendSecondary(subId, secKey, rawSec)
                }
                return
            }
            subMap.forEach { (k, v) ->
                if (RESERVED_SUB_TREE_KEYS.contains(k)) return@forEach
                when (v) {
                    is Map<*, *> -> appendSecondary(subId, k, v)
                    is List<*> -> v.forEachIndexed { i, e -> appendSecondary(subId, "$i", e) }
                }
            }
        }

        fun appendSub(mainId: String, subKey: String, rawSub: Any?) {
            val subMap = asMap(rawSub)
            val subId = firstNonEmpty(subMap["id"]?.toString(), subKey)
            val subName = firstNonEmpty(
                subMap["name"]?.toString(),
                subMap["title"]?.toString(),
                subId,
            )
            val subImage = firstNonEmpty(
                subMap["thumbnail"]?.toString(),
                subMap["image"]?.toString(),
                subMap["image_url"]?.toString(),
            )
            val subArchive = firstNonEmpty(
                subMap["archive_id"]?.toString(),
                subMap["archiveId"]?.toString(),
                subMap["ia_collection"]?.toString(),
            )
            if (subId.isEmpty()) return
            sub.add(
                mapOf(
                    "id" to subId,
                    "name" to subName,
                    "main_section" to mainId,
                    "image_url" to subImage,
                    "archive_id" to subArchive.ifEmpty { null },
                ),
            )
            appendSubSecondaries(subId, subMap)
        }

        fun parseMainBlock(mainKey: String, rawMain: Any?) {
            if (rawMain is List<*>) {
                rawMain.forEachIndexed { i, e -> parseMainBlock("$mainKey:$i", e) }
                return
            }
            val mainMap = asMap(rawMain)
            val mainId = firstNonEmpty(
                mainMap["id"]?.toString(),
                mainMap["slug"]?.toString(),
                mainKey,
            )
            val mainName = firstNonEmpty(
                mainMap["name"]?.toString(),
                mainMap["title"]?.toString(),
                mainId,
            )
            val mainImage = firstNonEmpty(
                mainMap["thumbnail"]?.toString(),
                mainMap["image"]?.toString(),
                mainMap["image_url"]?.toString(),
            )
            val mainArchive = firstNonEmpty(
                mainMap["archive_id"]?.toString(),
                mainMap["archiveId"]?.toString(),
                mainMap["ia_collection"]?.toString(),
            )
            if (mainId.isEmpty()) return
            main.add(
                mapOf(
                    "id" to mainId,
                    "name" to mainName,
                    "image_url" to mainImage,
                    "archive_id" to mainArchive.ifEmpty { null },
                ),
            )

            val subContainer = mapOrListToKeyedMap(
                mainMap["sub_sections"]
                    ?: mainMap["subsections"]
                    ?: mainMap["subSections"]
                    ?: mainMap["children"],
            )
            if (subContainer.isNotEmpty()) {
                subContainer.forEach { (subKey, rawSub) -> appendSub(mainId, subKey, rawSub) }
                return
            }

            mainMap.forEach { (k, v) ->
                if (RESERVED_MAIN_TREE_KEYS.contains(k)) return@forEach
                when (v) {
                    is Map<*, *> -> appendSub(mainId, k, v)
                    is List<*> -> v.forEachIndexed { i, e -> appendSub(mainId, "$k:$i", e) }
                }
            }
        }

        root.forEach { (key, value) -> parseMainBlock(key, value) }

        return SectionsParseResult(main, sub, secondary)
    }

    // ── تحليل السجلّات ──────────────────────────────────────────────

    private fun mapContentFromRtdb(row: Map<String, Any?>): Content =
        Content.fromJson(row + ("id" to (row["id"]?.toString() ?: "")))

    /**
     * حارس قبل عرض أيّ وثيقة. يستبعد:
     *   • وثيقة بلا sourceUrl صالح (تسبّب «لا يوجد مصدر متاح»)
     *   • روابط ليست http(s) — حماية من رابط غير قابل للتشغيل
     *   • المصادر الخارجيّة المحظورة وحالات الترخيص المرفوضة
     * منطق متطابق مع حارس `SearchDatasource`.
     */
    private fun isPlayableAndCompliant(item: Content): Boolean {
        // امتثال الحقوق + صلاحية الرابط عبر الحارس الموحَّد.
        if (!ContentCompliance.isRightsCompliant(item)) return false
        // احترام رأي المُبلِّغ: لا يظهر له المحتوى الذي أبلغ عنه.
        if (HiddenContentService.isHidden(item.id)) return false
        return true
    }

    private fun asMap(value: Any?): Map<String, Any?> = when (value) {
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        else -> emptyMap()
    }

    private fun resolveSubParentsAgainstMains(
        subs: List<SectionMeta>,
        mainLookup: Map<String, String>,
    ): List<SectionMeta> = subs.map { s ->
        val p = s.parentId.trim()
        if (p.isEmpty()) return@map s
        val resolved = mainLookup[p] ?: return@map s
        s.copy(parentId = resolved)
    }

    private fun parseMainSection(json: Map<String, Any?>): SectionMeta? {
        val id = firstNonEmpty(
            json["id"]?.toString(),
            json["slug"]?.toString(),
            json["name"]?.toString(),
        )
        val name = firstNonEmpty(json["name"]?.toString(), json["title"]?.toString(), id)
        if (id.isEmpty() || name.isEmpty()) return null
        val imageUrl = firstNonEmpty(
            json["thumbnail"]?.toString(),
            json["image_url"]?.toString(),
            json["image"]?.toString(),
        )
        return SectionMeta(id = id, name = name, imageUrl = imageUrl, archiveId = pickArchive())
    }

    private fun parseSubSection(json: Map<String, Any?>): SectionMeta? {
        val id = firstNonEmpty(json["id"]?.toString(), json["name"]?.toString())
        val name = firstNonEmpty(json["name"]?.toString(), json["title"]?.toString(), id)
        val parentId = firstNonEmpty(
            json["main_section"]?.toString(),
            json["main_section_id"]?.toString(),
            json["main_section_name"]?.toString(),
        )
        if (id.isEmpty() || name.isEmpty()) return null
        val imageUrl = firstNonEmpty(
            json["thumbnail"]?.toString(),
            json["image_url"]?.toString(),
            json["image"]?.toString(),
        )
        return SectionMeta(
            id = id,
            name = name,
            parentId = parentId,
            imageUrl = imageUrl,
            archiveId = pickArchive(),
            remoteSourceApp = firstNonEmptyNullable(
                listOf(
                    json["remote_source_app"]?.toString(),
                    json["remoteSourceApp"]?.toString(),
                ),
            ),
            remoteSourceLevel = firstNonEmptyNullable(
                listOf(
                    json["remote_source_level"]?.toString(),
                    json["remoteSourceLevel"]?.toString(),
                ),
            ),
            remoteSourceId = firstNonEmptyNullable(
                listOf(
                    json["remote_source_id"]?.toString(),
                    json["remoteSourceId"]?.toString(),
                ),
            ),
        )
    }

    private fun parseSecondarySection(json: Map<String, Any?>): SectionMeta? {
        val id = firstNonEmpty(json["id"]?.toString(), json["name"]?.toString())
        val name = firstNonEmpty(json["name"]?.toString(), json["title"]?.toString(), id)
        val parentId = firstNonEmpty(
            json["sub_section"]?.toString(),
            json["sub_section_id"]?.toString(),
            json["subsection"]?.toString(),
        )
        if (id.isEmpty() || name.isEmpty()) return null
        val imageUrl = firstNonEmpty(
            json["thumbnail"]?.toString(),
            json["image_url"]?.toString(),
            json["image"]?.toString(),
        )
        return SectionMeta(
            id = id,
            name = name,
            parentId = parentId,
            imageUrl = imageUrl,
            archiveId = pickArchive(),
            remoteSourceApp = firstNonEmptyNullable(
                listOf(
                    json["remote_source_app"]?.toString(),
                    json["remoteSourceApp"]?.toString(),
                ),
            ),
            remoteSourceLevel = firstNonEmptyNullable(
                listOf(
                    json["remote_source_level"]?.toString(),
                    json["remoteSourceLevel"]?.toString(),
                ),
            ),
            remoteSourceId = firstNonEmptyNullable(
                listOf(
                    json["remote_source_id"]?.toString(),
                    json["remoteSourceId"]?.toString(),
                ),
            ),
        )
    }

    /**
     * استخراج حقول ربط المصدر الخارجيّ من السجلّ. تبقى غير موجودة إن لم
     * تُضبط من اللوحة — عندها لا يتدخّل جسر المصدر الخارجيّ بتاتاً.
     */
    private fun pickRemoteSource(record: Map<String, Any?>): Map<String, Any?> {
        val app = firstNonEmptyNullable(
            listOf(
                record["remote_source_app"]?.toString(),
                record["remoteSourceApp"]?.toString(),
            ),
        )
        val level = firstNonEmptyNullable(
            listOf(
                record["remote_source_level"]?.toString(),
                record["remoteSourceLevel"]?.toString(),
            ),
        )
        val rid = firstNonEmptyNullable(
            listOf(
                record["remote_source_id"]?.toString(),
                record["remoteSourceId"]?.toString(),
            ),
        )
        if (app == null && level == null && rid == null) return emptyMap()
        return mapOf(
            "remote_source_app" to app,
            "remote_source_level" to level,
            "remote_source_id" to rid,
        )
    }

    /** ⛔ الأرشيف مقطوع — نُعيد null دائماً (نفس سلوك نسخة Flutter). */
    private fun pickArchive(): String? = null

    // ── بدائل احتياطيّة ─────────────────────────────────────────────

    private fun buildGroupedFallback(allItems: List<Content>): List<HomeSection> {
        val grouped = LinkedHashMap<String, MutableList<Content>>()
        for (item in allItems) {
            val key = item.section.ifEmpty { "other" }
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }

        return grouped.map { (key, value) ->
            val sorted = value.sortedContentOldestFirst()
            val types = sorted.mapTo(mutableSetOf()) { it.type }
            val type = if (types.size == 1) sorted.first().type.wire else "mixed"
            val apiSectionName = sorted
                .firstOrNull { !it.sectionName.isNullOrEmpty() }
                ?.sectionName
                ?: sorted.first().sectionName
            HomeSection(
                id = "fallback:$key",
                title = apiSectionName ?: typeFallbackTitle(type) ?: key,
                type = type,
                items = sorted,
            )
        }
    }

    private fun appendFallbackSections(
        sections: List<HomeSection>,
        allItems: List<Content>,
    ): List<HomeSection> {
        val existingIds = buildSet {
            for (section in sections) {
                for (item in section.items) add(item.id)
            }
        }

        val orphanItems = allItems.filter { it.id.isNotEmpty() && it.id !in existingIds }
        if (orphanItems.isEmpty()) return sections

        Log.d(TAG, "appending fallback sections for orphan items: ${orphanItems.size}")
        return sections + buildGroupedFallback(orphanItems)
    }

    // ── تحليل صفوف المحتوى ─────────────────────────────────────────

    private fun parseAndNormalizeContentRows(root: Map<String, Any?>): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()

        fun walk(value: Any?, keyHint: String? = null) {
            when (value) {
                is Map<*, *> -> {
                    val map = asMap(value)
                    if (RtdbUploadNormalizer.shouldTreatAsContentNode(map, keyHint)) {
                        rows.add(
                            mapOf("id" to firstNonEmpty(map["id"]?.toString(), keyHint ?: "")) + map,
                        )
                    }
                    map.forEach { (k, v) -> walk(v, k) }
                }
                is List<*> -> value.forEachIndexed { i, e -> walk(e, "$i") }
            }
        }

        walk(root)
        return rows.map(RtdbUploadNormalizer::normalizeRow)
    }

    private data class SectionMeta(
        val id: String,
        val name: String,
        val parentId: String = "",
        val imageUrl: String? = null,
        val archiveId: String? = null,
        val remoteSourceApp: String? = null,
        val remoteSourceLevel: String? = null,
        val remoteSourceId: String? = null,
    )

    private data class SectionsParseResult(
        val main: List<Map<String, Any?>>,
        val sub: List<Map<String, Any?>>,
        val secondary: List<Map<String, Any?>>,
    )
}

/** يحوّل [com.nebras.mobile.core.error.Failure] إلى استثناء قابل للرمي. */
internal fun com.nebras.mobile.core.error.Failure.toException(): Exception =
    IllegalStateException(message)
