package com.example.smallbasket.notifications

import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import com.example.smallbasket.notifications.NotificationManager as SBNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smallbasket.Homepage
import com.example.smallbasket.R
import com.example.smallbasket.RequestDetailActivity
import com.example.smallbasket.models.NotificationType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmallBasketFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when new FCM token is generated
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Save token locally
        saveTokenLocally(token)

        // Send to backend
        serviceScope.launch {
            SBNotificationManager.getInstance(applicationContext).registerFCMToken(token)
        }

    }


    /**
     * Called when message is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message notification: ${it.title}")
            showNotification(
                title = it.title ?: "SmallBasket",
                body = it.body ?: "",
                data = remoteMessage.data
            )
        }
    }

    /**
     * Handle data message
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return
        val title = data["title"] ?: "SmallBasket"
        val body = data["body"] ?: ""

        // Store notification for in-app display
        NotificationStorage.saveNotification(
            context = applicationContext,
            type = type,
            title = title,
            body = body,
            orderId = data["order_id"],
            userName = data["user_name"],
            amount = data["amount"]?.toDoubleOrNull()
        )

        // Show system notification
        showNotification(title, body, data)
    }

    /**
     * Show system notification
     */
    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as SystemNotificationManager

        // Create notification channel (Android 8.0+)
        createNotificationChannels(notificationManager)

        val channelId = getChannelIdFromType(data["type"])
        val intent = createIntentFromData(data)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        val notificationId = generateNotificationId(data["type"])
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Notification shown: $title")
    }


    /**
     * Create notification channels
     */
    private fun createNotificationChannels(notificationManager: SystemNotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val orderChannel = NotificationChannel(
                getString(R.string.order_updates_channel_id),
                getString(R.string.order_updates_channel_name),
                SystemNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.order_updates_channel_description)
            }

            val deliveryChannel = NotificationChannel(
                getString(R.string.delivery_updates_channel_id),
                getString(R.string.delivery_updates_channel_name),
                SystemNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.delivery_updates_channel_description)
            }

            val promotionalChannel = NotificationChannel(
                getString(R.string.promotional_channel_id),
                getString(R.string.promotional_channel_name),
                SystemNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.promotional_channel_description)
            }

            notificationManager.createNotificationChannel(orderChannel)
            notificationManager.createNotificationChannel(deliveryChannel)
            notificationManager.createNotificationChannel(promotionalChannel)
        }
    }


    /**
     * Get channel ID based on notification type
     */
    private fun getChannelIdFromType(type: String?): String {
        return when (type) {
            "ORDER_ACCEPTED", "ORDER_PICKED_UP", "ORDER_DELIVERED" ->
                getString(R.string.order_updates_channel_id)
            "DELIVERY_STARTED", "DELIVERY_COMPLETED" ->
                getString(R.string.delivery_updates_channel_id)
            "PROMOTIONAL" ->
                getString(R.string.promotional_channel_id)
            else -> getString(R.string.default_notification_channel_id)
        }
    }

    /**
     * Create intent based on notification data
     */
    private fun createIntentFromData(data: Map<String, String>): Intent {
        val orderId = data["order_id"]

        return if (orderId != null) {
            // Open order details
            Intent(this, RequestDetailActivity::class.java).apply {
                putExtra("order_id", orderId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            // Open homepage
            Intent(this, Homepage::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    /**
     * Generate unique notification ID
     */
    private fun generateNotificationId(type: String?): Int {
        return NOTIFICATION_ID_BASE + (type?.hashCode() ?: 0).rem(1000)
    }

    /**
     * Save token locally
     */
    private fun saveTokenLocally(token: String) {
        getSharedPreferences("smallbasket_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()
    }
}