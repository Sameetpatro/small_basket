package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.repository.OrderRepository
import kotlinx.coroutines.launch

class DeliveryConfimationActivity : AppCompatActivity() {

    private val repository = OrderRepository()
    private var orderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery_confimation)

        orderId = intent.getStringExtra("order_id") ?: ""
        val title = intent.getStringExtra("title") ?: "Order"

        findViewById<TextView>(R.id.tvConfirmationTitle)?.text =
            "Delivery Accepted: $title"

        findViewById<Button>(R.id.btnMarkPickedUp)?.setOnClickListener {
            // Backend doesn't have picked_up status, so we just show feedback
            Toast.makeText(this, "Order marked as picked up!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnMarkDelivered)?.setOnClickListener {
            // Use "completed" status for backend
            updateOrderStatus("completed")
        }

        findViewById<Button>(R.id.btnBackToHome)?.setOnClickListener {
            startActivity(Intent(this, Homepage::class.java))
            finish()
        }
    }

    private fun updateOrderStatus(status: String) {
        lifecycleScope.launch {
            val result = repository.updateOrderStatus(orderId, status)

            result.onSuccess { order ->
                val message = when(status) {
                    "completed" -> "Order marked as delivered!"
                    else -> "Status updated!"
                }
                Toast.makeText(this@DeliveryConfimationActivity, message, Toast.LENGTH_SHORT).show()

                if (status == "completed") {
                    startActivity(Intent(this@DeliveryConfimationActivity, Homepage::class.java))
                    finish()
                }
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@DeliveryConfimationActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}