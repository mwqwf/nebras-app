package com.nebras.mobile.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.InterestProfileService
import com.nebras.mobile.core.service.SearchFallbackReason
import com.nebras.mobile.core.service.SearchItemType
import com.nebras.mobile.core.service.SearchResultItem
import com.nebras.mobile.core.service.SearchService
import com.nebras.mobile.core.service.UserBehaviorTracker
import com.nebras.mobile.feature.home.model.HomeSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** حالة شاشة البحث — نقل `search/provider/search_provider.dart`. */
data class SearchUiState(
    val query: String = "",
    val selectedType: ContentType? = null,
    val selectedSection: String? = null,
    val selectedSubSection: String? = null,
    val sections: List<SearchSectionOption> = emptyList(),
    val hits: List<SearchResultItem> = emptyList(),
    val suggestions: List<SearchResultItem> = emptyList(),
    val fallbackReason: SearchFallbackReason = SearchFallbackReason.NONE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val recentSearches: List<String> = emptyList(),
) {
    val usedFallback: Boolean get() = fallbackReason != SearchFallbackReason.NONE

    val hasActiveFilters: Boolean
        get() = selectedType != null || selectedSection != null || selectedSubSection != null

    /** نتائج المحتوى فقط (بلا الأقسام) — لقوائم التشغيل. */
    val results: List<Content>
        get() = hits.mapNotNull { if (it.type == SearchItemType.ITEM) it.content else null }
}

class SearchViewModel(private val datasource: SearchDatasource) : ViewModel() {

    private companion object {
        const val TAG = "SearchViewModel"
        const val DEBOUNCE_MILLIS = 400L
    }

    private val searchService = SearchService(
        firebaseSearch = { query, type, section, subSection ->
            datasource.search(query, type, section, subSection)
        },
    )

    private val _state = MutableStateFlow(
        SearchUiState(recentSearches = UserBehaviorTracker.lastSearches),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var sectionsForSearch: List<HomeSection> = emptyList()
    private var debounceJob: Job? = null

    init {
        // تحديث سجلّ البحث المعروض عند تغيّر المتتبّع.
        viewModelScope.launch {
            UserBehaviorTracker.revision.collect {
                _state.value = _state.value.copy(
                    recentSearches = UserBehaviorTracker.lastSearches,
                )
            }
        }
    }

    fun removeRecentSearch(query: String) = UserBehaviorTracker.removeSearch(query)

    fun clearSearchHistory() = UserBehaviorTracker.clearSearchHistory()

    /** بحث فوريّ من سجلّ البحث (بلا debounce). */
    fun submitRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        debounceJob?.cancel()
        _state.value = _state.value.copy(query = q, isLoading = true, errorMessage = null)
        viewModelScope.launch { executeSearch() }
    }

    /** إرسال الاستعلام الحاليّ (زرّ البحث في لوحة المفاتيح). */
    fun submitCurrentQuery(override: String? = null) {
        val q = (override ?: _state.value.query).trim()
        if (q.isEmpty()) return
        debounceJob?.cancel()
        _state.value = _state.value.copy(query = q, isLoading = true, errorMessage = null)
        viewModelScope.launch { executeSearch() }
    }

