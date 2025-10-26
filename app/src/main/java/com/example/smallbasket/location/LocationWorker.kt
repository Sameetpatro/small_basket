package com.example.smallbasket.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Background worker that fetches location periodically
 * Runs for 2-5 seconds, checks cache first, then requests if needed
 */
class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocationWorker"
        const val WORK_NAME = "location_tracking_work"
        private const val LOCATION_TIMEOUT_MS = 5000L // 5 seconds max
        private const val CACHE_MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val repository = LocationRepository.getInstance(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "LocationWorker started - checking tracking status")

        // Check if tracking is enabled
        if (!repository.isTrackingEnabled()) {
            Log.d(TAG, "Tracking disabled, skipping")
            return Result.success()
        }

        // Check permissions
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

            // Step 1: Try cached location first
            val cachedLocation = getCachedLocation()
            if (cachedLocation != null) {
                Log.d(TAG, "Using cached location (${cachedLocation.accuracy}m accuracy)")

                val locationData = LocationData.fromLocation(
                    location = cachedLocation,
                    source = LocationData.LocationSource.BACKGROUND_WORKER,
                    activityType = if (repository.getLastActivityState()) "MOVING" else "STILL",
                    batteryLevel = repository.getBatteryLevel(applicationContext)
                )

                repository.saveLocation(locationData)

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Work completed in ${duration}ms (cached)")
                return Result.success()
            }

            // Step 2: Request fresh location
            Log.d(TAG, "No fresh cache, requesting new location")
            val freshLocation = requestFreshLocation()

            if (freshLocation != null) {
                val locationData = LocationData.fromLocation(
                    location = freshLocation,
                    source = LocationData.LocationSource.BACKGROUND_WORKER,
                    activityType = if (repository.getLastActivityState()) "MOVING" else "STILL",
                    batteryLevel = repository.getBatteryLevel(applicationContext)
                )

                repository.saveLocation(locationData)

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Work completed in ${duration}ms (fresh)")
                Result.success()
            } else {
                Log.w(TAG, "Failed to get location")
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
     * Try to get cached location (battery-free)
     */
    @Suppress("MissingPermission")
    private fun getCachedLocation(): Location? {
        return try {
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "Missing location permission for cached location")
                return null
            }

            val task = fusedLocationClient.lastLocation
            val location = Tasks.await(task, 1000, TimeUnit.MILLISECONDS)

            // Check if cache is fresh enough
            if (location != null) {
                val age = System.currentTimeMillis() - location.time
                Log.d(TAG, "Cached location age: ${age}ms")

                if (LocationUtils.isLocationFresh(location.time, CACHE_MAX_AGE_MS)) {
                    location
                } else {
                    Log.d(TAG, "Cached location too old")
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

    /**
     * Request fresh location with battery-optimized settings
     */
    @Suppress("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        return try {
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "Missing location permission for fresh location")
                return null
            }

            Log.d(TAG, "Requesting fresh location with balanced power")

            // Use BALANCED_POWER_ACCURACY for battery efficiency
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60000) // Accept up to 1 minute old
                .setDurationMillis(LOCATION_TIMEOUT_MS)
                .build()

            val task = fusedLocationClient.getCurrentLocation(request, null)

            // Wait with timeout
            val location = Tasks.await(task, LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (location == null) {
                Log.w(TAG, "Location request returned null")
            } else {
                Log.d(TAG, "Fresh location obtained: accuracy=${location.accuracy}m")
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