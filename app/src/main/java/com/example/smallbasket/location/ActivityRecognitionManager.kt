package com.example.smallbasket.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Manages Activity Recognition Transitions
 * Monitors when user starts/stops moving with minimal battery usage
 */
class ActivityRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "ActivityRecognition"
        const val ACTION_ACTIVITY_TRANSITION = "com.example.smallbasket.ACTIVITY_TRANSITION"
    }

    private val activityRecognitionClient = ActivityRecognition.getClient(context)
    private val repository = LocationRepository.getInstance(context)

    /**
     * Start monitoring activity transitions
     */
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun startMonitoring() {
        if (!LocationUtils.hasActivityRecognitionPermission(context)) {
            Log.w(TAG, "Activity recognition permission not granted")
            return
        }

        try {
            val transitions = buildTransitionRequest()
            val pendingIntent = createPendingIntent()

            activityRecognitionClient.requestActivityTransitionUpdates(transitions, pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Activity recognition started successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to start activity recognition", e)
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting activity recognition", e)
        }
    }

    /**
     * Stop monitoring activity transitions
     */
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun stopMonitoring() {
        try {
            val pendingIntent = createPendingIntent()
            activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Activity recognition stopped")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to stop activity recognition", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping activity recognition", e)
        }
    }

    /**
     * Build the activity transition request
     * We monitor: WALKING, RUNNING, ON_BICYCLE for MOVING state
     */
    private fun buildTransitionRequest(): ActivityTransitionRequest {
        val transitions = mutableListOf<ActivityTransition>()

        // Define activities that indicate movement
        val movingActivities = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE
        )

        // Define activities that indicate stillness
        val stationaryActivities = listOf(
            DetectedActivity.STILL
        )

        // Add ENTER transitions for moving activities
        movingActivities.forEach { activity ->
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
        }

        // Add ENTER transitions for stationary activities
        stationaryActivities.forEach { activity ->
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
        }

        return ActivityTransitionRequest(transitions)
    }

    /**
     * Create pending intent for receiving activity transitions
     */
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java).apply {
            action = ACTION_ACTIVITY_TRANSITION
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * Get human-readable activity name
     */
    fun getActivityName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN"
        }
    }
}