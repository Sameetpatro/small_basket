package com.example.smallbasket

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smallbasket.notifications.NotificationStorage
import com.example.smallbasket.models.StoredNotification

class NotificationActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnClearAll: TextView
    private lateinit var rvNotifications: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout

    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<StoredNotification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        initViews()
        setupRecyclerView()
        loadNotifications()
        setupListeners()
    }

    /**
     * Initialize all UI components
     */
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnClearAll = findViewById(R.id.btnClearAll)
        rvNotifications = findViewById(R.id.rvNotifications)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
    }

    /**
     * Setup RecyclerView and its adapter
     */
    private fun setupRecyclerView() {
        adapter = NotificationAdapter(notifications)
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = adapter
    }

    /**
     * Load stored notifications from NotificationStorage
     */
    private fun loadNotifications() {
        val storedNotifications = NotificationStorage.getNotifications(this)
        notifications.clear()
        notifications.addAll(storedNotifications.sortedByDescending { it.timestamp })
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    /**
     * Click listeners for back and clear buttons
     */
    private fun setupListeners() {
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        btnClearAll.setOnClickListener {
            NotificationStorage.clearAll(this)
            notifications.clear()
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    /**
     * Toggle empty state visibility
     */
    private fun updateEmptyState() {
        val isEmpty = notifications.isEmpty()
        rvNotifications.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        btnClearAll.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}
