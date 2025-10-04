package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.cekpicklist.base.BaseRfidActivity
import androidx.lifecycle.Observer
import com.example.cekpicklist.utils.LoadingAnimationHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cekpicklist.adapter.RelocationAdapter
import com.example.cekpicklist.adapter.WarehouseArrayAdapter
import com.example.cekpicklist.data.RelocationItem
import com.example.cekpicklist.data.TagStatus
import com.example.cekpicklist.data.Warehouse
import com.example.cekpicklist.utils.WarehouseSelectionDialog
import com.example.cekpicklist.utils.RfidScanSettings
import com.example.cekpicklist.databinding.ActivityRelocationBinding
import com.example.cekpicklist.viewmodel.RelocationViewModel
// Legacy RFID SDK imports removed - handled by BaseRfidActivity/RfidScanManager

class RelocationActivity : BaseRfidActivity() {
    companion object {
        private const val TAG = "RelocationActivity"
    }
    
    private lateinit var binding: ActivityRelocationBinding
    private val viewModel: RelocationViewModel by viewModels()
    private lateinit var relocationAdapter: RelocationAdapter
    
    // RFID handled by BaseRfidActivity/RfidScanManager
    
    // Cooldown untuk tombol scan (sama seperti MainActivity)
    private var lastScanButtonClickTime = 0L
    private val SCAN_BUTTON_COOLDOWN_MS = 2000L // 2 detik cooldown
    
    // Beep sound handled by BaseRfidActivity
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRelocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupWarehouseDropdowns()
        setupObservers()
        setupClickListeners()
        // Beep sound handled by BaseRfidActivity
        
        // Apply RFID settings from SharedPreferences
        applySettingsFromSharedPreferences()
        
        // **VERIFIKASI SETTINGS**: Log settings yang diterapkan
        verifyRfidSettings()
        
        Log.d(TAG, "🔥 RelocationActivity created")

