package com.example.smallbasket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.smallbasket.models.Order
import com.example.smallbasket.utils.TimeUtils

class MyLogsAdapter(
    private var orders: List<Order>,
    private val onItemClick: (Order) -> Unit = {}
) : RecyclerView.Adapter<MyLogsAdapter.LogViewHolder>() {

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItems = itemView.findViewById<TextView>(R.id.tvLogItems)
        val tvPickup = itemView.findViewById<TextView>(R.id.tvLogPickup)
        val tvDrop = itemView.findViewById<TextView>(R.id.tvLogDrop)
        val tvStatus = itemView.findViewById<TextView>(R.id.tvLogStatus)
        val tvDate = itemView.findViewById<TextView>(R.id.tvLogDate)
        val tvReward = itemView.findViewById<TextView>(R.id.tvLogReward)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val order = orders[position]

        holder.tvItems.text = order.items.joinToString(", ")

        // Safely display pickup/drop areas
        holder.tvPickup.text = "From: ${order.pickupArea ?: "Unknown"}"
        holder.tvDrop.text = "To: ${order.dropArea ?: "Unknown"}"

        // ✅ Use 'reward' (from backend) instead of 'rewardPercentage'
        // Fallback to 0 if null or invalid
        val rewardAmount = runCatching {
            (order.reward ?: 0.0).toInt()
        }.getOrDefault(0)
        holder.tvReward.text = "Reward: ₹$rewardAmount"

        // Format status with color
        val status = order.status?.lowercase() ?: "unknown"
        holder.tvStatus.text = status.replaceFirstChar { it.uppercase() }

        val color = when (status) {
            "open" -> android.R.color.holo_orange_dark
            "accepted" -> android.R.color.holo_blue_dark
            "completed" -> android.R.color.holo_green_dark
            "cancelled" -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, color))

        // Format date
        holder.tvDate.text = order.createdAt?.let { TimeUtils.formatDateTime(it) } ?: "Unknown"

        // Click listener
        holder.itemView.setOnClickListener {
            onItemClick(order)
        }
    }

    override fun getItemCount(): Int = orders.size

    fun updateData(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}