package com.example.cekpicklist

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import com.example.cekpicklist.utils.BarcodeAudioManager
import com.example.cekpicklist.viewmodel.BarcodeScannerViewModel
import com.example.cekpicklist.fragment.BarcodeSoundSettingsFragment
import com.example.cekpicklist.cache.BarcodeCacheManager
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
    private lateinit var audioManager: BarcodeAudioManager
    
    // Getter untuk audio manager (dapat digunakan oleh Fragment)
    fun getAudioManager(): BarcodeAudioManager {
        return audioManager
    }
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPagerAdapter: BarcodeViewPagerAdapter
    
    // Navbar components (like web app)
    private lateinit var navInput: LinearLayout
    private lateinit var navDashboard: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var contentFrame: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var scannerBroadcastReceiver: BroadcastReceiver? = null
    private val useBroadcastInput: Boolean = true
    private val isBroadcastEnabled: Boolean = true
    // One-shot latch: izinkan SATU broadcast setelah KeyUp 293, lalu reset
    @Volatile private var allowNextBroadcast: Boolean = false
    private var lastAutoRefreshMs: Long = 0L
    private val AUTO_REFRESH_INTERVAL_MS = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner_tabs)
        
        setupToolbar()
        setupViewModel()
        setupAudioManager()
        setupScannerBroadcast()
        setupNavbar()
        
        Log.d(TAG, "📱 BarcodeScannerTabsActivity created")
    }

    /**
     * Setup dynamic BroadcastReceiver untuk menerima hasil scan dari scanner
     * Action: com.scanner.broadcast
     * Extra key: "data"
     */
    private fun setupScannerBroadcast() {
        scannerBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.scanner.broadcast") {
                    val barcode = intent.getStringExtra("data")?.trim().orEmpty()
                    if (barcode.isNotEmpty()) {
                        if (allowNextBroadcast) {
                            Log.d(TAG, "📡 Broadcast scan received (one-shot latch): $barcode")
                            allowNextBroadcast = false
                            viewModel.processScannedBarcode(barcode)
                        } else {
                            Log.d(TAG, "⚠️ Broadcast ignored (latch not set)")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Broadcast received without 'data' extra")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register broadcast receiver
        if (isBroadcastEnabled && scannerBroadcastReceiver != null) {
            val filter = IntentFilter("com.scanner.broadcast")
            registerReceiver(scannerBroadcastReceiver, filter)
            Log.d(TAG, "✅ Scanner broadcast receiver registered")
        }

        // **OPTIMASI**: Auto-refresh ringan saat kembali ke Activity (debounce 30 detik)
        // Hanya refresh jika cache expired, tidak perlu force refresh jika cache masih valid
        val now = System.currentTimeMillis()
        val cacheStillValid = BarcodeCacheManager.isResiCacheValid() && 
                             BarcodeCacheManager.isExpedisiCacheValid() &&
                             BarcodeCacheManager.getAllResiRecords().isNotEmpty() &&
                             BarcodeCacheManager.getAllExpedisiRecords().isNotEmpty()
        
        if (now - lastAutoRefreshMs > AUTO_REFRESH_INTERVAL_MS && !cacheStillValid) {
            lastAutoRefreshMs = now
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "🔄 Auto-refresh onResume (cache expired)")
                    viewModel.forceRefreshData()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Auto-refresh failed: ${e.message}")
                }
            }
        } else if (cacheStillValid) {
            Log.d(TAG, "⚡ Cache still valid, skipping auto-refresh onResume")
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister broadcast receiver
        if (isBroadcastEnabled && scannerBroadcastReceiver != null) {
            try {
                unregisterReceiver(scannerBroadcastReceiver)
                Log.d(TAG, "🛑 Scanner broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error unregistering receiver: ${e.message}")
            }
        }
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
        
        // Initialize expedisi validator (PENTING: untuk validasi NOT_FOUND)
        viewModel.initializeExpedisiValidator(this)
        
        Log.d(TAG, "✅ ViewModel initialized")
    }
    
    private fun setupAudioManager() {
        // **PERBAIKAN**: Inisialisasi audio manager di Activity (bukan di Fragment)
        // Audio manager harus ready SEBELUM scan terjadi, jadi diinisialisasi saat Activity dibuka
        audioManager = BarcodeAudioManager(this)
        audioManager.initAudio()
        Log.d(TAG, "🔊 Audio manager initialized at Activity onCreate (ready before first scan)")
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
        navInput.setOnClickListener { 
            Log.d(TAG, "🔍 NavInput clicked - switching to tab 0")
            switchToTab(0) 
        }
        // Dashboard tab dihapus, arahkan tab kedua ke History
        navDashboard.visibility = View.GONE
        navHistory.setOnClickListener { 
            Log.d(TAG, "🔍 NavHistory clicked - switching to tab 1")
            switchToTab(1) 
        }
        
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

    private var currentTabPosition = -1  // Track current tab to prevent unnecessary switches
    
    private fun switchToTab(position: Int) {
        // Skip jika sudah di tab yang sama
        if (currentTabPosition == position) {
            Log.d(TAG, "⚠️ Already on tab $position, skipping switch")
            return
        }
        
        // Log stack trace untuk debug (dapat di-disable jika tidak diperlukan)
        if (currentTabPosition != -1) {  // Jangan log saat initial setup
            Log.d(TAG, "🔄 switchToTab($position) called from:")
            Log.d(TAG, "📋 Stack trace: ${Thread.currentThread().stackTrace.take(5).joinToString("\n") { it.toString() }}")
        }
        
        currentTabPosition = position
        
        // Update navbar selection (like web app)
        val inputSelected = (position == 0)
        val historySelected = (position == 1)
        
        navInput.isSelected = inputSelected
        navDashboard.isSelected = false
        navHistory.isSelected = historySelected
        
        // Update text dan icon color secara programmatic
        // Input tab
        val inputText = navInput.getChildAt(1) as? android.widget.TextView
        val inputIcon = navInput.getChildAt(0) as? android.widget.ImageView
        val inputColor = if (inputSelected) android.graphics.Color.parseColor("#4CAF50") else android.graphics.Color.WHITE
        inputText?.setTextColor(inputColor)
        inputIcon?.setColorFilter(inputColor)
        
        // History tab
        val historyText = navHistory.getChildAt(1) as? android.widget.TextView
        val historyIcon = navHistory.getChildAt(0) as? android.widget.ImageView
        val historyColor = if (historySelected) android.graphics.Color.parseColor("#4CAF50") else android.graphics.Color.WHITE
        historyText?.setTextColor(historyColor)
        historyIcon?.setColorFilter(historyColor)
        
        // Dashboard tab (hidden, tetap putih)
        val dashboardText = navDashboard.getChildAt(1) as? android.widget.TextView
        val dashboardIcon = navDashboard.getChildAt(0) as? android.widget.ImageView
        dashboardText?.setTextColor(android.graphics.Color.WHITE)
        dashboardIcon?.setColorFilter(android.graphics.Color.WHITE)
        
        // Refresh drawable state untuk background
        navInput.refreshDrawableState()
        navDashboard.refreshDrawableState()
        navHistory.refreshDrawableState()
        
        // Switch fragment
        when (position) {
            0 -> showFragment(com.example.cekpicklist.fragment.BarcodeInputFragment())
            1 -> showFragment(com.example.cekpicklist.fragment.BarcodeHistoryFragment())
        }
        
        Log.d(TAG, "🔄 Switched to tab: $position")
    }

    private var currentFragment: Fragment? = null
    
    private fun showFragment(fragment: Fragment) {
        // Cek apakah fragment sudah ditampilkan untuk mencegah recreation yang tidak perlu
        val fragmentTag = when (fragment) {
            is com.example.cekpicklist.fragment.BarcodeInputFragment -> "BarcodeInputFragment"
            is com.example.cekpicklist.fragment.BarcodeHistoryFragment -> "BarcodeHistoryFragment"
            else -> null
        }
        
        val existingFragment = fragmentTag?.let { 
            supportFragmentManager.findFragmentByTag(it) 
        }
        
        if (existingFragment != null && existingFragment.isVisible && existingFragment == currentFragment) {
            // Fragment sudah ditampilkan dan sama, tidak perlu replace
            Log.d(TAG, "⚠️ Fragment $fragmentTag already visible, skipping replace")
            return
        }
        
        // Replace fragment hanya jika diperlukan
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment, fragmentTag)
            .commit()
        
        currentFragment = fragment
        Log.d(TAG, "🔄 Fragment replaced: $fragmentTag")
    }



    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isTrigger = keyCode == 293
        if (!isTrigger) return super.onKeyDown(keyCode, event)

        if (event?.repeatCount == 0) {
            Log.d(TAG, "🔑 KeyDown trigger: $keyCode (repeat=0) → authorize broadcast")
            // Izinkan SATU broadcast berikutnya
            allowNextBroadcast = true
        }
        // JANGAN konsumsi event agar driver scanner tetap menerima trigger dan menyalakan laser
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val isTrigger = keyCode == 293
        if (!isTrigger) return super.onKeyUp(keyCode, event)

        Log.d(TAG, "🔑 KeyUp trigger: $keyCode → authorize next broadcast (one-shot)")
        allowNextBroadcast = true
        // Biarkan event diteruskan ke driver scanner
        return false
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
        // Unregister receiver jika masih terdaftar
        if (scannerBroadcastReceiver != null) {
            try {
                unregisterReceiver(scannerBroadcastReceiver)
                Log.d(TAG, "🛑 Scanner broadcast receiver unregistered in onDestroy")
            } catch (e: Exception) {
                // Receiver mungkin sudah di-unregister di onPause
                Log.d(TAG, "📱 Receiver already unregistered or not registered")
            }
        }
        audioManager.release()
        Log.d(TAG, "📱 BarcodeScannerTabsActivity destroyed")
    }
}
