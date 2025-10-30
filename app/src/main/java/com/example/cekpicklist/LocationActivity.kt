package com.example.cekpicklist

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
// import com.example.cekpicklist.databinding.ActivityLocationBinding
import com.example.cekpicklist.view.LocationView
import com.example.cekpicklist.viewmodel.LocationViewModel
import com.rscja.deviceapi.interfaces.IUHFLocationCallback

class LocationActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LocationActivity"
    }
    
    // private lateinit var binding: ActivityLocationBinding
    private lateinit var viewModel: LocationViewModel
    private lateinit var locationView: LocationView
    private lateinit var etTargetEpc: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var seekBarPower: SeekBar
    
    private var isScanning = false
    private var targetEpc = ""
    
    // Use existing RfidScanManager instead of direct RFID reader
    private lateinit var rfidScanManager: com.example.cekpicklist.utils.RfidScanManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        
        // Initialize RfidScanManager (reuse existing instance)
        initRfidScanManager()
        
        setupToolbar()
        setupViews()
        setupViewModel()
        setupClickListeners()
        
        Log.d(TAG, "🎯 LocationActivity created")
    }
    
    /**
     * Initialize RfidScanManager - Reuse existing instance to avoid conflicts
     */
    private fun initRfidScanManager() {
        try {
            // Get existing RfidScanManager instance from the main activity
            // This avoids conflicts with multiple RFID reader instances
            rfidScanManager = com.example.cekpicklist.utils.RfidScanManager(this, this)
            Log.d(TAG, "✅ RfidScanManager initialized successfully")
            Log.d(TAG, "📡 Using existing RFID reader instance to avoid conflicts")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing RfidScanManager: ${e.message}", e)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Location Finder"
    }
    
    private fun setupViews() {
        locationView = findViewById(R.id.locationView)
        etTargetEpc = findViewById(R.id.etTargetEpc)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        seekBarPower = findViewById(R.id.seekBarPower)
        
        // Setup seekbar
        seekBarPower.isEnabled = false
        seekBarPower.progress = 5   // Default to maximum range (power = 5)
        seekBarPower.max = 30
        seekBarPower.min = 5
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[LocationViewModel::class.java]
        
        // Observe location data
        viewModel.locationValue.observe(this) { value ->
            Log.d(TAG, "🎯 Location value updated: $value")
            locationView.setLocationData(value, true)
        }
        
        // Observe scanning state
        viewModel.isScanning.observe(this) { scanning ->
            isScanning = scanning
            updateUI()
        }
    }
    
    private fun setupClickListeners() {
        btnStart.setOnClickListener {
            startLocation()
        }
        
        btnStop.setOnClickListener {
            stopLocation()
        }
        
        seekBarPower.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.d(TAG, "🎯 Power progress changed: $progress")
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "🎯 Power tracking started")
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // OPTION 1: Formula inversi (seperti sekarang)
                // val power = 35 - seekBarPower.progress
                // Progress 5 → Power 30 (range maksimal)
                // Progress 30 → Power 5 (range minimal)
                
                // OPTION 2: Formula searah (lebih intuitif)
                val power = seekBarPower.progress
                // Progress 5 → Power 5 (range minimal)
                // Progress 30 → Power 30 (range maksimal)
                
                setRfidPower(power)
                Log.d(TAG, "🎯 Dynamic distance set to: $power (seekbar: ${seekBarPower.progress})")
            }
        })
    }
    
    private fun startLocation() {
        if (isScanning) return
        
        val inputText = etTargetEpc.text.toString().trim()
        
        if (inputText.isEmpty()) {
            Toast.makeText(this, "Masukkan target EPC", Toast.LENGTH_SHORT).show()
            return
        }
        
        targetEpc = inputText
        
        Log.d(TAG, "🎯 Starting location for EPC: $targetEpc")
        
        // Clear previous data
        locationView.clear()
        
        // Start location with RFID reader directly - like demo
        val success = startRfidLocation(targetEpc)
        
        if (success) {
            // Enable power control
            seekBarPower.isEnabled = true
            
            // Set initial power (dynamic distance) to 5 for maximum range
            // Note: Dynamic distance 5 = minimum distance = maximum range
            val initialPower = 5
            setRfidPower(initialPower)
            Log.d(TAG, "🎯 Initial power set to: $initialPower (maximum range)")
            
            // Update UI
            updateUI()
            
            Log.d(TAG, "🎯 Location started successfully")
            val message = "Location started - searching for: $targetEpc"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this, 
                "❌ Gagal memulai location\n\n" +
                "Requirements:\n" +
                "• RFID Hardware Scanner harus terhubung\n" +
                "• RFID Reader harus terinisialisasi dengan benar\n" +
                "• Periksa koneksi device dan permissions", 
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "❌ Failed to start location")
        }
    }
    
    /**
     * Start RFID Location - Using RfidScanManager to avoid conflicts
     */
    private fun startRfidLocation(targetEpc: String): Boolean {
        return try {
            Log.d(TAG, "📡 Starting RFID location for EPC: $targetEpc")
            Log.d(TAG, "📡 Using RfidScanManager to avoid conflicts")
            
            val callback = object : IUHFLocationCallback {
                override fun getLocationValue(value: Int, valid: Boolean) {
                    Log.d(TAG, "📊 RFID location data received: value=$value, valid=$valid")
                    
                    // Update location view
                    runOnUiThread {
                        locationView.setLocationData(value, valid)
                    }
                }
            }
            
            Log.d(TAG, "📡 Calling rfidScanManager.startLocation()...")
            val result = rfidScanManager.startLocation(targetEpc, callback)
            
            Log.d(TAG, "📡 startLocation() returned: $result")
            
            if (result) {
                isScanning = true
                Log.d(TAG, "✅ RFID location started successfully")
            } else {
                Log.e(TAG, "❌ Failed to start RFID location")
                Log.e(TAG, "   Possible causes:")
                Log.e(TAG, "   - RFID hardware not connected")
                Log.e(TAG, "   - Invalid EPC format")
                Log.e(TAG, "   - Hardware not initialized properly")
                Log.e(TAG, "   - Insufficient permissions")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting RFID location: ${e.message}", e)
            Log.e(TAG, "   Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "   Stack trace:", e)
            false
        }
    }
    
    /**
     * Stop RFID Location - Using RfidScanManager to avoid conflicts
     */
    private fun stopRfidLocation(): Boolean {
        return try {
            Log.d(TAG, "📡 Calling rfidScanManager.stopLocation()...")
            val result = rfidScanManager.stopLocation()
            
            if (result) {
                isScanning = false
                Log.d(TAG, "✅ RFID location stopped successfully")
            } else {
                Log.e(TAG, "❌ Failed to stop RFID location")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping RFID location: ${e.message}", e)
            false
        }
    }
    
    /**
     * Set RFID Power - Using RfidScanManager to avoid conflicts
     */
    private fun setRfidPower(power: Int) {
        try {
            Log.d(TAG, "📡 Calling rfidScanManager.setDynamicDistance($power)...")
            val result = rfidScanManager.setDynamicDistance(power)
            
            if (result) {
                Log.d(TAG, "✅ RFID power set to: $power")
            } else {
                Log.e(TAG, "❌ Failed to set RFID power: $power")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting RFID power: ${e.message}", e)
        }
    }
    
    private fun stopLocation() {
        if (!isScanning) return
        
        Log.d(TAG, "🎯 Stopping location")
        
        // Stop RFID location
        val success = stopRfidLocation()
        
        if (success) {
            // Disable power control
            seekBarPower.isEnabled = false
            seekBarPower.progress = 5   // Reset to maximum range (power = 5)
            
            // Clear location data
            locationView.clear()
            
            // Update UI
            updateUI()
            
            Log.d(TAG, "🎯 Location stopped successfully")
        } else {
            Toast.makeText(this, "Gagal menghentikan location", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ Failed to stop location")
        }
    }
    
    private fun updateUI() {
        btnStart.isEnabled = !isScanning
        btnStop.isEnabled = isScanning
        etTargetEpc.isEnabled = !isScanning
        
        btnStart.text = if (isScanning) "Scanning..." else "Start"
        btnStop.text = "Stop"
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.location_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_clear -> {
                clearLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun clearLocationData() {
        Log.d(TAG, "🎯 Clearing location data")
        locationView.clear()
        viewModel.clearLocationData()
        Toast.makeText(this, "Location data cleared", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPause() {
        super.onPause()
        if (isScanning) {
            stopLocation()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop location if running
        if (isScanning) {
            stopLocation()
        }
        
        // RfidScanManager will handle RFID reader cleanup
        Log.d(TAG, "📡 RfidScanManager will handle RFID reader cleanup")
        
        Log.d(TAG, "🎯 LocationActivity destroyed")
    }
}
