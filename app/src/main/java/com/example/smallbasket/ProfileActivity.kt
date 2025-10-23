package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.example.smallbasket.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // Set name and email
        binding.tvProfileName.text = user?.displayName ?: "User"
        binding.tvProfileEmail.text = user?.email ?: "No email"

        // Set joining date
        user?.metadata?.creationTimestamp?.let { timestamp ->
            binding.tvJoiningDate.text =
                "Joined: ${java.text.SimpleDateFormat("MMM yyyy").format(java.util.Date(timestamp))}"
        }

        // Fetch mobile number from Firebase Realtime Database
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
        }

        // Menu options
        binding.menuIcon.setOnClickListener { showMenu() }
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
