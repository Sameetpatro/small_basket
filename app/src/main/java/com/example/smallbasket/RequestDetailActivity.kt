package com.example.smallbasket

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityRequestDetailsBinding
import com.example.smallbasket.repository.OrderRepository
import com.example.smallbasket.utils.TimeUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestDetailsBinding
    private val repository = OrderRepository()
    private val auth = FirebaseAuth.getInstance()
    private var orderId: String = ""
    private var orderStatus: String = "open"
    private var acceptorEmail: String? = null
    private var acceptorName: String? = null
    private var acceptorPhone: String? = null  // ✅ NEW FIELD
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupStatusBar()
        enableEdgeToEdge()

        binding = ActivityRequestDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeHaptics()

        // Get data from intent
        orderId = intent.getStringExtra("order_id") ?: ""
        val title = intent.getStringExtra("title")
        val pickup = intent.getStringExtra("pickup")
        val pickupArea = intent.getStringExtra("pickup_area") ?: ""
        val drop = intent.getStringExtra("drop")
        val dropArea = intent.getStringExtra("drop_area") ?: ""
        val details = intent.getStringExtra("details")
        val priority = intent.getStringExtra("priority") ?: "normal"
        val bestBefore = intent.getStringExtra("best_before") ?: ""
        val deadline = intent.getStringExtra("deadline") ?: ""
        val rewardPercentage = intent.getDoubleExtra("reward_percentage", 10.0)
        val isImportant = intent.getBooleanExtra("isImportant", false)
        val itemPrice = intent.getDoubleExtra("item_price", 0.0)
        orderStatus = intent.getStringExtra("status") ?: "open"
        acceptorEmail = intent.getStringExtra("acceptor_email")
        acceptorName = intent.getStringExtra("acceptor_name")  // ✅ GET FROM INTENT
        acceptorPhone = intent.getStringExtra("acceptor_phone")  // ✅ GET FROM INTENT

        // Set basic data
        binding.tvItemTitle.text = title ?: "Unknown Item"

        // Set pickup with area
        binding.tvPickup.text = if (pickupArea.isNotEmpty()) {
            "$pickup ($pickupArea)"
        } else {
            pickup ?: "Pickup location"
        }

        // Set drop with area
        binding.tvDrop.text = if (dropArea.isNotEmpty()) {
            "$drop ($dropArea)"
        } else {
            drop ?: "Drop location"
        }

        // Set best before time with TimeUtils
        binding.tvBestBefore.text = if (bestBefore.isNotEmpty()) {
            TimeUtils.getTimeRemaining(bestBefore)
        } else {
            "Not specified"
        }

        // Display both item price and reward
        if (itemPrice > 0) {
            binding.layoutItemPrice.visibility = View.VISIBLE
            binding.tvItemPrice.text = "₹${itemPrice.toInt()}"
        } else {
            binding.layoutItemPrice.visibility = View.GONE
        }
        binding.tvReward.text = "₹${rewardPercentage.toInt()}"

        // Set notes
        if (!details.isNullOrEmpty()) {
            binding.tvNotes.text = details
            binding.cardNotes.visibility = View.VISIBLE
        } else {
            binding.cardNotes.visibility = View.GONE
        }

        // Handle priority badge
        if (isImportant || priority.equals("high", ignoreCase = true) || priority.equals("emergency", ignoreCase = true)) {
            binding.layoutPriority.visibility = View.VISIBLE
            binding.tvPriority.text = "Priority"
        } else {
            binding.layoutPriority.visibility = View.GONE
        }

        configureAcceptButton()

        // If order is accepted/completed, fetch and show acceptor info
        if (orderStatus == "accepted" || orderStatus == "completed") {
            loadAndDisplayAcceptorInfo()
        }

        setupBackButton()
        setupAcceptButton()
        setupScrollListener()

        binding.btnBackHome.setOnClickListener {
            performLightHaptic(it)
            finish()
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

    private fun performLightHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(10, 40)
            )
        }
    }

    private fun performMediumHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(15, 80)
            )
        }
    }

    private fun performSuccessHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 50, 100)
            val amplitudes = intArrayOf(0, 80, 0, 120)
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        }
    }

    // ===== END HAPTIC SYSTEM =====

    private fun configureAcceptButton() {
        if (orderStatus == "open") {
            binding.btnAcceptRequest.visibility = View.VISIBLE
        } else {
            binding.btnAcceptRequest.visibility = View.GONE
        }
    }

    private fun loadAndDisplayAcceptorInfo() {
        lifecycleScope.launch {
            try {
                val result = repository.getOrder(orderId)

                result.onSuccess { order ->
                    // Update all acceptor fields from API
                    acceptorEmail = order.acceptorEmail
                    acceptorName = order.acceptorName  // ✅ GET FROM API
                    acceptorPhone = order.acceptorPhone  // ✅ GET FROM API

                    if (!acceptorEmail.isNullOrEmpty()) {
                        displayAcceptorCard(
                            email = acceptorEmail!!,
                            name = acceptorName,
                            phone = acceptorPhone,
                            status = order.status
                        )
                    }
                }

                result.onFailure { error ->
                    // Fallback to intent data
                    if (!acceptorEmail.isNullOrEmpty()) {
                        displayAcceptorCard(
                            email = acceptorEmail!!,
                            name = acceptorName,
                            phone = acceptorPhone,
                            status = orderStatus
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to intent data
                if (!acceptorEmail.isNullOrEmpty()) {
                    displayAcceptorCard(
                        email = acceptorEmail!!,
                        name = acceptorName,
                        phone = acceptorPhone,
                        status = orderStatus
                    )
                }
            }
        }
    }

    private fun displayAcceptorCard(
        email: String,
        name: String?,
        phone: String?,
        status: String
    ) {
        val scrollViewContent = binding.scrollView.getChildAt(0) as? LinearLayout
        if (scrollViewContent == null) return

        // Remove existing card if present
        val existingCard = scrollViewContent.findViewWithTag<View>("acceptor_card")
        if (existingCard != null) {
            scrollViewContent.removeView(existingCard)
        }

        // Inflate acceptor info card
        val acceptorCard = layoutInflater.inflate(
            R.layout.card_acceptor_info,
            scrollViewContent,
            false
        )

        acceptorCard.tag = "acceptor_card"

        // Set acceptor name (or fallback to email if name is null)
        acceptorCard.findViewById<android.widget.TextView>(R.id.tvAcceptorName)?.text =
            name ?: "Name not available"

        // Set acceptor email
        acceptorCard.findViewById<android.widget.TextView>(R.id.tvAcceptorEmail)?.text = email

        // ✅ Set acceptor phone
        val phoneLayout = acceptorCard.findViewById<LinearLayout>(R.id.layoutAcceptorPhone)
        val phoneTextView = acceptorCard.findViewById<android.widget.TextView>(R.id.tvAcceptorPhone)

        if (!phone.isNullOrEmpty()) {
            phoneLayout?.visibility = View.VISIBLE
            phoneTextView?.text = phone
        } else {
            phoneLayout?.visibility = View.GONE
        }

        // Set status badge
        val statusText = when (status) {
            "accepted" -> "Delivery in Progress"
            "completed" -> "Delivered"
            else -> "Accepted"
        }

        val statusColor = when (status) {
            "accepted" -> android.R.color.holo_blue_dark
            "completed" -> android.R.color.holo_green_dark
            else -> android.R.color.holo_orange_dark
        }

        acceptorCard.findViewById<android.widget.TextView>(R.id.tvAcceptorStatus)?.apply {
            text = statusText
            setTextColor(resources.getColor(statusColor, null))
        }

        // Add card to layout
        val cardNotesIndex = scrollViewContent.indexOfChild(binding.cardNotes)
        if (cardNotesIndex >= 0) {
            scrollViewContent.addView(acceptorCard, cardNotesIndex + 1)
        } else {
            val hiddenFieldsIndex = scrollViewContent.indexOfChild(binding.tvDeadline)
            if (hiddenFieldsIndex >= 0) {
                scrollViewContent.addView(acceptorCard, hiddenFieldsIndex)
            } else {
                scrollViewContent.addView(acceptorCard)
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

    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > 140) {
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

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            performLightHaptic(it)
            finish()
        }
    }

    private fun setupAcceptButton() {
        val innerButton = binding.root.findViewById<android.widget.Button>(R.id.btnAcceptRequestInner)
        innerButton?.setOnClickListener {
            performMediumHaptic(it)
            showConfirmationDialog()
        }
    }

    private fun showConfirmationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Accept Delivery")
            .setMessage("Are you sure you want to accept this delivery request?")
            .setPositiveButton("Yes, Accept") { _, _ ->
                acceptOrder()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            performMediumHaptic(it)
            acceptOrder()
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            performLightHaptic(it)
            dialog.dismiss()
        }
    }

    private fun acceptOrder() {
        val innerButton = binding.root.findViewById<android.widget.Button>(R.id.btnAcceptRequestInner)
        innerButton?.isEnabled = false
        innerButton?.text = "Accepting..."

        lifecycleScope.launch {
            val result = repository.acceptOrder(orderId, auth.currentUser?.uid ?: "", 0.0)

            result.onSuccess { order ->
                performSuccessHaptic()

                // Update UI with acceptor info
                orderStatus = order.status
                acceptorEmail = order.acceptorEmail
                acceptorName = order.acceptorName ?: auth.currentUser?.displayName
                acceptorPhone = order.acceptorPhone  // ✅ GET FROM ORDER

                // Hide accept button
                binding.btnAcceptRequest.visibility = View.GONE

                // Show acceptor card
                if (!acceptorEmail.isNullOrEmpty()) {
                    displayAcceptorCard(
                        email = acceptorEmail!!,
                        name = acceptorName,
                        phone = acceptorPhone,
                        status = orderStatus
                    )
                }

                showOrderAcceptedDialog()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@RequestDetailActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                innerButton?.isEnabled = true
                innerButton?.text = "Accept Delivery"
            }
        }
    }

    private fun showOrderAcceptedDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("✅ Order Confirmed")
            .setMessage("Your order has been successfully accepted!\n\nYou can mark it as delivered later from the 'My Deliveries' section.")
            .setPositiveButton("OK") { _, _ ->
                // Do nothing, stay on this page to see acceptor info
            }
            .setCancelable(false)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            performLightHaptic(it)
            dialog.dismiss()
        }
    }
}