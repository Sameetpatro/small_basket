package com.example.smallbasket.location

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import android.os.BatteryManager


/**
 * Repository for managing location data persistence and backend sync
 */
class LocationRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val syncInProgress = AtomicBoolean(false)

    companion object {
        private const val TAG = "LocationRepository"
        private const val PREFS_NAME = "location_tracker"
        private const val KEY_LAST_LOCATION = "last_location"
        private const val KEY_PENDING_LOCATIONS = "pending_locations"
        private const val KEY_LAST_ACTIVITY_STATE = "last_activity_state"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
        private const val MAX_PENDING_LOCATIONS = 100

        @Volatile
        private var instance: LocationRepository? = null

        fun getInstance(context: Context): LocationRepository {
            return instance ?: synchronized(this) {
                instance ?: LocationRepository(context).also { instance = it }
            }
        }
    }

    /**
     * Save location and queue for backend sync
     */
    suspend fun saveLocation(locationData: LocationData) = withContext(Dispatchers.IO) {
        try {
            // Save as last known location
            prefs.edit().putString(KEY_LAST_LOCATION, locationData.toJson()).apply()

            // Add to pending queue for backend sync
            val pending = getPendingLocations().toMutableList()
            pending.add(locationData)

            // Keep only last MAX_PENDING_LOCATIONS
            val trimmed = if (pending.size > MAX_PENDING_LOCATIONS) {
                pending.takeLast(MAX_PENDING_LOCATIONS)
            } else {
                pending
            }

            savePendingLocations(trimmed)

            Log.d(TAG, "Location saved: ${locationData.source} at ${locationData.timestamp}")

            // Try to sync with backend
            syncWithBackend()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving location", e)
        }
    }

    /**
     * Get the last known location
     */
    fun getLastLocation(): LocationData? {
        return try {
            val json = prefs.getString(KEY_LAST_LOCATION, null) ?: return null
            parseLocationFromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading last location", e)
            null
        }
    }

    /**
     * Get all pending locations waiting to be synced
     */
    private fun getPendingLocations(): List<LocationData> {
        return try {
            val json = prefs.getString(KEY_PENDING_LOCATIONS, null) ?: return emptyList()
            val jsonArray = JSONArray(json)
            val locations = mutableListOf<LocationData>()

            for (i in 0 until jsonArray.length()) {
                parseLocationFromJson(jsonArray.getString(i))?.let { locations.add(it) }
            }

            locations
        } catch (e: Exception) {
            Log.e(TAG, "Error reading pending locations", e)
            emptyList()
        }
    }

    /**
     * Save pending locations
     */
    private fun savePendingLocations(locations: List<LocationData>) {
        try {
            val jsonArray = JSONArray()
            locations.forEach { jsonArray.put(it.toJson()) }
            prefs.edit().putString(KEY_PENDING_LOCATIONS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending locations", e)
        }
    }

    /**
     * Sync pending locations with backend
     */
    private suspend fun syncWithBackend() = withContext(Dispatchers.IO) {
        if (!syncInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress")
            return@withContext
        }

        try {
            val pending = getPendingLocations()
            if (pending.isEmpty()) {
                Log.d(TAG, "No pending locations to sync")
                return@withContext
            }

            Log.d(TAG, "Syncing ${pending.size} locations with backend")

            // TODO: Implement your backend sync logic here
            // Example:
            // val result = RetrofitClient.apiService.uploadLocations(pending)
            // if (result.isSuccessful) {
            //     clearPendingLocations()
            //     Log.d(TAG, "Successfully synced locations")
            // }

            // For now, just log
            pending.forEach {
                Log.d(TAG, "Would sync: ${it.source} - (${it.latitude}, ${it.longitude})")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with backend", e)
        } finally {
            syncInProgress.set(false)
        }
    }

    /**
     * Clear all pending locations after successful sync
     */
    fun clearPendingLocations() {
        prefs.edit().remove(KEY_PENDING_LOCATIONS).apply()
        Log.d(TAG, "Cleared pending locations")
    }

    /**
     * Save last activity state
     */
    fun saveActivityState(isMoving: Boolean) {
        prefs.edit().putBoolean(KEY_LAST_ACTIVITY_STATE, isMoving).apply()
        Log.d(TAG, "Activity state saved: ${if (isMoving) "MOVING" else "STATIONARY"}")
    }

    /**
     * Get last activity state
     */
    fun getLastActivityState(): Boolean {
        return prefs.getBoolean(KEY_LAST_ACTIVITY_STATE, false)
    }

    /**
     * Enable/disable location tracking
     */
    fun setTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply()
        Log.d(TAG, "Tracking ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if tracking is enabled
     */
    fun isTrackingEnabled(): Boolean {
        return prefs.getBoolean(KEY_TRACKING_ENABLED, false)
    }

    /**
     * Parse LocationData from JSON string
     */
    private fun parseLocationFromJson(json: String): LocationData? {
        return try {
            val obj = JSONObject(json)
            LocationData(
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                accuracy = obj.getDouble("accuracy").toFloat(),
                timestamp = obj.getLong("timestamp"),
                source = LocationData.LocationSource.valueOf(obj.getString("source")),
                activityType = if (obj.has("activityType") && !obj.isNull("activityType"))
                    obj.getString("activityType") else null,
                batteryLevel = if (obj.has("batteryLevel") && !obj.isNull("batteryLevel"))
                    obj.getInt("batteryLevel") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing location JSON", e)
            null
        }
    }

    /**
     * Get battery level for tracking metadata
     */
    fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level", e)
            -1
        }
    }
}