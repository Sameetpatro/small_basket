package com.example.smallbasket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.smallbasket.models.StoredNotification
import com.example.smallbasket.R
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val notifications: List<StoredNotification>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvBody: TextView = view.findViewById(R.id.tvBody)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val cardView: CardView = view.findViewById(R.id.cardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = notifications[position]
        holder.tvTitle.text = item.title
        holder.tvBody.text = item.body
        holder.tvTimestamp.text = formatTimestamp(item.timestamp)

        // Dim read notifications
        val alpha = if (item.isRead) 0.6f else 1.0f
        holder.cardView.alpha = alpha
    }

    override fun getItemCount(): Int = notifications.size

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
