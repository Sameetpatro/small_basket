package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smallbasket.databinding.ActivityMyLogsBinding
import com.example.smallbasket.models.Order
import com.example.smallbasket.repository.OrderRepository
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MyLogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyLogsBinding
    private val repository = OrderRepository()
    private lateinit var myOrdersAdapter: MyLogsAdapter
    private lateinit var myDeliveriesAdapter: MyLogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupTabs()
        loadMyOrders()

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerViews() {
        myOrdersAdapter = MyLogsAdapter(emptyList()) { order ->
            navigateToRequestDetail(order)
        }

        myDeliveriesAdapter = MyLogsAdapter(emptyList()) { order ->
            navigateToDeliveryDetail(order)
        }

        binding.rvMyOrders.apply {
            layoutManager = LinearLayoutManager(this@MyLogsActivity)
            adapter = myOrdersAdapter
        }

        binding.rvMyDeliveries.apply {
            layoutManager = LinearLayoutManager(this@MyLogsActivity)
            adapter = myDeliveriesAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.rvMyOrders.visibility = View.VISIBLE
                        binding.rvMyDeliveries.visibility = View.GONE
                        loadMyOrders()
                    }
                    1 -> {
                        binding.rvMyOrders.visibility = View.GONE
                        binding.rvMyDeliveries.visibility = View.VISIBLE
                        loadMyDeliveries()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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

    private fun isPriorityOrder(priority: String?): Boolean {
        return priority?.equals("emergency", ignoreCase = true) == true ||
                priority?.equals("high", ignoreCase = true) == true ||
                priority?.equals("urgent", ignoreCase = true) == true
    }

    private fun navigateToRequestDetail(order: Order) {
        val intent = Intent(this, RequestDetailActivity::class.java).apply {
            putExtra("order_id", order.id)
            putExtra("title", order.items.joinToString(", "))
            putExtra("pickup", extractLocation(order.pickupLocation, order.pickupArea))
            putExtra("pickup_area", order.pickupArea)
            putExtra("drop", extractLocation(order.dropLocation, order.dropArea))
            putExtra("drop_area", order.dropArea)
            putExtra("details", order.notes ?: "")
            putExtra("priority", if (isPriorityOrder(order.priority)) "emergency" else "normal")
            putExtra("best_before", order.bestBefore)
            putExtra("deadline", order.deadline)
            putExtra("reward_percentage", extractRewardPercentage(order).toDouble())
            putExtra("isImportant", isPriorityOrder(order.priority))
            // Only include item_price if it exists
            order.itemPrice?.let { putExtra("item_price", it) }
            putExtra("status", order.status)
            // ✅ PASS ACCEPTOR INFO
            putExtra("acceptor_email", order.acceptorEmail)
            putExtra("acceptor_name", order.acceptorName)  // ✅ NEW
            putExtra("acceptor_phone", order.acceptorPhone)  // ✅ NEW
        }
        startActivity(intent)
    }

    private fun navigateToDeliveryDetail(order: Order) {
        // If it's an accepted/completed delivery, navigate to delivery confirmation
        if (order.status == "accepted" || order.status == "completed") {
            val intent = Intent(this, DeliveryConfimationActivity::class.java).apply {
                putExtra("order_id", order.id)
                putExtra("title", order.items.joinToString(", "))
            }
            startActivity(intent)
        } else {
            // Otherwise show request detail
            navigateToRequestDetail(order)
        }
    }

    private fun loadMyOrders() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyMessage.visibility = View.GONE

        lifecycleScope.launch {
            val result = repository.getUserOrders()

            result.onSuccess { orders ->
                binding.progressBar.visibility = View.GONE

                if (orders.isEmpty()) {
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = "No orders placed yet"
                    binding.rvMyOrders.visibility = View.GONE
                } else {
                    binding.tvEmptyMessage.visibility = View.GONE
                    binding.rvMyOrders.visibility = View.VISIBLE
                    myOrdersAdapter.updateData(orders)
                }
            }

            result.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = "Error loading orders"
                binding.rvMyOrders.visibility = View.GONE

                Toast.makeText(
                    this@MyLogsActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadMyDeliveries() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyMessage.visibility = View.GONE

        lifecycleScope.launch {
            val result = repository.getAcceptedOrders()

            result.onSuccess { orders ->
                binding.progressBar.visibility = View.GONE

                if (orders.isEmpty()) {
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = "No deliveries accepted yet"
                    binding.rvMyDeliveries.visibility = View.GONE
                } else {
                    binding.tvEmptyMessage.visibility = View.GONE
                    binding.rvMyDeliveries.visibility = View.VISIBLE
                    myDeliveriesAdapter.updateData(orders)
                }
            }

            result.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = "Error loading deliveries"
                binding.rvMyDeliveries.visibility = View.GONE

                Toast.makeText(
                    this@MyLogsActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}