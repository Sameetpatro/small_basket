// File: app/src/main/java/com/example/smallbasket/location/ConnectivityStatusManager.kt
package com.example.smallbasket.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.ConnectivityUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CRITICAL: Manages connectivity status and syncs with backend
 * This is what makes users "reachable" in the backend
 */
class ConnectivityStatusManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ConnectivityManager"
        private const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        private var instance: ConnectivityStatusManager? = null

        fun getInstance(context: Context): ConnectivityStatusManager {
            return instance ?: synchronized(this) {
                instance ?: ConnectivityStatusManager(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val api = RetrofitClient.apiService
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isMonitoring = false
    private var lastUpdateTime = 0L

    /**
     * CRITICAL: Start monitoring connectivity and update backend
     * This makes the user "reachable"
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring")
            return
        }

        isMonitoring = true
        Log.i(TAG, "=== Starting Connectivity Monitoring ===")

        // Register network callback
        registerNetworkCallback()

        // Start periodic updates
        startPeriodicUpdates()

        // Do initial update immediately
        scope.launch {
            updateConnectivityStatus()
        }
    }

    /**
     * Stop monitoring (on logout or app termination)
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.i(TAG, "Stopped connectivity monitoring")
    }

    /**
     * Register network callback to detect connectivity changes
     */
    private fun registerNetworkCallback() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                scope.launch {
                    delay(1000) // Wait for connection to stabilize
                    updateConnectivityStatus()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                scope.launch {
                    updateConnectivityStatus()
                }
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    /**
     * Start periodic connectivity updates (every 5 minutes)
     */
    private fun startPeriodicUpdates() {
        scope.launch {
            while (isMonitoring) {
                try {
                    // Check if enough time has passed since last update
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                        updateConnectivityStatus()
                        lastUpdateTime = now
                    }

                    // Wait 30 seconds before checking again
                    delay(30_000)

                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic update", e)
                    delay(60_000) // Wait 1 minute on error
                }
            }
        }
    }

    /**
     * CRITICAL: Update connectivity status on backend
     * This sets is_reachable = true/false
     */
    private suspend fun updateConnectivityStatus() {
        try {
            val isConnected = checkInternetConnectivity()
            val hasLocationPermission = checkLocationPermission()

            Log.d(TAG, "=== Updating Connectivity Status ===")
            Log.d(TAG, "Connected: $isConnected")
            Log.d(TAG, "Location Permission: $hasLocationPermission")
            Log.d(TAG, "Will be reachable: ${isConnected && hasLocationPermission}")

            // Call backend API
            val request = ConnectivityUpdateRequest(
                isConnected = isConnected,
                locationPermissionGranted = hasLocationPermission
            )

            val response = api.updateConnectivity(request)

            if (response.isSuccessful) {
                val data = response.body()
                Log.d(TAG, "✓ Connectivity updated on backend")
                Log.d(TAG, "  - is_reachable: ${data?.data?.get("is_reachable")}")
                Log.d(TAG, "  - is_connected: ${data?.data?.get("is_connected")}")
                Log.d(TAG, "  - location_permission: ${data?.data?.get("location_permission_granted")}")
            } else {
                Log.e(TAG, "✗ Failed to update connectivity: ${response.code()}")
                Log.e(TAG, "  Error: ${response.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception updating connectivity", e)
        }
    }

    /**
     * Check if device has internet connectivity
     */
    private fun checkInternetConnectivity(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connectivity", e)
            false
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun checkLocationPermission(): Boolean {
        return try {
            LocationUtils.hasLocationPermission(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking location permission", e)
            false
        }
    }

    /**
     * Force update connectivity status (call manually if needed)
     */
    suspend fun forceUpdate() {
        Log.d(TAG, "Force updating connectivity status")
        updateConnectivityStatus()
    }
}