package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cekpicklist.adapter.PicklistSelectionAdapter
import com.example.cekpicklist.data.PicklistStatus
import com.example.cekpicklist.databinding.ActivityPicklistInputBinding
import com.example.cekpicklist.databinding.DialogPicklistSelectionBinding
import com.example.cekpicklist.utils.Logger
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.viewmodel.ScanViewModel
import com.example.cekpicklist.viewmodel.ScanViewModelFactory

class PicklistInputActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPicklistInputBinding
    private lateinit var viewModel: ScanViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPicklistInputBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Logger.PicklistInput.d("onCreate() called")
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this, ScanViewModelFactory(application))[ScanViewModel::class.java]
        
        // Setup UI
        setupSwipeRefresh()
        setupPicklistObserver()
        setupErrorObserver()
        setupPicklistSelection()
        setupSettingsButton()
        
        // Load picklists dengan optimasi
        viewModel.loadPicklistsOptimized()
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
        }
    }
    
    private fun setupErrorObserver() {
        Logger.PicklistInput.d("setupErrorObserver() called")
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Logger.PicklistInput.e("Error: $it")
                ToastUtils.showHighToastWithCooldown(this, it)
            }
        }
    }
    
    private fun setupPicklistSelection() {
        Logger.PicklistInput.d("setupPicklistSelection() called")
        
        binding.tvSelectedPicklist.setOnClickListener {
            Logger.PicklistInput.d("Picklist selection clicked")
            showPicklistSelectionDialog()
        }
    }
    
    private fun setupSettingsButton() {
        Logger.PicklistInput.d("setupSettingsButton() called")
        
        binding.btnSettings.setOnClickListener {
            Logger.PicklistInput.d("Settings button clicked - navigating to SettingsActivity")
            navigateToSettings()
        }
        
        // Tambahkan ripple effect untuk feedback visual
        binding.btnSettings.background = getDrawable(android.R.drawable.list_selector_background)
    }
    
    private fun navigateToSettings() {
        Logger.PicklistInput.d("Navigating to SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun showPicklistSelectionDialog() {
        Logger.PicklistInput.d("showPicklistSelectionDialog() called")
        
        // Ambil data picklist dari ViewModel
        val picklists = viewModel.picklists.value ?: emptyList()
        
        if (picklists.isEmpty()) {
            ToastUtils.showHighToastWithCooldown(this, "Tidak ada picklist tersedia")
            return
        }
        
        // Buat PicklistStatus untuk setiap picklist dengan status yang benar
        val picklistStatuses = picklists.map { picklistNumber ->
            // Ambil status completion dari ViewModel
            val completionStatus = viewModel.getPicklistCompletionStatus(picklistNumber)
            
            PicklistStatus(
                picklistNumber = picklistNumber,
                isScanned = completionStatus.isScanned,
                remainingQty = completionStatus.remainingQty,
                totalQty = completionStatus.totalQty,
                scannedQty = completionStatus.scannedQty,
                lastScanTime = completionStatus.lastScanTime,
                overscanQty = completionStatus.overscanQty
            )
        }
        
        // Buat dialog dengan binding
        val dialogBinding = DialogPicklistSelectionBinding.inflate(layoutInflater)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        dialogBinding.rvPicklistList.layoutManager = layoutManager
        
        // Setup adapter
        val adapter = PicklistSelectionAdapter(picklistStatuses) { selectedPicklist ->
            Logger.PicklistInput.d("Picklist selected: $selectedPicklist")
            
            // Update TextView
            binding.tvSelectedPicklist.text = selectedPicklist
            binding.tvSelectedPicklist.setTextColor(resources.getColor(android.R.color.black, null))
            binding.tvSelectedPicklist.setTypeface(null, android.graphics.Typeface.NORMAL)
            
            // Tutup dialog
            dialog.dismiss()
            
            // Navigasi ke MainActivity
            navigateToMainActivity(selectedPicklist)
        }
        
        dialogBinding.rvPicklistList.adapter = adapter
        
        // Setup search functionality
        setupSearchFunctionality(dialogBinding, adapter, picklistStatuses.size)
        
        // Update count
        updatePicklistCount(dialogBinding, adapter.getFilteredCount(), picklistStatuses.size)
        
        // Show dialog
        dialog.show()
    }
    
    private fun setupSearchFunctionality(
        dialogBinding: DialogPicklistSelectionBinding,
        adapter: PicklistSelectionAdapter,
        totalCount: Int
    ) {
        // Setup search text watcher
        dialogBinding.etSearchPicklist.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                adapter.filterPicklists(query)
                
                // Update count
                updatePicklistCount(dialogBinding, adapter.getFilteredCount(), totalCount)
                
                // Show/hide clear button
                dialogBinding.btnClearSearch.visibility = if (query.isNotBlank()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Setup clear button
        dialogBinding.btnClearSearch.setOnClickListener {
            dialogBinding.etSearchPicklist.setText("")
            dialogBinding.etSearchPicklist.clearFocus()
        }
    }
    
    private fun updatePicklistCount(
        dialogBinding: DialogPicklistSelectionBinding,
        filteredCount: Int,
        totalCount: Int
    ) {
        dialogBinding.tvPicklistCount.text = if (filteredCount == totalCount) {
            "$totalCount item"
        } else {
            "$filteredCount dari $totalCount item"
        }
    }
    
    private fun navigateToMainActivity(selectedPicklist: String) {
        Logger.PicklistInput.d("Navigating to MainActivity with picklist: $selectedPicklist")
        
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
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
        super.onDestroy()
        Logger.PicklistInput.d("onDestroy() called")
    }
}

