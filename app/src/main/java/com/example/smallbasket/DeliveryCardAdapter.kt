package com.example.smallbasket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class DeliveryCardAdapter(
    private val requests: List<DeliveryRequest>,
    private val onItemClick: (DeliveryRequest) -> Unit
) : RecyclerView.Adapter<DeliveryCardAdapter.DeliveryCardViewHolder>() {

    inner class DeliveryCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardDelivery: MaterialCardView = itemView.findViewById(R.id.cardDelivery)
        val tvPriorityBadge: TextView = itemView.findViewById(R.id.tvPriorityBadge)
        val tvDeliveryTitle: TextView = itemView.findViewById(R.id.tvDeliveryTitle)
        val ivPickupIcon: ImageView = itemView.findViewById(R.id.ivPickupIcon)
        val tvPickupLocation: TextView = itemView.findViewById(R.id.tvPickupLocation)
        val ivDropoffIcon: ImageView = itemView.findViewById(R.id.ivDropoffIcon)
        val tvDropoffLocation: TextView = itemView.findViewById(R.id.tvDropoffLocation)
        val tvDeliveryFee: TextView = itemView.findViewById(R.id.tvDeliveryFee)
        val tvDeliveryTime: TextView = itemView.findViewById(R.id.tvDeliveryTime)
        val btnAcceptDelivery: MaterialButton = itemView.findViewById(R.id.btnAcceptDelivery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delivery_card, parent, false)
        return DeliveryCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeliveryCardViewHolder, position: Int) {
        val request = requests[position]

        // Priority badge visibility
        holder.tvPriorityBadge.visibility = if (request.priority) View.VISIBLE else View.GONE

        // Set text content
        holder.tvDeliveryTitle.text = request.title
        holder.tvPickupLocation.text = request.pickup
        holder.tvDropoffLocation.text = request.dropoff
        holder.tvDeliveryFee.text = request.fee
        holder.tvDeliveryTime.text = request.time

        // Card click to view details
        holder.cardDelivery.setOnClickListener {
            onItemClick(request)
        }

        // Accept button click
        holder.btnAcceptDelivery.setOnClickListener {
            onItemClick(request)
        }
    }

    override fun getItemCount(): Int = requests.size
}