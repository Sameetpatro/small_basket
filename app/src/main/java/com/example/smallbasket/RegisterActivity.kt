package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Register"

        binding.tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnSignUp.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || mobile.isEmpty()) {
                Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!email.endsWith("@iiitsonepat.ac.in")) {
                Toast.makeText(this, "Please use your IIIT Sonepat email ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mobile.length != 10) {
                Toast.makeText(this, "Enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSignUp.isEnabled = false
            binding.btnSignUp.text = "Creating Account..."

            createAccount(name, email, password, mobile)
        }
    }

    private fun createAccount(name: String, email: String, password: String, mobile: String) {
        lifecycleScope.launch {
            try {
                // Create Firebase Auth User
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    // Update display name
                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    user.updateProfile(profileUpdates).await()

                    // Send email verification
                    user.sendEmailVerification().await()

                    // Store user data in Firestore
                    val userData = hashMapOf(
                        "uid" to user.uid,
                        "email" to email,
                        "name" to name,
                        "phone" to mobile,
                        "email_verified" to false,
                        "created_at" to com.google.firebase.Timestamp.now(),
                        "last_login" to com.google.firebase.Timestamp.now()
                    )

                    firestore.collection("users")
                        .document(user.uid)
                        .set(userData)
                        .await()

                    // Sign out until email is verified
                    auth.signOut()

                    // Show modern verification dialog
                    showVerificationDialog(email)

                } else {
                    throw Exception("User creation failed")
                }

            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("email address is already in use") == true ->
                        "This email is already registered. Please login instead."
                    e.message?.contains("network") == true ->
                        "Network error. Please check your internet connection."
                    e.message?.contains("weak-password") == true ->
                        "Password is too weak. Use at least 6 characters."
                    else -> "Registration failed: ${e.localizedMessage}"
                }

                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                binding.btnSignUp.isEnabled = true
                binding.btnSignUp.text = "Create Account"
            }
        }
    }

    private fun showVerificationDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("✅ Account Created!")
            .setMessage(
                "A verification email has been sent to:\n\n$email\n\n" +
                        "📧 Check your inbox and click the verification link\n\n" +
                        "Once verified, come back and login immediately - no waiting required!"
            )
            .setPositiveButton("Go to Login") { _, _ ->
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }
}