package com.example.smallbasket

data class DeliveryRequest(
    val orderId: String,
    val title: String,
    val pickup: String,
    val dropoff: String,
    val fee: String,
    val time: String,
    val priority: Boolean,
    val details: String,
    val bestBefore: String?,
    val deadline: String?,
    val rewardPercentage: Int?
)