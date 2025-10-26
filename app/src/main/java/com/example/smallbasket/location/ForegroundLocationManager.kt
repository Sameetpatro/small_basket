// File: app/src/main/java/com/example/smallbasket/location/ForegroundLocationManager.kt
package com.example.smallbasket.location

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.smallbasket.api.RetrofitClient
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * FIXED: Handles instant high-accuracy location AND syncs with backend
 */
class ForegroundLocationManager(private val context: Context) {

    companion object {
        private const val TAG = "ForegroundLocation"
        private const val HIGH_ACCURACY_TIMEOUT_MS = 3000L
        private const val MAX_RETRIES = 3
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val repository = LocationRepository.getInstance(context)
    private val api = RetrofitClient.apiService

    /**
     * FIXED: Get instant location AND sync with backend immediately
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
            Log.d(TAG, "=== Getting instant location ===")
            val startTime = System.currentTimeMillis()

            // Request high-accuracy location
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30000)
                .setDurationMillis(HIGH_ACCURACY_TIMEOUT_MS)
                .build()

            val location = withTimeoutOrNull(HIGH_ACCURACY_TIMEOUT_MS) {
                fusedLocationClient.getCurrentLocation(request, null).await()
            }

            if (location != null) {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "✓ Got location in ${duration}ms (accuracy: ${location.accuracy}m)")

                val locationData = LocationData.fromLocation(
                    location = location,
                    source = LocationData.LocationSource.FOREGROUND,
                    activityType = "USER_REQUESTED",
                    batteryLevel = repository.getBatteryLevel(context)
                )

                // Save to repository
                repository.saveLocation(locationData)

                // IMMEDIATELY sync with backend (THIS IS THE FIX!)
                val syncSuccess = syncLocationWithBackend(location)

                if (syncSuccess) {
                    Log.d(TAG, "✓ Location synced successfully")
                } else {
                    Log.w(TAG, "✗ Location sync failed")
                }

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
     * FIXED: Sync location with backend with retry logic
     */
    private suspend fun syncLocationWithBackend(location: android.location.Location): Boolean {
        var retryCount = 0

        while (retryCount < MAX_RETRIES) {
            try {
                Log.d(TAG, "Syncing location with backend (attempt ${retryCount + 1}/$MAX_RETRIES)")

                val request = UpdateGPSLocationRequest(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    fastMode = false // Use accurate mode for foreground
                )

                val response = api.updateGPSLocation(request)

                if (response.isSuccessful) {
                    val data = response.body()
                    Log.d(TAG, "✓ Location synced to backend!")
                    Log.d(TAG, "  - Primary area: ${data?.data?.primaryArea}")
                    Log.d(TAG, "  - All areas: ${data?.data?.allMatchingAreas}")
                    Log.d(TAG, "  - Lat/Lng: ${data?.data?.latitude}, ${data?.data?.longitude}")
                    return true
                } else {
                    Log.w(TAG, "✗ Backend sync failed: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    Log.w(TAG, "  Error: $errorBody")
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ Exception syncing (attempt ${retryCount + 1})", e)
            }

            retryCount++
            if (retryCount < MAX_RETRIES) {
                val delayMs = (500L * Math.pow(2.0, retryCount.toDouble() - 1)).toLong()
                Log.d(TAG, "Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }

        Log.e(TAG, "✗ Failed to sync after $MAX_RETRIES attempts")
        return false
    }

    /**
     * Get last known location (instant, battery-free)
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getLastKnownLocation(): LocationData? = withContext(Dispatchers.IO) {
        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "No location permission")
            return@withContext null
        }

        return@withContext try {
            Log.d(TAG, "Requesting last known location")

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