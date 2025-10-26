// File: app/src/main/java/com/example/smallbasket/utils/TimeUtils.kt
package com.example.smallbasket.utils

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {

    /**
     * Format ISO 8601 timestamp to relative time (e.g., "2 minutes ago")
     */
    fun formatRelativeTime(timestamp: String?): String {
        if (timestamp == null) return "Unknown"

        return try {
            // Try multiple date formats
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
            )

            var date: Date? = null
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    date = sdf.parse(timestamp)
                    if (date != null) break
                } catch (e: Exception) {
                    continue
                }
            }

            if (date == null) return "Recently"

            val now = System.currentTimeMillis()
            val then = date.time
            val diff = now - then

            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            when {
                seconds < 60 -> "Just now"
                minutes < 2 -> "1 minute ago"
                minutes < 60 -> "$minutes minutes ago"
                hours < 2 -> "1 hour ago"
                hours < 24 -> "$hours hours ago"
                days < 2 -> "Yesterday"
                days < 7 -> "$days days ago"
                days < 30 -> "${days / 7} weeks ago"
                else -> "Over a month ago"
            }
        } catch (e: Exception) {
            "Recently"
        }
    }

    /**
     * Format timestamp to human-readable date
     */
    fun formatDate(timestamp: String?): String {
        if (timestamp == null) return "Unknown"

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp) ?: return "Unknown"

            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Check if timestamp is recent (within last 5 minutes)
     */
    fun isRecent(timestamp: String?, thresholdMinutes: Int = 5): Boolean {
        if (timestamp == null) return false

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp) ?: return false

            val now = System.currentTimeMillis()
            val then = date.time
            val diff = now - then

            val minutes = diff / (1000 * 60)
            minutes <= thresholdMinutes
        } catch (e: Exception) {
            false
        }
    }
}