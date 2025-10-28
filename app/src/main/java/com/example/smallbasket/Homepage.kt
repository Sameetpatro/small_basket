package com.example.smallbasket

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import android.widget.ViewSwitcher
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.smallbasket.databinding.ActivityHomepageBinding
import com.example.smallbasket.location.*
import com.example.smallbasket.models.Order
import com.example.smallbasket.repository.OrderRepository
import com.example.smallbasket.repository.MapRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar

class Homepage : AppCompatActivity() {

    companion object {
        private const val TAG = "Homepage"
        private const val BACKEND_SYNC_DELAY = 3000L // 3 seconds for backend to process location
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private lateinit var activeRequestsContainer: LinearLayout
    private lateinit var onlineUsersSwitcher: ViewSwitcher
    private lateinit var vibrator: Vibrator

    private val orderRepository = OrderRepository()
    private val mapRepository = MapRepository()

    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var permissionManager: LocationPermissionManager
    private lateinit var connectivityManager: ConnectivityStatusManager

    private var currentUserLocation: org.maplibre.android.geometry.LatLng? = null
    private var isLoadingUsers = false

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        permissionManager.handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupStatusBar()
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize haptic system
        initializeHaptics()

        // Initialize location tracking
        initializeLocationTracking()

        // Setup UI
        binding.tvGreeting.text = getGreeting()
        binding.tvUserName.text = updateName()

        // Apply haptics to buttons
        setupButtonHaptics()

        setupCustomBottomNav()
        setupScrollListener()

        activeRequestsContainer = findViewById(R.id.activeRequestsContainer)
        onlineUsersSwitcher = findViewById(R.id.onlineUsersSwitcher)

        // Initially show shimmer until location loads
        onlineUsersSwitcher.displayedChild = 0

        // Load requests
        loadTopTwoRequests()

        // Pull-to-refresh with haptics
        binding.root.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh).setOnRefreshListener {
            performLightHaptic(binding.root)
            refreshAllData()
        }
    }

    // ===== HAPTIC FEEDBACK SYSTEM =====

    private fun initializeHaptics() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun setupButtonHaptics() {
        // Profile section - light tap
        binding.profileSection.setOnClickListener {
            performLightHaptic(it)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Order Now button - medium haptic (same as nav bar)
        binding.btnOrderNow.setOnClickListener {
            performMediumHaptic(it)
            val intent = Intent(this, OrderActivity::class.java)
            intent.putExtra("username", updateName())
            startActivity(intent)
        }

        // Notification - light tap
        binding.notification.setOnClickListener {
            performLightHaptic(it)
            startActivity(Intent(this, NotificationActivity::class.java))
        }
    }

    /**
     * Light haptic - Apple "Impact Light" equivalent
     * Used for: navigation, taps, secondary actions
     */
    private fun performLightHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        // Additional precise vibration for modern devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(10, 40) // 10ms, light amplitude
            )
        }
    }

    /**
     * Medium haptic - Apple "Impact Medium" equivalent
     * Used for: bottom nav, selections, confirmations
     */
    private fun performMediumHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(15, 80) // 15ms, medium amplitude
            )
        }
    }

    /**
     * Emphasized haptic - Apple "Impact Heavy" equivalent
     * Used for: primary actions, order now button
     * HIGHER AMPLITUDE than other buttons
     */
    private fun performEmphasizedHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(25, 150) // 25ms, strong amplitude
            )
        }
    }

    /**
     * Selection haptic - Apple "Selection" equivalent
     * Used for: card taps, item selections
     */
    private fun performSelectionHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(
                HapticFeedbackConstants.GESTURE_START,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } else {
            view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(8, 50) // 8ms, subtle
            )
        }
    }

    // ===== END HAPTIC SYSTEM =====

    override fun onResume() {
        super.onResume()
        loadTopTwoRequests()
    }

    private fun initializeLocationTracking() {
        Log.d(TAG, "=== Initializing Location Tracking ===")

        try {
            locationCoordinator = LocationTrackingCoordinator.getInstance(this)
            permissionManager = LocationPermissionManager(this)
            permissionManager.setPermissionLauncher(permissionLauncher)
            connectivityManager = ConnectivityStatusManager.getInstance(applicationContext)

            Log.d(TAG, "✓ Location components initialized")
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
                    currentUserLocation = org.maplibre.android.geometry.LatLng(location.latitude, location.longitude)

                    Log.d(TAG, "STEP 5: Waiting ${BACKEND_SYNC_DELAY}ms for backend sync...")
                    delay(BACKEND_SYNC_DELAY)

                    Log.d(TAG, "STEP 6: Loading nearby users...")
                    loadNearbyUsers(location.latitude, location.longitude)

                    Toast.makeText(this@Homepage, "Location tracking active ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Failed to get instant location, trying cached...")
                    val lastLocation = locationCoordinator.getLastKnownLocation()

                    if (lastLocation != null) {
                        currentUserLocation = org.maplibre.android.geometry.LatLng(lastLocation.latitude, lastLocation.longitude)
                        delay(BACKEND_SYNC_DELAY)
                        loadNearbyUsers(lastLocation.latitude, lastLocation.longitude)
                    } else {
                        Toast.makeText(this@Homepage, "Unable to get location", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error starting tracking", e)
                Toast.makeText(this@Homepage, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshAllData() {
        val refreshLayout = binding.root.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        refreshLayout.isRefreshing = true

        if (isLoadingUsers) {
            Log.d(TAG, "Already loading, skipping refresh")
            refreshLayout.isRefreshing = false
            return
        }

        runOnUiThread {
            onlineUsersSwitcher.displayedChild = 0
        }

        Log.d(TAG, "=== MANUAL REFRESH TRIGGERED ===")

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Step 1: Getting fresh location...")
                val freshLocation = locationCoordinator.getInstantLocation()

                if (freshLocation != null) {
                    Log.d(TAG, "✓ Got fresh location")
                    currentUserLocation = org.maplibre.android.geometry.LatLng(freshLocation.latitude, freshLocation.longitude)

                    Log.d(TAG, "Step 2: Waiting for backend sync...")
                    delay(BACKEND_SYNC_DELAY)

                    Log.d(TAG, "Step 3: Loading nearby users...")
                    loadNearbyUsers(freshLocation.latitude, freshLocation.longitude)
                    loadTopTwoRequests()
                } else {
                    Log.w(TAG, "Could not get fresh location")
                    Toast.makeText(this@Homepage, "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during refresh", e)
                Toast.makeText(this@Homepage, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                refreshLayout.isRefreshing = false
            }
        }
    }

    private fun loadNearbyUsers(latitude: Double, longitude: Double) {
        if (isLoadingUsers) {
            Log.d(TAG, "Already loading users")
            return
        }

        runOnUiThread {
            if (onlineUsersSwitcher.displayedChild != 0) {
                onlineUsersSwitcher.displayedChild = 0
            }
        }

        isLoadingUsers = true
        Log.d(TAG, "=== Loading nearby users ===")
        Log.d(TAG, "Location: ($latitude, $longitude)")

        lifecycleScope.launch {
            try {
                val result = mapRepository.getNearbyUsers(latitude, longitude, 5000.0)

                result.onSuccess { response ->
                    Log.d(TAG, "✓ SUCCESS! Found ${response.total} users")
                    runOnUiThread {
                        binding.tvOnlineUsers.text = response.total.toString()
                        onlineUsersSwitcher.displayedChild = 1
                    }

                    val message = if (response.total > 0) {
                        "Found ${response.total} deliverers nearby"
                    } else {
                        "No deliverers found nearby"
                    }
                    Toast.makeText(this@Homepage, message, Toast.LENGTH_SHORT).show()
                }

                result.onFailure { error ->
                    Log.e(TAG, "✗ FAILED: ${error.message}")
                    runOnUiThread {
                        binding.tvOnlineUsers.text = "0"
                        onlineUsersSwitcher.displayedChild = 1
                    }
                    Toast.makeText(this@Homepage, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading users", e)
                runOnUiThread {
                    binding.tvOnlineUsers.text = "0"
                    onlineUsersSwitcher.displayedChild = 1
                }
            } finally {
                isLoadingUsers = false
            }
        }
    }

    private fun loadTopTwoRequests() {
        lifecycleScope.launch {
            val result = orderRepository.getAllOrders(status = "open")
            result.onSuccess { orders ->
                runOnUiThread {
                    activeRequestsContainer.removeAllViews()
                    orders.take(2).forEach { order ->
                        val request = DeliveryRequest(
                            orderId = order.id,
                            title = order.items.joinToString(", "),
                            pickup = extractLocation(order.pickupLocation, order.pickupArea),
                            dropoff = extractLocation(order.dropLocation, order.dropArea),
                            fee = formatFee(order),
                            time = calculateTimeDisplay(order.deadline),
                            priority = isPriorityOrder(order.priority),
                            details = order.notes ?: "",
                            bestBefore = order.bestBefore,
                            deadline = order.deadline,
                            rewardPercentage = extractRewardPercentage(order)
                        )
                        val cardView = layoutInflater.inflate(R.layout.item_active_request_card, activeRequestsContainer, false)
                        bindRequestToCard(cardView, request)
                        activeRequestsContainer.addView(cardView)
                    }
                }
            }
        }
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun setupCustomBottomNav() {
        // Home - already active, lighter feedback
        binding.navHome.setOnClickListener {
            performLightHaptic(it)
        }

        // Browse - medium haptic for navigation
        binding.navBrowse.setOnClickListener {
            performMediumHaptic(it)
            startActivity(Intent(this, RequestActivity::class.java))
        }

        // Activity - medium haptic
        binding.navActivity.setOnClickListener {
            performMediumHaptic(it)
            startActivity(Intent(this, MyLogsActivity::class.java))
        }

        // Profile - medium haptic
        binding.navProfile.setOnClickListener {
            performMediumHaptic(it)
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY > 200) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
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

    private fun extractLocation(location: String?, area: String?): String {
        return when {
            location.isNullOrBlank() && area.isNullOrBlank() -> "Unknown"
            location.isNullOrBlank() -> area!!
            area.isNullOrBlank() -> location!!
            else -> "$location, $area"
        }
    }

    private fun extractRewardPercentage(order: Order): Int = try {
        order.reward?.toInt() ?: 0
    } catch (e: Exception) {
        0
    }

    private fun formatFee(order: Order): String {
        val reward = extractRewardPercentage(order)
        return if (reward > 0) "₹$reward" else "₹0"
    }

    private fun isPriorityOrder(priority: String?): Boolean {
        return priority?.equals("emergency", ignoreCase = true) == true ||
                priority?.equals("high", ignoreCase = true) == true ||
                priority?.equals("urgent", ignoreCase = true) == true
    }

    private fun calculateTimeDisplay(deadline: String?): String {
        return when {
            deadline == null || deadline.isEmpty() -> "ASAP"
            deadline.contains("30") || deadline.contains("30m") -> "30 min"
            deadline.contains("1h") || deadline.contains("60") -> "1 hour"
            deadline.contains("2h") || deadline.contains("120") -> "2 hours"
            deadline.contains("4h") || deadline.contains("240") -> "4 hours"
            deadline.lowercase().contains("asap") -> "ASAP"
            else -> deadline
        }
    }

    private fun bindRequestToCard(cardView: View, request: DeliveryRequest) {
        cardView.findViewById<TextView>(R.id.tvRequestTitle).text = request.title
        cardView.findViewById<TextView>(R.id.tvPickupLocation).text = request.pickup
        cardView.findViewById<TextView>(R.id.tvDropoffLocation).text = request.dropoff

        cardView.findViewById<ImageView>(R.id.ivPickupIcon).setColorFilter(getColor(R.color.teal_500))
        cardView.findViewById<ImageView>(R.id.ivDropoffIcon).setColorFilter(getColor(R.color.teal_700))

        val viewLink = cardView.findViewById<TextView>(R.id.tvViewDetail)
        val clickHandler = View.OnClickListener {
            performSelectionHaptic(it) // Selection haptic for card taps
            navigateToDetailFromHome(request)
        }
        viewLink.setOnClickListener(clickHandler)
        cardView.setOnClickListener(clickHandler)
    }

    private fun navigateToDetailFromHome(request: DeliveryRequest) {
        val intent = Intent(this, RequestDetailActivity::class.java).apply {
            putExtra("order_id", request.orderId)
            putExtra("title", request.title)
            putExtra("pickup", request.pickup)
            putExtra("drop", request.dropoff)
            putExtra("details", request.details)
            putExtra("priority", if (request.priority) "emergency" else "normal")
            putExtra("best_before", request.bestBefore)
            putExtra("deadline", request.deadline)
            putExtra("reward_percentage", request.rewardPercentage)
            putExtra("isImportant", request.priority)
            putExtra("fee", request.fee)
            putExtra("time", request.time)
        }
        startActivity(intent)
    }

    data class DeliveryRequest(
        val orderId: String,
        val title: String,
        val pickup: String,
        val dropoff: String,
        val fee: String,
        val time: String,
        val priority: Boolean,
        val details: String,
        val bestBefore: String?,
        val deadline: String?,
        val rewardPercentage: Int
    )

    override fun onDestroy() {
        super.onDestroy()
        if (::connectivityManager.isInitialized) {
            connectivityManager.stopMonitoring()
        }
    }
}