    suspend fun refreshCurrentQuery() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        debounceJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        executeSearch()
    }

    /**
     * تمرير أقسام الرئيسية للبحث المحلّيّ. إن وصلت الأقسام بعد أن كتب
     * المستخدم استعلاماً نُعيد تنفيذه ليشملها.
     */
    fun setSectionsForSearch(sections: List<HomeSection>) {
        val hadSections = sectionsForSearch.isNotEmpty()
        sectionsForSearch = sections
        if (!hadSections && sections.isNotEmpty() && _state.value.query.trim().isNotEmpty()) {
            viewModelScope.launch { executeSearch() }
        }
    }

    /** كتابة حيّة مع debounce 400ms — نفس نسخة Flutter. */
    fun onQueryChanged(value: String) {
        _state.value = _state.value.copy(query = value)
        debounceJob?.cancel()
        if (value.trim().isEmpty()) {
            _state.value = _state.value.copy(
                hits = emptyList(),
                suggestions = emptyList(),
                fallbackReason = SearchFallbackReason.NONE,
                isLoading = false,
                errorMessage = null,
            )
            return
        }
        if (!_state.value.isLoading) {
            _state.value = _state.value.copy(isLoading = true)
        }
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            executeSearch()
        }
    }

    fun setTypeFilter(type: ContentType?) {
        _state.value = _state.value.copy(selectedType = type)
        reSearchIfActive()
    }

    fun setSectionFilter(section: String?) {
        _state.value = _state.value.copy(selectedSection = section, selectedSubSection = null)
        reSearchIfActive()
    }

    fun setSubSectionFilter(subSection: String?) {
        _state.value = _state.value.copy(selectedSubSection = subSection)
        reSearchIfActive()
    }

    fun clearFilters() {
        _state.value = _state.value.copy(
            selectedType = null,
            selectedSection = null,
            selectedSubSection = null,
        )
        reSearchIfActive()
    }

    fun clearAll() {
        debounceJob?.cancel()
        _state.value = _state.value.copy(
            query = "",
            selectedType = null,
            selectedSection = null,
            selectedSubSection = null,
            hits = emptyList(),
            suggestions = emptyList(),
            fallbackReason = SearchFallbackReason.NONE,
            errorMessage = null,
        )
    }

    fun loadSections() {
        viewModelScope.launch {
            runCatching { datasource.getSections() }
                .onSuccess { sections ->
                    Log.d(TAG, "sections loaded: ${sections.size}")
                    _state.value = _state.value.copy(sections = sections)
                }
                .onFailure { Log.d(TAG, "Failed to load sections: ${it.message}") }
        }
    }

    suspend fun loadSectionContent(sectionId: String): List<Content> {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return runCatching { datasource.getSectionContent(sectionId) }
            .onSuccess { items ->
                Log.d(TAG, "section \"$sectionId\" content count: ${items.size}")
                _state.value = _state.value.copy(isLoading = false)
            }
            .getOrElse { e ->
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.toString())
                emptyList()
            }
    }

    private fun reSearchIfActive() {
        if (_state.value.query.trim().isNotEmpty()) {
            viewModelScope.launch { executeSearch() }
        }
    }

    private suspend fun executeSearch() {
        val currentQuery = _state.value.query.trim()
        if (currentQuery.isEmpty()) return
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)

        runCatching {
            searchService.search(
                query = currentQuery,
                sections = effectiveSectionsForSearch(),
                type = _state.value.selectedType,
                section = _state.value.selectedSection,
                subSection = _state.value.selectedSubSection,
            )
        }.onSuccess { result ->
            // حارس: استعلام أحدث كُتب أثناء البحث — تجاهل النتيجة المتأخّرة.
            if (_state.value.query.trim() != currentQuery) {
                _state.value = _state.value.copy(isLoading = false)
                return
            }
            _state.value = _state.value.copy(
                hits = result.items,
                suggestions = result.suggestions,
                fallbackReason = result.fallbackReason,
                isLoading = false,
            )
            Log.d(
                TAG,
                "unified search: results=${result.items.size}, " +
                    "suggestions=${result.suggestions.size}, " +
                    "fallback=${result.fallbackReason}, query=\"$currentQuery\"",
            )
            // إشارة اهتمام فقط عند تطابق حقيقيّ (لا نتائج fallback).
            val realMatch = result.isNotEmpty && !result.usedFallback
            if (realMatch) {
                UserBehaviorTracker.recordSearch(currentQuery)
                InterestProfileService.onSearchQuery(currentQuery)
            }
        }.onFailure { e ->
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.toString())
        }
    }

    /**
     * عند غياب أقسام الرئيسية (فُتح البحث قبل تحميلها) نبني أقساماً رمزيّة
     * من خيارات الفلتر كي يبقى بحث الأقسام ممكناً.
     */
    private fun effectiveSectionsForSearch(): List<HomeSection> {
        if (sectionsForSearch.isNotEmpty()) return sectionsForSearch
        val options = _state.value.sections
        if (options.isEmpty()) return emptyList()
        return options.map {
            HomeSection(
                id = "main:${it.id}",
                title = it.name,
                type = "main_section",
                items = emptyList(),
            )
        }
    }
}
