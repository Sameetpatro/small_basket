package com.example.smallbasket.location

import android.content.Context
import android.util.Log
//import androidx.constraintlayout.widget.Constraints
import androidx.work.*
import java.util.concurrent.TimeUnit

//private val work: Any

/**
 * Manages WorkManager scheduling with dynamic intervals
 * Uses REPLACE policy to update intervals based on activity
 */
class LocationWorkScheduler private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LocationWorkScheduler"

        @Volatile
        private var instance: LocationWorkScheduler? = null

        fun getInstance(context: Context): LocationWorkScheduler {
            return instance ?: synchronized(this) {
                instance ?: LocationWorkScheduler(context).also { instance = it }
            }
        }
    }

    /**
     * Schedule periodic location work with specified interval
     * Uses REPLACE policy to update existing work
     *
     * @param intervalMinutes 15 for moving, 30 for stationary
     */
    fun scheduleLocationWork(intervalMinutes: Long = 15) {
        Log.d(TAG, "Scheduling location work every $intervalMinutes minutes")

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // Run even on low battery
            .build()

        val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval for battery optimization
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                10, TimeUnit.MINUTES
            )
            .addTag("location_tracking")
            .build()

        // Use REPLACE to update interval when activity state changes
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LocationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Changed from REPLACE for better behavior
            locationWorkRequest
        )

        Log.d(TAG, "Location work scheduled successfully")
    }

    /**
     * Cancel all location tracking work
     */
    fun cancelLocationWork() {
        Log.d(TAG, "Cancelling location work")
        WorkManager.getInstance(context).cancelUniqueWork(LocationWorker.WORK_NAME)
    }

    /**
     * Check if location work is scheduled
     */
    fun isWorkScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(LocationWorker.WORK_NAME)
            .get()

        return workInfos.any { !it.state.isFinished }
    }
}