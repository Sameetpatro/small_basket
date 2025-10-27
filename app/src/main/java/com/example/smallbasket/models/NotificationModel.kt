package com.example.smallbasket.models

import com.google.gson.annotations.SerializedName

/**
 * Notification types
 */
enum class NotificationType {
    ORDER_ACCEPTED,
    ORDER_PICKED_UP,
    ORDER_DELIVERED,
    DELIVERY_STARTED,
    DELIVERY_COMPLETED,
    PROMOTIONAL
}

/**
 * Notification data payload
 */
data class NotificationPayload(
    @SerializedName("type") val type: String,
    @SerializedName("order_id") val orderId: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("click_action") val clickAction: String? = null
)

/**
 * Request to send notification
 */
data class SendNotificationRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("notification_type") val notificationType: String,
    @SerializedName("data") val data: Map<String, String>
)

/**
 * Stored notification for display in NotificationActivity
 */
data class StoredNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val orderId: String? = null,
    val isRead: Boolean = false
)
