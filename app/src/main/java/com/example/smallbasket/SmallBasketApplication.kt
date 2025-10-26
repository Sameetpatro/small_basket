// File: app/src/main/java/com/example/smallbasket/SmallBasketApplication.kt
package com.example.smallbasket

import android.app.Application
import android.util.Log
import com.example.smallbasket.location.LocationTrackingCoordinator

/**
 * Application class for SmallBasket
 * Initializes location tracking coordinator on app startup
 */
class SmallBasketApplication : Application() {

    companion object {
        private const val TAG = "SmallBasketApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "SmallBasket Application created")

        // Initialize location tracking coordinator
        // This sets up the lifecycle observer and prepares the system
        try {
            LocationTrackingCoordinator.getInstance(this).initialize()
            Log.d(TAG, "Location tracking coordinator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing location tracking coordinator", e)
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