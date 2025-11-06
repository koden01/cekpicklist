package com.example.cekpicklist

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.cekpicklist.utils.PlaySoundThread
import com.example.cekpicklist.utils.RfidScanManager
import com.example.cekpicklist.utils.UIHelper
import com.example.cekpicklist.view.CircleSeekBar
import com.example.cekpicklist.view.UhfLocationCanvasView
import com.rscja.deviceapi.interfaces.IUHFLocationCallback

/**
 * Duplikasi dari UHFLocationFragment.java dari demo UHF
 * Menggunakan komponen yang sama persis dengan demo
 */
class LocationDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LocationDemoActivity"
    }

    private lateinit var llChart: UhfLocationCanvasView
    private lateinit var etEPC: EditText
    private lateinit var btStart: Button
    private lateinit var btStop: Button
    private lateinit var btSettings: Button
    private lateinit var seekBarPower: CircleSeekBar
    private lateinit var playSoundThread: PlaySoundThread
    private lateinit var rfidScanManager: RfidScanManager
    
    private var progress = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_demo)
        
        initUHF()
        initViews()
        setupClickListeners()
        setupSeekBar()
        
        // Clean chart initially
        llChart.clean()
        
        // Prefill target EPC from intent and auto-start
        intent.getStringExtra("target_epc")?.let { prefill ->
            etEPC.setText(prefill)
            // Auto start locating this EPC
            startLocation()
        }
    }
    
    /**
     * Initialize UHF reader menggunakan RfidScanManager yang sudah ada
     */
    private fun initUHF() {
        try {
            // Initialize RFID manager seperti aplikasi yang sudah ada
            rfidScanManager = RfidScanManager(this, this)
            
            // Set power level default seperti demo UHF
            rfidScanManager.setCustomPower(25, -80) // Default seperti demo UHF
            Log.d(TAG, "✅ RFID manager initialized successfully with DEMO UHF DEFAULT (Power=25, RSSI=-80)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing RFID manager: ${e.message}")
        }
        
        // Initialize sound thread
        playSoundThread = PlaySoundThread(this)
        playSoundThread.start()
    }

    private fun initViews() {
        llChart = findViewById(R.id.llChart)
        etEPC = findViewById(R.id.etEPC)
        btStart = findViewById(R.id.btStart)
        btStop = findViewById(R.id.btStop)
        btSettings = findViewById(R.id.btSettings)
        seekBarPower = findViewById(R.id.seekBarPower)
    }

    private fun setupClickListeners() {
        btStart.setOnClickListener {
            startLocation()
        }

        btStop.setOnClickListener {
            stopLocation()
        }

        btSettings.setOnClickListener {
            navigateToSettings()
        }
    }
    
    private fun navigateToSettings() {
        Log.d(TAG, "🔥 Settings button clicked - navigasi ke SettingsActivity")
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun setupSeekBar() {
        seekBarPower.isEnabled = false
        seekBarPower.progress = 5
        
        seekBarPower.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress2: Int, fromUser: Boolean) {
                Log.d(TAG, "progress = $progress2")
                progress = progress2
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "onStartTrackingTouch")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = 35 - progress
                rfidScanManager.setDynamicDistance(p)
                Log.d(TAG, "onStopTrackingTouch p=$p progress=$progress")
            }
        })
    }

    private fun startLocation() {
        val epc = etEPC.text.toString()
        if (epc.isEmpty()) {
            UIHelper.ToastMessage(this, R.string.location_fail)
            return
        }

        // Load power level dan RSSI dari Settings (SharedPreferences)
        val sharedPreferences = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
        val powerLevel = sharedPreferences.getInt("power_level", 25)      // Default: 25 (seperti demo UHF)
        val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -80) // Default: -80 (seperti demo UHF)
        
        rfidScanManager.setCustomPower(powerLevel, rssiThreshold)
        Log.d(TAG, "🔥 Settings Loaded: Power=$powerLevel, RSSI=$rssiThreshold (Demo UHF Default)")

        val callback = object : IUHFLocationCallback {
            override fun getLocationValue(value: Int, valid: Boolean) {
                llChart.setData(value)
                if (valid) {
                    playSoundThread.playSoundDelayed(value)
                }
            }
        }

        val result = rfidScanManager.startLocation(epc, callback)
        if (!result) {
            UIHelper.ToastMessage(this, R.string.psam_msg_fail)
            return
        }

        seekBarPower.isEnabled = true
        btStart.isEnabled = false
        etEPC.isEnabled = false
    }

    private fun stopLocation() {
        rfidScanManager.stopLocation()
        btStart.isEnabled = true
        etEPC.isEnabled = true
        seekBarPower.isEnabled = false
        seekBarPower.progress = 5
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isTrigger = keyCode == 139 || keyCode == 280 || keyCode == 291 || 
                       keyCode == 293 || keyCode == 294 || keyCode == 311 || 
                       keyCode == 312 || keyCode == 313 || keyCode == 315
        
        if (!isTrigger) return super.onKeyDown(keyCode, event)
        
        if (event?.repeatCount == 0) {
            if (btStart.isEnabled) {
                startLocation()
            } else {
                stopLocation()
            }
        }
        return true
    }


    override fun onDestroy() {
        super.onDestroy()
        stopLocation()
        playSoundThread.release()
        
        // Cleanup RFID manager
        try {
            rfidScanManager.cleanup()
            Log.d(TAG, "✅ RFID manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up RFID manager: ${e.message}")
        }
        
        Log.i(TAG, "onDestroy")
    }
}
