package com.example.smallbasket.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smallbasket.Homepage
import com.example.smallbasket.R
import kotlin.random.Random

class PromotionalNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PromoNotificationWorker"
        const val WORK_NAME = "promotional_notification_work"

        private val PROMO_MESSAGES = listOf(
            Pair("Long time no see! 👋", "Try SmallBasket and get your order delivered quickly!"),
            Pair("Tap it, Grab it, Done! 🚀", "Order anything from campus in minutes"),
            Pair("Need something delivered? 📦", "SmallBasket is here to help!"),
            Pair("Campus delivery made easy! ⚡", "Place your order now on SmallBasket")
        )
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Showing promotional notification")

            // Pick random message
            val (title, body) = PROMO_MESSAGES.random()

            // Show notification
            showPromotionalNotification(title, body)

            // Store for in-app display
            NotificationStorage.saveNotification(
                context = applicationContext,
                type = "PROMOTIONAL",
                title = title,
                body = body
            )

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing promotional notification", e)
            Result.failure()
        }
    }

    private fun showPromotionalNotification(title: String, body: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel
        createNotificationChannel(notificationManager)

        // Create intent
        val intent = Intent(applicationContext, Homepage::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val channelId = applicationContext.getString(R.string.promotional_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // Show notification
        val notificationId = Random.nextInt(10000, 20000)
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Promotional notification shown: $title")
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = applicationContext.getString(R.string.promotional_channel_id)
            val channelName = applicationContext.getString(R.string.promotional_channel_name)
            val channelDescription = applicationContext.getString(R.string.promotional_channel_description)

            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = channelDescription
            }

            notificationManager.createNotificationChannel(channel)
        }
    }
}