package com.example.smallbasket.utils

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object TimeUtils {
    /**
     * Convert ISO timestamp to "X min remaining" format
     * Examples: "5 min", "2 hrs", "Expired"
     */
    fun getTimeRemaining(isoTimestamp: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")

            val targetDate = sdf.parse(isoTimestamp) ?: return "Invalid time"
            val now = Date()

            val diffMillis = targetDate.time - now.time

            if (diffMillis < 0) {
                return "Expired"
            }

            val minutes = (diffMillis / (1000 * 60)).toInt()

            when {
                minutes < 1 -> "< 1 min"
                minutes < 60 -> "$minutes min"
                else -> {
                    val hours = minutes / 60
                    if (hours < 24) {
                        "$hours hr${if (hours > 1) "s" else ""}"
                    } else {
                        val days = hours / 24
                        "$days day${if (days > 1) "s" else ""}"
                    }
                }
            }
        } catch (e: Exception) {
            "Invalid time"
        }
    }

    /**
     * Format date for display (e.g., "Oct 27, 2025 14:30")
     */
    fun formatDateTime(isoTimestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(isoTimestamp)
            date?.let { outputFormat.format(it) } ?: isoTimestamp
        } catch (e: Exception) {
            isoTimestamp
        }
    }
}