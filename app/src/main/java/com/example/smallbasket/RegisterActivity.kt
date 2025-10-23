package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityRegisterBinding
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val repository = OrderRepository()

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

            // Disable button during registration
            binding.btnSignUp.isEnabled = false
            binding.btnSignUp.text = "Creating Account..."

            createAccount(name, email, password, mobile)
        }
    }

    private fun createAccount(name: String, email: String, password: String, mobile: String) {
        lifecycleScope.launch {
            try {
                // Step 1: Create Firebase Auth User
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    // Step 2: Update display name
                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    user.updateProfile(profileUpdates).await()

                    // Step 3: Send email verification
                    user.sendEmailVerification().await()

                    // Step 4: Store user data in Firestore (matching backend structure)
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

                    Toast.makeText(
                        this@RegisterActivity,
                        "Account created! Please verify your email before logging in.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Sign out user until they verify email
                    auth.signOut()

                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finish()
                } else {
                    throw Exception("User creation failed")
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@RegisterActivity,
                    "Registration failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnSignUp.isEnabled = true
                binding.btnSignUp.text = "Create Account"
            }
        }
    }
}