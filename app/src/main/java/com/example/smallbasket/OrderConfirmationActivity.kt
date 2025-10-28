package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityOrderConfirmationBinding
import com.example.smallbasket.models.CreateOrderRequest
import com.example.smallbasket.repository.OrderRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class OrderConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderConfirmationBinding
    private val repository = OrderRepository()

    // Order data
    private lateinit var item: String
    private lateinit var pickup: String
    private lateinit var pickupArea: String
    private lateinit var drop: String
    private lateinit var dropArea: String
    private var itemPrice: Double = 0.0
    private lateinit var deadline: String
    private var customDeadlineMinutes: Int? = null
    private var priority: Boolean = false
    private var notes: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from Intent
        val username = intent.getStringExtra("username") ?: "User"
        item = intent.getStringExtra("item") ?: ""
        pickup = intent.getStringExtra("pickup") ?: ""
        pickupArea = intent.getStringExtra("pickup_area") ?: ""
        drop = intent.getStringExtra("drop") ?: ""
        dropArea = intent.getStringExtra("drop_area") ?: ""
        itemPrice = intent.getDoubleExtra("item_price", 0.0)
        deadline = intent.getStringExtra("deadline") ?: "30m"
        customDeadlineMinutes = intent.getIntExtra("custom_deadline_minutes", -1).let {
            if (it == -1) null else it
        }
        priority = intent.getBooleanExtra("priority", false)
        notes = intent.getStringExtra("notes")

        // Thank you message
        binding.tvThankYou.text = "Review Your Order, $username"

        // Show initial order summary
        displayOrderSummary()

        // Setup listeners
        setupListeners()
    }

    private fun displayOrderSummary() {
        // Item and locations
        binding.tvSampleItem.text = "$item"
        binding.tvOrderPickup.text = "Pickup: $pickup ($pickupArea)"
        binding.tvOrderDrop.text = "Drop: $drop ($dropArea)"

        // Show base price before backend reward is fetched
        binding.tvItemPrice.text = "Item Price: ₹${String.format("%.2f", itemPrice)}"
        binding.tvDeliveryFee.text = "Delivery Fee: Calculating..."
        binding.tvTotalAmount.text = "Total Amount: Calculating..."

        // Priority
        if (priority) {
            binding.tvPriority.text = "Priority: High"
            binding.tvPriority.visibility = View.VISIBLE
            binding.labelPriority.visibility = View.VISIBLE
            binding.layoutPriority.visibility = View.VISIBLE
        } else {
            binding.tvPriority.visibility = View.GONE
            binding.labelPriority.visibility = View.GONE
            binding.layoutPriority.visibility = View.GONE
        }

        // Notes
        if (!notes.isNullOrEmpty()) {
            binding.tvNotes.text = "$notes"
            binding.tvNotes.visibility = View.VISIBLE
            binding.labelNotes.visibility = View.VISIBLE
            binding.layoutNotes.visibility = View.VISIBLE
        } else {
            binding.tvNotes.visibility = View.GONE
            binding.labelNotes.visibility = View.GONE
            binding.layoutNotes.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        // Back arrow button - goes back to OrderActivity with data
        binding.btnBack.setOnClickListener {
            navigateBackToEdit()
        }

        // Confirm Order button
        binding.btnConfirmOrder.setOnClickListener {
            confirmAndCreateOrder()
        }

        // Back to Edit button
        binding.btnBackToEdit.setOnClickListener {
            navigateBackToEdit()
        }
    }

    private fun navigateBackToEdit() {
        // Navigate back to OrderActivity with all the data preserved
        val intent = Intent(this, OrderActivity::class.java).apply {
            putExtra("item", item)
            putExtra("pickup", pickup)
            putExtra("pickup_area", pickupArea)
            putExtra("drop", drop)
            putExtra("drop_area", dropArea)
            putExtra("item_price", itemPrice)
            putExtra("deadline", deadline)
            putExtra("custom_deadline_minutes", customDeadlineMinutes)
            putExtra("priority", priority)
            putExtra("notes", notes)
        }
        startActivity(intent)
        finish()
    }

    private fun confirmAndCreateOrder() {
        binding.btnConfirmOrder.isEnabled = false
        binding.btnConfirmOrder.text = "Creating Order..."

        // Convert deadline to ISO 8601
        val deadlineISO = convertDeadlineToISO8601(deadline, customDeadlineMinutes)
        val timeRequestedISO = deadlineISO // Using same for now

        // Create request
        val orderRequest = CreateOrderRequest(
            item = listOf(item),
            pickupLocation = pickup,
            pickupArea = pickupArea,
            dropLocation = drop,
            dropArea = dropArea,
            itemPrice = itemPrice,
            timeRequested = timeRequestedISO,
            deadline = deadlineISO,
            priority = priority,
            notes = notes
        )

        createOrder(orderRequest)
    }

    private fun convertDeadlineToISO8601(deadline: String, customMinutes: Int?): String {
        val calendar = Calendar.getInstance()
        when (deadline) {
            "30m" -> calendar.add(Calendar.MINUTE, 30)
            "1h" -> calendar.add(Calendar.HOUR_OF_DAY, 1)
            "2h" -> calendar.add(Calendar.HOUR_OF_DAY, 2)
            "4h" -> calendar.add(Calendar.HOUR_OF_DAY, 4)
            "custom" -> calendar.add(Calendar.MINUTE, customMinutes ?: 30)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }

    private fun createOrder(request: CreateOrderRequest) {
        lifecycleScope.launch {
            val result = repository.createOrder(request)

            result.onSuccess { order ->
                Toast.makeText(
                    this@OrderConfirmationActivity,
                    "Order created successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                // ✅ Update UI with backend reward
                val reward = order.reward
                val totalAmount = order.itemPrice + reward

                binding.tvDeliveryFee.text =
                    "Delivery Fee: ₹${String.format("%.2f", reward)}"
                binding.tvTotalAmount.text =
                    "Total Amount: ₹${String.format("%.2f", totalAmount)}"

                // Navigate to Homepage
                val intent = Intent(this@OrderConfirmationActivity, Homepage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@OrderConfirmationActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnConfirmOrder.isEnabled = true
                binding.btnConfirmOrder.text = "Confirm Order"
            }
        }
    }
}