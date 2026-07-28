package com.nebras.mobile.core.error

import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * يحوّل استثناءات طبقة الشبكة إلى [Failure] مفهومة في طبقة الـ domain.
 * هذا **المكان الوحيد** الذي تُعالَج فيه استثناءات OkHttp — الـ domain لا
 * يرى استثناء شبكة قطّ (مقابل `error_handler.dart` مع Dio).
 */
object ErrorHandler {

    fun handleException(exception: Throwable): Failure = when (exception) {
        is SocketTimeoutException, is InterruptedIOException ->
            TimeoutFailure("Connection timeout. Please check your internet connection.")

        is UnknownHostException, is ConnectException ->
            NetworkFailure("Network error. Please check your internet connection.")

        is SSLException -> NetworkFailure("SSL certificate error")

        is IOException -> NetworkFailure(
            exception.message ?: "Network error. Please check your internet connection.",
        )

        else -> UnknownFailure(exception.message ?: "An unexpected error occurred")
    }

    /** يترجم ردّاً غير ناجح إلى [Failure] بحسب رمز الحالة. */
    fun handleBadResponse(statusCode: Int, body: String?): Failure {
        val message = extractErrorMessage(statusCode, body)

        if (statusCode in 400..499) {
            return when (statusCode) {
                401 -> UnauthorizedFailure(message)
                403 -> ForbiddenFailure(message)
                404 -> NotFoundFailure(message)
                else -> ClientFailure(message, statusCode)
            }
        }
        if (statusCode >= 500) return ServerFailure(message, statusCode)
        return UnknownFailure(message)
    }

    fun handleResponse(response: Response, body: String?): Failure =
        handleBadResponse(response.code, body)

    /** يستخرج رسالة الخطأ من صيغ ردود الـ API الشائعة. */
    private fun extractErrorMessage(statusCode: Int, body: String?): String {
        if (body.isNullOrBlank()) return "Error $statusCode"
        return runCatching {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotEmpty() }
                ?: json.optString("error").takeIf { it.isNotEmpty() }
                ?: json.optString("detail").takeIf { it.isNotEmpty() }
                ?: "Error $statusCode"
        }.getOrDefault("Error $statusCode")
    }
}
