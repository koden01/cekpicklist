package com.example.cekpicklist

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cekpicklist.adapter.BarcodeViewPagerAdapter
import com.example.cekpicklist.utils.BuiltInBarcodeManager
import com.example.cekpicklist.utils.BarcodeAudioManager
import com.example.cekpicklist.viewmodel.BarcodeScannerViewModel
import com.example.cekpicklist.fragment.BarcodeSoundSettingsFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import kotlinx.coroutines.launch

class BarcodeScannerTabsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BarcodeScannerTabsActivity"
    }

    private lateinit var viewModel: BarcodeScannerViewModel
    private lateinit var builtInBarcodeManager: BuiltInBarcodeManager
    private lateinit var audioManager: BarcodeAudioManager
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPagerAdapter: BarcodeViewPagerAdapter
    
    // Navbar components (like web app)
    private lateinit var navInput: LinearLayout
    private lateinit var navDashboard: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var contentFrame: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner_tabs)
        
        setupToolbar()
        setupViewModel()
        setupAudioManager()
        setupBuiltInBarcodeManager()
        setupNavbar()
        
        Log.d(TAG, "📱 BarcodeScannerTabsActivity created")
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Barcode Scanner"
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[BarcodeScannerViewModel::class.java]
        
        // Initialize background sync
        viewModel.initializeBackgroundSync()
        
        Log.d(TAG, "✅ ViewModel initialized")
    }
    
    private fun setupAudioManager() {
        audioManager = BarcodeAudioManager(this)
        audioManager.initAudio()
        Log.d(TAG, "🔊 Audio manager initialized")
    }

    private fun setupNavbar() {
        // Initialize navbar components (like web app)
        navInput = findViewById(R.id.navInput)
        navDashboard = findViewById(R.id.navDashboard)
        navHistory = findViewById(R.id.navHistory)
        contentFrame = findViewById(R.id.contentFrame)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        
        // Setup SwipeRefreshLayout
        setupSwipeRefresh()
        
        // Set click listeners for navbar items
        navInput.setOnClickListener { switchToTab(0) }
        navDashboard.setOnClickListener { switchToTab(1) }
        navHistory.setOnClickListener { switchToTab(2) }
        
        // Set initial selection
        switchToTab(0)
        
        Log.d(TAG, "🧭 Navbar setup completed")
    }

    /**
     * Setup SwipeRefreshLayout untuk refresh data
     */
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "🔄 Swipe refresh triggered")
            forceRefreshData()
        }
        
        // Set warna refresh indicator
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
        
        Log.d(TAG, "✅ SwipeRefreshLayout setup completed")
    }

    /**
     * Force refresh data dari Supabase
     */
    private fun forceRefreshData() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔄 Force refreshing barcode data...")
                
                val success = viewModel.forceRefreshData()
                if (success) {
                    Toast.makeText(this@BarcodeScannerTabsActivity, "✅ Data berhasil di-refresh", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "✅ Force refresh completed successfully")
                } else {
                    Toast.makeText(this@BarcodeScannerTabsActivity, "❌ Gagal refresh data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error force refreshing data: ${e.message}", e)
                Toast.makeText(this@BarcodeScannerTabsActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // Stop refresh indicator
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun switchToTab(position: Int) {
        // Update navbar selection (like web app)
        navInput.isSelected = (position == 0)
        navDashboard.isSelected = (position == 1)
        navHistory.isSelected = (position == 2)
        
        // Switch fragment
        when (position) {
            0 -> showFragment(com.example.cekpicklist.fragment.BarcodeInputFragment())
            1 -> showFragment(com.example.cekpicklist.fragment.BarcodeDashboardFragment())
            2 -> showFragment(com.example.cekpicklist.fragment.BarcodeHistoryFragment())
        }
        
        Log.d(TAG, "🔄 Switched to tab: $position")
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment)
            .commit()
    }

    private fun setupBuiltInBarcodeManager() {
        Log.d(TAG, "🔧 Setting up BuiltInBarcodeManager...")
        builtInBarcodeManager = BuiltInBarcodeManager(this, this)
        
        Log.d(TAG, "🔧 Setting barcode callback...")
        builtInBarcodeManager.setBarcodeCallback { barcode ->
            Log.d(TAG, "📱 Barcode detected in activity: $barcode")
            Log.d(TAG, "📱 Processing barcode with ViewModel...")
            viewModel.processScannedBarcode(barcode)
            Log.d(TAG, "✅ Barcode processing completed")
        }
        
        Log.d(TAG, "🔧 Setting scan state callback...")
        builtInBarcodeManager.setScanStateCallback { isActive ->
            Log.d(TAG, "📱 Scanner state changed: $isActive")
            isScanning = isActive
            viewModel.setScanningState(isActive)
        }
        
        Log.d(TAG, "✅ BuiltInBarcodeManager initialized successfully")
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isTrigger = keyCode == 139 || keyCode == 280 || keyCode == 291 || 
                       keyCode == 293 || keyCode == 294 || keyCode == 311 || 
                       keyCode == 312 || keyCode == 313 || keyCode == 315
        
        if (!isTrigger) return super.onKeyDown(keyCode, event)
        
        if (event?.repeatCount == 0) {
            Log.d(TAG, "🔑 KeyDown trigger: $keyCode (repeat=0) → startBarcodeScan")
            if (!isScanning) {
                startBarcodeScan()
            }
        }
        
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val isTrigger = keyCode == 139 || keyCode == 280 || keyCode == 291 || 
                       keyCode == 293 || keyCode == 294 || keyCode == 311 || 
                       keyCode == 312 || keyCode == 313 || keyCode == 315
        
        if (!isTrigger) return super.onKeyUp(keyCode, event)
        
        Log.d(TAG, "🔑 KeyUp trigger: $keyCode → stopBarcodeScan")
        if (isScanning) {
            stopBarcodeScan()
        }
        
        return true
    }

    fun startBarcodeScan() {
        Log.d(TAG, "🚀 Starting barcode scan...")
        
        try {
            val success = builtInBarcodeManager.startScan()
            if (success) {
                isScanning = true
                viewModel.setScanningState(true)
                // Play start beep
                audioManager.playStartBeep()
                Toast.makeText(this, "✅ Scanner aktif - siap untuk scan barcode", Toast.LENGTH_LONG).show()
                Log.d(TAG, "✅ Barcode scan started successfully")
            } else {
                // Play failure beep
                audioManager.playFailureBeep()
                Toast.makeText(this, "❌ Gagal mengaktifkan scanner", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "❌ Failed to start barcode scan")
            }
        } catch (e: Exception) {
            // Play failure beep
            audioManager.playFailureBeep()
            Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ Exception starting barcode scan: ${e.message}")
        }
    }

    fun stopBarcodeScan() {
        Log.d(TAG, "🛑 Stopping barcode scan...")
        
        try {
            builtInBarcodeManager.stopScan()
            isScanning = false
            viewModel.setScanningState(false)
            Log.d(TAG, "✅ Barcode scan stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception stopping barcode scan: ${e.message}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.barcode_scanner_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_scan_history -> {
                // Switch to History tab
                viewPager.currentItem = 2
                true
            }
            R.id.action_sound_settings -> {
                // Open sound settings dialog
                openSoundSettingsDialog()
                true
            }
            R.id.action_settings -> {
                // TODO: Open settings
                Toast.makeText(this, "Settings belum diimplementasi", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openSoundSettingsDialog() {
        try {
            val soundSettingsFragment = BarcodeSoundSettingsFragment()
            soundSettingsFragment.show(supportFragmentManager, "SoundSettingsDialog")
            Log.d(TAG, "🔊 Opening sound settings dialog")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening sound settings dialog: ${e.message}", e)
            Toast.makeText(this, "❌ Error opening sound settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isScanning) {
            stopBarcodeScan()
        }
        audioManager.release()
        Log.d(TAG, "📱 BarcodeScannerTabsActivity destroyed")
    }
}
