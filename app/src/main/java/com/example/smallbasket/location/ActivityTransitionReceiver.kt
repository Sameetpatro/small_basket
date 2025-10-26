package com.example.smallbasket.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives activity transition events and adjusts WorkManager schedule
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityTransition"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return

            CoroutineScope(Dispatchers.Default).launch {
                handleActivityTransitions(context, result)
            }
        }
    }

    /**
     * Process activity transitions and adjust location tracking interval
     */
    private fun handleActivityTransitions(context: Context, result: ActivityTransitionResult) {
        val repository = LocationRepository.getInstance(context)
        val scheduler = LocationWorkScheduler.getInstance(context)
        val activityManager = ActivityRecognitionManager(context)

        for (event in result.transitionEvents) {
            val activityName = activityManager.getActivityName(event.activityType)
            val transitionType = if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                "ENTER"
            } else {
                "EXIT"
            }

            Log.d(TAG, "Activity transition: $activityName $transitionType")

            // Only process ENTER transitions
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                when (event.activityType) {
                    DetectedActivity.WALKING,
                    DetectedActivity.RUNNING,
                    DetectedActivity.ON_BICYCLE -> {
                        // User started moving - increase frequency to 15-20 minutes
                        Log.i(TAG, "User is MOVING - scheduling every 15 minutes")
                        repository.saveActivityState(isMoving = true)
                        scheduler.scheduleLocationWork(intervalMinutes = 15)
                    }

                    DetectedActivity.STILL -> {
                        // User became stationary - decrease frequency to 25-30 minutes
                        Log.i(TAG, "User is STATIONARY - scheduling every 30 minutes")
                        repository.saveActivityState(isMoving = false)
                        scheduler.scheduleLocationWork(intervalMinutes = 30)
                    }
                }
            }
        }
    }
}