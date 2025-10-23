package com.example.smallbasket.models

import com.google.gson.annotations.SerializedName

// Request Models
data class CreateOrderRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("items") val items: List<String>,
    @SerializedName("pickup_location") val pickupLocation: String,
    @SerializedName("pickup_area") val pickupArea: String,
    @SerializedName("drop_location") val dropLocation: String,
    @SerializedName("drop_area") val dropArea: String,
    @SerializedName("reward_percentage") val rewardPercentage: Double = 10.0,
    @SerializedName("best_before") val bestBefore: String,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("notes") val notes: String? = null
)

data class AcceptOrderRequest(
    @SerializedName("deliverer_id") val delivererId: String,
    @SerializedName("estimated_price") val estimatedPrice: Double
)

data class UpdateOrderStatusRequest(
    @SerializedName("status") val status: String
)

// Response Models
data class Order(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("deliverer_id") val delivererId: String? = null,
    @SerializedName("items") val items: List<String>,
    @SerializedName("pickup_location") val pickupLocation: String,
    @SerializedName("pickup_area") val pickupArea: String,
    @SerializedName("drop_location") val dropLocation: String,
    @SerializedName("drop_area") val dropArea: String,
    @SerializedName("reward_percentage") val rewardPercentage: Double,
    @SerializedName("estimated_price") val estimatedPrice: Double? = null,
    @SerializedName("final_price") val finalPrice: Double? = null,
    @SerializedName("best_before") val bestBefore: String,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("status") val status: String,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("created_at") val createdAt: String
)

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)

data class UserStats(
    @SerializedName("total_orders") val totalOrders: Int,
    @SerializedName("completed_deliveries") val completedDeliveries: Int,
    @SerializedName("active_orders") val activeOrders: Int
)