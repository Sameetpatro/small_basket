package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
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

        // Initialize MapLibre
        MapLibre.getInstance(this)

        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set greeting based on time
        binding.tvGreeting.text = getGreeting()

        // Show user details
        binding.tvUserName.text = updateName()

        binding.profileSection.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Order Now button click listener
        binding.btnOrderNow.setOnClickListener {
            val intent = Intent(this, OrderActivity::class.java)
            intent.putExtra("username", updateName())
            startActivity(intent)
        }
        binding.notification.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        binding.btnCheckRequests.setOnClickListener {
            startActivity(Intent(this, RequestActivity::class.java))
        }

        // Initialize MapView
        initializeMap(savedInstanceState)
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync { mapLibreMap ->
            map = mapLibreMap

            // Disable logo (optional - removes MapLibre logo)
            mapLibreMap.uiSettings.isLogoEnabled = false

            // Move attribution to a less obtrusive position
            mapLibreMap.uiSettings.isAttributionEnabled = true
            mapLibreMap.uiSettings.attributionGravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            mapLibreMap.uiSettings.setAttributionMargins(0, 0, 8, 8)

            // Disable rotation gestures (optional - makes map easier to use)
            mapLibreMap.uiSettings.isRotateGesturesEnabled = false

            // Use OpenStreetMap style (free and open source)
            mapLibreMap.setStyle(
                Style.Builder()
                    .fromUri("https://demotiles.maplibre.org/style.json")
            ) {
                // Map is ready, set initial position (example: Delhi, India)
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