package com.nebras.mobile.feature.home.data

import com.nebras.mobile.feature.home.model.HomeSection
import kotlinx.coroutines.flow.Flow

/**
 * عقد مستودع الرئيسية + تنفيذه + حالة الاستخدام النحيفة.
 * نقل `features/home/domain/{home_repository,home_repository_impl,get_home_data_usecase}.dart`
 * في ملفّ واحد (ثلاثة أصداف صغيرة لا تستحقّ ثلاثة ملفّات في Kotlin).
 */
interface HomeRepository {
    suspend fun getHomeData(page: Int = 1): List<HomeSection>

    /**
     * تدفّق حيّ — يتدفّق لحظياً عند أيّ تغيير في Firestore. يضمن ظهور
     * المحتوى الجديد فوراً دون إعادة تشغيل التطبيق أو مسح التخزين.
     */
    fun watchHomeData(page: Int = 1): Flow<List<HomeSection>>

    /** لقطة محفوظة محليّاً تُعرض فوراً عند الإقلاع قبل أوّل لقطة حيّة. */
    fun cachedHomeSections(): List<HomeSection>
}

/** تفويض كامل إلى [HomeDatasource] — بلا أيّ منطق واجهة. */
class HomeRepositoryImpl(private val datasource: HomeDatasource) : HomeRepository {

    override suspend fun getHomeData(page: Int): List<HomeSection> =
        datasource.getHomeData(page)

    /**
     * نُبقي واجهة المستودع كما هي، لكن نمرّر مسار الاحتياط الأوفلاين من
     * المصدر حتى تعرض الشاشة آخر ميتادات محفوظة إن فشل المستمع قبل أوّل لقطة.
     */
    override fun watchHomeData(page: Int): Flow<List<HomeSection>> =
        datasource.watchHomeDataWithOfflineFallback(page)

    override fun cachedHomeSections(): List<HomeSection> = datasource.cachedHomeSections()
}

/** غلاف نحيف حول [HomeRepository] — تمرير مباشر بلا منطق إضافيّ. */
class GetHomeDataUseCase(private val repository: HomeRepository) {

    suspend operator fun invoke(page: Int = 1): List<HomeSection> =
        repository.getHomeData(page)

    fun watch(page: Int = 1): Flow<List<HomeSection>> = repository.watchHomeData(page)

    fun cached(): List<HomeSection> = repository.cachedHomeSections()
}
