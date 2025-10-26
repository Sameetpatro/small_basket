// File: app/src/main/java/com/example/smallbasket/location/ForegroundLocationManager.kt
package com.example.smallbasket.location

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * MUST be called from a coroutine (not main thread)
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getInstantLocation(): LocationData? = withContext(Dispatchers.IO) {
        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            return@withContext null
        }

        if (!LocationUtils.isLocationEnabled(context)) {
            Log.w(TAG, "Location services disabled")
            return@withContext null
        }

        return@withContext try {
            Log.d(TAG, "Requesting high-accuracy location")
            val startTime = System.currentTimeMillis()

            // Request high-accuracy location
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30000) // Accept up to 30 seconds old
                .setDurationMillis(HIGH_ACCURACY_TIMEOUT_MS)
                .build()

            // Use coroutine timeout instead of Tasks.await with timeout
            val location = withTimeoutOrNull(HIGH_ACCURACY_TIMEOUT_MS) {
                fusedLocationClient.getCurrentLocation(request, null).await()
            }

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
                Log.w(TAG, "Location request returned null or timed out")
                null
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting instant location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting instant location", e)
            null
        }
    }

    /**
     * Get last known location (instant, battery-free)
     * MUST be called from a coroutine (not main thread)
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getLastKnownLocation(): LocationData? = withContext(Dispatchers.IO) {
        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            return@withContext null
        }

        return@withContext try {
            Log.d(TAG, "Requesting last known location")

            // Use coroutine await instead of Tasks.await
            val location = withTimeoutOrNull(1000) {
                fusedLocationClient.lastLocation.await()
            }

            if (location != null) {
                Log.d(TAG, "Got last known location (accuracy: ${location.accuracy}m, age: ${System.currentTimeMillis() - location.time}ms)")

                LocationData.fromLocation(
                    location = location,
                    source = LocationData.LocationSource.FOREGROUND,
                    activityType = "CACHED",
                    batteryLevel = repository.getBatteryLevel(context)
                )
            } else {
                Log.w(TAG, "No last known location available")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last known location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            null
        }
    }
}