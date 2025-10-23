package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.smallbasket.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

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

            // Create Firebase Auth User
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val profileUpdates = userProfileChangeRequest {
                            displayName = name
                        }
                        user?.updateProfile(profileUpdates)

                        // Push extra info to Firebase Database
                        val userId = user?.uid ?: ""
                        val database = FirebaseDatabase.getInstance().reference
                        val userData = mapOf(
                            "name" to name,
                            "email" to email,
                            "mobile" to mobile
                        )
                        database.child("users").child(userId).setValue(userData)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Account Created Successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@RegisterActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        }
    }
}
