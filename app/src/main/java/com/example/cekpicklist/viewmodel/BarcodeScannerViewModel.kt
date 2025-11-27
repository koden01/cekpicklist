package com.example.cekpicklist.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cekpicklist.cache.PendingOperationsManager
import com.example.cekpicklist.data.PendingOperationFactory
import com.example.cekpicklist.validation.ExpedisiValidator
import com.example.cekpicklist.api.BarcodeExpedisiService
import com.example.cekpicklist.utils.BarcodeNotificationManager
import com.example.cekpicklist.repository.EnhancedRepository
import com.example.cekpicklist.cache.BarcodeCacheManager
import com.example.cekpicklist.api.BarcodeSupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
        private const val MIN_SCAN_INTERVAL_MS = 100L // Minimum 100ms antar scan untuk notifikasi jelas
    }
    
    // Managers
    private val pendingOperationsManager = PendingOperationsManager(application)
    private lateinit var expedisiValidator: ExpedisiValidator
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

    private val _isSyncingMasterData = MutableLiveData(false)
    val isSyncingMasterData: LiveData<Boolean> = _isSyncingMasterData
    
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
    
    // Debounce: timestamp terakhir scan selesai (untuk delay antar scan)
    // Inisialisasi dengan 0L, scan pertama akan selalu diizinkan karena lastScanCompleteTime == 0
    private var lastScanCompleteTime = 0L
    
    init {
        _scanCount.value = 0
        _lastScanTime.value = "Belum ada scan"
        _scannerType.value = "built-in"
        
        // Initialize notification manager saja (audio dikelola oleh Activity/Fragment)
        notificationManager = BarcodeNotificationManager(application)
        
        // Initialize cache dengan background loading
        initializeCacheAsync()
        
        Log.d(TAG, "📱 BarcodeScannerViewModel initialized with Light Cache strategy")
    }

    fun syncBarcodeMasterData(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                _isSyncingMasterData.value = true
                val success = withContext(Dispatchers.IO) {
                    enhancedRepository.forceBarcodeRoomRefresh()
                }
                _isSyncingMasterData.value = false
                if (success) {
                    loadScanHistoryFromDatabase()
                    loadProcessedBarcodesFromDatabase()
                }
                onComplete(success)
            } catch (e: Exception) {
                _isSyncingMasterData.value = false
                onComplete(false)
                Log.e(TAG, "❌ Error syncing barcode master data: ${e.message}", e)
            }
        }
    }
    
    /**
     * Initialize cache secara async saat ViewModel dibuat
     * **OPTIMASI**: Gunakan cache yang sudah ada langsung, refresh di background jika expired
     */
    private fun initializeCacheAsync() {
        viewModelScope.launch {
            try {
                // **ROOM-ONLY**: Load UI data dari Room (tidak ada auto-fetch dari Supabase)
                loadScanHistoryFromDatabase()
                loadProcessedBarcodesFromDatabase()
                
                // Check cache dari Room
                val cachedResi = BarcodeCacheManager.getAllResiRecords()
                val cachedExpedisi = BarcodeCacheManager.getAllExpedisiRecords()
                
                // **ROOM-ONLY**: Hanya load dari Room, tidak ada auto-fetch
                if (cachedResi.isNotEmpty() && cachedExpedisi.isNotEmpty()) {
                    Log.d(TAG, "⚡ Loaded from Room: ${cachedResi.size} resi, ${cachedExpedisi.size} expedisi")
                    
                    // Pre-warm courier names cache dari expedisi cache yang sudah ada
                    val courierNames = BarcodeCacheManager.getUniqueCourierNamesFromCache()
                    if (courierNames.isNotEmpty() && !BarcodeCacheManager.isCouriersCacheValid()) {
                        BarcodeCacheManager.setCourierNamesCache(courierNames)
                        Log.d(TAG, "⚡ Pre-warmed courier names from existing expedisi cache: ${courierNames.size} couriers")
                    }
                } else {
                    Log.d(TAG, "📦 Room cache empty - data hanya akan tersedia setelah user melakukan sync manual")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing cache: ${e.message}", e)
                // Fallback: tetap load data yang ada dari cache
                loadScanHistoryFromDatabase()
                loadProcessedBarcodesFromDatabase()
            }
        }
    }
    
    /**
     * Load scan history dari Cache Manager (fast lookup)
     */
    private fun loadScanHistoryFromDatabase() {
        viewModelScope.launch {
            try {
                val historyLoadResult = withContext(Dispatchers.IO) {
                    // **ROOM-ONLY**: Langsung load dari Room (tidak ada auto-fetch)
                    val cachedRecords = BarcodeCacheManager.getAllResiRecords()
                    Log.d(TAG, "📦 Total records in cache: ${cachedRecords.size}")
                    
                    // **PERUBAHAN**: Tampilkan SEMUA data di cache, tidak peduli apakah punya created atau tidak
                    // **SORTING**: Urutkan berdasarkan kolom `created` secara langsung (ISO 8601 string comparison)
                    // **FORMAT**: Kolom `created` di cache adalah sama persis dengan `created` di tbl_resi Supabase
                    // Format yang digunakan: ISO 8601 UTC (contoh: "2024-11-01T10:30:00.000Z" atau "2024-11-01T10:30:00Z")
                    // Alur: ViewModel (Instant.now().toString()) -> Supabase (simpan as-is) -> Ambil (optString, as-is) -> Cache (simpan as-is)
                    var withCreatedCount = 0
                    var withoutCreatedCount = 0
                    val scanHistoryItems = cachedRecords.map { record ->
                        // Parse created timestamp untuk timestamp field (untuk display)
                        val timestamp = try {
                            if (!record.created.isNullOrBlank()) {
                                try {
                                    // **PERBAIKAN**: Support multiple ISO 8601 formats
                                    // Format 1: "2024-11-01T10:30:00.000Z" (dengan Z)
                                    // Format 2: "2024-11-01T10:30:00.000+00:00" (dengan offset seperti "2025-10-29T09:15:26.678+00:00")
                                    // Format 3: "2024-11-01T10:30:00Z" (tanpa milliseconds)
                                    // **STRATEGI**: Coba parse sebagai OffsetDateTime dulu (untuk format dengan offset), jika gagal baru parse sebagai Instant
                                    val instant = try {
                                        // Coba parse sebagai OffsetDateTime (untuk format dengan +00:00 atau -00:00)
                                        java.time.OffsetDateTime.parse(record.created).toInstant()
                                    } catch (_: Exception) {
                                        // Jika gagal, coba parse sebagai Instant (untuk format dengan Z)
                                        java.time.Instant.parse(record.created)
                                    }
                                    withCreatedCount++
                                    instant.toEpochMilli()
                                } catch (parseError: Exception) {
                                    // Jika parsing gagal, gunakan fallback timestamp
                                    withoutCreatedCount++
                                    Log.w(TAG, "⚠️ Failed to parse created '${record.created}' for resi ${record.Resi}, using fallback timestamp: ${parseError.message}")
                                    Long.MIN_VALUE // Urutan terakhir
                                }
                            } else {
                                // Data tanpa created: gunakan fallback timestamp
                                withoutCreatedCount++
                                Long.MIN_VALUE // Urutan terakhir
                            }
                        } catch (e: Exception) {
                            // Error parsing: gunakan fallback timestamp
                            withoutCreatedCount++
                            Log.w(TAG, "⚠️ Unexpected error parsing created for resi ${record.Resi}, using fallback: ${e.message}")
                            Long.MIN_VALUE // Urutan terakhir
                        }
                        
                        ScanHistoryItem(
                            Resi = record.Resi,
                            created = record.created ?: "N/A", // Kolom created sama persis dengan Supabase (tidak dimodifikasi)
                            Keterangan = record.Keterangan,
                            nokarung = record.nokarung,
                            schedule = record.schedule ?: "ontime",
                            timestamp = timestamp,
                            status = "success"
                        )
                    }
                    
                    // **SORT: Urutkan dari TERBARU ke LAMA berdasarkan kolom `created` (ISO 8601 string)**
                    // Sorting menggunakan `created` string secara langsung untuk akurasi maksimal
                    // Format ISO 8601 (e.g., "2024-11-01T10:30:00.000Z") dapat di-sort sebagai string secara leksikografis
                    val sortedHistoryItems = scanHistoryItems.sortedWith(compareByDescending<ScanHistoryItem> { item ->
                        // Prioritas 1: Data dengan created yang valid (bukan "N/A" atau null)
                        if (!item.created.isNullOrBlank() && item.created != "N/A") {
                            item.created // Sort berdasarkan created string (ISO 8601 format)
                        } else {
                            // Prioritas 2: Data tanpa created (diurutkan terakhir)
                            "" // Empty string akan selalu di bawah
                        }
                    })
                    
                    Triple(sortedHistoryItems, withCreatedCount, withoutCreatedCount)
                }
                
                val sortedHistoryItems = historyLoadResult.first
                val withCreatedCount = historyLoadResult.second
                val withoutCreatedCount = historyLoadResult.third
                
                _scanHistoryFromDB.value = sortedHistoryItems
                _scanHistory.value = sortedHistoryItems
                Log.d(TAG, "📱 Loaded ${sortedHistoryItems.size} scan history items from cache (${withCreatedCount} with valid created, ${withoutCreatedCount} without created)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading scan history from database: ${e.message}")
                _scanHistoryFromDB.value = emptyList()
                _scanHistory.value = emptyList()
            }
        }
    }
    
    /**
     * Load processed barcodes dari Cache Manager (HashSet - fast lookup)
     */
    private fun loadProcessedBarcodesFromDatabase() {
        viewModelScope.launch {
            try {
                val processedResult = withContext(Dispatchers.IO) {
                    // **ROOM-ONLY**: Langsung load dari Room (tidak ada auto-fetch)
                    BarcodeCacheManager.getProcessedResiSet()
                }
                
                _processedBarcodesFromDB.value = processedResult
                Log.d(TAG, "📱 Loaded ${processedResult.size} processed barcodes from Room (HashSet)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading processed barcodes: ${e.message}")
                // Fallback to cache if available
                val cachedSet = BarcodeCacheManager.getProcessedResiSet()
                _processedBarcodesFromDB.value = if (cachedSet.isNotEmpty()) cachedSet else emptySet()
            }
        }
    }
    
    /**
     * Initialize expedisi validator dengan Local-First strategy
     */
    fun initializeExpedisiValidator(context: android.content.Context) {
        // Gunakan EnhancedRepository untuk Local-First strategy
        expedisiValidator = ExpedisiValidator(
            context = context,
            audioManager = null,
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
     * **PERBAIKAN**: Debounce - jangan terima barcode baru sebelum barcode lama selesai
     */
    fun processScannedBarcode(barcode: String, scanDuration: Long = 0L, scannerType: String = "built-in") {
        val trimmedBarcode = barcode.trim()
        val currentTime = System.currentTimeMillis()
        
        Log.d(TAG, "📱 Processing barcode: $trimmedBarcode")
        Log.d(TAG, "📱 Barcode length: ${trimmedBarcode.length}")
        Log.d(TAG, "📱 Scanner type: $scannerType")
        Log.d(TAG, "📱 Is processing: $isProcessing")
        Log.d(TAG, "📱 Time since last scan: ${currentTime - lastScanCompleteTime}ms")
        
        // **DEBOUNCE**: Tolak barcode baru jika masih memproses
        if (isProcessing) {
            Log.w(TAG, "⏳ Masih memproses scan sebelumnya. Barcode $trimmedBarcode ditolak (tunggu hingga selesai)")
            // Tampilkan feedback cepat kepada user bahwa scan ditolak
            viewModelScope.launch {
                _scanResult.value = ScanResult(
                    status = "error",
                    message = "Tunggu scan sebelumnya selesai",
                    Resi = trimmedBarcode,
                    timestamp = currentTime
                )
            }
            return
        }
        
        // **DEBOUNCE**: Cek minimum interval antar scan untuk memastikan notifikasi terdengar
        val timeSinceLastScan = currentTime - lastScanCompleteTime
        if (timeSinceLastScan < MIN_SCAN_INTERVAL_MS && lastScanCompleteTime > 0) {
            val remainingTime = MIN_SCAN_INTERVAL_MS - timeSinceLastScan
            Log.w(TAG, "⏳ Terlalu cepat! Tunggu ${remainingTime}ms sebelum scan berikutnya. Barcode $trimmedBarcode ditolak")
            // Tampilkan feedback cepat kepada user bahwa scan terlalu cepat
            viewModelScope.launch {
                _scanResult.value = ScanResult(
                    status = "error",
                    message = "Scan terlalu cepat, tunggu sebentar",
                    Resi = trimmedBarcode,
                    timestamp = currentTime
                )
            }
            return
        }
        
        // **PROSES LANGSUNG**: Jika tidak ada yang sedang diproses dan interval cukup
        Log.d(TAG, "📱 Processing barcode: $trimmedBarcode")
        processBarcodeInternal(trimmedBarcode, scanDuration, scannerType)
    }
    
    /**
     * Internal method untuk memproses barcode
     */
    private fun processBarcodeInternal(barcode: String, scanDuration: Long, scannerType: String) {
        Log.d(TAG, "📱 Setting isProcessing to true for: $barcode")
        isProcessing = true
        
        viewModelScope.launch {
            try {
                val trimmedBarcode = barcode.trim()
                
                // **VALIDASI AWAL**: Cek expedisi dan karung sudah dipilih sebelum masuk ke ExpedisiValidator
                val expedisiError = when {
                    selectedExpedisi.isNullOrEmpty() -> "Mohon pilih Expedisi terlebih dahulu."
                    selectedExpedisi == "Pilih Expedisi" -> "Mohon pilih Expedisi terlebih dahulu."
                    else -> null
                }
                
                val karungError = when {
                    selectedKarung.isNullOrEmpty() -> "Mohon pilih No Karung terlebih dahulu."
                    selectedKarung == "Pilih Karung" -> "Mohon pilih No Karung terlebih dahulu."
                    else -> null
                }
                
                if (expedisiError != null || karungError != null) {
                    val errorMessage = when {
                        expedisiError != null && karungError != null -> "Mohon pilih Expedisi dan No Karung terlebih dahulu."
                        expedisiError != null -> expedisiError
                        karungError != null -> karungError
                        else -> "Mohon pilih Expedisi dan No Karung terlebih dahulu."
                    }
                    
                    Log.w(TAG, "🛑 [BLOCKED] $errorMessage - scan tidak dapat diproses")
                    _scanResult.value = ScanResult(
                        status = "error",
                        message = errorMessage,
                        Resi = trimmedBarcode,
                        timestamp = System.currentTimeMillis()
                    )
                    isProcessing = false
                    return@launch
                }
                
                // **SATU JALUR VALIDASI**: Hanya menggunakan ExpedisiValidator
                // Jika validator belum di-initialize, initialize secara otomatis
                if (!::expedisiValidator.isInitialized) {
                    Log.w(TAG, "⚠️ ExpedisiValidator not initialized, initializing automatically...")
                    try {
                        // Initialize validator secara otomatis menggunakan Application context
                        initializeExpedisiValidator(getApplication())
                        Log.d(TAG, "✅ ExpedisiValidator auto-initialized successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ [BLOCKED] Failed to auto-initialize ExpedisiValidator: ${e.message}", e)
                        _scanResult.value = ScanResult(
                            status = "error",
                            message = "Sistem validasi belum siap. Silakan restart aplikasi.",
                            Resi = trimmedBarcode,
                            timestamp = System.currentTimeMillis()
                        )
                        isProcessing = false
                        return@launch
                    }
                }
                
                // **SATU JALUR VALIDASI**: ExpedisiValidator dengan urutan:
                // 1. Cek Expedisi & Karung
                // 2. Cek Duplicate (semua data cache tbl_resi, 7 hari terakhir)
                // 3. Cek NOT_FOUND (tbl_expedisi, flag=NO)
                // 4. Cek Mismatch Expedisi
                // 5. VALIDASI OK → Data disimpan
                    val validationResult = expedisiValidator.validateBarcode(
                        barcode = trimmedBarcode,
                        selectedExpedisi = selectedExpedisi,
                        selectedKarung = selectedKarung
                    )
                    
                // **HANYA menyimpan jika VALIDASI OK**
                    when (validationResult.status) {
                        ExpedisiValidator.ValidationStatus.INVALID_FORMAT,
                        ExpedisiValidator.ValidationStatus.NOT_FOUND_IN_EXPEDISI,
                        ExpedisiValidator.ValidationStatus.MISMATCH_EXPEDISI,
                        ExpedisiValidator.ValidationStatus.DUPLICATE_PROCESSED -> {
                        // Validasi gagal → TIDAK menyimpan data
                        Log.d(TAG, "🛑 Validasi gagal (${validationResult.status}), data TIDAK disimpan: $trimmedBarcode")
                            _scanResult.value = ScanResult(
                                status = when (validationResult.status) {
                                    ExpedisiValidator.ValidationStatus.DUPLICATE_PROCESSED -> "duplicate"
                                    ExpedisiValidator.ValidationStatus.MISMATCH_EXPEDISI -> "mismatch"
                                    else -> "error"
                                },
                                message = validationResult.message ?: "Validasi gagal",
                                Resi = trimmedBarcode,
                                schedule = validationResult.resiDetails?.schedule ?: "ontime", // Pass schedule untuk duplicate
                                timestamp = System.currentTimeMillis()
                            )
                            isProcessing = false
                            return@launch
                        }
                        ExpedisiValidator.ValidationStatus.OK -> {
                        // **HANYA jika VALIDASI OK**: Data disimpan
                        Log.d(TAG, "✅ Validasi OK, data akan disimpan: $trimmedBarcode")
                            // **PERBAIKAN**: Await processSuccessfulScan agar isProcessing di-reset setelah selesai
                            processSuccessfulScan(
                                barcode = trimmedBarcode,
                                scanDuration = scanDuration,
                                scannerType = scannerType,
                                validationResult = validationResult
                            )
                            // **PERBAIKAN**: Reset isProcessing setelah processSuccessfulScan selesai
                            isProcessing = false
                            lastScanCompleteTime = System.currentTimeMillis()
                            Log.d(TAG, "✅ Scan selesai, ready untuk scan berikutnya (debounce: ${MIN_SCAN_INTERVAL_MS}ms)")
                        }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing barcode: ${e.message}", e)
                _scanResult.value = ScanResult(
                    status = "error",
                    message = "Terjadi kesalahan: ${e.message}",
                    Resi = barcode,
                    timestamp = System.currentTimeMillis()
                )
                isProcessing = false
                lastScanCompleteTime = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Process successful scan dan simpan via EnhancedRepository (Supabase-only cache)
     * **KRITIS**: Hanya menyimpan jika VALIDASI OK
     * **PERBAIKAN**: Ubah menjadi suspend function agar bisa di-await
     */
    private suspend fun processSuccessfulScan(
        barcode: String,
        scanDuration: Long,
        scannerType: String,
        validationResult: ExpedisiValidator.ValidationResult
    ) {
        try {
                // **GUARD**: Pastikan hanya menyimpan jika VALIDASI OK
                if (validationResult.status != ExpedisiValidator.ValidationStatus.OK) {
                    Log.e(TAG, "🚫 [BLOCKED] Attempted to save data with invalid status: ${validationResult.status} for resi: $barcode")
                    _scanResult.value = ScanResult(
                        status = "error",
                        message = "Validasi gagal: ${validationResult.message ?: "Status tidak valid"}",
                        Resi = barcode,
                        timestamp = System.currentTimeMillis()
                    )
                    return
                }
                
                Log.d(TAG, "✅ [VALIDASI OK] Saving data for resi: $barcode")
                
                // **LOCAL-FIRST STRATEGY**: Biarkan Repository handle (cache first, lalu sync)
                val createdTime = java.time.Instant.now().toString()
                val barcodeRecord = BarcodeSupabaseService.BarcodeScanRecord(
                    id = "${barcode}_${System.currentTimeMillis()}",
                    Resi = barcode,
                    created = createdTime, // ISO 8601 format
                    Keterangan = validationResult.actualCourierName ?: scannerType,
                    nokarung = selectedKarung,
                    schedule = validationResult.actualSchedule ?: "ontime"
                )
                
                // **LOCAL-FIRST**: Save via Repository (cache first, network background)
                // Repository sudah handle: addResiRecord ke cache INSTAN, lalu POST ke Supabase di background
                // Return value selalu true (optimistic) karena cache sudah di-update
                val cacheUpdated = enhancedRepository.saveBarcodeResiRecord(barcodeRecord)
                
                if (!cacheUpdated) {
                    // Cache update gagal (sangat jarang), tampilkan error
                    Log.e(TAG, "❌ Cache update failed for: $barcode")
                    _scanResult.value = ScanResult(
                        status = "error",
                    message = "Gagal menyimpan ke cache lokal",
                    Resi = barcode,
                    timestamp = System.currentTimeMillis()
                )
                return
            }
                
                // **OPTIMISTIC UI UPDATE**: Update UI INSTAN (cache sudah di-update oleh Repository)
                val processedCount = BarcodeCacheManager.getProcessedResiSet().size
                _scanCount.value = processedCount
                _processedBarcodesFromDB.value = BarcodeCacheManager.getProcessedResiSet()
                
                // Update last scan time
                val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                _lastScanTime.value = timeFormat.format(Date())
                
                // **IMMEDIATE UI FEEDBACK**: Update scanResult (cache sudah updated oleh Repository)
                val resultTimestamp = System.currentTimeMillis()
                _scanResult.value = ScanResult(
                    status = "success",
                    message = "Barcode $barcode berhasil di-scan dan disimpan",
                    Resi = barcode,
                    created = createdTime,
                    Keterangan = validationResult.actualCourierName ?: scannerType,
                    nokarung = selectedKarung,
                    schedule = validationResult.actualSchedule ?: "ontime",
                    timestamp = resultTimestamp
                )
                Log.d(TAG, "✅ ScanResult updated with status=success, timestamp=$resultTimestamp, Resi=$barcode")
                
                Log.d(TAG, "⚡ [LOCAL-FIRST] Cache updated instantly, Supabase sync in background...")
                // Note: Network sync terjadi di background (non-blocking)
                // Jika network gagal, background sync akan retry otomatis (tidak perlu handle di sini)
                
                // ❌ TIDAK perlu update flag (trigger di Supabase sudah handle)
                // Trigger di Supabase akan otomatis update flag menjadi "YES" saat resi di-save
                // Hapus dari cache expedisi karena flag akan menjadi "YES" (tidak perlu di cache)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // Hapus dari cache expedisi (karena flag akan menjadi "YES" oleh trigger)
                        BarcodeCacheManager.removeExpedisiRecord(barcode)
                        Log.d(TAG, "✅ Removed from expedisi cache (flag updated by trigger): $barcode")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error removing from expedisi cache: ${e.message}", e)
                    }
                }
                
                // **OPTIMASI**: Jangan reload history (tidak perlu, sudah local-first update)
                // Data sudah di-update di cache oleh Repository, UI sudah di-update di atas
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing barcode: ${e.message}")
            _scanResult.value = ScanResult(
                status = "error",
                message = "Gagal menyimpan barcode: ${e.message}",
                Resi = barcode,
                timestamp = System.currentTimeMillis()
            )
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
     * Get scan history dari EnhancedRepository
     */
    fun getScanHistory(): List<ScanHistoryItem> {
        return _scanHistoryFromDB.value ?: emptyList()
    }
    
    /**
     * Delete scan history item (hapus dari cache dan Supabase)
     */
    fun deleteScanHistoryItem(resi: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🗑️ Deleting scan history item: $resi")
                
                // Hapus via EnhancedRepository (cache + Supabase)
                val success = enhancedRepository.deleteBarcodeResiRecord(resi)
                
                if (success) {
                    // Reload history setelah delete
                    loadScanHistoryFromDatabase()
                    loadProcessedBarcodesFromDatabase()
                    
                    // Update scan count
                    val processedCount = BarcodeCacheManager.getProcessedResiSet().size
                    _scanCount.value = processedCount
                    _processedBarcodesFromDB.value = BarcodeCacheManager.getProcessedResiSet()
                    
                    Log.d(TAG, "✅ Scan history item deleted successfully: $resi")
                } else {
                    Log.e(TAG, "❌ Failed to delete scan history item: $resi")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting scan history item: ${e.message}", e)
            }
        }
    }
    
    /**
     * Clear scan history dari Room Database
     */
    fun clearScanHistory() {
        Log.d(TAG, "📱 Clearing scan history via repository")
        viewModelScope.launch {
            try {
                // Clear via EnhancedRepository
                // Clear cache
                BarcodeCacheManager.clearCache()
                
                // Clear Supabase data (optional - biasanya data tetap di Supabase)
                enhancedRepository.clearAllBarcodeData()
                
                // Reload data dari database
                loadScanHistoryFromDatabase()
                loadProcessedBarcodesFromDatabase()
                
                // Update UI
                _scanCount.value = 0
                _lastScanTime.value = "Belum ada scan"
                _processedBarcodesFromDB.value = emptySet()
                _scanHistoryFromDB.value = emptyList()
                
                Log.d(TAG, "✅ Scan history cleared (cache + repository)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing scan history: ${e.message}")
            }
        }
    }
    
    /**
     * Get scan statistics dari Cache Manager (HashSet - fast lookup)
     */
    fun getScanStatistics(): Map<String, Any> {
        val currentScanHistory = _scanHistoryFromDB.value ?: emptyList()
        
        // Gunakan HashSet dari cache manager untuk unique scans (fast O(1))
        val processedResiSet = BarcodeCacheManager.getProcessedResiSet()
        val uniqueScans = processedResiSet.size
        val totalScans = currentScanHistory.size
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
            if (expedition == null || karung == null || 
                expedition.isEmpty() || karung.isEmpty() ||
                karung == "Pilih Karung" || expedition == "Pilih Expedisi") {
                Log.d(TAG, "📊 No expedition or karung selected (or hint text), returning 0")
                return 0
            }
            
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            
            Log.d(TAG, "📊 Total resi records: ${resiRecords.size}")
            Log.d(TAG, "📊 Sample resi records:")
            resiRecords.take(3).forEach { record ->
                Log.d(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}'")
            }
            
            // Tanggal hari ini (UTC)
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            
            // **DEBUG ENHANCED**: Log date info untuk debugging
            Log.d(TAG, "📅 Date filtering info:")
            Log.d(TAG, "  - Today (UTC): $todayDate")
            
            // **DEBUG**: Cek sample records dengan detail date
            resiRecords.take(5).forEach { record ->
                val createdDateStr = record.created?.substring(0, minOf(10, record.created.length)) ?: "NULL"
                Log.d(TAG, "  - Sample: Resi='${record.Resi}', Keterangan='${record.Keterangan}', nokarung='${record.nokarung}', created_date='$createdDateStr'")
            }
            
            // Filter: hanya created = hari ini (UTC), Keterangan = expedisi, nokarung = karung
            val filteredRecords = resiRecords.filter { rec ->
                val dateOk = try {
                    val created = rec.created
                    if (created.isNullOrBlank()) {
                        false
                    } else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        val recordDate = java.time.LocalDate.parse(datePart)
                        recordDate == todayDate
                    }
                } catch (e: Exception) { 
                    Log.w(TAG, "⚠️ Error parsing date for resi ${rec.Resi}: ${rec.created}, error: ${e.message}")
                    false 
                }
                dateOk && rec.Keterangan == expedition && rec.nokarung == karung
            }
            
            Log.d(TAG, "📊 Filtering criteria:")
            Log.d(TAG, "  - Expedition (Keterangan): '$expedition'")
            Log.d(TAG, "  - Karung (nokarung): '$karung'")
            Log.d(TAG, "📊 Filtered records for expedition '$expedition' and karung '$karung': ${filteredRecords.size}")
            
            // **DEBUG ENHANCED**: Cek setiap step filtering dengan detail
            val todayResi = resiRecords.filter { rec ->
                try {
                    val created = rec.created
                    if (created.isNullOrBlank()) {
                        false
                    } else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        val recordDate = java.time.LocalDate.parse(datePart)
                        recordDate == todayDate
                    }
                } catch (e: Exception) { 
                    false 
                }
            }
            
            Log.d(TAG, "📊 Debug filtering (step by step):")
            Log.d(TAG, "  - Total records: ${resiRecords.size}")
            Log.d(TAG, "  - Records created today (UTC): ${todayResi.size}")
            
            val expeditionMatches = todayResi.filter { it.Keterangan == expedition }
            val karungMatches = todayResi.filter { it.nokarung == karung }
            
            // **DEBUG**: Cek sample dari filtered untuk melihat kenapa tidak match
            if (expeditionMatches.isEmpty() && todayResi.isNotEmpty()) {
                Log.w(TAG, "⚠️ No expedition match. Sample today records:")
                todayResi.take(3).forEach { rec ->
                    Log.w(TAG, "    - Resi='${rec.Resi}', Keterangan='${rec.Keterangan}' (looking for '$expedition')")
                }
            }
            
            if (karungMatches.isEmpty() && expeditionMatches.isNotEmpty()) {
                Log.w(TAG, "⚠️ No karung match. Sample expedition records:")
                expeditionMatches.take(3).forEach { rec ->
                    Log.w(TAG, "    - Resi='${rec.Resi}', nokarung='${rec.nokarung}' (looking for '$karung')")
                }
            }
            
            Log.d(TAG, "  - Records with Keterangan='$expedition' (today only): ${expeditionMatches.size}")
            Log.d(TAG, "  - Records with nokarung='$karung' (today only): ${karungMatches.size}")
            Log.d(TAG, "  - Records with both conditions (today only): ${filteredRecords.size}")
            
            if (filteredRecords.isEmpty()) {
                Log.w(TAG, "⚠️ No records found matching criteria")
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
     * Fetch expedition total hari ini langsung dari Supabase (flag=NO, created=today)
     */
    private suspend fun fetchExpeditionTotalTodayFromSupabase(expedition: String, todayDate: java.time.LocalDate): Int {
        return try {
            val todayDateStr = todayDate.toString() // Format: YYYY-MM-DD
            val encodedExpedition = java.net.URLEncoder.encode(expedition, "UTF-8")
            
            var total = 0
            var offset = 0
            val limit = 1000
            var hasMore = true
            
            // Gunakan config yang sama seperti di tempat lain
            val supabaseUrl = com.example.cekpicklist.config.BarcodeSupabaseConfig.SUPABASE_URL
            val supabaseKey = com.example.cekpicklist.config.BarcodeSupabaseConfig.SUPABASE_ANON_KEY
            
            while (hasMore) {
                // Filter: couriername=expedition, created>=today (tanpa membatasi flag)
                // Kita tetap filter ulang created==today di aplikasi
                val url = java.net.URL("$supabaseUrl/rest/v1/tbl_expedisi?couriername=eq.$encodedExpedition&created=gte.$todayDateStr&select=resino,created&order=created.desc&limit=$limit&offset=$offset")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val response = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream)).use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)
                    
                    // Filter lagi untuk created==today (untuk memastikan hanya hari ini)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val created = obj.optString("created", "")
                        if (created.isNotEmpty()) {
                            val datePart = if (created.length >= 10) created.substring(0, 10) else created
                            try {
                                val recordDate = java.time.LocalDate.parse(datePart)
                                if (recordDate == todayDate) {
                                    total++
                                }
                            } catch (e: Exception) {
                                // Skip invalid date
                            }
                        }
                    }
                    
                    hasMore = jsonArray.length() == limit
                    offset += limit
                } else {
                    hasMore = false
                }
                connection.disconnect()
            }
            
            Log.d(TAG, "✅ Total expedition today from Supabase (ALL flags, created=today): $total")
            total
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching expedition total from Supabase: ${e.message}", e)
            0
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
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            
            Log.d(TAG, "📊 Getting summary for expedition: '$expedition'")
            Log.d(TAG, "  - Today (UTC): $todayDate")
            
            // **TOTAL**: Query Supabase untuk ALL flags (NO + YES) - Total tetap konstan
            val total = try {
                val supabaseTotal = fetchExpeditionTotalTodayFromSupabase(expedition, todayDate)
                Log.d(TAG, "  - TOTAL from Supabase (ALL flags, today): $supabaseTotal")
                supabaseTotal
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error fetching total from Supabase: ${e.message}", e)
                0
            }
            
            // **SCAN**: dari cache tbl_resi hari ini, filter schedule="ontime" only
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            val scanned = resiRecords.count { rec ->
                val dateOk = try {
                    val created = rec.created
                    if (created.isNullOrBlank()) false else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        java.time.LocalDate.parse(datePart) == todayDate
                    }
                } catch (_: Exception) { false }
                // **FILTER schedule=ontime**: Hanya hitung scan yang ontime
                val isOntime = rec.schedule?.lowercase() == "ontime"
                dateOk && rec.Keterangan == expedition && isOntime
            }
            
            Log.d(TAG, "  - SCANNED from cache (schedule=ontime, today): $scanned")
            
            // **SISA**: Count dari cache tbl_expedisi (flag=NO) sesuai expedisi
            val expedisiCache = BarcodeCacheManager.getAllExpedisiRecords()
            val remaining = expedisiCache.count { rec ->
                val dateOk = try {
                    val created = rec.created
                    if (created.isNullOrBlank()) {
                        true // Keep jika tidak ada created date
                    } else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        val recordDate = java.time.LocalDate.parse(datePart)
                        recordDate == todayDate
                    }
                } catch (e: Exception) { 
                    true // Keep jika error parsing
                }
                // Filter hanya expedition yang dipilih
                dateOk && rec.couriername == expedition
            }
            
            Log.d(TAG, "  - SISA from cache (flag=NO, today): $remaining")
            
            Log.d(TAG, "📊 ========== Summary Calculation FINAL ==========")
            Log.d(TAG, "📊 Expedition: '$expedition'")
            Log.d(TAG, "📊   - TOTAL (Supabase ALL flags, today): $total")
            Log.d(TAG, "📊   - SCANNED (cache tbl_resi, schedule=ontime, today): $scanned")
            Log.d(TAG, "📊   - SISA (cache tbl_expedisi, flag=NO, today): $remaining")
            Log.d(TAG, "📊 Logic:")
            Log.d(TAG, "📊   - Total = Query Supabase ALL flags (tetap konstan)")
            Log.d(TAG, "📊   - Scan = Filter schedule ontime only")
            Log.d(TAG, "📊   - Sisa = Count flag=NO (belum di-proses)")
            Log.d(TAG, "📊 ================================================")
            
            val summary = mapOf(
                "total" to total,
                "scanned" to scanned,
                "remaining" to remaining
            )
            
            // **VALIDASI**: Log final values untuk memastikan benar
            Log.d(TAG, "✅ Returning summary: total=${summary["total"]}, scanned=${summary["scanned"]}, remaining=${summary["remaining"]}")
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
        
        // Gunakan HashSet dari cache manager (fast lookup)
        val processedResiSet = BarcodeCacheManager.getProcessedResiSet()
        
        export.appendLine("=== RIWAYAT SCAN BARCODE (Cached Data) ===")
        export.appendLine("Tanggal Export: ${dateFormat.format(Date())}")
        export.appendLine("Total Scan: ${currentScanHistory.size}")
        export.appendLine("Unique Scan: ${processedResiSet.size}")
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
     * Hitung SISA hari ini = jumlah resino HARI INI dengan flag = "NO" (dari Room).
     */
    suspend fun getTodayUniqueExpedisiCount(): Int {
        return try {
            val today = java.time.LocalDate.now()
            val expedisi = BarcodeCacheManager.getAllExpedisiRecords()

            val setFromExpedisi = expedisi.asSequence()
                .filter { rec ->
                    // Hanya flag NO
                    if (!rec.flag.equals("NO", ignoreCase = true)) return@filter false

                    // Tentukan tanggal referensi (pakai datetrans, fallback ke created)
                    val dateStr = when {
                        !rec.datetrans.isNullOrBlank() -> rec.datetrans
                        !rec.created.isNullOrBlank() -> rec.created
                        else -> null
                    } ?: return@filter false

                    try {
                        val part = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
                        java.time.LocalDate.parse(part) == today
                    } catch (_: Exception) {
                        false
                    }
                }
                .map { it.resino.trim().uppercase() }
                .toSet()

            setFromExpedisi.size
        } catch (_: Exception) { 0 }
    }

    /**
     * Hitung total SCAN HARI INI dari cache tbl_resi
     * Hanya menghitung scan dengan schedule = "ontime" (kecualikan "late" dan "batal")
     */
    suspend fun getTodayResiScanCount(): Int {
        return try {
            val fromRemote = enhancedRepository.getTodayResiScanCountSupabase()
            if (fromRemote > 0) return fromRemote
            // Fallback ke cache jika offline/gagal
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            resiRecords.count { rec ->
                try {
                    // Cek tanggal hari ini
                    val created = rec.created
                    val isToday = if (created.isNullOrBlank()) false else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        java.time.LocalDate.parse(datePart) == todayDate
                    }
                    // Hanya hitung schedule = "ontime" atau "batal", kecualikan "late"
                    val schedule = rec.schedule?.lowercase() ?: ""
                    isToday && (schedule == "ontime" || schedule == "batal")
                } catch (_: Exception) { false }
            }
        } catch (_: Exception) { 0 }
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
     * **OPTIMASI**: Lazy load - return cached data dulu, refresh di background
     */
    suspend fun loadExpedisiData(): List<String> {
        return try {
            // **OPTIMASI**: Ambil dari cache dulu (INSTANT, non-blocking)
            val cachedExpedisi = BarcodeCacheManager.getCourierNamesCache()
            val cachedExpedisiFromRecords = BarcodeCacheManager.getUniqueCourierNamesFromCache()
            
            // Gunakan cache yang tersedia (prioritas: courier cache > expedisi cache)
            val fastResult = when {
                cachedExpedisi.isNotEmpty() && BarcodeCacheManager.isCouriersCacheValid() -> {
                    Log.d(TAG, "⚡ Loaded ${cachedExpedisi.size} expedisi from courier cache (INSTANT)")
                    cachedExpedisi
                }
                cachedExpedisiFromRecords.isNotEmpty() -> {
                    Log.d(TAG, "⚡ Loaded ${cachedExpedisiFromRecords.size} expedisi from expedisi cache (INSTANT)")
                    cachedExpedisiFromRecords
                }
                else -> {
                    Log.d(TAG, "📋 No cache available, loading from repository...")
                    // Cache kosong, harus load dari repository (SLOW PATH)
                    enhancedRepository.getUniqueCourierNames()
                }
            }
            
            // **ROOM-ONLY**: Tidak ada background refresh, data hanya dari Room
            // User harus melakukan sync manual untuk update data dari Supabase
            
            Log.d(TAG, "📋 Loaded ${fastResult.size} expedisi (Local-First)")
            fastResult
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading expedisi data: ${e.message}")
            // Fallback ke cache jika ada
            val fallback = BarcodeCacheManager.getUniqueCourierNamesFromCache()
            if (fallback.isNotEmpty()) {
                Log.d(TAG, "⚠️ Using fallback expedisi cache: ${fallback.size}")
                return fallback
            }
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
            emptyList()
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
     * **HANYA mengambil data hari ini saja**
     */
    suspend fun getDefaultKarungForExpedition(expedition: String): String {
        return try {
            Log.d(TAG, "🔍 Getting default karung for expedition: '$expedition' (today only)")
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            Log.d(TAG, "📊 Total resi records: ${resiRecords.size}")
            
            // Tanggal hari ini (UTC)
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            
            // Filter: hanya created = hari ini (UTC), Keterangan = expedisi
            val expeditionKarungs = resiRecords
                .filter { rec ->
                    val dateOk = try {
                        val created = rec.created
                        if (created.isNullOrBlank()) false else {
                            val datePart = if (created.length >= 10) created.substring(0, 10) else created
                            java.time.LocalDate.parse(datePart) == todayDate
                        }
                    } catch (e: Exception) { false }
                    dateOk && rec.Keterangan == expedition
                }
                .mapNotNull { it.nokarung?.toIntOrNull() }
            
            Log.d(TAG, "📦 Expedition '$expedition' karungs (today only): $expeditionKarungs")
            
            val highestKarung = if (expeditionKarungs.isNotEmpty()) expeditionKarungs.maxOrNull() ?: 0 else 0
            
            // Mengikuti logika web app: if (highestKarung > 0) setSelectedKarung(highestKarung.toString()) else setSelectedKarung("1")
            val defaultKarung = if (highestKarung > 0) highestKarung.toString() else "1"
            
            Log.d(TAG, "📦 Default karung for expedition '$expedition' (today): $defaultKarung (highest: $highestKarung, karungs: $expeditionKarungs)")
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
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val expeditionRecords = resiRecords.filter { rec ->
                val dateOk = try {
                    val created = rec.created
                    if (created.isNullOrBlank()) false else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        java.time.LocalDate.parse(datePart) == todayDate
                    }
                } catch (_: Exception) { false }
                dateOk && rec.Keterangan == expedition
            }
            
            // Group by karung number and create summary
            val karungGroups = expeditionRecords.groupBy { it.nokarung ?: "0" }
            val summaryData = karungGroups.map { (karung, records) ->
                com.example.cekpicklist.fragment.KarungSummaryModalFragment.KarungSummaryItem(
                    karungNumber = karung,
                    quantity = records.size
                )
            }.sortedBy { it.karungNumber.toIntOrNull() ?: 0 }
            
            Log.d(TAG, "📊 Loaded ${summaryData.size} karung summary items for expedition '$expedition' (today only) from cache")
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
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "📱 BarcodeScannerViewModel cleared")
    }
}
