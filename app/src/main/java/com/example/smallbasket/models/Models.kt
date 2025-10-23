package com.example.smallbasket.models

import com.google.gson.annotations.SerializedName

// ============================================
// REQUEST MODELS
// ============================================

data class CreateOrderRequest(
    @SerializedName("item") val item: List<String>,
    @SerializedName("pickup_location") val pickupLocation: String,
    @SerializedName("pickup_area") val pickupArea: String,
    @SerializedName("drop_location") val dropLocation: String,
    @SerializedName("drop_area") val dropArea: String,
    @SerializedName("reward") val reward: Double = 10.0,
    @SerializedName("time_requested") val timeRequested: String,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("priority") val priority: Boolean = false,
    @SerializedName("notes") val notes: String? = null
)

data class AcceptOrderRequest(
    @SerializedName("request_id") val requestId: String
)

data class UpdateOrderStatusRequest(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("status") val status: String  // "completed", "cancelled"
)

// ============================================
// RESPONSE MODELS
// ============================================

data class Order(
    @SerializedName("request_id") val id: String,
    @SerializedName("posted_by") val userId: String,
    @SerializedName("poster_email") val posterEmail: String,
    @SerializedName("accepted_by") val delivererId: String? = null,
    @SerializedName("acceptor_email") val acceptorEmail: String? = null,
    @SerializedName("item") val items: List<String>,
    @SerializedName("pickup_location") val pickupLocation: String,
    @SerializedName("pickup_area") val pickupArea: String,
    @SerializedName("drop_location") val dropLocation: String,
    @SerializedName("drop_area") val dropArea: String,
    @SerializedName("reward") val rewardPercentage: Double,
    @SerializedName("time_requested") val bestBefore: String,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("priority") val priorityFlag: Boolean,
    @SerializedName("status") val status: String,  // "open", "accepted", "completed", "cancelled"
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("accepted_at") val acceptedAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("is_expired") val isExpired: Boolean = false
) {
    val priority: String
        get() = if (priorityFlag) "emergency" else "normal"
}

data class SuccessResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: Map<String, Any>? = null
)

data class RequestStatsResponse(
    @SerializedName("total_posted") val totalOrders: Int,
    @SerializedName("total_accepted") val completedDeliveries: Int,
    @SerializedName("active_requests") val activeOrders: Int
)

data class UserProfileResponse(
    @SerializedName("uid") val uid: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email_verified") val emailVerified: Boolean,
    @SerializedName("preferred_areas") val preferredAreas: List<String>? = null,
    @SerializedName("current_area") val currentArea: String? = null,
    @SerializedName("is_reachable") val isReachable: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_login") val lastLogin: String
)

// ============================================
// CONNECTIVITY MODELS
// ============================================

data class ConnectivityUpdateRequest(
    @SerializedName("is_connected") val isConnected: Boolean,
    @SerializedName("location_permission_granted") val locationPermissionGranted: Boolean
)

// ============================================
// AREA MODELS
// ============================================

data class AreasListResponse(
    @SerializedName("areas") val areas: List<String>,
    @SerializedName("total") val total: Int
)

data class PreferredAreasRequest(
    @SerializedName("preferred_areas") val preferredAreas: List<String>
)

// ============================================
// NOTIFICATION MODELS
// ============================================

data class FCMTokenRequest(
    @SerializedName("fcm_token") val fcmToken: String
)

// ============================================
// GENERIC API RESPONSE WRAPPER
// ============================================

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T? = null
)

// ============================================
// COMPATIBILITY ALIASES
// ============================================

typealias UserStats = RequestStatsResponse
