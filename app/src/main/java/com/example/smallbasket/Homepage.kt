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
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay


class Homepage : AppCompatActivity() {

    companion object {
        private const val TAG = "Homepage"
        private const val DEFAULT_ZOOM = 15.0
        private const val MAP_RADIUS_METERS = 5000.0
        private const val BACKEND_SYNC_DELAY = 3000L // 3 seconds for backend to process
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var isMapExpanded = false

    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var permissionManager: LocationPermissionManager
    private lateinit var connectivityManager: ConnectivityStatusManager

    private val mapRepository = MapRepository()
    private val userMarkers = mutableMapOf<Marker, MapUserData>()
    private var currentUserLocation: LatLng? = null
    private var isLoadingNearbyUsers = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        permissionManager.handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeLocationTracking()
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

    private fun setupRefreshButton() {
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

        Log.d(TAG, "=== MANUAL REFRESH TRIGGERED ===")
        showLoadingState(true)

        lifecycleScope.launch {
            try {
                // Step 1: Get fresh location
                Log.d(TAG, "Step 1: Getting fresh location...")
                val freshLocation = locationCoordinator.getInstantLocation()

                if (freshLocation != null) {
                    Log.d(TAG, "✓ Got fresh location: (${freshLocation.latitude}, ${freshLocation.longitude})")
                    currentUserLocation = LatLng(freshLocation.latitude, freshLocation.longitude)
                    updateMapWithLocation(freshLocation)

                    // Step 2: CRITICAL - Wait for backend to process location
                    Log.d(TAG, "Step 2: Waiting ${BACKEND_SYNC_DELAY}ms for backend sync...")
                    kotlinx.coroutines.delay(BACKEND_SYNC_DELAY)

                    // Step 3: Query nearby users
                    Log.d(TAG, "Step 3: Querying nearby users...")
                    loadNearbyUsersOnMap(freshLocation.latitude, freshLocation.longitude)

                } else {
                    // Fallback to cached location
                    Log.w(TAG, "Could not get fresh location, trying cached...")
                    val cachedLocation = locationCoordinator.getLastKnownLocation()

                    if (cachedLocation != null) {
                        Log.d(TAG, "Using cached location")
                        currentUserLocation = LatLng(cachedLocation.latitude, cachedLocation.longitude)
                        updateMapWithLocation(cachedLocation)

                        kotlinx.coroutines.delay(BACKEND_SYNC_DELAY)
                        loadNearbyUsersOnMap(cachedLocation.latitude, cachedLocation.longitude)
                    } else {
                        Log.e(TAG, "No location available at all")
                        showError("Unable to get your location. Please check GPS.")
                        showLoadingState(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during refresh", e)
                showError("Error: ${e.message}")
                showLoadingState(false)
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.tvOnlineUsers.text = "Error"
    }

    private fun showLoadingState(loading: Boolean) {
        isLoadingNearbyUsers = loading

        binding.apply {
            if (loading) {
                progressBar.visibility = View.VISIBLE
                tvOnlineUsers.text = "..."
                onlineUsersCard.alpha = 0.7f
            } else {
                progressBar.visibility = View.GONE
                onlineUsersCard.alpha = 1.0f
            }
        }
    }

    private fun initializeLocationTracking() {
        Log.d(TAG, "=== Initializing Location Tracking ===")

        try {
            locationCoordinator = LocationTrackingCoordinator.getInstance(this)
            permissionManager = LocationPermissionManager(this)
            permissionManager.setPermissionLauncher(permissionLauncher)

            // CRITICAL: Initialize connectivity manager with applicationContext
            connectivityManager = ConnectivityStatusManager.getInstance(applicationContext)

            Log.d(TAG, "✓ Location coordinator and connectivity manager initialized")
            checkAndStartTracking()

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error initializing location tracking", e)
            Toast.makeText(this, "Location setup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndStartTracking() {
        Log.d(TAG, "=== Checking Permissions ===")

        val hasPermissions = permissionManager.hasAllPermissions()
        val locationEnabled = LocationUtils.isLocationEnabled(this)

        Log.d(TAG, "Permissions: $hasPermissions, Location enabled: $locationEnabled")

        when {
            !hasPermissions -> {
                Log.d(TAG, "Requesting permissions")
                requestPermissions()
            }
            !locationEnabled -> {
                Log.w(TAG, "Location services disabled")
                permissionManager.showLocationServicesDialog()
            }
            else -> {
                Log.i(TAG, "✓ Starting tracking")
                startLocationTracking()
            }
        }
    }

    private fun requestPermissions() {
        permissionManager.requestPermissions { granted ->
            Log.d(TAG, "Permission granted: $granted")
            if (granted && LocationUtils.isLocationEnabled(this)) {
                startLocationTracking()
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startLocationTracking() {
        Log.i(TAG, "=== Starting Location Tracking ===")

        lifecycleScope.launch {
            try {
                // CRITICAL: Start connectivity monitoring FIRST
                Log.i(TAG, "STEP 1: Starting connectivity monitoring...")
                connectivityManager.startMonitoring()

                // Wait for connectivity to sync with backend
                Log.i(TAG, "STEP 2: Waiting 3 seconds for connectivity sync...")
                delay(3000)

                // Start background tracking
                Log.i(TAG, "STEP 3: Starting background location tracking...")
                locationCoordinator.startTracking()
                Log.d(TAG, "✓ Background tracking started")

                // Get instant location WITH sync
                Log.i(TAG, "STEP 4: Getting instant location...")
                val location = locationCoordinator.getInstantLocation()

                if (location != null) {
                    Log.d(TAG, "✓ Got instant location: (${location.latitude}, ${location.longitude})")
                    currentUserLocation = LatLng(location.latitude, location.longitude)
                    updateMapWithLocation(location)

                    // CRITICAL: Wait for backend to process
                    Log.d(TAG, "STEP 5: Waiting ${BACKEND_SYNC_DELAY}ms for backend to sync location...")
                    kotlinx.coroutines.delay(BACKEND_SYNC_DELAY)

                    // NOW load nearby users
                    Log.d(TAG, "STEP 6: Loading nearby users...")
                    showLoadingState(true)
                    loadNearbyUsersOnMap(location.latitude, location.longitude)

                    Toast.makeText(this@Homepage, "Location tracking active ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Failed to get instant location")
                    val lastLocation = locationCoordinator.getLastKnownLocation()

                    if (lastLocation != null) {
                        currentUserLocation = LatLng(lastLocation.latitude, lastLocation.longitude)
                        updateMapWithLocation(lastLocation)
                        kotlinx.coroutines.delay(BACKEND_SYNC_DELAY)
                        showLoadingState(true)
                        loadNearbyUsersOnMap(lastLocation.latitude, lastLocation.longitude)
                    } else {
                        showLoadingState(false)
                        Toast.makeText(this@Homepage, "Unable to get location", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error starting tracking", e)
                showLoadingState(false)
                Toast.makeText(this@Homepage, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateMapWithLocation(location: LocationData) {
        try {
            val latLng = LatLng(location.latitude, location.longitude)
            currentUserLocation = latLng

            Log.d(TAG, "Updating map to: $latLng (accuracy: ${location.accuracy}m)")

            map?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM),
                1000
            )

            map?.let { addCurrentUserMarker(it, latLng) }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating map", e)
        }
    }

    private fun addCurrentUserMarker(mapInstance: MapLibreMap, latLng: LatLng) {
        try {
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("You")
                .snippet("Current location")

            mapInstance.addMarker(markerOptions)
            Log.d(TAG, "Added current user marker")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding marker", e)
        }
    }

    private fun loadNearbyUsersOnMap(latitude: Double, longitude: Double) {
        Log.d(TAG, "=== Loading nearby users ===")
        Log.d(TAG, "Location: ($latitude, $longitude)")
        Log.d(TAG, "Radius: ${MAP_RADIUS_METERS}m")

        lifecycleScope.launch {
            try {
                showLoadingState(true)

                val result = mapRepository.getNearbyUsers(latitude, longitude, MAP_RADIUS_METERS)

                result.onSuccess { response ->
                    Log.d(TAG, "✓ SUCCESS! Found ${response.total} users")
                    Log.d(TAG, "Users: ${response.users.map { "${it.name ?: it.email} at (${it.latitude}, ${it.longitude})" }}")

                    displayUsersOnMap(response.users)

                    val message = if (response.total > 0) {
                        "Found ${response.total} deliverers nearby"
                    } else {
                        "No deliverers found nearby"
                    }
                    Toast.makeText(this@Homepage, message, Toast.LENGTH_SHORT).show()
                }

                result.onFailure { error ->
                    Log.e(TAG, "✗ FAILED: ${error.message}")
                    binding.tvOnlineUsers.text = "0"
                    Toast.makeText(this@Homepage, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }

                showLoadingState(false)

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading users", e)
                binding.tvOnlineUsers.text = "0"
                showLoadingState(false)
                Toast.makeText(this@Homepage, "Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayUsersOnMap(users: List<MapUserData>) {
        map?.let { mapInstance ->
            // Clear existing markers
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

                    Log.d(TAG, "✓ Added marker for ${user.name ?: user.email}")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error adding marker for ${user.uid}", e)
                }
            }

            // Update count
            binding.tvOnlineUsers.text = users.size.toString()

            Log.d(TAG, "✓ Displayed ${users.size} users successfully")
        } ?: run {
            Log.w(TAG, "Map not initialized")
        }
    }

    private fun setupCustomBottomNav() {
        binding.navHome.setOnClickListener { /* Already home */ }
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

            mapLibreMap.setStyle(
                Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
            ) { style ->
                Log.d(TAG, "Map style loaded")

                val delhi = LatLng(28.6139, 77.2090)
                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(delhi)
                    .zoom(12.0)
                    .build()

                mapLibreMap.setOnMarkerClickListener { marker ->
                    val userData = userMarkers[marker]
                    if (userData != null) {
                        showUserDetailsBottomSheet(userData)
                        true
                    } else {
                        Toast.makeText(this@Homepage, "${marker.title}\n${marker.snippet}", Toast.LENGTH_SHORT).show()
                        true
                    }
                }

                Log.d(TAG, "Map initialized successfully")
            }
        }
    }

    private fun showUserDetailsBottomSheet(userData: MapUserData) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_user_marker, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvUserName).text =
            userData.name ?: userData.email.substringBefore("@")
        dialogView.findViewById<TextView>(R.id.tvUserEmail).text = userData.email
        dialogView.findViewById<TextView>(R.id.tvCurrentArea).text =
            userData.currentArea ?: "Unknown"

        val lastActiveText = if (userData.lastUpdated != null) {
            try { formatLastActive(userData.lastUpdated) } catch (e: Exception) { "Recently active" }
        } else "Recently active"
        dialogView.findViewById<TextView>(R.id.tvLastActive).text = lastActiveText

        val accuracyText = if (userData.accuracy != null) "±${userData.accuracy.toInt()}m" else "Unknown"
        dialogView.findViewById<TextView>(R.id.tvAccuracy).text = accuracyText

        val onlineIndicator = dialogView.findViewById<View>(R.id.onlineIndicator)
        onlineIndicator.setBackgroundColor(
            if (userData.isReachable) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        dialogView.findViewById<Button>(R.id.btnViewProfile).setOnClickListener {
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
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp) ?: return "Recently"

            val diff = System.currentTimeMillis() - date.time
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
        binding.mapCard.setOnClickListener {
            Log.d(TAG, "Map card clicked")
            toggleMapSize()
        }
    }

    private fun toggleMapSize() {
        if (isMapExpanded) collapseMap() else expandMap()
    }

    private fun expandMap() {
        isMapExpanded = true
        // Implementation remains the same
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

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()

        if (::locationCoordinator.isInitialized) {
            lifecycleScope.launch {
                try {
                    val location = locationCoordinator.getLastKnownLocation()
                    if (location != null) {
                        currentUserLocation = LatLng(location.latitude, location.longitude)
                        showLoadingState(true)
                        kotlinx.coroutines.delay(BACKEND_SYNC_DELAY)
                        loadNearbyUsersOnMap(location.latitude, location.longitude)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error on resume", e)
                    showLoadingState(false)
                }
            }
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}