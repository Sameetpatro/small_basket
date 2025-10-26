// File: app/src/main/java/com/example/smallbasket/Homepage.kt
package com.example.smallbasket

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityHomepageBinding
import com.example.smallbasket.location.*
import com.example.smallbasket.models.MapUserData
import com.example.smallbasket.repository.MapRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.IconFactory
import java.text.SimpleDateFormat
import java.util.*

class Homepage : AppCompatActivity() {

    companion object {
        private const val TAG = "Homepage"
        private const val DEFAULT_ZOOM = 15.0
        private const val MAP_RADIUS_METERS = 5000.0 // 5km radius
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var isMapExpanded = false

    // Location tracking components
    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var permissionManager: LocationPermissionManager

    // Map repository
    private val mapRepository = MapRepository()

    // Store markers and their associated user data
    private val userMarkers = mutableMapOf<Marker, MapUserData>()

    // Current user location
    private var currentUserLocation: LatLng? = null

    // Loading state
    private var isLoadingNearbyUsers = false

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
        setupRefreshButton()
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

    // ============ REFRESH FUNCTIONALITY ============

    private fun setupRefreshButton() {
        // Add click listener to both refresh button and online users card
        binding.btnRefreshUsers.setOnClickListener {
            refreshNearbyUsers()
        }

        binding.onlineUsersCard.setOnClickListener {
            refreshNearbyUsers()
        }
    }

    private fun refreshNearbyUsers() {
        if (isLoadingNearbyUsers) {
            Log.d(TAG, "Already loading, skipping refresh")
            return
        }

        currentUserLocation?.let { location ->
            Log.d(TAG, "Manual refresh triggered")
            showLoadingState(true)
            loadNearbyUsersOnMap(location.latitude, location.longitude)
        } ?: run {
            Toast.makeText(
                this,
                "Getting your location first...",
                Toast.LENGTH_SHORT
            ).show()

            // Try to get location first
            lifecycleScope.launch {
                try {
                    val location = locationCoordinator.getInstantLocation()
                    if (location != null) {
                        currentUserLocation = LatLng(location.latitude, location.longitude)
                        updateMapWithLocation(location)
                        loadNearbyUsersOnMap(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(
                            this@Homepage,
                            "Unable to get location. Please check permissions.",
                            Toast.LENGTH_SHORT
                        ).show()
                        showLoadingState(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting location for refresh", e)
                    Toast.makeText(
                        this@Homepage,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showLoadingState(false)
                }
            }
        }
    }

    private fun showLoadingState(loading: Boolean) {
        isLoadingNearbyUsers = loading

        binding.apply {
            if (loading) {
                // Show loading indicator
                progressBar.visibility = View.VISIBLE
                tvOnlineUsers.text = "..."

                // Add pulsing animation to online users card
                onlineUsersCard.alpha = 0.7f
            } else {
                // Hide loading indicator
                progressBar.visibility = View.GONE
                onlineUsersCard.alpha = 1.0f
            }
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
                    currentUserLocation = LatLng(location.latitude, location.longitude)
                    updateMapWithLocation(location)

                    // Load nearby users on map with loading state
                    showLoadingState(true)
                    loadNearbyUsersOnMap(location.latitude, location.longitude)

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
                        currentUserLocation = LatLng(lastLocation.latitude, lastLocation.longitude)
                        updateMapWithLocation(lastLocation)
                        showLoadingState(true)
                        loadNearbyUsersOnMap(lastLocation.latitude, lastLocation.longitude)
                    } else {
                        Log.w(TAG, "No location available")
                        showLoadingState(false)
                    }
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while starting tracking", e)
                Toast.makeText(
                    this@Homepage,
                    "Location permission error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showLoadingState(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting location tracking", e)
                Toast.makeText(
                    this@Homepage,
                    "Error starting location tracking: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showLoadingState(false)
            }
        }
    }

    private fun updateMapWithLocation(location: LocationData) {
        try {
            val latLng = LatLng(location.latitude, location.longitude)
            currentUserLocation = latLng

            Log.d(TAG, "Updating map to location: $latLng (accuracy: ${location.accuracy}m)")

            map?.let { mapInstance ->
                // Animate camera to user location
                mapInstance.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        latLng,
                        DEFAULT_ZOOM
                    ),
                    1000
                )

                // Add marker for current user
                addCurrentUserMarker(mapInstance, latLng)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating map location", e)
        }
    }

    private fun addCurrentUserMarker(mapInstance: MapLibreMap, latLng: LatLng) {
        try {
            // Remove existing current user marker if any
            // (You can track this with a separate variable if needed)

            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("You")
                .snippet("Current location")

            mapInstance.addMarker(markerOptions)
            Log.d(TAG, "Added current user marker at $latLng")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding current user marker", e)
        }
    }

    // ============ MAP FUNCTIONALITY ============

    private fun loadNearbyUsersOnMap(latitude: Double, longitude: Double) {
        Log.d(TAG, "Loading nearby users on map for location: ($latitude, $longitude)")

        lifecycleScope.launch {
            try {
                showLoadingState(true)

                val result = mapRepository.getNearbyUsers(latitude, longitude, MAP_RADIUS_METERS)

                result.onSuccess { response ->
                    Log.d(TAG, "✓ Found ${response.total} nearby users")
                    Log.d(TAG, "Users list: ${response.users.map { it.name ?: it.email }}")

                    displayUsersOnMap(response.users)

                    Toast.makeText(
                        this@Homepage,
                        "Found ${response.total} deliverers nearby",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                result.onFailure { error ->
                    Log.e(TAG, "✗ Error loading nearby users: ${error.message}")

                    // Show error message
                    Toast.makeText(
                        this@Homepage,
                        "Error loading users: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Set count to 0
                    binding.tvOnlineUsers.text = "0"
                }

                showLoadingState(false)

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading nearby users", e)
                Toast.makeText(
                    this@Homepage,
                    "Exception: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                binding.tvOnlineUsers.text = "0"
                showLoadingState(false)
            }
        }
    }

    private fun displayUsersOnMap(users: List<MapUserData>) {
        map?.let { mapInstance ->
            // Clear existing user markers
            userMarkers.keys.forEach { marker ->
                mapInstance.removeMarker(marker)
            }
            userMarkers.clear()

            Log.d(TAG, "Displaying ${users.size} users on map")

            // Add markers for each user
            users.forEach { user ->
                try {
                    val latLng = LatLng(user.latitude, user.longitude)

                    val markerOptions = MarkerOptions()
                        .position(latLng)
                        .title(user.name ?: user.email.substringBefore("@"))
                        .snippet(user.currentArea ?: "Unknown Area")

                    val marker = mapInstance.addMarker(markerOptions)
                    userMarkers[marker] = user

                    Log.d(TAG, "✓ Added marker for ${user.name ?: user.email} at $latLng")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error adding marker for user ${user.uid}", e)
                }
            }

            // Update online users count
            binding.tvOnlineUsers.text = users.size.toString()

            Log.d(TAG, "✓ Successfully displayed ${users.size} users on map")
        } ?: run {
            Log.w(TAG, "Map not initialized, cannot display users")
        }
    }

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
                isRotateGesturesEnabled = true
                isCompassEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            // Load map style with street view
            mapLibreMap.setStyle(
                Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
            ) { style ->
                Log.d(TAG, "Map style loaded successfully")

                // Default to Delhi initially
                val delhi = LatLng(28.6139, 77.2090)
                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(delhi)
                    .zoom(12.0)
                    .build()

                // Enable marker click listener
                mapLibreMap.setOnMarkerClickListener { marker ->
                    val userData = userMarkers[marker]
                    if (userData != null) {
                        showUserDetailsBottomSheet(userData)
                        true
                    } else {
                        Toast.makeText(
                            this@Homepage,
                            "${marker.title}\n${marker.snippet}",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                }

                Log.d(TAG, "Map initialized successfully with street view")
            }
        }
    }

    private fun showUserDetailsBottomSheet(userData: MapUserData) {
        val dialogView = LayoutInflater.from(this).inflate(
            R.layout.bottom_sheet_user_marker,
            null
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Populate user details
        dialogView.findViewById<TextView>(R.id.tvUserName).text =
            userData.name ?: userData.email.substringBefore("@")

        dialogView.findViewById<TextView>(R.id.tvUserEmail).text = userData.email

        dialogView.findViewById<TextView>(R.id.tvCurrentArea).text =
            userData.currentArea ?: "Unknown"

        // Format last active time
        val lastActiveText = if (userData.lastUpdated != null) {
            try {
                formatLastActive(userData.lastUpdated)
            } catch (e: Exception) {
                "Recently active"
            }
        } else {
            "Recently active"
        }
        dialogView.findViewById<TextView>(R.id.tvLastActive).text = lastActiveText

        // Show accuracy if available
        val accuracyText = if (userData.accuracy != null) {
            "±${userData.accuracy.toInt()}m"
        } else {
            "Unknown"
        }
        dialogView.findViewById<TextView>(R.id.tvAccuracy).text = accuracyText

        // Online indicator
        val onlineIndicator = dialogView.findViewById<View>(R.id.onlineIndicator)
        if (userData.isReachable) {
            onlineIndicator.setBackgroundColor(Color.parseColor("#10B981")) // Green
        } else {
            onlineIndicator.setBackgroundColor(Color.parseColor("#EF4444")) // Red
        }

        // Button actions
        dialogView.findViewById<Button>(R.id.btnViewProfile).setOnClickListener {
            // TODO: Navigate to user profile
            Toast.makeText(this, "Profile view coming soon", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatLastActive(timestamp: String): String {
        try {
            // Parse ISO 8601 timestamp
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp) ?: return "Recently"

            val now = System.currentTimeMillis()
            val then = date.time
            val diff = now - then

            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                seconds < 60 -> "Just now"
                minutes < 2 -> "1 minute ago"
                minutes < 60 -> "$minutes minutes ago"
                hours < 2 -> "1 hour ago"
                hours < 24 -> "$hours hours ago"
                days < 2 -> "Yesterday"
                days < 7 -> "$days days ago"
                else -> "Over a week ago"
            }
        } catch (e: Exception) {
            return "Recently"
        }
    }

    private fun setupMapClickListener() {
        // Only click on the card background to expand, not the map itself
        binding.mapCard.setOnClickListener {
            Log.d(TAG, "Map card clicked - expanding map")
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

        val dialogView = layoutInflater.inflate(R.layout.dialog_expanded_map, null)
        val expandedMapView = dialogView.findViewById<MapView>(R.id.expanded_map_view)
        val closeButton = dialogView.findViewById<View>(R.id.btnCloseMap)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        // Initialize expanded map
        expandedMapView.onCreate(null)
        expandedMapView.getMapAsync { expandedMap ->
            expandedMap.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                isCompassEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isScrollGesturesEnabled = true
                isTiltGesturesEnabled = true
            }

            expandedMap.setStyle(
                Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
            ) {
                // Copy current camera position from main map
                map?.cameraPosition?.let { position ->
                    expandedMap.cameraPosition = position
                }

                // Add current user marker
                currentUserLocation?.let { location ->
                    val markerOptions = MarkerOptions()
                        .position(location)
                        .title("You")
                        .snippet("Current location")
                    expandedMap.addMarker(markerOptions)
                }

                // Add all user markers
                userMarkers.forEach { (_, userData) ->
                    try {
                        val latLng = LatLng(userData.latitude, userData.longitude)
                        val markerOptions = MarkerOptions()
                            .position(latLng)
                            .title(userData.name ?: userData.email.substringBefore("@"))
                            .snippet(userData.currentArea ?: "Unknown Area")
                        expandedMap.addMarker(markerOptions)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding marker in expanded map", e)
                    }
                }

                // Enable marker click listener
                expandedMap.setOnMarkerClickListener { marker ->
                    val clickedUserData = userMarkers.values.find { user ->
                        marker.position.latitude == user.latitude &&
                                marker.position.longitude == user.longitude
                    }

                    if (clickedUserData != null) {
                        showUserDetailsBottomSheet(clickedUserData)
                        true
                    } else {
                        Toast.makeText(
                            this@Homepage,
                            "${marker.title}\n${marker.snippet}",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                }

                // Animate to user location if available
                currentUserLocation?.let { location ->
                    expandedMap.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            location,
                            DEFAULT_ZOOM
                        )
                    )
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

            // Refresh map with latest location
            lifecycleScope.launch {
                try {
                    val location = locationCoordinator.getLastKnownLocation()
                    if (location != null) {
                        currentUserLocation = LatLng(location.latitude, location.longitude)
                        showLoadingState(true)
                        loadNearbyUsersOnMap(location.latitude, location.longitude)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing map on resume", e)
                    showLoadingState(false)
                }
            }
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