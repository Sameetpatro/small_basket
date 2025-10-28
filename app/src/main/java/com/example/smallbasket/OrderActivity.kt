package com.example.smallbasket

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
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
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupStatusBar()
        enableEdgeToEdge()

        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize vibrator for haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        setupBackButton()
        setupDeadlineButtons()
        setupListeners()
        setupScrollListener()

        // Restore state if coming back from confirmation screen
        restoreState()
    }

    private fun restoreState() {
        // Get data from Intent (if returning from OrderConfirmationActivity)
        val item = intent.getStringExtra("item")
        val pickup = intent.getStringExtra("pickup")
        val pickupArea = intent.getStringExtra("pickup_area")
        val drop = intent.getStringExtra("drop")
        val dropArea = intent.getStringExtra("drop_area")
        val itemPrice = intent.getDoubleExtra("item_price", 0.0)
        val deadline = intent.getStringExtra("deadline")
        val customMinutes = intent.getIntExtra("custom_deadline_minutes", -1)
        val priority = intent.getBooleanExtra("priority", false)
        val notes = intent.getStringExtra("notes")

        // Restore fields if data exists
        if (item != null) {
            binding.item.setText(item)
        }

        if (pickup != null) {
            binding.pickup.setText(pickup)
        }

        if (pickupArea != null) {
            val pickupAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.pickup_areas,
                android.R.layout.simple_spinner_item
            )
            val pickupPosition = pickupAdapter.getPosition(pickupArea)
            if (pickupPosition >= 0) {
                binding.pickupArea.setSelection(pickupPosition)
            }
        }

        if (drop != null) {
            binding.drop.setText(drop)
        }

        if (dropArea != null) {
            val dropAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.drop_areas,
                android.R.layout.simple_spinner_item
            )
            val dropPosition = dropAdapter.getPosition(dropArea)
            if (dropPosition >= 0) {
                binding.dropArea.setSelection(dropPosition)
            }
        }

        if (itemPrice > 0.0) {
            binding.fare.setText(itemPrice.toString())
        }

        if (deadline != null) {
            selectedDeadline = deadline
            if (deadline == "custom" && customMinutes != -1) {
                customDeadlineMinutes = customMinutes
                // Calculate and show the custom time
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MINUTE, customMinutes)
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(calendar.time)
                binding.tvCustomTime.visibility = View.VISIBLE
                binding.tvCustomTime.text = "Expires at $formattedTime"
            }
            updateDeadlineButton(deadline)
        }

        binding.priority.isChecked = priority

        if (notes != null) {
            binding.notes.setText(notes)
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
            // Medium Haptic for navigation action
            performMediumHaptic()
            finish()
        }
    }

    private fun setupDeadlineButtons() {
        updateDeadlineButton("30m")

        binding.deadline30m.setOnClickListener {
            // Light Haptic for selection toggle
            performLightHaptic()
            selectedDeadline = "30m"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("30m")
        }

        binding.deadline1h.setOnClickListener {
            performLightHaptic()
            selectedDeadline = "1h"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("1h")
        }

        binding.deadline2h.setOnClickListener {
            performLightHaptic()
            selectedDeadline = "2h"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("2h")
        }

        binding.deadline4h.setOnClickListener {
            performLightHaptic()
            selectedDeadline = "4h"
            customDeadlineMinutes = null
            binding.tvCustomTime.visibility = View.GONE
            updateDeadlineButton("4h")
        }

        binding.deadlineCustom.setOnClickListener {
            // Light Haptic when opening picker
            performLightHaptic()
            showCustomTimePicker()
        }

        // Priority toggle haptic
        binding.priority.setOnCheckedChangeListener { _, isChecked ->
            // Medium Haptic for important toggle (priority affects order)
            performMediumHaptic()
        }
    }

    private fun showCustomTimePicker() {
        val calendar = Calendar.getInstance()

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                // Light Haptic when time is selected
                performLightHaptic()

                val currentCalendar = Calendar.getInstance()
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (selectedCalendar.timeInMillis <= currentCalendar.timeInMillis) {
                    selectedCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                val diffInMillis = selectedCalendar.timeInMillis - currentCalendar.timeInMillis
                val diffInMinutes = (diffInMillis / (1000 * 60)).toInt()

                if (diffInMinutes < 10) {
                    // Medium Haptic for error feedback
                    performMediumHaptic()
                    Toast.makeText(
                        this,
                        "Please select a time at least 10 minutes from now",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@TimePickerDialog
                }

                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(selectedCalendar.time)

                binding.tvCustomTime.visibility = View.VISIBLE
                binding.tvCustomTime.text = "Expires at $formattedTime"

                selectedDeadline = "custom"
                customDeadlineMinutes = diffInMinutes

                updateDeadlineButton("custom")
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )

        timePickerDialog.setTitle("Select expiry time")
        timePickerDialog.show()
    }

    private fun updateDeadlineButton(selected: String) {
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
            val priceText = binding.fare.text.toString().trim()
            val priority = binding.priority.isChecked
            val notes = binding.notes.text.toString().trim()

            // Validation
            var hasError = false

            if (item.isEmpty()) {
                performMediumHaptic()
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

            // Validate price (REQUIRED now)
            if (priceText.isEmpty()) {
                binding.fareError.text = "Item price is required"
                binding.fareError.visibility = View.VISIBLE
                hasError = true
            }

            var itemPrice = 0.0
            if (priceText.isNotEmpty()) {
                try {
                    itemPrice = priceText.toDouble()
                    if (itemPrice <= 0) {
                        binding.fareError.text = "Please enter a valid price"
                        binding.fareError.visibility = View.VISIBLE
                        hasError = true
                    }
                } catch (e: NumberFormatException) {
                    binding.fareError.text = "Please enter a valid price"
                    binding.fareError.visibility = View.VISIBLE
                    hasError = true
                }
            }

            if (hasError) {
                // Medium Haptic for validation failure
                performMediumHaptic()
                return@setOnClickListener
            }

            // Medium Haptic for successful form submission
            performMediumHaptic()

            // Navigate to confirmation screen with all data
            val intent = Intent(this, OrderConfirmationActivity::class.java)
            intent.putExtra("username", auth.currentUser?.displayName ?: "User")
            intent.putExtra("item", item)
            intent.putExtra("pickup", pickup)
            intent.putExtra("pickup_area", pickupArea)
            intent.putExtra("drop", drop)
            intent.putExtra("drop_area", dropArea)
            intent.putExtra("item_price", itemPrice)
            intent.putExtra("deadline", selectedDeadline)
            intent.putExtra("custom_deadline_minutes", customDeadlineMinutes)
            intent.putExtra("priority", priority)
            intent.putExtra("notes", notes)
            startActivity(intent)
            finish()
        }
    }

    // ==================== HAPTIC FEEDBACK METHODS ====================

    /**
     * Light Haptic: 10ms duration, 40% amplitude
     * Used for: Deadline button selections, time picker interactions
     */
    private fun performLightHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                10,  // 10ms duration
                102  // ~40% amplitude (40% of 255 = 102)
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /**
     * Medium Haptic: 15ms duration, 80% amplitude
     * Used for: Back button, priority toggle, form submission, errors
     */
    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                15,  // 15ms duration
                204  // ~80% amplitude (80% of 255 = 204)
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }
}