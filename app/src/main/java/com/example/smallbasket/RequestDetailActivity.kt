package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smallbasket.databinding.ActivityRequestDetailsBinding

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get data from Intent
        val title = intent.getStringExtra("title")
        val pickup = intent.getStringExtra("pickup")
        val drop = intent.getStringExtra("drop")
        val details = intent.getStringExtra("details")
        val isImportant = intent.getBooleanExtra("isImportant", false)

        // Set dynamic data
        binding.tvItemTitle.text = title ?: "Unknown Item"
        binding.tvPickup.text = "Pickup: $pickup"
        binding.tvDrop.text = "Drop: $drop"
        binding.tvNotes.text = details ?: "No extra details provided"

        // Set importance indicator
        if (isImportant) {
            binding.tvImportance.text = "⚠️ Marked as Important"
            binding.tvImportance.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            binding.tvImportance.text = "Normal Request"
            binding.tvImportance.setTextColor(getColor(android.R.color.holo_green_dark))
        }

        // Back button → Return to RequestActivity
        binding.btnBackHome.setOnClickListener {
            finish()
        }

        // Accept button → Open ConfirmationActivity
        binding.btnAcceptRequest.setOnClickListener {
            val intent = Intent(this, DeliveryConfimationActivity::class.java)
            intent.putExtra("title", title)
            startActivity(intent)
        }
    }
}
