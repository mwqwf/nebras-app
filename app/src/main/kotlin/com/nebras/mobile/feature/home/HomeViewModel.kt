package com.nebras.mobile.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.service.PopularityFeedService
import com.nebras.mobile.core.service.RecommendationService
import com.nebras.mobile.core.service.UserBehaviorTracker
import com.nebras.mobile.core.util.extractKeywords
import com.nebras.mobile.feature.home.data.GetHomeDataUseCase
import com.nebras.mobile.feature.home.data.HomeRailBuilders
import com.nebras.mobile.feature.home.model.HomeRail
import com.nebras.mobile.feature.home.model.HomeSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * حالة الشاشة الرئيسيّة — نقل `features/home/providers/home_provider.dart`.
 *
 * لا يقوم بأيّ نداءات شبكة مباشرة — يفوّض ذلك لـ [GetHomeDataUseCase]، الذي
 * يقرأ حصرياً من Firestore. لا يوجد أيّ جسر لمصادر خارجيّة.
 */
data class HomeUiState(
    /** أقسام Firestore بعد الترتيب الذكيّ. */
    val sections: List<HomeSection> = emptyList(),
    /** نسخة خام قبل الترتيب الذكيّ. */
    val sectionsRaw: List<HomeSection> = emptyList(),
    /** رفوف الصفحة الرئيسيّة (ليست أقسام Firestore). */
    val homeRails: List<HomeRail> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    /**
     * هل اكتمل بناء الرفوف مرّة واحدة على الأقلّ (بغضّ النظر عن النتيجة)؟
     * 🐞 نُميّز «الرفوف قيد البناء» (⇒ skeleton) عن «بُنيت وخرجت فارغة»
     * (⇒ حالة فراغ دائمة لا skeleton أبديّ).
     */
    val railsBuilt: Boolean = false,
    val personalizationEnabled: Boolean = false,
    val suggestedFeed: List<Content> = emptyList(),
    val isShowingSuggestions: Boolean = false,
) {
    /**
     * 🐞 ثابت `false` بعد جعل [HomeViewModel.loadMore] لا-عمل (الرئيسية
     * اكتشاف فقط). مُبقى لتوافق الواجهة.
     */
    val isFetchingMore: Boolean get() = false
}