        // Fetch warehouses sudah dipanggil di init ViewModel; tidak perlu dipanggil lagi di sini
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        Log.d(TAG, "🔥 onCreateOptionsMenu called")
        menuInflater.inflate(R.menu.relocation_menu, menu)
        Log.d(TAG, "🔥 Menu inflated: ${menu?.size()} items")
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                Log.d(TAG, "🔥 Settings menu clicked - navigasi ke SettingsActivity")
                navigateToSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun navigateToSettings() {
        Log.d(TAG, "🔥 Navigasi ke SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupRecyclerView() {
        relocationAdapter = RelocationAdapter()
        
        binding.rvRelocationItems.apply {
            layoutManager = LinearLayoutManager(this@RelocationActivity)
            adapter = relocationAdapter
        }
    }
    
    private fun setupWarehouseDropdowns() {
        // Setup current location modal selection
        binding.actvCurrentLocation.apply {
            // Remove adapter setup - we'll use modal instead
            setOnClickListener {
                showWarehouseSelectionModal("Current Location") { selectedWarehouse ->
                    setText(selectedWarehouse.warehouseName, false)
                    viewModel.setSelectedCurrentWarehouse(selectedWarehouse)
                    Log.d(TAG, "🔥 Current warehouse selected: ${selectedWarehouse.warehouseName}")
                    
                    // **AUTO-REFRESH**: Refresh listbox saat warehouse berubah
                    refreshListboxData()
                }
                Log.d(TAG, "🔥 Current location clicked - showing modal")
            }
            // Remove focus listener - we only want click
        }
        
        // Setup target location modal selection
        binding.actvTargetLocation.apply {
            // Remove adapter setup - we'll use modal instead
            setOnClickListener {
                showWarehouseSelectionModal("Target Location") { selectedWarehouse ->
                    setText(selectedWarehouse.warehouseName, false)
                    viewModel.setSelectedTargetWarehouse(selectedWarehouse)
                    Log.d(TAG, "🔥 Target warehouse selected: ${selectedWarehouse.warehouseName}")
                    
                    // **NO AUTO-REFRESH**: Target location tidak perlu refresh listbox
                    // refreshListboxData() // Disabled untuk target location
                }
                Log.d(TAG, "🔥 Target location clicked - showing modal")
            }
            // Remove focus listener - we only want click
        }
    }
    
    private fun showWarehouseSelectionModal(title: String, onWarehouseSelected: (Warehouse) -> Unit) {
        val cachedWarehouses = viewModel.getCachedWarehouses()
        Log.d("RelocationActivity", "🔥 Cached warehouses: ${cachedWarehouses?.size ?: 0}")
        
        if (cachedWarehouses != null && cachedWarehouses.isNotEmpty()) {
            Log.d("RelocationActivity", "🔥 Using cached warehouses: ${cachedWarehouses.size}")
            WarehouseSelectionDialog(this, cachedWarehouses, onWarehouseSelected).show()
        } else {
            Log.d("RelocationActivity", "🔥 Cache empty or expired, loading warehouses from API...")
            // Load warehouses first if cache is empty or expired
            viewModel.loadWarehouses()
            viewModel.warehouses.observe(this) { warehouses ->
                Log.d("RelocationActivity", "🔥 Warehouses loaded from API: ${warehouses.size}")
                if (warehouses.isNotEmpty()) {
                    WarehouseSelectionDialog(this, warehouses, onWarehouseSelected).show()
                } else {
                    Toast.makeText(this, "Tidak ada warehouse tersedia", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupObservers() {
        // Set callback untuk clear RfidScanManager setelah submit berhasil
        viewModel.setOnRfidScanManagerClear {
            try {
                rfidScanManager.clearAllData()
                Log.d(TAG, "🔥 RfidScanManager data cleared after successful submit")
            } catch (t: Throwable) {
                Log.w(TAG, "⚠️ Failed to clear RfidScanManager data: ${t.message}")
            }
        }
        
        // RFID Detection Count - Hanya update UI, tidak auto lookup
        viewModel.rfidDetectionCount.observe(this, Observer { count ->
            binding.tvRfidDetected.text = count.toString()
            Log.d(TAG, "🔥 RFID detected count updated: $count")
        })
        
        // RFID Valid Count
        viewModel.rfidValidCount.observe(this, Observer { count ->
            binding.tvRfidValid.text = count.toString()
        })
        
        // RFID NA Count
        viewModel.rfidNaCount.observe(this, Observer { count ->
            binding.tvRfidNa.text = count.toString()
        })
        
        // Relocation Items
        viewModel.relocationItems.observe(this, Observer { items ->
            relocationAdapter.updateItems(items)
        })
        
        // Warehouses - Cache is handled automatically by ViewModel
        viewModel.warehouses.observe(this, Observer { warehouses ->
            Log.d(TAG, "🔥 Warehouses loaded: ${warehouses.size} warehouses")
        })
        
        // Selected Current Warehouse - refresh tampilan berdasarkan cache lokal
        viewModel.selectedCurrentWarehouse.observe(this, Observer { warehouse ->
            if (warehouse != null) {
                Log.d(TAG, "🔥 Current warehouse changed to: ${warehouse.warehouseName}")
                try {
                    val localResults = rfidScanManager.getAllLookupResults()
                    viewModel.applyLookupResults(localResults)
                } catch (t: Throwable) {
                    Log.w(TAG, "⚠️ Failed to refresh on warehouse change: ${t.message}")
                }
            }
        })
        
        // Selected Target Warehouse - no-op (tidak memicu refresh atau loading)
        viewModel.selectedTargetWarehouse.observe(this, Observer { warehouse ->
            if (warehouse != null) {
                Log.d(TAG, "ℹ️ Target warehouse changed (no-op): ${warehouse.warehouseName}")
            }
        })
        
        // Selected Tag Status - refresh tampilan berdasarkan cache lokal
        viewModel.selectedTagStatus.observe(this, Observer { tagStatus ->
            if (tagStatus != null) {
                Log.d(TAG, "🔥 Tag status changed to: $tagStatus")
                try {
                    val localResults = rfidScanManager.getAllLookupResults()
                    viewModel.applyLookupResults(localResults)
                } catch (t: Throwable) {
                    Log.w(TAG, "⚠️ Failed to refresh on tag status change: ${t.message}")
                }
            }
        })
        
        // Loading State - Show loading animation seperti SwipeRefreshLayout
        viewModel.isLoading.observe(this, Observer { isLoading ->
            if (isLoading) {
                LoadingAnimationHelper.showLoadingToast(this, "Processing...", Toast.LENGTH_SHORT)
            }
        })
        
        // Error Message
        viewModel.errorMessage.observe(this, Observer { message ->
            if (message != null) {
                // Toast dihilangkan sesuai permintaan user
                Log.e(TAG, "❌ Error: $message")
            }
        })
        
        // Success Message
        viewModel.successMessage.observe(this, Observer { message ->
            if (message != null) {
                // Toast dihilangkan sesuai permintaan user
                Log.d(TAG, "✅ Success: $message")
            }
        })
    }
    
    private fun setupClickListeners() {
        // Set default Tag Status to TAGGED for both current and target
        binding.rbTagged.isChecked = true
        binding.rbTargetTagged.isChecked = true
        
        // Initialize ViewModel dengan default tag status
        viewModel.setSelectedTagStatus(TagStatus.TAGGED)
        
        // Radio Group for Current Tag Status - trigger lookup
        binding.rgTagStatus.setOnCheckedChangeListener { _, checkedId ->
            val tagStatus = when (checkedId) {
                R.id.rbTagged -> TagStatus.TAGGED
                R.id.rbMaster -> TagStatus.MASTER
                else -> null
            }
            Log.d(TAG, "🔥 Current tag status changed to: $tagStatus")
            // Update ViewModel untuk trigger observer
            viewModel.setSelectedTagStatus(tagStatus)
            
            // **AUTO-REFRESH**: Refresh listbox saat radio current location berubah
            refreshListboxData()
        }
        
        // Radio Group for Target Tag Status - hanya untuk submit; tidak memicu filter/refresh
        binding.rgTargetTagStatus.setOnCheckedChangeListener { _, checkedId ->
            val tagStatus = when (checkedId) {
                R.id.rbTargetTagged -> TagStatus.TAGGED
                R.id.rbTargetMaster -> TagStatus.MASTER
                else -> null
            }
            Log.d(TAG, "🔥 Target tag status changed to: $tagStatus")
            // No-op: jangan update selectedTagStatus (filter current)
        }
        
        // Scan Button - Toggle scanning
        binding.btnScan.setOnClickListener {
            toggleScanning()
        }
        
        // Submit Button
        binding.btnSubmit.setOnClickListener {
            performSubmit()
        }
        
        // Clear All Button
        binding.btnClear.setOnClickListener {
            clearAllRfids()
        }
    }
    
    /**
     * Refresh listbox data saat warehouse atau radio current location berubah
     */
    private fun refreshListboxData() {
        Log.d(TAG, "🔄 Refreshing listbox data due to warehouse/radio change")
        
        // Trigger re-lookup dengan parameter yang sudah dipilih
        val currentWarehouse = viewModel.getSelectedCurrentWarehouse()
        val tagStatus = viewModel.getSelectedTagStatus()
        
        if (currentWarehouse != null && tagStatus != null) {
            Log.d(TAG, "🔄 Auto-refresh: Current warehouse=${currentWarehouse.warehouseName}, Tag status=$tagStatus")
            
            // Trigger lookup dengan parameter yang sudah dipilih
            viewModel.performLookupWithCurrentSettings()
        } else {
            Log.d(TAG, "🔄 Auto-refresh skipped: Missing warehouse or tag status")
        }
    }
    
    private fun performManualLookup() {
        val tagStatus = viewModel.getSelectedTagStatus()
        
        if (tagStatus == null) {
            Log.d(TAG, "⚠️ Manual lookup skipped - no tag status selected")
            return
        }
        
        if (viewModel.getScannedRfids().isEmpty()) {
            Log.d(TAG, "⚠️ Manual lookup skipped - no RFIDs scanned")
            return
        }
        
        Log.d(TAG, "🔥 Performing manual lookup using RfidScanManager results...")
        Log.d(TAG, "🔥 Tag Status: $tagStatus")
        Log.d(TAG, "🔥 RFIDs: ${viewModel.getScannedRfids()}")
        
        // Konsumsi hasil lookup yang sudah ada di RfidScanManager
        try {
            val localResults = rfidScanManager.getAllLookupResults()
            viewModel.applyLookupResults(localResults)
            Log.d(TAG, "🔥 Manual lookup completed: ${localResults.size} items from RfidScanManager")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in manual lookup: ${e.message}")
        }
    }
    
    private fun performSubmit() {
        val targetTagStatus = getSelectedTargetTagStatus()
        
        if (targetTagStatus == null) {
            Toast.makeText(this, "Pilih target tag status", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (viewModel.getValidRfids().isEmpty()) {
            Toast.makeText(this, "Tidak ada RFID valid untuk di-submit", Toast.LENGTH_SHORT).show()
            return
        }
        
        val targetWarehouseId = viewModel.getSelectedTargetWarehouseId()
        if (targetWarehouseId.isNullOrEmpty()) {
            Toast.makeText(this, "Pilih target location", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Tampilkan dialog konfirmasi sebelum submit
        showSubmitConfirmationDialog(targetTagStatus, targetWarehouseId)
    }
    
    /**
     * Tampilkan dialog konfirmasi untuk submit relocation
     */
    private fun showSubmitConfirmationDialog(targetTagStatus: TagStatus, targetWarehouseId: String) {
        // Get warehouse names
        val currentWarehouse = viewModel.getSelectedCurrentWarehouse()
        val targetWarehouse = viewModel.getSelectedTargetWarehouse()
        val currentTagStatus = getSelectedTagStatus()
        
        val currentWarehouseName = currentWarehouse?.warehouseName ?: "Unknown"
        val targetWarehouseName = targetWarehouse?.warehouseName ?: "Unknown"
        val currentTagStatusText = currentTagStatus?.value ?: "Unknown"
        val targetTagStatusText = targetTagStatus.value
        val validRfidCount = viewModel.getValidRfids().size
        
        val message = """
            Apakah Anda akan memindahkan $validRfidCount dari:
            
            📍 Warehouse: $currentWarehouseName
            🏷️ Tag Status: $currentTagStatusText
            
            Ke:
            
            📍 Warehouse: $targetWarehouseName  
            🏷️ Tag Status: $targetTagStatusText
            
       
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Konfirmasi Relocation")
            .setMessage(message)
            .setPositiveButton("Submit") { _, _ ->
                // User confirmed - proceed with submit
                Log.d(TAG, "🔥 User confirmed submit - proceeding...")
                Log.d(TAG, "🔥 Target Tag Status: $targetTagStatus")
                Log.d(TAG, "🔥 Target Warehouse: $targetWarehouseId")
                Log.d(TAG, "🔥 Valid RFIDs: ${viewModel.getValidRfids()}")
                
                viewModel.submitRelocation(targetTagStatus.value)
            }
            .setNegativeButton("Batal", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    
    private fun getSelectedTagStatus(): TagStatus? {
        return when (binding.rgTagStatus.checkedRadioButtonId) {
            R.id.rbTagged -> TagStatus.TAGGED
            R.id.rbMaster -> TagStatus.MASTER
            else -> null
        }
    }
    
    /**
     * Get selected target tag status from radio buttons (for submit)
     */
    private fun getSelectedTargetTagStatus(): TagStatus? {
        return when (binding.rgTargetTagStatus.checkedRadioButtonId) {
            R.id.rbTargetTagged -> TagStatus.TAGGED
            R.id.rbTargetMaster -> TagStatus.MASTER
            else -> null
        }
    }
    
    /**
     * Toggle RFID scanning on/off
     */
    private fun toggleScanning() {
        // Cek cooldown untuk mencegah scan berulang terlalu cepat
        val currentTime = System.currentTimeMillis()
        val timeSinceLastClick = currentTime - lastScanButtonClickTime
        
        if (timeSinceLastClick < SCAN_BUTTON_COOLDOWN_MS) {
            val remainingCooldown = SCAN_BUTTON_COOLDOWN_MS - timeSinceLastClick
            Log.d(TAG, "🔥 Scan button cooldown active - remaining: ${remainingCooldown}ms")
            // Toast dihilangkan sesuai permintaan user
            return
        }
        
        lastScanButtonClickTime = currentTime
        
        // **PERBAIKAN KRITIS**: Apply settings sebelum toggle scanning
        if (!isScanning) {
            applySettingsFromSharedPreferences()
        }
        
        toggleRfidScanning()
    }

    // ================= BaseRfidActivity Callbacks =================
    override fun handleRfidDetected(epc: String, rssi: Int) {
        // RFID sudah di-handle oleh RfidScanManager dengan automatic lookup
        // Hanya update counter untuk UI
        val isNew = viewModel.addRfid(epc)
        if (isNew) {
            super.playBeepSound()
            Log.d(TAG, "🔥 RFID detected (UNIQUE): $epc (RSSI=$rssi)")
        } else {
            Log.d(TAG, "ℹ️ RFID detected (DUPLICATE): $epc (RSSI=$rssi)")
        }
    }

    override fun handleScanStateChanged(isScanning: Boolean) {
        this.isScanning = isScanning
        binding.btnScan.text = if (isScanning) "Stop" else "Start"
    }

    override fun handleRfidError(error: String) {
        Log.e(TAG, "❌ RFID error: $error")
    }

    override fun handleGracePeriodCompleted() {
        // Lookup setelah grace period sudah ditangani oleh ViewModel.stopScanningWithGracePeriod()
        Log.d(TAG, "🔥 Grace period completed (Base callback)")
    }

    override fun handleLookupRequired(rfidList: List<String>) {
        // RfidScanManager sudah melakukan lookup dan menyimpan hasil di lokal storage
        // Konsumsi hasil lookup dari RfidScanManager dengan optimasi
        try {
            val localResults = rfidScanManager.getAllLookupResults()
            val stats = rfidScanManager.getLocalDataStats()
            
            Log.d(TAG, "🔥 Lookup results consumed from RfidScanManager: ${localResults.size} items")
            Log.d(TAG, "📊 Local data stats: $stats")
            
            // Filter hanya EPC yang ada di rfidList untuk efisiensi
            val relevantResults = localResults.filterKeys { epc -> 
                rfidList.contains(epc) 
            }
            
            Log.d(TAG, "🎯 Processing ${relevantResults.size} relevant EPCs from ${localResults.size} total cached results")
            
            // Apply lookup results ke ViewModel
            viewModel.applyLookupResults(relevantResults)
            
            // Log statistik untuk monitoring
            val foundCount = relevantResults.values.count { it.articleName != "NOT_FOUND" }
            val notFoundCount = relevantResults.values.count { it.articleName == "NOT_FOUND" }
            Log.d(TAG, "📈 Processed results: $foundCount found, $notFoundCount not found")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error consuming lookup results: ${e.message}")
        }
    }

    override fun handleDataCleared() {
        binding.tvRfidDetected.text = "0"
        relocationAdapter.updateItems(emptyList())
    }
    
    
    /**
     * Clear all scanned RFIDs
     */
    private fun clearAllRfids() {
        // Hentikan scanning agar tidak ada EPC baru masuk saat proses clear
        if (isRfidScanning()) {
            stopRfidScanning()
        }

        // Clear di level ViewModel (UI state)
        viewModel.clearAllRfids()

        // Clear di level engine (manager) agar buffer EPC benar-benar kosong
        try {
            rfidScanManager.clearAllData()
            Log.d(TAG, "🔥 RFID buffer cleared in RfidScanManager")
            
            // **PERBAIKAN BARU**: Re-seed dengan data dari database setelah clear (jika ada)
            // Untuk RelocationActivity, kita tidak perlu re-seeding karena tidak ada picklist yang di-load sebelumnya
            // Tapi kita bisa menambahkan log untuk konsistensi
            Log.d(TAG, "🔥 RelocationActivity clear completed - no re-seeding needed")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ Failed clearing manager data: ${t.message}")
        }

        // Reset counter UI
        binding.tvRfidDetected.text = "0"

        // Toast dihilangkan sesuai permintaan user
        Log.d(TAG, "🔥 All RFIDs cleared (ViewModel + Manager)")
    }
    
    /**
     * Method untuk menambahkan RFID dari external source (misalnya dari RFID scanner)
     */
    fun addRfid(epc: String) {
        val isNew = viewModel.addRfid(epc)
        Log.d(TAG, "🔥 RFID added from external: $epc (isNew=$isNew)")
    }
    
    
    /**
     * Initialize Sound Pool untuk beep sound
     */
    // Sound initialization removed; use BaseRfidActivity.playBeepSound()
    
    /**
     * Play beep sound
     */
    // Local playBeepSound removed; use BaseRfidActivity.playBeepSound()
    
    /**
     * Initialize RFID Reader
     */
    private fun initializeRfidReader() {
        // Legacy SDK init removed - handled by BaseRfidActivity
    }
    
    /**
     * Start RFID Scanning
     */
    // Legacy RFID scanning removed - handled by BaseRfidActivity
    
    /**
     * Stop RFID Scanning
     */
    // Legacy stop removed - use BaseRfidActivity
    
    /**
     * Stop RFID Scanning dengan grace period untuk menangkap RFID terlambat
     */
    // Legacy stop with grace removed - handled by BaseRfidActivity
    
    /**
     * Update UI untuk menunjukkan grace period
     */
    private fun updateUIForGracePeriod() {
        binding.btnScan.text = "Waiting..."
        binding.btnScan.isEnabled = false
        
        // Show grace period indicator
        showGracePeriodIndicator()
        
        // Monitor grace period status
        monitorGracePeriodStatus()
    }
    
    /**
     * Show grace period indicator
     */
    // gunakan BaseRfidActivity.showGracePeriodIndicator()
    
    /**
     * Monitor grace period status
     */
    private fun monitorGracePeriodStatus() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isGracePeriodActive()) {
                    // Grace period masih aktif, check lagi
                    handler.postDelayed(this, 500)
                } else {
                    // Grace period selesai, update UI
                    updateUIAfterGracePeriod()
                }
            }
        }
        handler.post(runnable)
    }
    
    /**
     * Update UI setelah grace period selesai
     */
    private fun updateUIAfterGracePeriod() {
        binding.btnScan.text = "Start"
        binding.btnScan.isEnabled = true
        
        // Hide grace period indicator
        hideGracePeriodIndicator()
        
        Log.d(TAG, "🔥 Grace period completed, UI updated")
    }
    
    /**
     * Handle grace period cancellation dengan user feedback
     */
    private fun handleGracePeriodCancellation() {
        if (isGracePeriodActive()) {
            Log.d(TAG, "🔥 Grace period cancelled by user - starting new scan")
            
            // Show feedback bahwa grace period di-cancel
            showGracePeriodCancelledFeedback()
            
            // Cancel grace period
            cancelGracePeriod()
            
            // Update UI immediately
            updateUIAfterGracePeriod()
        }
    }
    
    /**
     * Show feedback bahwa grace period di-cancel
     */
    // gunakan BaseRfidActivity.showGracePeriodCancelledFeedback()
    
    override fun onDestroy() {
        super.onDestroy()
        stopRfidScanning()
        
        // handled by BaseRfidActivity cleanup
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (super.onKeyDown(keyCode, event)) return true
        // Log detail untuk semua tombol yang ditekan
        val keyName = getKeyName(keyCode)
        val repeatCount = event?.repeatCount ?: 0
        val action = event?.action ?: -1
        val flags = event?.flags ?: 0
        
        Log.d(TAG, "🔥 KEYCODE DETECTED: $keyName (Code: $keyCode, Repeat: $repeatCount, Action: $action, Flags: $flags)")
        
        // Handle tombol fisik untuk scanning RFID
        when (keyCode) {
            // Tombol Scan Trigger (keycode 293) - Press to start, release to stop
            293 -> {
                if (repeatCount == 0) {
                    Log.d(TAG, "🔥 Physical scan button pressed - Code: $keyCode")
                    
                    // **PERBAIKAN**: Cek cooldown untuk tombol fisik juga
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastClick = currentTime - lastScanButtonClickTime
                    
                    if (timeSinceLastClick < SCAN_BUTTON_COOLDOWN_MS) {
                        val remainingCooldown = SCAN_BUTTON_COOLDOWN_MS - timeSinceLastClick
                        Log.d(TAG, "🔥 Physical scan button cooldown active - remaining: ${remainingCooldown}ms")
                        // Toast dihilangkan sesuai permintaan user
                        return true
                    }
                    
                    lastScanButtonClickTime = currentTime
                    
                    if (!isScanning) {
                        // **PERBAIKAN KRITIS**: Apply settings sebelum start scanning
                        applySettingsFromSharedPreferences()
                        startRfidScanning()
                        binding.btnScan.text = "Stop"
                            // Toast dihilangkan sesuai permintaan user
                    }
                }
                return true
            }
            
            // Tombol lainnya - Gunakan fungsi default device
            else -> {
                Log.d(TAG, "🔥 Tombol fisik ditekan: $keyName - Code: $keyCode (Fungsi default device)")
                return super.onKeyDown(keyCode, event)
            }
        }
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (super.onKeyUp(keyCode, event)) return true
        // Log detail untuk semua tombol yang dilepas
        val keyName = getKeyName(keyCode)
        val action = event?.action ?: -1
        val flags = event?.flags ?: 0
        
        Log.d(TAG, "🔥 KEYCODE RELEASED: $keyName (Code: $keyCode, Action: $action, Flags: $flags)")
        
        // Handle tombol fisik untuk stop scanning
        when (keyCode) {
            // Tombol Scan Trigger (keycode 293) - Release to stop
            293 -> {
                Log.d(TAG, "🔥 Physical scan button released - Code: $keyCode")
                
                if (isScanning) {
                    // Stop scanning dengan grace period
                    stopRfidScanningWithGracePeriod()
                }
                return true
            }
            
            // Tombol lainnya - Gunakan fungsi default device
            else -> {
                Log.d(TAG, "🔥 Tombol fisik dilepas: $keyName - Code: $keyCode (Fungsi default device)")
                return super.onKeyUp(keyCode, event)
            }
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
     * Get Power Level from settings
     */
    private fun getPowerLevel(): Int {
        val sharedPrefs = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
        return sharedPrefs.getInt("power_level", 20)
    }
    
    /**
     * Apply RFID settings from SharedPreferences
     */
    private fun applySettingsFromSharedPreferences() {
        try {
            // Use the new method from RfidScanManager that reads from SharedPreferences
            rfidScanManager.refreshSettingsFromSharedPreferences()
            Log.d(TAG, "🔥 RFID settings refreshed from SharedPreferences in RelocationActivity")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error applying settings: ${e.message}", e)
        }
    }
    
    /**
     * Verify RFID settings yang diterapkan
     */
    private fun verifyRfidSettings() {
        try {
            val sharedPreferences = getSharedPreferences("RFIDSettings", MODE_PRIVATE)
            val savedPowerLevel = sharedPreferences.getInt("power_level", 20)
            val savedRssiThreshold = sharedPreferences.getInt("rssi_threshold", -70)
            val currentSettings = rfidScanManager.getSettings()
            
            Log.d(TAG, "🔥 === RFID SETTINGS VERIFICATION ===")
            Log.d(TAG, "🔥 SharedPreferences - Power Level: $savedPowerLevel")
            Log.d(TAG, "🔥 SharedPreferences - RSSI Threshold: $savedRssiThreshold")
            Log.d(TAG, "🔥 RfidScanManager - Power Level: ${currentSettings.powerLevel}")
            Log.d(TAG, "🔥 RfidScanManager - RSSI Threshold: ${currentSettings.rssiThreshold}")
            Log.d(TAG, "🔥 RfidScanManager - Duplicate Removal: ${currentSettings.duplicateRemovalEnabled}")
            Log.d(TAG, "🔥 RfidScanManager - Grace Period: ${currentSettings.gracePeriodMs}ms")
            
            if (currentSettings.powerLevel != savedPowerLevel) {
                Log.w(TAG, "⚠️ WARNING: Power level mismatch! SharedPrefs: $savedPowerLevel, RfidScanManager: ${currentSettings.powerLevel}")
            }
            if (currentSettings.rssiThreshold != savedRssiThreshold) {
                Log.w(TAG, "⚠️ WARNING: RSSI threshold mismatch! SharedPrefs: $savedRssiThreshold, RfidScanManager: ${currentSettings.rssiThreshold}")
            }
            Log.d(TAG, "🔥 === END SETTINGS VERIFICATION ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error verifying settings: ${e.message}", e)
        }
    }
    
    /**
     * Get key name for logging purposes
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
            293 -> "SCAN_TRIGGER"
            else -> "UNKNOWN($keyCode)"
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
