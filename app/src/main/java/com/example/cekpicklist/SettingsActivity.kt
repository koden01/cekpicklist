package com.example.cekpicklist

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.cekpicklist.databinding.ActivitySettingsBinding
import com.example.cekpicklist.utils.ToastUtils

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "RFIDSettings"
        private const val KEY_POWER_LEVEL = "power_level"
        private const val KEY_RSSI_THRESHOLD = "rssi_threshold"
        private const val DEFAULT_POWER_LEVEL = 20 // Default power level 20
        private const val DEFAULT_RSSI_THRESHOLD = -70 // Default RSSI threshold -70 dBm
        private const val MIN_POWER_LEVEL = 1
        private const val MAX_POWER_LEVEL = 30
        private const val MIN_RSSI_THRESHOLD = -100 // Minimum RSSI threshold
        private const val MAX_RSSI_THRESHOLD = -30  // Maximum RSSI threshold
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("SettingsActivity", "🔥 SettingsActivity onCreate dimulai")
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        setupToolbar()
        setupPowerSlider()
        setupRSSISlider()
        // setupButtons() - dihilangkan
        
        // Hapus RSSI Debug Mode dari SharedPreferences jika ada
        removeRSSIDebugMode()
        
        Log.d("SettingsActivity", "🔥 SettingsActivity onCreate selesai")
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Settings RFID"
        }
        
        binding.toolbar.setNavigationOnClickListener {
            Log.d("SettingsActivity", "🔥 Back button ditekan")
            finish()
        }
    }
    
    private fun setupPowerSlider() {
        // Load saved power level
        val savedPowerLevel = sharedPreferences.getInt(KEY_POWER_LEVEL, DEFAULT_POWER_LEVEL)
        
        // Set initial values
        binding.seekBarPower.apply {
            max = MAX_POWER_LEVEL - MIN_POWER_LEVEL // 29 (0-28 for seekbar, +1 for actual power)
            progress = savedPowerLevel - MIN_POWER_LEVEL
        }
        
        binding.tvPowerValue.text = "$savedPowerLevel"
        binding.tvPowerDescription.text = getPowerDescription(savedPowerLevel)
        
        // Setup seekbar listener dengan auto-apply
        binding.seekBarPower.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val powerLevel = progress + MIN_POWER_LEVEL // Convert to actual power level (1-30)
                binding.tvPowerValue.text = "$powerLevel"
                binding.tvPowerDescription.text = getPowerDescription(powerLevel)
                
                // Auto-apply saat perubahan
                if (fromUser) {
                    savePowerLevel(powerLevel)
                    Log.d("SettingsActivity", "🔥 Power level auto-applied: $powerLevel")
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Not needed
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Not needed - sudah auto-apply di onProgressChanged
            }
        })
    }
    
    /**
     * Hapus RSSI Debug Mode dari SharedPreferences
     */
    private fun removeRSSIDebugMode() {
        try {
            if (sharedPreferences.contains("rssi_debug_mode")) {
                sharedPreferences.edit().remove("rssi_debug_mode").apply()
                Log.d("SettingsActivity", "🔥 RSSI Debug Mode dihapus dari SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "🔥 Error removing RSSI Debug Mode: ${e.message}")
        }
    }
    
    private fun setupRSSISlider() {
        // Load saved RSSI threshold
        val savedRSSIThreshold = sharedPreferences.getInt(KEY_RSSI_THRESHOLD, DEFAULT_RSSI_THRESHOLD)
        
        // Set initial values
        binding.seekBarRSSI.apply {
            max = MAX_RSSI_THRESHOLD - MIN_RSSI_THRESHOLD // 70 (0-70 for seekbar)
            progress = savedRSSIThreshold - MIN_RSSI_THRESHOLD
        }
        
        binding.tvRSSIValue.text = "$savedRSSIThreshold dBm"
        binding.tvRSSIDescription.text = getRSSIDescription(savedRSSIThreshold)
        
        // Setup seekbar listener dengan auto-apply
        binding.seekBarRSSI.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rssiThreshold = progress + MIN_RSSI_THRESHOLD // Convert to actual RSSI threshold (-100 to -30)
                binding.tvRSSIValue.text = "$rssiThreshold dBm"
                binding.tvRSSIDescription.text = getRSSIDescription(rssiThreshold)
                
                // Auto-apply saat perubahan
                if (fromUser) {
                    saveRSSIThreshold(rssiThreshold)
                    Log.d("SettingsActivity", "🔥 RSSI threshold auto-applied: $rssiThreshold dBm")
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Not needed
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Not needed - sudah auto-apply di onProgressChanged
            }
        })
    }
    
    /**
     * Get current power level from RFID reader
     */
    private fun getCurrentPowerLevel(): Int {
        return try {
            // TODO: Get power level from RFID reader
            // val currentPower = mReader.getPower()
            // return if (currentPower > 0) currentPower else DEFAULT_POWER_LEVEL
            
            // For now, return saved value
            sharedPreferences.getInt(KEY_POWER_LEVEL, DEFAULT_POWER_LEVEL)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "🔥 Error getting current power level: ${e.message}", e)
            DEFAULT_POWER_LEVEL
        }
    }
    
    // setupButtons() dihilangkan
    
    private fun getPowerDescription(powerLevel: Int): String {
        return when {
            powerLevel <= 5 -> "Sangat Rendah - Jangkauan sangat dekat, hemat baterai"
            powerLevel <= 10 -> "Rendah - Jangkauan dekat, hemat baterai"
            powerLevel <= 15 -> "Sedang - Jangkauan menengah, seimbang"
            powerLevel <= 20 -> "Tinggi - Jangkauan jauh, konsumsi baterai tinggi"
            powerLevel <= 25 -> "Sangat Tinggi - Jangkauan sangat jauh"
            else -> "Maksimal - Jangkauan maksimal, konsumsi baterai tinggi"
        }
    }
    
    private fun getRSSIDescription(rssiThreshold: Int): String {
        return when {
            rssiThreshold <= -90 -> "Sangat Longgar - Hampir semua tag terdeteksi termasuk yang sinyalnya sangat lemah"
            rssiThreshold <= -80 -> "Longgar - Tag dengan sinyal lemah (RSSI rendah) ke atas yang terdeteksi"
            rssiThreshold <= -70 -> "Sedang - Tag dengan sinyal sedang (RSSI sedang) ke atas yang terdeteksi"
            rssiThreshold <= -60 -> "Ketat - Hanya tag dengan sinyal kuat (RSSI tinggi) yang terdeteksi"
            rssiThreshold <= -50 -> "Sangat Ketat - Hanya tag dengan sinyal sangat kuat (RSSI tinggi) yang terdeteksi"
            else -> "Maksimal Ketat - Hanya tag dengan sinyal maksimal yang terdeteksi"
        }
    }
    
    private fun savePowerLevel(powerLevel: Int) {
        sharedPreferences.edit().apply {
            putInt(KEY_POWER_LEVEL, powerLevel)
            apply()
        }
        Log.d("SettingsActivity", "🔥 Power level disimpan: $powerLevel")
    }
    
    private fun saveRSSIThreshold(rssiThreshold: Int) {
        sharedPreferences.edit().apply {
            putInt(KEY_RSSI_THRESHOLD, rssiThreshold)
            apply()
        }
        Log.d("SettingsActivity", "🔥 RSSI threshold disimpan: $rssiThreshold dBm")
    }
    
    // resetToDefault() dan applySettings() dihilangkan - menggunakan auto-apply
    
    override fun onBackPressed() {
        Log.d("SettingsActivity", "🔥 Back pressed")
        super.onBackPressed()
    }
}
