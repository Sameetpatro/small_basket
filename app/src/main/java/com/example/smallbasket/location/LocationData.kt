package com.example.smallbasket.location

import android.location.Location
import android.os.BatteryManager
import android.content.Context

/**
 * Represents a location update with metadata
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val source: LocationSource,
    val activityType: String? = null,
    val batteryLevel: Int? = null
) {
    enum class LocationSource {
        FOREGROUND,        // User opened app
        BACKGROUND_WORKER, // WorkManager periodic fetch
        ACTIVITY_TRIGGERED // Motion state change
    }

    companion object {
        fun fromLocation(
            location: Location,
            source: LocationSource,
            activityType: String? = null,
            batteryLevel: Int? = null
        ): LocationData {
            return LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                timestamp = location.time,
                source = source,
                activityType = activityType,
                batteryLevel = batteryLevel
            )
        }
    }

    fun toJson(): String {
        return """
            {
                "latitude": $latitude,
                "longitude": $longitude,
                "accuracy": $accuracy,
                "timestamp": $timestamp,
                "source": "${source.name}",
                "activityType": ${activityType?.let { "\"$it\"" } ?: "null"},
                "batteryLevel": $batteryLevel
            }
        """.trimIndent()
    }
}