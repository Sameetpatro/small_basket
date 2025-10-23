package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.smallbasket.databinding.ActivityLoginBinding


class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Login"

        binding.tvGoToSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            if(email.isNotEmpty() && password.isNotEmpty()){
                MainActivity.auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                    if(it.isSuccessful){
                        Toast.makeText(this@LoginActivity, "Logged in Sucessfully",Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, Homepage::class.java))
                        finish()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this@LoginActivity, it.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}