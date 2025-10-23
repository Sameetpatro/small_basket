package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smallbasket.repository.OrderRepository
import com.example.smallbasket.models.Order
import kotlinx.coroutines.launch

class RequestActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val repository = OrderRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request)

        recyclerView = findViewById(R.id.recyclerRequests)
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progress_bar)

        loadAvailableOrders()
    }

    private fun loadAvailableOrders() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Use "open" status instead of "pending"
            val result = repository.getAllOrders(status = "open")

            result.onSuccess { orders ->
                progressBar.visibility = View.GONE

                if (orders.isEmpty()) {
                    Toast.makeText(
                        this@RequestActivity,
                        "No pending requests available",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    setupRecyclerView(orders)
                }
            }

            result.onFailure { error ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@RequestActivity,
                    "Error loading requests: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRecyclerView(orders: List<Order>) {
        val requests = orders.map { order ->
            Requests(
                title = order.items.joinToString(", "),
                pickup = "${order.pickupLocation} (${order.pickupArea})",
                drop = "${order.dropLocation} (${order.dropArea})",
                details = order.notes ?: "No additional details",
                orderId = order.id,
                priority = order.priority,
                bestBefore = order.bestBefore,
                deadline = order.deadline,
                rewardPercentage = order.rewardPercentage
            )
        }

        recyclerView.adapter = RequestAdapter(requests) { request ->
            val intent = Intent(this, RequestDetailActivity::class.java)
            intent.putExtra("order_id", request.orderId)
            intent.putExtra("title", request.title)
            intent.putExtra("pickup", request.pickup)
            intent.putExtra("drop", request.drop)
            intent.putExtra("details", request.details)
            intent.putExtra("priority", request.priority)
            intent.putExtra("best_before", request.bestBefore)
            intent.putExtra("deadline", request.deadline)
            intent.putExtra("reward_percentage", request.rewardPercentage)
            intent.putExtra("isImportant", request.priority == "emergency")
            startActivity(intent)
        }
    }
}