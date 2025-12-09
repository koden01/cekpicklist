package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cekpicklist.adapter.OutActivityAdapter
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.base.BaseRfidActivity
import com.example.cekpicklist.databinding.ActivityOutBinding
import com.example.cekpicklist.utils.Logger
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.viewmodel.OutActivityViewModel
import com.example.cekpicklist.viewmodel.OutActivityViewModelFactory

class OutActivity : BaseRfidActivity() {
    
    private lateinit var binding: ActivityOutBinding
    private lateinit var viewModel: OutActivityViewModel
    private lateinit var adapter: OutActivityAdapter
    
    companion object {
        private const val TAG = "OutActivity"
        private const val SCAN_BUTTON_COOLDOWN_MS = 2000L
    }
    
    private var lastScanButtonClickTime = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Logger.PicklistInput.d("OutActivity onCreate() called")
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this, OutActivityViewModelFactory(application))[OutActivityViewModel::class.java]
        
        // Setup UI
        setupRecyclerView()
        setupButtons()
        setupObservers()
        setupSettingsIcon()
        setupBackIcon()
        
        Logger.PicklistInput.d("OutActivity setup completed")
    }

    private fun setupSettingsIcon() {
        try {
            binding.ivSettings.setOnClickListener {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
        } catch (_: Throwable) { }
    }

    private fun setupBackIcon() {
        try {
            binding.ivBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        } catch (_: Throwable) { }
    }
    
    private fun setupRecyclerView() {
        adapter = OutActivityAdapter { groupedItem ->
            Logger.PicklistInput.d("Grouped item clicked: ${groupedItem.articleName}")
            showEpcSelectionDialog(groupedItem.articleId, groupedItem.articleName, groupedItem.size)
        }
        
        binding.recyclerViewScannedItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewScannedItems.adapter = adapter

        // Swipe to delete
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = (adapter as OutActivityAdapter).getItemAt(position)
                if (item != null) {
                    viewModel.removeGroupByArticleAndSize(item.articleId, item.size)
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerViewScannedItems)
    }
    
    private fun showEpcSelectionDialog(articleId: String, articleName: String, size: String) {
        val data = viewModel.scannedItems.value ?: emptyList()
        val uniqueRfids = data.filter { it.articleId == articleId && it.size.equals(size, true) }
            .map { it.epc }
            .distinct()  // **STANDAR**: Konsisten dengan activity lain
        if (uniqueRfids.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("EPC $articleName $size")
                .setMessage("Tidak ada EPC untuk artikel ini")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val arr = uniqueRfids.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pilih EPC - $articleName $size")
            .setItems(arr) { _, which ->
                val selected = arr[which]
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("EPC", selected))
                val intent = android.content.Intent(this, LocationDemoActivity::class.java)
                intent.putExtra("target_epc", selected)
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    
    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastClick = currentTime - lastScanButtonClickTime
            
            Logger.PicklistInput.d("Scan button clicked - isScanning: $isScanning, timeSinceLastClick: ${timeSinceLastClick}ms")
            
            // Check cooldown
            if (timeSinceLastClick < SCAN_BUTTON_COOLDOWN_MS) {
                val remainingCooldown = SCAN_BUTTON_COOLDOWN_MS - timeSinceLastClick
                Logger.PicklistInput.d("Scan button cooldown active - remaining: ${remainingCooldown}ms")
                ToastUtils.showHighToastWithCooldown(this, "Tunggu ${remainingCooldown/1000 + 1} detik sebelum scan lagi")
                return@setOnClickListener
            }
            
            lastScanButtonClickTime = currentTime
            toggleScanning()
        }
        
        binding.btnSubmit.setOnClickListener {
            Logger.PicklistInput.d("Submit button clicked")
            performSubmit()
        }

        binding.btnClear.setOnClickListener {
            Logger.PicklistInput.d("Clear button clicked")
            showClearConfirmationDialog()
        }
    }
    
    private fun setupObservers() {
        viewModel.scannedItems.observe(this) { items ->
            Logger.PicklistInput.d("Scanned items updated: ${items.size} items")
            adapter.updateItems(items)
            updateSummaryCards()
        }
        
        viewModel.isScanning.observe(this) { scanning ->
            this.isScanning = scanning
            binding.btnScan.text = if (scanning) "Stop Scan" else "Scan"
            Logger.PicklistInput.d("Scan state changed: $scanning")
        }
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Logger.PicklistInput.e("Error: $it")
                ToastUtils.showHighToastWithCooldown(this, it)
            }
        }
        
        viewModel.submitSuccess.observe(this) { success ->
            if (success) {
                Logger.PicklistInput.d("Submit successful - tag status updated to SOLD")
                ToastUtils.showHighToastWithCooldown(this, "Tag status berhasil diubah ke SOLD!")
                finish()
            }
        }
    }
    
    private fun toggleScanning() {
        if (isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
    
    private fun startScanning() {
        Logger.PicklistInput.d("Starting RFID scan...")
        viewModel.startScanning()
        startRfidScanning()
    }
    
    private fun stopScanning() {
        Logger.PicklistInput.d("Stopping RFID scan...")
        viewModel.stopScanning()
        stopRfidScanningWithGracePeriod()
    }
    
    // ================= BaseRfidActivity Callbacks =================
    override fun handleRfidDetected(epc: String, rssi: Int) {
        Logger.PicklistInput.d("RFID detected: $epc (RSSI: $rssi)")
        
        // **PERBAIKAN**: Cek apakah EPC sudah ada di scannedItemsList
        val currentScannedItems = viewModel.scannedItems.value ?: emptyList()
        if (currentScannedItems.any { it.epc == epc }) {
            Logger.PicklistInput.d("RFID already in scanned items, skipping: $epc")
            return
        }
        
        // **PERBAIKAN**: Cek cache RfidScanManager dulu sebelum melakukan lookup
        val cachedProduct: NirwanaApiService.ProductInfo? = rfidScanManager.getLookupResultForEpc(epc)
        if (cachedProduct != null) {
            Logger.PicklistInput.d("RFID found in cache: $epc -> ${cachedProduct.articleName}")
            
            // Jika ada di cache dan bukan NOT_FOUND, langsung gunakan
            if (cachedProduct.articleName != "NOT_FOUND" && cachedProduct.articleName.isNotEmpty()) {
                viewModel.addScannedItemFromCache(
                    epc = epc,
                    productInfo = cachedProduct
                )
            } else {
                Logger.PicklistInput.w("RFID found in cache but marked as NOT_FOUND: $epc")
                viewModel.handleRfidDetected(epc) // Fallback ke lookup manual
            }
        } else {
            // Belum ada di cache, tunggu lookup dari RfidScanManager atau lakukan lookup manual
            Logger.PicklistInput.d("RFID not in cache, will lookup: $epc")
            viewModel.handleRfidDetected(epc)
        }
    }
    
    override fun handleScanStateChanged(isScanning: Boolean) {
        this.isScanning = isScanning
        binding.btnScan.text = if (isScanning) "Stop Scan" else "Scan"
        Logger.PicklistInput.d("Scan state changed: $isScanning")
    }
    
    override fun handleRfidError(error: String) {
        Logger.PicklistInput.e("RFID Error: $error")
        ToastUtils.showHighToastWithCooldown(this, error)
    }
    
    override fun handleGracePeriodCompleted() {
        Logger.PicklistInput.d("Grace period completed")
        // Grace period completed, can process any pending scans
    }
    
    override fun handleLookupRequired(rfidList: List<String>) {
        Logger.PicklistInput.d("Lookup required for ${rfidList.size} RFID(s)")
        
        // **PERBAIKAN**: Hanya proses EPC yang BELUM ada di scannedItemsList
        val currentScannedItems = viewModel.scannedItems.value ?: emptyList()
        val existingEpcs = currentScannedItems.map { it.epc }.toSet()
        
        val newEpcs = rfidList.filter { epc -> !existingEpcs.contains(epc) }
        
        if (newEpcs.isEmpty()) {
            Logger.PicklistInput.d("All RFIDs already in scanned items, skipping")
            return
        }
        
        Logger.PicklistInput.d("Processing ${newEpcs.size} new RFID(s) (${rfidList.size - newEpcs.size} already in list)")
        
        // **PERBAIKAN**: Hanya proses EPC yang benar-benar baru
        newEpcs.forEach { epc ->
            val productInfo: NirwanaApiService.ProductInfo? = rfidScanManager.getLookupResultForEpc(epc)
            if (productInfo != null && productInfo.articleName != "NOT_FOUND" && productInfo.articleName.isNotEmpty()) {
                Logger.PicklistInput.d("Processing lookup result for NEW EPC: $epc -> ${productInfo.articleName}")
                viewModel.addScannedItemFromCache(
                    epc = epc,
                    productInfo = productInfo
                )
            } else {
                Logger.PicklistInput.w("Lookup result for $epc is NOT_FOUND or empty")
            }
        }
    }
    
    override fun handleDataCleared() {
        Logger.PicklistInput.d("RFID data cleared callback")
        viewModel.clearAllItems()
        updateSummaryCards()
    }
    
    private fun showClearConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear All Items")
            .setMessage("Apakah Anda yakin ingin menghapus semua item yang sudah di-scan?")
            .setPositiveButton("Ya, Hapus") { _, _ ->
                Logger.PicklistInput.d("User confirmed clear all")
                // **PERBAIKAN**: Clear cache RfidScanManager juga
                rfidScanManager.clearAllData()
                viewModel.clearAllItems()
                Logger.PicklistInput.d("Cleared all items and RFID cache")
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    
    private fun performSubmit() {
        val items = viewModel.scannedItems.value ?: emptyList()
        if (items.isEmpty()) {
            ToastUtils.showHighToastWithCooldown(this, "Tidak ada item untuk disubmit")
            return
        }
        
        Logger.PicklistInput.d("Performing submit - updating tag status to SOLD for ${items.size} items")
        viewModel.submitOutActivity()
    }
    
    private fun updateSummaryCards() {
        val items = viewModel.scannedItems.value ?: emptyList()
        val totalScanned = items.sumOf { it.qty }
        
        binding.tvTotalScanned.text = totalScanned.toString()
        
        // Enable submit button if there are items
        binding.btnSubmit.isEnabled = items.isNotEmpty()
    }
    
    override fun onResume() {
        super.onResume()
        Logger.PicklistInput.d("OutActivity onResume() called")
    }
    
    override fun onPause() {
        super.onPause()
        Logger.PicklistInput.d("OutActivity onPause() called")
        if (isScanning) {
            stopScanning()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Logger.PicklistInput.d("OutActivity onDestroy() called")
        if (isScanning) {
            stopScanning()
        }
    }
}
