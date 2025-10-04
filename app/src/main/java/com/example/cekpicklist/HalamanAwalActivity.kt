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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHalamanAwalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
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
        
        // Load picklists dengan optimasi
        viewModel.loadPicklistsOptimized()
        
        // Check for updates (non-blocking)
        checkForUpdates()
    }
    
    private fun setupSwipeRefresh() {
        Logger.PicklistInput.d("setupSwipeRefresh() called")
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            Logger.PicklistInput.d("Swipe refresh triggered")
            viewModel.loadPicklistsOptimized()
        }
    }
    
    private fun setupPicklistObserver() {
        Logger.PicklistInput.d("setupPicklistObserver() called")
        
        viewModel.picklists.observe(this) { picklists ->
            Logger.PicklistInput.d("Picklists updated: ${picklists.size} items")
            
            // Hide refresh indicator
            if (binding.swipeRefreshLayout.isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
            
            // UI tidak perlu diupdate karena menggunakan modal
        }
        
        // Observer untuk picklist statuses
        viewModel.picklistStatuses.observe(this) { statuses ->
            Logger.PicklistInput.d("Picklist statuses updated: ${statuses.size} items")
            // Update modal jika sedang terbuka
            if (activeDialog?.isShowing == true) {
                updateModalWithStatusData(statuses)
            }
        }
    }
    
    /**
     * Update modal dengan data status terbaru
     */
    private fun updateModalWithStatusData(statuses: List<PicklistStatus>) {
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
        Logger.PicklistInput.d("setupErrorObserver() called")
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Logger.PicklistInput.e("Error: $it")
                ToastUtils.showHighToastWithCooldown(this, it)
            }
        }
    }
    
    /**
     * Setup card click functionality
     */
    private fun setupCardClicks() {
        Logger.PicklistInput.d("setupCardClicks() called")
        
        // Card Picklist click
        binding.    cardPicklist.setOnClickListener {
            Logger.PicklistInput.d("Picklist card clicked")
            showPicklistSelectionModal()
        }
    }
    
    private fun navigateToSettings() {
        Logger.PicklistInput.d("Navigating to SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupRelocationCard() {
        Logger.PicklistInput.d("setupRelocationCard() called")
        
        binding.cardRelocation.setOnClickListener {
            Logger.PicklistInput.d("Relocation card clicked - navigating to RelocationActivity")
            navigateToRelocation()
        }
    }
    
    private fun navigateToRelocation() {
        Logger.PicklistInput.d("Navigating to RelocationActivity")
        val intent = Intent(this, RelocationActivity::class.java)
        startActivity(intent)
    }
    
    private fun setupVersionDisplay() {
        Logger.PicklistInput.d("setupVersionDisplay() called")
        
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.tvVersion.text = "Version $versionName"
            Logger.PicklistInput.d("Version displayed: $versionName")
        } catch (e: Exception) {
            Logger.PicklistInput.e("Error getting version: ${e.message}")
            binding.tvVersion.text = "Version Unknown"
        }
    }
    
    /**
     * Check for app updates
     */
    private fun checkForUpdates() {
        Logger.PicklistInput.d("checkForUpdates() called")
        
        if (!updateChecker.isUpdateCheckDisabled()) {
            updateChecker.checkForUpdates()
        } else {
            Logger.PicklistInput.d("Update check is disabled by user")
        }
    }
    
    /**
     * Show picklist selection modal
     */
    private fun showPicklistSelectionModal() {
        Logger.PicklistInput.d("showPicklistSelectionModal() called")
        // Hindari menampilkan dialog jika activity akan/ sudah ditutup
        if (isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed)) {
            Logger.PicklistInput.w("Activity is finishing/destroyed. Skip showing dialog to prevent WindowLeaked.")
            return
        }
        
        // Ambil data picklist dari ViewModel
        val picklists = viewModel.picklists.value ?: emptyList()
        
        if (picklists.isEmpty()) {
            ToastUtils.showHighToastWithCooldown(this, "Tidak ada picklist tersedia")
            return
        }
        
        // Create simple modal dialog
        val dialogView = layoutInflater.inflate(R.layout.modal_picklist_selection, null)
        
        // Setup RecyclerView
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewPicklists)
        val btnClose = dialogView.findViewById<android.view.View>(R.id.btnCloseModal)
        val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchPicklist)
        
        // Get picklist statuses with actual scan data from ViewModel
        val picklistStatuses = viewModel.picklistStatuses.value ?: emptyList()
        Logger.PicklistInput.d("Modal opened - picklistStatuses.size: ${picklistStatuses.size}")
        
        // Jika belum ada status data, buat default status
        val finalStatuses = if (picklistStatuses.isEmpty()) {
            Logger.PicklistInput.d("No status data available, using default statuses")
            picklists.map { picklistNo ->
                PicklistStatus(
                    picklistNumber = picklistNo,
                    isScanned = false,
                    remainingQty = 0,
                    totalQty = 0,
                    scannedQty = 0
                )
            }
        } else {
            Logger.PicklistInput.d("Using actual status data")
            picklistStatuses.forEach { status ->
                Logger.PicklistInput.d("Status: ${status.picklistNumber} - scanned: ${status.isScanned}, total: ${status.totalQty}, scanned: ${status.scannedQty}")
            }
            picklistStatuses
        }
        
        val adapter = PicklistSelectionAdapter(finalStatuses) { picklist ->
            Logger.PicklistInput.d("Picklist selected from modal: $picklist")
            // Tutup dialog terlebih dahulu sebelum navigasi agar tidak leak
            try { activeDialog?.dismiss() } catch (_: Throwable) {}
            activeDialog = null
            navigateToMainActivity(picklist)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        adapter.updatePicklists(finalStatuses)
        
        // Setup close button
        btnClose.setOnClickListener {
            activeDialog?.dismiss()
        }
        
        // Setup search functionality
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                val filteredList = if (query.isEmpty()) {
                    finalStatuses
                } else {
                    finalStatuses.filter { it.picklistNumber.contains(query, ignoreCase = true) }
                }
                adapter.updatePicklists(filteredList)
            }
        })
        
        // Create and show dialog
        activeDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        activeDialog?.show()
    }
    
    // Legacy dialog methods removed - using RecyclerView instead
    
    private fun navigateToMainActivity(selectedPicklist: String) {
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
        Logger.PicklistInput.d("refreshPicklistData() called")
        
        binding.swipeRefreshLayout.isRefreshing = true
        viewModel.loadPicklistsOptimized()
        ToastUtils.showHighToastWithCooldown(this, "Memperbarui data picklist...")
    }
    
    private fun updateStatusIndicator() {
        Logger.PicklistInput.d("updateStatusIndicator() called")
        
        val cacheInfo = viewModel.getCacheInfo()
        Logger.PicklistInput.d("Cache info: $cacheInfo")
        
        // TODO: Implementasi status indicator
    }
    
    private fun showStatusIndicator(icon: Int, message: String, isSuccess: Boolean = true) {
        Logger.PicklistInput.d("showStatusIndicator() called: $message")
        
        // TODO: Implementasi status indicator
    }
    
    private fun hideStatusIndicator() {
        Logger.PicklistInput.d("hideStatusIndicator() called")
        
        // TODO: Implementasi hide status indicator
    }

    override fun onDestroy() {
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

