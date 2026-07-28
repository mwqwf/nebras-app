package com.nebras.mobile.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nebras.mobile.MainActivity
import com.nebras.mobile.R
import com.nebras.mobile.core.di.ServiceLocator
import com.nebras.mobile.feature.notifications.NotificationModel
import com.nebras.mobile.feature.notifications.NotificationsPreferences

/**
 * استقبال رسائل FCM — مقابل `_firebaseMessagingBackgroundHandler` ومعالجة
 * الواجهة في `firebase_notification_datasource.dart` معاً: في Android
 * الأصليّ تصل رسائل **data-only** إلى [onMessageReceived] في المقدّمة
 * والخلفية على السواء، فمعالج واحد يكفي.
 *
 * الجوهر: اللوحة ترسل رسائل data-only (بلا كتلة notification)، فلا يعرضها
 * النظام تلقائياً؛ التطبيق وحده يقرّر العرض — **فيتوقّف التدفّق فوراً عند
 * إيقاف المستخدم للإشعارات** حتى في الخلفية، بلا انتظار سريان إلغاء
 * اشتراك الـ topic.
 */
class NebrasMessagingService : FirebaseMessagingService() {

    private companion object {
        const val TAG = "NebrasFCM"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // ⛔ احترام زرّ الإيقاف: إن أوقف المستخدم الإشعارات لا نعرض شيئاً.
        val enabled = runCatching {
            NotificationsPreferences.init(ServiceLocator.localStore)
            NotificationsPreferences.isPushEnabled()
        }.getOrDefault(true)
        if (!enabled) {
            Log.d(TAG, "suppressed — notifications disabled by user.")
            return
        }

        val model = NotificationModel.fromRemoteData(
            data = message.data,
            messageId = message.messageId,
            sentTimeMs = message.sentTime,
        )

        // نُفضّل اسم المحتوى الصريح كي يظهر في البانر بدل العنوان العامّ.
        val title = model.title.ifEmpty { message.notification?.title ?: "نبراس" }
        val body = model.body
            .ifEmpty { message.data["description"].orEmpty() }
            .ifEmpty { message.notification?.body.orEmpty() }

        // حفظ في السجلّ المحلّي (شاشة الإشعارات) قبل العرض.
        runCatching { ServiceLocator.notificationsRepository.saveNotification(model) }

        showNotification(title, body, message.data)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // النقر يفتح MainActivity مع الحمولة كاملة — يتولّى NotificationNavigator
        // فتح الشاشة المقصودة (نفس مسار FLUTTER_NOTIFICATION_CLICK القديم).
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
            putExtra("from_notification", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.GENERAL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this)
            .notify((System.currentTimeMillis() and 0x7FFFFFFF).toInt(), notification)
    }
}
