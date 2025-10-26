package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityProfileBinding
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

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

        setupClickListeners()

        if (user != null) {
            // Display basic Firebase Auth data
            val displayName = user.displayName ?: "User"
            binding.tvProfileName.text = displayName
            binding.tvFullName.text = displayName
            binding.tvEmailAddress.text = user.email ?: "No email"

            user.metadata?.creationTimestamp?.let { timestamp ->
                binding.tvJoiningDate.text =
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
            }

            // Load phone number from Firestore
            loadUserDataFromFirestore(user.uid)

            // Decode student info from email and display
            user.email?.let { email ->
                parseEmailAndShowDetails(email)
            }

            // Load user stats
            loadUserStats()
        } else {
            // User not logged in, redirect to MainActivity
            redirectToLogin()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Edit Profile coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserDataFromFirestore(uid: String) {
        // Set default immediately
        binding.tvMobileNumber.text = "Not set"

        lifecycleScope.launch {
            try {
                val document = firestore.collection("users")
                    .document(uid)
                    .get()
                    .await()

                if (document.exists()) {
                    val phone = document.getString("phone")
                    val name = document.getString("name")

                    // Update phone number
                    binding.tvMobileNumber.text = phone ?: "Not set"

                    // Update name if available
                    if (!name.isNullOrEmpty()) {
                        binding.tvProfileName.text = name
                        binding.tvFullName.text = name
                    }
                } else {
                    binding.tvMobileNumber.text = "Not available"
                }
            } catch (e: Exception) {
                binding.tvMobileNumber.text = "Error loading"
                println("Error loading user data: ${e.message}")
            }
        }
    }

    private fun loadUserStats() {
        // Set defaults immediately
        binding.tvStatDeliveries.text = "0"
        binding.tvStatOrders.text = "0"
        binding.tvStatEarned.text = "₹0"

        lifecycleScope.launch {
            try {
                val result = repository.getUserStats(auth.currentUser?.uid ?: "")
                result.onSuccess { stats ->
                    // Update UI with stats
                    binding.tvStatDeliveries.text = stats.completedDeliveries.toString()
                    binding.tvStatOrders.text = stats.totalOrders.toString()

                    // Calculate total earned based on completed deliveries
                    val totalEarned = stats.completedDeliveries * 10
                    binding.tvStatEarned.text = "₹$totalEarned"
                }
                result.onFailure { error ->
                    println("Stats error: ${error.message}")
                }
            } catch (e: Exception) {
                println("Exception loading stats: ${e.message}")
            }
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun parseEmailAndShowDetails(email: String) {
        val username = email.substringBefore("@")
        val pattern = Pattern.compile("(\\d{8})")
        val matcher = pattern.matcher(username)

        if (!matcher.find()) {
            // Not a student email format, hide student info section
            binding.cardStudentInfo.visibility = View.GONE
            return
        }

        val digits = matcher.group(1) ?: return
        if (digits.length != 8) {
            binding.cardStudentInfo.visibility = View.GONE
            return
        }

        try {
            val admissionDigit = digits[0]
            val batchSuffix = digits.substring(1, 3)
            val branchCode = digits.substring(3, 5)
            val rollStr = digits.substring(5, 8)

            val batchStartYear = 2000 + batchSuffix.toInt()
            val rollNumber = rollStr.toInt()

            val branch = when (branchCode) {
                "11" -> "Computer Science (CSE)"
                "12" -> "Information Technology (IT)"
                "13" -> "Data Science"
                else -> "Unknown Branch"
            }

            val section = if (branchCode == "11") {
                if (rollNumber > 60) "B" else "A"
            } else null

            val isDasa = admissionDigit == '2'

            // Show student info card
            binding.cardStudentInfo.visibility = View.VISIBLE

            // Update UI - Batch
            binding.layoutBatch.visibility = View.VISIBLE
            binding.tvBatch.text = "Batch: $batchStartYear–${batchStartYear + 4}"

            // Update UI - Branch
            binding.layoutBranch.visibility = View.VISIBLE
            binding.tvBranch.text = "Branch: $branch"

            // Update UI - Section
            section?.let {
                binding.layoutSection.visibility = View.VISIBLE
                binding.tvSection.text = "Section: $it"
            }

            // Update UI - DASA
            if (isDasa) {
                binding.layoutDasa.visibility = View.VISIBLE
            } else {
                binding.layoutDasa.visibility = View.GONE
            }
        } catch (e: Exception) {
            println("Error parsing email: ${e.message}")
            binding.cardStudentInfo.visibility = View.GONE
        }
    }
}