package com.example.smallbasket

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsetsController
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
    private lateinit var vibrator: Vibrator // Added for haptic feedback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Match status bar style from Homepage & RequestActivity
        setupStatusBar()

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Initialize vibrator for haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val user = auth.currentUser

        setupClickListeners()

        if (user != null) {
            val displayName = user.displayName ?: "User"
            binding.tvFullName.text = displayName
            binding.tvEmailAddress.text = user.email ?: "No email"

            user.metadata?.creationTimestamp?.let { timestamp ->
                binding.tvJoiningDate.text =
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
            }

            loadUserDataFromFirestore(user.uid)
            user.email?.let { email ->
                parseEmailAndShowDetails(email)
            }
            loadUserStats()
        } else {
            redirectToLogin()
        }
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            performMediumHaptic()
            finish()
        }

        binding.btnEditProfile.setOnClickListener {
            performMediumHaptic()
            Toast.makeText(this, "Edit Profile coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            performMediumHaptic()
            auth.signOut()
            redirectToLogin()
        }
    }

    private fun loadUserDataFromFirestore(uid: String) {
        binding.tvMobileNumber.text = "Not set"
        lifecycleScope.launch {
            try {
                val document = firestore.collection("users").document(uid).get().await()
                if (document.exists()) {
                    val phone = document.getString("phone")
                    val name = document.getString("name")
                    binding.tvMobileNumber.text = phone ?: "Not set"
                    if (!name.isNullOrEmpty()) {
                        binding.tvFullName.text = name
                    }
                } else {
                    binding.tvMobileNumber.text = "Not available"
                }
            } catch (e: Exception) {
                binding.tvMobileNumber.text = "Error loading"
            }
        }
    }

    private fun loadUserStats() {
        binding.tvStatDeliveries.text = "0"
        binding.tvStatOrders.text = "0"
        binding.tvStatEarned.text = "₹0"

        lifecycleScope.launch {
            try {
                val result = repository.getUserStats(auth.currentUser?.uid ?: "")
                result.onSuccess { stats ->
                    binding.tvStatDeliveries.text = stats.completedDeliveries.toString()
                    binding.tvStatOrders.text = stats.totalOrders.toString()
                    val totalEarned = stats.completedDeliveries * 10
                    binding.tvStatEarned.text = "₹$totalEarned"
                }
            } catch (e: Exception) {
                // Silent fail
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

            binding.cardStudentInfo.visibility = View.VISIBLE
            binding.layoutBatch.visibility = View.VISIBLE
            binding.tvBatch.text = "Batch: $batchStartYear–${batchStartYear + 4}"
            binding.layoutBranch.visibility = View.VISIBLE
            binding.tvBranch.text = "Branch: $branch"

            if (section != null) {
                binding.layoutSection.visibility = View.VISIBLE
                binding.tvSection.text = "Section: $section"
            } else {
                binding.layoutSection.visibility = View.GONE
            }

            binding.layoutDasa.visibility = if (isDasa) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            binding.cardStudentInfo.visibility = View.GONE
        }
    }

    /**
     * Medium Haptic: 15ms duration, 80 amplitude (0-255 scale = ~204)
     * Used for: Button taps, secondary navigation actions
     */
    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                15,  // 15ms duration
                80  // ~80% amplitude (80% of 255 = 204)
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }
}