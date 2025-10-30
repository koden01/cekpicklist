package com.example.cekpicklist.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cekpicklist.cache.PendingOperationsManager
import com.example.cekpicklist.data.PendingOperationFactory
import com.example.cekpicklist.sync.BackgroundSyncManager
import com.example.cekpicklist.validation.ExpedisiValidator
import com.example.cekpicklist.api.BarcodeExpedisiService
import com.example.cekpicklist.utils.BarcodeAudioManager
import com.example.cekpicklist.utils.BarcodeNotificationManager
import com.example.cekpicklist.repository.EnhancedRepository
import com.example.cekpicklist.api.BarcodeSupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ScanResult(
    val status: String, // "success", "error", "duplicate"
    val message: String,
    val Resi: String, // Barcode value (Primary Key)
    val created: String? = null, // Created timestamp
    val Keterangan: String? = null, // Description/Scanner type
    val nokarung: String? = null, // Karung number
    val schedule: String = "ontime", // Schedule status: 'ontime', 'late', 'batal'
    val timestamp: Long = System.currentTimeMillis()
)

data class ScanHistoryItem(
    val Resi: String, // Barcode value (Primary Key)
    val created: String? = null, // Created timestamp
    val Keterangan: String? = null, // Description/Scanner type
    val nokarung: String? = null, // Karung number
    val schedule: String = "ontime", // Schedule status: 'ontime', 'late', 'batal'
    val timestamp: Long,
    val status: String
)

class BarcodeScannerViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "BarcodeScannerViewModel"
        private const val SCANNER_TIMEOUT_MS = 500L // 500ms timeout for scanner input
    }
    
    // Managers
    private val pendingOperationsManager = PendingOperationsManager(application)
    private lateinit var backgroundSyncManager: BackgroundSyncManager
    private lateinit var expedisiValidator: ExpedisiValidator
    private lateinit var audioManager: BarcodeAudioManager
    private lateinit var notificationManager: BarcodeNotificationManager
    
    // Repository untuk Local-First strategy
    private val enhancedRepository = EnhancedRepository(application)
    
    // Coroutine scope
    private val viewModelScope = CoroutineScope(Dispatchers.Main)
    
    // Current selection state (mengikuti pola web app)
    private var selectedExpedisi: String? = null
    private var selectedKarung: String? = null
    
    // LiveData for scan results
    private val _scanResult = MutableLiveData<ScanResult>()
    val scanResult: LiveData<ScanResult> = _scanResult
    
    // LiveData for scan count
    private val _scanCount = MutableLiveData<Int>()
    val scanCount: LiveData<Int> = _scanCount
    
    // LiveData for last scan time
    private val _lastScanTime = MutableLiveData<String>()
    val lastScanTime: LiveData<String> = _lastScanTime
    
    // LiveData for selected expedisi and karung
    private val _selectedExpedisiLive = MutableLiveData<String>()
    val selectedExpedisiLive: LiveData<String> = _selectedExpedisiLive
    
    private val _selectedKarungLive = MutableLiveData<String>()
    val selectedKarungLive: LiveData<String> = _selectedKarungLive
    
    // LiveData for scan history
    private val _scanHistory = MutableLiveData<List<ScanHistoryItem>>()
    val scanHistory: LiveData<List<ScanHistoryItem>> = _scanHistory
    
    // LiveData for scanner type
    private val _scannerType = MutableLiveData<String>()
    val scannerType: LiveData<String> = _scannerType
    
    // LiveData for scanning state
    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning
    
    // Scan history list - diambil via EnhancedRepository (Supabase-only cache)
    private val _scanHistoryFromDB = MutableLiveData<List<ScanHistoryItem>>()
    val scanHistoryFromDB: LiveData<List<ScanHistoryItem>> = _scanHistoryFromDB
    
    // Processed barcodes untuk mencegah duplikasi - via EnhancedRepository
    private val _processedBarcodesFromDB = MutableLiveData<Set<String>>()
    val processedBarcodesFromDB: LiveData<Set<String>> = _processedBarcodesFromDB
    
    // Scanner input buffer (mengikuti pola web app)
    private var scannerInputBuffer = ""
    private var lastKeyPressTime = 0L
    
    // Processing state
    private var isProcessing = false
    
    init {
        _scanCount.value = 0
        _lastScanTime.value = "Belum ada scan"
        _scannerType.value = "built-in"
        
        // Initialize audio and notification managers
        audioManager = BarcodeAudioManager(application)
        notificationManager = BarcodeNotificationManager(application)
        audioManager.initAudio()
        
        // Load data dari EnhancedRepository (Supabase-only)
        loadScanHistoryFromDatabase()
        loadProcessedBarcodesFromDatabase()
        
        Log.d(TAG, "📱 BarcodeScannerViewModel initialized with Local-First strategy")
    }
    
    /**
     * Load scan history dari EnhancedRepository
     */
    private fun loadScanHistoryFromDatabase() {
        viewModelScope.launch {
            try {
                val barcodeResiRecords = enhancedRepository.getBarcodeResiRecords()
                val scanHistoryItems = barcodeResiRecords.map { record ->
                    ScanHistoryItem(
                        Resi = record.Resi,
                        created = record.created,
                        Keterangan = record.Keterangan,
                        nokarung = record.nokarung,
                        schedule = record.schedule ?: "ontime",
                        timestamp = record.created?.toLongOrNull() ?: System.currentTimeMillis(),
                        status = "success"
                    )
                }
                _scanHistoryFromDB.value = scanHistoryItems
                _scanHistory.value = scanHistoryItems
                Log.d(TAG, "📱 Loaded ${scanHistoryItems.size} scan history items from repository")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading scan history from database: ${e.message}")
                _scanHistoryFromDB.value = emptyList()
                _scanHistory.value = emptyList()
            }
        }
    }
    
    /**
     * Load processed barcodes dari EnhancedRepository
     */
    private fun loadProcessedBarcodesFromDatabase() {
        viewModelScope.launch {
            try {
                val barcodeResiRecords = enhancedRepository.getBarcodeResiRecords()
                val processedBarcodes = barcodeResiRecords.map { it.Resi }.toSet()
                _processedBarcodesFromDB.value = processedBarcodes
                Log.d(TAG, "📱 Loaded ${processedBarcodes.size} processed barcodes from repository")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading processed barcodes from database: ${e.message}")
                _processedBarcodesFromDB.value = emptySet()
            }
        }
    }
    
    /**
     * Initialize background sync manager
     */
    fun initializeBackgroundSync() {
        backgroundSyncManager = BackgroundSyncManager(
            context = getApplication(),
            pendingOperationsManager = pendingOperationsManager
        )
        backgroundSyncManager.startBackgroundSync()
        Log.d(TAG, "🔄 Background sync initialized")
    }
    
    /**
     * Initialize expedisi validator dengan Local-First strategy
     */
    fun initializeExpedisiValidator(context: android.content.Context) {
        // Gunakan EnhancedRepository untuk Local-First strategy
        expedisiValidator = ExpedisiValidator(
            context = context,
            enhancedRepository = enhancedRepository,
            audioManager = audioManager,
            notificationManager = notificationManager
        )
        Log.d(TAG, "🔍 Expedisi validator initialized with Local-First strategy")
    }
    
    /**
     * Set current expedition and karung selection
     */
    fun setSelection(expedisi: String?, karung: String?) {
        selectedExpedisi = expedisi
        selectedKarung = karung
        Log.d(TAG, "📋 Selection updated: expedisi=$expedisi, karung=$karung")
    }
    
    /**
     * Process scanned barcode (mengikuti pola web app dengan validasi expedisi)
     */
    fun processScannedBarcode(barcode: String, scanDuration: Long = 0L, scannerType: String = "built-in") {
        Log.d(TAG, "📱 Processing barcode: $barcode")
        Log.d(TAG, "📱 Barcode length: ${barcode.length}")
        Log.d(TAG, "📱 Scanner type: $scannerType")
        Log.d(TAG, "📱 Is processing: $isProcessing")
        
        if (isProcessing) {
            Log.w(TAG, "⏳ UI update pending. Ignoring rapid input.")
            return
        }
        
        Log.d(TAG, "📱 Setting isProcessing to true")
        isProcessing = true
        
        viewModelScope.launch {
            try {
                val trimmedBarcode = barcode.trim()
                
                // Use ExpedisiValidator untuk validasi yang sama dengan web app
                if (::expedisiValidator.isInitialized) {
                    val validationResult = expedisiValidator.validateBarcode(
                        barcode = trimmedBarcode,
                        selectedExpedisi = selectedExpedisi,
                        selectedKarung = selectedKarung
                    )
                    
                    when (validationResult.status) {
                        ExpedisiValidator.ValidationStatus.INVALID_FORMAT,
                        ExpedisiValidator.ValidationStatus.NOT_FOUND_IN_EXPEDISI,
                        ExpedisiValidator.ValidationStatus.MISMATCH_EXPEDISI,
                        ExpedisiValidator.ValidationStatus.DUPLICATE_PROCESSED -> {
                            _scanResult.value = ScanResult(
                                status = when (validationResult.status) {
                                    ExpedisiValidator.ValidationStatus.DUPLICATE_PROCESSED -> "duplicate"
                                    else -> "error"
                                },
                                message = validationResult.message ?: "Validasi gagal",
                                Resi = trimmedBarcode
                            )
                            isProcessing = false
                            return@launch
                        }
                        ExpedisiValidator.ValidationStatus.OK -> {
                            // Continue with successful processing
                            processSuccessfulScan(
                                barcode = trimmedBarcode,
                                scanDuration = scanDuration,
                                scannerType = scannerType,
                                validationResult = validationResult
                            )
                        }
                    }
                } else {
                    // Fallback to simple validation if validator not initialized
                    processSimpleValidation(trimmedBarcode, scanDuration, scannerType)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing barcode: ${e.message}", e)
                _scanResult.value = ScanResult(
                    status = "error",
                    message = "Terjadi kesalahan: ${e.message}",
                    Resi = barcode
                )
            } finally {
                isProcessing = false
            }
        }
    }
    
    /**
     * Process successful scan dan simpan via EnhancedRepository (Supabase-only cache)
     */
    private fun processSuccessfulScan(
        barcode: String,
        scanDuration: Long,
        scannerType: String,
        validationResult: ExpedisiValidator.ValidationResult
    ) {
        viewModelScope.launch {
            try {
                // Simpan via EnhancedRepository (Local-First strategy)
                val barcodeRecord = BarcodeSupabaseService.BarcodeScanRecord(
                    id = "${barcode}_${System.currentTimeMillis()}",
                    Resi = barcode,
                    created = java.time.Instant.now().toString(), // ISO 8601 format
                    Keterangan = validationResult.actualCourierName ?: scannerType,
                    nokarung = selectedKarung,
                    schedule = validationResult.actualSchedule ?: "ontime"
                )
                
                // Persist via EnhancedRepository
                enhancedRepository.saveBarcodeResiRecord(barcodeRecord)
                
                // **PERBAIKAN KRITIS**: Update flag di tbl_expedisi dari "NO" ke "YES"
                updateExpedisiFlag(barcode)
                
                // Reload data dari database
                loadScanHistoryFromDatabase()
                loadProcessedBarcodesFromDatabase()
                
                // Update scan count dari database
                val currentProcessedBarcodes = _processedBarcodesFromDB.value ?: emptySet()
                _scanCount.value = currentProcessedBarcodes.size
                
                // Update last scan time
                val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                _lastScanTime.value = timeFormat.format(Date())
                
                _scanResult.value = ScanResult(
                    status = "success",
                    message = "Barcode $barcode berhasil di-scan dan disimpan",
                    Resi = barcode,
                    created = java.time.Instant.now().toString(), // ISO 8601 format
                    Keterangan = validationResult.actualCourierName ?: scannerType,
                    nokarung = selectedKarung,
                    schedule = validationResult.actualSchedule ?: "ontime"
                )
                
                Log.d(TAG, "✅ Barcode processed and saved via repository: $barcode")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing barcode: ${e.message}")
                _scanResult.value = ScanResult(
                    status = "error",
                    message = "Gagal menyimpan barcode: ${e.message}",
                    Resi = barcode
                )
            }
        }
    }
    
    /**
     * Fallback simple validation (untuk backward compatibility) dengan Local-First strategy
     */
    private fun processSimpleValidation(barcode: String, scanDuration: Long, scannerType: String) {
        viewModelScope.launch {
            try {
                // Check for duplicates dari EnhancedRepository (local cache)
                val currentProcessedBarcodes = _processedBarcodesFromDB.value ?: emptySet()
                if (currentProcessedBarcodes.contains(barcode)) {
                    val currentScanHistory = _scanHistoryFromDB.value ?: emptyList()
                    val duplicateItem = currentScanHistory.find { it.Resi == barcode }
                    val duplicateTime = duplicateItem?.let {
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                            .format(Date(it.timestamp))
                    } ?: "Tidak diketahui"
                    
                    _scanResult.value = ScanResult(
                        status = "duplicate",
                        message = "Barcode $barcode sudah di-scan sebelumnya pada $duplicateTime",
                        Resi = barcode
                    )
                    return@launch
                }
                
                // Validate barcode format (basic validation)
                if (!isValidBarcode(barcode)) {
                    _scanResult.value = ScanResult(
                        status = "error",
                        message = "Format barcode tidak valid: $barcode",
                        Resi = barcode
                    )
                    return@launch
                }
                
                // Process successful scan with default values
                processSuccessfulScan(
                    barcode = barcode,
                    scanDuration = scanDuration,
                    scannerType = scannerType,
                    validationResult = ExpedisiValidator.ValidationResult(
                        status = ExpedisiValidator.ValidationStatus.OK,
                        actualCourierName = scannerType,
                        actualSchedule = "ontime"
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in simple validation: ${e.message}")
                _scanResult.value = ScanResult(
                    status = "error",
                    message = "Terjadi kesalahan: ${e.message}",
                    Resi = barcode
                )
            }
        }
    }
    
    /**
     * Validate barcode format
     */
    private fun isValidBarcode(barcode: String): Boolean {
        // Basic validation - adjust based on your requirements
        return barcode.length >= 3 && barcode.matches(Regex("[A-Za-z0-9]+"))
    }
    
    /**
     * Handle scanner input buffer (mengikuti pola web app)
     */
    fun handleScannerInput(input: String) {
        val currentTime = System.currentTimeMillis()
        
        // Reset buffer if too much time has passed
        if (currentTime - lastKeyPressTime > SCANNER_TIMEOUT_MS) {
            scannerInputBuffer = ""
        }
        lastKeyPressTime = currentTime
        
        // Add input to buffer
        scannerInputBuffer += input
        
        // Process if buffer is complete (ends with Enter or has reasonable length)
        if (scannerInputBuffer.length >= 3) {
            processScannedBarcode(scannerInputBuffer.trim())
            scannerInputBuffer = ""
        }
    }
    
    /**
     * Get pending operations count
     */
    fun getPendingOperationsCount(): Int {
        return pendingOperationsManager.getPendingOperationsCount()
    }
    
    /**
     * Get sync status
     */
    fun getSyncStatus(): Map<String, Any> {
        return if (::backgroundSyncManager.isInitialized) {
            backgroundSyncManager.getSyncStatus()
        } else {
            mapOf("isSyncing" to false, "pendingOperations" to 0)
        }
    }
    
    /**
     * Trigger manual sync
     */
    fun triggerManualSync() {
        if (::backgroundSyncManager.isInitialized) {
            backgroundSyncManager.forceSyncNow()
        }
    }
    
    /**
     * Get scan history dari EnhancedRepository
     */
    fun getScanHistory(): List<ScanHistoryItem> {
        return _scanHistoryFromDB.value ?: emptyList()
    }
    
    /**
     * Clear scan history dari Room Database
     */
    fun clearScanHistory() {
        Log.d(TAG, "📱 Clearing scan history via repository")
        viewModelScope.launch {
            try {
                // Clear via EnhancedRepository
                enhancedRepository.clearAllBarcodeData()
                
                // Reload data dari database
                loadScanHistoryFromDatabase()
                loadProcessedBarcodesFromDatabase()
                
                // Update UI
                _scanCount.value = 0
                _lastScanTime.value = "Belum ada scan"
                
                Log.d(TAG, "✅ Scan history cleared via repository")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing scan history: ${e.message}")
            }
        }
    }
    
    /**
     * Get scan statistics dari EnhancedRepository data
     */
    fun getScanStatistics(): Map<String, Any> {
        val currentScanHistory = _scanHistoryFromDB.value ?: emptyList()
        val currentProcessedBarcodes = _processedBarcodesFromDB.value ?: emptySet()
        
        val totalScans = currentScanHistory.size
        val uniqueScans = currentProcessedBarcodes.size
        val duplicates = totalScans - uniqueScans
        
        return mapOf(
            "totalScans" to totalScans,
            "uniqueScans" to uniqueScans,
            "duplicates" to duplicates,
            "lastScanTime" to (currentScanHistory.lastOrNull()?.timestamp ?: 0L)
        )
    }
    
    /**
     * Force refresh data dari Supabase
     */
    suspend fun forceRefreshData(): Boolean {
        return try {
            Log.d(TAG, "🔄 Force refreshing all barcode data...")
            
            // Force refresh barcode resi data
            val resiRecords = enhancedRepository.forceRefreshBarcodeData()
            Log.d(TAG, "📥 Retrieved ${resiRecords.size} barcode resi records")
            
            // Force refresh expedisi data
            val expedisiRecords = enhancedRepository.forceRefreshExpedisiData()
            Log.d(TAG, "📥 Retrieved ${expedisiRecords.size} barcode expedisi records")
            
            // Reload data dari database
            loadScanHistoryFromDatabase()
            loadProcessedBarcodesFromDatabase()
            
            Log.d(TAG, "✅ Force refresh completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error force refreshing data: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get barcode resi records untuk debug
     */
    suspend fun getBarcodeResiRecords(): List<BarcodeSupabaseService.BarcodeScanRecord> {
        return try {
            val records = enhancedRepository.getBarcodeResiRecords()
            Log.d(TAG, "📊 Debug: Retrieved ${records.size} barcode resi records")
            Log.d(TAG, "📊 Debug: Sample records:")
            records.take(5).forEach { record ->
                Log.d(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}', schedule: '${record.schedule}'")
            }
            records
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode resi records: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Test join query untuk debug
     */
    suspend fun testJoinQuery(): String {
        return try {
            Log.d(TAG, "🔍 Testing join query...")
            val response = enhancedRepository.testJoinQuery()
            Log.d(TAG, "🔍 Join query response: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing join query: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Get count berdasarkan expedisi dan karung yang dipilih
     */
    suspend fun getCountByExpeditionAndKarung(expedition: String?, karung: String?): Int {
        return try {
            if (expedition == null || karung == null) {
                Log.d(TAG, "📊 No expedition or karung selected, returning 0")
                return 0
            }
            
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            
            Log.d(TAG, "📊 Total resi records: ${resiRecords.size}")
            Log.d(TAG, "📊 Sample resi records:")
            resiRecords.take(3).forEach { record ->
                Log.d(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}'")
            }
            
            // **PERBAIKAN**: Filter berdasarkan Keterangan = expedisi dan nokarung = karung
            val filteredRecords = resiRecords.filter { 
                it.Keterangan == expedition && it.nokarung == karung 
            }
            
            Log.d(TAG, "📊 Filtering criteria:")
            Log.d(TAG, "  - Expedition (Keterangan): '$expedition'")
            Log.d(TAG, "  - Karung (nokarung): '$karung'")
            Log.d(TAG, "📊 Filtered records for expedition '$expedition' and karung '$karung': ${filteredRecords.size}")
            
            // **DEBUG**: Cek apakah ada data yang match
            val expeditionMatches = resiRecords.filter { it.Keterangan == expedition }
            val karungMatches = resiRecords.filter { it.nokarung == karung }
            
            Log.d(TAG, "📊 Debug filtering:")
            Log.d(TAG, "  - Records with Keterangan='$expedition': ${expeditionMatches.size}")
            Log.d(TAG, "  - Records with nokarung='$karung': ${karungMatches.size}")
            Log.d(TAG, "  - Records with both conditions: ${filteredRecords.size}")
            
            if (filteredRecords.isNotEmpty()) {
                Log.d(TAG, "📊 Sample filtered records:")
                filteredRecords.take(3).forEach { record ->
                    Log.d(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}'")
                }
            } else {
                Log.w(TAG, "⚠️ No records found matching criteria")
                Log.w(TAG, "⚠️ Sample expedition matches:")
                expeditionMatches.take(3).forEach { record ->
                    Log.w(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}'")
                }
                Log.w(TAG, "⚠️ Sample karung matches:")
                karungMatches.take(3).forEach { record ->
                    Log.w(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}'")
                }
            }
            
            filteredRecords.size
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting count by expedition and karung: ${e.message}")
            0
        }
    }
    
    /**
     * Update flag di tbl_expedisi dari "NO" ke "YES" ketika resi berhasil di-scan
     */
    private suspend fun updateExpedisiFlag(barcode: String) {
        try {
            Log.d(TAG, "🔄 Updating flag in tbl_expedisi for barcode: $barcode")
            
            // Update flag di local database
            val success = enhancedRepository.updateExpedisiFlag(barcode, "YES")
            
            if (success) {
                Log.d(TAG, "✅ Flag updated to YES for barcode: $barcode")
                
                // Update UI summary info - method akan dipanggil dari fragment
                Log.d(TAG, "📊 Flag updated, UI akan di-refresh dari fragment")
            } else {
                Log.w(TAG, "⚠️ Failed to update flag for barcode: $barcode")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating expedisi flag: ${e.message}", e)
        }
    }
    
    /**
     * Get summary statistics berdasarkan expedisi hari ini
     */
    suspend fun getExpeditionSummaryToday(expedition: String?): Map<String, Int> {
        return try {
            if (expedition == null) {
                Log.d(TAG, "📊 No expedition selected, returning empty summary")
                return mapOf("total" to 0, "scanned" to 0, "remaining" to 0)
            }
            
            // **PERBAIKAN**: Gunakan data dari tbl_expedisi berdasarkan couriername
            val expedisiRecords = enhancedRepository.getBarcodeExpedisiRecords()
            
            Log.d(TAG, "📊 Total expedisi records: ${expedisiRecords.size}")
            Log.d(TAG, "📊 Sample expedisi records:")
            expedisiRecords.take(3).forEach { record ->
                Log.d(TAG, "  - OrderNo: '${record.orderno}', CourierName: '${record.couriername}', ResiNo: '${record.resino}', Flag: '${record.flag}'")
            }
            
            // **PERBAIKAN**: Total resi sesuai expedisi hari ini (couriername = expedition)
            val totalResi = expedisiRecords.filter { it.couriername == expedition }
            Log.d(TAG, "📊 Total resi for expedition '$expedition': ${totalResi.size}")
            
            // **PERBAIKAN**: Total scan sesuai expedisi hari ini (berdasarkan flag = "YES")
            val scannedResi = totalResi.filter { it.flag == "YES" }
            Log.d(TAG, "📊 Scanned resi for expedition '$expedition': ${scannedResi.size}")
            
            // **PERBAIKAN**: Sisa = Total - Scan
            val remaining = totalResi.size - scannedResi.size
            
            Log.d(TAG, "📊 Summary calculation:")
            Log.d(TAG, "  - Total resi (couriername='$expedition'): ${totalResi.size}")
            Log.d(TAG, "  - Scanned resi (flag='YES'): ${scannedResi.size}")
            Log.d(TAG, "  - Remaining (Total - Scanned): $remaining")
            
            val summary = mapOf(
                "total" to totalResi.size,
                "scanned" to scannedResi.size,
                "remaining" to remaining
            )
            
            Log.d(TAG, "📊 Expedition '$expedition' summary today: total=${summary["total"]}, scanned=${summary["scanned"]}, remaining=${summary["remaining"]}")
            summary
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expedition summary: ${e.message}")
            mapOf("total" to 0, "scanned" to 0, "remaining" to 0)
        }
    }
    
    /**
     * Export scan history as text dari EnhancedRepository data
     */
    fun exportScanHistory(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val export = StringBuilder()
        
        val currentScanHistory = _scanHistoryFromDB.value ?: emptyList()
        val currentProcessedBarcodes = _processedBarcodesFromDB.value ?: emptySet()
        
        export.appendLine("=== RIWAYAT SCAN BARCODE (Local Data) ===")
        export.appendLine("Tanggal Export: ${dateFormat.format(Date())}")
        export.appendLine("Total Scan: ${currentScanHistory.size}")
        export.appendLine("Unique Scan: ${currentProcessedBarcodes.size}")
        export.appendLine()
        
        currentScanHistory.forEachIndexed { index, item ->
            export.appendLine("${index + 1}. ${item.Resi}")
            export.appendLine("   Created: ${item.created ?: "N/A"}")
            export.appendLine("   Keterangan: ${item.Keterangan ?: "N/A"}")
            export.appendLine("   Karung: ${item.nokarung ?: "N/A"}")
            export.appendLine("   Schedule: ${item.schedule}")
            export.appendLine("   Waktu: ${dateFormat.format(Date(item.timestamp))}")
            export.appendLine("   Status: ${item.status}")
            export.appendLine()
        }
        
        return export.toString()
    }
    
    /**
     * Methods for fragment communication
     */
    fun setSelectedExpedisi(expedisi: String) {
        selectedExpedisi = expedisi
        _selectedExpedisiLive.value = expedisi
        Log.d(TAG, "📋 Selected expedisi: $expedisi")
    }

    fun setSelectedKarung(karung: String) {
        selectedKarung = karung
        _selectedKarungLive.value = karung
        Log.d(TAG, "📦 Selected karung: $karung")
    }
    
    fun setScanningState(isScanning: Boolean) {
        _isScanning.value = isScanning
        // Update scanner type based on scanning state
        _scannerType.value = if (isScanning) "built-in (active)" else "built-in"
        Log.d(TAG, "📱 Scanning state: $isScanning")
    }
    
    /**
     * Load expedisi data from database dengan Local-First strategy
     */
    suspend fun loadExpedisiData(): List<String> {
        return try {
            val expedisiList = enhancedRepository.getUniqueCourierNames()
            Log.d(TAG, "📋 Loaded ${expedisiList.size} expedisi from Local-First database")
            expedisiList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading expedisi data: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Load karung data from database dengan Local-First strategy
     */
    suspend fun loadKarungData(): List<String> {
        return try {
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            val karungList = resiRecords.mapNotNull { it.nokarung }.distinct().sortedBy { it.toIntOrNull() ?: 0 }
            Log.d(TAG, "📦 Loaded ${karungList.size} karung from Local-First database")
            karungList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading karung data: ${e.message}")
            // Fallback ke hardcoded list
            (1..50).map { it.toString() }
        }
    }
    
    /**
     * Get karung options based on highest karung for specific expedition dengan Local-First strategy
     */
    suspend fun getKarungOptionsForExpedition(expedition: String): List<String> {
        return try {
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            val expeditionKarungs = resiRecords
                .filter { it.Keterangan == expedition }
                .mapNotNull { it.nokarung?.toIntOrNull() }
            
            val highestKarung = if (expeditionKarungs.isNotEmpty()) expeditionKarungs.maxOrNull() ?: 1 else 1
            
            // Mengikuti logika web app: maxKarung = Math.max(1, highestKarung, 100)
            val maxKarung = maxOf(1, highestKarung, 100)
            val karungOptions = (1..maxKarung).map { it.toString() }
            
            Log.d(TAG, "📦 Generated ${karungOptions.size} karung options for expedition '$expedition' (highest: $highestKarung)")
            karungOptions
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating karung options: ${e.message}")
            // Fallback ke default range
            (1..100).map { it.toString() }
        }
    }
    
    /**
     * Get default karung for specific expedition dengan Local-First strategy
     */
    suspend fun getDefaultKarungForExpedition(expedition: String): String {
        return try {
            Log.d(TAG, "🔍 Getting default karung for expedition: '$expedition'")
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            Log.d(TAG, "📊 Total resi records: ${resiRecords.size}")
            
            val expeditionKarungs = resiRecords
                .filter { it.Keterangan == expedition }
                .mapNotNull { it.nokarung?.toIntOrNull() }
            
            Log.d(TAG, "📦 Expedition '$expedition' karungs: $expeditionKarungs")
            
            val highestKarung = if (expeditionKarungs.isNotEmpty()) expeditionKarungs.maxOrNull() ?: 0 else 0
            
            // Mengikuti logika web app: if (highestKarung > 0) setSelectedKarung(highestKarung.toString()) else setSelectedKarung("1")
            val defaultKarung = if (highestKarung > 0) highestKarung.toString() else "1"
            
            Log.d(TAG, "📦 Default karung for expedition '$expedition': $defaultKarung (highest: $highestKarung, karungs: $expeditionKarungs)")
            defaultKarung
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting default karung: ${e.message}", e)
            "1" // Fallback ke "1"
        }
    }

    /**
     * Get karung summary data for specific expedition dengan Local-First strategy
     * Mengikuti logika web app: data karung yang sudah diproses untuk expedisi tertentu
     */
    suspend fun getKarungSummaryForExpedition(expedition: String): List<com.example.cekpicklist.fragment.KarungSummaryModalFragment.KarungSummaryItem> {
        return try {
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            val expeditionRecords = resiRecords.filter { it.Keterangan == expedition }
            
            // Group by karung number and create summary
            val karungGroups = expeditionRecords.groupBy { it.nokarung ?: "0" }
            val summaryData = karungGroups.map { (karung, records) ->
                com.example.cekpicklist.fragment.KarungSummaryModalFragment.KarungSummaryItem(
                    karungNumber = karung,
                    quantity = records.size
                )
            }.sortedBy { it.karungNumber.toIntOrNull() ?: 0 }
            
            Log.d(TAG, "📊 Loaded ${summaryData.size} karung summary items for expedition '$expedition' from Local-First database")
            summaryData
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading karung summary for expedition '$expedition': ${e.message}")
            emptyList() // Return empty list if error
        }
    }
    
    fun startBarcodeScan() {
        // This method will be called by the activity
        Log.d(TAG, "📱 startBarcodeScan called from ViewModel")
    }
    
    fun stopBarcodeScan() {
        // This method will be called by the activity
        Log.d(TAG, "📱 stopBarcodeScan called from ViewModel")
    }
    
    /**
     * Debug method untuk mengecek data di database
     */
    suspend fun debugDatabaseData() {
        try {
            Log.d(TAG, "🔍 === DEBUG DATABASE DATA ===")
            
            // 1. Cek data tbl_expedisi
            val expedisiRecords = enhancedRepository.getBarcodeExpedisiRecords()
            Log.d(TAG, "📊 tbl_expedisi count: ${expedisiRecords.size}")
            
            if (expedisiRecords.isNotEmpty()) {
                Log.d(TAG, "📊 Sample tbl_expedisi records:")
                expedisiRecords.take(3).forEach { record ->
                    Log.d(TAG, "  - orderno: ${record.orderno}, couriername: ${record.couriername}, created: ${record.created}")
                }
            } else {
                Log.w(TAG, "⚠️ tbl_expedisi is EMPTY!")
            }
            
            // 2. Cek unique courier names
            val uniqueCouriers = enhancedRepository.getUniqueCourierNames()
            Log.d(TAG, "📊 Unique courier names count: ${uniqueCouriers.size}")
            
            if (uniqueCouriers.isNotEmpty()) {
                Log.d(TAG, "📊 Unique courier names:")
                uniqueCouriers.forEach { courier ->
                    Log.d(TAG, "  - $courier")
                }
            } else {
                Log.w(TAG, "⚠️ No unique courier names found!")
            }
            
            // 3. Cek data tbl_resi
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            Log.d(TAG, "📊 tbl_resi count: ${resiRecords.size}")
            
            if (resiRecords.isNotEmpty()) {
                Log.d(TAG, "📊 Sample tbl_resi records:")
                resiRecords.take(3).forEach { record ->
                    Log.d(TAG, "  - Resi: ${record.Resi}, Keterangan: ${record.Keterangan}, nokarung: ${record.nokarung}")
                }
            } else {
                Log.w(TAG, "⚠️ tbl_resi is EMPTY!")
            }
            
            Log.d(TAG, "🔍 === END DEBUG DATABASE DATA ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error debugging database data: ${e.message}", e)
        }
    }
    
    /**
     * Debug method untuk test koneksi Supabase langsung
     */
    suspend fun debugSupabaseConnection() {
        try {
            Log.d(TAG, "🌐 === DEBUG SUPABASE CONNECTION ===")
            
            // Test 1: Cek config
            Log.d(TAG, "🔧 Supabase URL: ${com.example.cekpicklist.config.BarcodeSupabaseConfig.SUPABASE_URL}")
            Log.d(TAG, "🔧 Supabase Key: ${com.example.cekpicklist.config.BarcodeSupabaseConfig.SUPABASE_ANON_KEY.take(20)}...")
            
            // Test 2: Test API call langsung ke Supabase
            Log.d(TAG, "🌐 Testing direct Supabase API call...")
            val barcodeService = com.example.cekpicklist.api.BarcodeSupabaseService()
            
            // Test getUniqueCourierNames
            Log.d(TAG, "📞 Calling getUniqueCourierNames()...")
            val courierNames = barcodeService.getUniqueCourierNames()
            Log.d(TAG, "📞 getUniqueCourierNames() result: ${courierNames.size} couriers")
            if (courierNames.isNotEmpty()) {
                Log.d(TAG, "📞 Courier names: $courierNames")
            }
            
            // Test getAllBarcodeExpedisi
            Log.d(TAG, "📞 Calling getAllBarcodeExpedisi()...")
            val expedisiRecords = barcodeService.getAllBarcodeExpedisi()
            Log.d(TAG, "📞 getAllBarcodeExpedisi() result: ${expedisiRecords.size} records")
            if (expedisiRecords.isNotEmpty()) {
                Log.d(TAG, "📞 Sample expedisi records:")
                expedisiRecords.take(3).forEach { record ->
                    Log.d(TAG, "  - orderno: ${record.orderno}, couriername: ${record.couriername}")
                }
            }
            
            Log.d(TAG, "🌐 === END DEBUG SUPABASE CONNECTION ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error debugging Supabase connection: ${e.message}", e)
        }
    }
    
    /**
     * Debug method untuk test raw Supabase API call
     */
    suspend fun debugRawSupabaseCall() {
        try {
            Log.d(TAG, "🔍 === DEBUG RAW SUPABASE API CALL ===")
            
            val supabaseUrl = com.example.cekpicklist.config.BarcodeSupabaseConfig.SUPABASE_URL
            val supabaseKey = com.example.cekpicklist.config.BarcodeSupabaseConfig.SUPABASE_ANON_KEY
            
            // Test 1: Cek apakah tabel ada
            Log.d(TAG, "🔍 Testing if tbl_expedisi exists...")
            val testUrl = "$supabaseUrl/rest/v1/tbl_expedisi?select=count"
            
            val connection = java.net.URL(testUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "🔍 Response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "🔍 Response: $response")
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error message"
                Log.e(TAG, "❌ Error response: $errorResponse")
            }
            
            // Test 2: Cek data tanpa filter tanggal
            Log.d(TAG, "🔍 Testing data without date filter...")
            val allDataUrl = "$supabaseUrl/rest/v1/tbl_expedisi?select=couriername&couriername=not.is.null&limit=10"
            
            val connection2 = java.net.URL(allDataUrl).openConnection() as java.net.HttpURLConnection
            connection2.requestMethod = "GET"
            connection2.setRequestProperty("apikey", supabaseKey)
            connection2.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection2.setRequestProperty("Content-Type", "application/json")
            
            val responseCode2 = connection2.responseCode
            Log.d(TAG, "🔍 All data response code: $responseCode2")
            
            if (responseCode2 == 200) {
                val response2 = connection2.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "🔍 All data response: $response2")
            } else {
                val errorResponse2 = connection2.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error message"
                Log.e(TAG, "❌ All data error response: $errorResponse2")
            }
            
            // Test 3: Cek data dengan filter tanggal hari ini
            Log.d(TAG, "🔍 Testing data with today filter...")
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            val todayDataUrl = "$supabaseUrl/rest/v1/tbl_expedisi?select=couriername&couriername=not.is.null&created=gte.$todayDate&limit=10"
            
            val connection3 = java.net.URL(todayDataUrl).openConnection() as java.net.HttpURLConnection
            connection3.requestMethod = "GET"
            connection3.setRequestProperty("apikey", supabaseKey)
            connection3.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection3.setRequestProperty("Content-Type", "application/json")
            
            val responseCode3 = connection3.responseCode
            Log.d(TAG, "🔍 Today data response code: $responseCode3")
            Log.d(TAG, "🔍 Today date filter: $todayDate")
            
            if (responseCode3 == 200) {
                val response3 = connection3.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "🔍 Today data response: $response3")
            } else {
                val errorResponse3 = connection3.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error message"
                Log.e(TAG, "❌ Today data error response: $errorResponse3")
            }
            
            Log.d(TAG, "🔍 === END DEBUG RAW SUPABASE API CALL ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in raw Supabase API call: ${e.message}", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Stop background sync
        if (::backgroundSyncManager.isInitialized) {
            backgroundSyncManager.stopBackgroundSync()
        }
        
        Log.d(TAG, "📱 BarcodeScannerViewModel cleared")
    }
}
