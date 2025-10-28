package com.example.smallbasket

import android.content.Context
import android.content.Intent
import android.os.*
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import com.example.smallbasket.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isSwipeCompleted = false
    private var lastHapticProgress = 0f
    private lateinit var vibrator: Vibrator

    // Samsung-optimized smooth haptic system
    private var lastHapticTime = 0L
    private val hapticThrottleMs = 25L // refresh every ~25 ms (≈40 Hz)

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

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Floating animation
        val floatAnim = AnimationUtils.loadAnimation(this, R.anim.float_up_down)
        binding.imgLogo.startAnimation(floatAnim)

        // Swipe hint animation
        binding.tvSwipeHint.postDelayed({
            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.tvSwipeHint.startAnimation(pulse)
        }, 1500)

        // MotionLayout transitions
        binding.motionLayout.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {
                playImpactLight()
            }

            override fun onTransitionChange(
                motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float
            ) {
                binding.tvSwipeHint.alpha = 1f - progress

                // ---------- Ultra-Smooth Samsung Swipe Haptics ----------
                val now = System.currentTimeMillis()
                if (now - lastHapticTime > hapticThrottleMs) {
                    // Slightly softer amplitude curve for Samsung linear motor
                    val amplitude = (progress * 150 + 40).toInt().coerceIn(30, 180)
                    val duration = (4 + progress * 4).toLong() // 4–8 ms burst
                    playMicroPulse(duration, amplitude)
                    lastHapticTime = now
                }

                // Gradual intro card reveal
                if (progress > 0.5f) {
                    if (binding.cardIntro.visibility != View.VISIBLE)
                        binding.cardIntro.visibility = View.VISIBLE
                } else if (binding.cardIntro.visibility == View.VISIBLE) {
                    binding.cardIntro.visibility = View.GONE
                }
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                if (currentId == binding.motionLayout.endState) {
                    if (!isSwipeCompleted) {
                        isSwipeCompleted = true
                        playSuccess()

                        binding.imgLogo.clearAnimation()
                        binding.tvTagline.visibility = View.VISIBLE
                        binding.tvTagline.animate().alpha(1f)
                            .setDuration(800)
                            .setStartDelay(300)
                            .start()

                        binding.cardIntro.animate()
                            .scaleX(1.02f).scaleY(1.02f)
                            .setDuration(150)
                            .withEndAction {
                                binding.cardIntro.animate()
                                    .scaleX(1f).scaleY(1f)
                                    .setDuration(150).start()
                            }.start()

                        binding.btnGoToRegister.isEnabled = true
                        binding.btnGoToLogin.isEnabled = true
                        binding.btnGoToLogin.isClickable = true
                    }
                } else {
                    if (isSwipeCompleted) {
                        isSwipeCompleted = false
                        lastHapticProgress = 0f
                        playImpactLight()

                        val floatAnim =
                            AnimationUtils.loadAnimation(this@MainActivity, R.anim.float_up_down)
                        binding.imgLogo.startAnimation(floatAnim)

                        binding.tvTagline.visibility = View.GONE
                        binding.tvTagline.alpha = 0f

                        binding.btnGoToRegister.isEnabled = false
                        binding.btnGoToLogin.isEnabled = false
                        binding.btnGoToLogin.isClickable = false

                        binding.tvSwipeHint.alpha = 1f
                    }
                }
            }

            override fun onTransitionTrigger(
                motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float
            ) {}
        })

        // Buttons
        binding.btnGoToRegister.setOnClickListener {
            if (isSwipeCompleted) {
                playSelectionTick()
                startActivity(Intent(this, RegisterActivity::class.java))
                finish()
            }
        }

        binding.btnGoToLogin.setOnClickListener {
            if (isSwipeCompleted) {
                playSelectionTick()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    // ---------- Haptic Presets ----------

    private fun playImpactLight() = playWaveform(longArrayOf(0, 10), intArrayOf(0, 90))
    private fun playImpactMedium() = playWaveform(longArrayOf(0, 14), intArrayOf(0, 140))
    private fun playImpactHeavy() = playWaveform(longArrayOf(0, 20), intArrayOf(0, 180))
    private fun playSelectionTick() = playWaveform(longArrayOf(0, 6, 12), intArrayOf(0, 80, 0))
    private fun playSuccess() = playWaveform(
        longArrayOf(0, 8, 40, 10),
        intArrayOf(0, 120, 0, 160)
    )

    // Core micro-pulse generator
    private fun playMicroPulse(duration: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, duration, 6),
                intArrayOf(0, amplitude, 0),
                -1
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    // Core waveform helper
    private fun playWaveform(timings: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings.sum())
        }
    }
}
