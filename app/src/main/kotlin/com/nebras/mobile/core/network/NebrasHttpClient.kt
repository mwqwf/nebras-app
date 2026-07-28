package com.nebras.mobile.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * طبقة الشبكة — بديل `DioClient` مع نفس السلوك (المهلات، إعادة المحاولة،
 * الرؤوس، تجميع المسار مع [ApiConstants.baseUrl]).
 */
object NebrasHttpClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** العميل العامّ لطلبات JSON. */
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(ApiConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ApiConstants.RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConstants.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    /**
     * عميل التنزيلات: بلا مهلة قراءة (ملفّات كبيرة قد تستغرق دقائق) وبلا
     * إعادة محاولة تلقائيّة — الاستئناف يُدار في طبقة التنزيل نفسها.
     */
    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun resolve(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            ApiConstants.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }

    @Throws(IOException::class)
    fun getString(path: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(resolve(path)).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        instance.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${resolve(path)}")
            }
            return body
        }
    }

    @Throws(IOException::class)
    fun getJsonObject(path: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(getString(path, headers))

    @Throws(IOException::class)
    fun getJsonArray(path: String, headers: Map<String, String> = emptyMap()): JSONArray =
        JSONArray(getString(path, headers))

    @Throws(IOException::class)
    fun post(
        path: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val builder = Request.Builder()
            .url(resolve(path))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        headers.forEach { (k, v) -> builder.header(k, v) }
        instance.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${resolve(path)}")
            }
            return text
        }
    }
}
