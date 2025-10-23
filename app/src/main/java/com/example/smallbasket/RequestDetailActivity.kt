package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityRequestDetailsBinding
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestDetailsBinding
    private val repository = OrderRepository()
    private val auth = FirebaseAuth.getInstance()
    private var orderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.tvItemTitle.text = title ?: "Unknown Item"
        binding.tvPickup.text = "Pickup: $pickup"
        binding.tvDrop.text = "Drop: $drop"
        binding.tvNotes.text = details ?: "No extra details provided"
        binding.tvBestBefore.text = "Best Before: $bestBefore"
        binding.tvDeadline.text = "Deadline: $deadline"
        binding.tvReward.text = "Reward: ${rewardPercentage}%"

        if (isImportant) {
            binding.tvImportance.text = "⚠️ Marked as Emergency"
            binding.tvImportance.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.tvPriority.text = "Emergency"
            binding.tvPriority.setBackgroundColor(getColor(android.R.color.holo_red_light))
        } else {
            binding.tvImportance.text = "Normal Request"
            binding.tvImportance.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.tvPriority.text = "Normal"
        }

        binding.btnBackHome.setOnClickListener {
            finish()
        }

        binding.btnAcceptRequest.setOnClickListener {
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

        binding.btnAcceptRequest.isEnabled = false
        binding.btnAcceptRequest.text = "Accepting..."

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
                binding.btnAcceptRequest.isEnabled = true
                binding.btnAcceptRequest.text = "Accept Request"
            }
        }
    }
}