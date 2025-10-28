// File: app/src/main/java/com/example/smallbasket/location/LocationTrackingCoordinator.kt
package com.example.smallbasket.location

import android.content.Context
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main coordinator that orchestrates all location tracking components
 * This is the single entry point for starting/stopping location tracking
 */
class LocationTrackingCoordinator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationCoordinator"

        @Volatile
        private var instance: LocationTrackingCoordinator? = null

        fun getInstance(context: Context): LocationTrackingCoordinator {
            return instance ?: synchronized(this) {
                instance ?: LocationTrackingCoordinator(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val repository = LocationRepository.getInstance(context)
    private val activityRecognitionManager = ActivityRecognitionManager(context)
    private val workScheduler = LocationWorkScheduler.getInstance(context)
    private val foregroundLocationManager = ForegroundLocationManager(context)

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var lifecycleObserver: AppLifecycleObserver? = null
    private var isTrackingActive = false

    /**
     * Initialize the coordinator and set up lifecycle observer
     */
    fun initialize() {
        Log.d(TAG, "Initializing LocationTrackingCoordinator")

        // Set up lifecycle observer
        lifecycleObserver = AppLifecycleObserver(
            context = context,
            onForeground = { handleAppForeground() },
            onBackground = { handleAppBackground() }
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver!!)

        Log.d(TAG, "Coordinator initialized")
    }

    /**
     * Start location tracking
     * This is the main entry point to enable tracking
     */
    fun startTracking() {
        if (!LocationUtils.hasLocationPermission(context)) {
            Log.w(TAG, "Cannot start tracking - no location permission")
            return
        }

        if (!LocationUtils.hasActivityRecognitionPermission(context)) {
            Log.w(TAG, "Cannot start tracking - no activity recognition permission")
            return
        }

        if (!LocationUtils.isLocationEnabled(context)) {
            Log.w(TAG, "Cannot start tracking - location services disabled")
            return
        }

        if (isTrackingActive) {
            Log.d(TAG, "Tracking already active")
            return
        }

        Log.i(TAG, "Starting location tracking")

        // Enable tracking in repository
        repository.setTrackingEnabled(true)
        isTrackingActive = true

        // Start activity recognition to monitor movement
        activityRecognitionManager.startMonitoring()

        // Schedule initial WorkManager job
        // Start with moving interval (15 min), will adjust based on activity
        val isMoving = repository.getLastActivityState()
        val interval = if (isMoving) 15L else 30L
        workScheduler.scheduleLocationWork(interval)

        Log.i(TAG, "Location tracking started (interval: ${interval}min)")
    }

    /**
     * Stop location tracking
     */
    fun stopTracking() {
        if (!isTrackingActive) {
            Log.d(TAG, "Tracking not active")
            return
        }

        Log.i(TAG, "Stopping location tracking")

        // Disable tracking in repository
        repository.setTrackingEnabled(false)
        isTrackingActive = false

        // Stop activity recognition
        activityRecognitionManager.stopMonitoring()

        // Cancel WorkManager jobs
        workScheduler.cancelLocationWork()

        Log.i(TAG, "Location tracking stopped")
    }

    /**
     * Get instant location for foreground use
     */
    suspend fun getInstantLocation(): LocationData? {
        return foregroundLocationManager.getInstantLocation()
    }

    /**
     * Get last known location (instant, no network request)
     */
    suspend fun getLastKnownLocation(): LocationData? {
        return foregroundLocationManager.getLastKnownLocation()
    }

    /**
     * Check if tracking is currently active
     */
    fun isTracking(): Boolean {
        return isTrackingActive && repository.isTrackingEnabled()
    }

    /**
     * Handle app coming to foreground
     */
    private fun handleAppForeground() {
        Log.d(TAG, "App in foreground")

        if (isTrackingActive) {
            // Optionally fetch instant location when app opens
            coordinatorScope.launch {
                try {
                    val location = foregroundLocationManager.getInstantLocation()
                    if (location != null) {
                        Log.d(TAG, "Foreground location updated")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting foreground location", e)
                }
            }
        }
    }

    /**
     * Handle app going to background
     */
    private fun handleAppBackground() {
        Log.d(TAG, "App in background")
        // Background work is handled by WorkManager and Activity Recognition
        // No action needed here
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up coordinator")

        lifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
        }

        if (isTrackingActive) {
            stopTracking()
        }
    }

    /**
     * Force sync pending locations with backend
     */
    suspend fun forceSyncLocations() {
        // Repository handles sync automatically, but this can be called manually
        Log.d(TAG, "Force sync requested")
        // Sync is triggered automatically in repository.saveLocation()
    }

    /**
     * Get tracking statistics
     */
    fun getTrackingStats(): TrackingStats {
        val lastLocation = repository.getLastLocation()
        val isMoving = repository.getLastActivityState()
        val workScheduled = workScheduler.isWorkScheduled()

        return TrackingStats(
            isTrackingEnabled = isTrackingActive,
            lastLocationTimestamp = lastLocation?.timestamp,
            lastLocationSource = lastLocation?.source?.name,
            currentActivityState = if (isMoving) "MOVING" else "STATIONARY",
            workManagerScheduled = workScheduled,
            currentInterval = if (isMoving) "15 minutes" else "30 minutes"
        )
    }
}

/**
 * Data class for tracking statistics
 */
data class TrackingStats(
    val isTrackingEnabled: Boolean,
    val lastLocationTimestamp: Long?,
    val lastLocationSource: String?,
    val currentActivityState: String,
    val workManagerScheduled: Boolean,
    val currentInterval: String
)