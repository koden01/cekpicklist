package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DURATION = 3000L // 3 detik
    }
    
    private lateinit var logoImageView: ImageView
    private lateinit var loadingTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        Log.d(TAG, "🎬 Splash screen started")
        
        // Initialize views
        initializeViews()
        
        // Start animations
        startAnimations()
        
        // Navigate to main activity after delay
        navigateToMainActivity()
    }
    
    private fun initializeViews() {
        logoImageView = findViewById(R.id.logoImageView)
        loadingTextView = findViewById(R.id.loadingTextView)
        
        // Set logo to the app logo
        logoImageView.setImageResource(R.mipmap.ic_launcher_foreground)
        
        // Set loading text
        loadingTextView.text = "Loading..."
    }
    
    private fun startAnimations() {
        Log.d(TAG, "🎭 Starting splash animations")
        
        // Logo animation - combined rotation and bounce
        val logoAnimation = AnimationUtils.loadAnimation(this, R.anim.logo_rotate_bounce)
        logoImageView.startAnimation(logoAnimation)
        
        // Loading text animation - fade in with delay
        val loadingAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_delayed)
        loadingTextView.startAnimation(loadingAnimation)
        
        // Start loading dots animation
        startLoadingDotsAnimation()
    }
    
    private fun startLoadingDotsAnimation() {
        val handler = Handler(Looper.getMainLooper())
        var dotCount = 0
        
        val runnable = object : Runnable {
            override fun run() {
                val dots = ".".repeat(dotCount % 4) // 0, 1, 2, 3 dots
                loadingTextView.text = "Loading$dots"
                dotCount++
                
                handler.postDelayed(this, 500) // Update every 500ms
            }
        }
        
        handler.post(runnable)
    }
    
    private fun navigateToMainActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🚀 Navigating to MainActivity")
            
            val intent = Intent(this, CekPicklistActivity::class.java)
            startActivity(intent)
            
            // Add fade transition
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            
            finish()
        }, SPLASH_DURATION)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🎬 Splash screen destroyed")
    }
}
