package com.example.smallbasket

import android.app.Application
import android.util.Log
import com.example.smallbasket.location.LocationTrackingCoordinator
import com.example.smallbasket.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmallBasketApplication : Application() {

    companion object {
        private const val TAG = "SmallBasketApp"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "SmallBasket Application created")

        // Initialize location tracking coordinator
        try {
            LocationTrackingCoordinator.getInstance(this).initialize()
            Log.d(TAG, "Location tracking coordinator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing location tracking coordinator", e)
        }

        // Initialize notifications
        applicationScope.launch {
            try {
                val notificationManager = com.example.smallbasket.notifications.NotificationManager.getInstance(this@SmallBasketApplication)
                notificationManager.initialize()
                Log.d(TAG, "Notifications initialized")

                // Schedule promotional notifications
                NotificationScheduler.schedulePromotionalNotifications(this@SmallBasketApplication)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing notifications", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        Log.d(TAG, "SmallBasket Application terminated")

        // Clean up coordinator resources
        try {
            LocationTrackingCoordinator.getInstance(this).cleanup()
            Log.d(TAG, "Location tracking coordinator cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up location tracking coordinator", e)
        }
    }
}