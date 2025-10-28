// File: app/src/main/java/com/example/smallbasket/location/LocationWorker.kt
package com.example.smallbasket.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smallbasket.api.RetrofitClient
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * FIXED: Background worker that fetches location AND syncs with backend immediately
 */
class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocationWorker"
        const val WORK_NAME = "location_tracking_work"
        private const val LOCATION_TIMEOUT_MS = 5000L
        private const val CACHE_MAX_AGE_MS = 10 * 60 * 1000L
        private const val MAX_RETRIES = 3
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val repository = LocationRepository.getInstance(context)
    private val api = RetrofitClient.apiService

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== LocationWorker STARTED ===")

        if (!repository.isTrackingEnabled()) {
            Log.d(TAG, "Tracking disabled, skipping")
            return Result.success()
        }

        if (!LocationUtils.hasLocationPermission(applicationContext)) {
            Log.w(TAG, "No location permission, stopping work")
            return Result.failure()
        }

        if (!LocationUtils.isLocationEnabled(applicationContext)) {
            Log.w(TAG, "Location services disabled")
            return Result.retry()
        }

        return try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Attempting to get location")

            // Step 1: Get location (try cache first, then fresh)
            val location = getCachedLocation() ?: run {
                Log.d(TAG, "No fresh cache, requesting new location")
                requestFreshLocation()
            }

            if (location != null) {
                Log.d(TAG, "✓ Got location: (${location.latitude}, ${location.longitude})")

                // Step 2: Save locally
                val locationData = LocationData.fromLocation(
                    location = location,
                    source = LocationData.LocationSource.BACKGROUND_WORKER,
                    activityType = if (repository.getLastActivityState()) "MOVING" else "STILL",
                    batteryLevel = repository.getBatteryLevel(applicationContext)
                )
                repository.saveLocation(locationData)

                // Step 3: IMMEDIATELY sync with backend (THIS IS THE FIX!)
                val syncSuccess = syncLocationWithBackend(location)

                if (syncSuccess) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "✓ Work completed successfully in ${duration}ms")
                    Result.success()
                } else {
                    Log.w(TAG, "✗ Location sync failed, will retry")
                    Result.retry()
                }
            } else {
                Log.w(TAG, "✗ Failed to get location")
                Result.retry()
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - missing permissions", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error in LocationWorker", e)
            Result.retry()
        }
    }

    /**
     * FIXED: Sync location with backend API with retry logic
     * Returns true if successful, false otherwise
     */
    private suspend fun syncLocationWithBackend(location: Location): Boolean {
        var retryCount = 0

        while (retryCount < MAX_RETRIES) {
            try {
                Log.d(TAG, "Syncing location with backend (attempt ${retryCount + 1}/$MAX_RETRIES)")

                val request = UpdateGPSLocationRequest(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    fastMode = true // Use fast mode for background updates
                )

                // Call backend API
                val response = api.updateGPSLocation(request)

                if (response.isSuccessful) {
                    val data = response.body()
                    Log.d(TAG, "✓ Location synced successfully!")
                    Log.d(TAG, "  - Primary area: ${data?.data?.primaryArea}")
                    Log.d(TAG, "  - All areas: ${data?.data?.allMatchingAreas}")
                    Log.d(TAG, "  - Is on edge: ${data?.data?.isOnEdge}")
                    return true
                } else {
                    Log.w(TAG, "✗ Backend returned error: ${response.code()} - ${response.message()}")
                    val errorBody = response.errorBody()?.string()
                    Log.w(TAG, "  Error body: $errorBody")
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ Exception syncing location (attempt ${retryCount + 1})", e)
            }

            retryCount++
            if (retryCount < MAX_RETRIES) {
                // Exponential backoff: 1s, 2s, 4s
                val delayMs = (1000L * Math.pow(2.0, retryCount.toDouble() - 1)).toLong()
                Log.d(TAG, "Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }

        Log.e(TAG, "✗ Failed to sync location after $MAX_RETRIES attempts")
        return false
    }

    @Suppress("MissingPermission")
    private fun getCachedLocation(): Location? {
        return try {
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "Missing location permission for cached location")
                return null
            }

            val task = fusedLocationClient.lastLocation
            val location = Tasks.await(task, 1000, TimeUnit.MILLISECONDS)

            if (location != null) {
                val age = System.currentTimeMillis() - location.time
                Log.d(TAG, "Cached location age: ${age}ms")

                if (LocationUtils.isLocationFresh(location.time, CACHE_MAX_AGE_MS)) {
                    Log.d(TAG, "✓ Using cached location")
                    location
                } else {
                    Log.d(TAG, "✗ Cached location too old")
                    null
                }
            } else {
                Log.d(TAG, "No cached location available")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting cached location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached location", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        return try {
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "Missing location permission for fresh location")
                return null
            }

            Log.d(TAG, "Requesting fresh location with balanced power")

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60000)
                .setDurationMillis(LOCATION_TIMEOUT_MS)
                .build()

            val task = fusedLocationClient.getCurrentLocation(request, null)
            val location = Tasks.await(task, LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (location == null) {
                Log.w(TAG, "✗ Location request returned null")
            } else {
                Log.d(TAG, "✓ Fresh location obtained: accuracy=${location.accuracy}m")
            }

            location

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting fresh location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting fresh location", e)
            null
        }
    }
}