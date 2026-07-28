package com.nebras.mobile.core.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * حالة الاتصال بالشبكة.
 *
 * يكشف توفّر واجهة شبكة عبر [ConnectivityManager]، **ثمّ يؤكّد النفاذ الفعليّ
 * للإنترنت** بفحص خفيف للوصول إلى خادم Firestore (مصدر محتوى التطبيق).
 * السبب: وجود الواجهة لا يعني نفاذاً — قد تكون على Wi-Fi بلا خروج (captive
 * portal أو باقة منتهية) فتفشل كلّ قراءات Firestore بينما يبدو الجهاز متّصلاً.
 *
 * السلامة:
 *  - متفائل بالاتصال حتى ينتهي الفحص الأوّليّ كي لا يومض الشريط لحظة الإقلاع.
 *  - **Hysteresis**: لا نُظهر "لا اتصال" إلّا بعد فشلين متتاليين (إلّا عند
 *    انعدام الواجهة فالقطع مؤكَّد) — يمنع الوميض على ضعف لحظيّ. ونعود
 *    "online" فور أوّل نجاح.
 */
class ConnectivityController(context: Context) {

    private companion object {
        const val TAG = "Connectivity"
        const val PROBE_HOST = "firestore.googleapis.com"
        const val PROBE_INTERVAL_MILLIS = 20_000L
        const val PROBE_TIMEOUT_MILLIS = 4_000L
    }

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // نبدأ متفائلين بأنّنا متّصلون حتى ينتهي الفحص الأوّلي.
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isOnMobileData = MutableStateFlow(false)
    val isOnMobileData: StateFlow<Boolean> = _isOnMobileData.asStateFlow()

    @Volatile
    private var hasInterface = true

    private var consecutiveFailures = 0

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshFromSystem()
        override fun onLost(network: Network) = refreshFromSystem()
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) = refreshFromSystem()
    }

    init {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure { Log.d(TAG, "register failed: ${it.message}") }
        refreshFromSystem()
        scope.launch {
            while (true) {
                delay(PROBE_INTERVAL_MILLIS)
                refreshReachability()
            }
        }
    }

    private fun refreshFromSystem() {
        val capabilities = runCatching {
            manager.getNetworkCapabilities(manager.activeNetwork)
        }.getOrNull()

        hasInterface = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOnMobileData.value =
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        // عند تغيّر الواجهة افحص النفاذ الفعليّ فوراً.
        scope.launch { refreshReachability() }
    }

    private suspend fun refreshReachability() {
        val reachable = if (hasInterface) hasRealInternet() else false
        if (reachable) {
            consecutiveFailures = 0
            setOnline(true)
        } else {
            consecutiveFailures++
            // قطع مؤكَّد فوراً عند انعدام الواجهة؛ وإلّا ننتظر فشلين متتاليين.
            if (!hasInterface || consecutiveFailures >= 2) setOnline(false)
        }
    }

    /**
     * فحص نفاذ فعليّ خفيف: DNS lookup لخادم Firestore بمهلة قصيرة. نختار
     * مضيف Firestore لأنّه تبعيّة التطبيق الحقيقيّة — لو تعذّر الوصول إليه
     * فالمحتوى لن يُحمَّل فعلاً.
     */
    private suspend fun hasRealInternet(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) {
            runCatching { InetAddress.getAllByName(PROBE_HOST).isNotEmpty() }
                .getOrDefault(false)
        } ?: false
    }

    private fun setOnline(online: Boolean) {
        if (online == _isOnline.value) return
        _isOnline.value = online
        Log.d(TAG, "isOnline=$online")
    }
}
