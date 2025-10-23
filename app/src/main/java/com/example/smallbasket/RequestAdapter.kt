package com.example.smallbasket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RequestAdapter(
    private val requests: List<Requests>,
    private val onViewDetailsClick: (Requests) -> Unit
) : RecyclerView.Adapter<RequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title = itemView.findViewById<TextView>(R.id.tvItemTitle)
        val pickup = itemView.findViewById<TextView>(R.id.tvPickup)
        val drop = itemView.findViewById<TextView>(R.id.tvDrop)
        val btnView = itemView.findViewById<Button>(R.id.btnViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_requests, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        holder.title.text = request.title
        holder.pickup.text = "Pickup: ${request.pickup}"
        holder.drop.text = "Drop: ${request.drop}"

        holder.btnView.setOnClickListener {
            onViewDetailsClick(request)
        }
    }

    override fun getItemCount(): Int = requests.size
}