class HomeViewModel(
    private val getHomeDataUseCase: GetHomeDataUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"

        /**
         * مهلة انتظار أوّل لقطة قبل تشغيل مسار الاحتياط الأوفلاين.
         * 🐞 رُفعت من 3s إلى 7s: 3s كانت تنطلق **قبل** اكتمال إقلاع Firebase
         * البارد (4–8s) فيُحمَّل المحتوى مرّتين ويومض skeleton فوق محتوى وصل
         * للتوّ. 7s تمنح البثّ الحيّ فرصته وتبقى شبكة أمان للإنترنت الضعيف.
         */
        const val WEAK_NETWORK_BOOT_TIMEOUT_MILLIS = 7_000L

        /**
         * سقف صارم للقراءة الاحتياطيّة لمرّة واحدة. يجب أن يكون أكبر بكثير
         * من مهلة الإقلاع — إقلاع Firebase البارد قد يستغرق 4–8 ثوانٍ، وسقف
         * صغير يضمن فشل القراءة دائماً على أوّل تثبيت بلا كاش محلّي.
         */
        private const val FETCH_TIMEOUT_MILLIS = 30_000L

        private const val IDLE_THRESHOLD_MILLIS = 6_000L
    }

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var firebaseSections: List<HomeSection> = emptyList()

    /** اشتراك البثّ الحيّ — نحتفظ به لإلغائه عند إعادة الاشتراك أو التخلّص. */
    private var liveJob: Job? = null

    /** مؤقّت الاحتياط للإنترنت الضعيف. */
    private var bootFallbackJob: Job? = null

    private var idleJob: Job? = null

    private var railsBuilding = false

    /**
     * نحاول جلب معرّفات الشعبيّة من الشبكة مرّة واحدة فقط لكلّ جلسة. بدون
     * هذا الحارس كان كلّ إعادة بناء للرفوف تُعيد المحاولة — وإن رُفضت القراءة
     * بـ permission-denied تدفّقت مئات المحاولات الفاشلة في السجلّ.
     */
    private var popularRefreshAttempted = false

    init {
        // نعيد البناء كلّما تغيّر ملفّ المستخدم السلوكيّ كي يُحدَّث الترتيب
        // الذكيّ للأقسام — دون أيّ طلب شبكة. العمليّة خفيفة ومحصورة بالواجهة.
        viewModelScope.launch {
            UserBehaviorTracker.revision.collect {
                if (firebaseSections.isNotEmpty()) rebuildHomeRails()
            }
        }
    }

    /**
     * يضبط حالة التخصيص ويعيد بناء الرفوف فوراً إن تغيّرت. تُستدعى عند معرفة
     * حالة الحساب أو تغيّرها (دخول/خروج).
     */
    fun setPersonalizationEnabled(value: Boolean) {
        if (_state.value.personalizationEnabled == value) return
        _state.update { it.copy(personalizationEnabled = value) }
        viewModelScope.launch { rebuildHomeRails() }
    }

    // ── الاقتراح التلقائيّ عند الخمول ───────────────────────────────

    fun resetIdleTimer() {
        idleJob?.cancel()
        if (_state.value.isShowingSuggestions) {
            _state.update { it.copy(isShowingSuggestions = false, suggestedFeed = emptyList()) }
        }
        idleJob = viewModelScope.launch {
            delay(IDLE_THRESHOLD_MILLIS)
            onIdle()
        }
    }

    private fun onIdle() {
        if (personalizedFeed().isNotEmpty()) return
        val suggestions = buildSuggestedFeed()
        if (suggestions.isEmpty()) return
        _state.update { it.copy(suggestedFeed = suggestions, isShowingSuggestions = true) }
    }

    /** الخلاصة الفعّالة: المُشخصَنة إن وُجدت وإلا الاقتراحات عند الخمول. */
    fun effectiveFeed(): List<Content> {
        val personal = personalizedFeed()
        if (personal.isNotEmpty()) return personal
        val s = _state.value
        return if (s.isShowingSuggestions && s.suggestedFeed.isNotEmpty()) {
            s.suggestedFeed
        } else {
            emptyList()
        }
    }

    private fun buildSuggestedFeed(maxItems: Int = 30): List<Content> {
        val secs = _state.value.sections
        if (secs.isEmpty()) return emptyList()

        val byId = secs.associateBy { it.id }
        val out = mutableListOf<Content>()
        val seen = mutableSetOf<String>()

        for (id in UserBehaviorTracker.topSectionIds(6)) {
            val s = byId[id] ?: continue
            val items = s.items.sortedByDescending { it.createdAt }
            for (item in items.take(6)) {
                if (seen.add(item.id)) out.add(item)
                if (out.size >= maxItems) return out
            }
        }

        // شعبيّة محلّية من view_count (بديل قسم Firestore القديم most_watched).
        val popularPool = secs.flatMap { it.items }.sortedWith(
            compareByDescending<Content> { it.viewCount }.thenByDescending { it.createdAt },
        )
        for (item in popularPool.take(8)) {
            if (seen.add(item.id)) out.add(item)
            if (out.size >= maxItems) return out
        }

        val pools = secs
            .map { s -> s.items.sortedByDescending { it.createdAt } }
            .filter { it.isNotEmpty() }
        var depth = 0
        while (out.size < maxItems) {
            var advanced = false
            for (pool in pools) {
                if (depth >= pool.size) continue
                advanced = true
                val item = pool[depth]
                if (seen.add(item.id)) out.add(item)
                if (out.size >= maxItems) break
            }
            if (!advanced) break
            depth++
        }
        return out
    }

    // ── الترتيب الذكيّ للأقسام ──────────────────────────────────────

    private fun rankSections(input: List<HomeSection>): List<HomeSection> {
        if (input.isEmpty()) return input
        if (!UserBehaviorTracker.isLoaded) return input

        val scored = input.mapIndexed { i, s ->
            val baseRank = -i.toDouble() * 0.001
            // (أُزيل تعزيز الفئات الدينيّة: نبراس منصّة معرفيّة عامّة، فلا
            //  يجوز تحيّز الترتيب لفئة على حساب البقيّة.)
            var visitBoost = if (UserBehaviorTracker.sectionScore(s.id) > 0) {
                (1 + UserBehaviorTracker.sectionScore(s.id)) * 0.8
            } else {
                0.0
            }
            visitBoost *= UserBehaviorTracker.visitDecayMultiplier(s.id)
            val quickPenalty = UserBehaviorTracker.quickExitPenalty(s.id)

            var keywordBoost = 0.0
            for (k in extractKeywords(s.title, 6)) {
                keywordBoost += UserBehaviorTracker.keywordScore(k) * 0.25
            }
            if (keywordBoost > 5.0) keywordBoost = 5.0

            ScoredSection(s, i, visitBoost + keywordBoost + quickPenalty + baseRank)
        }

        return scored
            .sortedWith(compareByDescending<ScoredSection> { it.score }.thenBy { it.index })
            .map { it.section }
    }

    fun personalizedFeed(): List<Content> {
        val ranked = _state.value.sections
        val allContent = LinkedHashMap<String, Content>()
        for (section in ranked) {
            for (item in section.items) allContent[item.id] = item
        }
        return RecommendationService.rank(allContent.values)
    }

    // ── البثّ الحيّ ─────────────────────────────────────────────────

    /**
     * يبدأ اشتراكاً مباشراً مع Firestore — أيّ محتوى أو قسم جديد من اللوحة
     * يصل خلال ميلّي ثانية بلا مسح بيانات ولا إعادة تشغيل.
     * آمن لاستدعائه أكثر من مرّة؛ يُلغي الاشتراك القديم قبل فتح جديد.
     */
    fun startWatching() {
        liveJob?.cancel()

        if (firebaseSections.isEmpty()) {
            // عرض فوريّ: نبذُر الشاشة بآخر لقطة محفوظة محليّاً كي يظهر المحتوى
            // لحظة الفتح للمستخدم العائد بدل skeleton متحرّك لثوانٍ. أوّل لقطة
            // حيّة ستستبدلها بالشجرة الكاملة.
            val cached = getHomeDataUseCase.cached()
            if (cached.isNotEmpty()) {
                applySections(cached, loading = false)
                viewModelScope.launch { rebuildHomeRails() }
            } else {
                // أوّل تثبيت — لا كاش محلّي بعد، فلا مفرّ من انتظار الشبكة.
                _state.update { it.copy(isLoading = true, errorMessage = null, currentPage = 1) }
            }
        }

        liveJob = viewModelScope.launch {
            getHomeDataUseCase.watch(page = 1)
                .catch { err ->
                    Log.d(TAG, "live stream error: ${err.message}")
                    _state.update { it.copy(isLoading = false, errorMessage = err.toString()) }
                }
                .collect { result ->
                    applySections(result, loading = false)
                    val totalItems = result.sumOf { it.items.size }
                    Log.d(TAG, "live update: sections=${result.size}, items=$totalItems")
                    rebuildHomeRails()
                }
        }

        // في الإنترنت الضعيف قد لا ترسل Firestore أوّل لقطة بسرعة. بعد المهلة
        // نقرأ مسار getHomeData الذي يملك مسار احتياط محلّيّ، فلا تبقى الشاشة
        // عالقة. نُلغي أيّ مؤقّت سابق كي لا يتراكم مع كلّ استدعاء.
        bootFallbackJob?.cancel()
        bootFallbackJob = viewModelScope.launch {
            delay(WEAK_NETWORK_BOOT_TIMEOUT_MILLIS)
            if (_state.value.isLoading && firebaseSections.isEmpty()) {
                loadInitialFromWeakNetworkFallback()
            }
        }
    }

    private suspend fun loadInitialFromWeakNetworkFallback() {
        // 🐞 حارس سباق — إن وصل البثّ الحيّ بأقسام خلال نافذة الانتظار لا
        // نُطلق القراءة الاحتياطيّة إطلاقاً (كانت تُحمّل المحتوى مرّتين وتومض
        // skeleton فوق محتوى ظاهر).
        if (firebaseSections.isNotEmpty()) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        _state.update { it.copy(isLoading = false) }
        loadInitial()
    }

    /** قراءة لمرّة واحدة — مسار احتياطيّ فقط. */
    suspend fun loadInitial() {
        if (_state.value.isLoading) return
        // 🐞 إن كانت الأقسام قد وصلت فعلاً (بثّ حيّ أو بذرة الكاش) لا نُحوّل
        // الشاشة إلى skeleton ولا نُعيد الجلب.
        if (firebaseSections.isNotEmpty()) return

        _state.update {
            it.copy(isLoading = true, errorMessage = null, currentPage = 1, hasMore = true)
        }

        runCatching {
            withTimeout(FETCH_TIMEOUT_MILLIS) { getHomeDataUseCase(page = 1) }
        }.onSuccess { result ->
            applySections(result, loading = false)
            val totalItems = result.sumOf { it.items.size }
            Log.d(TAG, "sections loaded: ${result.size}, total items: $totalItems")
            rebuildHomeRails()
        }.onFailure { e ->
            _state.update { it.copy(isLoading = false, errorMessage = e.toString()) }
        }
    }

    private fun applySections(result: List<HomeSection>, loading: Boolean) {
        firebaseSections = result
        val ranked = rankSections(result)
        _state.update {
            it.copy(
                sections = ranked,
                sectionsRaw = result,
                hasMore = result.isNotEmpty(),
                currentPage = 1,
                isLoading = loading,
                errorMessage = null,
            )
        }
    }

    // ── بناء الرفوف ────────────────────────────────────────────────

    private suspend fun rebuildHomeRails() {
        if (railsBuilding || firebaseSections.isEmpty()) return
        railsBuilding = true
        try {
            // لا ننتظر شبكة الشعبيّة هنا: انتظارها كان يحجب بناء الرفوف على
            // قراءة Firestore فتبقى الرئيسية تعرض مؤشّر تحميل ثوانٍ. نبني
            // فوراً بالمعرّفات المتوفّرة في الذاكرة (قد تكون فارغة فيسقط رفّ
            // «الأكثر مشاهدة» على ترتيب view_count محليّاً) ثم نُحدّثها لاحقاً.
            val popularIds = PopularityFeedService.popularIds
            val built = HomeRailBuilders.buildAll(
                sections = firebaseSections,
                popularIds = popularIds,
                personalized = _state.value.personalizationEnabled,
            )
            // اكتمل بناء واحد على الأقلّ — تخرج الشاشة من skeleton حتى لو
            // كانت القائمة فارغة (تظهر عندها حالة الفراغ بدل دوران أبديّ).
            _state.update { it.copy(homeRails = built, railsBuilt = true) }
        } catch (e: Throwable) {
            Log.d(TAG, "rebuild rails failed: ${e.message}")
        } finally {
            railsBuilding = false
        }

        // تحديث معرّفات الشعبيّة من الشبكة مرّة واحدة لكلّ جلسة، ثمّ إعادة
        // بناء الرفوف فقط إن تغيّرت — دون حجب أوّل عرض ودون إغراق السجلّ.
        if (!popularRefreshAttempted && PopularityFeedService.popularIds.isEmpty()) {
            popularRefreshAttempted = true
            viewModelScope.launch { refreshPopularIdsThenRebuild() }
        }
    }

    private suspend fun refreshPopularIdsThenRebuild() {
        runCatching {
            val updated = PopularityFeedService.loadPopularIds()
            if (updated.isEmpty() || firebaseSections.isEmpty()) return
            val built = HomeRailBuilders.buildAll(
                sections = firebaseSections,
                popularIds = updated,
                personalized = _state.value.personalizationEnabled,
            )
            _state.update { it.copy(homeRails = built, railsBuilt = true) }
        }.onFailure { Log.d(TAG, "popular refresh failed: ${it.message}") }
    }

    /** يُستدعى من الواجهة عند تغيّر ملفّ الاهتمامات. */
    fun refreshHomeRails() {
        viewModelScope.launch { rebuildHomeRails() }
    }

    // ── السحب للتحديث ──────────────────────────────────────────────

    fun refresh() {
        // إن كنّا مشتركين بالبثّ الحيّ أعد الاشتراك لإجبار Firestore على
        // إعادة الإرسال (مفيد عند استرجاع الشبكة بعد انقطاع).
        if (liveJob != null) {
            startWatching()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(errorMessage = null, currentPage = 1, hasMore = true) }
            runCatching { getHomeDataUseCase(page = 1) }
                .onSuccess { result ->
                    applySections(result, loading = false)
                    Log.d(TAG, "refresh sections: ${result.size}")
                    rebuildHomeRails()
                }
                .onFailure { e ->
                    _state.update { it.copy(errorMessage = e.toString()) }
                }
        }
    }

    /**
     * 🐞 لا-عمل (no-op) مقصود. الرئيسية شاشة **اكتشاف** تتغذّى من بثّ حيّ
     * لأحدث المحتوى (نافذة أحدث 300 وثيقة).
     *
     * النسخة السابقة كانت تستدعي `getHomeData(page: nextPage)` — لكنّ ذلك
     * المسار يقرأ **نفس** أحدث 300 وثيقة دائماً (لا cursor/offset)، فكان
     * الدمج يُزيلها كمكرّرات إلى صفر: قراءات مهدورة بلا محتوى أقدم. ترقيم
     * cursor حقيقيّ يتطلّب تعديل مسار التحديث الحيّ (الذي **يستبدل** الأقسام
     * مع كلّ لقطة) فيكسر القاعدة الذهبيّة «لا تمسّ البثّ الحيّ». الكتالوج
     * الكامل يُبلَغ عبر البحث وتصفّح الأقسام.
     */
    fun loadMore() {
        // مقصود: لا شيء. انظر التوثيق أعلاه.
    }

    fun retry() = startWatching()

    override fun onCleared() {
        idleJob?.cancel()
        bootFallbackJob?.cancel()
        liveJob?.cancel()
        super.onCleared()
    }

    private data class ScoredSection(
        val section: HomeSection,
        val index: Int,
        val score: Double,
    )
}

/** مساعد مختصر لتحديث [MutableStateFlow] بنمط `copy`. */
internal inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
