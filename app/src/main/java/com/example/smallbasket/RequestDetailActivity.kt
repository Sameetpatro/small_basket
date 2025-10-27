package com.example.smallbasket

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityRequestDetailsBinding
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestDetailsBinding
    private val repository = OrderRepository()
    private val auth = FirebaseAuth.getInstance()
    private var orderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup transparent status bar BEFORE enableEdgeToEdge
        setupStatusBar()

        enableEdgeToEdge()

        binding = ActivityRequestDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from intent
        orderId = intent.getStringExtra("order_id") ?: ""
        val title = intent.getStringExtra("title")
        val pickup = intent.getStringExtra("pickup")
        val drop = intent.getStringExtra("drop")
        val details = intent.getStringExtra("details")
        val priority = intent.getStringExtra("priority") ?: "normal"
        val bestBefore = intent.getStringExtra("best_before") ?: ""
        val deadline = intent.getStringExtra("deadline") ?: ""
        val rewardPercentage = intent.getDoubleExtra("reward_percentage", 10.0)
        val isImportant = intent.getBooleanExtra("isImportant", false)
        val pickupArea = intent.getStringExtra("pickup_area") ?: ""
        val dropArea = intent.getStringExtra("drop_area") ?: ""
        val fare = intent.getDoubleExtra("fare", 0.0)

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

        // Set best before time
        binding.tvBestBefore.text = bestBefore.ifEmpty { "Not specified" }

        // Set reward/fare
        binding.tvReward.text = if (fare > 0) {
            "₹${fare.toInt()}"
        } else {
            "₹${(rewardPercentage * 10).toInt()}"
        }

        // Set notes
        if (!details.isNullOrEmpty()) {
            binding.tvNotes.text = details
            binding.cardNotes.visibility = View.VISIBLE
        } else {
            binding.cardNotes.visibility = View.GONE
        }

        // Set posted time (calculate from timestamp if available, otherwise show "Just now")
        binding.tvPostedTime.text = "Posted 15 minutes ago" // You can calculate actual time difference

        // Handle priority badge
        if (isImportant || priority.equals("high", ignoreCase = true)) {
            binding.layoutPriority.visibility = View.VISIBLE
            binding.tvPriority.text = "Priority"
        } else {
            binding.layoutPriority.visibility = View.GONE
        }

        // Keep old fields populated for backward compatibility
        binding.tvSpecificPickup.text = pickup
        binding.tvSpecificDrop.text = drop
        binding.tvDeadline.text = deadline

        if (isImportant) {
            binding.tvImportance.text = "⚠️ Marked as Emergency"
        } else {
            binding.tvImportance.text = "Normal Request"
        }

        setupBackButton()
        setupAcceptButton()
        setupScrollListener()

        // Back home button (legacy)
        binding.btnBackHome.setOnClickListener {
            finish()
        }
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

    private fun setupAcceptButton() {
        // Get the actual button inside the CardView and set click listener
        val innerButton = binding.root.findViewById<android.widget.Button>(R.id.btnAcceptRequestInner)
        innerButton?.setOnClickListener {
            showPriceDialog()
        }
    }

    private fun showPriceDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val editText = EditText(this).apply {
            hint = "Enter estimated price"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Accept Order")
            .setMessage("Enter the estimated price for this order:")
            .setView(editText)
            .setPositiveButton("Accept") { _, _ ->
                val priceText = editText.text.toString()
                if (priceText.isNotEmpty()) {
                    val price = priceText.toDoubleOrNull()
                    if (price != null && price > 0) {
                        acceptOrder(price)
                    } else {
                        Toast.makeText(this, "Please enter a valid price", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Price cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun acceptOrder(estimatedPrice: Double) {
        val delivererId = auth.currentUser?.uid ?: ""

        // Get reference to the actual button inside the CardView
        val innerButton = binding.root.findViewById<android.widget.Button>(R.id.btnAcceptRequestInner)
        innerButton?.isEnabled = false
        innerButton?.text = "Accepting..."

        lifecycleScope.launch {
            val result = repository.acceptOrder(orderId, delivererId, estimatedPrice)

            result.onSuccess { order ->
                Toast.makeText(
                    this@RequestDetailActivity,
                    "Order accepted successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this@RequestDetailActivity, DeliveryConfimationActivity::class.java)
                intent.putExtra("order_id", order.id)
                intent.putExtra("title", binding.tvItemTitle.text.toString())
                startActivity(intent)
                finish()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@RequestDetailActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                val innerButton = binding.root.findViewById<android.widget.Button>(R.id.btnAcceptRequestInner)
                innerButton?.isEnabled = true
                innerButton?.text = "Accept Delivery"
            }
        }
    }
}