package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smallbasket.databinding.ActivityOrderConfirmationBinding

class OrderConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderConfirmationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from Intent
        val username = intent.getStringExtra("username") ?: "User"
        val itemsString = intent.getStringExtra("order_items") ?: ""
        val itemsList = itemsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // Set thank you message
        binding.tvThankYou.text = "Thank you for your order, $username!"

        // Populate items dynamically
        addOrderItems(itemsList)

        // Initially hide Return button
        binding.btnReturnHome.visibility = View.GONE

        // Button click logic
        binding.btnSubmitPrice.setOnClickListener {
            val enteredPriceText = binding.etExpectedPrice.text.toString().trim()
            if (enteredPriceText.isEmpty()) {
                Toast.makeText(this, "Please enter the total price!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredPrice = enteredPriceText.toDoubleOrNull()
            if (enteredPrice == null) {
                Toast.makeText(this, "Invalid price format!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalPrice = enteredPrice + (enteredPrice * 0.10) // Add 10% reward
            binding.tvFinalPrice.text = "💰 Final Price (with 10% Delivery): ₹${String.format("%.2f", finalPrice)}"
            binding.tvFinalPrice.visibility = View.VISIBLE

            // Show Return to Home button
            binding.btnReturnHome.visibility = View.VISIBLE
        }

        // Return to Home button logic
        binding.btnReturnHome.setOnClickListener {
            val intent = Intent(this, Homepage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun addOrderItems(items: List<String>) {
        val container: LinearLayout = binding.layoutOrderItems
        container.removeAllViews()

        if (items.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No items found!"
                textSize = 16f
                setTextColor(getColor(android.R.color.darker_gray))
                setPadding(10, 10, 10, 10)
            }
            container.addView(emptyText)
            return
        }

        for (item in items) {
            val tv = TextView(this).apply {
                text = "• $item"
                textSize = 16f
                setTextColor(getColor(android.R.color.black))
                setPadding(8, 8, 8, 8)
            }
            container.addView(tv)
        }
    }
}
