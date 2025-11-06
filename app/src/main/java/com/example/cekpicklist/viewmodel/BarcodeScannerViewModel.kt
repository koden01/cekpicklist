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
import com.example.cekpicklist.utils.BarcodeNotificationManager
import com.example.cekpicklist.repository.EnhancedRepository
import com.example.cekpicklist.cache.BarcodeCacheManager
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
        private const val MIN_SCAN_INTERVAL_MS = 100L // Minimum 100ms antar scan untuk notifikasi jelas
    }
    
    // Managers
    private val pendingOperationsManager = PendingOperationsManager(application)
    private lateinit var backgroundSyncManager: BackgroundSyncManager
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
    
    /**
     * Initialize cache secara async saat ViewModel dibuat
     * **OPTIMASI**: Gunakan cache yang sudah ada langsung, refresh di background jika expired
     */
    private fun initializeCacheAsync() {
        viewModelScope.launch {
            try {
                // **OPTIMASI**: Load UI data dulu dari cache yang sudah ada (INSTANT)
                loadScanHistoryFromDatabase()
                loadProcessedBarcodesFromDatabase()
                
                // Check cache terlebih dahulu
                val cachedResi = BarcodeCacheManager.getAllResiRecords()
                val cachedExpedisi = BarcodeCacheManager.getAllExpedisiRecords()
                val resiCacheValid = BarcodeCacheManager.isResiCacheValid()
                val expedisiCacheValid = BarcodeCacheManager.isExpedisiCacheValid()
                
                // **OPTIMASI**: Jika cache ada dan valid, langsung gunakan (tidak perlu network call)
                if (cachedResi.isNotEmpty() && cachedExpedisi.isNotEmpty() && resiCacheValid && expedisiCacheValid) {
                    Log.d(TAG, "⚡ Using existing valid cache: ${cachedResi.size} resi, ${cachedExpedisi.size} expedisi (INSTANT)")
                    
                    // Pre-warm courier names cache dari expedisi cache yang sudah ada
                    val courierNames = BarcodeCacheManager.getUniqueCourierNamesFromCache()
                    if (courierNames.isNotEmpty() && !BarcodeCacheManager.isCouriersCacheValid()) {
                        BarcodeCacheManager.setCourierNamesCache(courierNames)
                        Log.d(TAG, "⚡ Pre-warmed courier names from existing expedisi cache: ${courierNames.size} couriers")
                    }
                    
                    // Cache sudah ready, tidak perlu network call
                    return@launch
                }
                
                // Cache kosong atau expired - refresh di background (non-blocking)
                if (cachedResi.isEmpty() || cachedExpedisi.isEmpty() || !resiCacheValid || !expedisiCacheValid) {
                    Log.d(TAG, "📦 Cache empty or expired, refreshing from Supabase (background)...")
                    
                    // Load dari repository (akan mengisi cache) - background, non-blocking
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val resiRecords = enhancedRepository.getBarcodeResiRecords()
                            val expedisiRecords = enhancedRepository.getBarcodeExpedisiRecords()
                            
                            // Pre-warm courier names cache dari expedisi records
                            if (expedisiRecords.isNotEmpty()) {
                                val courierNames = BarcodeCacheManager.getUniqueCourierNamesFromCache()
                                if (courierNames.isNotEmpty()) {
                                    BarcodeCacheManager.setCourierNamesCache(courierNames)
                                    Log.d(TAG, "⚡ Pre-warmed courier names cache: ${courierNames.size} couriers")
                                }
                            }
                            
                            // Reload UI data setelah cache di-update
                            loadScanHistoryFromDatabase()
                            loadProcessedBarcodesFromDatabase()
                            
                            Log.d(TAG, "✅ Cache refreshed: ${resiRecords.size} resi, ${expedisiRecords.size} expedisi")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error refreshing cache: ${e.message}", e)
                        }
                    }
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
                // Load dari cache manager - lebih cepat
                val barcodeResiRecords = BarcodeCacheManager.getAllResiRecords()
                
                // Jika cache kosong, load dari repository (akan mengisi cache)
                if (barcodeResiRecords.isEmpty()) {
                    Log.d(TAG, "📦 Cache empty, loading from repository...")
                    enhancedRepository.getBarcodeResiRecords()
                    enhancedRepository.getBarcodeExpedisiRecords()
                }
                
                // Gunakan data dari cache
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
                // Load dari cache manager (HashSet) - sangat cepat
                val processedResiSet = BarcodeCacheManager.getProcessedResiSet()
                _processedBarcodesFromDB.value = processedResiSet
                Log.d(TAG, "📱 Loaded ${processedResiSet.size} processed barcodes from cache (HashSet)")
                
                // Jika cache kosong, load dari repository (akan mengisi cache)
                if (processedResiSet.isEmpty()) {
                    Log.d(TAG, "📦 Cache empty, loading from repository...")
                val barcodeResiRecords = enhancedRepository.getBarcodeResiRecords()
                    val expedisiRecords = enhancedRepository.getBarcodeExpedisiRecords()
                    
                    // Cache akan diupdate oleh repository
                    val updatedSet = BarcodeCacheManager.getProcessedResiSet()
                    _processedBarcodesFromDB.value = updatedSet
                    Log.d(TAG, "📱 Updated processed barcodes from repository: ${updatedSet.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading processed barcodes: ${e.message}")
                // Fallback to cache if available
                val cachedSet = BarcodeCacheManager.getProcessedResiSet()
                _processedBarcodesFromDB.value = if (cachedSet.isNotEmpty()) cachedSet else emptySet()
            }
        }
    }
    
    /**
     * Initialize background sync manager
     */
    fun initializeBackgroundSync() {
        backgroundSyncManager = BackgroundSyncManager(
            context = getApplication(),
            pendingOperationsManager = pendingOperationsManager,
            enhancedRepository = enhancedRepository
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
                // 2. Cek Duplicate (semua data cache tbl_resi, 4 hari terakhir)
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
                                    else -> "error"
                                },
                                message = validationResult.message ?: "Validasi gagal",
                                Resi = trimmedBarcode,
                                timestamp = System.currentTimeMillis()
                            )
                            isProcessing = false
                            return@launch
                        }
                        ExpedisiValidator.ValidationStatus.OK -> {
                        // **HANYA jika VALIDASI OK**: Data disimpan
                        Log.d(TAG, "✅ Validasi OK, data akan disimpan: $trimmedBarcode")
                            processSuccessfulScan(
                                barcode = trimmedBarcode,
                                scanDuration = scanDuration,
                                scannerType = scannerType,
                                validationResult = validationResult
                            )
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
            } finally {
                isProcessing = false
                // **PERBAIKAN**: Update timestamp setelah scan selesai untuk debounce
                lastScanCompleteTime = System.currentTimeMillis()
                Log.d(TAG, "✅ Scan selesai, ready untuk scan berikutnya (debounce: ${MIN_SCAN_INTERVAL_MS}ms)")
            }
        }
    }
    
    /**
     * Process successful scan dan simpan via EnhancedRepository (Supabase-only cache)
     * **KRITIS**: Hanya menyimpan jika VALIDASI OK
     */
    private fun processSuccessfulScan(
        barcode: String,
        scanDuration: Long,
        scannerType: String,
        validationResult: ExpedisiValidator.ValidationResult
    ) {
        viewModelScope.launch {
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
                    return@launch
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
                    return@launch
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
                
                // **NON-BLOCKING**: Update flag di background (tidak blocking UI)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        updateExpedisiFlag(barcode)
                        Log.d(TAG, "✅ Flag updated in background for: $barcode")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error updating flag in background: ${e.message}", e)
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
            // Total: dari cache expedisi (flag=NO) untuk hari ini
            val todayDate = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val expedisiCache = BarcodeCacheManager.getAllExpedisiRecords()
            
            Log.d(TAG, "📊 Expedisi cache info:")
            Log.d(TAG, "  - Total expedisi records in cache (flag=NO): ${expedisiCache.size}")
            Log.d(TAG, "  - Today (UTC): $todayDate")
            Log.d(TAG, "  - Filtering for expedition: '$expedition'")
            
            // **DEBUG**: Log sample expedisi records untuk debugging
            val expeditionRecords = expedisiCache.filter { it.couriername == expedition }
            Log.d(TAG, "  - Records with couriername='$expedition': ${expeditionRecords.size}")
            
            if (expeditionRecords.isNotEmpty()) {
                Log.d(TAG, "  - Sample expedisi records for '$expedition':")
                expeditionRecords.take(5).forEach { rec ->
                    val createdDateStr = rec.created?.substring(0, minOf(10, rec.created.length)) ?: "NULL"
                    Log.d(TAG, "    - Resino='${rec.resino}', couriername='${rec.couriername}', created='$createdDateStr', flag='${rec.flag}'")
                }
            }
            
            val total = expedisiCache.count { rec ->
                val dateOk = try {
                    val created = rec.created
                    if (created.isNullOrBlank()) {
                        false
                    } else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        val recordDate = java.time.LocalDate.parse(datePart)
                        val isToday = recordDate == todayDate
                        if (!isToday && rec.couriername == expedition) {
                            // Log untuk debugging jika ada data expedisi tapi bukan hari ini
                            Log.d(TAG, "  - Record not today: Resino='${rec.resino}', date='$datePart' (looking for '$todayDate')")
                        }
                        isToday
                    }
                } catch (e: Exception) { 
                    Log.w(TAG, "⚠️ Error parsing date for expedisi ${rec.resino}: ${rec.created}, error: ${e.message}")
                    false 
                }
                dateOk && rec.couriername == expedition
            }
            
            Log.d(TAG, "  - Total records matching (today + expedition='$expedition'): $total")
            
            // Scan: dari cache tbl_resi hari ini dengan Keterangan = expedition
            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            val scanned = resiRecords.count { rec ->
                val dateOk = try {
                    val created = rec.created
                    if (created.isNullOrBlank()) false else {
                        val datePart = if (created.length >= 10) created.substring(0, 10) else created
                        java.time.LocalDate.parse(datePart) == todayDate
                    }
                } catch (_: Exception) { false }
                dateOk && rec.Keterangan == expedition
            }
            
            // **FALLBACK**: Jika cache tidak ada data hari ini, fetch langsung dari Supabase
            // dengan filter flag=NO dan created=today (lebih akurat)
            var finalTotal = total
            if (total == 0) {
                Log.d(TAG, "⚠️ No data in cache for today, fetching directly from Supabase (flag=NO, created=today)...")
                try {
                    // Fetch langsung dari Supabase dengan filter yang tepat
                    val supabaseTotal = fetchExpeditionTotalTodayFromSupabase(expedition, todayDate)
                    Log.d(TAG, "📊 Fetching TOTAL from Supabase (ALL flags, created=today) for expedition '$expedition'")
                    Log.d(TAG, "  - TOTAL (Supabase today, ALL flags): $supabaseTotal")
                    if (supabaseTotal > 0) {
                        Log.w(TAG, "⚠️ Cache mismatch: Supabase has $supabaseTotal records but cache has 0 for today")
                        // Gunakan nilai dari Supabase sebagai fallback
                        finalTotal = supabaseTotal
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error fetching from Supabase: ${e.message}", e)
                }
            }

            // **PERBAIKAN**: Hitung remaining dengan benar (tidak boleh negatif)
            val remaining = (finalTotal - scanned).coerceAtLeast(0)
            
            Log.d(TAG, "📊 ========== Summary Calculation FINAL ==========")
            Log.d(TAG, "📊 Expedition: '$expedition'")
            Log.d(TAG, "📊   - TOTAL resi (couriername='$expedition', today): $finalTotal")
            Log.d(TAG, "📊   - SCANNED resi (Keterangan='$expedition', today): $scanned")
            Log.d(TAG, "📊   - REMAINING (Total - Scanned): $remaining")
            Log.d(TAG, "📊   - Calculation: $finalTotal - $scanned = $remaining")
            Log.d(TAG, "📊 ================================================")
            
            val summary = mapOf(
                "total" to finalTotal,
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
     * Hitung total transaksi hari ini (unique resi) dari cache tbl_expedisi
     */
    suspend fun getTodayUniqueExpedisiCount(): Int {
        return try {
            // Supabase-first untuk akurasi real-time; fallback ke cache union jika gagal
            val fromRemote = enhancedRepository.getTodayUniqueExpedisiCountSupabase()
            if (fromRemote > 0) return fromRemote

            val today = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val expedisi = BarcodeCacheManager.getAllExpedisiRecords()
            val setFromExpedisi = expedisi.asSequence()
                .filter { rec ->
                    val dateStr = when {
                        !rec.datetrans.isNullOrBlank() -> rec.datetrans
                        !rec.created.isNullOrBlank() -> rec.created
                        else -> null
                    }
                    if (dateStr == null) return@filter false
                    try {
                        val part = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
                        java.time.LocalDate.parse(part) == today
                    } catch (_: Exception) { false }
                }
                .map { it.resino.trim().uppercase() }
                .toSet()

            val resiRecords = enhancedRepository.getBarcodeResiRecords()
            val setFromResi = resiRecords.asSequence()
                .filter { rec ->
                    try {
                        val created = rec.created
                        if (created.isNullOrBlank()) false else {
                            val part = if (created.length >= 10) created.substring(0, 10) else created
                            java.time.LocalDate.parse(part) == today
                        }
                    } catch (_: Exception) { false }
                }
                .map { it.Resi.trim().uppercase() }
                .toSet()

            (setFromExpedisi + setFromResi).size
        } catch (_: Exception) { 0 }
    }

    /**
     * Hitung total scan hari ini dari cache tbl_resi
     * Hanya menghitung scan dengan schedule = "ontime" atau "batal" (kecualikan "late")
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
            
            // **BACKGROUND REFRESH**: Refresh di background jika cache expired atau kosong
            if (fastResult.isNotEmpty() && (!BarcodeCacheManager.isCouriersCacheValid() || cachedExpedisi.isEmpty())) {
                Log.d(TAG, "🔄 Background refreshing expedisi data...")
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val refreshed = enhancedRepository.getUniqueCourierNames()
                        Log.d(TAG, "✅ Background refresh completed: ${refreshed.size} expedisi")
                        // UI akan auto-update jika LiveData dipakai
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Background refresh failed: ${e.message}")
                    }
                }
            }
            
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
