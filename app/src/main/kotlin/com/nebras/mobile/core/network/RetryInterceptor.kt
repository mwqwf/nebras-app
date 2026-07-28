package com.nebras.mobile.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.random.Random

/**
 * إعادة محاولة الطلبات الفاشلة بتراجع أُسّيّ + jitter — مقابل
 * `core/network/retry_interceptor.dart`.
 *
 * يُعاد المحاولة على: أخطاء الشبكة/المهلة، و5xx، و429.
 * لا يُعاد المحاولة على: 4xx (عدا 429) — خطأ في الطلب نفسه.
 */
class RetryInterceptor(
    private val maxRetries: Int = ApiConstants.MAX_RETRIES,
    private val baseDelayMillis: Long = ApiConstants.RETRY_DELAY_MILLIS,
) : Interceptor {

    private companion object {
        const val TAG = "RetryInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastError: IOException? = null
        var response: Response? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                val delay = backoffMillis(attempt)
                Log.d(TAG, "retry #$attempt after ${delay}ms → ${request.url.encodedPath}")
                runCatching { Thread.sleep(delay) }
                    .onFailure { throw InterruptedIOException("retry interrupted") }
            }

            response?.close()
            response = null

            try {
                response = chain.proceed(request)
                if (!shouldRetry(response.code)) return response
            } catch (e: IOException) {
                lastError = e
            }
        }

        response?.let { return it }
        throw lastError ?: IOException("Request failed after $maxRetries retries")
    }

    /** 5xx و429 فقط — أخطاء العميل الأخرى لا تُصلحها إعادة المحاولة. */
    private fun shouldRetry(code: Int): Boolean = code >= 500 || code == 429

    /** تراجع أُسّي (2s → 4s → 8s) مع jitter يصل إلى 30% لتفادي التزامن. */
    private fun backoffMillis(attempt: Int): Long {
        val exponential = baseDelayMillis * (1L shl (attempt - 1))
        val jitter = Random.nextLong(0, (exponential * 0.3).toLong().coerceAtLeast(1))
        return exponential + jitter
    }
}
