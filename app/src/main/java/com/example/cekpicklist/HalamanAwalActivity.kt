package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.cekpicklist.adapter.PicklistSelectionAdapter
import com.example.cekpicklist.databinding.ActivityHalamanAwalBinding
import com.example.cekpicklist.databinding.DialogPicklistSelectionBinding
import com.example.cekpicklist.data.PicklistStatus
import com.example.cekpicklist.utils.Logger
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.viewmodel.ScanViewModel
import com.example.cekpicklist.viewmodel.ScanViewModelFactory
import com.example.cekpicklist.utils.LoadingAnimationHelper
import com.example.cekpicklist.utils.UpdateChecker

class HalamanAwalActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityHalamanAwalBinding
    private lateinit var viewModel: ScanViewModel
    private lateinit var updateChecker: UpdateChecker
    
    // Dialog references for proper cleanup
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null
    private var loadingDialog: androidx.appcompat.app.AlertDialog? = null
    
    // For double click detection
    private var lastClickTime: Long = 0
    
    // Auto-refresh timer untuk modal
    private var modalRefreshTimer: kotlinx.coroutines.Job? = null
    
    // Loading observer untuk proper cleanup
    private var loadingObserver: androidx.lifecycle.Observer<Boolean>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHalamanAwalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Logger.PicklistInput.d("🔥 === HALAMAN AWAL ONCREATE START ===")
        Logger.PicklistInput.d("🔍 DEBUG: onCreate() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("onCreate() called")
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this, ScanViewModelFactory(application))[ScanViewModel::class.java]
        
        // Initialize Update Checker
        updateChecker = UpdateChecker(this)
        
        // Setup UI
        setupSwipeRefresh()
        setupPicklistObserver()
        setupErrorObserver()
        setupCardClicks()
        setupRelocationCard()
        setupVersionDisplay()
        
        // **PERBAIKAN**: Load picklist statuses untuk modal
        Logger.PicklistInput.d("🔥 Starting picklist loading on startup...")
        viewModel.loadPicklistsOptimized()
        
        // **DEBUG**: Tambahkan debug info setelah loading
        lifecycleScope.launch {
            delay(2000) // Tunggu 2 detik
            updateStatusIndicator()
        }
        
        // Check for updates (non-blocking)
        checkForUpdates()
    }
    
    private fun setupSwipeRefresh() {
        Logger.PicklistInput.d("🔥 === SETUP SWIPE REFRESH START ===")
        Logger.PicklistInput.d("🔍 DEBUG: setupSwipeRefresh() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("setupSwipeRefresh() called")
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            Logger.PicklistInput.d("🔥 === SWIPE REFRESH TRIGGERED ===")
            Logger.PicklistInput.d("🔍 DEBUG: Swipe refresh triggered at ${System.currentTimeMillis()}")
            Logger.PicklistInput.d("Swipe refresh triggered")
            viewModel.loadPicklistsOptimized()
        }
    }
    
    private fun setupPicklistObserver() {
        Logger.PicklistInput.d("🔥 === HALAMAN AWAL SETUP PICKLIST OBSERVER START ===")
        Logger.PicklistInput.d("🔍 DEBUG: setupPicklistObserver() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("setupPicklistObserver() called")
        
        // **OBSERVER ORIGINAL**: Gunakan picklist statuses observer
        viewModel.picklistStatuses.observe(this) { statuses ->
            Logger.PicklistInput.d("🔥 === PICKLIST STATUSES OBSERVER TRIGGERED ===")
            Logger.PicklistInput.d("🔍 DEBUG: Picklist statuses observer triggered at ${System.currentTimeMillis()}")
            Logger.PicklistInput.d("Picklist statuses updated: ${statuses.size} items")
            Logger.PicklistInput.d("🔍 DEBUG: Picklist statuses observer triggered with ${statuses.size} items")
            
            if (statuses.isNotEmpty()) {
                Logger.PicklistInput.d("🔍 DEBUG: First 3 picklist statuses: ${statuses.take(3).map { "${it.picklistNumber}(${if (it.isScanned) "scanned" else "not scanned"})" }}")
            } else {
                Logger.PicklistInput.w("⚠️ DEBUG: Picklist statuses observer received empty list!")
            }
            
            // Hide refresh indicator
            if (binding.swipeRefreshLayout.isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            
            // UI tidak perlu diupdate karena menggunakan modal
        }
        
        // Observer untuk loading state
        viewModel.isLoading.observe(this) { isLoading ->
            Logger.PicklistInput.d("Loading state updated: $isLoading")
            if (!isLoading && binding.swipeRefreshLayout.isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    /**
     * Update summary info di modal
     */
    private fun updateSummaryInfo(
        statuses: List<PicklistStatus>,
        tvTotal: android.widget.TextView?,
        tvCompleted: android.widget.TextView?,
        tvRemaining: android.widget.TextView?,
        llSummary: android.view.View?
    ) {
        try {
            val total = statuses.size
            val completed = statuses.count { it.isScanned && it.remainingQty == 0 }
            val remaining = statuses.count { !it.isScanned || it.remainingQty > 0 }
            
            tvTotal?.text = "Total: $total picklist"
            tvCompleted?.text = "Selesai: $completed"
            tvRemaining?.text = "Sisa: $remaining"
            
            llSummary?.visibility = android.view.View.VISIBLE
            
            Logger.PicklistInput.d("📊 Summary updated: Total=$total, Completed=$completed, Remaining=$remaining")
        } catch (e: Exception) {
            Logger.PicklistInput.e("❌ Error updating summary info: ${e.message}", e)
        }
    }
    
    /**
     * Update summary info untuk simple picklists
     */
    private fun updateSimpleSummaryInfo(
        simplePicklists: List<com.example.cekpicklist.api.SupabaseService.SimplePicklist>,
        tvTotal: android.widget.TextView?,
        tvCompleted: android.widget.TextView?,
        tvRemaining: android.widget.TextView?,
        llSummary: android.view.View?
    ) {
        try {
            val total = simplePicklists.size
            val scanned = simplePicklists.count { it.isScanned }
            val notScanned = simplePicklists.count { !it.isScanned }
            
            tvTotal?.text = "Total: $total picklist"
            tvCompleted?.text = "Sudah scan: $scanned"
            tvRemaining?.text = "Belum scan: $notScanned"
            
            llSummary?.visibility = android.view.View.VISIBLE
            
            Logger.PicklistInput.d("📊 Simple Summary updated: Total=$total, Scanned=$scanned, Not Scanned=$notScanned")
        } catch (e: Exception) {
            Logger.PicklistInput.e("❌ Error updating simple summary info: ${e.message}", e)
        }
    }

    /**
     * Update modal dengan data status terbaru
     */
    private fun updateModalWithStatusData(statuses: List<PicklistStatus>) {
        Logger.PicklistInput.d("🔥 === UPDATE MODAL WITH STATUS DATA START ===")
        Logger.PicklistInput.d("🔍 DEBUG: updateModalWithStatusData() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("updateModalWithStatusData called with ${statuses.size} statuses")
        
        // Cari adapter dari modal yang sedang terbuka
        val dialogView = activeDialog?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewPicklists)
        val adapter = dialogView?.adapter as? PicklistSelectionAdapter
        
        if (adapter != null && statuses.isNotEmpty()) {
            Logger.PicklistInput.d("Updating modal adapter with new status data")
            adapter.updatePicklists(statuses)
            
            // Log status data untuk debug
            statuses.forEach { status ->
                Logger.PicklistInput.d("Updated status: ${status.picklistNumber} - scanned: ${status.isScanned}, total: ${status.totalQty}, scanned: ${status.scannedQty}")
            }
        } else {
            Logger.PicklistInput.w("Cannot update modal: adapter=$adapter, statuses.size=${statuses.size}")
        }
    }
    
    /**
     * Update UI based on picklist data
     */
    // Method ini tidak diperlukan lagi karena menggunakan modal
    
    // Method ini tidak diperlukan lagi karena menggunakan modal
    
    private fun setupErrorObserver() {
        Logger.PicklistInput.d("🔥 === SETUP ERROR OBSERVER START ===")
        Logger.PicklistInput.d("🔍 DEBUG: setupErrorObserver() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("setupErrorObserver() called")
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Logger.PicklistInput.e("🔥 === ERROR OBSERVER TRIGGERED ===")
                Logger.PicklistInput.e("🔍 DEBUG: Error observer triggered at ${System.currentTimeMillis()}")
                Logger.PicklistInput.e("Error: $it")
                ToastUtils.showHighToastWithCooldown(this, it)
            }
        }
    }
    
    /**
     * Setup card click functionality
     */
    private fun setupCardClicks() {
        Logger.PicklistInput.d("🔥 === SETUP CARD CLICKS START ===")
        Logger.PicklistInput.d("🔍 DEBUG: setupCardClicks() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("setupCardClicks() called")
        
        // Card Picklist click
        binding.cardPicklist.setOnClickListener {
            Logger.PicklistInput.d("🔥 === PICKLIST CARD CLICKED ===")
            Logger.PicklistInput.d("🔍 DEBUG: Picklist card clicked at ${System.currentTimeMillis()}")
            Logger.PicklistInput.d("Picklist card clicked")
            showPicklistSelectionModal()
        }
    }
    
    private fun navigateToSettings() {
        Logger.PicklistInput.d("🔥 === NAVIGATE TO SETTINGS START ===")
        Logger.PicklistInput.d("🔍 DEBUG: navigateToSettings() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("Navigating to SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupRelocationCard() {
        Logger.PicklistInput.d("🔥 === SETUP RELOCATION CARD START ===")
        Logger.PicklistInput.d("🔍 DEBUG: setupRelocationCard() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("setupRelocationCard() called")
        
        binding.cardRelocation.setOnClickListener {
            Logger.PicklistInput.d("🔥 === RELOCATION CARD CLICKED ===")
            Logger.PicklistInput.d("🔍 DEBUG: Relocation card clicked at ${System.currentTimeMillis()}")
            Logger.PicklistInput.d("Relocation card clicked - navigating to RelocationActivity")
            navigateToRelocation()
        }
    }
    
    private fun navigateToRelocation() {
        Logger.PicklistInput.d("🔥 === NAVIGATE TO RELOCATION START ===")
        Logger.PicklistInput.d("🔍 DEBUG: navigateToRelocation() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("Navigating to RelocationActivity")
        val intent = Intent(this, RelocationActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupVersionDisplay() {
        Logger.PicklistInput.d("🔥 === SETUP VERSION DISPLAY START ===")
        Logger.PicklistInput.d("🔍 DEBUG: setupVersionDisplay() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("setupVersionDisplay() called")
        
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.tvVersion.text = "Version $versionName"
            Logger.PicklistInput.d("Version displayed: $versionName")
            
            // Long click untuk testing update checker
            binding.tvVersion.setOnLongClickListener {
                Logger.PicklistInput.d("🔥 === VERSION TEXT LONG CLICKED ===")
                Logger.PicklistInput.d("🔍 DEBUG: Version text long clicked at ${System.currentTimeMillis()}")
                Logger.PicklistInput.d("Version text long clicked - testing update checker")
                testUpdateChecker()
                true
            }
            
            // Double click untuk menampilkan debug info
            binding.tvVersion.setOnClickListener {
                // Check if this is a double click
                if (System.currentTimeMillis() - lastClickTime < 500) {
                    Logger.PicklistInput.d("🔥 === VERSION TEXT DOUBLE CLICKED ===")
                    Logger.PicklistInput.d("🔍 DEBUG: Version text double clicked at ${System.currentTimeMillis()}")
                    Logger.PicklistInput.d("Version text double clicked - showing debug info")
                    showUpdateCheckerDebugInfo()
                }
                lastClickTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Logger.PicklistInput.e("🔥 === VERSION DISPLAY ERROR ===")
            Logger.PicklistInput.e("🔍 DEBUG: Version display error at ${System.currentTimeMillis()}")
            Logger.PicklistInput.e("Error getting version: ${e.message}")
            binding.tvVersion.text = "Version Unknown"
        }
    }
    
    /**
     * Check for app updates
     */
    private fun checkForUpdates() {
        Logger.PicklistInput.d("🔥 === CHECK FOR UPDATES START ===")
        Logger.PicklistInput.d("🔍 DEBUG: checkForUpdates() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("checkForUpdates() called")
        
        // Log debug info
        Logger.PicklistInput.d(updateChecker.getDebugInfo())
        
        if (!updateChecker.isUpdateCheckDisabled()) {
            updateChecker.checkForUpdates()
        } else {
            Logger.PicklistInput.d("Update check is disabled by user")
        }
    }
    
    /**
     * Test update checker (untuk debugging)
     */
    private fun testUpdateChecker() {
        Logger.PicklistInput.d("🔥 === TEST UPDATE CHECKER START ===")
        Logger.PicklistInput.d("🔍 DEBUG: testUpdateChecker() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("Testing update checker...")
        Logger.PicklistInput.d(updateChecker.getDebugInfo())
        
        // Tampilkan debug info di dialog
        val debugInfo = updateChecker.getDebugInfo()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Update Checker Debug Info")
            .setMessage(debugInfo)
            .setPositiveButton("Force Check") { _, _ ->
                // Reset last check time untuk testing
                updateChecker.resetLastCheckTime()
                // Force check update
                updateChecker.forceCheckUpdate()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Show update checker debug info (untuk debugging)
     */
    private fun showUpdateCheckerDebugInfo() {
        Logger.PicklistInput.d("🔥 === SHOW UPDATE CHECKER DEBUG INFO START ===")
        Logger.PicklistInput.d("🔍 DEBUG: showUpdateCheckerDebugInfo() called at ${System.currentTimeMillis()}")
        val debugInfo = updateChecker.getDebugInfo()
        Logger.PicklistInput.d("Update Checker Debug Info:")
        Logger.PicklistInput.d(debugInfo)
        
        // Tampilkan di toast juga
        ToastUtils.showHighToastWithCooldown(this, "Debug info logged to logcat")
    }
    
    /**
     * Show picklist selection modal
     */
    private fun showPicklistSelectionModal() {
        Logger.PicklistInput.d("🔥 === SHOW PICKLIST SELECTION MODAL START ===")
        Logger.PicklistInput.d("🔍 DEBUG: showPicklistSelectionModal() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("showPicklistSelectionModal() called")
        // Hindari menampilkan dialog jika activity akan/ sudah ditutup
        if (isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed)) {
            Logger.PicklistInput.w("Activity is finishing/destroyed. Skip showing dialog to prevent WindowLeaked.")
            return
        }
        
        // **PERBAIKAN**: Ambil data picklist statuses dari ViewModel
        val statuses = viewModel.picklistStatuses.value ?: emptyList()
        val isLoading = viewModel.isLoading.value ?: false
        
        Logger.PicklistInput.d("🔍 DEBUG: showPicklistSelectionModal - statuses: ${statuses.size}, isLoading: $isLoading")
        
        if (statuses.isEmpty()) {
            Logger.PicklistInput.w("⚠️ DEBUG: No picklist statuses available - opening modal in loading state")
            // Tetap buka modal dan biarkan observer mengisi data ketika siap
        }

        // **PERBAIKAN**: Tunggu loading picklist selesai sebelum membuka modal
        if (viewModel.isLoading.value == true) {
            Logger.PicklistInput.d("Picklist masih loading, tunggu sebentar...")
            ToastUtils.showHighToastWithCooldown(this, "Memuat data picklist...")
            
            // Observe loading state dan buka modal setelah selesai
            loadingObserver = androidx.lifecycle.Observer<Boolean> { isLoading ->
                if (!isLoading) {
                    Logger.PicklistInput.d("🔥 === PICKLIST LOADING COMPLETED ===")
                    Logger.PicklistInput.d("🔍 DEBUG: Picklist loading completed at ${System.currentTimeMillis()}")
                    Logger.PicklistInput.d("Picklist loading selesai, buka modal")
                    loadingObserver?.let { observer ->
                        viewModel.isLoading.removeObserver(observer)
                    }
                    loadingObserver = null
                    openPicklistModal()
                }
            }
            loadingObserver?.let { observer ->
                viewModel.isLoading.observe(this, observer)
            }
            return
        }
        
        // Jika sudah selesai loading, langsung buka modal
        Logger.PicklistInput.d("🔍 DEBUG: Loading completed, opening modal directly...")
        openPicklistModal()
    }
    
    /**
     * Open picklist modal dengan data yang sudah siap
     */
    private fun openPicklistModal() {
        Logger.PicklistInput.d("🔥 === OPEN PICKLIST MODAL START ===")
        Logger.PicklistInput.d("🔍 DEBUG: openPicklistModal() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("openPicklistModal() called")
        
        // Hindari membatalkan loading yang sedang berjalan. Hanya trigger load jika belum ada data dan tidak sedang loading
        val hasStatuses = !(viewModel.picklistStatuses.value.isNullOrEmpty())
        val isLoadingNow = viewModel.isLoading.value == true
        if (!hasStatuses && !isLoadingNow) {
            Logger.PicklistInput.d("🔍 DEBUG: No statuses cached and not loading, triggering loadPicklistsOptimized() once...")
            viewModel.loadPicklistsOptimized()
        } else {
            Logger.PicklistInput.d("🔍 DEBUG: Skip triggering load (hasStatuses=$hasStatuses, isLoading=$isLoadingNow)")
        }
        
        // Create simple modal dialog
        Logger.PicklistInput.d("🔍 DEBUG: Creating modal dialog...")
        val dialogView = layoutInflater.inflate(R.layout.modal_picklist_selection, null)
        
        // Setup RecyclerView
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewPicklists)
        val btnClose = dialogView.findViewById<android.view.View>(R.id.btnCloseModal)
        val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchPicklist)
        
        // Setup loading/empty views
        val llLoading = dialogView.findViewById<android.view.View>(R.id.llLoading)
        val llEmpty = dialogView.findViewById<android.view.View>(R.id.llEmptyState)
        llLoading?.visibility = android.view.View.VISIBLE
        llEmpty?.visibility = android.view.View.GONE
        
        // Setup summary info views
        val llSummaryInfo = dialogView.findViewById<android.view.View>(R.id.llSummaryInfo)
        val tvTotalPicklists = dialogView.findViewById<android.widget.TextView>(R.id.tvTotalPicklists)
        val tvCompletedPicklists = dialogView.findViewById<android.widget.TextView>(R.id.tvCompletedPicklists)
        val tvRemainingPicklists = dialogView.findViewById<android.widget.TextView>(R.id.tvRemainingPicklists)
        
        Logger.PicklistInput.d("🔍 DEBUG: Modal dialog created successfully")

        // Ambil picklist statuses awal bila sudah ada
        val statuses = viewModel.picklistStatuses.value ?: emptyList()
        Logger.PicklistInput.d("Modal opened - initialStatuses.size: ${statuses.size}")
        val finalStatuses = statuses
        
        Logger.PicklistInput.d("🔍 DEBUG: Creating PicklistSelectionAdapter with ${finalStatuses.size} picklists...")
        val adapter = PicklistSelectionAdapter(finalStatuses) { picklist ->
            Logger.PicklistInput.d("🔥 === PICKLIST SELECTED FROM MODAL ===")
            Logger.PicklistInput.d("🔍 DEBUG: Picklist selected from modal at ${System.currentTimeMillis()}")
            Logger.PicklistInput.d("Picklist selected from modal: $picklist")
            
            // Tutup dialog terlebih dahulu sebelum navigasi agar tidak leak
            try { activeDialog?.dismiss() } catch (_: Throwable) {}
            activeDialog = null
            navigateToMainActivity(picklist)
        }

        Logger.PicklistInput.d("🔍 DEBUG: Setting up RecyclerView...")
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        if (finalStatuses.isNotEmpty()) {
            Logger.PicklistInput.d("🔍 DEBUG: Updating adapter with ${finalStatuses.size} picklist statuses...")
            adapter.updatePicklists(finalStatuses)
            llLoading?.visibility = android.view.View.GONE
            updateSummaryInfo(finalStatuses, tvTotalPicklists, tvCompletedPicklists, tvRemainingPicklists, llSummaryInfo)
        } else {
            Logger.PicklistInput.d("🔍 DEBUG: No picklist statuses yet, using placeholder list from picklists")
            val picklistNumbers = viewModel.picklists.value.orEmpty()
            val placeholderStatuses = picklistNumbers.map { pickNo ->
                PicklistStatus(
                    picklistNumber = pickNo,
                    isScanned = false,
                    remainingQty = 0,
                    totalQty = 0,
                    scannedQty = 0,
                    lastScanTime = null,
                    overscanQty = 0
                )
            }
            adapter.updatePicklists(placeholderStatuses)
            // Tampilkan list segera, dan biarkan observer mengganti data saat status asli siap
            llLoading?.visibility = android.view.View.GONE
            llSummaryInfo?.visibility = android.view.View.GONE
        }
        
        // Observe picklist statuses yang mungkin datang terlambat (mis. masih loading dari Supabase)
        Logger.PicklistInput.d("🔍 DEBUG: Setting up picklist statuses observer...")
        viewModel.picklistStatuses.observe(this) { latestStatuses ->
            if (activeDialog != null && latestStatuses != null) {
                Logger.PicklistInput.d("🔥 === MODAL PICKLIST STATUSES UPDATED ===")
                Logger.PicklistInput.d("🔍 DEBUG: Modal picklist statuses updated at ${System.currentTimeMillis()}")
                Logger.PicklistInput.d("Modal picklist statuses updated: ${latestStatuses.size} items - refreshing adapter")
                adapter.updatePicklists(latestStatuses)
                llLoading?.visibility = if (latestStatuses.isNotEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                llEmpty?.visibility = if (latestStatuses.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                
                // Update summary info
                if (latestStatuses.isNotEmpty()) {
                    updateSummaryInfo(latestStatuses, tvTotalPicklists, tvCompletedPicklists, tvRemainingPicklists, llSummaryInfo)
                } else {
                    llSummaryInfo?.visibility = android.view.View.GONE
                }
            }
        }

        // Observer untuk simple picklists (cepat) agar segera menunjukkan status sudah/belum scan
        Logger.PicklistInput.d("🔍 DEBUG: Setting up simple picklists observer...")
        viewModel.simplePicklists.observe(this) { simpleList ->
            if (activeDialog != null && simpleList != null && (viewModel.picklistStatuses.value.isNullOrEmpty())) {
                Logger.PicklistInput.d("🔥 === MODAL SIMPLE PICKLISTS UPDATED ===")
                val quickStatuses = simpleList.map { sp ->
                    PicklistStatus(
                        picklistNumber = sp.noPicklist,
                        isScanned = sp.isScanned,
                        // Gunakan hitungan cepat: totalCount = total baris, scannedCount = jumlah scan=true, unscannedCount = sisa
                        remainingQty = sp.unscannedCount,
                        totalQty = sp.totalCount,
                        scannedQty = sp.scannedCount,
                        lastScanTime = null,
                        overscanQty = 0
                    )
                }
                adapter.updatePicklists(quickStatuses)
                llLoading?.visibility = android.view.View.GONE
                llEmpty?.visibility = if (quickStatuses.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                // Tampilkan ringkasannya berdasarkan simple data
                updateSimpleSummaryInfo(simpleList, tvTotalPicklists, tvCompletedPicklists, tvRemainingPicklists, llSummaryInfo)
            }
        }

        // Trigger load simple picklists cepat jika belum punya status detail
        if (viewModel.picklistStatuses.value.isNullOrEmpty()) {
            Logger.PicklistInput.d("🔍 DEBUG: Triggering loadSimplePicklists() for quick scan status...")
            viewModel.loadSimplePicklists()
        }
        
        // **PERBAIKAN**: Tambahkan refresh button di modal dengan delay untuk menghindari race condition
        val btnRefresh = dialogView.findViewById<android.view.View>(R.id.btnRefreshModal)
        btnRefresh?.setOnClickListener {
            Logger.PicklistInput.d("🔥 === REFRESH BUTTON CLICKED IN MODAL ===")
            Logger.PicklistInput.d("🔍 DEBUG: Refresh button clicked in modal at ${System.currentTimeMillis()}")
            Logger.PicklistInput.d("Refresh button clicked in modal")
            ToastUtils.showHighToastWithCooldown(this, "Memperbarui data...")
            
            // **PERBAIKAN**: Delay sedikit untuk menghindari race condition
            lifecycleScope.launch {
                delay(500) // Tunggu 500ms sebelum refresh
                viewModel.loadPicklistsOptimized()
            }
        }
        
        // Setup close button
        btnClose.setOnClickListener {
            Logger.PicklistInput.d("🔥 === CLOSE BUTTON CLICKED IN MODAL ===")
            Logger.PicklistInput.d("🔍 DEBUG: Close button clicked in modal at ${System.currentTimeMillis()}")
            stopModalAutoRefresh()
            activeDialog?.dismiss()
        }
        
        // Setup search functionality
        Logger.PicklistInput.d("🔍 DEBUG: Setting up search functionality...")
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                val source = viewModel.picklistStatuses.value ?: finalStatuses
                val filteredList = if (query.isEmpty()) source else source.filter { it.picklistNumber.contains(query, ignoreCase = true) }
                Logger.PicklistInput.d("🔍 DEBUG: Search query: '$query', filtered results: ${filteredList.size}")
                adapter.updatePicklists(filteredList)
            }
        })
        
        // Create and show dialog
        Logger.PicklistInput.d("🔍 DEBUG: Creating and showing dialog...")
        activeDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        activeDialog?.show()
        Logger.PicklistInput.d("🔍 DEBUG: Dialog shown successfully")
        
        // **PERBAIKAN**: Start auto-refresh untuk modal dengan delay yang lebih lama
        startModalAutoRefresh()
    }
    
    /**
     * Start auto-refresh untuk modal picklist (setiap 60 detik untuk menghindari race condition)
     */
    private fun startModalAutoRefresh() {
        Logger.PicklistInput.d("🔥 === START MODAL AUTO REFRESH START ===")
        Logger.PicklistInput.d("🔍 DEBUG: startModalAutoRefresh() called at ${System.currentTimeMillis()}")
        modalRefreshTimer?.cancel()
        modalRefreshTimer = lifecycleScope.launch {
            while (activeDialog?.isShowing == true) {
                delay(60_000) // 60 detik (diperpanjang untuk menghindari race condition)
                if (activeDialog?.isShowing == true) {
                    Logger.PicklistInput.d("🔥 === AUTO REFRESH MODAL DATA ===")
                    Logger.PicklistInput.d("🔍 DEBUG: Auto-refreshing modal data at ${System.currentTimeMillis()}")
                    Logger.PicklistInput.d("Auto-refreshing modal data...")
                    
                    // **PERBAIKAN**: Delay sebelum refresh untuk menghindari race condition
                    delay(1000) // Tunggu 1 detik
                    if (activeDialog?.isShowing == true) {
                    viewModel.forceRefreshPicklistStatuses()
                    }
                }
            }
        }
    }
    
    /**
     * Stop auto-refresh untuk modal
     */
    private fun stopModalAutoRefresh() {
        Logger.PicklistInput.d("🔥 === STOP MODAL AUTO REFRESH START ===")
        Logger.PicklistInput.d("🔍 DEBUG: stopModalAutoRefresh() called at ${System.currentTimeMillis()}")
        modalRefreshTimer?.cancel()
        modalRefreshTimer = null
    }
    
    // Legacy dialog methods removed - using RecyclerView instead
    
    private fun navigateToMainActivity(selectedPicklist: String) {
        Logger.PicklistInput.d("🔥 === NAVIGATE TO MAIN ACTIVITY START ===")
        Logger.PicklistInput.d("🔍 DEBUG: navigateToMainActivity() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("Navigating to MainActivity with picklist: $selectedPicklist")
        
        try {
            // Pastikan dialog ditutup sebelum berpindah Activity
            try { activeDialog?.dismiss() } catch (_: Throwable) {}
            activeDialog = null
            val intent = Intent(this, CekPicklistActivity::class.java).apply {
                putExtra("SELECTED_PICKLIST", selectedPicklist)
            }
            
            Logger.PicklistInput.d("Intent created successfully")
            Logger.PicklistInput.d("Starting MainActivity...")
            
            startActivity(intent)
            
            Logger.PicklistInput.d("MainActivity started successfully")
            Logger.PicklistInput.d("Finishing PicklistInputActivity...")
            
            finish()
            
            Logger.PicklistInput.d("PicklistInputActivity finished")
            
        } catch (e: Exception) {
            Logger.PicklistInput.e("Error navigating to MainActivity: ${e.message}")
            ToastUtils.showHighToastWithCooldown(this, "Error: ${e.message}")
        }
    }

    // merged into the single onDestroy at bottom
    
    private fun refreshPicklistData() {
        Logger.PicklistInput.d("🔥 === REFRESH PICKLIST DATA START ===")
        Logger.PicklistInput.d("🔍 DEBUG: refreshPicklistData() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("refreshPicklistData() called")
        
        binding.swipeRefreshLayout.isRefreshing = true
        viewModel.loadPicklistsOptimized()
        ToastUtils.showHighToastWithCooldown(this, "Memperbarui data picklist...")
    }
    
    private fun updateStatusIndicator() {
        Logger.PicklistInput.d("🔥 === UPDATE STATUS INDICATOR START ===")
        Logger.PicklistInput.d("🔍 DEBUG: updateStatusIndicator() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("updateStatusIndicator() called")
        
        val cacheInfo = viewModel.getCacheInfo()
        Logger.PicklistInput.d("Cache info: $cacheInfo")
        
        // **DEBUG**: Tambahkan debug info ke UI menggunakan picklist statuses
        val picklistCount = viewModel.picklistStatuses.value?.size ?: 0
        binding.tvVersion.text = "v4.6.0-debug - $picklistCount picklist statuses"
        Logger.PicklistInput.d("🔍 DEBUG: Updated UI with $picklistCount picklist statuses")
        
            // Hilangkan auto-force refresh debug untuk mencegah double load saat startup
    }
    
    private fun showStatusIndicator(icon: Int, message: String, isSuccess: Boolean = true) {
        Logger.PicklistInput.d("🔥 === SHOW STATUS INDICATOR START ===")
        Logger.PicklistInput.d("🔍 DEBUG: showStatusIndicator() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("showStatusIndicator() called: $message")
        
        // TODO: Implementasi status indicator
    }
    
    private fun hideStatusIndicator() {
        Logger.PicklistInput.d("🔥 === HIDE STATUS INDICATOR START ===")
        Logger.PicklistInput.d("🔍 DEBUG: hideStatusIndicator() called at ${System.currentTimeMillis()}")
        Logger.PicklistInput.d("hideStatusIndicator() called")
        
        // TODO: Implementasi hide status indicator
    }

    override fun onDestroy() {
        Logger.PicklistInput.d("🔥 === HALAMAN AWAL ONDESTROY START ===")
        Logger.PicklistInput.d("🔍 DEBUG: onDestroy() called at ${System.currentTimeMillis()}")
        // Stop auto-refresh timer
        stopModalAutoRefresh()
        
        // Cleanup loading observer
        loadingObserver?.let { observer ->
            viewModel.isLoading.removeObserver(observer)
        }
        loadingObserver = null
        
        // Dismiss dialog untuk mencegah WindowLeaked
        try { activeDialog?.dismiss() } catch (_: Throwable) {}
        activeDialog = null
        try { loadingDialog?.dismiss() } catch (_: Throwable) {}
        loadingDialog = null
        
        // Cancel any ongoing loading operations
        viewModel.cancelLoadingJobs()
        
        super.onDestroy()
        Logger.PicklistInput.d("onDestroy() called")
    }
}

