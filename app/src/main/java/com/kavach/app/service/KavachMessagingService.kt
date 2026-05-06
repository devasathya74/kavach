package com.kavach.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kavach.app.MainActivity
import com.kavach.app.R

/**
 * PRODUCTION-GRADE FCM SERVICE
 * 
 * Rules:
 *  1. NO COMPOSE HERE. (No setContent, no UI calls)
 *  2. Launch MainActivity via PendingIntent.
 *  3. Handle data payload for deep-linking.
 */
class KavachMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("KavachFCM", "From: ${remoteMessage.from}")

        // 1. Check data payload for deep-linking
        val targetScreen = remoteMessage.data["screen"] // e.g. "orders", "live"
        val notifId = remoteMessage.data["notif_id"]

        // 2. Extract notification details
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "KAVACH Alert"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "नया निर्देश प्राप्त हुआ है।"

        sendNotification(title, body, targetScreen, notifId)
    }

    private fun sendNotification(title: String, body: String, screen: String?, notifId: String?) {
        val priority = if (title.contains("CRITICAL", ignoreCase = true)) 1 else 0
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("screen", screen)
            putExtra("notif_id", notifId)
            putExtra("priority", priority)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "kavach_alerts"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.kavach.app.R.drawable.ic_kavach_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kavach Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        Log.d("KavachFCM", "New Token: $token")
        // In production, send this to backend to bind with user PNO
    }
}
