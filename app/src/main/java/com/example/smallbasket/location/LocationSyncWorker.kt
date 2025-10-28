// File: app/src/main/java/com/example/smallbasket/location/LocationSyncWorker.kt
package com.example.smallbasket.location

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smallbasket.api.RetrofitClient
import com.google.firebase.auth.FirebaseAuth

/**
 * Worker to sync location data with backend API
 * Runs after LocationWorker captures location
 */
class LocationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocationSyncWorker"
        const val WORK_NAME = "location_sync_work"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ACCURACY = "accuracy"
    }

    private val api = RetrofitClient.apiService

    override suspend fun doWork(): Result {
        Log.d(TAG, "LocationSyncWorker started")

        // Check if user is authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated, skipping sync")
            return Result.success()
        }

        // Get location data from input
        val latitude = inputData.getDouble(KEY_LATITUDE, 0.0)
        val longitude = inputData.getDouble(KEY_LONGITUDE, 0.0)
        val accuracy = inputData.getDouble(KEY_ACCURACY, 0.0).toFloat()

        if (latitude == 0.0 && longitude == 0.0) {
            Log.w(TAG, "Invalid location data, skipping sync")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Syncing location to backend: ($latitude, $longitude)")

            // Create request body
            val request = UpdateGPSLocationRequest(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                fastMode = true // Use fast mode for background updates
            )

            // Call backend API
            val response = api.updateGPSLocation(request)

            if (response.isSuccessful) {
                Log.d(TAG, "Location synced successfully")
                Result.success()
            } else {
                Log.w(TAG, "Failed to sync location: ${response.code()}")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing location", e)
            Result.retry()
        }
    }
}

// Request model for updating GPS location
data class UpdateGPSLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val fastMode: Boolean = true
)