package com.nebras.mobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * قنوات الإشعارات — بديل إنشاء القنوات داخل
 * `firebase_notification_datasource.dart` و`main.dart`.
 *
 * ⚠️ [GENERAL] يجب أن يطابق `default_notification_channel_id` في
 * AndroidManifest، وإلّا عرض النظام الإشعار على قناة افتراضية بلا صوت ولا
 * أولويّة.
 */
object NotificationChannels {

    /** قناة الإشعارات العامّة (محتوى جديد، متابعة قسم، تفاعل مجتمع). */
    const val GENERAL = "nebras_notifications"

    /** قناة تنزيل المحتوى للعمل دون إنترنت (تقدّم صامت). */
    const val DOWNLOADS = "nebras_downloads"

    /** قناة تشغيل الوسائط — Media3 يُنشئها بنفسه لكن نضبط اسمها العربيّ. */
    const val PLAYBACK = "nebras_playback"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                GENERAL,
                "إشعارات نبراس",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "إشعارات المحتوى الجديد والمتابعة والتفاعل"
                enableVibration(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOADS,
                "التنزيلات",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "تقدّم تنزيل المحتوى للعمل دون إنترنت"
                setShowBadge(false)
                enableVibration(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                PLAYBACK,
                "التشغيل",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "إشعار المشغّل الدائم وأزرار التحكّم"
                setShowBadge(false)
                enableVibration(false)
            },
        )
    }
}
