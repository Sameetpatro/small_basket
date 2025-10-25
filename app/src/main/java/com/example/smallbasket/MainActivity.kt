package com.example.smallbasket

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import com.example.smallbasket.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        lateinit var auth: FirebaseAuth
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, Homepage::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Floating animation for logo + DOZO + tagline
        val floatAnim = AnimationUtils.loadAnimation(this, R.anim.float_up_down)
        binding.imgLogo.startAnimation(floatAnim)
        binding.tvAppTitle.startAnimation(floatAnim)
        binding.tvTagline.startAnimation(floatAnim)

        // Swipe hint pulse animation starts after 1.5s
        binding.tvSwipeHint.postDelayed({
            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.tvSwipeHint.startAnimation(pulse)
        }, 1500)

        // Listen for motion transition completion
        binding.motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {}

            override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) {
                // Fade out swipe hint as user swipes
                binding.tvSwipeHint.alpha = 1f - progress
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                // Stop floating animation when transition completes
                if (currentId == binding.motionLayout.endState) {
                    binding.imgLogo.clearAnimation()
                    binding.tvAppTitle.clearAnimation()
                    binding.tvTagline.clearAnimation()
                }
            }

            override fun onTransitionTrigger(motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float) {}
        })

        // Buttons click
        binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
        binding.btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}