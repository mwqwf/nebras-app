package com.nebras.mobile.feature.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.service.ContentReportService
import com.nebras.mobile.core.service.HiddenContentService
import com.nebras.mobile.core.service.PersonalizationResetService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class AuthStatus { UNKNOWN, SIGNED_OUT, SIGNED_IN }

/** حالة المصادقة — نقل `auth/provider/auth_provider.dart`. */
data class AuthUiState(
    val status: AuthStatus = AuthStatus.UNKNOWN,
    val user: UserProfile? = null,
    val isBusy: Boolean = false,
    val lastError: String? = null,
) {
    val isSignedIn: Boolean get() = status == AuthStatus.SIGNED_IN

    /** ضيف = «داخل» بلا ملفّ مستخدم. */
    val isGuest: Boolean get() = status == AuthStatus.SIGNED_IN && user == null
}

class AuthViewModel(
    private val service: AuthService,
    private val store: LocalStore,
) : ViewModel() {

    private companion object {
        const val TAG = "AuthViewModel"
        const val KEY_USER = "auth_current_user_v1"
        const val BOOTSTRAP_TIMEOUT_MILLIS = 3_000L
    }

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /**
     * الإقلاع: نبذُر الحالة من الملفّ المحفوظ (دخول فوريّ للمستخدم العائد)
     * ثمّ نحاول الدخول الصامت بمهلة قصيرة — نجاحه يحدّث الملفّ، وفشله مع
     * غياب ملفّ محفوظ يعني «خارج».
     */
    fun bootstrap() {
        viewModelScope.launch {
            var user: UserProfile? = null
            runCatching {
                val raw = store.getString(KEY_USER)
                if (!raw.isNullOrEmpty()) {
                    runCatching { UserProfile.fromJson(JSONObject(raw)) }
                        .onSuccess { user = it }
                        .onFailure {
                            Log.d(TAG, "cached user parse failed: ${it.message}")
                            store.remove(KEY_USER)
                        }
                }
                if (user != null) {
                    _state.value = AuthUiState(status = AuthStatus.SIGNED_IN, user = user)
                }

                val silent = withTimeoutOrNull(BOOTSTRAP_TIMEOUT_MILLIS) {
                    service.signInSilently()
                }
                if (silent != null) {
                    user = silent
                    persist(silent)
                    _state.value = AuthUiState(status = AuthStatus.SIGNED_IN, user = silent)
                } else if (user == null) {
                    _state.value = AuthUiState(status = AuthStatus.SIGNED_OUT)
                }
            }.onFailure {
                Log.d(TAG, "bootstrap error: ${it.message}")
                if (user == null) {
                    _state.value = AuthUiState(status = AuthStatus.SIGNED_OUT)
                }
            }
        }
    }

    /** يعود true عند نجاح الدخول، وfalse عند الإلغاء أو الفشل. */
    suspend fun signInWithGoogle(activityContext: Context): Boolean {
        if (_state.value.isBusy) return false
        _state.value = _state.value.copy(isBusy = true, lastError = null)
        return try {
            val profile = service.signInInteractive(activityContext)
            if (profile == null) {
                _state.value = _state.value.copy(isBusy = false)
                false // ألغى المستخدم.
            } else {
                persist(profile)
                _state.value = AuthUiState(
                    status = AuthStatus.SIGNED_IN,
                    user = profile,
                    isBusy = false,
                )
                true
            }
        } catch (e: AuthException) {
            _state.value = _state.value.copy(isBusy = false, lastError = friendlyError(e))
            false
        } catch (e: Throwable) {
            _state.value = _state.value.copy(isBusy = false, lastError = e.toString())
            false
        }
    }

    /** دخول كضيف: «داخل» بلا ملفّ مستخدم — لا تخصيص ولا رفوف شخصيّة. */
    fun continueAsGuest() {
        _state.value = AuthUiState(status = AuthStatus.SIGNED_IN, user = null)
    }

    fun exitGuest() {
        if (_state.value.user != null) return // ليس ضيفاً — تجاهَل.
        _state.value = AuthUiState(status = AuthStatus.SIGNED_OUT)
    }

    fun signOut() {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true)
        viewModelScope.launch {
            try {
                service.signOut()
                PersonalizationResetService.forgetAllLocalSignals()
            } finally {
                store.remove(KEY_USER)
                _state.value = AuthUiState(status = AuthStatus.SIGNED_OUT)
            }
        }
    }

    /**
     * حذف الحساب: تنظيف بلاغات المستخدم من السحابة (**قبل** حذف Auth —
     * القاعدة تشترط `reporterUid == request.auth.uid`)، ثمّ حذف الحساب،
     * ثمّ مسح كلّ الإشارات المحليّة.
     */
    suspend fun deleteAccount(activityContext: Context): Boolean {
        if (_state.value.isBusy) return false
        _state.value = _state.value.copy(isBusy = true, lastError = null)
        return try {
            runCatching { ContentReportService.deleteReportsForCurrentUser() }
                .onFailure { Log.d(TAG, "cloud report cleanup skipped: ${it.message}") }
            service.deleteAccount(activityContext)
            PersonalizationResetService.forgetAllLocalSignals()
            HiddenContentService.clearAll()
            store.remove(KEY_USER)
            _state.value = AuthUiState(status = AuthStatus.SIGNED_OUT)
            true
        } catch (e: AuthException) {
            _state.value = _state.value.copy(isBusy = false, lastError = e.message)
            false
        } catch (e: Throwable) {
            _state.value = _state.value.copy(isBusy = false, lastError = e.toString())
            false
        }
    }

    fun clearError() {
        if (_state.value.lastError == null) return
        _state.value = _state.value.copy(lastError = null)
    }

    private fun persist(user: UserProfile?) {
        if (user == null) {
            store.remove(KEY_USER)
        } else {
            store.putString(KEY_USER, user.toJson().toString())
        }
    }

    /** مفاتيح `ArStrings` — تُحلّ للنصّ العربيّ في الواجهة. */
    private fun friendlyError(e: AuthException): String = when (e.code) {
        "network_error" -> "Auth error network"
        "sign_in_canceled", "sign_in_required" -> "Auth error canceled"
        "sign_in_failed" -> {
            Log.d(
                TAG,
                "sign_in_failed = DEVELOPER_ERROR (ApiException 10). " +
                    "تحقّق من تسجيل بصمة SHA-1 في Firebase Console وتحديث google-services.json.",
            )
            "Auth error sign_in_failed"
        }
        else -> e.message ?: e.code
    }
}
