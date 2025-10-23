package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Login"

        binding.tvGoToSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button during login
            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Logging in..."

            loginUser(email, password)
        }
    }

    private fun loginUser(email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Step 1: Sign in with Firebase Auth
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    // Step 2: Reload user to get latest email verification status
                    user.reload().await()

                    // Step 3: Check if email is verified
                    if (!user.isEmailVerified) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Please verify your email first. Check your inbox.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Optionally, offer to resend verification email
                        user.sendEmailVerification().await()
                        Toast.makeText(
                            this@LoginActivity,
                            "Verification email sent again.",
                            Toast.LENGTH_SHORT
                        ).show()

                        auth.signOut()
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Login"
                        return@launch
                    }

                    // Step 4: Update email_verified status in Firestore
                    firestore.collection("users")
                        .document(user.uid)
                        .update(
                            mapOf(
                                "email_verified" to true,
                                "last_login" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .await()

                    Toast.makeText(
                        this@LoginActivity,
                        "Logged in successfully",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(Intent(this@LoginActivity, Homepage::class.java))
                    finish()

                } else {
                    throw Exception("Login failed")
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Login failed: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Login"
            }
        }
    }
}