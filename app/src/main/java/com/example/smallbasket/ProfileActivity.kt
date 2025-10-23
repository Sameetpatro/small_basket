package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityProfileBinding
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private val repository = OrderRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        binding.tvProfileName.text = user?.displayName ?: "User"
        binding.tvProfileEmail.text = user?.email ?: "No email"

        user?.metadata?.creationTimestamp?.let { timestamp ->
            binding.tvJoiningDate.text =
                "Joined: ${java.text.SimpleDateFormat("MMM yyyy").format(java.util.Date(timestamp))}"
        }

        val userId = user?.uid
        val database = FirebaseDatabase.getInstance().reference
        if (userId != null) {
            database.child("users").child(userId).get()
                .addOnSuccessListener { snapshot ->
                    val mobile = snapshot.child("mobile").getValue(String::class.java) ?: "Not set"
                    binding.tvProfileMobile.text = "Mobile: $mobile"
                }
                .addOnFailureListener {
                    binding.tvProfileMobile.text = "Mobile: Not available"
                }

            // Load user stats from backend
            loadUserStats()
        }

        binding.menuIcon.setOnClickListener { showMenu() }
    }

    private fun loadUserStats() {
        lifecycleScope.launch {
            val result = repository.getUserStats("")

            result.onSuccess { stats ->
                // Display stats in UI
                val statsText = "Orders: ${stats.totalOrders} | Active: ${stats.activeOrders} | Completed: ${stats.completedDeliveries}"
                Toast.makeText(
                    this@ProfileActivity,
                    statsText,
                    Toast.LENGTH_SHORT
                ).show()

                // You can update UI elements here
                // Example: binding.tvStats.text = statsText
            }

            result.onFailure { error ->
                // Silently fail or show minimal error
                println("Stats error: ${error.message}")
            }
        }
    }

    private fun showMenu() {
        val popup = PopupMenu(this, binding.menuIcon)
        popup.menu.add("Edit Profile")
        popup.menu.add("Sign Out")
        popup.menu.add("Contact Developers")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title) {
                "Sign Out" -> {
                    auth.signOut()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                else -> true
            }
        }
        popup.show()
    }
}