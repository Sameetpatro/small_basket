// File: app/src/main/java/com/example/smallbasket/Homepage.kt
package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityHomepageBinding
import com.example.smallbasket.location.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Calendar

class Homepage : AppCompatActivity() {

    companion object {
        private const val TAG = "Homepage"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null

    // ============ LOCATION TRACKING COMPONENTS ============
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var permissionManager: LocationPermissionManager

    // Permission launcher for requesting location permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.handlePermissionResult(permissions)
    }
    // ======================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ============ INITIALIZE LOCATION TRACKING FIRST ============
        initializeLocationTracking()
        // ============================================================

        // Greeting
        binding.tvGreeting.text = getGreeting()
        binding.tvUserName.text = updateName()

        // Profile click
        binding.profileSection.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Order now
        binding.btnOrderNow.setOnClickListener {
            val intent = Intent(this, OrderActivity::class.java)
            intent.putExtra("username", updateName())
            startActivity(intent)
        }

        // Notifications
        binding.notification.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        // Initialize MapLibre
        initializeMap(savedInstanceState)

        // Setup custom bottom navigation
        setupCustomBottomNav()
    }

    // ============ LOCATION TRACKING METHODS ============

    /**
     * Initialize location tracking coordinator and permission manager
     */
    private fun initializeLocationTracking() {
        Log.d(TAG, "Initializing location tracking")

        try {
            // Get singleton instances
            locationCoordinator = LocationTrackingCoordinator.getInstance(this)
            permissionManager = LocationPermissionManager(this)
            permissionManager.setPermissionLauncher(permissionLauncher)

            // Check permissions and start tracking if ready
            checkAndStartTracking()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing location tracking", e)
            Toast.makeText(
                this,
                "Location tracking initialization failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Check permissions and start tracking if all requirements are met
     */
    private fun checkAndStartTracking() {
        Log.d(TAG, "Checking permissions and requirements")

        if (permissionManager.hasAllPermissions()) {
            // All permissions granted
            if (LocationUtils.isLocationEnabled(this)) {
                // Location services enabled - start tracking
                startLocationTracking()
            } else {
                // Location services disabled - show dialog
                Log.w(TAG, "Location services are disabled")
                permissionManager.showLocationServicesDialog()
            }
        } else {
            // Missing permissions - request them
            Log.d(TAG, "Requesting location permissions")
            permissionManager.requestPermissions { granted ->
                if (granted) {
                    if (LocationUtils.isLocationEnabled(this)) {
                        startLocationTracking()
                    } else {
                        permissionManager.showLocationServicesDialog()
                    }
                } else {
                    Log.w(TAG, "Location permissions denied")
                    Toast.makeText(
                        this,
                        "Location permission is required for delivery tracking",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Start background location tracking and get instant location
     */
    private fun startLocationTracking() {
        Log.i(TAG, "Starting location tracking")

        lifecycleScope.launch {
            try {
                // Start background tracking (WorkManager + Activity Recognition)
                locationCoordinator.startTracking()

                Log.d(TAG, "Background tracking started successfully")

                // Get instant high-accuracy location for foreground display
                val location = locationCoordinator.getInstantLocation()

                if (location != null) {
                    Log.d(TAG, "Got instant location: (${location.latitude}, ${location.longitude})")

                    // Update map with user's location
                    updateMapWithLocation(location)

                    // Show success message
                    Toast.makeText(
                        this@Homepage,
                        "Location tracking active",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.w(TAG, "Failed to get instant location")

                    // Try to get last known location instead
                    val lastLocation = locationCoordinator.getLastKnownLocation()
                    if (lastLocation != null) {
                        Log.d(TAG, "Using last known location")
                        updateMapWithLocation(lastLocation)
                    }
                }

                // Log tracking stats
                val stats = locationCoordinator.getTrackingStats()
                Log.d(TAG, "Tracking stats: $stats")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting location tracking", e)
                Toast.makeText(
                    this@Homepage,
                    "Error starting location tracking: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Update map camera to show user's location
     */
    private fun updateMapWithLocation(location: LocationData) {
        try {
            val latLng = LatLng(location.latitude, location.longitude)

            Log.d(TAG, "Updating map to location: $latLng (accuracy: ${location.accuracy}m)")

            // Animate camera to user's location with zoom
            map?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    latLng,
                    15.0 // Zoom level
                ),
                1000 // Animation duration in ms
            )

            // Optional: Add a marker at user's location
            // addUserMarker(latLng)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating map location", e)
        }
    }

    /**
     * Optional: Add a marker at user's location
     * Uncomment if you want to show a marker
     */
    /*
    private fun addUserMarker(latLng: LatLng) {
        map?.let { mapInstance ->
            // Remove previous marker if exists
            // Add new marker at user location
            // This is optional - implement based on your needs
        }
    }
    */

    /**
     * Check tracking status (useful for debugging)
     */
    private fun checkTrackingStatus() {
        val isTracking = locationCoordinator.isTracking()
        val stats = locationCoordinator.getTrackingStats()

        Log.d(TAG, """
            Tracking Status:
            - Enabled: $isTracking
            - Activity: ${stats.currentActivityState}
            - Interval: ${stats.currentInterval}
            - WorkManager: ${stats.workManagerScheduled}
            - Last Location: ${stats.lastLocationTimestamp}
        """.trimIndent())
    }

    // ====================================================

    private fun setupCustomBottomNav() {
        // Home
        binding.navHome.setOnClickListener {
            // Already on home
        }

        // Browse Requests
        binding.navBrowse.setOnClickListener {
            startActivity(Intent(this, RequestActivity::class.java))
        }

        // My Logs
        binding.navActivity.setOnClickListener {
            startActivity(Intent(this, MyLogsActivity::class.java))
        }

        // Profile
        binding.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.uiSettings.isLogoEnabled = false
            mapLibreMap.uiSettings.isAttributionEnabled = true
            mapLibreMap.uiSettings.attributionGravity =
                android.view.Gravity.BOTTOM or android.view.Gravity.END
            mapLibreMap.uiSettings.setAttributionMargins(0, 0, 8, 8)
            mapLibreMap.uiSettings.isRotateGesturesEnabled = false

            mapLibreMap.setStyle(
                Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
            ) {
                // Default to Delhi initially
                val delhi = LatLng(28.6139, 77.2090)
                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(delhi)
                    .zoom(12.0)
                    .build()

                Log.d(TAG, "Map initialized successfully")
            }
        }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private fun updateName(): String {
        return auth.currentUser?.displayName ?: "User"
    }

    // MapView lifecycle methods
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()

        // Optional: Check tracking status when returning to app
        if (::locationCoordinator.isInitialized) {
            checkTrackingStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()

        // NOTE: We don't stop tracking here because we want it to continue
        // in the background. Tracking will continue via WorkManager.
        Log.d(TAG, "Homepage destroyed - tracking continues in background")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}