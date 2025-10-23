package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RequestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerRequests)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val sampleRequests = listOf(
            Requests("Groceries Delivery", "Market Area", "Hostel Block A", "Details about the grocery delivery task"),
            Requests("Laptop Pickup", "Home", "Service Center", "Pick up laptop for repair"),
            Requests("Document Delivery", "Office", "University", "Deliver admission documents safely")
        )

        recyclerView.adapter = RequestAdapter(sampleRequests) { request ->
            val intent = Intent(this, RequestDetailActivity::class.java)
            intent.putExtra("title", request.title)
            intent.putExtra("pickup", request.pickup)
            intent.putExtra("drop", request.drop)
            intent.putExtra("details", request.details)
            startActivity(intent)
        }
    }
}
