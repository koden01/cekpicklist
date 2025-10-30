package com.example.cekpicklist.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.cekpicklist.R
import com.example.cekpicklist.BarcodeScannerTabsActivity
import com.example.cekpicklist.viewmodel.BarcodeScannerViewModel
import com.example.cekpicklist.utils.BarcodeAudioManager
import com.example.cekpicklist.utils.BarcodeToastManager
import com.example.cekpicklist.validation.ExpedisiValidator
import com.example.cekpicklist.api.BarcodeExpedisiService
import kotlinx.coroutines.launch
import android.util.Log

class BarcodeInputFragment : Fragment() {

    private lateinit var viewModel: BarcodeScannerViewModel
    private lateinit var tvScanCount: TextView
    private lateinit var tvSelectedExpedisi: TextView
    private lateinit var tvSummaryInfo: TextView
    private lateinit var spinnerExpedisi: Spinner
    private lateinit var spinnerKarung: Spinner
    private lateinit var etBarcodeInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var audioManager: BarcodeAudioManager
    private lateinit var expedisiValidator: ExpedisiValidator
    
    // Scanner input buffering (like web app)
    private var scannerInputBuffer = ""
    private var lastKeyPressTime = 0L
    private val SCANNER_TIMEOUT_MS = 500L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_barcode_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[BarcodeScannerViewModel::class.java]
        
        // Initialize audio manager
        audioManager = BarcodeAudioManager(requireContext())
        audioManager.initAudio()
        
        // Initialize expedisi validator dengan Local-First strategy
        expedisiValidator = ExpedisiValidator(requireContext(), null, null, audioManager)
        
