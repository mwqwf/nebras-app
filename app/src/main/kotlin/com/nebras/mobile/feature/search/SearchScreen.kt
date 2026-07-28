package com.nebras.mobile.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.core.widget.SearchBarWidget
import com.nebras.mobile.core.widget.SearchSkeletonLoader
import com.nebras.mobile.feature.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * صفحة البحث المخصّصة — تُفتح عند الضغط على أيقونة البحث في الشريط
 * العلويّ (نمط YouTube). نُقلت هنا حرفياً كلّ وظائف البحث التي كانت
 * مدمجة سابقاً داخل شريط البحث في الرئيسية، **دون أيّ فقدان للوظائف**:
 *   • شريط بحث مع تركيز تلقائيّ.
 *   • سجلّ عمليات البحث الأخيرة (يظهر عند فراغ الاستعلام والتركيز).
 *   • النتائج الحيّة + الاقتراحات + سبب الاحتياط (fallback).
 *   • هيكل تحميل أثناء الجلب.
 * نعيد استخدام نفس [SearchViewModel] المرتبط بمالك الحالة الحاليّ، فالسجلّ
 * والاستعلام محفوظان عند التنقّل بين تبويبات القشرة (نظير الـ provider
 * العموميّ في نسخة Flutter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
) {
    val searchVm: SearchViewModel = viewModel(factory = SearchViewModelFactory)
    val state by searchVm.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // نضمن وصول أقسام الرئيسية للبحث (نفس ما كان يفعله شريط الرئيسية).
    // نُعيد التمرير عند تغيّر الأقسام لأنّ الشاشة قد تُركَّب قبل اكتمال جلبها.
    LaunchedEffect(homeState.sectionsRaw) {
        searchVm.setSectionsForSearch(homeState.sectionsRaw)
    }

    // تركيز تلقائيّ على الحقل بعد أوّل إطار — مقابل `requestFocus()` في
    // `addPostFrameCallback`. نلفّه بحارس لأنّ العقدة قد لا تكون مركَّبة بعد.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val hasQuery = state.query.trim().isNotEmpty()
    val showRecent = !hasQuery && state.recentSearches.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    // `titleSpacing: 0` + حشوة 12 من جهة البداية (اليمين في RTL).
                    Box(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                        SearchBarWidget(
                            query = state.query,
                            onQueryChange = { value ->
                                homeViewModel.resetIdleTimer()
                                searchVm.onQueryChanged(value)
                            },
                            onClear = {
                                searchVm.clearAll()
                                runCatching { focusRequester.requestFocus() }
                            },
                            onSubmitted = { value ->
                                searchVm.submitCurrentQuery(value)
                                focusManager.clearFocus()
                            },
                            hintText = "ابحث عن محتوى...",
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                },
                actions = {
                    // أيقونة الفلاتر — تفتح ورقة التصفية الجاهزة.
                    // نقطة صغيرة تظهر عند وجود فلاتر فعّالة.
                    Box(
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "التصفية",
                            )
                        }
                        if (state.hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 10.dp, start = 10.dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                // أعد تنفيذ البحث الحاليّ عند السحب للتحديث. ننتظر انتهاء
                // العمليّة كي يبقى مؤشّر السحب ظاهراً حتى وصول النتائج.
                scope.launch {
                    if (state.query.trim().isNotEmpty()) {
                        isRefreshing = true
                        runCatching { searchVm.refreshCurrentQuery() }
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            // 🐞 بناء كسول للنتائج: عند وجود استعلام نُمرّر [SearchResultsPanel]
            // (وهو قائمة كسولة) مباشرةً كي تُبنى الصفوف عند ظهورها فقط. حالة
            // التحميل/السجلّ/الفراغ تبقى داخل عمود قابل للتمرير كما كانت
            // (قوائم قصيرة، لا حاجة للكسل). لا تغيّر في الشكل أو الترتيب.
            if (hasQuery && !state.isLoading) {
                SearchResultsPanel(
                    hits = state.hits,
                    suggestions = state.suggestions,
                    fallbackReason = state.fallbackReason,
                    navController = navController,
                    allSections = homeState.sectionsRaw,
                    onMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                    query = state.query,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    if (hasQuery) {
                        // hasQuery هنا يعني isLoading == true (الفرع أعلاه أخذ
                        // حالة النتائج)، فنعرض هيكل التحميل.
                        SearchSkeletonLoader()
                    } else if (showRecent) {
                        RecentSearchesPanel(
                            recentSearches = state.recentSearches,
                            onSelect = { term ->
                                searchVm.submitRecentSearch(term)
                                focusManager.clearFocus()
                            },
                            onRemove = { term -> searchVm.removeRecentSearch(term) },
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        SearchFilterSheet(
            state = state,
            onTypeSelected = { type -> searchVm.setTypeFilter(type) },
            onSectionSelected = { section -> searchVm.setSectionFilter(section) },
            onClearFilters = { searchVm.clearFilters() },
            onDismiss = { showFilters = false },
        )
    }
}

/** مصنع [SearchViewModel] — بديل حقن التبعيّات بلا مكتبة DI. */
private object SearchViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SearchViewModel(ServiceLocator.searchDatasource) as T
}
