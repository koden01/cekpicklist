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
    private lateinit var tvTodayUnique: TextView
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

    private fun updateTodayUnique() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uniqueCount = viewModel.getTodayUniqueExpedisiCount()
                val scanCount = viewModel.getTodayResiScanCount()
                tvTodayUnique.text = "transaksi hari ini : $uniqueCount  scan : $scanCount"
            } catch (e: Exception) {
                Log.e("BarcodeInputFragment", "❌ Error updating today unique: ${e.message}")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[BarcodeScannerViewModel::class.java]
        
        // **PERBAIKAN**: Gunakan audio manager dari Activity (sudah diinisialisasi di onCreate)
        // Audio manager harus ready SEBELUM scan terjadi, jadi harus diinisialisasi di Activity
        val activity = requireActivity() as? BarcodeScannerTabsActivity
        if (activity != null) {
            // Gunakan audio manager dari Activity (sudah ready)
            audioManager = activity.getAudioManager()
            Log.d("BarcodeInputFragment", "🔊 Using audio manager from Activity (already initialized)")
        } else {
            // Fallback: buat instance sendiri jika Activity tidak tersedia
            audioManager = BarcodeAudioManager(requireContext())
            audioManager.initAudio()
            Log.w("BarcodeInputFragment", "⚠️ Using new audio manager instance (Activity not ready)")
        }
        
        // Initialize expedisi validator dengan Local-First strategy
        expedisiValidator = ExpedisiValidator(requireContext(), null, null, audioManager)
        
        initViews(view)
        setupObservers()
        setupClickListeners()
        setupSpinners()
        updateTodayUnique()
    }

    private fun initViews(view: View) {
        tvScanCount = view.findViewById(R.id.tvScanCount)
        tvSelectedExpedisi = view.findViewById(R.id.tvSelectedExpedisi)
        tvSummaryInfo = view.findViewById(R.id.tvSummaryInfo)
        tvTodayUnique = view.findViewById(R.id.tvTodayUnique)
        spinnerExpedisi = view.findViewById(R.id.spinnerExpedisi)
        spinnerKarung = view.findViewById(R.id.spinnerKarung)
        etBarcodeInput = view.findViewById(R.id.etBarcodeInput)
        progressBar = view.findViewById(R.id.progressBar)
        // Disable soft keyboard popup; keep focus for hardware scanner
        try {
            etBarcodeInput.showSoftInputOnFocus = false
            requireActivity().window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } catch (_: Exception) { }
    }

    private fun setupSpinners() {
        // Load expedisi data from database (tied to view lifecycle)
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            try {
                val expedisiList = viewModel.loadExpedisiData()
                // Hint di posisi TERAKHIR agar tidak muncul di dropdown
                val withHint = expedisiList + listOf("Pilih Expedisi")
                val expedisiAdapter = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, withHint) {
                    override fun getCount(): Int {
                        // Sembunyikan hint (item terakhir) dari dropdown
                        val count = super.getCount()
                        return if (count > 0) count - 1 else count
                    }
                }
                expedisiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerExpedisi.adapter = expedisiAdapter
                
                // **PERBAIKAN**: Restore selection dari ViewModel jika ada, jika tidak baru set ke hint
                val currentExpedisi = viewModel.selectedExpedisiLive.value
                if (currentExpedisi != null && currentExpedisi.isNotEmpty() && expedisiList.contains(currentExpedisi)) {
                    // Restore selection yang sudah ada
                    val restoredIndex = expedisiList.indexOf(currentExpedisi)
                    if (restoredIndex >= 0) {
                        spinnerExpedisi.setSelection(restoredIndex, false) // false = jangan trigger listener
                        Log.d("BarcodeInputFragment", "✅ Restored expedisi selection: $currentExpedisi")
                    }
                } else {
                    // Tidak ada selection sebelumnya, set ke hint
                    spinnerExpedisi.setSelection(expedisiAdapter.count)
                    Log.d("BarcodeInputFragment", "📋 No previous expedisi selection, showing hint")
                }
                
                if (expedisiList.isNotEmpty()) {
                    // Toast dihapus untuk menghindari gangguan setelah memilih expedisi
                    Log.d("BarcodeInputFragment", "📋 Loaded ${expedisiList.size} expedisi from database")
                } else {
                    Toast.makeText(ctx, "⚠️ No expedisi data found in database", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // No fallback, must be from database
                val expedisiAdapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, emptyList<String>())
                expedisiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerExpedisi.adapter = expedisiAdapter
                
                Toast.makeText(ctx, "❌ Error loading expedisi data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Setup karung spinner dengan hint (tidak muncul di dropdown)
        val defaultKarungList = (1..100).map { it.toString() }
        val karungWithHint = defaultKarungList + listOf("Pilih Karung")
        val karungAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, karungWithHint) {
            override fun getCount(): Int {
                val count = super.getCount()
                return if (count > 0) count - 1 else count
            }
        }
        karungAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerKarung.adapter = karungAdapter
        
        // **PERBAIKAN**: Restore selection dari ViewModel jika ada, jika tidak baru set ke hint
        val currentKarung = viewModel.selectedKarungLive.value
        if (currentKarung != null && currentKarung.isNotEmpty() && defaultKarungList.contains(currentKarung)) {
            // Restore selection yang sudah ada
            val restoredIndex = defaultKarungList.indexOf(currentKarung)
            if (restoredIndex >= 0) {
                spinnerKarung.setSelection(restoredIndex, false) // false = jangan trigger listener
                Log.d("BarcodeInputFragment", "✅ Restored karung selection: $currentKarung")
            }
        } else {
            // Tidak ada selection sebelumnya, set ke hint
            spinnerKarung.setSelection(karungAdapter.count)
            Log.d("BarcodeInputFragment", "📦 No previous karung selection, showing hint")
        }

        // Spinner listeners
        spinnerExpedisi.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Jika posisi == count (hint), abaikan
                if (position == (spinnerExpedisi.adapter?.count ?: 0)) {
                    tvSelectedExpedisi.text = "Belum dipilih"
                    return
                }
                val selectedExpedisi = parent?.getItemAtPosition(position) as? String
                selectedExpedisi?.let {
                        viewModel.setSelectedExpedisi(it)
                        tvSelectedExpedisi.text = it
                        updateKarungOptionsForExpedition(it)
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
        viewModel.scanCount.observe(viewLifecycleOwner) { _ ->
            // When global scan count changes, refresh per-expedition+karung CON count
            updateExpeditionInfo()
        }
        
        // Observer untuk expedisi berubah
        viewModel.selectedExpedisiLive.observe(viewLifecycleOwner) { expedition ->
            if (expedition.isNotEmpty()) {
                updateExpeditionInfo()
            }
        }

        // Track last processed result timestamp untuk mencegah re-processing saat fragment muncul kembali
        var lastProcessedTimestamp = 0L

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                Log.d("BarcodeInputFragment", "🔍 ScanResult observer triggered: status=${it.status}, Resi=${it.Resi}, timestamp=${it.timestamp}")
                
                // JANGAN memanggil processScannedBarcode di sini!
                // Observer hanya untuk menampilkan hasil, bukan untuk memproses ulang
                // processScannedBarcode sudah dipanggil sebelumnya saat scan terjadi
                // Memanggilnya lagi akan menyebabkan validasi ulang yang tidak diinginkan
                
                // Skip jika ini adalah value lama (observer re-attach saat tab switch)
                // Hanya proses hasil scan yang baru (timestamp dalam 30 detik terakhir - diperpanjang dari 10 detik)
                val currentTime = System.currentTimeMillis()
                val timeDiff = currentTime - result.timestamp
                if (timeDiff > 30_000 || result.timestamp <= lastProcessedTimestamp) {
                    Log.d("BarcodeInputFragment", "⏭️ Skipping old scan result (age: ${timeDiff}ms, lastProcessed: $lastProcessedTimestamp)")
                    return@let
                }
                
                Log.d("BarcodeInputFragment", "✅ Processing scan result: status=${it.status}, Resi=${it.Resi}")
                lastProcessedTimestamp = result.timestamp
                
                // Play appropriate audio and show toast based on result status (like web app)
                when (it.status) {
                    "success" -> {
                        Log.d("BarcodeInputFragment", "🔊 Playing success beep and showing toast for: ${it.Resi}")
                        audioManager.playSuccessBeep()
                        BarcodeToastManager.showSuccess(requireContext(), "Scan berhasil: ${it.Resi}")
                        // Bersihkan input dan fokus kembali untuk scan berikutnya
                        etBarcodeInput.setText("")
                        etBarcodeInput.requestFocus()
                        updateTodayUnique()
                    }
                    "duplicate" -> {
                        Log.d("BarcodeInputFragment", "🔊 Playing duplicate beep and showing toast for: ${it.Resi}")
                        audioManager.playDoubleBeep()
                        BarcodeToastManager.showError(requireContext(), it.message ?: "Resi sudah diproses")
                        // Tetap kosongkan agar siap input ulang
                        etBarcodeInput.setText("")
                        etBarcodeInput.requestFocus()
                        updateTodayUnique()
                    }
                    "error" -> {
                        Log.d("BarcodeInputFragment", "🔊 Playing failure beep and showing toast for: ${it.Resi}")
                        audioManager.playFailureBeep()
                        BarcodeToastManager.showError(requireContext(), it.message ?: "Terjadi kesalahan")
                        // Tetap kosongkan agar siap input ulang
                        etBarcodeInput.setText("")
                        etBarcodeInput.requestFocus()
                        updateTodayUnique()
                    }
                    else -> {
                        Log.d("BarcodeInputFragment", "🔊 Playing failure beep (unknown status) and showing toast for: ${it.Resi}")
                        audioManager.playFailureBeep()
                        BarcodeToastManager.showError(requireContext(), it.message ?: "Terjadi kesalahan")
                        // Tetap kosongkan agar siap input ulang
                        etBarcodeInput.setText("")
                        etBarcodeInput.requestFocus()
                        updateTodayUnique()
                    }
                }
                // **PERBAIKAN**: Refresh metrics after any scan outcome dengan delay kecil
                // untuk memastikan cache sudah sempat di-update
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(100) // Delay 100ms untuk memastikan cache ter-update
                    updateExpeditionInfo()
                    Log.d("BarcodeInputFragment", "🔄 Expedition info refreshed after scan")
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
                // Jangan blok tombol BACK
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    return@setOnKeyListener false
                }
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
                var karungOptions = viewModel.getKarungOptionsForExpedition(expedition)
                if (karungOptions.isEmpty()) {
                    karungOptions = listOf("1")
                }
                val withHint = karungOptions + listOf("Pilih Karung")
                val karungAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, withHint) {
                    override fun getCount(): Int {
                        val count = super.getCount()
                        return if (count > 0) count - 1 else count
                    }
                }
                karungAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerKarung.adapter = karungAdapter
                
                // **PERBAIKAN**: Cek apakah ada selection karung yang sudah ada untuk expedisi ini
                val currentKarung = viewModel.selectedKarungLive.value
                val shouldRestoreSelection = currentKarung != null && 
                    currentKarung.isNotEmpty() && 
                    karungOptions.contains(currentKarung)
                
                if (shouldRestoreSelection) {
                    // Restore selection yang sudah ada (tidak reset ke default)
                    val restoredIndex = karungOptions.indexOf(currentKarung)
                    if (restoredIndex >= 0) {
                        spinnerKarung.setSelection(restoredIndex, false) // false = jangan trigger listener
                        Log.d("BarcodeInputFragment", "✅ Restored karung selection for expedition '$expedition': $currentKarung")
                        updateExpeditionInfo()
                        return@launch
                    }
                }
                
                // Hanya set default jika belum ada selection sebelumnya
                // Set default karung (mengikuti logika web app)
                val defaultKarung = viewModel.getDefaultKarungForExpedition(expedition)
                val defaultIndex = karungOptions.indexOf(defaultKarung)
                if (defaultIndex >= 0) {
                    // Shift index karena hint di akhir
                    spinnerKarung.setSelection(defaultIndex, false) // false = jangan trigger listener jika tidak perlu
                    viewModel.setSelectedKarung(defaultKarung)
                    Log.d("BarcodeInputFragment", "📦 Set default karung for expedition '$expedition': $defaultKarung")
                    updateExpeditionInfo()
                } else {
                    // Tidak ada default dari data → pilih "1" sebagai default
                    val idxOne = karungOptions.indexOf("1")
                    if (idxOne >= 0) {
                        spinnerKarung.setSelection(idxOne, false) // false = jangan trigger listener jika tidak perlu
                        viewModel.setSelectedKarung("1")
                        Log.d("BarcodeInputFragment", "📦 Set fallback karung '1' for expedition '$expedition'")
                        updateExpeditionInfo()
                    } else {
                        spinnerKarung.setSelection(karungAdapter.count)
                    }
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
        
        // Skip jika masih menggunakan hint text
        if (expedition.isEmpty() || karung.isEmpty() || 
            expedition == "Pilih Expedisi" || karung == "Pilih Karung") {
            tvSelectedExpedisi.text = "Belum dipilih"
            tvSummaryInfo.text = "Total: 0 - Scan: 0 - Sisa: 0"
            tvScanCount.text = "0"
            return
        }
        
        tvSelectedExpedisi.text = "$expedition - Karung $karung"
        
        // Get real summary data from ViewModel
        lifecycleScope.launch {
            try {
                // **PERBAIKAN**: Ambil summary dan pastikan semua nilai di-update di UI thread
                val summary = withContext(Dispatchers.IO) {
                    viewModel.getExpeditionSummaryToday(expedition)
                }
                
                val total = summary["total"] ?: 0
                val scanned = summary["scanned"] ?: 0
                val remaining = summary["remaining"] ?: 0
                
                // **PERBAIKAN**: Update UI di Main thread dengan nilai yang fresh
                withContext(Dispatchers.Main) {
                    tvSummaryInfo.text = "Total: $total - Scan: $scanned - Sisa: $remaining"
                    
                    // Update CON count based on expedition+karung today
                    val conCount = withContext(Dispatchers.IO) {
                        viewModel.getCountByExpeditionAndKarung(expedition, karung)
                    }
                    tvScanCount.text = conCount.toString()
                    
                    Log.d("BarcodeInputFragment", "📊 ✅ UI Updated - Summary for $expedition: Total=$total, Scan=$scanned, Sisa=$remaining")
                }
            } catch (e: Exception) {
                Log.e("BarcodeInputFragment", "❌ Error getting expedition summary: ${e.message}", e)
                // Fallback to scan count
                withContext(Dispatchers.Main) {
                    tvSummaryInfo.text = "Total: $scanCount - Scan: $scanCount - Sisa: 0"
                    tvScanCount.text = "0"
                }
            }
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
                    updateTodayUnique()
                    
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
