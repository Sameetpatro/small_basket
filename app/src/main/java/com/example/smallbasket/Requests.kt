package com.example.smallbasket

data class Requests(
    val title: String,
    val pickup: String,
    val drop: String,
    val details: String,
    val orderId: String = "",
    val priority: String = "normal",
    val bestBefore: String = "",
    val deadline: String = "",
    val rewardPercentage: Double = 10.0
)