// ============================================================================
// PART 4: FOREGROUND LOCATION & PERMISSION MANAGEMENT
// ============================================================================

// File: app/src/main/java/com/example/smallbasket/location/ForegroundLocationManager.kt
package com.example.smallbasket.location

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Handles instant high-accuracy location when app is in foreground
 * Provides 2-3 second response time for user-initiated requests
 */
class ForegroundLocationManager(private val context: Context) {

    companion object {
        private const val TAG = "ForegroundLocation"
        private const val HIGH_ACCURACY_TIMEOUT_MS = 3000L // 3 seconds
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val repository = LocationRepository.getInstance(context)

    /**
     * Get instant high-accuracy location for foreground use
     * Returns within 2-3 seconds
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getInstantLocation(): LocationData? {
        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            return null
        }

        if (!LocationUtils.isLocationEnabled(context)) {
            Log.w(TAG, "Location services disabled")
            return null
        }

        return try {
            Log.d(TAG, "Requesting high-accuracy location")
            val startTime = System.currentTimeMillis()

            // Request high-accuracy location
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30000) // Accept up to 30 seconds old
                .setDurationMillis(HIGH_ACCURACY_TIMEOUT_MS)
                .build()

            val task = fusedLocationClient.getCurrentLocation(request, null)
            val location = Tasks.await(task, HIGH_ACCURACY_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (location != null) {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Got location in ${duration}ms (accuracy: ${location.accuracy}m)")

                val locationData = LocationData.fromLocation(
                    location = location,
                    source = LocationData.LocationSource.FOREGROUND,
                    activityType = "USER_REQUESTED",
                    batteryLevel = repository.getBatteryLevel(context)
                )

                // Save to repository
                repository.saveLocation(locationData)

                locationData
            } else {
                Log.w(TAG, "Location request returned null")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting instant location", e)
            null
        }
    }

    /**
     * Get last known location (instant, battery-free)
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getLastKnownLocation(): LocationData? {
        if (!LocationUtils.hasLocationPermission(context)) {
            return null
        }

        return try {
            val task = fusedLocationClient.lastLocation
            val location = Tasks.await(task, 1000, TimeUnit.MILLISECONDS)

            if (location != null) {
                LocationData.fromLocation(
                    location = location,
                    source = LocationData.LocationSource.FOREGROUND,
                    activityType = "CACHED",
                    batteryLevel = repository.getBatteryLevel(context)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            null
        }
    }
}