        initViews(view)
        setupObservers()
        setupClickListeners()
        setupSpinners()
    }

    private fun initViews(view: View) {
        tvScanCount = view.findViewById(R.id.tvScanCount)
        tvSelectedExpedisi = view.findViewById(R.id.tvSelectedExpedisi)
        tvSummaryInfo = view.findViewById(R.id.tvSummaryInfo)
        spinnerExpedisi = view.findViewById(R.id.spinnerExpedisi)
        spinnerKarung = view.findViewById(R.id.spinnerKarung)
        etBarcodeInput = view.findViewById(R.id.etBarcodeInput)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupSpinners() {
        // Load expedisi data from database
        lifecycleScope.launch {
            try {
                val expedisiList = viewModel.loadExpedisiData()
                val expedisiListWithPrompt = listOf("Pilih Expedisi") + expedisiList
                val expedisiAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, expedisiListWithPrompt)
                expedisiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerExpedisi.adapter = expedisiAdapter
                
                if (expedisiList.isNotEmpty()) {
                    // Toast dihapus untuk menghindari gangguan setelah memilih expedisi
                    Log.d("BarcodeInputFragment", "📋 Loaded ${expedisiList.size} expedisi from database")
                } else {
                    Toast.makeText(requireContext(), "⚠️ No expedisi data found in database", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // No fallback, must be from database
                val expedisiList = listOf("Pilih Expedisi")
                val expedisiAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, expedisiList)
                expedisiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerExpedisi.adapter = expedisiAdapter
                
                Toast.makeText(requireContext(), "❌ Error loading expedisi data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Setup karung spinner dengan default range (akan diupdate saat expedisi dipilih)
        val defaultKarungList = (1..100).map { it.toString() }
        val karungAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, defaultKarungList)
        karungAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerKarung.adapter = karungAdapter

        // Spinner listeners
        spinnerExpedisi.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedExpedisi = parent?.getItemAtPosition(position) as? String
                selectedExpedisi?.let {
                    if (it != "Pilih Expedisi") {
                        viewModel.setSelectedExpedisi(it)
                        tvSelectedExpedisi.text = it
                        
                        // Update karung options based on selected expedition (mengikuti logika web app)
                        updateKarungOptionsForExpedition(it)
                    } else {
                        // Reset jika "Pilih Expedisi" dipilih
                        viewModel.setSelectedExpedisi("")
                        tvSelectedExpedisi.text = "Belum dipilih"
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerKarung.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKarung = parent?.getItemAtPosition(position) as? String
                selectedKarung?.let {
                    viewModel.setSelectedKarung(it)
                    updateExpeditionInfo()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupObservers() {
        viewModel.scanCount.observe(viewLifecycleOwner) { count ->
            tvScanCount.text = count.toString()
        }
        
        // Observer untuk expedisi berubah
        viewModel.selectedExpedisiLive.observe(viewLifecycleOwner) { expedition ->
            if (expedition.isNotEmpty()) {
                updateExpeditionInfo()
            }
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                etBarcodeInput.setText(it.Resi)
                
                // **TAMBAHAN**: Auto Enter setelah barcode diisi
                Log.d("BarcodeInputFragment", "📱 Auto Enter triggered for barcode: ${it.Resi}")
                processScannedBarcode(it.Resi)
                
                // Play appropriate audio and show toast based on result status (like web app)
                when (it.status) {
                    "success" -> {
                        audioManager.playSuccessBeep()
                        BarcodeToastManager.showSuccess(requireContext(), "Scan berhasil: ${it.Resi}")
                    }
                    "duplicate" -> {
                        audioManager.playDoubleBeep()
                        BarcodeToastManager.showError(requireContext(), it.message ?: "Resi sudah diproses")
                    }
                    "error" -> {
                        audioManager.playFailureBeep()
                        BarcodeToastManager.showError(requireContext(), it.message ?: "Terjadi kesalahan")
                    }
                    else -> {
                        audioManager.playFailureBeep()
                        BarcodeToastManager.showError(requireContext(), it.message ?: "Terjadi kesalahan")
                    }
                }
            }
        }

        // Observe processing state to show/hide loading indicator
        viewModel.isScanning.observe(viewLifecycleOwner) { isProcessing ->
            progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        // Make expedition info clickable (like web app)
        tvSelectedExpedisi.setOnClickListener {
            val expedition = viewModel.selectedExpedisiLive.value
            if (expedition != null && expedition.isNotEmpty()) {
                openKarungSummaryModal(expedition)
            } else {
                // Debug: Jika tidak ada expedisi, cek database dan Supabase
                lifecycleScope.launch {
                    viewModel.debugDatabaseData()
                    viewModel.debugSupabaseConnection()
                    viewModel.debugRawSupabaseCall()
                }
            }
        }

        // Handle barcode input (like web app scanner input buffering)
        etBarcodeInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                
                // Reset buffer if too much time has passed (like web app)
                if (currentTime - lastKeyPressTime > SCANNER_TIMEOUT_MS) {
                    scannerInputBuffer = ""
                }
                lastKeyPressTime = currentTime

                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (scannerInputBuffer.isNotEmpty()) {
                            processScannedBarcode(scannerInputBuffer)
                            scannerInputBuffer = ""
                            etBarcodeInput.setText("")
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DEL -> {
                        scannerInputBuffer = scannerInputBuffer.dropLast(1)
                        etBarcodeInput.setText(scannerInputBuffer)
                        true
                    }
                    else -> {
                        // Handle character input
                        val char = event.unicodeChar.toChar()
                        if (char.isLetterOrDigit() || char.isWhitespace()) {
                            scannerInputBuffer += char
                            etBarcodeInput.setText(scannerInputBuffer)
                        }
                        true
                    }
                }
            } else {
                false
            }
        }
    }

    private fun processScannedBarcode(barcode: String) {
        val expedition = viewModel.selectedExpedisiLive.value
        val karung = viewModel.selectedKarungLive.value
        
        // Use ExpedisiValidator for full validation (like web app)
        lifecycleScope.launch {
            try {
                val validationResult = expedisiValidator.validateBarcode(barcode, expedition, karung)
                
                when (validationResult.status) {
                    ExpedisiValidator.ValidationStatus.OK -> {
                        // Success - update scan count
                        val currentCount = viewModel.scanCount.value ?: 0
                        // TODO: Add setScanCount method to ViewModel
                        Log.d("BarcodeInputFragment", "✅ Validation successful: $barcode")
                    }
                    else -> {
                        // Error cases are already handled by ExpedisiValidator with toast and audio
                        Log.d("BarcodeInputFragment", "❌ Validation failed: ${validationResult.status}")
                    }
                }
            } catch (e: Exception) {
                Log.e("BarcodeInputFragment", "❌ Error processing barcode: ${e.message}")
                BarcodeToastManager.showError(requireContext(), "Terjadi kesalahan: ${e.message}")
                audioManager.playFailureBeep()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Release audio resources
        audioManager.release()
    }
    
    /**
     * Update karung options based on selected expedition (mengikuti logika web app)
     */
    private fun updateKarungOptionsForExpedition(expedition: String) {
        lifecycleScope.launch {
            try {
                // Get karung options based on highest karung for this expedition
                val karungOptions = viewModel.getKarungOptionsForExpedition(expedition)
                val karungAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, karungOptions)
                karungAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerKarung.adapter = karungAdapter
                
                // Set default karung (mengikuti logika web app)
                val defaultKarung = viewModel.getDefaultKarungForExpedition(expedition)
                val defaultIndex = karungOptions.indexOf(defaultKarung)
                if (defaultIndex >= 0) {
                    spinnerKarung.setSelection(defaultIndex)
                    viewModel.setSelectedKarung(defaultKarung)
                    updateExpeditionInfo()
                }
                
                // Toast dihapus untuk menghindari gangguan saat update karung options
                Log.d("BarcodeInputFragment", "📦 Updated karung options for $expedition (default: $defaultKarung)")
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Error updating karung options: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateExpeditionInfo() {
        val expedition = viewModel.selectedExpedisiLive.value ?: ""
        val karung = viewModel.selectedKarungLive.value ?: ""
        val scanCount = viewModel.scanCount.value ?: 0
        
        if (expedition.isNotEmpty() && karung.isNotEmpty()) {
            tvSelectedExpedisi.text = "$expedition - Karung $karung"
            
            // Get real summary data from ViewModel
            lifecycleScope.launch {
                try {
                    val summary = viewModel.getExpeditionSummaryToday(expedition)
                    val total = summary["total"] ?: 0
                    val scanned = summary["scanned"] ?: 0
                    val remaining = summary["remaining"] ?: 0
                    
                    tvSummaryInfo.text = "Total: $total - Scan: $scanned - Sisa: $remaining"
                    
                    Log.d("BarcodeInputFragment", "📊 Updated summary for $expedition: Total=$total, Scan=$scanned, Sisa=$remaining")
                } catch (e: Exception) {
                    Log.e("BarcodeInputFragment", "❌ Error getting expedition summary: ${e.message}")
                    // Fallback to scan count
                    tvSummaryInfo.text = "Total: $scanCount - Scan: $scanCount - Sisa: 0"
                }
            }
        } else {
            tvSelectedExpedisi.text = "Pilih Expedisi"
            tvSummaryInfo.text = "Total: 0 - Scan: 0 - Sisa: 0"
        }
    }

    /**
     * Force refresh data dari Supabase
     */
    private fun forceRefreshData() {
        lifecycleScope.launch {
            try {
                Log.d("BarcodeInputFragment", "🔄 Force refreshing barcode data...")
                
                val success = viewModel.forceRefreshData()
                if (success) {
                    Toast.makeText(requireContext(), "✅ Data berhasil di-refresh", Toast.LENGTH_SHORT).show()
                    
                    // Reload data setelah refresh
                    setupSpinners()
                    updateExpeditionInfo()
                    
                    Log.d("BarcodeInputFragment", "✅ Force refresh completed successfully")
                } else {
                    Toast.makeText(requireContext(), "❌ Gagal refresh data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BarcodeInputFragment", "❌ Error force refreshing data: ${e.message}", e)
                Toast.makeText(requireContext(), "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Debug data untuk troubleshooting
     */
    private fun debugData() {
        lifecycleScope.launch {
            try {
                Log.d("BarcodeInputFragment", "🔍 Debug: Checking barcode data...")
                
                val resiRecords = viewModel.getBarcodeResiRecords()
                val currentExpedition = viewModel.selectedExpedisiLive.value
                val currentKarung = viewModel.selectedKarungLive.value
                
                Log.d("BarcodeInputFragment", "🔍 Debug: Current selection - expedition: $currentExpedition, karung: $currentKarung")
                Log.d("BarcodeInputFragment", "🔍 Debug: Total resi records: ${resiRecords.size}")
                
                if (resiRecords.isNotEmpty()) {
                    Log.d("BarcodeInputFragment", "🔍 Debug: Sample resi records:")
                    resiRecords.take(5).forEach { record ->
                        Log.d("BarcodeInputFragment", "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}', schedule: '${record.schedule}'")
                    }
                    
                    // Cek filtering
                    val expeditionFiltered = resiRecords.filter { it.Keterangan == currentExpedition }
                    val karungFiltered = expeditionFiltered.filter { it.nokarung == currentKarung }
                    
                    Log.d("BarcodeInputFragment", "🔍 Debug: After expedition filter ('$currentExpedition'): ${expeditionFiltered.size}")
                    Log.d("BarcodeInputFragment", "🔍 Debug: After karung filter ('$currentKarung'): ${karungFiltered.size}")
                    
                    val message = "Debug: ${resiRecords.size} total, ${karungFiltered.size} filtered"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                } else {
                    Log.w("BarcodeInputFragment", "🔍 Debug: No resi records found")
                    Toast.makeText(requireContext(), "Debug: No data found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BarcodeInputFragment", "❌ Error debugging data: ${e.message}", e)
                Toast.makeText(requireContext(), "Debug Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openKarungSummaryModal(expedition: String) {
        // Get current date
        val currentDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        // Get real karung summary data from ViewModel
        lifecycleScope.launch {
            try {
                val summaryData = viewModel.getKarungSummaryForExpedition(expedition)
                val modal = KarungSummaryModalFragment.newInstance(expedition, currentDate, summaryData)
                modal.show(parentFragmentManager, "KarungSummaryModal")
            } catch (e: Exception) {
                Log.e("BarcodeInputFragment", "❌ Error loading karung summary: ${e.message}")
                BarcodeToastManager.showError(requireContext(), "Gagal memuat data karung: ${e.message}")
            }
        }
    }
}
