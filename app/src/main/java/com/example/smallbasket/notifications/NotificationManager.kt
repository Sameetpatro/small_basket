package com.example.smallbasket.notifications

import android.content.Context
import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.FCMTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class NotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"

        @Volatile
        private var instance: NotificationManager? = null

        fun getInstance(context: Context): NotificationManager {
            return instance ?: synchronized(this) {
                instance ?: NotificationManager(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val api = RetrofitClient.apiService

    /**
     * Initialize notifications (call from Application class)
     */
    suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing notifications...")

            // Request notification permission (Android 13+)
            // This is handled in the activity

            // Get FCM token
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token: $token")

            // Register with backend
            registerFCMToken(token)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing notifications", e)
        }
    }

    /**
     * Register FCM token with backend
     */
    suspend fun registerFCMToken(token: String) {
        try {
            Log.d(TAG, "Registering FCM token with backend...")

            val request = FCMTokenRequest(token)
            val response = api.registerFCMToken(request)

            if (response.isSuccessful) {
                Log.d(TAG, "✓ FCM token registered successfully")
            } else {
                Log.e(TAG, "✗ Failed to register FCM token: ${response.code()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception registering FCM token", e)
        }
    }

    /**
     * Unregister FCM token (on logout)
     */
    suspend fun unregisterFCMToken() {
        try {
            Log.d(TAG, "Unregistering FCM token...")

            val response = api.unregisterFCMToken()

            if (response.isSuccessful) {
                Log.d(TAG, "✓ FCM token unregistered")
            } else {
                Log.e(TAG, "✗ Failed to unregister: ${response.code()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception unregistering", e)
        }
    }
}