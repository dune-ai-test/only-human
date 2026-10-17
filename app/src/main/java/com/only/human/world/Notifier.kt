package com.only.human.world

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.only.human.R

/**
 * Local notifications — the only kind this app has. No push, no server:
 * when one of the fifteen acts while you're away, a quiet line lands in the
 * shade. Tapping it opens the app. Off until you switch it on.
 */
object Notifier {
    private const val CHANNEL = "they_act"

    fun post(context: Context, tag: String, title: String, body: String) {
        // A runtime grant on 13+; earlier Androids post without asking.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "They act", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notes when the fifteen write while you're away" },
        )

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                if (launch != null) {
                    setContentIntent(
                        PendingIntent.getActivity(
                            context, 0, launch,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            .build()
        mgr.notify(tag, 1, n)
    }
}
