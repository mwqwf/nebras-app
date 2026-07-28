package com.nebras.mobile.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** ملفّ المستخدم — نقل `auth/model/user_profile.dart`. */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val signedInAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("email", email)
        put("displayName", displayName)
        photoUrl?.let { put("photoUrl", it) }
        put("signedInAtMs", signedInAtMs)
    }

    companion object {
        fun fromJson(json: JSONObject): UserProfile = UserProfile(
            id = json.optString("id"),
            email = json.optString("email"),
            displayName = json.optString("displayName"),
            photoUrl = json.optString("photoUrl").takeIf { it.isNotEmpty() },
            signedInAtMs = json.optLong("signedInAtMs", System.currentTimeMillis()),
        )
    }
}

class AuthException(val code: String, message: String) : Exception(message) {
    override fun toString(): String = "AuthException($code): $message"
}

/**
 * خدمة تسجيل الدخول بـ Google — نقل `auth/service/auth_service.dart`.
 *
 * بديل حزمة `google_sign_in`: **Credential Manager** + `googleid` (الطريق
 * المعتمد حالياً من Google). نفس `serverClientId` المسجَّل في Firebase.
 */
class AuthService(private val context: Context) {

    private companion object {
        const val TAG = "AuthService"

        /** معرّف عميل الويب من Firebase — نفس قيمة نسخة Flutter حرفياً. */
        const val SERVER_CLIENT_ID =
            "412379996427-0i55p23iunk93tilvje5442d6e42s4p9.apps.googleusercontent.com"
    }

    private val credentialManager = CredentialManager.create(context)
    private val auth = FirebaseAuth.getInstance()

    /**
     * دخول صامت: حساب Google مصرَّح سابقاً فقط (بلا واجهة). يعود null عند
     * عدم وجود اعتماد — نفس دلالة `signInSilently`.
     */
    suspend fun signInSilently(): UserProfile? = runCatching {
        // مستخدم Firebase حيّ من جلسة سابقة يكفي — لا حاجة لحوار.
        auth.currentUser?.let { return mapUser(it) }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(SERVER_CLIENT_ID)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val response = credentialManager.getCredential(
            context,
            GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        firebaseAuthWithCredential(response.credential)
    }.getOrElse {
        if (it !is NoCredentialException) {
            Log.d(TAG, "signInSilently failed: ${it.message}")
        }
        null
    }

    /**
     * دخول تفاعليّ من [activityContext] (حوار اختيار الحساب يحتاج Activity).
     * يعود null إن ألغى المستخدم.
     */
    suspend fun signInInteractive(activityContext: Context): UserProfile? {
        try {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(SERVER_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val response = credentialManager.getCredential(
                activityContext,
                GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
            return firebaseAuthWithCredential(response.credential)
        } catch (e: GetCredentialCancellationException) {
            return null // ألغى المستخدم.
        } catch (e: NoCredentialException) {
            throw AuthException("sign_in_failed", e.message ?: "Google sign-in failed")
        } catch (e: Throwable) {
            Log.d(TAG, "signIn unknown error: ${e.message}")
            throw AuthException("unknown", e.toString())
        }
    }

    private suspend fun firebaseAuthWithCredential(
        credential: androidx.credentials.Credential,
    ): UserProfile? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthException("sign_in_failed", "Unexpected credential type")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(firebaseCredential).await()
        return mapUser(result.user)
    }

    suspend fun signOut() {
        runCatching {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }.onFailure { Log.d(TAG, "signOut error: ${it.message}") }
    }

    /**
     * حذف الحساب من Firebase Auth. عند `requires-recent-login` نُعيد
     * المصادقة تفاعليّاً ثمّ نحذف — نفس مسار نسخة Flutter.
     */
    suspend fun deleteAccount(activityContext: Context) {
        val user = auth.currentUser ?: return
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(SERVER_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val response = try {
                credentialManager.getCredential(
                    activityContext,
                    GetCredentialRequest.Builder().addCredentialOption(option).build(),
                )
            } catch (cancel: GetCredentialCancellationException) {
                throw AuthException(
                    "reauth_cancelled",
                    "Re-authentication required to delete the account",
                )
            }
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
                user.delete().await()
            } else {
                throw AuthException("sign_in_failed", "Unexpected credential type")
            }
        } finally {
            runCatching {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    suspend fun disconnect() {
        runCatching {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } // قد يرمي إن كان المستخدم خارجاً أصلاً — نتجاهل بأمان.
    }

    private fun mapUser(user: FirebaseUser?): UserProfile? {
        if (user == null) return null
        return UserProfile(
            id = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: user.email?.substringBefore('@')
                ?: "User",
            photoUrl = user.photoUrl?.toString(),
            signedInAtMs = System.currentTimeMillis(),
        )
    }
}
