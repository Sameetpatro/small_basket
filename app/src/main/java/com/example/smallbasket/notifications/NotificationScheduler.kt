package com.example.smallbasket.notifications

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"

    /**
     * Schedule promotional notifications
     * Shows every 3 days if user hasn't opened app
     */
    fun schedulePromotionalNotifications(context: Context) {
        Log.d(TAG, "Scheduling promotional notifications")

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<PromotionalNotificationWorker>(
            3, TimeUnit.DAYS,  // Repeat every 3 days
            12, TimeUnit.HOURS  // Flex interval
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                1, TimeUnit.HOURS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PromotionalNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "Promotional notifications scheduled")
    }

    /**
     * Cancel promotional notifications
     */
    fun cancelPromotionalNotifications(context: Context) {
        Log.d(TAG, "Cancelling promotional notifications")
        WorkManager.getInstance(context).cancelUniqueWork(PromotionalNotificationWorker.WORK_NAME)
    }
}