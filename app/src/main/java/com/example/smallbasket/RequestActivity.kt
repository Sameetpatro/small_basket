package com.example.smallbasket

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.smallbasket.repository.OrderRepository
import com.example.smallbasket.models.Order
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException

class RequestActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var skeletonLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var btnBack: ImageView

    private val repository = OrderRepository()
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make status bar transparent and blend with the gradient
        window.apply {
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            statusBarColor = android.graphics.Color.TRANSPARENT
        }

        setContentView(R.layout.activity_request)

        // Initialize vibrator for haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        initializeViews()
        setupListeners()
        loadAvailableOrders(showSkeleton = true)
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerRequests)
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progress_bar)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        skeletonLayout = findViewById(R.id.skeletonLayout)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        btnBack = findViewById(R.id.btnBack)

        // Configure SwipeRefreshLayout colors
        swipeRefreshLayout.setColorSchemeColors(
            getColor(R.color.teal_500),
            getColor(R.color.teal_600),
            getColor(R.color.teal_700)
        )
    }

    private fun setupListeners() {
        // Back button click listener with Medium Haptic (secondary navigation)
        btnBack.setOnClickListener {
            performMediumHaptic()
            onBackPressed()
        }

        // Pull-to-refresh listener with Light Haptic
        swipeRefreshLayout.setOnRefreshListener {
            performLightHaptic()
            loadAvailableOrders(showSkeleton = false)
        }
    }

    private fun loadAvailableOrders(showSkeleton: Boolean = false) {
        if (showSkeleton) {
            // Show skeleton on initial load
            skeletonLayout.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            emptyStateLayout.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            // Don't show skeleton on refresh, just the refresh indicator
            skeletonLayout.visibility = View.GONE
        }

        lifecycleScope.launch {
            val result = repository.getAllOrders(status = "open")

            result.onSuccess { orders ->
                // Hide all loading indicators
                skeletonLayout.visibility = View.GONE
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false

                if (orders.isEmpty()) {
                    emptyStateLayout.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    Toast.makeText(
                        this@RequestActivity,
                        getString(R.string.empty_no_requests),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    emptyStateLayout.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    setupRecyclerView(orders)
                }
            }

            result.onFailure { error ->
                // Hide all loading indicators
                skeletonLayout.visibility = View.GONE
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                emptyStateLayout.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE

                val messageId = when {
                    // Network-related errors
                    error is UnknownHostException || error is IOException -> {
                        R.string.error_no_internet
                    }

                    // Firebase-specific errors
                    error is FirebaseFirestoreException -> {
                        when (error.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                R.string.error_permission_denied
                            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                                R.string.error_timeout
                            FirebaseFirestoreException.Code.UNAVAILABLE ->
                                R.string.error_service_unavailable
                            else ->
                                R.string.error_service_unavailable
                        }
                    }

                    // Session or auth-related issues (heuristic-based)
                    error.message?.contains("auth", ignoreCase = true) == true ||
                            error.message?.contains("session", ignoreCase = true) == true ||
                            error.message?.contains("expired", ignoreCase = true) == true -> {
                        R.string.error_session_expired
                    }

                    // Fallback for any other unexpected error
                    else -> R.string.error_generic
                }

                Toast.makeText(this@RequestActivity, getString(messageId), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView(orders: List<Order>) {
        val requests = orders.map { order ->
            DeliveryRequest(
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
        }

        recyclerView.adapter = DeliveryCardAdapter(requests) { request ->
            performMediumHaptic()
            navigateToDetail(request, orders)
        }
    }

    private fun extractLocation(location: String?, area: String?): String {
        return when {
            location.isNullOrBlank() && area.isNullOrBlank() -> "Unknown"
            location.isNullOrBlank() -> area!!
            area.isNullOrBlank() -> location
            else -> location
        }
    }

    private fun extractRewardPercentage(order: Order): Int {
        return try {
            order.reward?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun formatFee(order: Order): String {
        val reward = extractRewardPercentage(order)
        return if (reward > 0) {
            "₹$reward"
        } else {
            "₹0"
        }
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

    private fun navigateToDetail(request: DeliveryRequest, orders: List<Order>) {
        // Find the corresponding order to get acceptor info
        val order = orders.find { it.id == request.orderId }

        val intent = Intent(this, RequestDetailActivity::class.java).apply {
            putExtra("order_id", request.orderId)
            putExtra("title", request.title)
            putExtra("pickup", request.pickup)
            putExtra("drop", request.dropoff)
            putExtra("details", request.details)
            putExtra("priority", if (request.priority) "emergency" else "normal")
            putExtra("best_before", request.bestBefore)
            putExtra("deadline", request.deadline)
            putExtra("reward_percentage", request.rewardPercentage?.toDouble() ?: 0.0)
            putExtra("isImportant", request.priority)
            putExtra("fee", request.fee)
            putExtra("time", request.time)

            // ✅ ADD ALL ORDER DATA
            order?.let {
                putExtra("pickup_area", it.pickupArea)
                putExtra("drop_area", it.dropArea)
                putExtra("item_price", it.itemPrice)
                putExtra("status", it.status)
                // ✅ ADD ACCEPTOR INFO
                putExtra("acceptor_email", it.acceptorEmail)
                putExtra("acceptor_name", it.acceptorName)  // ✅ NEW
                putExtra("acceptor_phone", it.acceptorPhone)  // ✅ NEW
            }
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Refresh orders when returning to this activity
        loadAvailableOrders(showSkeleton = false)
    }

    // ==================== HAPTIC FEEDBACK METHODS ====================

    /**
     * Light Haptic: 10ms duration, 40% amplitude (~102 on 0–255 scale)
     * Used for: Pull-to-refresh
     */
    private fun performLightHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(10, 102)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /**
     * Medium Haptic: 15ms duration, 80% amplitude (~204 on 0–255 scale)
     * Used for: Button taps, card taps, back navigation
     */
    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(15, 204)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }

    // Public wrappers (in case needed externally)
    fun triggerLightHaptic() = performLightHaptic()
    fun triggerMediumHaptic() = performMediumHaptic()
}