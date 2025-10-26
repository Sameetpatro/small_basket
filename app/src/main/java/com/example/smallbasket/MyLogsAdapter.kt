package com.example.smallbasket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smallbasket.models.Order
import java.text.SimpleDateFormat
import java.util.*

class MyLogsAdapter(
    private var orders: List<Order>
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
        holder.tvPickup.text = "From: ${order.pickupArea}"
        holder.tvDrop.text = "To: ${order.dropArea}"
        holder.tvReward.text = "Reward: ${order.rewardPercentage}%"

        // Format status with color
        val statusText = order.status.uppercase()
        holder.tvStatus.text = statusText
        when (order.status.lowercase()) {
            "open" -> {
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
            }
            "accepted" -> {
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
            }
            "completed" -> {
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
            }
            "cancelled" -> {
                holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
            }
        }

        // Format date
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(order.createdAt)
            holder.tvDate.text = date?.let { outputFormat.format(it) } ?: order.createdAt
        } catch (e: Exception) {
            holder.tvDate.text = order.createdAt
        }
    }

    override fun getItemCount(): Int = orders.size

    fun updateData(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}