package com.example.cekpicklist

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cekpicklist.databinding.ActivitySettingsBinding
import com.example.cekpicklist.utils.RfidScanSettings

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        private const val PREFS_NAME = "RFIDSettings"  // Sama dengan RfidScanManager
        private const val KEY_POWER_LEVEL = "power_level"  // Sama dengan RfidScanManager
        private const val KEY_RSSI_THRESHOLD = "rssi_threshold"  // Sama dengan RfidScanManager
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var seekBarPowerLevel: SeekBar
    private lateinit var seekBarRssiThreshold: SeekBar
    private lateinit var tvPowerLevel: TextView
    private lateinit var tvRssiThreshold: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initViews()
        loadSettings()
        setupSeekBars()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "RFID Settings"
    }

    private fun initViews() {
        seekBarPowerLevel = findViewById(R.id.seekBarPowerLevel)
        seekBarRssiThreshold = findViewById(R.id.seekBarRssiThreshold)
        tvPowerLevel = findViewById(R.id.tvPowerLevel)
        tvRssiThreshold = findViewById(R.id.tvRssiThreshold)
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private fun loadSettings() {
        val powerLevel = sharedPreferences.getInt(KEY_POWER_LEVEL, 25)
        val rssiThreshold = sharedPreferences.getInt(KEY_RSSI_THRESHOLD, -55)
        
        seekBarPowerLevel.progress = powerLevel
        seekBarRssiThreshold.progress = rssiThreshold
        
        updatePowerLevelDisplay(powerLevel)
        updateRssiThresholdDisplay(rssiThreshold)
        
        Log.d(TAG, "Loaded settings: power=$powerLevel, rssi=$rssiThreshold")
    }

    private fun setupSeekBars() {
        // Power Level SeekBar
        seekBarPowerLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updatePowerLevelDisplay(progress)
                    savePowerLevel(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // RSSI Threshold SeekBar
        seekBarRssiThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateRssiThresholdDisplay(progress)
                    saveRssiThreshold(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

    }

    private fun updatePowerLevelDisplay(powerLevel: Int) {
        tvPowerLevel.text = "Current: $powerLevel"
        Log.d(TAG, "Power level updated: $powerLevel")
    }

    private fun updateRssiThresholdDisplay(rssiThreshold: Int) {
        tvRssiThreshold.text = "Current: $rssiThreshold dBm"
        Log.d(TAG, "RSSI threshold updated: $rssiThreshold")
    }


    private fun savePowerLevel(powerLevel: Int) {
        sharedPreferences.edit()
            .putInt(KEY_POWER_LEVEL, powerLevel)
            .apply()
        Log.d(TAG, "Power level saved: $powerLevel")
        
        // Notify RfidScanManager that settings have changed
        notifySettingsChanged()
    }

    private fun saveRssiThreshold(rssiThreshold: Int) {
        sharedPreferences.edit()
            .putInt(KEY_RSSI_THRESHOLD, rssiThreshold)
            .apply()
        Log.d(TAG, "RSSI threshold saved: $rssiThreshold")
        
        // Notify RfidScanManager that settings have changed
        notifySettingsChanged()
    }


    private fun notifySettingsChanged() {
        // Broadcast intent to notify RfidScanManager that settings have changed
        val intent = android.content.Intent("com.example.cekpicklist.SETTINGS_CHANGED")
        sendBroadcast(intent)
        Log.d(TAG, "Settings change notification sent")
    }

    private fun setupClickListeners() {
        // No click listeners needed now
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_reset_settings -> {
                resetSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun resetSettings() {
        Log.d(TAG, "Resetting settings to defaults...")
        
        // Reset to default values
        val defaultPowerLevel = 25
        val defaultRssiThreshold = -55
        
        // Update UI
        seekBarPowerLevel.progress = defaultPowerLevel
        seekBarRssiThreshold.progress = defaultRssiThreshold
        updatePowerLevelDisplay(defaultPowerLevel)
        updateRssiThresholdDisplay(defaultRssiThreshold)
        
        // Save to preferences
        savePowerLevel(defaultPowerLevel)
        saveRssiThreshold(defaultRssiThreshold)
        
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Settings reset completed: power=$defaultPowerLevel, rssi=$defaultRssiThreshold")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}