package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cekpicklist.adapter.OutActivityAdapter
import com.example.cekpicklist.base.BaseRfidActivity
import com.example.cekpicklist.databinding.ActivityOutBinding
import com.example.cekpicklist.utils.Logger
import com.example.cekpicklist.utils.ToastUtils
import com.example.cekpicklist.viewmodel.OutActivityViewModel
import com.example.cekpicklist.viewmodel.OutActivityViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class OutActivity : BaseRfidActivity() {
    
    private lateinit var binding: ActivityOutBinding
    private lateinit var viewModel: OutActivityViewModel
    private lateinit var adapter: OutActivityAdapter
    private var notrans: String = ""
    
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
        
        // Generate notrans
        notrans = generateNotrans()
        binding.tvNotrans.text = notrans
        
        // Realtime removed (Supabase-only without live subscriptions)

        // Setup UI
        setupRecyclerView()
        setupButtons()
        setupObservers()
        setupSettingsIcon()
        setupBackIcon()
        
        Logger.PicklistInput.d("OutActivity setup completed with notrans: $notrans")
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
        val epcs = data.filter { it.articleId == articleId && it.size.equals(size, true) }
            .map { it.epc }
            .distinct()
        if (epcs.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("EPC $articleName $size")
                .setMessage("Tidak ada EPC untuk artikel ini")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val arr = epcs.toTypedArray()
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
                Logger.PicklistInput.d("Submit successful")
                // Increment counter AFTER successful submit
                incrementTransactionCounterForToday()
                ToastUtils.showHighToastWithCooldown(this, "Out activity berhasil disimpan!")
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
        viewModel.handleRfidDetected(epc)
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
        // This will be handled by the ViewModel's RFID lookup system
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
                viewModel.clearAllItems()
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
        
        Logger.PicklistInput.d("Performing submit with ${items.size} items")
        viewModel.submitOutActivity(notrans)
    }
    
    private fun updateSummaryCards() {
        val items = viewModel.scannedItems.value ?: emptyList()
        val totalScanned = items.sumOf { it.qty }
        
        binding.tvTotalScanned.text = totalScanned.toString()
        
        // Enable submit button if there are items
        binding.btnSubmit.isEnabled = items.isNotEmpty()
    }
    
    /**
     * Generate notrans dengan format OUT01292025
     * OUT = transaksi OUT
     * 01 = transaksi ke 01 hari ini
     * 292025 = tanggal hari ini (DDMMYYYY)
     */
    private fun generateNotrans(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
        val today = dateFormat.format(calendar.time)
        // Read current counter WITHOUT increment
        val number = getCurrentTransactionNumberForToday()
        return "OUT${String.format("%02d", number)}$today"
    }

    private fun getCurrentTransactionNumberForToday(): Int {
        val prefs = getSharedPreferences("out_activity", MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_date", null)
        val lastCount = prefs.getInt("transaction_count", 0)
        return if (lastDate == today && lastCount > 0) lastCount else 1
    }

    private fun incrementTransactionCounterForToday() {
        val prefs = getSharedPreferences("out_activity", MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_date", null)
        val lastCount = prefs.getInt("transaction_count", 0)
        if (lastDate == today) {
            prefs.edit().putInt("transaction_count", (if (lastCount > 0) lastCount + 1 else 2)).apply()
        } else {
            // New day: set to 2 after first submit because current shown was 1
            prefs.edit().putString("last_date", today).putInt("transaction_count", 2).apply()
        }
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
