package com.nebras.mobile.core.service

/**
 * مسح إشارات التخصيص **المحليّة** فقط.
 *
 * لا يمسّ `view_count` / `play_count` على Firestore — تبقى مجهولة وعامّة.
 * يُستدعى من «نسيان اهتماماتي» ومن `signOut`.
 */
object PersonalizationResetService {

    fun forgetAllLocalSignals() {
        InterestProfileService.clearProfile()
        UserBehaviorTracker.clear()
        ContinueWatchingService.clearAll()
    }
}
