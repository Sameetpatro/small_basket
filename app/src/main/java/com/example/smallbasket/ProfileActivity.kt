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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val repository = OrderRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            // Display basic Firebase Auth data
            binding.tvProfileName.text = user.displayName ?: "User"
            binding.tvProfileEmail.text = user.email ?: "No email"

            user.metadata?.creationTimestamp?.let { timestamp ->
                binding.tvJoiningDate.text =
                    "Joined: ${java.text.SimpleDateFormat("MMM yyyy").format(java.util.Date(timestamp))}"
            }

            // Load phone number from Firestore
            loadUserDataFromFirestore(user.uid)

            // Load user stats from backend
            loadUserStats()
        }

        binding.menuIcon.setOnClickListener { showMenu() }
    }

    private fun loadUserDataFromFirestore(uid: String) {
        lifecycleScope.launch {
            try {
                val document = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (document.exists()) {
                    val phone = document.getString("phone") ?: "Not set"
                    val name = document.getString("name")

                    binding.tvProfileMobile.text = "Mobile: $phone"

                    // Update name if available in Firestore
                    if (!name.isNullOrEmpty()) {
                        binding.tvProfileName.text = name
                    }
                } else {
                    binding.tvProfileMobile.text = "Mobile: Not available"
                }
            } catch (e: Exception) {
                binding.tvProfileMobile.text = "Mobile: Error loading"
                println("Error loading user data: ${e.message}")
            }
        }
    }

    private fun loadUserStats() {
        lifecycleScope.launch {
            val result = repository.getUserStats("")

            result.onSuccess { stats ->
                val statsText = "Orders: ${stats.totalOrders} | Active: ${stats.activeOrders} | Completed: ${stats.completedDeliveries}"
                Toast.makeText(
                    this@ProfileActivity,
                    statsText,
                    Toast.LENGTH_SHORT
                ).show()

                // You can update UI elements here if you add TextViews for stats
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