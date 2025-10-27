package com.example.smallbasket

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityOrderBinding
import com.example.smallbasket.models.CreateOrderRequest
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class OrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderBinding
    private val repository = OrderRepository()
    private val auth = FirebaseAuth.getInstance()
    private var selectedDeadline = "30m"
    private var customDeadlineMinutes: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup transparent status bar BEFORE enableEdgeToEdge
        setupStatusBar()

        enableEdgeToEdge()

        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackButton()
        setupDeadlineButtons()
        setupListeners()

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

    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            // Change status bar appearance based on scroll position
            // When scrolled past the header (e.g., 140px - height of header), change to light icons
            if (scrollY > 140) {
                // Scrolled down - use dark icons for light background
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
                // At top - use white icons for teal background
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
            finish()
        }
    }

    private fun setupDeadlineButtons() {
        // Set default selection
        updateDeadlineButton("30m")

        binding.deadline30m.setOnClickListener {
            selectedDeadline = "30m"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("30m")
        }

        binding.deadline1h.setOnClickListener {
            selectedDeadline = "1h"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("1h")
        }

        binding.deadline2h.setOnClickListener {
            selectedDeadline = "2h"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("2h")
        }

        binding.deadline4h.setOnClickListener {
            selectedDeadline = "4h"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("4h")
        }

        binding.deadlineCustom.setOnClickListener {
            showCustomTimePicker()
        }
    }

    private fun showCustomTimePicker() {
        val calendar = Calendar.getInstance()

        // Create TimePickerDialog
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                // Calculate time difference
                val currentCalendar = Calendar.getInstance()
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If selected time is before current time, assume next day
                if (selectedCalendar.timeInMillis <= currentCalendar.timeInMillis) {
                    selectedCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                // Calculate difference in minutes
                val diffInMillis = selectedCalendar.timeInMillis - currentCalendar.timeInMillis
                val diffInMinutes = (diffInMillis / (1000 * 60)).toInt()

                // Validate minimum time (at least 10 minutes)
                if (diffInMinutes < 10) {
                    Toast.makeText(
                        this,
                        "Please select a time at least 10 minutes from now",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@TimePickerDialog
                }

                // Format the time
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(selectedCalendar.time)

                // Update custom time display
                binding.tvCustomTime.visibility = View.VISIBLE
                binding.tvCustomTime.text = "Expires at $formattedTime"

                // Store the deadline
                selectedDeadline = "custom"
                customDeadlineMinutes = diffInMinutes

                // Update button states
                updateDeadlineButton("custom")
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false // Use 12-hour format
        )

        timePickerDialog.setTitle("Select expiry time")
        timePickerDialog.show()
    }

    private fun updateDeadlineButton(selected: String) {
        // Reset all buttons to outline style
        binding.deadline30m.setBackgroundResource(R.drawable.bg_button_outline)
        binding.deadline30m.setTextColor(resources.getColor(android.R.color.black, null))

        binding.deadline1h.setBackgroundResource(R.drawable.bg_button_outline)
        binding.deadline1h.setTextColor(resources.getColor(android.R.color.black, null))

        binding.deadline2h.setBackgroundResource(R.drawable.bg_button_outline)
        binding.deadline2h.setTextColor(resources.getColor(android.R.color.black, null))

        binding.deadline4h.setBackgroundResource(R.drawable.bg_button_outline)
        binding.deadline4h.setTextColor(resources.getColor(android.R.color.black, null))

        binding.deadlineCustom.setBackgroundResource(R.drawable.bg_button_outline)
        binding.deadlineCustom.setTextColor(resources.getColor(android.R.color.black, null))

        // Set selected button to teal style
        when (selected) {
            "30m" -> {
                binding.deadline30m.setBackgroundResource(R.drawable.bg_button_teal)
                binding.deadline30m.setTextColor(resources.getColor(android.R.color.white, null))
            }
            "1h" -> {
                binding.deadline1h.setBackgroundResource(R.drawable.bg_button_teal)
                binding.deadline1h.setTextColor(resources.getColor(android.R.color.white, null))
            }
            "2h" -> {
                binding.deadline2h.setBackgroundResource(R.drawable.bg_button_teal)
                binding.deadline2h.setTextColor(resources.getColor(android.R.color.white, null))
            }
            "4h" -> {
                binding.deadline4h.setBackgroundResource(R.drawable.bg_button_teal)
                binding.deadline4h.setTextColor(resources.getColor(android.R.color.white, null))
            }
            "custom" -> {
                binding.deadlineCustom.setBackgroundResource(R.drawable.bg_button_teal)
                binding.deadlineCustom.setTextColor(resources.getColor(android.R.color.white, null))
            }
        }
    }

    private fun setupListeners() {
        binding.btnPlaceNow.setOnClickListener {
            // Clear previous errors
            binding.pickupError.visibility = View.GONE
            binding.dropError.visibility = View.GONE
            binding.fareError.visibility = View.GONE

            // Get form values
            val item = binding.item.text.toString().trim()
            val pickup = binding.pickup.text.toString().trim()
            val pickupArea = binding.pickupArea.selectedItem?.toString() ?: ""
            val drop = binding.drop.text.toString().trim()
            val dropArea = binding.dropArea.selectedItem?.toString() ?: ""
            val fareText = binding.fare.text.toString().trim()
            val priority = binding.priority.isChecked
            val notes = binding.notes.text.toString().trim()

            // Validation
            var hasError = false

            if (item.isEmpty()) {
                Toast.makeText(this, "Item name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pickup.isEmpty()) {
                binding.pickupError.text = "Pickup location is required"
                binding.pickupError.visibility = View.VISIBLE
                hasError = true
            }

            if (drop.isEmpty()) {
                binding.dropError.text = "Drop location is required"
                binding.dropError.visibility = View.VISIBLE
                hasError = true
            }

            // Validate fare if entered
            var fare = 0.0
            if (fareText.isNotEmpty()) {
                try {
                    fare = fareText.toDouble()
                    if (fare < 0) {
                        binding.fareError.text = "Please enter a valid amount"
                        binding.fareError.visibility = View.VISIBLE
                        hasError = true
                    }
                } catch (e: NumberFormatException) {
                    binding.fareError.text = "Please enter a valid amount"
                    binding.fareError.visibility = View.VISIBLE
                    hasError = true
                }
            }

            if (hasError) {
                return@setOnClickListener
            }

            // Convert deadline to ISO 8601
            val deadline = convertDeadlineToISO8601(selectedDeadline, customDeadlineMinutes)
            val timeRequested = deadline // Using same time for both

            // Create order request with single item
            val orderRequest = CreateOrderRequest(
                item = listOf(item),
                pickupLocation = pickup,
                pickupArea = pickupArea,
                dropLocation = drop,
                dropArea = dropArea,
                reward = fare,
                timeRequested = timeRequested,
                deadline = deadline,
                priority = priority,
                notes = notes.ifEmpty { null }
            )

            createOrder(orderRequest, listOf(item))
        }
    }

    private fun convertDeadlineToISO8601(deadline: String, customMinutes: Int?): String {
        val calendar = Calendar.getInstance()

        when (deadline) {
            "30m" -> calendar.add(Calendar.MINUTE, 30)
            "1h" -> calendar.add(Calendar.HOUR_OF_DAY, 1)
            "2h" -> calendar.add(Calendar.HOUR_OF_DAY, 2)
            "4h" -> calendar.add(Calendar.HOUR_OF_DAY, 4)
            "custom" -> {
                if (customMinutes != null) {
                    calendar.add(Calendar.MINUTE, customMinutes)
                } else {
                    calendar.add(Calendar.MINUTE, 30) // Default fallback
                }
            }
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }

    private fun createOrder(request: CreateOrderRequest, itemList: List<String>) {
        binding.btnPlaceNow.isEnabled = false
        binding.btnPlaceNow.text = "Posting..."

        lifecycleScope.launch {
            val result = repository.createOrder(request)

            result.onSuccess { order ->
                Toast.makeText(
                    this@OrderActivity,
                    "Order created successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                val username = intent.getStringExtra("username")
                val intent = Intent(this@OrderActivity, OrderConfirmationActivity::class.java)
                intent.putExtra("username", username)
                intent.putExtra("order_items", itemList.joinToString(","))
                intent.putExtra("order_id", order.id)
                startActivity(intent)
                finish()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@OrderActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnPlaceNow.isEnabled = true
                binding.btnPlaceNow.text = "Post Request"
            }
        }
    }
}