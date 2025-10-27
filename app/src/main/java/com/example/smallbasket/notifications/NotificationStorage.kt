package com.example.smallbasket.notifications

import android.content.Context
import android.util.Log
import com.example.smallbasket.models.NotificationType
import com.example.smallbasket.models.StoredNotification
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object NotificationStorage {

    private const val TAG = "NotificationStorage"
    private const val PREFS_NAME = "notifications_prefs"
    private const val KEY_NOTIFICATIONS = "stored_notifications"
    private const val MAX_NOTIFICATIONS = 50

    private val gson = Gson()

    /**
     * Save notification for in-app display
     */
    fun saveNotification(
        context: Context,
        type: String,
        title: String,
        body: String,
        orderId: String? = null,
        userName: String? = null,
        amount: Double? = null
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Get existing notifications
            val notifications = getNotifications(context).toMutableList()

            // Create new notification
            val notification = StoredNotification(
                id = System.currentTimeMillis().toString(),
                type = NotificationType.valueOf(type),
                title = title,
                body = body,
                timestamp = System.currentTimeMillis(),
                orderId = orderId,
                isRead = false
            )

            // Add to beginning
            notifications.add(0, notification)

            // Keep only last MAX_NOTIFICATIONS
            val trimmed = notifications.take(MAX_NOTIFICATIONS)

            // Save
            val json = gson.toJson(trimmed)
            prefs.edit().putString(KEY_NOTIFICATIONS, json).apply()

            Log.d(TAG, "Notification saved: $title")

        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification", e)
        }
    }

    /**
     * Get all stored notifications
     */
    fun getNotifications(context: Context): List<StoredNotification> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()

            val type = object : TypeToken<List<StoredNotification>>() {}.type
            gson.fromJson(json, type) ?: emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "Error getting notifications", e)
            emptyList()
        }
    }

    /**
     * Mark notification as read
     */
    fun markAsRead(context: Context, notificationId: String) {
        try {
            val notifications = getNotifications(context).map {
                if (it.id == notificationId) it.copy(isRead = true) else it
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(notifications)
            prefs.edit().putString(KEY_NOTIFICATIONS, json).apply()

        } catch (e: Exception) {
            Log.e(TAG, "Error marking as read", e)
        }
    }

    /**
     * Clear all notifications
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NOTIFICATIONS)
            .apply()
    }

    /**
     * Get unread count
     */
    fun getUnreadCount(context: Context): Int {
        return getNotifications(context).count { !it.isRead }
    }
}