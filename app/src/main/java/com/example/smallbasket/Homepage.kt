// File: app/src/main/java/com/example/smallbasket/Homepage.kt
package com.example.smallbasket

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory
import java.util.Calendar

class Homepage : AppCompatActivity() {

    companion object {
        private const val TAG = "Homepage"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var isMapExpanded = false

    // Location tracking components
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var permissionManager: LocationPermissionManager

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        permissionManager.handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize MapLibre BEFORE setting content view
        MapLibre.getInstance(this)

        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize location tracking FIRST
        initializeLocationTracking()

        // Setup UI
        setupUI()
        initializeMap(savedInstanceState)
        setupCustomBottomNav()
        setupMapClickListener()
    }

    private fun setupUI() {
        binding.tvGreeting.text = getGreeting()
        binding.tvUserName.text = updateName()

        binding.profileSection.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnOrderNow.setOnClickListener {
            val intent = Intent(this, OrderActivity::class.java)
            intent.putExtra("username", updateName())
            startActivity(intent)
        }

        binding.notification.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
    }

    // ============ LOCATION TRACKING METHODS ============

    private fun initializeLocationTracking() {
        Log.d(TAG, "=== Initializing Location Tracking ===")

        try {
            locationCoordinator = LocationTrackingCoordinator.getInstance(this)
            permissionManager = LocationPermissionManager(this)
            permissionManager.setPermissionLauncher(permissionLauncher)

            Log.d(TAG, "Location coordinator and permission manager initialized")
            checkAndStartTracking()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing location tracking", e)
            Toast.makeText(
                this,
                "Location tracking initialization failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkAndStartTracking() {
        Log.d(TAG, "=== Checking Permissions and Starting Tracking ===")

        val hasPermissions = permissionManager.hasAllPermissions()
        val locationEnabled = LocationUtils.isLocationEnabled(this)

        Log.d(TAG, "Has all permissions: $hasPermissions")
        Log.d(TAG, "Location services enabled: $locationEnabled")

        when {
            !hasPermissions -> {
                Log.d(TAG, "Missing permissions - requesting")
                requestPermissions()
            }
            !locationEnabled -> {
                Log.w(TAG, "Location services disabled - showing dialog")
                permissionManager.showLocationServicesDialog()
            }
            else -> {
                Log.i(TAG, "All requirements met - starting tracking")
                startLocationTracking()
            }
        }
    }

    private fun requestPermissions() {
        Log.d(TAG, "Requesting location permissions")

        permissionManager.requestPermissions { granted ->
            Log.d(TAG, "Permission request completed. Granted: $granted")

            if (granted) {
                if (LocationUtils.isLocationEnabled(this)) {
                    Log.i(TAG, "Permissions granted and location enabled - starting tracking")
                    startLocationTracking()
                } else {
                    Log.w(TAG, "Permissions granted but location services disabled")
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

    private fun startLocationTracking() {
        Log.i(TAG, "=== Starting Location Tracking ===")

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Calling coordinator.startTracking()")
                locationCoordinator.startTracking()

                Log.d(TAG, "Background tracking started successfully")

                // Get instant location for foreground display
                Log.d(TAG, "Requesting instant location")
                val location = locationCoordinator.getInstantLocation()

                if (location != null) {
                    Log.d(TAG, "Got instant location: (${location.latitude}, ${location.longitude})")
                    updateMapWithLocation(location)

                    Toast.makeText(
                        this@Homepage,
                        "Location tracking active ✓",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.w(TAG, "Failed to get instant location - trying last known")

                    val lastLocation = locationCoordinator.getLastKnownLocation()
                    if (lastLocation != null) {
                        Log.d(TAG, "Using last known location")
                        updateMapWithLocation(lastLocation)
                    } else {
                        Log.w(TAG, "No location available")
                    }
                }

                // Log tracking stats
                val stats = locationCoordinator.getTrackingStats()
                Log.d(TAG, """
                    === Tracking Stats ===
                    Enabled: ${stats.isTrackingEnabled}
                    Activity: ${stats.currentActivityState}
                    Interval: ${stats.currentInterval}
                    WorkManager: ${stats.workManagerScheduled}
                    Last Location: ${stats.lastLocationTimestamp}
                """.trimIndent())

            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while starting tracking", e)
                Toast.makeText(
                    this@Homepage,
                    "Location permission error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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

    private fun updateMapWithLocation(location: LocationData) {
        try {
            val latLng = LatLng(location.latitude, location.longitude)

            Log.d(TAG, "Updating map to location: $latLng (accuracy: ${location.accuracy}m)")

            map?.let { mapInstance ->
                // Animate camera to user location
                mapInstance.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        latLng,
                        15.0
                    ),
                    1000
                )

                // Add marker for user location
                addUserMarker(mapInstance, latLng, auth.currentUser?.displayName ?: "You")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating map location", e)
        }
    }

    private fun addUserMarker(mapInstance: MapLibreMap, latLng: LatLng, userName: String) {
        try {
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(userName)
                .snippet("Last updated: ${System.currentTimeMillis()}")

            mapInstance.addMarker(markerOptions)
            Log.d(TAG, "Added marker for $userName at $latLng")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding marker", e)
        }
    }

    private fun checkTrackingStatus() {
        val isTracking = locationCoordinator.isTracking()
        val stats = locationCoordinator.getTrackingStats()

        Log.d(TAG, """
            === Current Tracking Status ===
            Enabled: $isTracking
            Activity: ${stats.currentActivityState}
            Interval: ${stats.currentInterval}
            WorkManager: ${stats.workManagerScheduled}
            Last Location: ${stats.lastLocationTimestamp}
        """.trimIndent())
    }

    // ============ MAP METHODS ============

    private fun setupCustomBottomNav() {
        binding.navHome.setOnClickListener {
            // Already on home
        }

        binding.navBrowse.setOnClickListener {
            startActivity(Intent(this, RequestActivity::class.java))
        }

        binding.navActivity.setOnClickListener {
            startActivity(Intent(this, MyLogsActivity::class.java))
        }

        binding.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync { mapLibreMap ->
            map = mapLibreMap

            // Configure map UI settings
            mapLibreMap.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setAttributionMargins(0, 0, 8, 8)
                isRotateGesturesEnabled = false
                isCompassEnabled = true
                isZoomGesturesEnabled = false
            }

            // Load map style
            mapLibreMap.setStyle(
                Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
            ) { style ->
                Log.d(TAG, "Map style loaded successfully")

                // Default to Delhi initially
                val delhi = LatLng(28.6139, 77.2090)
                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(delhi)
                    .zoom(12.0)
                    .build()

                // Enable info window for markers
                mapLibreMap.setOnMarkerClickListener { marker ->
                    Toast.makeText(
                        this@Homepage,
                        "${marker.title}\n${marker.snippet}",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

                Log.d(TAG, "Map initialized successfully")
            }
        }
    }

    private fun setupMapClickListener() {
        binding.mapCard.setOnClickListener {
            toggleMapSize()
        }
    }

    private fun toggleMapSize() {
        if (isMapExpanded) {
            collapseMap()
        } else {
            expandMap()
        }
    }

    private fun expandMap() {
        isMapExpanded = true

        // Create fullscreen dialog
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .create()

        val dialogView = layoutInflater.inflate(R.layout.dialog_expanded_map, null)
        val expandedMapView = dialogView.findViewById<MapView>(R.id.expanded_map_view)
        val closeButton = dialogView.findViewById<View>(R.id.btnCloseMap)

        dialog.setView(dialogView)

        // Initialize expanded map
        expandedMapView.onCreate(null)
        expandedMapView.getMapAsync { expandedMap ->
            expandedMap.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                isCompassEnabled = true
                isZoomGesturesEnabled = true
            }

            expandedMap.setStyle(
                Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
            ) {
                // Copy current camera position from main map
                map?.cameraPosition?.let { position ->
                    expandedMap.cameraPosition = position
                }

                // Add user location marker if available
                val lastLocation = locationCoordinator.getTrackingStats().lastLocationTimestamp
                if (lastLocation != null) {
                    lifecycleScope.launch {
                        val location = locationCoordinator.getLastKnownLocation()
                        location?.let {
                            val latLng = LatLng(it.latitude, it.longitude)
                            addUserMarker(expandedMap, latLng, auth.currentUser?.displayName ?: "You")

                            expandedMap.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                    latLng,
                                    15.0
                                )
                            )
                        }
                    }
                }
            }
        }

        closeButton.setOnClickListener {
            expandedMapView.onDestroy()
            dialog.dismiss()
            isMapExpanded = false
        }

        dialog.setOnDismissListener {
            expandedMapView.onDestroy()
            isMapExpanded = false
        }

        dialog.show()
    }

    private fun collapseMap() {
        isMapExpanded = false
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

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
        Log.d(TAG, "onStart()")
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        Log.d(TAG, "onResume()")

        if (::locationCoordinator.isInitialized) {
            checkTrackingStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        Log.d(TAG, "onPause()")
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        Log.d(TAG, "onStop()")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
        Log.d(TAG, "onDestroy() - tracking continues in background")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}