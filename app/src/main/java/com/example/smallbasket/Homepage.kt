// File: app/src/main/java/com/example/smallbasket/Homepage.kt
package com.example.smallbasket

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityHomepageBinding
import com.example.smallbasket.location.*
import com.example.smallbasket.models.MapUserData
import com.example.smallbasket.repository.MapRepository
import com.example.smallbasket.utils.MapUtils
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
        private const val BACKEND_SYNC_DELAY = 3000L

        // 🔧 DEBUG MODE: Set to true to see test heatmap, false to see real users only
        private const val ENABLE_DEBUG_HEATMAP = false  // DISABLED - you have 2 real devices!
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
        requestNotificationPermission()
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
                Log.d(TAG, "Step 1: Getting fresh location...")
                val freshLocation = locationCoordinator.getInstantLocation()

                if (freshLocation != null) {
                    Log.d(TAG, "✓ Got fresh location: (${freshLocation.latitude}, ${freshLocation.longitude})")
                    currentUserLocation = LatLng(freshLocation.latitude, freshLocation.longitude)
                    updateMapWithLocation(freshLocation)

                    Log.d(TAG, "Step 2: Waiting ${BACKEND_SYNC_DELAY}ms for backend sync...")
                    delay(BACKEND_SYNC_DELAY)

                    Log.d(TAG, "Step 3: Querying nearby users...")
                    loadNearbyUsersOnMap(freshLocation.latitude, freshLocation.longitude)
                } else {
                    Log.w(TAG, "Could not get fresh location, trying cached...")
                    val cachedLocation = locationCoordinator.getLastKnownLocation()

                    if (cachedLocation != null) {
                        Log.d(TAG, "Using cached location")
                        currentUserLocation = LatLng(cachedLocation.latitude, cachedLocation.longitude)
                        updateMapWithLocation(cachedLocation)
                        delay(BACKEND_SYNC_DELAY)
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
                Log.i(TAG, "STEP 1: Starting connectivity monitoring...")
                connectivityManager.startMonitoring()

                Log.i(TAG, "STEP 2: Waiting 3 seconds for connectivity sync...")
                delay(3000)

                Log.i(TAG, "STEP 3: Starting background location tracking...")
                locationCoordinator.startTracking()
                Log.d(TAG, "✓ Background tracking started")

                Log.i(TAG, "STEP 4: Getting instant location...")
                val location = locationCoordinator.getInstantLocation()

                if (location != null) {
                    Log.d(TAG, "✓ Got instant location: (${location.latitude}, ${location.longitude})")
                    currentUserLocation = LatLng(location.latitude, location.longitude)
                    updateMapWithLocation(location)

                    Log.d(TAG, "STEP 5: Waiting ${BACKEND_SYNC_DELAY}ms for backend to sync location...")
                    delay(BACKEND_SYNC_DELAY)

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
                        delay(BACKEND_SYNC_DELAY)
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
            Log.d(TAG, "✓ Added YOUR red pointer marker")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding marker", e)
        }
    }

    private fun loadNearbyUsersOnMap(latitude: Double, longitude: Double) {
        Log.d(TAG, "=== Loading nearby users ===")
        Log.d(TAG, "Location: ($latitude, $longitude)")
        Log.d(TAG, "Radius: ${MAP_RADIUS_METERS}m")
        Log.d(TAG, "Debug heatmap: $ENABLE_DEBUG_HEATMAP")

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
                    } else if (ENABLE_DEBUG_HEATMAP) {
                        "Debug mode: Showing test heatmap"
                    } else {
                        "No deliverers found nearby"
                    }
                    Toast.makeText(this@Homepage, message, Toast.LENGTH_SHORT).show()
                }

                result.onFailure { error ->
                    Log.e(TAG, "✗ FAILED: ${error.message}")
                    if (ENABLE_DEBUG_HEATMAP) {
                        Log.d(TAG, "🔧 Debug mode: showing test heatmap anyway")
                        displayUsersOnMap(emptyList())
                    } else {
                        binding.tvOnlineUsers.text = "0"
                    }
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
            Log.d(TAG, "🗺️ Displaying ${users.size} users on map with HEATMAP + GRAY CIRCLES")

            try {
                // Use the new method that adds both heatmap and gray circles
                MapUtils.addHeatmapWithAreas(mapInstance, users, currentUserLocation)

                // Get statistics
                val stats = MapUtils.getHeatmapStats(users, currentUserLocation)

                Log.d(TAG, "✓ Map rendering complete!")
                Log.d(TAG, "  🔥 Total users in heatmap: ${stats.totalUsers}")
                Log.d(TAG, "  📍 Your red pointer: visible")
                Log.d(TAG, "  🎨 Top areas:")
                stats.topAreas.forEach { (area, count) ->
                    val intensity = when {
                        count >= 30 -> "🔴 HIGH"
                        count >= 20 -> "🟠 MEDIUM-HIGH"
                        count >= 10 -> "🟡 MEDIUM"
                        count >= 5 -> "🟢 LOW-MEDIUM"
                        else -> "🔵 LOW"
                    }
                    Log.d(TAG, "    $intensity - $area: $count users")
                }

                // Update user count
                binding.tvOnlineUsers.text = users.size.toString()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error rendering map", e)
                Toast.makeText(this, "Map error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

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
    private fun setupDemoButton() {
        // You can add this as a FloatingActionButton in your layout
        // or call it directly for testing
        binding.btnRefreshUsers.setOnLongClickListener {
            Log.d(TAG, "Long press detected - loading demo users")
            loadDemoUsers()
            true
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
                    Toast.makeText(this@Homepage, "${marker.title}\n${marker.snippet}", Toast.LENGTH_SHORT).show()
                    true
                }

                Log.d(TAG, "Map initialized successfully")
            }
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
                        delay(BACKEND_SYNC_DELAY)
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
        map?.let { MapUtils.removeHeatmapLayer(it) }
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
    private fun loadDemoUsers() {
        Log.d(TAG, "=== LOADING DEMO USERS FOR TESTING ===")

        // Generate demo users
        val demoUsers = MapUtils.getDemoUsers()

        Log.d(TAG, "Generated ${demoUsers.size} demo users")
        demoUsers.forEach { user ->
            Log.d(TAG, "  - ${user.name} at ${user.currentArea}: (${user.latitude}, ${user.longitude})")
        }

        // Display on map
        displayUsersOnMap(demoUsers)

        // Update count
        binding.tvOnlineUsers.text = demoUsers.size.toString()

        Toast.makeText(this, "Loaded ${demoUsers.size} demo users", Toast.LENGTH_SHORT).show()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

