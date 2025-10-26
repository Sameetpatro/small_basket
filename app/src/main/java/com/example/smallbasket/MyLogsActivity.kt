package com.example.smallbasket

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        myOrdersAdapter = MyLogsAdapter(emptyList())
        myDeliveriesAdapter = MyLogsAdapter(emptyList())

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

    private fun loadMyOrders() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = repository.getUserOrders()

            result.onSuccess { orders ->
                binding.progressBar.visibility = View.GONE
                myOrdersAdapter.updateData(orders)

                if (orders.isEmpty()) {
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = "No orders placed yet"
                } else {
                    binding.tvEmptyMessage.visibility = View.GONE
                }
            }

            result.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = "Error loading orders"
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

        lifecycleScope.launch {
            val result = repository.getAcceptedOrders()

            result.onSuccess { orders ->
                binding.progressBar.visibility = View.GONE
                myDeliveriesAdapter.updateData(orders)

                if (orders.isEmpty()) {
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = "No deliveries accepted yet"
                } else {
                    binding.tvEmptyMessage.visibility = View.GONE
                }
            }

            result.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.tvEmptyMessage.text = "Error loading deliveries"
                Toast.makeText(
                    this@MyLogsActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}