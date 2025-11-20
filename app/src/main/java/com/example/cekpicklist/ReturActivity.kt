package com.example.cekpicklist

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.adapter.ReturAdapter
import com.example.cekpicklist.base.BaseRfidActivity
import com.example.cekpicklist.databinding.ActivityReturBinding
import com.example.cekpicklist.utils.WarehouseSelectionDialog
import com.example.cekpicklist.viewmodel.ReturViewModel

class ReturActivity : BaseRfidActivity() {

    private lateinit var binding: ActivityReturBinding
    private val viewModel: ReturViewModel by viewModels()
    private lateinit var adapter: ReturAdapter
    private var lastScanButtonClickTime = 0L
    private val scanCooldownMs = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReturBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()

        viewModel.loadWarehouses()
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.ivSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = ReturAdapter()
        binding.rvScannedItems.layoutManager = LinearLayoutManager(this)
        binding.rvScannedItems.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.getItemAt(position)
                if (item != null) {
                    viewModel.removeGroup(item)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvScannedItems)
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            val now = System.currentTimeMillis()
            val elapsed = now - lastScanButtonClickTime
            if (elapsed < scanCooldownMs) {
                val remaining = (scanCooldownMs - elapsed) / 1000 + 1
                Toast.makeText(this, "Tunggu $remaining detik sebelum scan lagi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastScanButtonClickTime = now
            toggleScanning()
        }

        binding.btnClear.setOnClickListener {
            if (isRfidScanning()) {
                stopRfidScanning()
            }
            viewModel.clearAll()
            try {
                rfidScanManager.clearAllData()
            } catch (_: Throwable) {
            }
        }

        binding.btnSubmit.setOnClickListener {
            viewModel.submitRetur()
        }

        binding.tvSelectedWarehouse.setOnClickListener {
            showWarehouseSelection()
        }
    }

    private fun observeViewModel() {
        viewModel.scannedItems.observe(this) { items ->
            adapter.submitList(items)
            val totalValidQty = items.filter { it.isValid }.sumOf { it.quantity }
            binding.tvTotalValid.text = totalValidQty.toString()
        }

        viewModel.invalidScanCount.observe(this) { count ->
            binding.tvInvalidCount.text = count.toString()
        }

        viewModel.isScanning.observe(this) { scanning ->
            binding.btnScan.text = if (scanning == true) "Stop Scan" else "Scan"
        }

        viewModel.selectedWarehouse.observe(this) { warehouse ->
            binding.tvSelectedWarehouse.text = warehouse?.let {
                "${it.warehouseName} (${it.warehouseId})"
            } ?: "Tap untuk memilih"
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
        }

        viewModel.successMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeSuccess()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
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
        viewModel.startScanning()
        startRfidScanning()
    }

    private fun stopScanning() {
        viewModel.stopScanning()
        stopRfidScanningWithGracePeriod()
    }

    private fun showWarehouseSelection() {
        val warehouses = viewModel.warehouses.value.orEmpty()
        if (warehouses.isEmpty()) {
            Toast.makeText(this, "Memuat data warehouse...", Toast.LENGTH_SHORT).show()
            viewModel.loadWarehouses()
            return
        }

        WarehouseSelectionDialog(this, warehouses) { warehouse ->
            viewModel.setSelectedWarehouse(warehouse)
        }.show()
    }

    // ================= BaseRfidActivity Callbacks ================= //
    override fun handleRfidDetected(epc: String, rssi: Int) {
        // Retur sekarang menunggu tombol Stop (grace period) untuk melakukan lookup batch
        // jadi deteksi realtime hanya dicatat oleh RfidScanManager.
    }

    override fun handleScanStateChanged(isScanning: Boolean) {
        binding.btnScan.text = if (isScanning) "Stop Scan" else "Scan"
        if (isScanning) {
            if (viewModel.isScanning.value != true) {
                viewModel.startScanning()
            }
        } else {
            if (viewModel.isScanning.value == true) {
                viewModel.stopScanning()
            }
        }
    }

    override fun handleRfidError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    override fun handleGracePeriodCompleted() {
        // Lookup batch dipicu melalui handleLookupRequired setelah grace period.
    }

    override fun handleLookupRequired(rfidList: List<String>) {
        if (rfidList.isEmpty()) return
        val normalizedList = rfidList.mapNotNull { epc ->
            val trimmed = epc.trim()
            if (trimmed.isNotEmpty()) trimmed else null
        }
        viewModel.processBatchLookup(normalizedList)
    }

    override fun handleDataCleared() {
        viewModel.clearAll()
    }
}
