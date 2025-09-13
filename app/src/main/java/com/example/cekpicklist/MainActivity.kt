package com.example.cekpicklist

import android.app.ProgressDialog
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.Gravity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cekpicklist.R
import com.example.cekpicklist.adapter.PicklistAdapter
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.databinding.ActivityMainBinding
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.utils.Logger
import com.example.cekpicklist.api.SupabaseRealtimeService
import com.example.cekpicklist.data.PicklistUpdate
import com.example.cekpicklist.data.CacheUpdate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.example.cekpicklist.utils.VersionComparator
import com.example.cekpicklist.viewmodel.ScanViewModel
import com.example.cekpicklist.viewmodel.ScanViewModelFactory
import com.example.cekpicklist.service.AutoUpdateService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.appcompat.app.AlertDialog
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback
import com.example.cekpicklist.test.SupabaseRealtimeTest

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScanViewModel by viewModels { 
        ScanViewModelFactory(application) 
    }
    private lateinit var picklistAdapter: PicklistAdapter
    private var rfidReader: RFIDWithUHFUART? = null
    
    // Auto-update components
    private lateinit var updateReceiver: BroadcastReceiver
    private var isScanning = false
    private var isRfidSupported = false
    
    // Sound variables
    private var soundPool: SoundPool? = null
    private val soundMap = HashMap<Int, Int>()
    private var audioManager: AudioManager? = null
    
    // Realtime service
    private lateinit var realtimeService: SupabaseRealtimeService
    
    // Test realtime
    private val realtimeTest = SupabaseRealtimeTest()
    
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "🔥 onResume - menerapkan power level")
        
        // Apply power level setiap kali activity resume
        if (isRfidSupported && rfidReader != null) {
            applyPowerLevelToRFID()
        }
    }
    
    /**
     * Initialize sound system
     */
    private fun initSound() {
        Log.d("MainActivity", "🔥 Initializing sound system...")
        try {
            soundPool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            
            // Load sound files
            soundMap[1] = soundPool?.load(this, R.raw.barcodebeep, 1) ?: 0 // Success sound
            soundMap[2] = soundPool?.load(this, R.raw.serror, 1) ?: 0       // Error sound
            soundMap[3] = soundPool?.load(this, R.raw.barcodebeep, 1) ?: 0 // Celebration sound (menggunakan barcodebeep dengan efek berbeda)
            soundMap[4] = soundPool?.load(this, R.raw.barcodebeep, 1) ?: 0 // Applause sound (menggunakan barcodebeep dengan efek tepuk tangan)
            
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            
            Log.d("MainActivity", "🔥 Sound system initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error initializing sound: ${e.message}", e)
        }
    }
    
    /**
     * Play sound effect
     * @param soundId 1 for success, 2 for error, 3 for celebration
     */
    private fun playSound(soundId: Int) {
        try {
            if (soundPool != null && soundMap.containsKey(soundId)) {
                val volume = audioManager?.let { am ->
                    val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    currentVolume.toFloat() / maxVolume.toFloat()
                } ?: 0.5f
                
                soundPool?.play(soundMap[soundId] ?: 0, volume, volume, 1, 0, 1.0f)
                Log.d("MainActivity", "🔥 Playing sound: $soundId")
            } else {
                Log.w("MainActivity", "🔥 Sound pool not initialized or sound not found: $soundId")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error playing sound: ${e.message}", e)
        }
    }
    
    /**
     * Play celebration sound dengan efek terompet meriah
     * Memutar suara berulang dengan efek dramatis
     */
    private fun playCelebrationSound() {
        try {
            if (soundPool != null && soundMap.containsKey(3)) {
                val volume = audioManager?.let { am ->
                    val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    currentVolume.toFloat() / maxVolume.toFloat()
                } ?: 0.5f
                
                // Putar suara celebration dengan efek dramatis
                // Suara pertama - normal
                soundPool?.play(soundMap[3] ?: 0, volume, volume, 1, 0, 1.0f)
                
                // Suara kedua - sedikit lebih cepat (efek terompet)
                binding.root.postDelayed({
                    soundPool?.play(soundMap[3] ?: 0, volume * 0.8f, volume * 0.8f, 1, 0, 1.2f)
                }, 200)
                
                // Suara ketiga - lebih cepat lagi (efek meriah)
                binding.root.postDelayed({
                    soundPool?.play(soundMap[3] ?: 0, volume * 0.6f, volume * 0.6f, 1, 0, 1.5f)
                }, 400)
                
                Log.d("MainActivity", "🔥 Playing celebration sound dengan efek terompet meriah")
            } else {
                Log.w("MainActivity", "🔥 Sound pool not initialized or celebration sound not found")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error playing celebration sound: ${e.message}", e)
        }
    }
    
    /**
     * Play applause sound dengan efek tepuk tangan
     * Memutar suara berulang dengan ritme tepuk tangan
     */
    private fun playApplauseSound() {
        try {
            if (soundPool != null && soundMap.containsKey(4)) {
                val volume = audioManager?.let { am ->
                    val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    currentVolume.toFloat() / maxVolume.toFloat()
                } ?: 0.5f
                
                // Efek tepuk tangan dengan ritme yang berbeda
                // Tepuk tangan pertama - volume penuh
                soundPool?.play(soundMap[4] ?: 0, volume, volume, 1, 0, 0.8f)
                
                // Tepuk tangan kedua - sedikit lebih cepat
                binding.root.postDelayed({
                    soundPool?.play(soundMap[4] ?: 0, volume * 0.9f, volume * 0.9f, 1, 0, 0.9f)
                }, 300)
                
                // Tepuk tangan ketiga - lebih cepat lagi
                binding.root.postDelayed({
                    soundPool?.play(soundMap[4] ?: 0, volume * 0.8f, volume * 0.8f, 1, 0, 1.0f)
                }, 600)
                
                // Tepuk tangan keempat - cepat
                binding.root.postDelayed({
                    soundPool?.play(soundMap[4] ?: 0, volume * 0.7f, volume * 0.7f, 1, 0, 1.1f)
                }, 900)
                
                // Tepuk tangan kelima - sangat cepat
                binding.root.postDelayed({
                    soundPool?.play(soundMap[4] ?: 0, volume * 0.6f, volume * 0.6f, 1, 0, 1.2f)
                }, 1200)
                
                Log.d("MainActivity", "🔥 Playing applause sound dengan efek tepuk tangan")
            } else {
                Log.w("MainActivity", "🔥 Sound pool not initialized or applause sound not found")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error playing applause sound: ${e.message}", e)
        }
    }
    
    /**
     * Initialize realtime service
     */
    private fun initRealtimeService() {
        Log.d("MainActivity", "🔥 Initializing Realtime Service...")
        
        try {
            // Initialize realtime service
            realtimeService = SupabaseRealtimeService()
            
            // Set up callbacks
            realtimeService.setPicklistUpdateCallback { update ->
                Log.d("MainActivity", "📦 Picklist update received: ${update.action}")
                handlePicklistUpdate(update)
            }
            
            realtimeService.setCacheUpdateCallback { update ->
                Log.d("MainActivity", "📦 Cache update received: ${update.action}")
                handleCacheUpdate(update)
            }
            
            // Connect to realtime
            lifecycleScope.launch {
                try {
                    realtimeService.connect()
                    delay(1000) // Wait for connection
                    
                    // Subscribe to picklist changes
                    realtimeService.subscribeToPicklists()
                    
                    // Subscribe to picklist scan changes for current picklist
                    val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
                    if (!selectedPicklist.isNullOrEmpty()) {
                        realtimeService.subscribeToPicklistScans(selectedPicklist)
                    }
                    
                    Log.d("MainActivity", "✅ Realtime service initialized successfully")
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Error initializing realtime service: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error creating realtime service: ${e.message}", e)
        }
    }
    
    /**
     * Handle picklist update from realtime
     */
    private fun handlePicklistUpdate(update: PicklistUpdate) {
        Log.d("MainActivity", "🔄 Handling picklist update: ${update.action}")
        
        runOnUiThread {
            when (update.action) {
                "INSERT" -> {
                    Log.d("MainActivity", "➕ New picklist item added: ${update.articleName} ${update.size}")
                    // Refresh picklist data
                    val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
                    if (!selectedPicklist.isNullOrEmpty()) {
                        viewModel.loadPicklistItems(selectedPicklist)
                    }
                }
                "UPDATE" -> {
                    Log.d("MainActivity", "🔄 Picklist item updated: ${update.articleName} ${update.size}")
                    // Refresh picklist data
                    val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
                    if (!selectedPicklist.isNullOrEmpty()) {
                        viewModel.loadPicklistItems(selectedPicklist)
                    }
                }
                "DELETE" -> {
                    Log.d("MainActivity", "🗑️ Picklist item deleted: ${update.articleName} ${update.size}")
                    // Refresh picklist data
                    val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
                    if (!selectedPicklist.isNullOrEmpty()) {
                        viewModel.loadPicklistItems(selectedPicklist)
                    }
                }
            }
        }
    }
    
    /**
     * Handle cache update from realtime
     */
    private fun handleCacheUpdate(update: CacheUpdate) {
        Log.d("MainActivity", "🔄 Handling cache update: ${update.action}")
        
        runOnUiThread {
            when (update.action) {
                "INSERT_SCAN", "UPDATE_SCAN", "DELETE_SCAN" -> {
                    Log.d("MainActivity", "📦 Scan data updated: ${update.action}")
                    // Refresh picklist data to update scan counts
                    val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
                    if (!selectedPicklist.isNullOrEmpty()) {
                        viewModel.loadPicklistItems(selectedPicklist)
                    }
                }
            }
        }
    }
    
    /**
     * Test realtime connection
     */
    private fun testRealtimeConnection() {
        Log.d("MainActivity", "🧪 Testing realtime connection...")
        
        lifecycleScope.launch {
            try {
                // Test basic connection
                realtimeTest.testBasicConnection()
                delay(3000)
                
                // Test picklist subscription
                realtimeTest.testPicklistSubscription()
                delay(3000)
                
                // Test picklist scan subscription
                realtimeTest.testPicklistScanSubscription()
                delay(3000)
                
                Log.d("MainActivity", "✅ Realtime tests completed")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Realtime test error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Release sound resources
     */
    private fun releaseSoundPool() {
        try {
            soundPool?.release()
            soundPool = null
            soundMap.clear()
            Log.d("MainActivity", "🔥 Sound resources released")
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error releasing sound: ${e.message}", e)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Log dengan tag yang unik dan mudah dikenali
        android.util.Log.e("CEKPICKLIST_MAIN", "ONCREATE DIMULAI")
        
        // Reset toast counter saat activity baru dimulai
        ToastUtils.resetToastCounter()
        
        super.onCreate(savedInstanceState)
        android.util.Log.e("CEKPICKLIST_MAIN", "super.onCreate selesai")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        android.util.Log.e("CEKPICKLIST_MAIN", "binding inflate selesai")
        
        setContentView(binding.root)
        android.util.Log.e("CEKPICKLIST_MAIN", "setContentView selesai")
        
        // Setup window flags untuk mencegah focus loss
        setupWindowFlags()
        
        setupRecyclerView()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupRecyclerView selesai")
        
        setupSwipeRefresh()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupSwipeRefresh selesai")
        
        setupButtons()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupButtons selesai")
        
        setupSettingsIcon()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupSettingsIcon selesai")
        
        observeViewModel()
        android.util.Log.e("CEKPICKLIST_MAIN", "observeViewModel selesai")
        
        handleIncomingPicklist()
        android.util.Log.e("CEKPICKLIST_MAIN", "handleIncomingPicklist selesai")
        
        // Setup back press handler
        setupBackPressHandler()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupBackPressHandler selesai")
        
        // Initialize sound system
        initSound()
        
        // Setup auto-update system
        setupAutoUpdate()
        
        // Initialize realtime service
        initRealtimeService()
        
        // Test realtime connection
        testRealtimeConnection()
        
        // Prefetch sudah dilakukan di PicklistInputActivity, tidak perlu duplikasi
        
        // Setup RFID Reader setelah UI siap (dengan delay)
        binding.root.post {
            android.util.Log.e("CEKPICKLIST_MAIN", "Mulai setup RFID Reader")
            setupRFIDReader()
        }
        
                android.util.Log.e("CEKPICKLIST_MAIN", "ONCREATE SELESAI")
    }
    
    /**
     * Navigasi kembali ke PicklistInputActivity
     */
    private fun navigateBackToPicklistInput() {
        Log.d("MainActivity", "🔥 Navigasi kembali ke PicklistInputActivity")
        
        // Simpan data ke Supabase sebelum kembali
        // viewModel.returnToPicklistInput() // TODO: Implementasi method ini
        
        // Clear data yang tidak perlu
        // viewModel.clearError() // TODO: Implementasi method ini
        picklistAdapter.updateItems(emptyList())
        updateSummaryCards(0, 0)
        
        // Navigasi ke PicklistInputActivity
        val intent = Intent(this, PicklistInputActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    /**
     * Setup OnBackPressedCallback untuk Android API level 33+
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Logger.MainActivity.d("Back button pressed - showing back confirmation dialog")
                Logger.Dialog.d("Using SimpleDialogTheme for back confirmation")
                
                // Tampilkan konfirmasi sebelum kembali
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity, R.style.RoundDialogTheme)
                    .setTitle("Konfirmasi")
                    .setPositiveButton("Ya") { _, _ ->
                        Logger.Dialog.dialogConfirmed("Back to Picklist")
                        navigateBackToPicklistInput()
                    }
                    .setNegativeButton("Tidak") { dialog, _ ->
                        Logger.Dialog.dialogCancelled("Back to Picklist")
                        dialog.dismiss()
                    }
                    .create()
                
                // Log dialog sizing
                dialog.window?.let { window ->
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    
                    Logger.Dialog.d("Back confirmation dialog window metrics: ${screenWidth}x${screenHeight}")
                }
                
                Logger.Dialog.dialogShown("Back to Picklist Confirmation")
                dialog.show()
                
                // Set message dengan text rata tengah dan ukuran yang tepat
                dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
                    text = "Apakah Anda yakin ingin kembali ke menu picklist?"
                    gravity = android.view.Gravity.CENTER
                    textSize = resources.getDimension(R.dimen.dialog_message_size) / resources.displayMetrics.scaledDensity
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding),
                        resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding),
                        resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding),
                        resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding)
                    )
                }
            }
        })
    }
    
    private fun handleIncomingPicklist() {
        val startTime = System.currentTimeMillis()
        Log.i("MainActivity", "🔥 === HANDLE INCOMING PICKLIST START ===")
        Log.i("MainActivity", "🔥 Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        try {
            val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
            Log.i("MainActivity", "🔥 Selected picklist: $selectedPicklist")
            
            if (!selectedPicklist.isNullOrEmpty()) {
                // Check apakah sudah loading picklist yang sama untuk menghindari duplikasi
                val currentPicklist = viewModel.getCurrentPicklistNumber()
                if (currentPicklist == selectedPicklist) {
                    Log.w("MainActivity", "⚠️ Already loading picklist $selectedPicklist, skipping duplicate request")
                    return
                }
                
                // Load picklist items berdasarkan pilihan dari PicklistInputActivity
                Log.i("MainActivity", "🔥 Loading picklist items for: $selectedPicklist")
                viewModel.loadPicklistItems(selectedPicklist)
                
                // Update nama picklist di header
                binding.tvPicklistName.text = "Picklist: $selectedPicklist"
                
                val loadTime = System.currentTimeMillis() - startTime
                Log.i("MainActivity", "🔥 Picklist loading initiated in ${loadTime}ms")
                
                ToastUtils.showHighToastWithCooldown(this, "Picklist dipilih: $selectedPicklist")
            } else {
                // Jika tidak ada picklist yang dipilih, kembali ke PicklistInputActivity
                Log.w("MainActivity", "⚠️ No picklist selected, navigating to PicklistInputActivity")
                val intent = Intent(this, PicklistInputActivity::class.java)
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            val loadTime = System.currentTimeMillis() - startTime
            Log.e("MainActivity", "❌ Error in handleIncomingPicklist after ${loadTime}ms: ${e.message}", e)
        }
        Log.i("MainActivity", "🔥 === HANDLE INCOMING PICKLIST END ===")
    }
    
    /**
     * Setup window flags untuk mencegah focus loss dan window switching
     */
    private fun setupWindowFlags() {
        try {
            // Keep screen on untuk mencegah sleep
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Mencegah focus loss
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            
            // Mencegah window switching
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            
            Log.d("MainActivity", "🔥 Window flags setup selesai")
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error setting window flags: ${e.message}", e)
        }
    }
    
    /**
     * Monitor window focus changes untuk debugging
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("MainActivity", "🔥 Window focus changed: hasFocus=$hasFocus")
        
        if (hasFocus) {
            Log.d("MainActivity", "🔥 Window gained focus - activity aktif")
            // Pastikan focus tetap di activity
            window.decorView.requestFocus()
        } else {
            Log.d("MainActivity", "🔥 Window lost focus - activity tidak aktif")
        }
    }
    
    private fun setupRecyclerView() {
        picklistAdapter = PicklistAdapter()
        binding.rvPicklistItems.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = picklistAdapter
        }
        
        // Setup tap-to-delete functionality
        setupTapToDelete()
    }
    
    /**
     * Setup tap-to-delete functionality
     */
    private fun setupTapToDelete() {
        Log.d("MainActivity", "🔥 Setup tap-to-delete")
        
        // Set listener untuk tap delete
        picklistAdapter.setOnItemDeleteListener(object : PicklistAdapter.OnItemDeleteListener {
            override fun onItemDelete(position: Int, item: PicklistItem) {
                Log.d("MainActivity", "🔥 Item delete tapped: ${item.articleName} ${item.size}")
                handleItemDelete(item)
            }
        })
        
        Log.d("MainActivity", "🔥 Tap-to-delete berhasil disetup")
    }
    
    /**
     * Handle item delete - langsung hapus RFID tanpa konfirmasi
     */
    private fun handleItemDelete(item: PicklistItem) {
        Logger.MainActivity.d("handleItemDelete called for: ${item.articleName} ${item.size}")
        Logger.MainActivity.itemDeleted(item.articleId, item.size)
        
        // Langsung hapus RFID tanpa konfirmasi
        performRemoveRfid(item)
    }
    
    /**
     * Perform remove RFID for specific article
     */
    private fun performRemoveRfid(item: PicklistItem) {
        Log.d("MainActivity", "🔥 performRemoveRfid dipanggil untuk: ${item.articleName} ${item.size}")
        
        try {
            // Remove RFID di ViewModel
            // viewModel.removeRfidForArticle(item.articleName, item.size) // TODO: Implementasi method ini
            
            // Show success message
            ToastUtils.showHighToastWithCooldown(this, "RFID dihapus: ${item.articleName} ${item.size}")
            
            Log.d("MainActivity", "🔥 Remove RFID berhasil dilakukan")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error saat remove RFID: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Refresh adapter to restore swiped item
     */
    private fun refreshAdapter() {
        Log.d("MainActivity", "🔥 Refresh adapter")
        val currentItems = viewModel.getFilteredItems()
        val groupedItems = groupItemsByArticleAndSize(currentItems)
        picklistAdapter.updateItems(groupedItems)
    }
    
    private fun setupSwipeRefresh() {
        Log.d("MainActivity", "=== SETUP SWIPE REFRESH ===")
        
        // Aktifkan swipe refresh untuk manual fetch
        binding.swipeRefreshLayout.setOnRefreshListener {
            val startTime = System.currentTimeMillis()
            Log.i("MainActivity", "🔥 === SWIPE REFRESH START ===")
            Log.i("MainActivity", "🔥 Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}")
            
            // Ambil picklist number dari intent atau current state
            val selectedPicklist = intent.getStringExtra("SELECTED_PICKLIST")
            if (!selectedPicklist.isNullOrEmpty()) {
                Log.i("MainActivity", "🔥 Refreshing picklist: $selectedPicklist")
                
                // Check apakah sudah loading untuk menghindari duplikasi
                val currentPicklist = viewModel.getCurrentPicklistNumber()
                if (currentPicklist == selectedPicklist && viewModel.isLoading.value == true) {
                    Log.w("MainActivity", "⚠️ Already refreshing picklist $selectedPicklist, skipping duplicate request")
                    binding.swipeRefreshLayout.isRefreshing = false
                    return@setOnRefreshListener
                }
                
                viewModel.loadPicklistItems(selectedPicklist)
                
                val refreshTime = System.currentTimeMillis() - startTime
                Log.i("MainActivity", "🔥 Swipe refresh initiated in ${refreshTime}ms")
            } else {
                Log.w("MainActivity", "⚠️ No picklist selected for refresh")
                ToastUtils.showHighToastWithCooldown(this, "Tidak ada picklist yang dipilih")
                binding.swipeRefreshLayout.isRefreshing = false
            }
            
            Log.i("MainActivity", "🔥 === SWIPE REFRESH END ===")
            
            // Stop refresh indicator setelah selesai
            viewModel.isLoading.observe(this) { isLoading ->
                if (!isLoading) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Log.d("MainActivity", "🔥 Swipe refresh selesai - data lama dihapus, data baru di-cache")
                }
            }
        }
        
        // Aktifkan swipe refresh
        binding.swipeRefreshLayout.isEnabled = true
        Log.d("MainActivity", "=== SWIPE REFRESH DIAKTIFKAN UNTUK MANUAL FETCH ===")
    }
    
    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            Log.d("MainActivity", "🔥 UI BUTTON CLICKED: btnScan - isScanning: $isScanning")
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }
        
        binding.btnClear.setOnClickListener {
            Logger.MainActivity.d("btnClear clicked - showing clear confirmation dialog")
            Logger.Dialog.d("Using RoundDialogTheme for clear confirmation")
            Logger.Dialog.d("Text colors: Primary=Black, Secondary=Black, Tertiary=Black")
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.RoundDialogTheme)
                .setTitle("Konfirmasi Clear")
                .setMessage("Apakah Anda yakin ingin mereset scan data?\\n\\n• Reset qtyScan ke 0\\n• Reset RFID detect counter\\n• Hapus overscan & non-picklist\\n• Tampilkan ulang data scan yang valid")
                .setPositiveButton("Clear") { _, _ ->
                    Logger.Dialog.dialogConfirmed("Clear All")
                    Logger.MainActivity.clearAllConfirmed()
                    performClearRfidAndNonPicklist()
                }
                .setNegativeButton("Batal") { dialog, _ ->
                    Logger.Dialog.dialogCancelled("Clear All")
                    dialog.dismiss()
                }
                .create()
            
            // Log dialog sizing
            dialog.window?.let { window ->
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                Logger.Dialog.d("Clear confirmation dialog window metrics: ${screenWidth}x${screenHeight}")
            }
            
            Logger.Dialog.dialogShown("Clear All Confirmation")
            dialog.show()
            
            // Set message dengan text rata tengah dan ukuran yang tepat
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
                text = "Apakah Anda yakin ingin mereset semua scan data?\n\n• Reset qtyScan ke 0\n• Reset RFID detect counter\n• Hapus overscan & non-picklist\n• Kembali ke state awal picklist"
                gravity = android.view.Gravity.CENTER
                textSize = resources.getDimension(R.dimen.dialog_message_size) / resources.displayMetrics.scaledDensity
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding),
                    resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding),
                    resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding),
                    resources.getDimensionPixelSize(R.dimen.confirmation_dialog_padding)
                )
            }
        }
        
        // Tombol Submit dihilangkan - sekarang menggunakan auto-post
        // binding.btnSubmit.setOnClickListener {
        //     Log.d("MainActivity", "🔥 UI BUTTON CLICKED: btnSubmit - memproses EPC dan clear buffer")
        //     viewModel.processScannedEPCs()
        //     viewModel.submitScannedEPCs()
        // }
        
        // Debug: Long press pada tombol Scan untuk cek power level
        binding.btnScan.setOnLongClickListener {
            Log.d("MainActivity", "🔥 UI BUTTON LONG PRESS: btnScan - cek power level")
            checkPowerLevelStatus()
            true
        }
        
        // Long press hint untuk icon Clear
        binding.btnClear.setOnLongClickListener {
            Log.d("MainActivity", "🔥 UI BUTTON LONG PRESS: btnClear - show hint")
            ToastUtils.showHighToastWithCooldown(this, "Reset scan data dan tampilkan ulang data scan yang valid")
            true
        }
        
        // Long press pada Settings untuk reconnect realtime
        binding.ivSettings.setOnLongClickListener {
            Log.d("MainActivity", "🔥 UI BUTTON LONG PRESS: ivSettings - reconnect realtime")
            // viewModel.reconnectRealtime() // TODO: Implementasi method ini
            ToastUtils.showHighToastWithCooldown(this, "Mencoba reconnect ke Supabase Realtime...")
            true
        }
    }
    
    private fun setupSettingsIcon() {
        binding.ivSettings.setOnClickListener {
            Log.d("MainActivity", "🔥 UI BUTTON CLICKED: ivSettings - navigasi ke Settings")
            navigateToSettings()
        }
    }
    
    private fun navigateToSettings() {
        Log.d("MainActivity", "🔥 Navigasi ke SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
        private fun setupRFIDReader() {
        Log.d("MainActivity", "=== SETUP RFID READER START ===")
        
        // Gunakan try-catch yang lebih aman
        try {
            // Cek apakah device mendukung RFID terlebih dahulu
            val deviceModel = android.os.Build.MODEL
            val deviceManufacturer = android.os.Build.MANUFACTURER
            val deviceBrand = android.os.Build.BRAND
            
            Log.d("MainActivity", "Device Model: $deviceModel")
            Log.d("MainActivity", "Device Manufacturer: $deviceManufacturer")
            Log.d("MainActivity", "Device Brand: $deviceBrand")
            
            // Cek apakah device adalah Chainway C5 atau device yang mendukung RFID
            val isChainwayDevice = deviceModel.contains("C5", ignoreCase = true) || 
                                  deviceModel.contains("C4000", ignoreCase = true) || 
                                  deviceManufacturer.contains("Chainway", ignoreCase = true) ||
                                  deviceBrand.contains("Chainway", ignoreCase = true)
            
            Log.d("MainActivity", "Is Chainway Device: $isChainwayDevice")
            
            if (isChainwayDevice) {
                Log.d("MainActivity", "Device terdeteksi sebagai Chainway, mencoba inisialisasi RFID...")
                try {
                    Log.d("MainActivity", "Attempting to get RFID instance...")
                    rfidReader = RFIDWithUHFUART.getInstance()
                    Log.d("MainActivity", "RFID instance created successfully")
                    
                    // Gunakan AsyncTask untuk inisialisasi
                    InitTask().execute()
                } catch (e: ConfigurationException) {
                    Log.e("MainActivity", "ConfigurationException: ${e.message}", e)
                    ToastUtils.showHighLongToast(this, "Device tidak mendukung RFID SDK: ${e.message}")
                    isRfidSupported = false
                } catch (e: Exception) {
                    Log.e("MainActivity", "Exception during RFID setup: ${e.message}", e)
                    e.printStackTrace()
                    ToastUtils.showHighLongToast(this, "Error inisialisasi RFID: ${e.message}")
                    isRfidSupported = false
                }
            } else {
                Log.d("MainActivity", "Device not recognized as Chainway")
                ToastUtils.showHighLongToast(this, "Device ini tidak mendukung RFID (Model: $deviceModel, Brand: $deviceBrand)")
                isRfidSupported = false
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in setupRFIDReader: ${e.message}", e)
            e.printStackTrace()
            ToastUtils.showHighLongToast(this, "Error saat setup RFID: ${e.message}")
            isRfidSupported = false
        }
        
        Log.d("MainActivity", "=== SETUP RFID READER END ===")
    }
    
    /**
     * Perform clear all data - reset scan data dan tampilkan ulang data scan yang valid
     * Reset ke state awal picklist:
     * - Reset qtyScan untuk semua items
     * - Reset RFID detect counter
     * - Hapus overscan dan non-picklist
     * - Tampilkan ulang data scan yang valid dari cache/Supabase
     */
    private fun performClearRfidAndNonPicklist() {
        Log.d("MainActivity", "🔥 performClearRfidAndNonPicklist dipanggil")
        
        try {
            // 1. Panggil method di ViewModel untuk clear RFID dan reset state
            viewModel.clearRfidAndResetToInitialState()
            
            // 2. Reset RFID counter display
            binding.tvRfidDetected.text = "0"
            
            // 3. Show success message
            ToastUtils.showHighToastWithCooldown(this, "Scan data berhasil direset dan data scan yang valid ditampilkan ulang")
            
            Log.d("MainActivity", "🔥 Clear RFID & reset berhasil dilakukan")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error saat clear RFID & reset: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Perform clear all data - clear RFID buffer and reset qty scan
     */
    private fun performClearAll() {
        Log.d("MainActivity", "🔥 performClearAll dipanggil")
        
        try {
            // Clear RFID buffer di ViewModel
            // viewModel.clearAllData() // TODO: Implementasi method ini
            
            // Clear UI
            // viewModel.clearError() // TODO: Implementasi method ini
            picklistAdapter.updateItems(emptyList())
            updateSummaryCards(0, 0)
            
            // Reset RFID counter display
            binding.tvRfidDetected.text = "0"
            
            // Show success message
            ToastUtils.showHighToastWithCooldown(this, "Semua data berhasil dibersihkan")
            
            Log.d("MainActivity", "🔥 Clear all berhasil dilakukan")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error saat clear all: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Check power level status and show debug info
     */
    private fun checkPowerLevelStatus() {
        try {
            val sharedPreferences = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
            val savedPowerLevel = sharedPreferences.getInt("power_level", 20)
            val currentPowerLevel = getCurrentPowerLevel()
            
            val message = """
                🔥 Power Level Debug Info:
                
                📱 Saved in Settings: $savedPowerLevel
                🔌 Current RFID Power: $currentPowerLevel
                📡 RFID Reader Ready: ${rfidReader != null}
                ✅ RFID Supported: $isRfidSupported
                
                ${if (currentPowerLevel != savedPowerLevel) "⚠️ Power level tidak sesuai!" else "✅ Power level sesuai"}
            """.trimIndent()
            
            Logger.MainActivity.d("Power Level Debug: $message")
            Logger.Dialog.d("Using RoundDialogTheme for power level dialog")
            
            // Show dialog with power level info
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.RoundDialogTheme)
                .setTitle("Power Level Status")
                .setPositiveButton("Apply Power") { _, _ ->
                    Logger.Dialog.dialogConfirmed("Power Level Apply")
                    applyPowerLevelToRFID()
                }
                .setNegativeButton("Close") { dialog, _ ->
                    Logger.Dialog.dialogCancelled("Power Level Status")
                    dialog.dismiss()
                }
                .create()
            
            // Log dialog sizing
            dialog.window?.let { window ->
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                Logger.Dialog.d("Power level dialog window metrics: ${screenWidth}x${screenHeight}")
            }
            
            Logger.Dialog.dialogShown("Power Level Status")
            dialog.show()
            
            // Set message dengan text rata tengah
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
                text = message
                gravity = android.view.Gravity.CENTER
            }
                
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error checking power level status: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Get current power level from RFID reader
     */
    private fun getCurrentPowerLevel(): Int {
        return try {
            if (rfidReader != null && isRfidSupported) {
                val currentPower = rfidReader?.getPower() ?: -1
                Log.d("MainActivity", "🔥 Current RFID power level: $currentPower")
                currentPower
            } else {
                Log.d("MainActivity", "🔥 RFID reader tidak tersedia, tidak bisa get power level")
                -1
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error getting power level: ${e.message}", e)
            -1
        }
    }
    
    /**
     * Apply power level to RFID reader
     */
    private fun applyPowerLevelToRFID() {
        try {
            val sharedPreferences = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
            val powerLevel = sharedPreferences.getInt("power_level", 20)
            
            Log.d("MainActivity", "🔥 applyPowerLevelToRFID dipanggil")
            Log.d("MainActivity", "🔥 Power level dari SharedPreferences: $powerLevel")
            Log.d("MainActivity", "🔥 RFID Reader null: ${rfidReader == null}")
            Log.d("MainActivity", "🔥 isRfidSupported: $isRfidSupported")
            
            if (rfidReader != null && isRfidSupported) {
                Log.d("MainActivity", "🔥 Menerapkan power level ke RFID reader: $powerLevel")
                
                // Get current power level before setting
                val currentPower = rfidReader?.getPower() ?: -1
                Log.d("MainActivity", "🔥 Current power level: $currentPower")
                
                // Apply power level to RFID reader
                val success = rfidReader?.setPower(powerLevel) ?: false
                
                if (success) {
                    // Verify the power level was actually set
                    val newPower = rfidReader?.getPower() ?: -1
                    Log.d("MainActivity", "🔥 Power level berhasil diterapkan: $powerLevel")
                    Log.d("MainActivity", "🔥 Power level setelah set: $newPower")
                    
                    if (newPower == powerLevel) {
                        ToastUtils.showHighToastWithCooldown(this, "Power level diterapkan: $powerLevel")
                        Log.d("MainActivity", "🔥 Power level berhasil diverifikasi: $powerLevel")
                    } else {
                        Log.w("MainActivity", "🔥 Power level tidak sesuai: expected=$powerLevel, actual=$newPower")
                        ToastUtils.showHighToastWithCooldown(this, "Power level tidak sesuai: $newPower")
                    }
                } else {
                    Log.e("MainActivity", "🔥 Gagal menerapkan RFID power level: $powerLevel")
                    ToastUtils.showHighToastWithCooldown(this, "Gagal menerapkan power level")
                }
            } else {
                Log.d("MainActivity", "🔥 RFID reader tidak tersedia, tidak bisa menerapkan power level")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error applying power level: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Get RSSI threshold from settings
     */
    private fun getRSSIThreshold(): Int {
        val sharedPrefs = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
        return sharedPrefs.getInt("rssi_threshold", -70)
    }
    
    /**
     * Check if RSSI debug mode is enabled (bypass threshold)
     */
    private fun isRSSIDebugMode(): Boolean {
        val sharedPrefs = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
        return sharedPrefs.getBoolean("rssi_debug_mode", false)
    }
    
    // AsyncTask untuk inisialisasi RFID Reader
    inner class InitTask : AsyncTask<String, Int, Boolean>() {
        private var progressDialog: ProgressDialog? = null
        
        override fun doInBackground(vararg params: String?): Boolean {
            Log.d("MainActivity", "=== INIT TASK START ===")
            return try {
                Log.d("MainActivity", "Starting RFID initialization...")
                val result = rfidReader?.init(this@MainActivity) ?: false
                Log.d("MainActivity", "RFID initialization result: $result")
                Log.d("MainActivity", "=== INIT TASK END ===")
                result
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception during RFID initialization: ${e.message}", e)
                e.printStackTrace()
                Log.d("MainActivity", "=== INIT TASK END WITH ERROR ===")
                false
            }
        }
        
        override fun onPreExecute() {
            super.onPreExecute()
            Log.d("MainActivity", "Showing progress dialog...")
            progressDialog = ProgressDialog(this@MainActivity).apply {
                setProgressStyle(ProgressDialog.STYLE_SPINNER)
                setMessage("Inisialisasi RFID Reader...")
                setCanceledOnTouchOutside(false)
                show()
            }
        }
        
        override fun onPostExecute(result: Boolean) {
            super.onPostExecute(result)
            Log.d("MainActivity", "RFID initialization completed with result: $result")
            progressDialog?.cancel()
            if (result) {
                Log.d("MainActivity", "RFID Reader berhasil diinisialisasi")
                ToastUtils.showHighToastWithCooldown(this@MainActivity, "RFID Reader berhasil diinisialisasi")
                isRfidSupported = true
                
                // Apply power level after successful initialization
                applyPowerLevelToRFID()
            } else {
                Log.e("MainActivity", "RFID Reader gagal diinisialisasi")
                ToastUtils.showHighLongToast(this@MainActivity, "Device tidak mendukung RFID SDK atau gagal inisialisasi")
                isRfidSupported = false
            }
        }
    }
    
    private fun observeViewModel() {
        Log.d("MainActivity", "=== OBSERVE VIEWMODEL START ===")
        
        viewModel.picklists.observe(this) { picklists ->
            Log.d("MainActivity", "Picklists updated: ${picklists.size} items")
            // Hapus toast untuk menghindari spam
            // ToastUtils.showHighToastWithCooldown(this, "Daftar picklist ter-load: ${picklists.size} item")
        }
        
        // HANYA SATU OBSERVER dengan debug logging
        viewModel.picklistItems.observe(this) { items ->
            Log.d("MainActivity", "Received ${items.size} items in MainActivity")
            // Hapus toast untuk menghindari spam
            // ToastUtils.showHighToastWithCooldown(this, "Received ${items.size} items")
            
            if (items.isEmpty()) {
                // Tampilkan placeholder ketika tidak ada items
                Log.d("MainActivity", "Items empty, showing placeholder")
                binding.rvPicklistItems.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
                updateSummaryCards(0, 0)
            } else {
                // Sembunyikan placeholder dan tampilkan RecyclerView
                Log.d("MainActivity", "Items available, showing RecyclerView")
                binding.rvPicklistItems.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
                
                // Gunakan getFilteredItems() untuk menyembunyikan item yang sudah complete (GREEN)
                val filteredItems = viewModel.getFilteredItems()
                Log.d("MainActivity", "Filtered items: ${filteredItems.size} (hiding completed GREEN items)")
                
                // Debug log untuk melihat filtered items
                Log.d("MainActivity", "🔥 Showing ${filteredItems.size} items (completed GREEN items are hidden)")
                
                // Kelompokkan items berdasarkan article_name dan size
                val groupedItems = groupItemsByArticleAndSize(filteredItems)
                Log.d("MainActivity", "Grouped into ${groupedItems.size} unique items")
                
                // Tampilkan hanya items yang belum complete
                val finalItems = groupedItems
                Log.d("MainActivity", "Final items (after grouping): ${finalItems.size}")
                
                // Debug log untuk final items (dikurangi untuk menghindari spam)
                Log.d("MainActivity", "🔥 Final items count: ${finalItems.size}")
                if (finalItems.isNotEmpty()) {
                    val firstItem = finalItems.first()
                    Log.d("MainActivity", "🔥 First item: ${firstItem.articleName} ${firstItem.size} - qtyPl=${firstItem.qtyPl}, qtyScan=${firstItem.qtyScan}")
                }
                
                // Check apakah semua items sudah complete untuk animasi
                // Gunakan original items untuk check completion, bukan filtered items
                val allComplete = items.all { it.isComplete() }
                Log.d("MainActivity", "🔥 All items complete check: $allComplete")
                
                if (allComplete && items.isNotEmpty()) {
                    Log.d("MainActivity", "🎉 All items completed! Showing completion animation")
                    
                    // Tampilkan animasi completion yang spektakuler
                    showCompletionAnimation()
                    
                    // Toast notification
                    ToastUtils.showHighToastWithCooldown(this, "🎉 Semua items sudah selesai di-scan!")
                }
                
                // Selalu tampilkan filtered items (yang belum complete)
                picklistAdapter.updateItems(finalItems)
                
                // Update summary cards dengan data yang benar
                // **PERUBAHAN**: Gunakan original items untuk summary, bukan filtered items
                // Ini memastikan total qtyPl tetap konsisten meskipun ada filtering
                val totalQty = items.sumOf { it.qtyPl }
                val scannedQty = items.sumOf { it.qtyScan }
                val remainingQty = totalQty - scannedQty
                
                // Log summary dengan informasi tentang completion status
                val completedItems = items.count { it.isComplete() }
                val incompleteItemsCount = items.count { !it.isComplete() }
                Log.i("MainActivity", "🔥 Summary: Total=${items.size}, Completed=$completedItems (showing ✓), Incomplete=$incompleteItemsCount, QtyPl=$totalQty, QtyScan=$scannedQty")
                
                updateSummaryCards(totalQty, remainingQty)
                
                // Update RFID detected counter dengan total scanned items
                binding.tvRfidDetected.text = scannedQty.toString()
                
                // Tombol Submit sudah dihilangkan - menggunakan auto-post
            }
        }
        
        // viewModel.scanCounter.observe(this) { count ->
        //     // Update UI untuk menampilkan jumlah EPC yang di-scan
        //     Log.d("MainActivity", "Scan counter updated: $count")
        //     binding.tvRfidDetected.text = count.toString()
        //     
        //     // Reset counter saat EPC diproses (count = 0)
        //     if (count == 0) {
        //         Log.d("MainActivity", "RFID counter direset setelah EPC diproses")
        //         // Tombol Submit sudah dihilangkan - menggunakan auto-post
        //     }
        // }
        
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d("MainActivity", "Loading state: $isLoading")
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Log.e("MainActivity", "Error message: $it")
                ToastUtils.showHighLongToast(this, it)
                // viewModel.clearError() // TODO: Implementasi method ini
            }
        }
        
        // Observer untuk completion status dan animasi
        // Check completion status setiap kali items di-update
        viewModel.picklistItems.observe(this) { items ->
            if (items.isNotEmpty()) {
                val allComplete = items.all { it.isComplete() }
                Log.d("MainActivity", "🔥 Completion check: All complete = $allComplete")
                
                if (allComplete) {
                    Log.d("MainActivity", "🎉 Real-time completion detected! Showing animation")
                    showCompletionAnimation()
                }
            }
        }
        
        // Observer untuk status koneksi realtime
        if (::realtimeService.isInitialized) {
            lifecycleScope.launch {
                realtimeService.connectionStatus.collect { isConnected ->
                    Log.d("MainActivity", "Realtime connection status: $isConnected")
                    updateRealtimeStatusIndicator(isConnected)
                }
            }
        }
        
        Log.d("MainActivity", "=== OBSERVE VIEWMODEL END ===")
    }
    
    private fun startScanning() {
        Log.d("MainActivity", "🔥 startScanning dipanggil")
        if (!isRfidSupported) {
            Log.d("MainActivity", "🔥 Device tidak mendukung RFID")
            ToastUtils.showHighLongToast(this, "Device tidak mendukung RFID SDK")
            return
        }
        
        if (rfidReader == null) {
            Log.d("MainActivity", "🔥 RFID Reader null")
            ToastUtils.showHighToastWithCooldown(this, "RFID Reader tidak tersedia")
            return
        }
        
        if (!isScanning) {
            Log.d("MainActivity", "🔥 Memulai scanning RFID...")
            
            // Apply power level sebelum memulai scanning
            applyPowerLevelToRFID()
            
            try {
                rfidReader?.setInventoryCallback(object : IUHFInventoryCallback {
                    override fun callback(uhfTagInfo: UHFTAGInfo) {
                        val epc = uhfTagInfo.getEPC()
                        val rssiString = uhfTagInfo.getRssi()
                        val rssiThreshold = getRSSIThreshold()
                        
                        // Convert RSSI string to int for comparison (handle decimal values)
                        val rssi = try {
                            // Handle decimal values by converting to double first, then to int
                            rssiString.toDouble().toInt()
                        } catch (e: NumberFormatException) {
                            Log.w("MainActivity", "🔥 Invalid RSSI format: $rssiString, using -100")
                            -100
                        }
                        
                        // Check if debug mode is enabled
                        val debugMode = isRSSIDebugMode()
                        
                        // Filter berdasarkan RSSI threshold (atau bypass jika debug mode)
                        if (epc.isNotEmpty() && (debugMode || rssi >= rssiThreshold)) {
                            // Pastikan ini berjalan di main thread
                            runOnUiThread {
                                // val isNewEpc = viewModel.addEpc(epc, rssi) // TODO: Implementasi method dengan parameter RSSI
                                viewModel.addEpc(epc) // Temporary fix
                                
                                // Play sound untuk semua EPC (temporary)
                                playSound(1) // Success sound
                            }
                        }
                    }
                })
                
                if (rfidReader?.startInventoryTag() == true) {
                    isScanning = true
                    binding.btnScan.text = "Stop Scan"
                    Log.d("MainActivity", "🔥 Scanning berhasil dimulai")
                    ToastUtils.showHighToastWithCooldown(this, "Scanning dimulai")
                } else {
                    Log.d("MainActivity", "🔥 Gagal memulai scanning")
                    playSound(2) // Error sound
                    ToastUtils.showHighToastWithCooldown(this, "Gagal memulai scanning")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "🔥 Error saat scanning: ${e.message}", e)
                playSound(2) // Error sound
                ToastUtils.showHighLongToast(this, "Error saat scanning: ${e.message}")
            }
        } else {
            Log.d("MainActivity", "🔥 Scanning sudah berjalan")
        }
    }
    
    private fun stopScanning() {
        Log.d("MainActivity", "🔥 stopScanning dipanggil")
        if (isScanning) {
            Log.d("MainActivity", "🔥 Menghentikan scanning RFID...")
            try {
                rfidReader?.stopInventory()
                isScanning = false
                binding.btnScan.text = "Scan"
                Log.d("MainActivity", "🔥 Scanning berhasil dihentikan")
                ToastUtils.showHighToastWithCooldown(this, "Scanning dihentikan")
            } catch (e: Exception) {
                Log.e("MainActivity", "🔥 Error saat menghentikan scanning: ${e.message}", e)
                ToastUtils.showHighLongToast(this, "Error saat menghentikan scanning: ${e.message}")
            }
        } else {
            Log.d("MainActivity", "🔥 Scanning sudah dihentikan")
        }
    }
    
    private fun updateSummaryCards(totalQty: Int, remainingQty: Int) {
        binding.tvTotalQty.text = totalQty.toString()
        binding.tvRemainingQty.text = remainingQty.toString()
    }
    
    /**
     * Update status indicator untuk koneksi realtime
     */
    private fun updateRealtimeStatusIndicator(isConnected: Boolean) {
        try {
            // Update icon berdasarkan status
            val icon = if (isConnected) {
                R.drawable.ic_signal_wifi_4_bar // Icon WiFi terhubung
            } else {
                R.drawable.ic_signal_wifi_off // Icon WiFi terputus
            }
            
            binding.ivWifiStatus.setImageResource(icon)
            
            // Update color
            val color = if (isConnected) {
                getColor(android.R.color.holo_green_dark)
            } else {
                getColor(android.R.color.holo_red_dark)
            }
            
            binding.ivWifiStatus.setColorFilter(color)
            
            Log.d("MainActivity", "🔥 WiFi status indicator updated: $isConnected")
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error updating WiFi status indicator: ${e.message}", e)
        }
    }
    
    /**
     * Tampilkan animasi completion confetti ball dengan suara terompet meriah dan tepuk tangan
     */
    private fun showCompletionAnimation() {
        Log.d("MainActivity", "🔥 Menampilkan animasi completion dengan overlay yang memenuhi layar")
        
        // Sembunyikan tombol submit
        binding.btnSubmit.visibility = View.GONE
        
        // Putar suara terompet meriah dengan efek celebration
        playCelebrationSound()
        
        // Putar suara tepuk tangan setelah terompet (delay 500ms)
        binding.root.postDelayed({
            playApplauseSound()
        }, 500)
        
        // Tampilkan animasi overlay yang memenuhi layar
        binding.root.post {
            // Buat overlay yang memenuhi layar
            val overlayView = createFullScreenOverlay()
            binding.root.addView(overlayView)
            
            // Animasi fade in dengan efek dramatis
            overlayView.alpha = 0f
            overlayView.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
            
            // Hapus view setelah 3 detik
            overlayView.postDelayed({
                overlayView.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction {
                        binding.root.removeView(overlayView)
                    }
                    .start()
            }, 2500)
        }
    }
    
    /**
     * Sembunyikan animasi completion
     */
    private fun hideCompletionAnimation() {
        Log.d("MainActivity", "🔥 Menyembunyikan animasi completion")
        // Tombol submit tetap disembunyikan karena sudah tidak digunakan
        binding.btnSubmit.visibility = View.GONE
    }
    
    /**
     * Buat overlay yang memenuhi layar dengan animasi konfirmasi di tengah
     */
    private fun createFullScreenOverlay(): View {
        // Buat FrameLayout yang memenuhi layar
        val overlayLayout = FrameLayout(this)
        overlayLayout.setBackgroundColor(android.graphics.Color.parseColor("#80000000")) // Semi-transparent black
        
        // Layout parameters untuk memenuhi layar
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        overlayLayout.layoutParams = layoutParams
        
        // Buat container untuk konten di tengah
        val centerContainer = LinearLayout(this)
        centerContainer.orientation = LinearLayout.VERTICAL
        centerContainer.gravity = Gravity.CENTER
        
        val centerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        centerParams.gravity = Gravity.CENTER
        centerContainer.layoutParams = centerParams
        
        // Buat checkmark besar
        val checkmarkView = TextView(this)
        checkmarkView.text = "✅" // Checkmark emoji
        checkmarkView.textSize = 120f // Ukuran besar
        checkmarkView.gravity = Gravity.CENTER
        checkmarkView.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Hijau
        
        // Buat teks konfirmasi
        val successText = TextView(this)
        successText.text = "SELESAI!"
        successText.textSize = 32f
        successText.gravity = Gravity.CENTER
        successText.setTextColor(android.graphics.Color.WHITE)
        successText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // Buat teks sub-konfirmasi
        val subText = TextView(this)
        subText.text = "Semua item berhasil di-scan"
        subText.textSize = 16f
        subText.gravity = Gravity.CENTER
        subText.setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
        val subTextParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        subTextParams.topMargin = 16
        subText.layoutParams = subTextParams
        
        // Buat confetti emoji
        val confettiView = TextView(this)
        confettiView.text = "🎊"
        confettiView.textSize = 80f
        confettiView.gravity = Gravity.CENTER
        val confettiParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        confettiParams.topMargin = 24
        confettiView.layoutParams = confettiParams
        
        // Tambahkan semua view ke container
        centerContainer.addView(checkmarkView)
        centerContainer.addView(successText)
        centerContainer.addView(subText)
        centerContainer.addView(confettiView)
        
        // Tambahkan container ke overlay
        overlayLayout.addView(centerContainer)
        
        // Animasi scale untuk efek pop
        centerContainer.scaleX = 0.3f
        centerContainer.scaleY = 0.3f
        
        centerContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(400)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .withEndAction {
                // Animasi bounce ringan
                centerContainer.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        centerContainer.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            }
            .start()
        
        return overlayLayout
    }
    
    private fun groupItemsByArticleAndSize(items: List<PicklistItem>): List<PicklistItem> {
        Log.d("MainActivity", "🔥 === GROUPING ITEMS ===")
        Log.d("MainActivity", "🔥 Input items: ${items.size}")
        
        val grouped = items.groupBy { item -> "${item.articleName}_${item.size}" }
        Log.d("MainActivity", "🔥 Grouped into ${grouped.size} groups")
        
        val result = grouped.map { (key, groupedItems) ->
            val firstItem = groupedItems.first()
            
            // **PERBAIKAN LOGIC**: Gunakan distinct untuk menghindari duplikasi
            val distinctItems = groupedItems.distinctBy { "${it.articleId}_${it.size}" }
            
            // **PERBAIKAN QTY CALCULATION**: 
            // - qtyPl: Jumlah total yang direncanakan (bisa dari multiple records)
            // - qtyScan: Jumlah distinct EPC yang sudah di-scan (sudah dihitung dengan distinct di SupabaseService)
            val totalQtyPl = distinctItems.sumOf { it.qtyPl }
            val totalQtyScan = distinctItems.sumOf { it.qtyScan }
            
            // **VALIDASI DATA**: Deteksi duplikasi dan overscan
            if (groupedItems.size > distinctItems.size) {
                Log.w("MainActivity", "🔥 DUPLICATE DATA DETECTED: $key - ${groupedItems.size} raw items, ${distinctItems.size} distinct items")
            }
            
            if (totalQtyScan > totalQtyPl) {
                Log.w("MainActivity", "🔥 OVERSCAN DETECTED: $key - qtyScan=$totalQtyScan > qtyPl=$totalQtyPl")
                Log.w("MainActivity", "🔥 OVERSCAN DETAIL: ${totalQtyScan - totalQtyPl} items overscan")
            }
            
            Log.d("MainActivity", "🔥 Group '$key': ${groupedItems.size} raw items, ${distinctItems.size} distinct items")
            Log.d("MainActivity", "🔥 Group '$key': totalQtyPl=$totalQtyPl, totalQtyScan=$totalQtyScan")
            
            PicklistItem(
                id = firstItem.id,
                noPicklist = firstItem.noPicklist,
                articleId = firstItem.articleId,
                articleName = firstItem.articleName,
                size = firstItem.size,
                productId = firstItem.productId,
                qtyPl = totalQtyPl,
                qtyScan = totalQtyScan,
                createdAt = firstItem.createdAt,
                warehouse = firstItem.warehouse,
                tagStatus = firstItem.tagStatus
            )
        }
        .sortedBy { it.articleName }
        
        Log.d("MainActivity", "🔥 Final grouped items: ${result.size}")
        
        // **SUMMARY LOGGING**: Hitung total dan overscan
        val totalQtyPl = result.sumOf { it.qtyPl }
        val totalQtyScan = result.sumOf { it.qtyScan }
        val totalOverscan = result.sumOf { if (it.qtyScan > it.qtyPl) it.qtyScan - it.qtyPl else 0 }
        val completedItems = result.count { it.isComplete() }
        val overscanItems = result.count { it.qtyScan > it.qtyPl }
        
        Log.i("MainActivity", "🔥 === GROUPING SUMMARY ===")
        Log.i("MainActivity", "🔥 Total Items: ${result.size}")
        Log.i("MainActivity", "🔥 Total Qty Planned: $totalQtyPl")
        Log.i("MainActivity", "🔥 Total Qty Scanned: $totalQtyScan")
        Log.i("MainActivity", "🔥 Total Overscan: $totalOverscan")
        Log.i("MainActivity", "🔥 Completed Items: $completedItems")
        Log.i("MainActivity", "🔥 Overscan Items: $overscanItems")
        Log.i("MainActivity", "🔥 === END GROUPING SUMMARY ===")
        
        result.forEach { item ->
            val isComplete = item.isComplete()
            val overscanText = if (item.qtyScan > item.qtyPl) " (+${item.qtyScan - item.qtyPl})" else ""
            Log.d("MainActivity", "🔥 Final item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}$overscanText, isComplete=$isComplete")
        }
        Log.d("MainActivity", "🔥 === END GROUPING ===")
        
        return result
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Log detail untuk semua tombol yang ditekan
        val keyName = getKeyName(keyCode)
        val repeatCount = event?.repeatCount ?: 0
        val action = event?.action ?: -1
        val flags = event?.flags ?: 0
        
        Log.d("MainActivity", "🔥 KEYCODE DETECTED: $keyName (Code: $keyCode, Repeat: $repeatCount, Action: $action, Flags: $flags)")
        
        // Handle tombol fisik untuk scanning RFID
        when (keyCode) {
            // Tombol Function Keys (F1-F8) - Gunakan fungsi default device
            KeyEvent.KEYCODE_F1 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F1 (Function Key 1) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F2 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F2 (Function Key 2) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F3 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F3 (Function Key 3) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F4 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F4 (Function Key 4) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F5 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F5 (Function Key 5) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F6 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F6 (Function Key 6) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F7 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F7 (Function Key 7) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_F8 -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: F8 (Function Key 8) - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Menu - Gunakan fungsi default device
            KeyEvent.KEYCODE_MENU -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: MENU - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Volume Up/Down - Gunakan fungsi default device
            KeyEvent.KEYCODE_VOLUME_UP -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: VOLUME_UP - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: VOLUME_DOWN - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Camera - Gunakan fungsi default device
            KeyEvent.KEYCODE_CAMERA -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: CAMERA - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Enter/DPAD_CENTER - Gunakan fungsi default device
            KeyEvent.KEYCODE_ENTER -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: ENTER - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: DPAD_CENTER - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Space - Gunakan fungsi default device
            KeyEvent.KEYCODE_SPACE -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: SPACE - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Back - Untuk navigasi
            KeyEvent.KEYCODE_BACK -> {
                if (repeatCount == 0) {
                    Log.d("MainActivity", "🔥 Tombol fisik ditekan: BACK - Code: $keyCode")
                    // Handle back button jika diperlukan
                }
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Home - Untuk navigasi
            KeyEvent.KEYCODE_HOME -> {
                if (repeatCount == 0) {
                    Log.d("MainActivity", "🔥 Tombol fisik ditekan: HOME - Code: $keyCode")
                    // Handle home button jika diperlukan
                }
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Power - Untuk power management
            KeyEvent.KEYCODE_POWER -> {
                if (repeatCount == 0) {
                    Log.d("MainActivity", "🔥 Tombol fisik ditekan: POWER - Code: $keyCode")
                    // Handle power button jika diperlukan
                }
                return super.onKeyDown(keyCode, event)
            }
            
            // Tombol Scan Trigger (keycode 293) - Press to start, release to stop
            293 -> {
                if (repeatCount == 0) {
                    Log.d("MainActivity", "🔥 Tombol fisik ditekan: SCAN_TRIGGER (293) - Memulai scanning")
                    
                    // Pastikan focus tetap di activity ini
                    if (currentFocus != null) {
                        Log.d("MainActivity", "🔥 Current focus: ${currentFocus?.javaClass?.simpleName}")
                    }
                    
                    // Request focus untuk mencegah focus loss
                    window.decorView.requestFocus()
                    
                    if (!isScanning) {
                        startScanning()
                    } else {
                        Log.d("MainActivity", "🔥 Scanning sudah berjalan, tidak perlu memulai lagi")
                    }
                    
                    // Pastikan focus tetap di activity setelah startScanning
                    window.decorView.post {
                        window.decorView.requestFocus()
                        Log.d("MainActivity", "🔥 Focus restored setelah startScanning")
                    }
                }
                return true
            }
            
            // Tombol lainnya - Gunakan fungsi default device
            else -> {
                Log.d("MainActivity", "🔥 Tombol fisik ditekan: $keyName - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
        }
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Log detail untuk semua tombol yang dilepas
        val keyName = getKeyName(keyCode)
        val action = event?.action ?: -1
        val flags = event?.flags ?: 0
        
        Log.d("MainActivity", "🔥 KEYCODE RELEASED: $keyName (Code: $keyCode, Action: $action, Flags: $flags)")
        
        // Handle tombol fisik untuk stop scanning
        when (keyCode) {
            // Tombol Scan Trigger (keycode 293) - Release to stop
            293 -> {
                Log.d("MainActivity", "🔥 Tombol fisik dilepas: SCAN_TRIGGER (293) - Menghentikan scanning")
                if (isScanning) {
                    stopScanning()
                } else {
                    Log.d("MainActivity", "🔥 Scanning sudah dihentikan, tidak perlu menghentikan lagi")
                }
                return true
            }
            
            // Tombol lainnya - Gunakan fungsi default device
            else -> {
                Log.d("MainActivity", "🔥 Tombol fisik dilepas: $keyName - Code: $keyCode (Fungsi default device)")
                return super.onKeyUp(keyCode, event)
            }
        }
    }
    
    /**
     * Helper function untuk mendapatkan nama tombol dari keycode
     */
    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_F1 -> "F1"
            KeyEvent.KEYCODE_F2 -> "F2"
            KeyEvent.KEYCODE_F3 -> "F3"
            KeyEvent.KEYCODE_F4 -> "F4"
            KeyEvent.KEYCODE_F5 -> "F5"
            KeyEvent.KEYCODE_F6 -> "F6"
            KeyEvent.KEYCODE_F7 -> "F7"
            KeyEvent.KEYCODE_F8 -> "F8"
            KeyEvent.KEYCODE_MENU -> "MENU"
            KeyEvent.KEYCODE_VOLUME_UP -> "VOLUME_UP"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "VOLUME_DOWN"
            KeyEvent.KEYCODE_CAMERA -> "CAMERA"
            KeyEvent.KEYCODE_ENTER -> "ENTER"
            KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"
            KeyEvent.KEYCODE_SPACE -> "SPACE"
            KeyEvent.KEYCODE_BACK -> "BACK"
            KeyEvent.KEYCODE_HOME -> "HOME"
            KeyEvent.KEYCODE_POWER -> "POWER"
            293 -> "SCAN_TRIGGER" // Tombol khusus untuk scan
            KeyEvent.KEYCODE_0 -> "0"
            KeyEvent.KEYCODE_1 -> "1"
            KeyEvent.KEYCODE_2 -> "2"
            KeyEvent.KEYCODE_3 -> "3"
            KeyEvent.KEYCODE_4 -> "4"
            KeyEvent.KEYCODE_5 -> "5"
            KeyEvent.KEYCODE_6 -> "6"
            KeyEvent.KEYCODE_7 -> "7"
            KeyEvent.KEYCODE_8 -> "8"
            KeyEvent.KEYCODE_9 -> "9"
            KeyEvent.KEYCODE_A -> "A"
            KeyEvent.KEYCODE_B -> "B"
            KeyEvent.KEYCODE_C -> "C"
            KeyEvent.KEYCODE_D -> "D"
            KeyEvent.KEYCODE_E -> "E"
            KeyEvent.KEYCODE_F -> "F"
            KeyEvent.KEYCODE_G -> "G"
            KeyEvent.KEYCODE_H -> "H"
            KeyEvent.KEYCODE_I -> "I"
            KeyEvent.KEYCODE_J -> "J"
            KeyEvent.KEYCODE_K -> "K"
            KeyEvent.KEYCODE_L -> "L"
            KeyEvent.KEYCODE_M -> "M"
            KeyEvent.KEYCODE_N -> "N"
            KeyEvent.KEYCODE_O -> "O"
            KeyEvent.KEYCODE_P -> "P"
            KeyEvent.KEYCODE_Q -> "Q"
            KeyEvent.KEYCODE_R -> "R"
            KeyEvent.KEYCODE_S -> "S"
            KeyEvent.KEYCODE_T -> "T"
            KeyEvent.KEYCODE_U -> "U"
            KeyEvent.KEYCODE_V -> "V"
            KeyEvent.KEYCODE_W -> "W"
            KeyEvent.KEYCODE_X -> "X"
            KeyEvent.KEYCODE_Y -> "Y"
            KeyEvent.KEYCODE_Z -> "Z"
            else -> "UNKNOWN_KEY_$keyCode"
        }
    }
    
    private fun toggleScanning() {
        Log.d("MainActivity", "🔥 toggleScanning dipanggil - isScanning: $isScanning")
        if (isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        try {
            rfidReader?.free()
        } catch (e: Exception) {
            // Ignore error saat cleanup
        }
        
        // Stop realtime subscription
        // viewModel.stopRealtimeSubscription() // TODO: Implementasi method ini
        
        // Cleanup realtime service
        try {
            if (::realtimeService.isInitialized) {
                lifecycleScope.launch {
                    realtimeService.disconnect()
                    realtimeService.cleanup()
                }
                Log.d("MainActivity", "✅ Realtime service cleaned up")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error cleaning up realtime service: ${e.message}", e)
        }
        
        // Cleanup test realtime
        try {
            realtimeTest.cleanup()
            Log.d("MainActivity", "✅ Realtime test cleaned up")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error cleaning up realtime test: ${e.message}", e)
        }
        
        // Release sound resources
        releaseSoundPool()
        
        // Unregister update receiver
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering update receiver: ${e.message}")
        }
    }
    
    /**
     * Setup auto-update system
     */
    private fun setupAutoUpdate() {
        Log.d("MainActivity", "🔥 Setting up auto-update system")
        
        // Setup broadcast receiver untuk update notifications
        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.example.cekpicklist.UPDATE_AVAILABLE" -> {
                        val currentVersion = intent.getStringExtra("current_version")
                        val latestVersion = intent.getStringExtra("latest_version")
                        val updateType = intent.getStringExtra("update_type")
                        
                        showUpdateDialog(currentVersion, latestVersion, updateType)
                    }
                }
            }
        }
        
        // Register broadcast receiver
        val filter = IntentFilter("com.example.cekpicklist.UPDATE_AVAILABLE")
        registerReceiver(updateReceiver, filter)
        
        // Cek update saat startup (dengan delay untuk tidak mengganggu UI)
        binding.root.postDelayed({
            checkForUpdates()
        }, 3000) // Delay 3 detik setelah startup
    }
    
    /**
     * Cek update dari Git repository
     */
    private fun checkForUpdates() {
        Log.d("MainActivity", "🔥 Checking for updates...")
        
        val intent = Intent(this, AutoUpdateService::class.java).apply {
            action = AutoUpdateService.ACTION_CHECK_UPDATE
        }
        startService(intent)
    }
    
    /**
     * Tampilkan dialog update
     */
    private fun showUpdateDialog(currentVersion: String?, latestVersion: String?, updateType: String?) {
        val builder = AlertDialog.Builder(this, R.style.RoundDialogTheme)
        
        val updateTypeText = when (updateType) {
            "MAJOR" -> "Major Update"
            "MINOR" -> "Minor Update"
            "PATCH" -> "Patch Update"
            else -> "Update"
        }
        
        builder.setTitle("🔄 $updateTypeText Tersedia")
            .setMessage("""
                Versi saat ini: $currentVersion
                Versi terbaru: $latestVersion
                
                Update akan diunduh dan diinstall otomatis.
                Apakah Anda ingin melanjutkan?
            """.trimIndent())
            .setPositiveButton("Update Sekarang") { _, _ ->
                Log.d("MainActivity", "🔥 User confirmed update")
                // Update sudah diunduh di background, tidak perlu action tambahan
            }
            .setNegativeButton("Nanti") { dialog, _ ->
                Log.d("MainActivity", "🔥 User declined update")
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}