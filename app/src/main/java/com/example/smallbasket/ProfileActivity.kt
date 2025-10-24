package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.View
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

        if (user != null) {
            // Display basic Firebase Auth data
            binding.tvProfileName.text = user.displayName ?: "User"
            binding.tvProfileEmail.text = user.email ?: "No email"

            user.metadata?.creationTimestamp?.let { timestamp ->
                binding.tvJoiningDate.text =
                    "Joined: ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(timestamp))}"
            }

            // Load phone number from Firestore
            loadUserDataFromFirestore(user.uid)

            // 🔽 New: Decode student info from email and display
            user.email?.let { email ->
                parseEmailAndShowDetails(email)
            }

            // Load user stats
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
                val statsText =
                    "Orders: ${stats.totalOrders} | Active: ${stats.activeOrders} | Completed: ${stats.completedDeliveries}"
                Toast.makeText(this@ProfileActivity, statsText, Toast.LENGTH_SHORT).show()
            }
            result.onFailure { error ->
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

    // 🔽 New Function: Parse and display student info from email
    private fun parseEmailAndShowDetails(email: String) {
        val username = email.substringBefore("@")
        val pattern = Pattern.compile("(\\d{8})")
        val matcher = pattern.matcher(username)
        if (!matcher.find()) return

        val digits = matcher.group(1) ?: return
        if (digits.length != 8) return

        val admissionDigit = digits[0]
        val batchSuffix = digits.substring(1, 3)
        val branchCode = digits.substring(3, 5)
        val rollStr = digits.substring(5, 8)

        val batchStartYear = 2000 + batchSuffix.toIntOrNull()!!
        val rollNumber = rollStr.toIntOrNull() ?: 0

        val branch = when (branchCode) {
            "11" -> "Computer Science (CSE)"
            "12" -> "Information Technology (IT)"
            "13" -> "Data Science"
            else -> "Unknown Branch"
        }

        val section =
            if (branchCode == "11") { // only for CSE
                if (rollNumber > 60) "B" else "A"
            } else null

        val isDasa = admissionDigit == '2'

        // Update UI
        binding.tvBatch.apply {
            text = "Batch: $batchStartYear–${batchStartYear + 4}"
            visibility = View.VISIBLE
        }

        binding.tvBranch.apply {
            text = "Branch: $branch"
            visibility = View.VISIBLE
        }

        section?.let {
            binding.tvSection.text = "Section: $it"
            binding.tvSection.visibility = View.VISIBLE
        }

        if (isDasa) {
            binding.tvDasa.visibility = View.VISIBLE
        } else {
            binding.tvDasa.visibility = View.GONE
        }
    }
}
