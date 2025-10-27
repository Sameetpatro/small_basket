package com.example.smallbasket

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.smallbasket.databinding.ActivityHomepageBinding
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class Homepage : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        // Setup transparent status bar BEFORE enableEdgeToEdge
        setupStatusBar()

        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        // Setup scroll listener to change status bar color
        setupScrollListener()
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                // Make status bar transparent
                statusBarColor = Color.TRANSPARENT

                // Allow content to draw behind status bar
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }

        // Set white icons for teal background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+ - Remove light status bar to get white icons
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6.0-10 - Ensure light status bar flag is NOT set (for white icons)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

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

    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            // Change status bar appearance based on scroll position
            // When scrolled past the header (e.g., 200px), change to light icons
            if (scrollY > 200) {
                // Scrolled down - use dark icons for light background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        APPEARANCE_LIGHT_STATUS_BARS,
                        APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } else {
                // At top - use white icons for teal background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
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
                val delhi = LatLng(28.6139, 77.2090)
                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(delhi)
                    .zoom(12.0)
                    .build()
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