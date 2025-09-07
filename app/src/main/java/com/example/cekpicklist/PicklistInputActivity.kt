package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cekpicklist.adapter.PicklistSelectionAdapter
import com.example.cekpicklist.databinding.DialogPicklistSelectionBinding
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.utils.Logger
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.cekpicklist.databinding.ActivityPicklistInputBinding
import com.example.cekpicklist.viewmodel.ScanViewModel
import com.example.cekpicklist.viewmodel.ScanViewModelFactory

class PicklistInputActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPicklistInputBinding
    private val viewModel: ScanViewModel by viewModels { 
        ScanViewModelFactory(application) 
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.PicklistInput.d("onCreate() started")
        
        super.onCreate(savedInstanceState)
        Logger.PicklistInput.d("super.onCreate() completed")
        
        binding = ActivityPicklistInputBinding.inflate(layoutInflater)
        Logger.PicklistInput.d("binding inflate completed")
        
        setContentView(binding.root)
        Logger.PicklistInput.d("setContentView completed")
        
        setupPicklistSelection()
        Logger.PicklistInput.d("setupPicklistSelection completed")
        
        setupSettingsButton()
        Logger.PicklistInput.d("setupSettingsButton completed")
        
        setupSwipeRefresh()
        Logger.PicklistInput.d("setupSwipeRefresh completed")
        
        setupPicklistObserver()
        Logger.PicklistInput.d("setupPicklistObserver completed")
        
        // Load picklists saat aplikasi dimulai
        viewModel.loadPicklists()
        Logger.PicklistInput.d("loadPicklists called")
        
        // Prefetch data dengan cache management
        Logger.PicklistInput.d("Starting prefetch data with cache management")
        viewModel.prefetchData()
        
        // Setup back press handler
        setupBackPressHandler()
        Logger.PicklistInput.d("setupBackPressHandler completed")
        
        Logger.PicklistInput.i("PicklistInputActivity onCreate completed successfully")
    }
    
    /**
     * Setup OnBackPressedCallback untuk Android API level 33+
     * Dihapus - tidak ada konfirmasi exit application
     */
    private fun setupBackPressHandler() {
        // Tidak ada konfirmasi exit - langsung finish activity
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("PicklistInputActivity", "🔥 OnBackPressedCallback - langsung finish activity")
                finishAffinity() // Keluar dari semua activity dalam task
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("PicklistInputActivity", "🔥 onDestroy - activity dihancurkan")
    }
    
    private fun setupPicklistSelection() {
        // Tampilkan dialog picklist ketika TextView diklik
        binding.tvSelectedPicklist.setOnClickListener {
            showPicklistSelectionDialog()
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("PicklistInputActivity", "🔥 Swipe refresh triggered")
            refreshPicklistData()
        }
        
        // Set warna untuk refresh indicator
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }
    
    private fun setupPicklistObserver() {
        viewModel.picklists.observe(this) { picklists ->
            Log.d("PicklistInputActivity", "🔥 Picklists updated: ${picklists.size} items")
            
            // Update status indicator
            updateStatusIndicator(picklists)
            
            // Stop refresh indicator jika sedang refresh
            if (binding.swipeRefreshLayout.isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
        
        viewModel.picklistStatuses.observe(this) { statuses ->
            Log.d("PicklistInputActivity", "🔥 Picklist statuses updated: ${statuses.size} items")
            
            // Update status indicator dengan informasi scan
            val scannedCount = statuses.count { it.isScanned }
            val totalCount = statuses.size
            
            if (totalCount > 0) {
                showStatusIndicator(
                    icon = android.R.drawable.ic_popup_sync,
                    message = "Data terbaru: $totalCount picklist ($scannedCount sudah di-scan)",
                    isSuccess = true
                )
                
                // Hide status indicator setelah 3 detik
                binding.llStatusIndicator.postDelayed({
                    binding.llStatusIndicator.visibility = View.GONE
                }, 3000)
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d("PicklistInputActivity", "🔥 Loading state: $isLoading")
            
            if (!isLoading && binding.swipeRefreshLayout.isRefreshing) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun refreshPicklistData() {
        Log.d("PicklistInputActivity", "🔥 Starting refresh picklist data")
        
        // Show status indicator
        showStatusIndicator(
            icon = android.R.drawable.ic_popup_sync,
            message = "Mengambil data terbaru dari server...",
            isSuccess = false
        )
        
        // Clear existing data dan fetch fresh data
        viewModel.clearCache()
        viewModel.loadPicklists()
        
        // Show toast notification
        ToastUtils.showHighToastWithCooldown(this, "🔄 Memperbarui data picklist...")
    }
    
    private fun updateStatusIndicator(picklists: List<String>) {
        if (picklists.isEmpty()) {
            showStatusIndicator(
                icon = android.R.drawable.ic_dialog_alert,
                message = "Tidak ada picklist tersedia. Swipe down untuk refresh.",
                isSuccess = false
            )
        } else {
            showStatusIndicator(
                icon = android.R.drawable.ic_popup_sync,
                message = "Data terbaru: ${picklists.size} picklist tersedia",
                isSuccess = true
            )
            
            // Hide status indicator setelah 3 detik
            binding.llStatusIndicator.postDelayed({
                binding.llStatusIndicator.visibility = View.GONE
            }, 3000)
        }
    }
    
    private fun showStatusIndicator(icon: Int, message: String, isSuccess: Boolean) {
        binding.ivStatusIcon.setImageResource(icon)
        binding.tvStatusMessage.text = message
        
        // Set warna berdasarkan status
        val textColor = if (isSuccess) {
            resources.getColor(android.R.color.holo_green_dark, null)
        } else {
            resources.getColor(android.R.color.holo_orange_dark, null)
        }
        binding.tvStatusMessage.setTextColor(textColor)
        
        binding.llStatusIndicator.visibility = View.VISIBLE
    }
    
    private fun showPicklistSelectionDialog() {
        Logger.PicklistInput.d("showPicklistSelectionDialog() called")
        
        val picklistStatuses = viewModel.getPicklistStatuses()
        Logger.PicklistInput.d("Retrieved ${picklistStatuses.size} picklist statuses")
        
        if (picklistStatuses.isEmpty()) {
            Logger.PicklistInput.w("No picklist data available")
            ToastUtils.showHighToastWithCooldown(this, "Tidak ada data picklist")
            return
        }
        
        Logger.PicklistInput.d("Creating picklist selection dialog")
        Logger.Dialog.d("Using CustomDialogTheme for picklist selection")
        Logger.Performance.operationStarted("Dialog Creation")
        
        // Buat custom dialog dengan card style
        val dialogBinding = DialogPicklistSelectionBinding.inflate(layoutInflater)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        Logger.PicklistInput.d("Dialog builder created")
        
        // Set window background untuk round corner
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        
        // Set window sizing untuk dialog yang lebih tinggi
        dialog.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Set maksimal width 85% dari lebar layar
            val maxWidth = (screenWidth * 0.85).toInt()
            // Set maksimal height 70% dari tinggi layar
            val maxHeight = (screenHeight * 0.7).toInt()
            
            Logger.PicklistInput.windowSized(maxWidth, maxHeight)
            
            // Log detailed window metrics
            Logger.Dialog.d("Picklist selection dialog window metrics: ${screenWidth}x${screenHeight}, maxWidth: $maxWidth, maxHeight: $maxHeight")
            
            val layoutParams = window.attributes
            layoutParams.width = maxWidth
            layoutParams.height = maxHeight  // Use maxHeight instead of WRAP_CONTENT
            window.attributes = layoutParams
            
            // Ensure dialog is visible
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        
        Logger.PicklistInput.dialogCreated()
        
        // Setup RecyclerView dengan optimasi performa
        val layoutManager = LinearLayoutManager(this)
        layoutManager.isAutoMeasureEnabled = false
        dialogBinding.rvPicklistList.layoutManager = layoutManager
        
        // Optimasi RecyclerView untuk performa
        dialogBinding.rvPicklistList.setHasFixedSize(true)
        dialogBinding.rvPicklistList.setItemViewCacheSize(10)
        dialogBinding.rvPicklistList.setDrawingCacheEnabled(true)
        dialogBinding.rvPicklistList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH)
        
        Logger.Performance.d("RecyclerView optimized: hasFixedSize=true, cacheSize=10, drawingCache=enabled")
        
        // Log picklist data
        Logger.PicklistInput.adapterSetup(picklistStatuses.size)
        
        // Setup adapter dengan optimasi dan lazy loading
        val adapter = PicklistSelectionAdapter(picklistStatuses) { selectedPicklist ->
            Logger.PicklistInput.picklistSelected(selectedPicklist)
            
            // Update UI
            binding.tvSelectedPicklist.text = selectedPicklist
            binding.tvSelectedPicklist.setTextColor(resources.getColor(android.R.color.black, null))
            binding.tvSelectedPicklist.setTypeface(null, android.graphics.Typeface.NORMAL)
            
            ToastUtils.showHighToastWithCooldown(this, "Memilih: $selectedPicklist")
            
            // Tutup dialog
            dialog.dismiss()
            Logger.PicklistInput.dialogDismissed()
            
            // Navigasi ke MainActivity dengan membawa nomor picklist
            navigateToMainActivity(selectedPicklist)
        }
        
        // Setup adapter dengan optimasi
        dialogBinding.rvPicklistList.adapter = adapter
        
        Logger.PicklistInput.d("Adapter setup completed")
        
        // Setup search functionality dengan delay untuk menghindari blocking
        dialogBinding.root.post {
            setupSearchFunctionality(dialogBinding, adapter, picklistStatuses.size)
            
            // Update count
            updatePicklistCount(dialogBinding, adapter.getFilteredCount(), picklistStatuses.size)
            
            // Show/hide empty state
            updateEmptyState(dialogBinding, adapter.getFilteredCount())
            
            Logger.PicklistInput.dialogShown()
        }
        
        // Show dialog immediately after setup
        Logger.PicklistInput.d("Showing picklist selection dialog...")
        try {
            dialog.show()
            Logger.PicklistInput.d("Dialog shown successfully")
        } catch (e: Exception) {
            Logger.PicklistInput.e("Error showing dialog: ${e.message}")
            Log.e("PicklistInputActivity", "Error showing picklist selection dialog: ${e.message}", e)
        }
        Logger.Performance.operationCompleted("Dialog Creation", System.currentTimeMillis())
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
                
                // Update empty state
                updateEmptyState(dialogBinding, adapter.getFilteredCount())
                
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
    
    private fun updateEmptyState(
        dialogBinding: DialogPicklistSelectionBinding,
        filteredCount: Int
    ) {
        if (filteredCount == 0) {
            dialogBinding.llEmptyState.visibility = View.VISIBLE
            dialogBinding.rvPicklistList.visibility = View.GONE
            
            // Update empty message based on search state
            val searchText = dialogBinding.etSearchPicklist.text.toString()
            dialogBinding.tvEmptyMessage.text = if (searchText.isNotBlank()) {
                "Tidak ada picklist yang cocok dengan \"$searchText\""
            } else {
                "Tidak ada picklist tersedia"
            }
        } else {
            dialogBinding.llEmptyState.visibility = View.GONE
            dialogBinding.rvPicklistList.visibility = View.VISIBLE
        }
    }
    
    private fun navigateToMainActivity(selectedPicklist: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("SELECTED_PICKLIST", selectedPicklist)
        }
        startActivity(intent)
        // Hapus finish() agar bisa kembali ke activity ini
        // finish() // Tutup activity ini agar tidak bisa kembali
    }
    
    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            Log.d("PicklistInputActivity", "🔥 Icon Settings ditekan")
            navigateToSettings()
        }
    }
    
    private fun navigateToSettings() {
        Log.d("PicklistInputActivity", "🔥 Navigasi ke SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}
