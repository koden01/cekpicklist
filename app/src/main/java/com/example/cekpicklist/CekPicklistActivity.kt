package com.example.cekpicklist

import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
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
import com.example.cekpicklist.base.BaseRfidActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cekpicklist.adapter.PicklistAdapter
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.databinding.ActivityMainBinding
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.utils.Logger
import com.example.cekpicklist.utils.RfidScanSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.cekpicklist.viewmodel.ScanViewModel
import com.example.cekpicklist.viewmodel.ScanViewModelFactory

// Legacy RFID SDK imports removed - handled by BaseRfidActivity/RfidScanManager

class CekPicklistActivity : BaseRfidActivity() {
    companion object {
        private const val TAG = "CekPicklistActivity"
    }
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScanViewModel by viewModels { 
        ScanViewModelFactory(application) 
    }
    private lateinit var picklistAdapter: PicklistAdapter
    // RFID handled by BaseRfidActivity/RfidScanManager
    private var hasShownCompletionAnimation = false
    private var completionCheckJob: Job? = null
    
    // **PERBAIKAN**: Tambahkan cooldown untuk tombol scan
    private var lastScanButtonClickTime = 0L
    private val SCAN_BUTTON_COOLDOWN_MS = 2000L // 2 detik cooldown
    
    // Sound variables
    private var soundPool: SoundPool? = null
    private val soundMap = HashMap<Int, Int>()
    private var audioManager: AudioManager? = null
    
    
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔥 onResume - menerapkan power level")
    }
    
    /**
     * Initialize sound system
     */
    private fun initSound() {
        Log.d(TAG, "🔥 Initializing sound system...")
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
            soundMap[3] = soundPool?.load(this, R.raw.celebration, 1) ?: 0  // Celebration sound (file MP3 asli)
            soundMap[4] = soundPool?.load(this, R.raw.applause, 1) ?: 0     // Applause sound (file MP3 asli)
            
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            
            Log.d(TAG, "🔥 Sound system initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error initializing sound: ${e.message}", e)
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
                Log.d(TAG, "🔥 Playing sound: $soundId")
            } else {
                Log.w(TAG, "🔥 Sound pool not initialized or sound not found: $soundId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error playing sound: ${e.message}", e)
        }
    }
    
    /**
     * Play celebration sound dengan file MP3 asli (maksimal 5 detik)
     * Memutar suara celebration yang sudah direkam
     */
    private fun playCelebrationSound() {
        try {
            // **PERBAIKAN**: Check yang lebih robust untuk sound pool dan sound map
            if (soundPool != null && soundMap.isNotEmpty() && soundMap.containsKey(3) && soundMap[3] != 0) {
                val volume = audioManager?.let { am ->
                    val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    currentVolume.toFloat() / maxVolume.toFloat()
                } ?: 0.5f
                
                // Putar suara celebration MP3 asli
                val streamId = soundPool?.play(soundMap[3] ?: 0, volume, volume, 1, 0, 1.0f) ?: 0
                
                // Stop suara setelah 5 detik
                binding.root.postDelayed({
                    soundPool?.stop(streamId)
                    Log.d("MainActivity", "🔥 Celebration sound stopped after 5 seconds")
                }, 5000)
                
                Log.d("MainActivity", "🔥 Playing celebration sound (MP3 file, max 5 seconds)")
            } else {
                Log.w("MainActivity", "🔥 Sound pool not initialized or celebration sound not found - soundPool: ${soundPool != null}, soundMap: ${soundMap.size}, hasKey3: ${soundMap.containsKey(3)}")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error playing celebration sound: ${e.message}", e)
        }
    }
    
    /**
     * Play applause sound dengan file MP3 asli (maksimal 5 detik)
     * Memutar suara applause yang sudah direkam
     */
    private fun playApplauseSound() {
        try {
            // **PERBAIKAN**: Check yang lebih robust untuk sound pool dan sound map
            if (soundPool != null && soundMap.isNotEmpty() && soundMap.containsKey(4) && soundMap[4] != 0) {
                val volume = audioManager?.let { am ->
                    val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    currentVolume.toFloat() / maxVolume.toFloat()
                } ?: 0.5f
                
                // Putar suara applause MP3 asli
                val streamId = soundPool?.play(soundMap[4] ?: 0, volume, volume, 1, 0, 1.0f) ?: 0
                
                // Stop suara setelah 5 detik
                binding.root.postDelayed({
                    soundPool?.stop(streamId)
                    Log.d("MainActivity", "🔥 Applause sound stopped after 5 seconds")
                }, 5000)
                
                Log.d("MainActivity", "🔥 Playing applause sound (MP3 file, max 5 seconds)")
            } else {
                Log.w("MainActivity", "🔥 Sound pool not initialized or applause sound not found - soundPool: ${soundPool != null}, soundMap: ${soundMap.size}, hasKey4: ${soundMap.containsKey(4)}")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error playing applause sound: ${e.message}", e)
        }
    }
    
    // Legacy realtime service methods removed - using direct Supabase calls
    
    /**
     * Release sound resources
     */
    private fun releaseSoundPool() {
        try {
            soundPool?.release()
            soundPool = null
            soundMap.clear()
            Log.d(TAG, "🔥 Sound resources released")
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error releasing sound: ${e.message}", e)
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
        
        
        setupButtons()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupButtons selesai")
        
        setupSettingsIcon()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupSettingsIcon selesai")
        
        // SwipeRefresh dinonaktifkan
        
        // Hapus RSSI Debug Mode dari SharedPreferences jika ada
        removeRSSIDebugMode()
        
        observeViewModel()
        android.util.Log.e("CEKPICKLIST_MAIN", "observeViewModel selesai")
        
        handleIncomingPicklist()
        android.util.Log.e("CEKPICKLIST_MAIN", "handleIncomingPicklist selesai")
        
        // Setup back press handler
        setupBackPressHandler()
        android.util.Log.e("CEKPICKLIST_MAIN", "setupBackPressHandler selesai")
        
        // Initialize sound system
        initSound()
        
        // Apply RFID settings from SharedPreferences
        applySettingsFromSharedPreferences()
        
        // **PERBAIKAN**: Seeding akan dilakukan di observer setelah data selesai dimuat
        
        android.util.Log.e("CEKPICKLIST_MAIN", "ONCREATE SELESAI")
    }

    // ================= BaseRfidActivity Callbacks =================
    override fun handleRfidDetected(epc: String, rssi: Int) {
        // RFID sudah di-handle oleh RfidScanManager dengan automatic lookup
        // Hanya update counter untuk UI
        val (isNew, shouldPlay) = viewModel.addEpc(epc)
        if (shouldPlay) {
            Log.d(TAG, "🔥 RFID detected: $epc (isNew=$isNew, RSSI=$rssi)")
        }
    }

    override fun handleScanStateChanged(isScanning: Boolean) {
        this.isScanning = isScanning
        binding.btnScan.text = if (isScanning) "Stop Scan" else "Scan"
    }

    override fun handleRfidError(error: String) {
        ToastUtils.showHighLongToast(this, error)
    }

    override fun handleGracePeriodCompleted() {
        val rfids = getAllUniqueRfids()
        if (rfids.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    viewModel.performBatchLookup(rfids)
                } catch (e: Exception) {
        Log.e(TAG, "❌ Error performBatchLookup after grace period: ${e.message}")
                }
            }
        }
    }

    override fun handleLookupRequired(rfidList: List<String>) {
        // RfidScanManager sudah melakukan lookup dan menyimpan hasil di lokal storage
        // Konsumsi hasil lookup dari RfidScanManager dengan optimasi
        try {
            val localResults = rfidScanManager.getAllLookupResults()
            val stats = rfidScanManager.getLocalDataStats()
            
            Log.d(TAG, "🔥 Lookup results consumed from RfidScanManager: ${localResults.size} items")
            Log.d(TAG, "📊 Local data stats: $stats")
            
            // Process lookup results through ViewModel
            if (localResults.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        // Filter hanya EPC yang ada di rfidList untuk efisiensi
                        val relevantResults = localResults.filterKeys { epc -> 
                            rfidList.contains(epc) 
                        }
                        
                        Log.d(TAG, "🎯 Processing ${relevantResults.size} relevant EPCs from ${localResults.size} total cached results")
                        
                        // Process through ViewModel's new method that uses epcToProduct data directly
                        val successCount = viewModel.processLookupResultsFromRfidScanManager(localResults, rfidList)
                        Log.d(TAG, "✅ Processed ${successCount} RFIDs from RfidScanManager local cache")
                        
                        // Log statistik untuk monitoring
                        val foundCount = relevantResults.values.count { it.articleName != "NOT_FOUND" }
                        val notFoundCount = relevantResults.values.count { it.articleName == "NOT_FOUND" }
                        Log.d(TAG, "📈 Processed results: $foundCount found, $notFoundCount not found")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing lookup results: ${e.message}")
                    }
                }
            } else {
                Log.d(TAG, "ℹ️ No local lookup results available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error consuming lookup results: ${e.message}")
        }
    }

    override fun handleDataCleared() {
        binding.tvRfidDetected.text = "0"
        picklistAdapter.updateItems(emptyList())
    }
    
    /**
     * Navigasi kembali ke PicklistInputActivity
     */
    private fun navigateBackToPicklistInput() {
        Log.d(TAG, "🔥 Navigasi kembali ke PicklistInputActivity")
        
        // **PERBAIKAN**: Clear overscan dan non-picklist sebelum kembali
        viewModel.clearRfidAndResetToInitialState()
        
        // Clear UI
        picklistAdapter.updateItems(emptyList())
        updateSummaryCards(0, 0)
        
        // Reset completion animation flag
        hasShownCompletionAnimation = false
        
        // Navigasi ke PicklistInputActivity dengan flags untuk mencegah multiple instances
        val intent = Intent(this, HalamanAwalActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@CekPicklistActivity, R.style.RoundDialogTheme)
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
                
                // **PERBAIKAN**: Seeding akan dilakukan di observer setelah data selesai dimuat
                
                // Update nama picklist di header
                binding.tvPicklistName.text = "Picklist: $selectedPicklist"
                
                val loadTime = System.currentTimeMillis() - startTime
                Log.i("MainActivity", "🔥 Picklist loading initiated in ${loadTime}ms")
                
                ToastUtils.showHighToastWithCooldown(this, "Picklist dipilih: $selectedPicklist")
            } else {
                // Jika tidak ada picklist yang dipilih, kembali ke PicklistInputActivity
                Log.w("MainActivity", "⚠️ No picklist selected, navigating to PicklistInputActivity")
                val intent = Intent(this, HalamanAwalActivity::class.java)
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
     * Seed RfidScanManager dengan data dari database untuk mencegah duplicate processing
     * Dipanggil SETELAH load picklist items untuk memastikan data sudah tersedia
     */
    private fun seedRfidScanManagerWithDatabaseData(picklistNumber: String) {
        Log.d(TAG, "🔥 Seeding RfidScanManager with database data for picklist: $picklistNumber")
        
        try {
            // Seed dengan processed EPC list
            val processedEpcList = viewModel.getProcessedEpcListForCurrentPicklist()
            if (processedEpcList.isNotEmpty()) {
                rfidScanManager.seedWithDatabaseEpcs(processedEpcList)
                Log.d(TAG, "🔥 Seeded RfidScanManager with ${processedEpcList.size} EPCs from database")
            } else {
                Log.d(TAG, "ℹ️ No processed EPCs found for seeding")
            }
            
            // Seed dengan lookup results
            val lookupResults = viewModel.getDatabaseLookupResultsForCurrentPicklist()
            if (lookupResults.isNotEmpty()) {
                rfidScanManager.seedWithDatabaseLookupResults(lookupResults)
                Log.d(TAG, "🔥 Seeded RfidScanManager with ${lookupResults.size} lookup results from database")
            } else {
                Log.d(TAG, "ℹ️ No lookup results found for seeding")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error seeding RfidScanManager with database data: ${e.message}", e)
        }
    }
    
    /**
     * Setup window flags untuk mencegah focus loss dan window switching
     */
    private fun setupWindowFlags() {
        try {
            // Keep screen on untuk mencegah sleep
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Mencegah focus loss
            // NOTE: FLAG_SHOW_WHEN_LOCKED and FLAG_DISMISS_KEYGUARD deprecated on newer API levels.
            // Dibiarkan untuk kompatibilitas perangkat lama; tidak mengubah perilaku saat ini.
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            
            // Mencegah window switching
            // NOTE: Deprecated di API baru; gunakan setTurnScreenOn/setShowWhenLocked pada implementasi modern.
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            
            Log.d(TAG, "🔥 Window flags setup selesai")
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error setting window flags: ${e.message}", e)
        }
    }
    
    /**
     * Monitor window focus changes untuk debugging
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "🔥 Window focus changed: hasFocus=$hasFocus")
        
        if (hasFocus) {
            Log.d(TAG, "🔥 Window gained focus - activity aktif")
            // Pastikan focus tetap di activity
            window.decorView.requestFocus()
        } else {
            Log.d(TAG, "🔥 Window lost focus - activity tidak aktif")
        }
    }
    
    private fun setupRecyclerView() {
        picklistAdapter = PicklistAdapter()
        binding.rvPicklistItems.apply {
            layoutManager = LinearLayoutManager(this@CekPicklistActivity)
            adapter = picklistAdapter
        }
        
        // Setup tap-to-delete functionality
        setupTapToDelete()
    }
    
    /**
     * Setup tap-to-delete functionality
     */
    private fun setupTapToDelete() {
        Log.d(TAG, "🔥 Setup tap-to-delete")
        
        // Set listener untuk tap delete
        picklistAdapter.setOnItemDeleteListener(object : PicklistAdapter.OnItemDeleteListener {
            override fun onItemDelete(position: Int, item: PicklistItem) {
                Log.d(TAG, "🔥 Item delete tapped: ${item.articleName} ${item.size}")
                handleItemDelete(item)
            }
        })
        
        Log.d(TAG, "🔥 Tap-to-delete berhasil disetup")
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
        Log.d(TAG, "🔥 performRemoveRfid dipanggil untuk: ${item.articleName} ${item.size}")
        
        try {
            // Determine removal strategy based on overscan status
            val isOverscan = item.qtyScan > item.qtyPl
            val overscanCount = if (isOverscan) item.qtyScan - item.qtyPl else 0
            
            Log.d(TAG, "📊 Item analysis: qtyScan=${item.qtyScan}, qtyPl=${item.qtyPl}, overscanCount=$overscanCount")
            
            // Remove ALL RFID di ViewModel (clear all scanned RFID for this item)
            viewModel.removeAllRfidForArticle(item.articleName, item.size, rfidScanManager)
            
            // Show appropriate success message
            val message = if (item.qtyScan == 0) {
                "Tidak ada RFID untuk dihapus: ${item.articleName} ${item.size} (${item.qtyScan}/${item.qtyPl})"
            } else {
                "Semua RFID dihapus: ${item.articleName} ${item.size} (${item.qtyScan}/${item.qtyPl})"
            }
            
            ToastUtils.showHighToastWithCooldown(this, message)
            
            Log.d(TAG, "🔥 Remove RFID berhasil dilakukan")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error saat remove RFID: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Refresh adapter to restore swiped item
     */
    private fun refreshAdapter() {
        Log.d(TAG, "🔥 Refresh adapter")
        // The adapter will be automatically updated by the filteredItems observer
        // No need to manually get filtered items here
    }
    
    
    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastClick = currentTime - lastScanButtonClickTime
            
            Log.d(TAG, "🔥 UI BUTTON CLICKED: btnScan - isScanning: $isScanning, timeSinceLastClick: ${timeSinceLastClick}ms")
            
            // **PERBAIKAN**: Cek cooldown untuk mencegah scan berulang terlalu cepat
            if (timeSinceLastClick < SCAN_BUTTON_COOLDOWN_MS) {
                val remainingCooldown = SCAN_BUTTON_COOLDOWN_MS - timeSinceLastClick
                Log.d(TAG, "🔥 Scan button cooldown active - remaining: ${remainingCooldown}ms")
                ToastUtils.showHighToastWithCooldown(this, "Tunggu ${remainingCooldown/1000 + 1} detik sebelum scan lagi")
                return@setOnClickListener
            }
            
            // Update last click time
            lastScanButtonClickTime = currentTime
            
            toggleRfidScanning()
        }
        
        binding.btnClear.setOnClickListener {
            Logger.MainActivity.d("btnClear clicked - showing clear confirmation dialog")
            Logger.Dialog.d("Using RoundDialogTheme for clear confirmation")
            Logger.Dialog.d("Text colors: Primary=Black, Secondary=Black, Tertiary=Black")
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.RoundDialogTheme)
                .setTitle("Konfirmasi Clear")
                .setMessage("Apakah Anda yakin ingin mereset semua scan data?")
                .setPositiveButton("Clear") { _, _ ->
                    Logger.Dialog.dialogConfirmed("Clear All")
                    Logger.MainActivity.clearAllConfirmed()
                    performClearRfidOnly()
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
                text = "Apakah Anda yakin ingin mereset semua scan data?"
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
            Log.d(TAG, "🔥 UI BUTTON LONG PRESS: btnScan - cek power level")
            checkPowerLevelStatus()
            true
        }
        
        // Long press hint untuk icon Clear
        binding.btnClear.setOnLongClickListener {
            Log.d(TAG, "🔥 UI BUTTON LONG PRESS: btnClear - show hint")
            ToastUtils.showHighToastWithCooldown(this, "Reset scan data dan tampilkan ulang data scan yang valid")
            true
        }
        
        // Long press pada Settings: non-functional for duplicate removal (always enabled)
        binding.ivSettings.setOnLongClickListener {
            Log.d(TAG, "🔥 UI BUTTON LONG PRESS: ivSettings - duplicate removal is forced enabled")
            ToastUtils.showHighToastWithCooldown(this, "Duplicate removal is always Enabled")
            true
        }
        
        // **PERBAIKAN**: Tambahkan tombol cleanup overscan (akan ditambahkan ke layout nanti)
        // binding.btnCleanup.setOnClickListener {
        //     Log.d("MainActivity", "🔥 UI BUTTON CLICKED: btnCleanup - cleanup overscan data")
        //     cleanupOverscanData()
        // }
    }
    
    /**
     * Cleanup overscan data dari database
     */
    private fun cleanupOverscanData() {
        try {
            Log.d(TAG, "🧹 Starting overscan cleanup...")
            
            // Tampilkan dialog konfirmasi
            androidx.appcompat.app.AlertDialog.Builder(this, R.style.RoundDialogTheme)
                .setTitle("Cleanup Overscan Data")
                .setMessage("Apakah Anda yakin ingin menghapus data overscan dari database? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("Ya, Hapus") { _, _ ->
                    // Jalankan cleanup di background thread
                    lifecycleScope.launch {
                        try {
                            Log.d(TAG, "🧹 Executing overscan cleanup...")
                            Log.d(TAG, "🔥 Overscan cleanup removed - using direct Supabase calls")
                            ToastUtils.showHighToastWithCooldown(this@CekPicklistActivity, "Overscan cleanup removed")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "❌ Error during cleanup: ${e.message}")
                            ToastUtils.showHighToastWithCooldown(this@CekPicklistActivity, "Error: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
                
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error in cleanupOverscanData: ${e.message}")
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
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
    
    // Legacy RFID setup removed - now handled by BaseRfidActivity
    
    /**
     * Perform clear RFID saja (tanpa menyimpan data)
     * - Clear temp RFID collection
     * - Reset RFID detection counter
     * - Hapus overscan dan non-picklist
     * - TIDAK menyimpan data ke Supabase
     */
    private fun performClearRfidOnly() {
        Log.d("MainActivity", "🔥 performClearRfidOnly dipanggil")
        
        try {
            // Hentikan scanning terlebih dahulu agar tidak ada EPC baru yang masuk saat proses clear
            if (isScanning) {
                stopRfidScanning()
            }
            
            // 1. Clear RFID collection di ViewModel (tanpa save) - ini akan reset counter juga
            viewModel.clearRfidCollectionOnly()
            // 1b. Explicitly reset RFID detection counter for UI expectation
            viewModel.resetRfidDetectionCount()
            
            // **PERBAIKAN KRITIS**: Clear RFID buffer di RfidScanManager agar EPC bisa di-scan kembali
            try {
                rfidScanManager.clearAllData()
                Log.d("MainActivity", "🔥 RFID buffer cleared in RfidScanManager")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error clearing RFID buffer: ${e.message}", e)
            }
            
            // 2. Reset completion animation flag
            hasShownCompletionAnimation = false
            
            // 3. Non-picklist items sekarang terintegrasi di main list, tidak ada section terpisah
            
            // 4. Show success message
            ToastUtils.showHighToastWithCooldown(this, "✅ RFID data cleared successfully!")
            
            // 5. Update UI counter immediately
            binding.tvRfidDetected.text = "0"
            
            Log.d("MainActivity", "✅ Clear RFID berhasil (tanpa save)")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error clearing RFID: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "❌ Error clearing RFID: ${e.message}")
        }
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
            // 1b. Explicitly reset RFID detection counter to align with Clear expectations
            viewModel.resetRfidDetectionCount()
            
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
            val currentSettings = getRfidScanSettings()
            
            val message = """
                🔥 Power Level Debug Info:
                
                📱 Saved in Settings: $savedPowerLevel
                🔌 Current RFID Power: ${currentSettings.powerLevel}
                🔄 Duplicate Removal: ${currentSettings.duplicateRemovalEnabled}
                ⏱️ Grace Period: ${currentSettings.gracePeriodMs}ms
                📶 RSSI Threshold: ${currentSettings.rssiThreshold}
                
                ${if (currentSettings.powerLevel != savedPowerLevel) "⚠️ Power level tidak sesuai!" else "✅ Power level sesuai"}
            """.trimIndent()
            
            Logger.MainActivity.d("Power Level Debug: $message")
            Logger.Dialog.d("Using RoundDialogTheme for power level dialog")
            
            // Show dialog with power level info
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.RoundDialogTheme)
                .setTitle("RFID Settings Status")
                .setPositiveButton("Apply Settings") { _, _ ->
                    Logger.Dialog.dialogConfirmed("RFID Settings Apply")
                    applySettingsFromSharedPreferences()
                }
                .setNegativeButton("Close") { dialog, _ ->
                    Logger.Dialog.dialogCancelled("RFID Settings Status")
                    dialog.dismiss()
                }
                .create()
            
            // Log dialog sizing
            dialog.window?.let { window ->
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                Logger.Dialog.d("RFID settings dialog window metrics: ${screenWidth}x${screenHeight}")
            }
            
            Logger.Dialog.dialogShown("RFID Settings Status")
            dialog.show()
            
            // Set message dengan text rata tengah
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
                text = message
                gravity = android.view.Gravity.CENTER
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error checking power level status: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }
    
    /**
     * Apply settings from SharedPreferences to RfidScanManager
     */
    private fun applySettingsFromSharedPreferences() {
        try {
            val sharedPreferences = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
            val powerLevel = sharedPreferences.getInt("power_level", 20)
            val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -70)

            val gracePeriod = sharedPreferences.getLong("grace_period", 0L) // **PERBAIKAN**: Set ke 0 untuk response instant
            
            val newSettings = RfidScanSettings(
                powerLevel = powerLevel,
                duplicateRemovalEnabled = true, // Always enabled
                gracePeriodMs = gracePeriod,
                rssiThreshold = rssiThreshold
            )
            
            updateRfidScanSettings(newSettings)
            ToastUtils.showHighToastWithCooldown(this, "RFID settings applied successfully")
            Log.d(TAG, "🔥 RFID settings applied: $newSettings")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error applying settings: ${e.message}", e)
            ToastUtils.showHighToastWithCooldown(this, "Error applying settings: ${e.message}")
        }
    }
    
    // Legacy power level and duplicate removal methods removed - now handled by RfidScanManager
    
    /**
     * Get RSSI threshold from settings
     */
    private fun getRSSIThreshold(): Int {
        val sharedPrefs = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
        return sharedPrefs.getInt("rssi_threshold", -70)
    }
    
    /**
     * Hapus RSSI Debug Mode dari SharedPreferences
     */
    private fun removeRSSIDebugMode() {
        try {
            val sharedPrefs = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
            if (sharedPrefs.contains("rssi_debug_mode")) {
                sharedPrefs.edit().remove("rssi_debug_mode").apply()
                Log.d("MainActivity", "🔥 RSSI Debug Mode dihapus dari SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "🔥 Error removing RSSI Debug Mode: ${e.message}")
        }
    }
    
    // SwipeRefresh dinonaktifkan
    private fun setupSwipeRefresh() { /* no-op */ }
    
    /**
     * Perform batch lookup untuk semua RFID yang terdeteksi (OPTIMIZED)
     */
    private fun performBatchLookup() {
        try {
            Log.d("MainActivity", "🔥 Starting OPTIMIZED batch lookup for all detected RFIDs")
            
            // Get all detected RFIDs from ViewModel
            val detectedRfids = viewModel.getAllDetectedRfids()
            
            if (detectedRfids.isEmpty()) {
                Log.d("MainActivity", "ℹ️ No RFIDs detected for batch lookup")
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Tidak ada RFID yang terdeteksi", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d("MainActivity", "🔥 OPTIMIZED batch lookup for ALL ${detectedRfids.size} RFIDs (including already scanned)")
            
            // Show progress message
            Toast.makeText(this, "Memproses SEMUA ${detectedRfids.size} RFID (10 per chunk)...", Toast.LENGTH_SHORT).show()
            
            // Perform batch lookup in background
            lifecycleScope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val successCount = viewModel.performBatchLookup(detectedRfids)
                    val processingTime = System.currentTimeMillis() - startTime
                    
                    runOnUiThread {
                        binding.swipeRefreshLayout.isRefreshing = false
                        
                        if (successCount > 0) {
                            val message = if (successCount == detectedRfids.size) {
                                "✅ Semua ${successCount} RFID berhasil diproses (${processingTime}ms)"
                            } else {
                                "✅ ${successCount}/${detectedRfids.size} RFID diproses (${processingTime}ms)"
                            }
                            
                            Toast.makeText(
                                this@CekPicklistActivity, 
                                message, 
                                Toast.LENGTH_LONG
                            ).show()
                            Log.d("MainActivity", "✅ OPTIMIZED batch lookup completed: $successCount RFIDs processed in ${processingTime}ms")
                        } else {
                            Toast.makeText(
                                this@CekPicklistActivity, 
                                "❌ Batch lookup gagal - coba lagi", 
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.w("MainActivity", "⚠️ Batch lookup failed for all RFIDs")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Error during batch lookup: ${e.message}", e)
                    runOnUiThread {
                        binding.swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(
                            this@CekPicklistActivity, 
                            "Error batch lookup: ${e.message}", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error starting batch lookup: ${e.message}", e)
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    
    // AsyncTask untuk inisialisasi RFID Reader
    /* Legacy AsyncTask removed
        private var loadingDialog: androidx.appcompat.app.AlertDialog? = null
        
        override fun doInBackground(vararg params: String?): Boolean {
            Log.d("MainActivity", "=== INIT TASK START ===")
            return try {
                Log.d("MainActivity", "Starting RFID initialization...")
                val result = false
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
            Log.d("MainActivity", "Showing loading dialog...")
            loadingDialog = LoadingAnimationHelper.showLoadingDialog(
                this@MainActivity,
                "Inisialisasi RFID Reader...",
                false
            )
        }
        
        override fun onPostExecute(result: Boolean) {
            super.onPostExecute(result)
            Log.d("MainActivity", "RFID initialization completed with result: $result")
            loadingDialog?.dismiss()
            if (result) {
                Log.d("MainActivity", "RFID Reader berhasil diinisialisasi")
                ToastUtils.showHighToastWithCooldown(this@MainActivity, "RFID Reader berhasil diinisialisasi")
                // isRfidSupported = true
                
                // Apply settings after successful initialization
                applyPowerLevelToRFID()
                applyDuplicateRemovalToRFID()
            } else {
                Log.e("MainActivity", "RFID Reader gagal diinisialisasi")
                ToastUtils.showHighLongToast(this@MainActivity, "Device tidak mendukung RFID SDK atau gagal inisialisasi")
                // isRfidSupported = false
            }
    */
    
    private fun observeViewModel() {
        Log.d(TAG, "🔥 Setting up ViewModel observers")
        
        // Observe picklist items
        viewModel.filteredItems.observe(this) { items ->
            Log.d(TAG, "🔥 Filtered items updated: ${items.size} items")
            picklistAdapter.updateItems(items)
            
            // Update UI visibility based on items
            updateUIVisibility(items)
            
            // Summary cards kini dikelola oleh observer qtySummary di ViewModel
        }
        
        // **PERBAIKAN**: Observe ALL picklist items untuk completion check (bukan filtered items)
        viewModel.picklistItems.observe(this) { allItems ->
            Log.d(TAG, "🔥 All picklist items updated: ${allItems.size} items")
            
            // **PERBAIKAN KRITIS**: Seed RfidScanManager dengan data dari database SETELAH data selesai dimuat
            // Ini mencegah RFID yang sudah pernah di-scan dihitung ulang
            val currentPicklist = viewModel.getCurrentPicklistNumber()
            if (currentPicklist != null && allItems.isNotEmpty()) {
                Log.d(TAG, "🔥 Data loaded, seeding RfidScanManager for picklist: $currentPicklist")
                seedRfidScanManagerWithDatabaseData(currentPicklist)
            }
            
            // Check for completion dengan SEMUA items (termasuk yang sudah complete)
            checkCompletionStatus(allItems)
        }
        
        // Observe RFID detection count
        viewModel.rfidDetectionCount.observe(this) { count ->
            Log.d(TAG, "🔥 RFID detection count updated: $count")
            binding.tvRfidDetected.text = count.toString()
        }

        // Observe Qty Summary to ensure Total Qty = sum(qtyPl) and Remaining shown correctly
        viewModel.qtySummary.observe(this) { summary ->
            try {
                binding.tvTotalQty.text = summary.totalQtyPl.toString()
                binding.tvRemainingQty.text = summary.remainingQty.toString()
                Log.d(TAG, "🔥 Summary cards updated from ViewModel: total=${summary.totalQtyPl}, remaining=${summary.remainingQty}")
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Failed to update summary cards from qtySummary: ${t.message}")
            }
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(this) { error ->
            if (error.isNotEmpty()) {
                Log.e(TAG, "🔥 ViewModel error: $error")
                ToastUtils.showHighToastWithCooldown(this, error)
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "🔥 Loading state: $isLoading")
            // Update UI based on loading state if needed
        }
        
        Log.d(TAG, "🔥 ViewModel observers setup complete")
    }
    
    /**
     * Update UI visibility based on items
     */
    private fun updateUIVisibility(items: List<PicklistItem>) {
        if (items.isEmpty()) {
            // Show empty state
            binding.rvPicklistItems.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
            Log.d(TAG, "🔥 Showing empty state - no items")
        } else {
            // Show RecyclerView with items
            binding.llEmptyState.visibility = View.GONE
            binding.rvPicklistItems.visibility = View.VISIBLE
            Log.d(TAG, "🔥 Showing RecyclerView with ${items.size} items")
        }
    }
    
    /**
     * Check completion status and show animation if all items are complete
     */
    private fun checkCompletionStatus(items: List<PicklistItem>) {
        Log.d(TAG, "🔥 checkCompletionStatus called with ${items.size} items")
        
        if (hasShownCompletionAnimation) {
            Log.d(TAG, "🔥 Completion animation already shown, skipping")
            return
        }
        
        if (items.isEmpty()) {
            Log.d(TAG, "🔥 No items to check completion status")
            return
        }
        
        // **PERBAIKAN KRITIS**: Cek completion dengan syarat TIDAK ada non-picklist atau overscan
        val picklistItems = items.filter { it.qtyPl > 0 }
        val nonPicklistItems = items.filter { it.qtyPl == 0 }
        val overscanItems = items.filter { it.qtyPl > 0 && it.qtyScan > it.qtyPl }
        
        Log.d(TAG, "🔥 Filtered items: ${picklistItems.size} picklist items, ${nonPicklistItems.size} non-picklist items, ${overscanItems.size} overscan items")
        
        // Log detail setiap picklist item
        picklistItems.forEach { item ->
            val isComplete = item.isComplete()
            val isOverscan = item.qtyScan > item.qtyPl
            Log.d(TAG, "🔥 Picklist Item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}, isComplete=$isComplete, isOverscan=$isOverscan")
        }
        
        // Log detail setiap non-picklist item
        nonPicklistItems.forEach { item ->
            Log.d(TAG, "🔥 Non-Picklist Item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan} (BLOCKS completion)")
        }
        
        // Log detail setiap overscan item
        overscanItems.forEach { item ->
            Log.d(TAG, "🔥 Overscan Item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan} (BLOCKS completion)")
        }
        
        // **PERBAIKAN**: Cek completion dengan syarat TIDAK ada non-picklist atau overscan
        val allPicklistItemsComplete = picklistItems.all { it.isComplete() }
        val hasNonPicklistItems = nonPicklistItems.isNotEmpty()
        val hasOverscanItems = overscanItems.isNotEmpty()
        val completedCount = picklistItems.count { it.isComplete() }
        val totalPicklistCount = picklistItems.size
        
        Log.d(TAG, "🔥 Completion check: $completedCount/$totalPicklistCount picklist items complete, allComplete=$allPicklistItemsComplete")
        Log.d(TAG, "🔥 Blocking conditions: hasNonPicklist=$hasNonPicklistItems, hasOverscan=$hasOverscanItems")
        
        // **PERBAIKAN**: Hanya trigger animasi jika SEMUA picklist items complete DAN TIDAK ada non-picklist atau overscan
        if (allPicklistItemsComplete && picklistItems.isNotEmpty() && !hasNonPicklistItems && !hasOverscanItems) {
            Log.d(TAG, "🎉 All picklist items completed with no non-picklist or overscan items! Showing completion animation")
            hasShownCompletionAnimation = true
            showCompletionAnimation()
        } else {
            val blockingReasons = mutableListOf<String>()
            if (!allPicklistItemsComplete) blockingReasons.add("not all picklist items complete")
            if (hasNonPicklistItems) blockingReasons.add("has non-picklist items")
            if (hasOverscanItems) blockingReasons.add("has overscan items")
            
            Log.d(TAG, "🔥 Completion blocked: ${blockingReasons.joinToString(", ")}")
        }
    }
    
    private fun startScanning() {
        Log.d(TAG, "🔥 startScanning dipanggil")
        
        if (!isScanning) {
            Log.d(TAG, "🔥 Memulai scanning RFID...")
            
            // Apply settings before starting scan
            applySettingsFromSharedPreferences()
            
            // Use BaseRfidActivity method
            startRfidScanning()
        } else {
            Log.d(TAG, "🔥 Scanning sudah berjalan")
        }
    }
    
    private fun stopScanning() {
        Log.d(TAG, "🔥 stopScanning dipanggil")
        // Use BaseRfidActivity method
        stopRfidScanningWithGracePeriod()
    }
    
    private fun updateSummaryCards(totalQty: Int, remainingQty: Int) {
        binding.tvTotalQty.text = totalQty.toString()
        binding.tvRemainingQty.text = remainingQty.toString()
    }
    
    
    /**
     * Tampilkan animasi completion confetti ball dengan suara terompet meriah dan tepuk tangan
     */
    private fun showCompletionAnimation() {
        Log.d("MainActivity", "🔥 Menampilkan animasi completion dengan overlay yang memenuhi layar")
        
        // **PERBAIKAN KRITIS**: Save data baru ke Supabase SEBELUM update status
        Log.d(TAG, "💾 Saving new data to Supabase before completion...")
        viewModel.saveDataToSupabaseOnCompletion()
        
        // **PERBAIKAN BARU**: Update status picklist ke "completed" di database
        viewModel.updatePicklistStatus("completed")
        
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
            
            // Hapus view setelah 3 detik dan navigasi kembali ke PicklistInputActivity
            overlayView.postDelayed({
                overlayView.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction {
                        binding.root.removeView(overlayView)
                        
                        // Navigasi kembali ke PicklistInputActivity setelah animasi selesai
                        Log.d("MainActivity", "🎉 Completion animation finished, navigating back to PicklistInputActivity")
                        navigateBackToPicklistInput()
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
        
        // Buat icon smile besar menggunakan icon aplikasi
        val smileImageView = ImageView(this)
        smileImageView.setImageResource(R.mipmap.ic_launcher_foreground)
        smileImageView.layoutParams = LinearLayout.LayoutParams(200, 200) // Ukuran besar
        smileImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        // Buat teks konfirmasi
        val successText = TextView(this)
        successText.text = "HADE EUY !!"
        successText.textSize = 36f
        successText.gravity = Gravity.CENTER
        successText.setTextColor(android.graphics.Color.parseColor("#FFD700")) // Emas untuk lebih ceria
        successText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // Buat teks sub-konfirmasi
        val subText = TextView(this)
        subText.text = "Semua item berhasil di-scan! 🎉"
        subText.textSize = 18f
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
        centerContainer.addView(smileImageView)
        centerContainer.addView(successText)
        centerContainer.addView(subText)
        centerContainer.addView(confettiView)
        
        // Tambahkan container ke overlay
        overlayLayout.addView(centerContainer)
        
        // Animasi scale untuk efek pop
        centerContainer.scaleX = 0.3f
        centerContainer.scaleY = 0.3f
        
        // Animasi khusus untuk icon smile dengan efek bounce
        smileImageView.scaleX = 0.1f
        smileImageView.scaleY = 0.1f
        smileImageView.rotation = -180f
        
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
                
                // Animasi khusus untuk icon smile
                smileImageView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .rotation(0f)
                    .setDuration(600)
                    .setInterpolator(android.view.animation.BounceInterpolator())
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
            
            // **PERBAIKAN**: Validasi qtyScan untuk menghindari overscan
            // **EXCEPTION**: Jangan batasi qtyScan untuk item non-picklist (qtyPl=0) dan item overscan
            // Item overscan harus tetap menampilkan qtyScan yang sebenarnya untuk menunjukkan status kuning
            val validatedQtyScan = distinctItems.sumOf { item ->
                // Tidak ada pembatasan qtyScan - biarkan overscan ditampilkan dengan benar
                item.qtyScan
            }
            
            // **VALIDASI DATA**: Deteksi duplikasi dan overscan
            if (groupedItems.size > distinctItems.size) {
                Log.w("MainActivity", "🔥 DUPLICATE DATA DETECTED: $key - ${groupedItems.size} raw items, ${distinctItems.size} distinct items")
            }
            
            if (validatedQtyScan > totalQtyPl) {
                Log.w("MainActivity", "🔥 OVERSCAN DETECTED: $key - qtyScan=$validatedQtyScan > qtyPl=$totalQtyPl")
                Log.w("MainActivity", "🔥 OVERSCAN DETAIL: ${validatedQtyScan - totalQtyPl} items overscan")
            }
            
            Log.d("MainActivity", "🔥 Group '$key': ${groupedItems.size} raw items, ${distinctItems.size} distinct items")
            Log.d("MainActivity", "🔥 Group '$key': totalQtyPl=$totalQtyPl, validatedQtyScan=$validatedQtyScan")
            
            PicklistItem(
                id = firstItem.id,
                noPicklist = firstItem.noPicklist,
                articleId = firstItem.articleId,
                articleName = firstItem.articleName,
                size = firstItem.size,
                productId = firstItem.productId,
                qtyPl = totalQtyPl,
                qtyScan = validatedQtyScan,
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
        Log.i("MainActivity", "🔥 Total Qty Scanned (VALIDATED): $totalQtyScan")
        Log.i("MainActivity", "🔥 Total Overscan: $totalOverscan")
        Log.i("MainActivity", "🔥 Completed Items: $completedItems")
        Log.i("MainActivity", "🔥 Overscan Items: $overscanItems")
        Log.i("MainActivity", "🔥 === END GROUPING SUMMARY ===")
        
        result.forEach { item ->
            val isComplete = item.isComplete()
            Log.d("MainActivity", "🔥 Final item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}, isComplete=$isComplete")
        }
        Log.d("MainActivity", "🔥 === END GROUPING ===")
        
        return result
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Beri kesempatan BaseRfidActivity menangani tombol scan
        if (super.onKeyDown(keyCode, event)) return true
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
                    // BaseRfidActivity sudah menangani tombol scan
                    return true
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
        
        // **PERBAIKAN**: Biarkan BaseRfidActivity yang menangani key events untuk konsistensi
        // Handle tombol fisik untuk stop scanning
        when (keyCode) {
            // Tombol Scan Trigger (keycode 293) - Release to stop
            293 -> {
                Log.d("MainActivity", "🔥 Tombol fisik dilepas: SCAN_TRIGGER (293) - Delegating to BaseRfidActivity")
                // **PERBAIKAN**: Biarkan BaseRfidActivity yang menangani untuk konsistensi
                return super.onKeyUp(keyCode, event)
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
        Log.d(TAG, "🔥 toggleScanning dipanggil - isScanning: $isScanning")
        // Use BaseRfidActivity method
        toggleRfidScanning()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop RFID scanning
        stopRfidScanning()
        
        // Cancel completion check job to prevent memory leaks
        completionCheckJob?.cancel()
        completionCheckJob = null
        
        // Release sound resources
        releaseSoundPool()
        
        Log.d(TAG, "🔥 CekPicklistActivity destroyed")
    }
}