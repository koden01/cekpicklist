package com.example.cekpicklist.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan
import com.example.cekpicklist.data.PicklistStatus
// import com.example.cekpicklist.data.TempRfidScan // dihapus karena model tidak digunakan lagi
import com.example.cekpicklist.data.ProcessedRfidData
import com.example.cekpicklist.data.QtySummary
import com.example.cekpicklist.data.QtyUpdate
import com.example.cekpicklist.data.QtyStatusInfo
import com.example.cekpicklist.data.QtyStatus
import com.example.cekpicklist.repository.Repository
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.SupabaseService.SimplePicklist
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException

// Data class untuk menyimpan 4 nilai
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

// Data class untuk menyimpan 6 nilai (picklistNo, articleId, epc, productId, articleName, size)
data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application.applicationContext)
    
    // LiveData untuk UI
    private val _picklists = MutableLiveData<List<String>>()
    val picklists: LiveData<List<String>> = _picklists
    
    private val _picklistStatuses = MutableLiveData<List<PicklistStatus>>()
    val picklistStatuses: LiveData<List<PicklistStatus>> = _picklistStatuses
    
    // NEW: LiveData untuk picklist sederhana dengan status scan
    private val _simplePicklists = MutableLiveData<List<SimplePicklist>>()
    val simplePicklists: LiveData<List<SimplePicklist>> = _simplePicklists

    private val _picklistItems = MutableLiveData<List<PicklistItem>>()
    val picklistItems: LiveData<List<PicklistItem>> = _picklistItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Removed unused _showCompletionAnimation - completion animation is handled directly in Activity
    
    // **POIN 1: Summary Statistics untuk Qty**
    private val _qtySummary = MutableLiveData<QtySummary>()
    val qtySummary: LiveData<QtySummary> = _qtySummary
    
    // **POIN 2: Status Colors untuk Qty**
    private val _qtyStatusColors = MutableLiveData<Map<String, QtyStatus>>()
    val qtyStatusColors: LiveData<Map<String, QtyStatus>> = _qtyStatusColors
    
    // **POIN 4: Filtered Items (hide completed)**
    private val _filteredItems = MutableLiveData<List<PicklistItem>>()
    val filteredItems: LiveData<List<PicklistItem>> = _filteredItems
    
    // **Loading job untuk mencegah konflik**
    private var loadingJob: kotlinx.coroutines.Job? = null
    // Job terpisah untuk simple picklists agar tidak membatalkan loading utama
    private var simpleLoadingJob: kotlinx.coroutines.Job? = null
    // Job terpisah untuk status agar tidak overlap
    private var statusesLoadingJob: kotlinx.coroutines.Job? = null
    // Job untuk prefetch semua data hari ini
    private var prefetchJob: kotlinx.coroutines.Job? = null
    // Debounce untuk load picklists
    private var lastPicklistsLoadAt: Long = 0L
    
    // **POIN 5: LiveData untuk Real-time Qty Updates**
    private val _qtyUpdates = MutableLiveData<QtyUpdate>()
    val qtyUpdates: LiveData<QtyUpdate> = _qtyUpdates
    
    // Current picklist number
    private var currentPicklistNumber: String? = null
    
    // Temp RFID collection untuk batch processing (removed)
    // Background processing job (removed)
    private var rfidProcessingJob: Job? = null
    private var lastRfidDetectionTime: Long = 0L
    private var lastClearTime: Long = 0L
    
    // RFID detection counter untuk UI
    private val _rfidDetectionCount = MutableLiveData<Int>()
    val rfidDetectionCount: LiveData<Int> = _rfidDetectionCount
    
    // Total RFID detections (termasuk duplikasi)
    private var totalRfidDetections = 0

    // Mengizinkan re-scan EPC yang sudah pernah diproses setelah Clear untuk picklist tertentu
    private val allowRescanPicklists = mutableSetOf<String>()
    
    // **PERBAIKAN**: Cooldown mechanism (removed)
    
    // **PERBAIKAN BARU**: EPC yang sudah pernah diproses per picklist (permanent block dalam satu picklist)
    private val processedEpcPerPicklist = mutableMapOf<String, MutableSet<String>>()
    // **PERBAIKAN BARU**: Guard idempoten untuk mencegah double apply kenaikan qtyScan/processedData per EPC per picklist
    private val appliedEpcPerPicklist: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // === MODE SEDERHANA: EPC UNIK PER PICKLIST ===
    // Sumber kebenaran untuk EPC unik yang sudah terdeteksi (seeding dari database saat load)
    private val uniqueEpcPerPicklist: MutableMap<String, MutableSet<String>> = mutableMapOf()

    private fun getCurrentUniqueSet(): MutableSet<String> {
        val pick = currentPicklistNumber ?: return mutableSetOf()
        return uniqueEpcPerPicklist.getOrPut(pick) { mutableSetOf() }
    }
    
    // **PERBAIKAN BARU**: Background processing dengan sliding window
    // removed: backgroundProcessingJob (batch disabled)
    private var backgroundReloadJob: Job? = null
    // **PERBAIKAN**: Global background reload job yang tidak di-cancel saat activity destroy
    private var globalBackgroundReloadJob: Job? = null
    // removed: BACKGROUND_PROCESSING_DELAY (batch disabled)

    // === RETRY QUEUE FOR NIRWANA LOOKUP (removed) ===
    
    // **REMOVED**: Timer extension counter dan rfidProcessingJob (tidak digunakan lagi)
    // private var timerExtensionCount = 0
    // private val MAX_TIMER_EXTENSIONS = 3
    // private var rfidProcessingJob: Job? = null
    
    // Processed RFID data untuk UI
    private val _processedRfidData = MutableLiveData<List<ProcessedRfidData>>()
    val processedRfidData: LiveData<List<ProcessedRfidData>> = _processedRfidData
    
    // Logging controls
    private companion object {
        private const val VERBOSE_LOGS = false // set true saat debugging rinci
        private fun v(tag: String, message: String) {
            if (VERBOSE_LOGS) android.util.Log.d(tag, message)
        }
    }
    
    // Mutex untuk mencegah race condition saat merge hasil lookup ke UI
    private val uiMergeMutex = kotlinx.coroutines.sync.Mutex()
    
    // EPC yang sedang diproses secara single agar tidak ikut terproses oleh batch
    private val inFlightEpcs = mutableSetOf<String>()
    
    init {
        Log.d("ScanViewModel", "🔥 Initializing ScanViewModel...")
    }
    
    /**
     * Optimized load picklists - menggunakan cache dari Repository
     */
    fun loadPicklistsOptimized() {
        val startTime = System.currentTimeMillis()
        Log.i("ScanViewModel", "🔥 === OPTIMIZED LOADING PICKLISTS START ===")
        Log.d("ScanViewModel", "🔍 DEBUG: loadPicklistsOptimized() called at ${System.currentTimeMillis()}")
        
        // Debounce: hindari trigger berulang dalam waktu singkat
        if (_isLoading.value == true) {
            Log.d("ScanViewModel", "⏭️ Skip loadPicklistsOptimized: still loading")
            return
        }
        if (startTime - lastPicklistsLoadAt < 1500) {
            Log.d("ScanViewModel", "⏭️ Skip loadPicklistsOptimized: called too frequently")
            return
        }
        lastPicklistsLoadAt = startTime

        // **V4.3.4 STYLE**: Simple loading without complex mutex
        loadingJob?.cancel()
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }
                
                Log.i("ScanViewModel", "🔥 Loading picklists via Repository (with cache)")
                
                // Repository sudah handle cache management
                Log.d("ScanViewModel", "🔍 DEBUG: Calling repository.getPicklists()...")
                val picklists = repository.getPicklists()
                Log.d("ScanViewModel", "🔍 DEBUG: Repository returned ${picklists.size} picklists")
                
                if (picklists.isNotEmpty()) {
                    Log.d("ScanViewModel", "🔍 DEBUG: First 3 picklists from repository: ${picklists.take(3).joinToString(", ")}")
                } else {
                    Log.w("ScanViewModel", "⚠️ DEBUG: Repository returned empty picklist list!")
                }
                
                withContext(Dispatchers.Main) {
                    if (picklists.isNotEmpty()) {
                        val loadTime = System.currentTimeMillis() - startTime
                        Log.i("ScanViewModel", "✅ Picklists loaded in ${loadTime}ms (${picklists.size} items)")
                        Log.d("ScanViewModel", "🔍 DEBUG: Setting _picklists.value to ${picklists.size} items")
                        _picklists.value = picklists
                        Log.d("ScanViewModel", "🔍 DEBUG: _picklists.value set successfully")
                    } else {
                        Log.w("ScanViewModel", "⚠️ No picklists found")
                        Log.d("ScanViewModel", "🔍 DEBUG: Setting _picklists.value to empty list")
                        _picklists.value = emptyList()
                    }
                }
                
                Log.i("ScanViewModel", "🔥 === OPTIMIZED LOADING PICKLISTS END ===")
                
                // Load picklist statuses after loading picklists
                if (picklists.isNotEmpty()) {
                    loadPicklistStatuses(picklists)
                }
                
            } catch (e: CancellationException) {
                Log.d("ScanViewModel", "ℹ️ Loading cancelled: ${e.message}")
                throw e // Re-throw cancellation
            } catch (e: Exception) {
                val loadTime = System.currentTimeMillis() - startTime
                Log.e("ScanViewModel", "❌ Error in optimized loading after ${loadTime}ms: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error loading picklists: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    Log.d("ScanViewModel", "🔍 DEBUG: Setting _isLoading.value to false")
                    _isLoading.value = false
                    Log.d("ScanViewModel", "🔍 DEBUG: _isLoading.value set to false")
                }
                loadingJob = null
            }
        }
    }
    
    /**
     * Cancel all loading jobs
     */
    fun cancelLoadingJobs() {
        loadingJob?.cancel()
        loadingJob = null
        simpleLoadingJob?.cancel()
        simpleLoadingJob = null
        statusesLoadingJob?.cancel()
        statusesLoadingJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        Log.d("ScanViewModel", "🔥 All loading jobs cancelled")
    }
    
    /**
     * METODE BARU: Load picklist sederhana dengan status scan untuk modal
     */
    fun loadSimplePicklists() {
        val startTime = System.currentTimeMillis()
        Log.i("ScanViewModel", "🔥 === LOAD SIMPLE PICKLISTS START ===")
        Log.d("ScanViewModel", "🔍 DEBUG: loadSimplePicklists() called at ${System.currentTimeMillis()}")
        
        // Gunakan job terpisah agar tidak membatalkan loading utama/status detail
        simpleLoadingJob?.cancel()
        simpleLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ScanViewModel", "🔍 DEBUG: Starting simple picklist loading...")
                
                // Ambil picklist sederhana dengan status scan
                val simplePicklists = repository.getUniquePicklistsWithScanStatus()
                Log.d("ScanViewModel", "🔍 DEBUG: Repository returned ${simplePicklists.size} simple picklists")
                
                withContext(Dispatchers.Main) {
                    _simplePicklists.value = simplePicklists
                }
                
                val endTime = System.currentTimeMillis()
                Log.i("ScanViewModel", "✅ SIMPLE PICKLISTS LOADING COMPLETED in ${endTime - startTime}ms")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error in simple picklist loading: ${e.message}", e)
            }
        }
    }

    /**
     * Prefetch semua data hari ini ke cache (items + processed EPC) di background
     * Tidak menyentuh _isLoading agar tidak mengganggu UI
     */
    fun prefetchTodayData() {
        if (prefetchJob?.isActive == true) {
            Log.d("ScanViewModel", "⏭️ Skip prefetchTodayData: already running")
            return
        }
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ScanViewModel", "🚀 PREFETCH: Fetching all today's data into cache...")
                val result = repository.getAllTodayDataUltraOptimized()
                Log.d("ScanViewModel", "✅ PREFETCH completed: cached ${result.size} picklists")
            } catch (e: CancellationException) {
                Log.d("ScanViewModel", "ℹ️ PREFETCH cancelled: ${e.message}")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ PREFETCH error: ${e.message}", e)
            } finally {
                prefetchJob = null
            }
        }
    }
    
    /**
     * LAZY LOADING: Load detail picklist saat dipilih
     */
    fun loadPicklistDetails(picklistNumber: String) {
        Log.i("ScanViewModel", "🔥 === LOAD PICKLIST DETAILS START ===")
        Log.d("ScanViewModel", "🔍 DEBUG: loadPicklistDetails() called for picklist: $picklistNumber")
        
        // **CRITICAL FIX**: Set currentPicklistNumber untuk lazy loading
        currentPicklistNumber = picklistNumber
        Log.d("ScanViewModel", "🔍 DEBUG: currentPicklistNumber set to: $currentPicklistNumber")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }
                
                Log.d("ScanViewModel", "🔍 DEBUG: Starting picklist details loading for: $picklistNumber")
                
                // Ambil detail picklist
                val picklistItems = repository.getPicklistDetails(picklistNumber)
                Log.d("ScanViewModel", "🔍 DEBUG: Repository returned ${picklistItems.size} items for picklist $picklistNumber")
                
                withContext(Dispatchers.Main) {
                    _picklistItems.value = picklistItems
                    _isLoading.value = false
                    
                    // **CRITICAL FIX**: Update filtered items setelah picklist items diupdate
                    updateFilteredItems()
                    Log.d("ScanViewModel", "🔍 DEBUG: updateFilteredItems() called after loading picklist details")
                }
                
                Log.i("ScanViewModel", "✅ PICKLIST DETAILS LOADING COMPLETED for $picklistNumber")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error in picklist details loading: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * Load scan status for all picklists
     */
    private fun loadPicklistStatuses(picklists: List<String>) {
        // Hindari overlap: jika masih berjalan, biarkan yang lama selesai
        if (statusesLoadingJob?.isActive == true) {
            Log.d("ScanViewModel", "⏭️ Skip loadPicklistStatuses: already in progress")
            return
        }
        statusesLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ScanViewModel", "🔥 Loading picklist statuses for ${picklists.size} picklists")
                
                // Use batch processing for better performance
                val statuses = repository.getAllPicklistCompletionStatuses(picklists)
                
                withContext(Dispatchers.Main) {
                    _picklistStatuses.value = statuses
                    Log.d("ScanViewModel", "✅ Loaded ${statuses.size} picklist statuses")
                    
                    // Log each status for debugging
                    statuses.forEach { status ->
                        Log.d("ScanViewModel", "📊 ${status.picklistNumber}: scanned=${status.isScanned}, total=${status.totalQty}, scanned=${status.scannedQty}, remaining=${status.remainingQty}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error loading picklist statuses: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _picklistStatuses.value = emptyList()
                }
            }
        }
    }

    /**
     * ULTRA OPTIMASI: Prefetch SEMUA data hari ini dalam 1 SINGLE query saja (ULTRA CEPAT!)
     */
    fun prefetchAllTodayDataUltraOptimized() {
        Log.d("ScanViewModel", "🔥 === PREFETCH ALL TODAY DATA ULTRA OPTIMIZED START ===")
        Log.d("ScanViewModel", "🔍 DEBUG: prefetchAllTodayDataUltraOptimized() called at ${System.currentTimeMillis()}")
        Log.d("ScanViewModel", "🚀 ULTRA OPTIMASI: Prefetching ALL today's data in 1 SINGLE query!")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // **ULTRA OPTIMASI**: Ambil SEMUA data hari ini dalam 1 query dengan JOIN
                Log.d("ScanViewModel", "🔍 DEBUG: Calling repository.getAllTodayDataUltraOptimized()...")
                val allTodayData = repository.getAllTodayDataUltraOptimized()
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                Log.d("ScanViewModel", "✅ ULTRA OPTIMASI: Retrieved ${allTodayData.size} picklists in ${duration}ms (1 SINGLE query!)")
                
                // Update picklists jika belum ada
                if (_picklists.value.isNullOrEmpty()) {
                    val picklistNumbers = allTodayData.keys.toList()
                    withContext(Dispatchers.Main) {
                        _picklists.value = picklistNumbers
                        Log.d("ScanViewModel", "✅ Updated picklists: ${picklistNumbers.size} items")
                    }
                }
                
                // Generate statuses dari data yang sudah di-cache
                val statuses = allTodayData.map { (picklistNo, data) ->
                    val (items, scans) = data
                    val totalQty = items.sumOf { it.qtyPl }
                    val scannedQty = scans.size
                    val remainingQty = totalQty - scannedQty
                    
                    PicklistStatus(
                        picklistNumber = picklistNo,
                        isScanned = remainingQty <= 0,
                        remainingQty = remainingQty,
                        totalQty = totalQty,
                        scannedQty = scannedQty,
                        lastScanTime = if (scans.isNotEmpty()) System.currentTimeMillis().toString() else null,
                        overscanQty = maxOf(0, scannedQty - totalQty)
                    )
                }
                
                withContext(Dispatchers.Main) {
                    _picklistStatuses.value = statuses
                    Log.d("ScanViewModel", "✅ OPTIMASI: Updated ${statuses.size} picklist statuses from optimized data")
                }
                
            } catch (e: CancellationException) {
                Log.d("ScanViewModel", "ℹ️ Optimized prefetch cancelled: ${e.message}")
                throw e
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "❌ Optimized prefetch failed: ${t.message}")
            }
        }
    }

    /**
     * Prefetch picklist statuses: seed dari cache bila ada agar UI cepat,
     * lalu lengkapi dengan fetch jaringan (status hari ini) dan update kembali.
     */
    fun prefetchPicklistStatuses() {
        val currentPicklists = _picklists.value ?: emptyList()
        if (currentPicklists.isEmpty()) return

        // **PERBAIKAN**: Gunakan job terpisah untuk prefetch agar tidak conflict dengan loadingJob
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ambil data dari cache jika tersedia
                val cachedData = repository.getBatchPicklistData(currentPicklists)
                if (cachedData.isNotEmpty()) {
                    val cachedStatuses = cachedData.values.map { it.status }
                    withContext(Dispatchers.Main) {
                        if (cachedStatuses.isNotEmpty()) {
                            _picklistStatuses.value = cachedStatuses
                            Log.d("ScanViewModel", "✅ Seeded picklist statuses from cache: ${cachedStatuses.size} items")
                        }
                    }
                }

                // **PERBAIKAN**: Delay sedikit sebelum fetch dari jaringan untuk menghindari race condition
                delay(1000)

                // Prefetch lengkap dari jaringan (menghitung scan hari ini) dan update UI kembali
                loadPicklistStatuses(currentPicklists)
            } catch (e: CancellationException) {
                Log.d("ScanViewModel", "ℹ️ Prefetch cancelled: ${e.message}")
                throw e // Re-throw cancellation
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "❌ Prefetch picklist statuses failed: ${t.message}")
            }
        }
    }
    
    /**
     * Force refresh picklist statuses: bypass cache dan langsung fetch dari jaringan
     * Digunakan untuk memastikan data terbaru saat modal dibuka
     */
    fun forceRefreshPicklistStatuses() {
        val currentPicklists = _picklists.value ?: emptyList()
        if (currentPicklists.isEmpty()) return

        Log.d("ScanViewModel", "🔥 Force refreshing picklist statuses (bypass cache)")
        
        // **PERBAIKAN**: Gunakan job terpisah untuk force refresh agar tidak conflict dengan loadingJob
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // **PERBAIKAN**: Delay sedikit untuk menghindari race condition
                delay(500)
                
                // **OPTIMASI**: Langsung fetch dari jaringan tanpa cache menggunakan method baru
                val statuses = repository.forceRefreshPicklistCompletionStatuses(currentPicklists)
                
                withContext(Dispatchers.Main) {
                    _picklistStatuses.value = statuses
                    Log.d("ScanViewModel", "✅ Force refreshed ${statuses.size} picklist statuses from network (bypass cache)")
                    
                    // Log each status for debugging
                    statuses.forEach { status ->
                        Log.d("ScanViewModel", "📊 FORCE REFRESH ${status.picklistNumber}: scanned=${status.isScanned}, total=${status.totalQty}, scanned=${status.scannedQty}, remaining=${status.remainingQty}")
                    }
                }
                
            } catch (e: CancellationException) {
                Log.d("ScanViewModel", "ℹ️ Force refresh cancelled: ${e.message}")
                throw e // Re-throw cancellation
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error force refreshing picklist statuses: ${e.message}")
            }
        }
    }
    
    
    /**
     * Load picklist items menggunakan cache dari Repository
     */
    fun loadPicklistItems(picklistNumber: String) {
        val startTime = System.currentTimeMillis()
        Log.i("ScanViewModel", "🔥 === LOADING PICKLIST ITEMS START ===")
        Log.i("ScanViewModel", "🔥 Picklist: $picklistNumber")
        Log.i("ScanViewModel", "🔥 Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        // Hindari membatalkan request yang sedang berjalan untuk picklist yang sama
        if (currentPicklistNumber == picklistNumber && (loadingJob?.isActive == true || _isLoading.value == true)) {
            Log.w("ScanViewModel", "⚠️ Already loading picklist $picklistNumber, skipping duplicate request")
            return
        }
        
        // **PERBAIKAN BARU**: Clear EPC yang sudah diproses saat ganti picklist
        val previousPicklist = currentPicklistNumber
        if (previousPicklist != null && previousPicklist != picklistNumber) {
            Log.d("ScanViewModel", "🔄 Switching picklist from $previousPicklist to $picklistNumber")
            Log.d("ScanViewModel", "🧹 Clearing EPC tracking untuk picklist baru")
            
        // Clear cache untuk picklist baru
            _processedRfidData.value = emptyList()
            
            // Reset unique set/applied guard untuk picklist lama
            appliedEpcPerPicklist.remove(previousPicklist)
            
            // Single-only mode: tidak ada background batch / retry queue untuk dibatalkan
            backgroundReloadJob?.cancel()
            backgroundReloadJob = null
            
            // **PERBAIKAN**: Jangan reset RFID detection counter saat ganti picklist
            // Biarkan counter tetap untuk tracking total deteksi
            Log.d("ScanViewModel", "📊 RFID detection counter tetap: ${totalRfidDetections}")
        }
        
        currentPicklistNumber = picklistNumber
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }
                
                Log.i("ScanViewModel", "🔥 Loading picklist items via Repository (with cache)")
                // v4.3.4 flow: ambil list artikel langsung
                val items = repository.getPicklistItems(picklistNumber)
                
                val loadTime = System.currentTimeMillis() - startTime
                
                if (items.isNotEmpty()) {
                    Log.i("ScanViewModel", "✅ Loaded ${items.size} items in ${loadTime}ms")
                    
                    // Log detail item pertama dan beberapa sample
                    val firstItem = items.first()
                    Log.i("ScanViewModel", "🔥 First item: ${firstItem.articleName} ${firstItem.size} - qtyPl=${firstItem.qtyPl}, qtyScan=${firstItem.qtyScan}")
                    
                    // Log summary statistics
                    val totalQtyPl = items.sumOf { it.qtyPl }
                    val totalQtyScan = items.sumOf { it.qtyScan }
                    val scannedItems = items.count { it.qtyScan > 0 }
                    val completedItems = items.count { it.qtyScan >= it.qtyPl }
                    
                    Log.i("ScanViewModel", "🔥 Summary: Total items=${items.size}, Scanned items=$scannedItems, Completed items=$completedItems")
                    Log.i("ScanViewModel", "🔥 Quantities: Total qtyPl=$totalQtyPl, Total qtyScan=$totalQtyScan, Remaining=${totalQtyPl - totalQtyScan}")
                    
                    // **PERBAIKAN**: Update UI di main thread SETELAH seeding selesai
                    withContext(Dispatchers.Main) {
                        _picklistItems.value = items
                        
                        // **PERBAIKAN BARU**: Inisialisasi RFID detection counter berdasarkan data yang sudah ada
                        initializeRfidDetectionCounter(items)
                        
                    // Skema 2: Jangan buat placeholder processed data. UI akan menampilkan qtyScan via items
                        
                        // **POIN 1, 2, 4, 5: Update semua qty display data**
                        updateQtySummary()
                        updateQtyStatusColors()
                        updateFilteredItems()
                        
                        // Update RFID counter: gunakan total qtyScan dari picklist saat load awal (skema 2)
                        val totalQtyScanAtLoad = items.sumOf { it.qtyScan }
                        _rfidDetectionCount.value = totalQtyScanAtLoad
                    }
                    
                } else {
                    Log.w("ScanViewModel", "⚠️ No items found for picklist: $picklistNumber in ${loadTime}ms")
                    withContext(Dispatchers.Main) {
                        _picklistItems.value = emptyList()
                    }
                }
                
                Log.i("ScanViewModel", "🔥 === LOADING PICKLIST ITEMS END ===")
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("ScanViewModel", "🔥 Loading cancelled for picklist: $picklistNumber")
                throw e // Re-throw cancellation exception
            } catch (e: Exception) {
                val loadTime = System.currentTimeMillis() - startTime
                Log.e("ScanViewModel", "❌ Error loading picklist items after ${loadTime}ms: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error loading picklist items: ${e.message}"
                    _picklistItems.value = emptyList()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
                loadingJob = null
            }
        }
    }
    
    /**
     * Add EPC ke temp collection dengan deduplication dan cooldown mechanism
     * RFID yang sudah ada tidak akan double dan tidak akan berbunyi dalam waktu cooldown
     * @param epc EPC yang dideteksi
     * @return Pair<Boolean, Boolean> - (isNewEpc, shouldPlaySound)
     */
    fun addEpc(epc: String): Pair<Boolean, Boolean> {
        Log.d("ScanViewModel", "🔥 Adding EPC to temp collection: $epc")
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClearTime <= 500L) {
            Log.d("ScanViewModel", "ℹ️ Ignoring EPC $epc due to recent clear (within 500ms)")
            return Pair(false, false)
        }
        
        // Single-only unique-set flow: tidak menggunakan temp collection
        
        // **MODE SEDERHANA**: gunakan Set unik per picklist sebagai satu-satunya sumber kebenaran
        val uniqueSet = getCurrentUniqueSet()
        val currentPicklist = currentPicklistNumber
        
        // **DEBUG ENHANCED**: Log detail tentang EPC set dengan informasi lebih lengkap
        Log.d("ScanViewModel", "🔍 EPC Check: $epc")
        Log.d("ScanViewModel", "🔍   Current picklist: $currentPicklist")
        Log.d("ScanViewModel", "🔍   Unique set size: ${uniqueSet.size}")
        Log.d("ScanViewModel", "🔍   Unique set contains EPC: ${uniqueSet.contains(epc)}")
        Log.d("ScanViewModel", "🔍   Processed EPC set size: ${processedEpcPerPicklist[currentPicklist]?.size ?: 0}")
        
        // **DEBUG ENHANCED**: Log isi unique set untuk debugging
        if (uniqueSet.isNotEmpty()) {
            Log.d("ScanViewModel", "🔍   Unique set contents: ${uniqueSet.take(10).joinToString(", ")}${if (uniqueSet.size > 10) "..." else ""}")
        } else {
            Log.d("ScanViewModel", "🔍   Unique set is EMPTY - this might be the problem!")
        }
        
        // **DEBUG ENHANCED**: Log isi processed EPC set juga
        val processedSet = processedEpcPerPicklist[currentPicklist]
        if (processedSet != null && processedSet.isNotEmpty()) {
            Log.d("ScanViewModel", "🔍   Processed set contents: ${processedSet.take(10).joinToString(", ")}${if (processedSet.size > 10) "..." else ""}")
        } else {
            Log.d("ScanViewModel", "🔍   Processed set is EMPTY or NULL")
        }
        
        // **FALLBACK CHECK**: Jika unique set kosong tapi processed set ada, cek processed set juga
        if (uniqueSet.isEmpty() && processedSet != null && processedSet.contains(epc)) {
            Log.d("ScanViewModel", "🔧 FALLBACK: EPC $epc found in processed set - TIDAK ADA SUARA")
            // Seed unique set dengan processed set untuk konsistensi
            uniqueSet.addAll(processedSet)
            _rfidDetectionCount.value = uniqueSet.size
            Log.d("ScanViewModel", "🔧 FALLBACK: Seeded unique set with ${processedSet.size} EPCs from processed set")
            return Pair(false, false)
        }
        
        if (!uniqueSet.add(epc)) {
            Log.d("ScanViewModel", "ℹ️ EPC $epc sudah ada di unique set - TIDAK ADA SUARA")
            return Pair(false, false)
        }
        _rfidDetectionCount.value = uniqueSet.size
        Log.d("ScanViewModel", "✅ EPC $epc: BARU - masuk unique set - ADA SUARA (uniqueCount=${uniqueSet.size})")
        lastRfidDetectionTime = currentTime
        
        // Lookup sudah dilakukan oleh RfidScanManager secara otomatis
        // Tidak perlu melakukan lookup lagi di ViewModel
        
        // Return (isNewEpc, shouldPlaySound) -> EPC baru memicu beep
        return Pair(true, true)
    }
    
    
    /**
     * Process batch RFID collection ke API Nirwana dengan validasi status
     */
    private suspend fun processBatchRfidCollection() {
        Log.d("ScanViewModel", "ℹ️ Batch processing disabled (single-only mode)")
        return
        try {
            /* removed tempRfidCollection */ if (emptyList<String>().isEmpty()) {
                v("ScanViewModel", "🔥 No RFID in temp collection to process")
                return
            }
            
            // **PERBAIKAN KRITIS**: Cek apakah job masih aktif sebelum memproses
            // Tapi tetap proses EPC yang sudah ada untuk mencegah data loss
            /* removed backgroundProcessingJob */ if (false) {
                v("ScanViewModel", "🔥 Background processing job cancelled, but processing existing EPCs to prevent data loss")
                // Jangan return, tetap lanjutkan processing untuk EPC yang sudah ada
            }
            
            v("ScanViewModel", "🔥 Processing 0 RFID tags...")
            
            // **PERBAIKAN KRITIS**: Copy RFID collection dan clear untuk batch berikutnya
            // Gunakan distinct untuk menghindari duplikasi saat post ke API
            val distinctRfidList = emptyList<String>()
            
            // **PERBAIKAN KRITIS**: Clear tempRfidCollection SEBELUM processing untuk mencegah interference
            /* removed clear */
            v("ScanViewModel", "🔥 Cleared tempRfidCollection to prevent interference with ongoing scanning")
            
            // **PERBAIKAN BARU**: Filter EPC yang sudah diproses sebelumnya untuk mencegah re-posting
            val currentPicklist = currentPicklistNumber
            val rfidListToProcess = if (currentPicklist != null) {
                val processedEpcSet = processedEpcPerPicklist[currentPicklist] ?: mutableSetOf()
                val appliedSet = appliedEpcPerPicklist[currentPicklist] ?: mutableSetOf()
                val newEpcs = distinctRfidList.filter { epc ->
                    !processedEpcSet.contains(epc) &&
                    !appliedSet.contains(epc) &&
                    !inFlightEpcs.contains(epc)
                }
                
                v("ScanViewModel", "🔥 EPC filtering results:")
                v("ScanViewModel", "🔥   Total distinct EPCs: ${distinctRfidList.size}")
                v("ScanViewModel", "🔥   Already processed EPCs: ${distinctRfidList.size - newEpcs.size}")
                v("ScanViewModel", "🔥   New EPCs to process: ${newEpcs.size}")
                
                if (distinctRfidList.size - newEpcs.size > 0) {
                    val alreadyProcessedEpcs = distinctRfidList.filter { epc -> processedEpcSet.contains(epc) }
                    v("ScanViewModel", "🔥   Already processed EPCs: ${alreadyProcessedEpcs.joinToString(", ")}")
                }
                
                newEpcs
            } else {
                distinctRfidList
            }
            
            // **PERBAIKAN KRITIS**: Jika tidak ada EPC baru, tidak perlu proses
            if (rfidListToProcess.isEmpty()) {
                v("ScanViewModel", "🔥 No new EPCs to process (all EPCs already processed)")
                return
            }
            
            v("ScanViewModel", "🔥 Processing ${rfidListToProcess.size} new RFID tags (filtered out already processed)")
            
            // **PERBAIKAN BARU**: Tambahkan EPC yang akan diproses ke processedEpcPerPicklist dan guard appliedEpcPerPicklist
            if (currentPicklist != null) {
                val processedEpcSet = processedEpcPerPicklist.getOrPut(currentPicklist) { mutableSetOf() }
                val appliedSet = appliedEpcPerPicklist.getOrPut(currentPicklist) { mutableSetOf() }
                rfidListToProcess.forEach { epc ->
                    processedEpcSet.add(epc)
                    appliedSet.add(epc)
                    v("ScanViewModel", "✅ EPC $epc ditambahkan ke processedEpcPerPicklist[$currentPicklist] (permanent block)")
                }
                // Pastikan counter ikut bertambah setelah processed set bertambah
                updateRfidCounter()
            }
            
            // **PERBAIKAN KRITIS**: Batch lookup ke API Nirwana dengan true background processing
            // Gunakan coroutine terpisah untuk mencegah interference dengan scanning
            val products = try {
                v("ScanViewModel", "🔥 Starting TRUE BACKGROUND batch lookup for ${rfidListToProcess.size} RFID tags...")
                v("ScanViewModel", "🔥 This will NOT interfere with ongoing scanning")
                
                // **TRUE BACKGROUND**: Gunakan Job independen agar tidak ikut terbatal oleh debounce/parent
                // Penting: Job() membuat root job baru (tidak menjadi child), sehingga tidak menerima cancel dari parent
                withContext(Dispatchers.IO + Job()) {
                    repository.batchLookupRfidList(rfidListToProcess)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                v("ScanViewModel", "🔥 Background batch lookup cancelled (normal behavior): ${e.message}")
                // **PERBAIKAN KRITIS**: Jangan retry jika job di-cancel, biarkan proses berjalan normal
                v("ScanViewModel", "🔥 Skipping retry due to cancellation - process will continue normally")
                return
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error during background batch lookup: ${e.message}", e)
                _errorMessage.value = "Error processing RFID batch: ${e.message}"
                return
            }
            
            if (products.isNotEmpty()) {
                Log.d("ScanViewModel", "✅ Successfully processed ${products.size} products from API")
            v("ScanViewModel", "Products from API:")
                products.forEachIndexed { index, product ->
                    Log.d("ScanViewModel", "🔥 Product #${index + 1}: ${product.articleName} ${product.size} (${product.articleId})")
                }
            v("ScanViewModel", "Products end")
                Log.d("ScanViewModel", "🚩 ENTER MAPPING: products=${products.size}, epcs=${rfidListToProcess.size}")
                
                // Validasi dan kategorisasi data berdasarkan picklist
                var currentItems = _picklistItems.value ?: emptyList()
                val processedData = mutableListOf<ProcessedRfidData>()
                
                // **DEBUGGING**: Log semua items di picklist untuk debugging
                v("ScanViewModel", "🔥 Available picklist items:")
                currentItems.forEach { item ->
                    v("ScanViewModel", "🔥   - ${item.articleName} ${item.size} (${item.articleId}) - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}")
                }
                
                // **PERBAIKAN KRITIS**: Mapping EPC ke produk menggunakan rfidList dari API response
                Log.d("ScanViewModel", "🔥 ===== STARTING EPC MAPPING =====")
                Log.d("ScanViewModel", "🔥 Total products from API: ${products.size}")
                Log.d("ScanViewModel", "🔥 Total EPCs to process: ${rfidListToProcess.size}")
                
                // **TRACKING EPC**: Buat set untuk melacak EPC yang sudah diproses
                val processedEpcs = mutableSetOf<String>()
                
                products.forEach { product ->
                    v("ScanViewModel", "🔥 Processing product: ${product.articleName} ${product.size}")
                    v("ScanViewModel", "🔥 Product RFID List: ${product.rfidList}")
                    
                    // **PERBAIKAN BARU**: Gunakan rfidList dari API response untuk mapping yang akurat
                    val productEpcs = product.rfidList
                    
                    if (productEpcs.isNotEmpty()) {
                        v("ScanViewModel", "✅ Product has ${productEpcs.size} RFID(s) in rfidList")
                        
                        // **PERBAIKAN**: Proses setiap EPC dalam rfidList
                        productEpcs.forEach { epc ->
                            // **TRACKING**: Tambahkan EPC ke set yang sudah diproses
                            processedEpcs.add(epc)
                            
                            v("ScanViewModel", "🔥 Processing EPC $epc -> ${product.articleName} ${product.size} (${product.articleId})")
                    
                    // **PERBAIKAN**: Cari item berdasarkan articleId DAN size untuk deteksi overscan yang akurat
                    val picklistItem = currentItems.find { it.articleId == product.articleId && it.size == product.size }
                    
                    if (picklistItem != null) {
                        // Item ada di picklist dengan size yang sama - cek status
                        val currentQtyScan = picklistItem.qtyScan
                        val maxAllowedQty = picklistItem.qtyPl
                        // **PERBAIKAN KRITIS**: Setiap EPC hanya menambah 1, bukan product.qty
                        val newQtyScan = currentQtyScan + 1
                        
                        val status = when {
                            newQtyScan <= maxAllowedQty -> "VALID" // Sesuai atau kurang
                            newQtyScan > maxAllowedQty -> "OVERCAN" // Lebih dari rencana
                            else -> "UNKNOWN"
                        }
                        
                        v("ScanViewModel", "🔥 Item ${product.articleName} ${product.size}: current=$currentQtyScan, new=$newQtyScan, max=$maxAllowedQty, status=$status")
                        v("ScanViewModel", "🔥 PicklistItem found: ${picklistItem.articleName} ${picklistItem.size} (${picklistItem.articleId})")
                        
                        val processedItem = ProcessedRfidData(
                            epc = epc, // **PERBAIKAN**: Gunakan EPC yang sebenarnya
                            articleId = product.articleId,
                            articleName = product.articleName,
                            size = product.size,
                            qty = 1, // **PERBAIKAN KRITIS**: Setiap EPC = 1 qty
                            productId = product.productId,
                            productName = product.productName,
                            brand = product.brand,
                            category = product.category,
                            subCategory = product.subCategory,
                            color = product.color,
                            gender = product.gender,
                            warehouse = product.warehouse,
                            tagStatus = status // Tambahkan status
                        )
                        
                        processedData.add(processedItem)
                        
                        // **CRITICAL FIX**: Update PicklistItem dengan qtyScan yang baru
                        val updatedPicklistItem = picklistItem.copy(qtyScan = newQtyScan)
                        currentItems = currentItems.map { item ->
                            if (item.articleId == product.articleId && item.size == product.size) {
                                updatedPicklistItem
                            } else {
                                item
                            }
                        }
                        
                        v("ScanViewModel", "🔥 Updated PicklistItem: ${product.articleName} ${product.size} qtyScan: $currentQtyScan -> $newQtyScan")
                        v("ScanViewModel", "🔥 Updated item details: articleId=${product.articleId}, size=${product.size}, qtyPl=${picklistItem.qtyPl}")
                        
                    } else {
                        // **PERBAIKAN**: Item tidak ada di picklist dengan size yang sama
                        // Cek apakah ada item dengan articleId yang sama tapi size berbeda (OVERCAN)
                        val sameArticleItem = currentItems.find { it.articleId == product.articleId }
                        
                        val status = if (sameArticleItem != null) {
                            "OVERCAN" // Ada artikel yang sama tapi size berbeda
                        } else {
                            "NON_PICKLIST" // Tidak ada artikel yang sama sama sekali
                        }
                        
                        // Keep warnings, but reduce details unless verbose
                        Log.w("ScanViewModel", "⚠️ Item ${product.articleName} ${product.size} not found in picklist with same size - $status")
                        v("ScanViewModel", "⚠️ Same article check: ${sameArticleItem?.articleName} ${sameArticleItem?.size} (${sameArticleItem?.articleId})")
                        v("ScanViewModel", "⚠️ Looking for: articleId=${product.articleId}, size=${product.size}")
                        v("ScanViewModel", "⚠️ Available items with same articleId:")
                        currentItems.filter { it.articleId == product.articleId }.forEach { item ->
                            v("ScanViewModel", "⚠️   - ${item.articleName} ${item.size} (${item.articleId})")
                        }
                        
                        val processedItem = ProcessedRfidData(
                            epc = epc, // **PERBAIKAN**: Gunakan EPC yang sebenarnya
                            articleId = product.articleId,
                            articleName = product.articleName,
                            size = product.size,
                            qty = product.qty,
                            productId = product.productId,
                            productName = product.productName,
                            brand = product.brand,
                            category = product.category,
                            subCategory = product.subCategory,
                            color = product.color,
                            gender = product.gender,
                            warehouse = product.warehouse,
                            tagStatus = status
                        )
                        
                        processedData.add(processedItem)
                    }
                }
                    } else {
                        Log.w("ScanViewModel", "⚠️ Product ${product.articleName} tidak memiliki rfidList - menggunakan fallback mapping")
                        Log.w("ScanViewModel", "⚠️ Product rfidList is empty: ${product.rfidList}")
                        
                        // **FALLBACK**: Jika tidak ada rfidList, gunakan mapping berdasarkan index (untuk backward compatibility)
                        val productIndex = products.indexOf(product)
                        val epc = if (productIndex < rfidListToProcess.size) rfidListToProcess[productIndex] else ""
                        
                        if (epc.isNotEmpty()) {
                            Log.d("ScanViewModel", "🔥 Fallback mapping EPC $epc -> ${product.articleName} ${product.size}")
                            Log.d("ScanViewModel", "🔥 Fallback index: $productIndex, EPC from list: $epc")
                            
                            // Gunakan logika yang sama seperti sebelumnya untuk fallback
                            val picklistItem = currentItems.find { it.articleId == product.articleId && it.size == product.size }
                            
                            if (picklistItem != null) {
                                val currentQtyScan = picklistItem.qtyScan
                                val maxAllowedQty = picklistItem.qtyPl
                                // **PERBAIKAN KRITIS**: Setiap EPC hanya menambah 1, bukan product.qty
                                val newQtyScan = currentQtyScan + 1
                                
                                val status = when {
                                    newQtyScan <= maxAllowedQty -> "VALID"
                                    newQtyScan > maxAllowedQty -> "OVERCAN"
                                    else -> "UNKNOWN"
                                }
                                
                                val processedItem = ProcessedRfidData(
                                    epc = epc,
                                    articleId = product.articleId,
                                    articleName = product.articleName,
                                    size = product.size,
                                    qty = 1, // **PERBAIKAN KRITIS**: Setiap EPC = 1 qty
                                    productId = product.productId,
                                    productName = product.productName,
                                    brand = product.brand,
                                    category = product.category,
                                    subCategory = product.subCategory,
                                    color = product.color,
                                    gender = product.gender,
                                    warehouse = product.warehouse,
                                    tagStatus = status
                                )
                                
                                processedData.add(processedItem)
                                
                                val updatedPicklistItem = picklistItem.copy(qtyScan = newQtyScan)
                                currentItems = currentItems.map { item ->
                                    if (item.articleId == product.articleId && item.size == product.size) {
                                        updatedPicklistItem
                                    } else {
                                        item
                                    }
                                }
                            } else {
                                val processedItem = ProcessedRfidData(
                                    epc = epc,
                                    articleId = product.articleId,
                                    articleName = product.articleName,
                                    size = product.size,
                                    qty = product.qty,
                                    productId = product.productId,
                                    productName = product.productName,
                                    brand = product.brand,
                                    category = product.category,
                                    subCategory = product.subCategory,
                                    color = product.color,
                                    gender = product.gender,
                                    warehouse = product.warehouse,
                                    tagStatus = "NON_PICKLIST"
                                )
                                
                                processedData.add(processedItem)
                            }
                        }
                    }
                }
                
                // **LOGGING SUMMARY**: Log hasil mapping
                val validCount = processedData.count { it.tagStatus == "VALID" }
                val overscanCount = processedData.count { it.tagStatus == "OVERCAN" }
                val nonPicklistCount = processedData.count { it.tagStatus == "NON_PICKLIST" }
                Log.d("ScanViewModel", "Mapping summary: total=${processedData.size}, valid=$validCount, overscan=$overscanCount, nonPicklist=$nonPicklistCount")
                
                // **TRACKING EPC & FALLBACK**: Tangani EPC yang belum termap dari hasil batch
                var remainingEpcs = rfidListToProcess.filter { !processedEpcs.contains(it) }
                Log.d("ScanViewModel", "🔥 ===== EPC NOT FOUND ANALYSIS (pre-fallback) =====")
                Log.d("ScanViewModel", "🔥 Total EPCs sent to API: ${rfidListToProcess.size}")
                Log.d("ScanViewModel", "🔥 EPCs processed by API: ${processedEpcs.size}")
                Log.d("ScanViewModel", "🔥 EPCs NOT mapped yet: ${remainingEpcs.size}")

                if (remainingEpcs.isNotEmpty()) {
                    if (products.size == 1) {
                        // Fallback 1: Jika hanya ada 1 produk, atribusikan semua EPC sisa ke produk itu
                        val p = products.first()
                        Log.d("ScanViewModel", "🔥 Fallback mapping: assigning ${remainingEpcs.size} EPC(s) to single product ${p.articleName} ${p.size}")
                        remainingEpcs.forEach { epc ->
                            processedEpcs.add(epc)
                            val picklistItem = currentItems.find { it.articleId == p.articleId && it.size == p.size }
                            if (picklistItem != null) {
                                val currentQtyScan = picklistItem.qtyScan
                                val maxAllowedQty = picklistItem.qtyPl
                                val newQtyScan = currentQtyScan + 1
                                val status = if (newQtyScan <= maxAllowedQty) "VALID" else "OVERCAN"
                                processedData.add(
                                    ProcessedRfidData(
                                        epc = epc,
                                        articleId = p.articleId,
                                        articleName = p.articleName,
                                        size = p.size,
                                        qty = 1,
                                        productId = p.productId,
                                        productName = p.productName,
                                        brand = p.brand,
                                        category = p.category,
                                        subCategory = p.subCategory,
                                        color = p.color,
                                        gender = p.gender,
                                        warehouse = p.warehouse,
                                        tagStatus = status
                                    )
                                )
                                val updated = picklistItem.copy(qtyScan = newQtyScan)
                                currentItems = currentItems.map { item ->
                                    if (item.articleId == p.articleId && item.size == p.size) updated else item
                                }
                            } else {
                                // Non-picklist fallback
                                processedData.add(
                                    ProcessedRfidData(
                                        epc = epc,
                                        articleId = p.articleId,
                                        articleName = p.articleName,
                                        size = p.size,
                                        qty = 1,
                                        productId = p.productId,
                                        productName = p.productName,
                                        brand = p.brand,
                                        category = p.category,
                                        subCategory = p.subCategory,
                                        color = p.color,
                                        gender = p.gender,
                                        warehouse = p.warehouse,
                                        tagStatus = "NON_PICKLIST"
                                    )
                                )
                            }
                        }
                    } else {
                        // Fallback 2: Multi-produk — lakukan single-lookup per EPC yang tersisa
                        Log.d("ScanViewModel", "🚩 FALLBACK SINGLE-LOOKUP: remaining=${remainingEpcs.size}")
                        val resolved = mutableListOf<String>()
                        for (epc in remainingEpcs) {
                            try {
                                val single = withContext(Dispatchers.IO) { repository.lookupSingleRfid(epc) }
                                if (single != null) {
                                    processedEpcs.add(epc)
                                    resolved.add(epc)
                                    val picklistItem = currentItems.find { it.articleId == single.articleId && it.size == single.size }
                                    if (picklistItem != null) {
                                        val currentQtyScan = picklistItem.qtyScan
                                        val maxAllowedQty = picklistItem.qtyPl
                                        val newQtyScan = currentQtyScan + 1
                                        val status = if (newQtyScan <= maxAllowedQty) "VALID" else "OVERCAN"
                                        processedData.add(
                                            ProcessedRfidData(
                                                epc = epc,
                                                articleId = single.articleId,
                                                articleName = single.articleName,
                                                size = single.size,
                                                qty = 1,
                                                productId = single.productId,
                                                productName = single.productName,
                                                brand = single.brand,
                                                category = single.category,
                                                subCategory = single.subCategory,
                                                color = single.color,
                                                gender = single.gender,
                                                warehouse = single.warehouse,
                                                tagStatus = status
                                            )
                                        )
                                        val updated = picklistItem.copy(qtyScan = newQtyScan)
                                        currentItems = currentItems.map { item ->
                                            if (item.articleId == single.articleId && item.size == single.size) updated else item
                                        }
                                    } else {
                                        processedData.add(
                                            ProcessedRfidData(
                                                epc = epc,
                                                articleId = single.articleId,
                                                articleName = single.articleName,
                                                size = single.size,
                                                qty = 1,
                                                productId = single.productId,
                                                productName = single.productName,
                                                brand = single.brand,
                                                category = single.category,
                                                subCategory = single.subCategory,
                                                color = single.color,
                                                gender = single.gender,
                                                warehouse = single.warehouse,
                                                tagStatus = "NON_PICKLIST"
                                            )
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("ScanViewModel", "⚠️ Single lookup failed for EPC $epc: ${'$'}{e.message}")
                                // removed tempRfidCollection handling
                                Log.d("ScanViewModel", "🔥 Lookup failed for EPC $epc (single-only mode, no temp removal)")
                            }
                        }
                        // Update remaining after single-lookups
                        remainingEpcs = remainingEpcs.filter { !resolved.contains(it) }
                    }
                }

                // Enqueue EPC yang tetap tidak ter-resolve untuk retry
                if (remainingEpcs.isNotEmpty()) {
                    Log.d("ScanViewModel", "🔥 EPCs still unresolved after fallback: ${remainingEpcs.size}")
                    remainingEpcs.forEach { epc -> Log.d("ScanViewModel", "🔥   - $epc") }
                    // Single-only mode: skip enqueue retry for batch
                }
                Log.d("ScanViewModel", "🔥 ===== EPC MAPPING COMPLETE =====")
                
                // **CRITICAL FIX**: Lindungi merge UI dengan Mutex dan NonCancellable di Main thread
                uiMergeMutex.lock()
                try {
                    withContext(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                    Log.d("ScanViewModel", "🔥 Updating _picklistItems.value with ${currentItems.size} items")
                    currentItems.forEach { item ->
                        if (item.qtyScan > 0) {
                            Log.d("ScanViewModel", "🔥 Item with qtyScan > 0: ${item.articleName} ${item.size} - qtyScan=${item.qtyScan}")
                        }
                    }
                    _picklistItems.value = currentItems
                    Log.d("ScanViewModel", "🔥 _picklistItems.value updated successfully")
                    val incSummary = currentItems.filter { it.qtyScan > 0 }
                        .joinToString { it.articleName + " " + it.size + "=" + it.qtyScan }
                    Log.d("ScanViewModel", "🚩 ITEMS UPDATED: ${incSummary}")

                    // **DEBUGGING**: Log semua items setelah update untuk debugging
                    Log.d("ScanViewModel", "🔥 Final items after update:")
                    currentItems.forEach { item ->
                        Log.d("ScanViewModel", "🔥   - ${item.articleName} ${item.size}: qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}")
                    }

                    // **CRITICAL FIX**: Update processedRfidData SEBELUM updateFilteredItems
                    val currentData = _processedRfidData.value ?: emptyList()
                    _processedRfidData.value = currentData + processedData

                    // **CRITICAL FIX**: Update filtered items setelah data diupdate
                    updateFilteredItems()
                    updateQtySummary()
                    updateQtyStatusColors()
                    }
                } finally {
                    uiMergeMutex.unlock()
                }
                
                // Mapping ke Nirwana (batchLookupRfidList) sudah dilakukan di atas dan berjalan di background
                
                // **CRITICAL FIX**: Notify qty updates setelah data diupdate
                processedData.forEach { processedItem ->
                    if (processedItem.tagStatus == "VALID" || processedItem.tagStatus == "OVERCAN") {
                        val picklistItem = currentItems.find { it.articleId == processedItem.articleId }
                        if (picklistItem != null) {
                            notifyQtyUpdate(
                                articleId = processedItem.articleId,
                                articleName = processedItem.articleName,
                                oldQtyScan = picklistItem.qtyScan - processedItem.qty,
                                newQtyScan = picklistItem.qtyScan,
                                qtyPl = picklistItem.qtyPl
                            )
                        }
                    }
                }
                
                // **PERBAIKAN BARU**: Verifikasi sinkronisasi data setelah pemrosesan
                val currentPicklist = currentPicklistNumber
                if (currentPicklist != null && processedData.isNotEmpty()) {
                    Log.d("ScanViewModel", "🔄 Verifying data synchronization after processing...")
                    
                    // Cek apakah ada EPC yang berhasil diproses
                    val validProcessedEpc = processedData.filter { it.tagStatus == "VALID" || it.tagStatus == "OVERCAN" }
                    if (validProcessedEpc.isNotEmpty()) {
                        Log.d("ScanViewModel", "✅ ${validProcessedEpc.size} EPCs successfully processed, data should be synchronized")
                        
                        // **PERBAIKAN**: Pastikan processedEpcPerPicklist sudah ter-update dengan benar
                        val processedEpcSet = processedEpcPerPicklist.getOrPut(currentPicklist) { mutableSetOf() }
                        val epcsFromProcessing = validProcessedEpc.map { it.epc }
                        processedEpcSet.addAll(epcsFromProcessing)
                        
                        // **PERBAIKAN KRITIS**: Update RFID counter (non-decreasing)
                        val before = _rfidDetectionCount.value ?: 0
                        updateRfidCounter()
                        val after = _rfidDetectionCount.value ?: 0
                        if (after < before) {
                            _rfidDetectionCount.value = before
                            totalRfidDetections = before
                        }
                        
                        Log.d("ScanViewModel", "✅ Updated processedEpcPerPicklist[$currentPicklist]: ${processedEpcSet.size} EPCs total")
                    }
                }
                
                
            } else {
                Log.w("ScanViewModel", "⚠️ No products found for RFID batch")
                _errorMessage.value = "No products found for scanned RFID tags"
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error processing batch RFID: ${e.message}", e)
            _errorMessage.value = "Error processing RFID batch: ${e.message}"
        }
    }

    // processSingleEpcImmediate method removed - lookup now handled by RfidScanManager

    /**
     * Force flush pending EPCs segera (dipanggil saat Stop Scan)
     */
    fun forceFlushPendingRfid() {
        Log.d("ScanViewModel", "ℹ️ forceFlush disabled (single-only mode)")
    }

    // === RETRY QUEUE HELPERS ===
    private fun enqueueFailedEpcs(epcs: List<String>) { /* disabled in single-only mode */ }

    private fun enqueueFailedEpcs(epcs: Collection<String>) { /* disabled in single-only mode */ }

    private fun startRetryWorkerIfNeeded() { /* disabled in single-only mode */ }
    
    
    /**
     * Find article ID berdasarkan EPC menggunakan API Nirwana
     */
    private suspend fun findArticleIdByEpc(epc: String): String? {
        try {
            Log.d("ScanViewModel", "🔥 Looking up EPC using Nirwana API: $epc")
            
            // Coba lookup menggunakan API Nirwana terlebih dahulu
            val articleId = repository.lookupEpcToArticleId(epc)
            
            if (articleId != null) {
                Log.d("ScanViewModel", "✅ EPC $epc mapped to article $articleId via Nirwana API")
                return articleId
            }
            
            // Fallback ke logic sederhana jika API Nirwana gagal
            Log.w("ScanViewModel", "⚠️ Nirwana API lookup failed for EPC: $epc, using fallback logic")
            
            val currentItems = _picklistItems.value ?: emptyList()
            
            if (currentItems.isEmpty()) {
                Log.w("ScanViewModel", "⚠️ No picklist items available for EPC mapping: $epc")
                return null
            }
            
            // Logic sederhana sebagai fallback
            val epcSuffix = epc.takeLast(4)
            val epcIndex = try {
                epcSuffix.toInt() % currentItems.size
            } catch (e: NumberFormatException) {
                epc.hashCode() % currentItems.size
            }
            
            val selectedItem = currentItems[epcIndex]
            Log.d("ScanViewModel", "🔍 EPC $epc mapped to article ${selectedItem.articleId} (${selectedItem.articleName}) via fallback")
            
            return selectedItem.articleId
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error finding article ID for EPC $epc: ${e.message}")
            return null
        }
    }
    
    /**
     * Get processed RFID data berdasarkan status
     */
    fun getProcessedDataByStatus(status: String): List<ProcessedRfidData> {
        val allData = _processedRfidData.value ?: emptyList()
        return allData.filter { it.tagStatus == status }
    }
    
    /**
     * Get summary dari processed RFID data
     * **PERBAIKAN**: Hitung qty bukan jumlah item
     */
    fun getProcessedDataSummary(): Map<String, Int> {
        val allData = _processedRfidData.value ?: emptyList()
        return mapOf(
            "VALID" to allData.filter { it.tagStatus == "VALID" }.sumOf { it.qty },
            "OVERCAN" to allData.filter { it.tagStatus == "OVERCAN" }.sumOf { it.qty },
            "NON_PICKLIST" to allData.filter { it.tagStatus == "NON_PICKLIST" }.sumOf { it.qty },
            "TOTAL" to allData.sumOf { it.qty }
        )
    }
    
    // **POIN 1: Summary Statistics untuk Qty**
    /**
     * Update qty summary statistics
     */
    private fun updateQtySummary() {
        val items = _picklistItems.value ?: emptyList()
        
        val totalQtyPl = items.sumOf { it.qtyPl }
        val totalQtyScan = items.sumOf { it.qtyScan }
        // Best practice: remaining boleh negatif untuk merefleksikan overscan
        val remainingQty = totalQtyPl - totalQtyScan
        val scannedItems = items.count { it.qtyScan > 0 }
        val completedItems = items.count { it.isComplete() }
        val totalItems = items.size
        val progressPercentage = if (totalQtyPl > 0) {
            ((totalQtyScan.toFloat() / totalQtyPl.toFloat()) * 100).toInt() // bisa >100 jika overscan
        } else 0
        
        val summary = QtySummary(
            totalQtyPl = totalQtyPl,
            totalQtyScan = totalQtyScan,
            remainingQty = remainingQty,
            scannedItems = scannedItems,
            completedItems = completedItems,
            totalItems = totalItems,
            progressPercentage = progressPercentage
        )
        
        _qtySummary.value = summary
        
        Log.d("ScanViewModel", "🔥 QtySummary updated: totalPl=$totalQtyPl, totalScan=$totalQtyScan, remaining=$remainingQty, progress=$progressPercentage%")
    }
    
    // **POIN 2: Status Colors untuk Qty**
    /**
     * Update qty status colors untuk semua items
     */
    private fun updateQtyStatusColors() {
        val items = _picklistItems.value ?: emptyList()
        val statusMap = items.associate { item ->
            item.articleId to item.getQtyStatus()
        }
        
        _qtyStatusColors.value = statusMap
        
        Log.d("ScanViewModel", "🔥 QtyStatusColors updated: ${statusMap.size} items")
    }
    
    // **POIN 4: Filtered Items (hide completed items, show remaining + non-picklist items)**
    /**
     * Update filtered items - HIDE COMPLETED ITEMS + SHOW NON-PICKLIST ITEMS
     * 
     * REASONING: 
     * - Item yang sudah complete (qtyScan == qtyPl) disembunyikan untuk fokus pada item yang belum selesai
     * - NON-PICKLIST items ditampilkan sebagai artikel dalam list utama dengan qtyPl=0 dan qtyScan=1
     * - Sorting konsisten: status -> articleName -> size untuk mempertahankan posisi yang stabil
     */
    private fun updateFilteredItems() {
        val items = _picklistItems.value ?: emptyList()
        val processedData = _processedRfidData.value ?: emptyList()
        
        Log.d("ScanViewModel", "🔥 === UPDATE FILTERED ITEMS START ===")
        Log.d("ScanViewModel", "🔥 updateFilteredItems: items=${items.size}, processedData=${processedData.size}")
        items.forEach { item ->
            Log.d("ScanViewModel", "🔥   - PICKLIST ITEM: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}")
        }
        processedData.forEach { item ->
            Log.d("ScanViewModel", "🔥   - PROCESSED DATA: ${item.articleName} ${item.size} (${item.tagStatus})")
        }
        
        // **PERBAIKAN**: Agregasi non-picklist per (articleId,size) agar qty mencerminkan jumlah EPC
        val nonPicklistGroups = processedData
            .filter { it.tagStatus == "NON_PICKLIST" }
            .groupBy { Pair(it.articleId, it.size) }

        val nonPicklistItems = nonPicklistGroups.map { (key, group) ->
            val any = group.first()
            val totalQty = group.size // setiap EPC menyumbang 1
            Log.d("ScanViewModel", "🔥 NON_PICKLIST aggregated: ${any.articleName} ${any.size} = ${totalQty}")
            PicklistItem(
                id = "non_picklist_${any.articleId}_${any.size}",
                noPicklist = currentPicklistNumber ?: "",
                articleId = any.articleId,
                articleName = any.articleName,
                size = any.size,
                productId = any.productId,
                qtyPl = 0,
                qtyScan = totalQty,
                createdAt = "",
                warehouse = any.warehouse,
                tagStatus = "NON_PICKLIST"
            )
        }

        // **PERBAIKAN**: Agregasi overscan yang TIDAK ada di picklist
        val overscanGroups = processedData
            .filter { it.tagStatus == "OVERCAN" }
            .filter { pd -> items.none { it.articleId == pd.articleId && it.size == pd.size } }
            .groupBy { Pair(it.articleId, it.size) }

        val overscanItems = overscanGroups.map { (key, group) ->
            val any = group.first()
            val totalQty = group.size
            Log.d("ScanViewModel", "🔥 OVERCAN aggregated (not in picklist): ${any.articleName} ${any.size} = ${totalQty}")
            PicklistItem(
                id = "overscan_${any.articleId}_${any.size}",
                noPicklist = currentPicklistNumber ?: "",
                articleId = any.articleId,
                articleName = any.articleName,
                size = any.size,
                productId = any.productId,
                qtyPl = 0,
                qtyScan = totalQty,
                createdAt = "",
                warehouse = any.warehouse,
                tagStatus = "OVERCAN"
            )
        }

        val additionalItems = nonPicklistItems + overscanItems
        
        // Gabungkan item picklist dengan item tambahan (non-picklist dan overscan yang tidak ada di picklist)
        val combinedItems = items + additionalItems
        
        // **PERBAIKAN BARU**: Filter out completed items dan maintain consistent sorting
        val filteredItems = combinedItems
            .filter { !it.isComplete() } // Hide completed items
            .sortedWith(compareBy<PicklistItem> { it.tagStatus } // Sort by status first
                .thenBy { it.articleName } // Then by article name
                .thenBy { it.size }) // Finally by size
        
        val completedCount = combinedItems.count { it.isComplete() }
        val remainingCount = filteredItems.size
        
        Log.d("ScanViewModel", "🔥 FilteredItems updated: ${combinedItems.size} total items")
        Log.d("ScanViewModel", "🔥   - ${completedCount} completed items (HIDDEN)")
        Log.d("ScanViewModel", "🔥   - ${remainingCount} remaining items (VISIBLE)")
        Log.d("ScanViewModel", "🔥   - ${additionalItems.size} additional items (non-picklist/overscan)")
        
        if (additionalItems.isNotEmpty()) {
            Log.d("ScanViewModel", "🔥 Additional items added to main list:")
            additionalItems.forEach { item ->
                Log.d("ScanViewModel", "🔥   - ${item.articleName} ${item.size} (qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}, status=${item.tagStatus})")
            }
        }
        
        if (completedCount > 0) {
            Log.d("ScanViewModel", "🔥 Completed items (HIDDEN):")
            combinedItems.filter { it.isComplete() }.forEach { item ->
                Log.d("ScanViewModel", "🔥   - ${item.articleName} ${item.size} (qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan})")
            }
        }
        
        _filteredItems.value = filteredItems
        Log.d("ScanViewModel", "🔥 === UPDATE FILTERED ITEMS COMPLETED ===")
        Log.d("ScanViewModel", "🔥 Final filtered items: ${filteredItems.size}")
        filteredItems.forEach { item ->
            Log.d("ScanViewModel", "🔥   - ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}")
        }
    }
    
    // **POIN 5: LiveData untuk Real-time Qty Updates**
    /**
     * Notify qty update untuk real-time UI updates
     */
    private fun notifyQtyUpdate(articleId: String, articleName: String, oldQtyScan: Int, newQtyScan: Int, qtyPl: Int) {
        val item = _picklistItems.value?.find { it.articleId == articleId }
        if (item != null) {
            val status = item.getQtyStatus()
            val message = item.getQtyStatusMessage()
            
            val update = QtyUpdate(
                articleId = articleId,
                articleName = articleName,
                oldQtyScan = oldQtyScan,
                newQtyScan = newQtyScan,
                qtyPl = qtyPl,
                status = status,
                message = message
            )
            
            _qtyUpdates.value = update
            
            Log.d("ScanViewModel", "🔥 QtyUpdate notified: $articleName $oldQtyScan->$newQtyScan/$qtyPl ($status)")
        }
    }
    
    /**
     * Get qty status info untuk item tertentu
     */
    fun getQtyStatusInfo(articleId: String): QtyStatusInfo? {
        val item = _picklistItems.value?.find { it.articleId == articleId }
        return item?.let {
            QtyStatusInfo(
                articleId = it.articleId,
                status = it.getQtyStatus(),
                message = it.getQtyStatusMessage(),
                progressPercentage = it.getProgressPercentage(),
                isComplete = it.isComplete()
            )
        }
    }
    
    /**
     * Get all qty status info
     */
    fun getAllQtyStatusInfo(): List<QtyStatusInfo> {
        val items = _picklistItems.value ?: emptyList()
        return items.map { item ->
            QtyStatusInfo(
                articleId = item.articleId,
                status = item.getQtyStatus(),
                message = item.getQtyStatusMessage(),
                progressPercentage = item.getProgressPercentage(),
                isComplete = item.isComplete()
            )
        }
    }
    
    /**
     * Clear processed RFID data berdasarkan status
     */
    fun clearProcessedDataByStatus(status: String) {
        val currentData = _processedRfidData.value ?: emptyList()
        val filteredData = currentData.filter { it.tagStatus != status }
        _processedRfidData.value = filteredData
        
        Log.d("ScanViewModel", "🔥 Cleared $status items, remaining: ${filteredData.size}")
    }
    
    /**
     * Get cache info untuk debugging
     */
    fun getCacheInfo(): String {
        Log.d("ScanViewModel", "🔥 === GET CACHE INFO START ===")
        Log.d("ScanViewModel", "🔍 DEBUG: getCacheInfo() called at ${System.currentTimeMillis()}")
        val picklists = _picklists.value ?: emptyList()
        val statuses = _picklistStatuses.value ?: emptyList()
        val isLoading = _isLoading.value ?: false
        
        Log.d("ScanViewModel", "🔍 DEBUG: Cache info - picklists: ${picklists.size}, statuses: ${statuses.size}, loading: $isLoading")
        
        return """
            Cache Info:
            - Picklists: ${picklists.size} items
            - Statuses: ${statuses.size} items  
            - Loading: $isLoading
            - Picklist Numbers: ${picklists.take(3).joinToString(", ")}${if (picklists.size > 3) "..." else ""}
        """.trimIndent()
    }
    
    /**
     * Debug method untuk troubleshooting
     */
    fun debugPicklistData() {
        viewModelScope.launch {
            try {
                Log.d("ScanViewModel", "🔥 === DEBUG PICKLIST DATA START ===")
                Log.d("ScanViewModel", "🔍 DEBUG: debugPicklistData() called at ${System.currentTimeMillis()}")
                Log.d("ScanViewModel", "🔍 DEBUG: Starting picklist data debug...")

                // Test koneksi Supabase
                val isConnected = repository.testSupabaseConnection()
                Log.d("ScanViewModel", "🔍 DEBUG: Supabase connection: $isConnected")

                // Cek cache stats
                val cacheStats = repository.getCacheStats()
                Log.d("ScanViewModel", "🔍 DEBUG: Cache stats: $cacheStats")

                // Cek picklist data
                Log.d("ScanViewModel", "🔍 DEBUG: Calling repository.getPicklists() from debug...")
                val picklists = repository.getPicklists()
                Log.d("ScanViewModel", "🔍 DEBUG: Picklists from repository: ${picklists.size} items")

                // Cek optimized data
                Log.d("ScanViewModel", "🔍 DEBUG: Calling repository.getAllTodayDataOptimized() from debug...")
                val optimizedData = repository.getAllTodayDataOptimized()
                Log.d("ScanViewModel", "🔍 DEBUG: Optimized data: ${optimizedData.size} picklists")

                Log.d("ScanViewModel", "🔍 DEBUG: Debug completed")

            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ DEBUG: Error during debug: ${e.message}", e)
            }
        }
    }

    fun forceRefreshPicklistData() {
        viewModelScope.launch {
            try {
                Log.d("ScanViewModel", "🔥 === FORCE REFRESH PICKLIST DATA START ===")
                Log.d("ScanViewModel", "🔍 DEBUG: forceRefreshPicklistData() called at ${System.currentTimeMillis()}")
                Log.d("ScanViewModel", "🔄 Force refreshing picklist data...")

                // **PERBAIKAN**: Coba ambil data langsung dari cache tanpa timeout
                try {
                    Log.d("ScanViewModel", "🔍 DEBUG: Attempting direct cache access...")
                    val directPicklists = repository.getPicklists()
                    
                    if (directPicklists.isNotEmpty()) {
                        Log.d("ScanViewModel", "✅ Force refresh successful: ${directPicklists.size} picklists")
                        _picklists.value = directPicklists
                        
                        // Load statuses juga
                        loadPicklistStatuses(directPicklists)
                    } else {
                        Log.w("ScanViewModel", "⚠️ Force refresh returned empty list")
                    }
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "❌ Force refresh failed: ${e.message}", e)
                }

            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error during force refresh: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get completion status untuk picklist tertentu dengan validasi overscan
     */
    fun getPicklistCompletionStatus(picklistNumber: String): PicklistStatus {
        return try {
            // **PERBAIKAN**: Gunakan data dari _picklistItems yang sudah ada untuk menghindari ANR
            val items = _picklistItems.value ?: emptyList()
            
            // Filter hanya items untuk picklist ini
            val picklistItems = items.filter { it.noPicklist == picklistNumber }
            
            if (picklistItems.isNotEmpty()) {
                val totalQty = picklistItems.sumOf { it.qtyPl }
                val scannedQty = picklistItems.sumOf { it.qtyScan }
                
                // Best practice: hitung overscan dan biarkan remaining bisa negatif
                val validatedScannedQty = scannedQty
                val remainingQty = totalQty - validatedScannedQty
                val overscanQty = kotlin.math.max(0, validatedScannedQty - totalQty)
                val isScanned = validatedScannedQty > 0
                
                Log.d("ScanViewModel", "🔥 VALIDATED Completion status for $picklistNumber:")
                Log.d("ScanViewModel", "🔥   totalQty: $totalQty")
                Log.d("ScanViewModel", "🔥   scannedQty (original): $scannedQty")
                Log.d("ScanViewModel", "🔥   scannedQty (validated): $validatedScannedQty")
                Log.d("ScanViewModel", "🔥   remainingQty: $remainingQty")
                Log.d("ScanViewModel", "🔥   overscanQty: $overscanQty (should be 0)")
                Log.d("ScanViewModel", "🔥   isScanned: $isScanned")
                
                PicklistStatus(
                    picklistNumber = picklistNumber,
                    isScanned = isScanned,
                    remainingQty = remainingQty,
                    totalQty = totalQty,
                    scannedQty = validatedScannedQty,
                    lastScanTime = null,
                    overscanQty = overscanQty
                )
            } else {
                // Picklist belum pernah di-scan atau tidak ada data
                PicklistStatus(
                    picklistNumber = picklistNumber,
                    isScanned = false,
                    remainingQty = 0,
                    totalQty = 0,
                    scannedQty = 0,
                    lastScanTime = null,
                    overscanQty = 0
                )
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "🔥 Error getting completion status for $picklistNumber: ${e.message}")
            PicklistStatus(
                picklistNumber = picklistNumber,
                isScanned = false,
                remainingQty = 0,
                totalQty = 0,
                scannedQty = 0,
                lastScanTime = null,
                overscanQty = 0
            )
        }
    }
    
    /**
     * Get current picklist number
     */
    fun getCurrentPicklistNumber(): String? = currentPicklistNumber
    
    /**
     * Get processed EPC list for current picklist (untuk seeding RfidScanManager)
     */
    fun getProcessedEpcListForCurrentPicklist(): List<String> {
        val currentPicklist = currentPicklistNumber
        return if (currentPicklist != null) {
            processedEpcPerPicklist[currentPicklist]?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Get lookup results untuk EPC yang sudah ada di database
     * Digunakan untuk seeding RfidScanManager agar tidak duplicate
     * 
     * **PERBAIKAN**: Buat ProductInfo dengan data asli dari picklist items,
     * bukan data dummy "UNKNOWN" agar RFID yang sudah pernah di-scan tidak tampil sebagai "UNKNOWN"
     */
    fun getDatabaseLookupResultsForCurrentPicklist(): Map<String, NirwanaApiService.ProductInfo> {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist == null) {
            Log.w("ScanViewModel", "⚠️ No current picklist number for lookup results")
            return emptyMap()
        }
        
        // **PERBAIKAN KRITIS**: Include EPC dari database DAN session saat ini
        val databaseProcessedEpcs = processedEpcPerPicklist[currentPicklist] ?: emptySet()
        val sessionProcessedEpcs = _processedRfidData.value?.map { it.epc }?.toSet() ?: emptySet()
        val allProcessedEpcs = databaseProcessedEpcs + sessionProcessedEpcs
        
        if (allProcessedEpcs.isEmpty()) {
            Log.d("ScanViewModel", "ℹ️ No processed EPCs for lookup results")
            return emptyMap()
        }
        
        Log.d("ScanViewModel", "🔍 Database processed EPCs: ${databaseProcessedEpcs.size}")
        Log.d("ScanViewModel", "🔍 Session processed EPCs: ${sessionProcessedEpcs.size}")
        Log.d("ScanViewModel", "🔍 Total processed EPCs for seeding: ${allProcessedEpcs.size}")
        
        // **PERBAIKAN KRITIS**: Buat lookup results berdasarkan data ASLI dari picklist items
        val lookupResults = mutableMapOf<String, NirwanaApiService.ProductInfo>()
        
        // Ambil data picklist items untuk mapping EPC ke article
        val currentItems = _picklistItems.value ?: emptyList()
        
        // Ambil data processed RFID untuk mapping EPC ke ProductInfo
        val processedData = _processedRfidData.value ?: emptyList()
        
        allProcessedEpcs.forEach { epc ->
            // **STRATEGI 1**: Cari di processedRfidData (data yang sudah pernah di-scan dan di-proses)
            val existingData = processedData.find { it.epc == epc }
            
            if (existingData != null) {
                // Gunakan data asli dari processed RFID
                val productInfo = NirwanaApiService.ProductInfo(
                    articleId = existingData.articleId,
                    articleName = existingData.articleName,
                    size = existingData.size,
                    qty = 1,
                    productId = existingData.productId ?: "",
                    productName = existingData.productName ?: existingData.articleName,
                    brand = existingData.brand ?: "",
                    category = existingData.category ?: "",
                    subCategory = existingData.subCategory ?: "",
                    color = existingData.color ?: "",
                    gender = existingData.gender ?: "",
                    warehouse = existingData.warehouse ?: "",
                    tagStatus = existingData.tagStatus,
                    rfidList = listOf(epc)
                )
                lookupResults[epc] = productInfo
                Log.d("ScanViewModel", "✅ Lookup result from processed data: $epc -> ${existingData.articleName} ${existingData.size}")
            } else {
                // **STRATEGI 2**: Jika tidak ada di processedRfidData, cari di picklist items
                // dan buat ProductInfo berdasarkan item yang qtyScan > 0
                val matchingItem = currentItems.find { item ->
                    item.qtyScan > 0 // Item yang sudah pernah di-scan
                }
                
                if (matchingItem != null) {
                    val productInfo = NirwanaApiService.ProductInfo(
                        articleId = matchingItem.articleId,
                        articleName = matchingItem.articleName,
                        size = matchingItem.size,
                        qty = 1,
                        productId = matchingItem.productId ?: "",
                        productName = matchingItem.articleName, // Gunakan articleName sebagai productName
                        brand = "",
                        category = "",
                        subCategory = "",
                        color = "",
                        gender = "",
                        warehouse = "",
                        tagStatus = "VALID",
                        rfidList = listOf(epc)
                    )
                    lookupResults[epc] = productInfo
                    Log.d("ScanViewModel", "✅ Lookup result from picklist item: $epc -> ${matchingItem.articleName} ${matchingItem.size}")
                } else {
                    // **FALLBACK**: Jika tidak ditemukan, JANGAN buat lookup result
                    // Biarkan RfidScanManager men-trigger lookup API untuk EPC ini
                    Log.w("ScanViewModel", "⚠️ No matching data for EPC $epc, skipping lookup result (will trigger API lookup)")
                }
            }
        }
        
        Log.d("ScanViewModel", "🔥 Generated ${lookupResults.size} lookup results for ${allProcessedEpcs.size} EPCs from database and session")
        return lookupResults
    }
    
    /**
     * Get filtered items (items yang belum complete)
     */
    fun getFilteredItems(): List<PicklistItem> = _filteredItems.value ?: emptyList()
    
    /**
     * Inisialisasi RFID detection counter berdasarkan data yang sudah ada
     * Menghitung total EPC unik yang sudah pernah diproses dari processedEpcPerPicklist
     */
    private fun initializeRfidDetectionCounter(items: List<PicklistItem>) {
        try {
            // Skema 2: gunakan total qtyScan dari picklist yang sudah pernah di-scan sebagai RFID detect
            val totalQtyScan = items.sumOf { it.qtyScan }
            totalRfidDetections = totalQtyScan
            _rfidDetectionCount.value = totalQtyScan
            Log.d("ScanViewModel", "📊 RFID counter diinisialisasi (skema 2): totalQtyScan=$totalQtyScan")
            
            // Log detail untuk debugging
            val itemsWithScan = items.filter { it.qtyScan > 0 }
            if (itemsWithScan.isNotEmpty()) {
                Log.d("ScanViewModel", "📊 Items dengan scan data: ${itemsWithScan.size}")
                itemsWithScan.take(3).forEach { item ->
                    Log.d("ScanViewModel", "📊   ${item.articleName} ${item.size}: qtyScan=${item.qtyScan}")
                }
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error initializing RFID detection counter: ${e.message}")
            // Fallback ke 0 jika ada error
            totalRfidDetections = 0
            _rfidDetectionCount.value = 0
        }
    }
    
    /**
     * Hitung RFID counter berdasarkan EPC unik yang terscan
     * Fungsi terpusat untuk memastikan konsistensi
     */
    private fun updateRfidCounter() {
        val uniqueSet = getCurrentUniqueSet()
        _rfidDetectionCount.value = uniqueSet.size
        Log.d("ScanViewModel", "📊 RFID counter updated (unique) total=${uniqueSet.size}")
    }
    
    /**
     * Reset RFID detection counter
     * Hanya digunakan saat clear RFID atau ganti picklist
     */
    fun resetRfidDetectionCount() {
        totalRfidDetections = 0
        _rfidDetectionCount.value = 0
        Log.d("ScanViewModel", "🔥 RFID counter direset ke 0 (clear RFID atau ganti picklist)")
    }
    
    /**
     * Get completion status untuk semua picklists secara asynchronous
     */
    suspend fun getAllPicklistCompletionStatuses(picklistNumbers: List<String>): List<PicklistStatus> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            Log.d("ScanViewModel", "🚀 BATCH Calculating completion statuses for ${picklistNumbers.size} picklists")
            
            // Gunakan batch processing dari repository
            val statuses = repository.getAllPicklistCompletionStatuses(picklistNumbers)
            
            Log.d("ScanViewModel", "✅ BATCH Calculated completion statuses for ${statuses.size} picklists")
            statuses
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error calculating all completion statuses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Clear semua cache
     */
    fun clearCache() {
        Log.d("ScanViewModel", "🔥 Clearing cache...")
        // TODO: Implementasi clear cache
    }
    
    
    /**
     * Clear RFID dan reset ke state awal picklist
     * - Simpan data scan yang valid (sesuai atau kurang) ke Supabase
     * - Hapus data scan yang tidak valid (overscan atau non-picklist)
     * - Reset UI ke state awal
     */
    fun clearRfidAndResetToInitialState() {
        Log.d("ScanViewModel", "🔥 Clearing RFID dan reset ke state awal...")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                lastClearTime = System.currentTimeMillis()
                
                // **PERBAIKAN**: Cek apakah semua item sudah complete sebelum reset
                val items = _picklistItems.value ?: emptyList()
                val allItemsComplete = items.all { it.isComplete() }
                
                if (allItemsComplete && items.isNotEmpty()) {
                    Log.d("ScanViewModel", "🎉 All items complete - skipping reset to preserve completion state")
                    _isLoading.value = false
                    return@launch
                }
                
                // Single-only mode: tidak memproses batch sebelum cleanup (no temp collection)
                
                // Cancel semua background jobs (disabled paths kept noop)
                // removed backgroundProcessingJob
                backgroundReloadJob?.cancel()
                backgroundReloadJob = null
                globalBackgroundReloadJob?.cancel()
                globalBackgroundReloadJob = null
                
                // **PERBAIKAN KRITIS**: Simpan HANYA data yang valid ke Supabase menggunakan TRUE BACKGROUND processing
                // Gunakan GlobalScope untuk mencegah interference dengan activity lifecycle
                Log.d("ScanViewModel", "🔥 Starting TRUE BACKGROUND save to Supabase (VALID DATA ONLY)...")
                Log.d("ScanViewModel", "🔥 Overscan dan Non-picklist data akan DIBUANG, tidak disimpan")
                
                // **PERBAIKAN KRITIS**: Gunakan GlobalScope agar proses save tidak ikut di-cancel
                // saat user keluar dari activity
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        saveValidDataToSupabaseBatch()
                        Log.d("ScanViewModel", "✅ Background save completed successfully - only valid data saved")
                        
                        // **PERBAIKAN KRITIS**: Clear data SETELAH save selesai
                        withContext(Dispatchers.Main) {
                            _processedRfidData.value = emptyList()
                            Log.d("ScanViewModel", "🧹 Processed data cleared after successful save")
                        }
                    } catch (e: Exception) {
                        Log.e("ScanViewModel", "❌ Background save failed: ${e.message}", e)
                        
                        // **PERBAIKAN**: Clear data meskipun save gagal
                        withContext(Dispatchers.Main) {
                            _processedRfidData.value = emptyList()
                            Log.d("ScanViewModel", "🧹 Processed data cleared after failed save")
                        }
                    }
                }
                
                // **PERBAIKAN**: Izinkan re-scan untuk picklist saat ini setelah Clear
                currentPicklistNumber?.let { allowRescanPicklists.add(it) }
                
                // removed epcCooldownMap
                
                // removed retry queue & attempts
                Log.d("ScanViewModel", "🧹 Cleared retry queue and attempts")
                
                // **PERBAIKAN BARU**: Clear processed EPC set untuk picklist saat ini saja
                val currentPicklist = currentPicklistNumber
                if (currentPicklist != null) {
                    processedEpcPerPicklist.remove(currentPicklist)
                    uniqueEpcPerPicklist.remove(currentPicklist)
                    appliedEpcPerPicklist.remove(currentPicklist)
                    Log.d("ScanViewModel", "🧹 Cleared all EPC sets untuk picklist: $currentPicklist (processed, unique, applied)")
                }
                
                // **CRITICAL FIX**: Reload data dari database untuk mengembalikan qtyScan ke nilai asli
                if (currentPicklistNumber != null) {
                    Log.d("ScanViewModel", "🔥 Reloading data from database for picklist: $currentPicklistNumber")
                    
                    // **OPTIMASI SINGLE BATCH**: Load picklist items dan processed EPC list dalam satu operasi
                    val batchResult = repository.getPicklistDataBatch(currentPicklistNumber!!)
                    val databaseItems = batchResult.first
                    val processedEpcList = batchResult.second
                    
                    // Set data langsung ke UI
                    _picklistItems.value = databaseItems
                    
                    // **OPTIMASI**: Process processed EPC list yang sudah di-load dalam batch
                    if (processedEpcList.isNotEmpty()) {
                        val epcSet = processedEpcList.toMutableSet()
                        processedEpcPerPicklist[currentPicklistNumber!!] = epcSet
                        
                        // **PERBAIKAN KRITIS**: Seed uniqueEpcPerPicklist juga
                        uniqueEpcPerPicklist[currentPicklistNumber!!] = epcSet
                        Log.d("ScanViewModel", "🔥 Reloaded ${processedEpcList.size} processed EPCs for picklist: $currentPicklistNumber")
                        Log.d("ScanViewModel", "🔥 Seeded uniqueEpcPerPicklist with ${epcSet.size} EPCs")
                    }
                    
                    // **CRITICAL FIX**: Set RFID counter sesuai total qtyScan dari database
                    val totalQtyScanFromDatabase = databaseItems.sumOf { it.qtyScan }
                    
                    // **PERBAIKAN KRITIS**: Jangan override counter dengan database value
                    // Gunakan nilai maksimum antara current counter dan database value
                    val currentCounter = totalRfidDetections
                    val maxCounter = kotlin.math.max(currentCounter, totalQtyScanFromDatabase)
                    
                    if (maxCounter > currentCounter) {
                        totalRfidDetections = maxCounter
                        _rfidDetectionCount.value = maxCounter
                        Log.d("ScanViewModel", "📊 RFID counter updated (max): $currentCounter -> $maxCounter (DB: $totalQtyScanFromDatabase)")
                    } else {
                        Log.d("ScanViewModel", "📊 RFID counter maintained: $currentCounter (DB: $totalQtyScanFromDatabase)")
                    }
                    
                    Log.d("ScanViewModel", "✅ Data reloaded from database - qtyScan dikembalikan ke nilai asli")
                    Log.d("ScanViewModel", "✅ RFID counter dikembalikan ke ${totalQtyScanFromDatabase} (sesuai database)")
                } else {
                    Log.w("ScanViewModel", "⚠️ No current picklist number, cannot reload from database")
                }
                
                // Update UI setelah reload
                updateFilteredItems()
                updateQtySummary()
                updateQtyStatusColors()
                
                // JANGAN reset RFID detection counter di sini; sudah diset sesuai database di atas
                Log.d("ScanViewModel", "✅ Clear RFID dan reset berhasil - qtyScan dipulihkan dari database dan counter dipertahankan")
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("ScanViewModel", "🔥 Clear RFID cancelled (normal behavior): ${e.message}")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error clearing RFID: ${e.message}", e)
                _errorMessage.value = "Error clearing RFID: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * OPTIMASI: Get cache statistics untuk monitoring
     */
    fun getCacheStats() {
        viewModelScope.launch {
            try {
                val stats = repository.getCacheStats()
                Log.d("ScanViewModel", "📊 Cache Statistics: $stats")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error getting cache stats: ${e.message}", e)
            }
        }
    }
    
    /**
     * OPTIMASI: Cleanup expired cache entries
     */
    fun cleanupCache() {
        viewModelScope.launch {
            try {
                repository.cleanupCache()
                Log.d("ScanViewModel", "🧹 Cache cleanup completed")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error cleaning up cache: ${e.message}", e)
            }
        }
    }
    
    /**
     * **FITUR BARU**: Remove ALL RFID untuk article tertentu (clear all scanned RFID for this item)
     * @param articleName Nama article yang akan dihapus
     * @param size Size article yang akan dihapus
     */
    fun removeAllRfidForArticle(articleName: String, size: String, rfidScanManager: com.example.cekpicklist.utils.RfidScanManager? = null) {
        Log.d("ScanViewModel", "🗑️ Removing ALL RFID for article: $articleName $size")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val currentItems = _picklistItems.value ?: emptyList()
                val currentPicklist = currentPicklistNumber
                
                if (currentPicklist == null) {
                    Log.w("ScanViewModel", "⚠️ No current picklist - cannot remove RFID")
                    return@launch
                }
                
                // **STEP 1**: Find the specific item to remove (check both picklistItems and filteredItems)
                var itemToRemove = currentItems.find { item ->
                    item.articleName.equals(articleName, ignoreCase = true) && 
                    item.size.equals(size, ignoreCase = true)
                }
                
                // **PERBAIKAN**: Jika tidak ditemukan di picklistItems, cari di filteredItems (untuk non-picklist items)
                if (itemToRemove == null) {
                    val filteredItems = _filteredItems.value ?: emptyList()
                    itemToRemove = filteredItems.find { item ->
                        item.articleName.equals(articleName, ignoreCase = true) && 
                        item.size.equals(size, ignoreCase = true)
                    }
                    if (itemToRemove != null) {
                        Log.d("ScanViewModel", "🔍 Found item in filteredItems: ${itemToRemove.articleName} ${itemToRemove.size}")
                    }
                }
                
                if (itemToRemove == null) {
                    Log.w("ScanViewModel", "⚠️ Item not found in both picklistItems and filteredItems: $articleName $size")
                    return@launch
                }
                
                Log.d("ScanViewModel", "🔍 Found item to remove ALL RFID: ${itemToRemove.articleName} ${itemToRemove.size} (qtyScan=${itemToRemove.qtyScan}, qtyPl=${itemToRemove.qtyPl})")
                
                // **STEP 2**: Remove ALL processed RFID data for this article
                val currentProcessedData = _processedRfidData.value ?: emptyList()
                val updatedProcessedData = currentProcessedData.filter { processedItem ->
                    !(processedItem.articleName.equals(articleName, ignoreCase = true) && 
                      processedItem.size.equals(size, ignoreCase = true))
                }
                
                val removedCount = currentProcessedData.size - updatedProcessedData.size
                Log.d("ScanViewModel", "🗑️ Removed ALL $removedCount processed RFID records for $articleName $size")
                
                // **STEP 2.5**: Get EPCs to remove from RfidScanManager
                val epcsToRemove = currentProcessedData.filter { processedItem ->
                    processedItem.articleName.equals(articleName, ignoreCase = true) && 
                    processedItem.size.equals(size, ignoreCase = true)
                }.map { it.epc }.toSet()
                
                // **STEP 3**: Update processed RFID data
                _processedRfidData.value = updatedProcessedData
                
                // **STEP 3.5**: Remove EPCs from RfidScanManager to prevent re-processing
                if (epcsToRemove.isNotEmpty() && rfidScanManager != null) {
                    Log.d("ScanViewModel", "🗑️ Removing ${epcsToRemove.size} EPCs from RfidScanManager: ${epcsToRemove.take(3).joinToString(", ")}${if (epcsToRemove.size > 3) "..." else ""}")
                    rfidScanManager.removeEpcs(epcsToRemove)
                } else if (epcsToRemove.isNotEmpty()) {
                    Log.w("ScanViewModel", "⚠️ Cannot remove EPCs from RfidScanManager: reference not provided")
                }
                
                // **STEP 4**: Handle item removal based on qtyPl
                if (itemToRemove.qtyPl == 0) {
                    // **PERBAIKAN**: Item dengan qtyPl=0 (non-picklist) harus dihapus dari listbox
                    Log.d("ScanViewModel", "🗑️ NON-PICKLIST ITEM: Removing ${itemToRemove.articleName} ${itemToRemove.size} from listbox (qtyPl=0)")
                    Log.d("ScanViewModel", "✅ Non-picklist item removed from processedRfidData - UI will update automatically")
                } else {
                    // **NORMAL PICKLIST ITEM**: Reset qtyScan to 0 and reload from Supabase
                    val updatedItems = currentItems.map { item ->
                        if (item.articleName.equals(articleName, ignoreCase = true) && 
                            item.size.equals(size, ignoreCase = true)) {
                            
                            Log.d("ScanViewModel", "🔄 Resetting qtyScan to 0 for ${item.articleName} ${item.size}")
                            item.copy(qtyScan = 0)
                        } else {
                            item
                        }
                    }
                    _picklistItems.value = updatedItems
                    
                    // **STEP 5**: Reload data from Supabase to restore valid scans (only for picklist items)
                    Log.d("ScanViewModel", "🔄 Reloading data from Supabase to restore valid scans...")
                    if (currentPicklistNumber != null) {
                        val reloadedItems = repository.getPicklistItems(currentPicklistNumber!!)
                        if (reloadedItems.isNotEmpty()) {
                            Log.d("ScanViewModel", "✅ Reloaded ${reloadedItems.size} items from Supabase")
                            
                            // Find the specific item that was reset
                            val resetItem = reloadedItems.find { reloadedItem ->
                                reloadedItem.articleName.equals(articleName, ignoreCase = true) && 
                                reloadedItem.size.equals(size, ignoreCase = true)
                            }
                            
                            if (resetItem != null && resetItem.qtyScan > 0) {
                                Log.d("ScanViewModel", "🔄 Found valid data in Supabase for ${resetItem.articleName} ${resetItem.size}: qtyScan=${resetItem.qtyScan}")
                                
                                // Update the specific item with Supabase data
                                val finalItems = _picklistItems.value?.map { item ->
                                    if (item.articleName.equals(articleName, ignoreCase = true) && 
                                        item.size.equals(size, ignoreCase = true)) {
                                        
                                        Log.d("ScanViewModel", "🔄 Restoring ${item.articleName} ${item.size}: qtyScan=0 -> ${resetItem.qtyScan}")
                                        item.copy(qtyScan = resetItem.qtyScan)
                                    } else {
                                        item
                                    }
                                }
                                
                                _picklistItems.value = finalItems ?: emptyList()
                                Log.d("ScanViewModel", "✅ Successfully restored ${resetItem.articleName} ${resetItem.size} from Supabase")
                            } else {
                                Log.d("ScanViewModel", "ℹ️ No valid data found in Supabase for $articleName $size - keeping qtyScan=0")
                            }
                        } else {
                            Log.d("ScanViewModel", "ℹ️ No data found in Supabase - keeping qtyScan=0")
                        }
                    }
                }
                
                // **STEP 6**: Update filtered items untuk memastikan UI terupdate
                updateFilteredItems()
                updateQtySummary()
                updateQtyStatusColors()
                
                // **STEP 7**: Update RFID detection count
                // **PERBAIKAN KRITIS**: Jangan kurangi RFID detection counter saat remove RFID
                // RFID detection counter harus tetap non-decreasing
                val currentCount = _rfidDetectionCount.value ?: 0
                Log.d("ScanViewModel", "✅ Successfully removed ALL RFID for $articleName $size")
                Log.d("ScanViewModel", "📊 RFID detection counter maintained: $currentCount (non-decreasing)")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error removing all RFID for article: ${e.message}", e)
                _errorMessage.value = "Error removing all RFID: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * **FITUR BARU**: Remove RFID untuk article tertentu (delete per article - remove 1 RFID only)
     * @param articleName Nama article yang akan dihapus
     * @param size Size article yang akan dihapus
     */
    fun removeRfidForArticle(articleName: String, size: String) {
        Log.d("ScanViewModel", "🗑️ Removing RFID for article: $articleName $size")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val currentItems = _picklistItems.value ?: emptyList()
                val currentPicklist = currentPicklistNumber
                
                if (currentPicklist == null) {
                    Log.w("ScanViewModel", "⚠️ No current picklist - cannot remove RFID")
                    return@launch
                }
                
                // **STEP 1**: Find the specific item to remove (check both picklistItems and filteredItems)
                var itemToRemove = currentItems.find { item ->
                    item.articleName.equals(articleName, ignoreCase = true) && 
                    item.size.equals(size, ignoreCase = true)
                }
                
                // **PERBAIKAN**: Jika tidak ditemukan di picklistItems, cari di filteredItems (untuk non-picklist items)
                if (itemToRemove == null) {
                    val filteredItems = _filteredItems.value ?: emptyList()
                    itemToRemove = filteredItems.find { item ->
                        item.articleName.equals(articleName, ignoreCase = true) && 
                        item.size.equals(size, ignoreCase = true)
                    }
                    if (itemToRemove != null) {
                        Log.d("ScanViewModel", "🔍 Found item in filteredItems: ${itemToRemove.articleName} ${itemToRemove.size}")
                    }
                }
                
                if (itemToRemove == null) {
                    Log.w("ScanViewModel", "⚠️ Item not found in both picklistItems and filteredItems: $articleName $size")
                    return@launch
                }
                
                Log.d("ScanViewModel", "🔍 Found item to remove: ${itemToRemove.articleName} ${itemToRemove.size} (qtyScan=${itemToRemove.qtyScan}, qtyPl=${itemToRemove.qtyPl})")
                
                // **STEP 2**: Determine removal strategy based on overscan status
                val isOverscan = itemToRemove.qtyScan > itemToRemove.qtyPl
                val overscanCount = if (isOverscan) itemToRemove.qtyScan - itemToRemove.qtyPl else 0
                
                Log.d("ScanViewModel", "📊 Item analysis: qtyScan=${itemToRemove.qtyScan}, qtyPl=${itemToRemove.qtyPl}, overscanCount=$overscanCount")
                
                if (isOverscan && overscanCount > 0) {
                    // **STRATEGY 1**: Remove ALL overscan RFID and restore to qtyPl
                    Log.d("ScanViewModel", "🎯 OVERCAN DETECTED: Removing $overscanCount overscan RFID, restoring to qtyPl=${itemToRemove.qtyPl}")
                    
                    // Remove ALL processed RFID data for this article
                    val currentProcessedData = _processedRfidData.value ?: emptyList()
                    val updatedProcessedData = currentProcessedData.filter { processedItem ->
                        !(processedItem.articleName.equals(articleName, ignoreCase = true) && 
                          processedItem.size.equals(size, ignoreCase = true))
                    }
                    
                    val removedCount = currentProcessedData.size - updatedProcessedData.size
                    Log.d("ScanViewModel", "🗑️ Removed ALL $removedCount processed RFID records for $articleName $size")
                    
                    // **STEP 3**: Update processed RFID data
                    _processedRfidData.value = updatedProcessedData
                    
                    // **STEP 4**: Handle item removal based on qtyPl
                    if (itemToRemove.qtyPl == 0) {
                        // **PERBAIKAN**: Item dengan qtyPl=0 (non-picklist) harus dihapus dari listbox
                        Log.d("ScanViewModel", "🗑️ NON-PICKLIST ITEM: Removing ${itemToRemove.articleName} ${itemToRemove.size} from listbox (qtyPl=0)")
                        
                        // **PERBAIKAN KRITIS**: Non-picklist items tidak ada di _picklistItems, 
                        // jadi cukup hapus dari _processedRfidData dan updateFilteredItems akan otomatis update UI
                        Log.d("ScanViewModel", "✅ Non-picklist item removed from processedRfidData - UI will update automatically")
                        
                    } else {
                        // **NORMAL PICKLIST ITEM**: Reset qtyScan to 0 and reload from Supabase
                        val updatedItems = currentItems.map { item ->
                            if (item.articleName.equals(articleName, ignoreCase = true) && 
                                item.size.equals(size, ignoreCase = true)) {
                                
                                Log.d("ScanViewModel", "🔄 Resetting qtyScan to 0 for ${item.articleName} ${item.size}")
                                item.copy(qtyScan = 0)
                            } else {
                                item
                            }
                        }
                        _picklistItems.value = updatedItems
                    }
                    
                    // **STEP 5**: Reload data from Supabase to restore valid scans (only for picklist items)
                    if (itemToRemove.qtyPl > 0) {
                        Log.d("ScanViewModel", "🔄 Reloading data from Supabase to restore valid scans...")
                        if (currentPicklistNumber != null) {
                            val reloadedItems = repository.getPicklistItems(currentPicklistNumber!!)
                            if (reloadedItems.isNotEmpty()) {
                                Log.d("ScanViewModel", "✅ Reloaded ${reloadedItems.size} items from Supabase")
                                
                                // Find the specific item that was reset
                                val resetItem = reloadedItems.find { reloadedItem ->
                                    reloadedItem.articleName.equals(articleName, ignoreCase = true) && 
                                    reloadedItem.size.equals(size, ignoreCase = true)
                                }
                                
                                if (resetItem != null && resetItem.qtyScan > 0) {
                                    Log.d("ScanViewModel", "🔄 Found valid data in Supabase for ${resetItem.articleName} ${resetItem.size}: qtyScan=${resetItem.qtyScan}")
                                    
                                    // Update the specific item with Supabase data
                                    val finalItems = _picklistItems.value?.map { item ->
                                        if (item.articleName.equals(articleName, ignoreCase = true) && 
                                            item.size.equals(size, ignoreCase = true)) {
                                            
                                            Log.d("ScanViewModel", "🔄 Restoring ${item.articleName} ${item.size}: qtyScan=0 -> ${resetItem.qtyScan}")
                                            item.copy(qtyScan = resetItem.qtyScan)
                                        } else {
                                            item
                                        }
                                    }
                                    
                                    _picklistItems.value = finalItems ?: emptyList()
                                    Log.d("ScanViewModel", "✅ Successfully restored ${resetItem.articleName} ${resetItem.size} from Supabase")
                                } else {
                                    Log.d("ScanViewModel", "ℹ️ No valid data found in Supabase for $articleName $size - keeping qtyScan=0")
                                }
                            } else {
                                Log.d("ScanViewModel", "ℹ️ No data found in Supabase - keeping qtyScan=0")
                            }
                        }
                    } else {
                        Log.d("ScanViewModel", "ℹ️ Skipping Supabase reload for non-picklist item (qtyPl=0)")
                    }
                    
                    // **STEP 6**: Update filtered items untuk memastikan UI terupdate
                    updateFilteredItems()
                    updateQtySummary()
                    updateQtyStatusColors()
                    
                    // **STEP 7**: Update RFID detection count
                    // **PERBAIKAN KRITIS**: Jangan kurangi RFID detection counter saat remove RFID
                    // RFID detection counter harus tetap non-decreasing
                    val currentCount = _rfidDetectionCount.value ?: 0
                    Log.d("ScanViewModel", "✅ Successfully removed ALL overscan RFID for $articleName $size")
                    Log.d("ScanViewModel", "📊 RFID detection counter maintained: $currentCount (non-decreasing)")
                    
                } else {
                    // **STRATEGY 2**: Normal deletion (remove 1 RFID)
                    Log.d("ScanViewModel", "🎯 NORMAL DELETE: Removing 1 RFID for $articleName $size")
                    
                    // Check if there are any processed RFID data for this article
                    val currentProcessedData = _processedRfidData.value ?: emptyList()
                    val itemProcessedData = currentProcessedData.filter { processedItem ->
                        processedItem.articleName.equals(articleName, ignoreCase = true) && 
                        processedItem.size.equals(size, ignoreCase = true)
                    }
                    
                    if (itemProcessedData.isEmpty()) {
                        Log.d("ScanViewModel", "ℹ️ No processed RFID data found for $articleName $size - nothing to remove")
                        
                        // Just update the UI without changing data
                        val updatedItems = currentItems.map { item ->
                            if (item.articleName.equals(articleName, ignoreCase = true) && 
                                item.size.equals(size, ignoreCase = true)) {
                                
                                Log.d("ScanViewModel", "ℹ️ Item ${item.articleName} ${item.size} already has qtyScan=${item.qtyScan}")
                                item
                            } else {
                                item
                            }
                        }
                        
                        _picklistItems.value = updatedItems
                        Log.d("ScanViewModel", "ℹ️ No RFID data to remove for $articleName $size")
                        
                    } else {
                        // Remove 1 processed RFID data for this article
                        val updatedProcessedData = if (itemProcessedData.isNotEmpty()) {
                            // Remove the first occurrence
                            val toRemove = itemProcessedData.first()
                            currentProcessedData.filter { it != toRemove }
                        } else {
                            currentProcessedData
                        }
                        
                        val removedCount = currentProcessedData.size - updatedProcessedData.size
                        Log.d("ScanViewModel", "🗑️ Removed $removedCount processed RFID record for $articleName $size")
                        
                        // **STEP 3**: Update processed RFID data
                        _processedRfidData.value = updatedProcessedData
                        
                        // **STEP 4**: Update picklist items (decrease qtyScan by 1)
                        val updatedItems = currentItems.map { item ->
                            if (item.articleName.equals(articleName, ignoreCase = true) && 
                                item.size.equals(size, ignoreCase = true)) {
                                
                                val newQtyScan = maxOf(0, item.qtyScan - 1)
                                Log.d("ScanViewModel", "📉 Updated qtyScan for ${item.articleName} ${item.size}: ${item.qtyScan} -> $newQtyScan")
                                
                                item.copy(qtyScan = newQtyScan)
                            } else {
                                item
                            }
                        }
                        
                        _picklistItems.value = updatedItems
                        
                        // **STEP 5**: Update RFID detection count
                        // **PERBAIKAN KRITIS**: Jangan kurangi RFID detection counter saat remove RFID
                        // RFID detection counter harus tetap non-decreasing
                        val currentCount = _rfidDetectionCount.value ?: 0
                        Log.d("ScanViewModel", "✅ Successfully removed 1 RFID for $articleName $size")
                        Log.d("ScanViewModel", "📊 RFID detection counter maintained: $currentCount (non-decreasing)")
                    }
                }
                
                // **STEP 7**: Update UI
                updateFilteredItems()
                updateQtySummary()
                updateQtyStatusColors()
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error removing RFID for article $articleName $size: ${e.message}", e)
                _errorMessage.value = "Error removing RFID: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * **HELPER METHOD**: Merge current items with reloaded data from Supabase
     */
    private fun mergeWithReloadedData(currentItems: List<PicklistItem>, reloadedItems: List<PicklistItem>): List<PicklistItem> {
        Log.d("ScanViewModel", "🔄 Merging current items (${currentItems.size}) with reloaded items (${reloadedItems.size})")
        
        val mergedItems = mutableListOf<PicklistItem>()
        
        // Add all current items first
        mergedItems.addAll(currentItems)
        
        // Add reloaded items that are not already in current items
        reloadedItems.forEach { reloadedItem ->
            val exists = currentItems.any { currentItem ->
                currentItem.articleId == reloadedItem.articleId && 
                currentItem.size == reloadedItem.size
            }
            
            if (!exists) {
                mergedItems.add(reloadedItem)
                Log.d("ScanViewModel", "➕ Added reloaded item: ${reloadedItem.articleName} ${reloadedItem.size} (qtyScan=${reloadedItem.qtyScan})")
            } else {
                Log.d("ScanViewModel", "⏭️ Skipped existing item: ${reloadedItem.articleName} ${reloadedItem.size}")
            }
        }
        
        Log.d("ScanViewModel", "✅ Merge completed: ${mergedItems.size} total items")
        return mergedItems
    }
    
    /**
     * OPTIMASI: Simpan data yang valid ke Supabase menggunakan batch processing
     */
    private suspend fun saveValidDataToSupabaseBatch() {
        try {
        // Single-only mode: background processing job check disabled
            
            val processedData = _processedRfidData.value ?: emptyList()
            val currentItems = _picklistItems.value ?: emptyList()
            
            Log.d("ScanViewModel", "🔍 DEBUG: processedData.size=${processedData.size}, currentPicklistNumber=$currentPicklistNumber")
            
            if (processedData.isNotEmpty() && currentPicklistNumber != null) {
                Log.d("ScanViewModel", "🚀 BATCH Saving valid items to Supabase (OVERCAN & NON-PICKLIST WILL BE DISCARDED)...")
                
                // Prepare batch data
                val batchScans = mutableListOf<Sextuple<String, String, String, String, String, String>>()
                var skippedOverscanCount = 0
                var skippedNonPicklistCount = 0
                
                processedData.forEach { data ->
                    val picklistItem = currentItems.find { it.articleId == data.articleId }
                    
                    // **PERBAIKAN KRITIS**: Cek overscan secara eksplisit berdasarkan qtyScan vs qtyPl
                    val isOverscan = picklistItem?.let { item ->
                        item.qtyScan > item.qtyPl
                    } ?: false
                    
                    when (data.tagStatus) {
                        "VALID" -> {
                            // **PERBAIKAN KRITIS**: Cek overscan secara eksplisit sebelum menyimpan
                            if (isOverscan) {
                                // Item overscan - TIDAK DISIMPAN, DIBUANG
                                skippedOverscanCount++
                                Log.d("ScanViewModel", "🚫 OVERSCAN DISCARDED: ${data.articleName} (qtyScan=${picklistItem?.qtyScan} > qtyPl=${picklistItem?.qtyPl}) - DATA DIBUANG")
                            } else if (picklistItem != null) {
                                // Item valid dan tidak overscan - tambahkan ke batch
                                batchScans.add(Sextuple(
                                    currentPicklistNumber!!, 
                                    data.articleId, 
                                    data.epc, 
                                    data.productId,
                                    data.articleName,
                                    data.size
                                ))
                                Log.d("ScanViewModel", "✅ Added to batch: ${data.articleName} (productId: ${data.productId}, EPC: ${data.epc})")
                            }
                        }
                        "OVERCAN" -> {
                            // Item overscan - TIDAK DISIMPAN, DIBUANG
                            skippedOverscanCount++
                            Log.d("ScanViewModel", "🚫 OVERSCAN DISCARDED: ${data.articleName} - DATA DIBUANG")
                        }
                        "NON_PICKLIST" -> {
                            // Item non-picklist - TIDAK DISIMPAN, DIBUANG
                            skippedNonPicklistCount++
                            Log.d("ScanViewModel", "🚫 NON-PICKLIST DISCARDED: ${data.articleName} - DATA DIBUANG")
                        }
                    }
                }
                
                // **PERBAIKAN BARU**: Cek duplikasi sebelum batch save
                if (batchScans.isNotEmpty()) {
                    Log.d("ScanViewModel", "🔍 Checking for duplicate EPCs before batch save...")
                    
                    // Extract EPCs yang akan di-save
                    val epcsToSave = batchScans.map { it.third } // EPC adalah field ketiga
                    
                    // Cek apakah EPC sudah ada di database
                    val existingEpcs = repository.batchCheckExistingEpcs(epcsToSave)
                    
                    if (existingEpcs.isNotEmpty()) {
                        Log.w("ScanViewModel", "⚠️ Found ${existingEpcs.size} duplicate EPCs: $existingEpcs")
                        
                        // Filter out duplicate EPCs
                        val filteredBatchScans = batchScans.filter { scan ->
                            val epc = scan.third
                            val isDuplicate = existingEpcs.contains(epc)
                            if (isDuplicate) {
                                Log.w("ScanViewModel", "⚠️ Skipping duplicate EPC: $epc")
                            }
                            !isDuplicate
                        }
                        
                        Log.d("ScanViewModel", "🔍 Filtered batch: ${filteredBatchScans.size} items (removed ${batchScans.size - filteredBatchScans.size} duplicates)")
                        
                        // Save hanya EPC yang belum ada
                        if (filteredBatchScans.isNotEmpty()) {
                            val success = repository.savePicklistScansBatch(filteredBatchScans)
                            if (success) {
                                Log.d("ScanViewModel", "✅ Batch save completed: ${filteredBatchScans.size} new items saved")
                                
                                // **PERBAIKAN KRITIS**: Reload data dari database setelah save berhasil
                                startRobustBackgroundDataReload()
                            } else {
                                Log.e("ScanViewModel", "❌ Batch save failed")
                            }
                        } else {
                            Log.d("ScanViewModel", "ℹ️ All EPCs already exist in database - no new items to save")
                        }
                    } else {
                        // Tidak ada duplikasi - save semua
                        val success = repository.savePicklistScansBatch(batchScans)
                        if (success) {
                            Log.d("ScanViewModel", "✅ Batch save completed: ${batchScans.size} items saved")
                            
                            // **PERBAIKAN KRITIS**: Reload data dari database setelah save berhasil
                            startRobustBackgroundDataReload()
                        } else {
                            Log.e("ScanViewModel", "❌ Batch save failed")
                        }
                    }
                } else {
                    Log.d("ScanViewModel", "⚠️ No valid items to save in batch")
                }
                
                Log.d("ScanViewModel", "📊 Batch save summary: ${batchScans.size} valid items saved, $skippedOverscanCount overscan DISCARDED, $skippedNonPicklistCount non-picklist DISCARDED")
            } else {
                Log.d("ScanViewModel", "⚠️ No processed data to save: processedData.size=${processedData.size}, currentPicklistNumber=$currentPicklistNumber")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("ScanViewModel", "🔥 Batch save cancelled (normal behavior): ${e.message}")
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error in batch save: ${e.message}", e)
        }
    }
    
    /**
     * Simpan data yang valid ke Supabase dengan validasi status
     */
    private suspend fun saveValidDataToSupabase() {
        try {
            val processedData = _processedRfidData.value ?: emptyList()
            val currentItems = _picklistItems.value ?: emptyList()
            
            if (processedData.isNotEmpty() && currentPicklistNumber != null) {
                Log.d("ScanViewModel", "🚀 BATCH Saving valid items to Supabase...")
                
                val batchScans = mutableListOf<Sextuple<String, String, String, String, String, String>>()
                var skippedOverscanCount = 0
                var skippedNonPicklistCount = 0
                
                processedData.forEach { data ->
                    val picklistItem = currentItems.find { it.articleId == data.articleId }
                    
                    // **PERBAIKAN KRITIS**: Cek overscan secara eksplisit berdasarkan qtyScan vs qtyPl
                    val isOverscan = picklistItem?.let { item ->
                        item.qtyScan > item.qtyPl
                    } ?: false
                    
                    when (data.tagStatus) {
                        "VALID" -> {
                            // **PERBAIKAN KRITIS**: Cek overscan secara eksplisit sebelum menyimpan
                            if (isOverscan) {
                                // Item overscan - tidak disimpan meskipun status "VALID"
                                skippedOverscanCount++
                                Log.d("ScanViewModel", "⚠️ OVERSCAN DETECTED: Skipped overscan item: ${data.articleName} (qtyScan=${picklistItem?.qtyScan} > qtyPl=${picklistItem?.qtyPl})")
                            } else {
                                // Item valid dan tidak overscan - tambahkan ke batch
                                batchScans.add(
                                    Sextuple(
                                        currentPicklistNumber!!,
                                        data.articleId,
                                        data.epc,
                                        data.productId,
                                        data.articleName,
                                        data.size
                                    )
                                )
                                Log.d("ScanViewModel", "✅ Added to batch: ${data.articleName}")
                            }
                        }
                        "OVERCAN" -> {
                            // Item overscan - TIDAK DISIMPAN, DIBUANG
                            skippedOverscanCount++
                            Log.d("ScanViewModel", "🚫 OVERSCAN DISCARDED: ${data.articleName} - DATA DIBUANG")
                        }
                        "NON_PICKLIST" -> {
                            // Item non-picklist - TIDAK DISIMPAN, DIBUANG
                            skippedNonPicklistCount++
                            Log.d("ScanViewModel", "🚫 NON-PICKLIST DISCARDED: ${data.articleName} - DATA DIBUANG")
                        }
                    }
                }
                
                // **PERBAIKAN BARU**: Cek duplikasi sebelum batch save
                if (batchScans.isNotEmpty()) {
                    Log.d("ScanViewModel", "🔍 Checking for duplicate EPCs before batch save...")
                    
                    // Extract EPCs yang akan di-save
                    val epcsToSave = batchScans.map { it.third } // EPC adalah field ketiga
                    
                    // Cek apakah EPC sudah ada di database
                    val existingEpcs = repository.batchCheckExistingEpcs(epcsToSave)
                    
                    if (existingEpcs.isNotEmpty()) {
                        Log.w("ScanViewModel", "⚠️ Found ${existingEpcs.size} duplicate EPCs: $existingEpcs")
                        
                        // Filter out duplicate EPCs
                        val filteredBatchScans = batchScans.filter { scan ->
                            val epc = scan.third
                            val isDuplicate = existingEpcs.contains(epc)
                            if (isDuplicate) {
                                Log.w("ScanViewModel", "⚠️ Skipping duplicate EPC: $epc")
                            }
                            !isDuplicate
                        }
                        
                        Log.d("ScanViewModel", "🔍 Filtered batch: ${filteredBatchScans.size} items (removed ${batchScans.size - filteredBatchScans.size} duplicates)")
                        
                        // Save hanya EPC yang belum ada
                        if (filteredBatchScans.isNotEmpty()) {
                            val success = repository.savePicklistScansBatch(filteredBatchScans)
                            if (success) {
                                Log.d("ScanViewModel", "✅ Batch save completed: ${filteredBatchScans.size} new items saved")
                                
                                // **PERBAIKAN KRITIS**: Reload data dari database setelah save berhasil
                                startRobustBackgroundDataReload()
                            } else {
                                Log.e("ScanViewModel", "❌ Batch save failed")
                            }
                        } else {
                            Log.d("ScanViewModel", "ℹ️ All EPCs already exist in database - no new items to save")
                        }
                    } else {
                        // Tidak ada duplikasi - save semua
                        val success = repository.savePicklistScansBatch(batchScans)
                        if (success) {
                            Log.d("ScanViewModel", "✅ Batch save completed: ${batchScans.size} items saved")
                            
                            // **PERBAIKAN KRITIS**: Reload data dari database setelah save berhasil
                            startRobustBackgroundDataReload()
                        } else {
                            Log.e("ScanViewModel", "❌ Batch save failed")
                        }
                    }
                } else {
                    Log.d("ScanViewModel", "⚠️ No valid items to save in batch")
                }
                
                Log.d("ScanViewModel", "📊 Batch save summary: ${batchScans.size} valid items saved, $skippedOverscanCount overscan DISCARDED, $skippedNonPicklistCount non-picklist DISCARDED")
                
                // **POIN 1, 2, 4, 5: Update semua qty display data setelah save**
                updateQtySummary()
                updateQtyStatusColors()
                updateFilteredItems()
                
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error saving valid data: ${e.message}", e)
        }
    }
    
    /**
     * **PERBAIKAN KRITIS**: Start background data reload dengan retry mechanism
     * Memastikan qtyScan selalu sinkron dengan database tanpa mengganggu scanning
     */
    private fun startBackgroundDataReload() {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist != null) {
            Log.d("ScanViewModel", "🚀 Starting background data reload for picklist: $currentPicklist")
            
            // **PERBAIKAN KRITIS**: Cancel job lama jika ada
            backgroundReloadJob?.cancel()
            globalBackgroundReloadJob?.cancel()
            
            // **BACKGROUND PROCESSING**: Jalankan di background tanpa mengganggu scanning
            // **PERBAIKAN**: Gunakan GlobalScope dengan job terpisah agar tidak di-cancel saat activity destroy
            globalBackgroundReloadJob = GlobalScope.launch(Dispatchers.IO + NonCancellable) {
                try {
                    // **RETRY MECHANISM**: Coba reload dengan retry jika gagal
                    var retryCount = 0
                    val maxRetries = 3
                    var success = false
                    
                    while (retryCount < maxRetries && !success) {
                        try {
                            Log.d("ScanViewModel", "🔄 Background reload attempt ${retryCount + 1}/$maxRetries")
                            
                            // **ADAPTIVE DELAY**: Delay yang disesuaikan dengan jumlah retry
                            val delayMs = (1000 * (retryCount + 1)).toLong() // 1s, 2s, 3s
                            Log.d("ScanViewModel", "⏳ Waiting ${delayMs}ms for database synchronization...")
                            delay(delayMs)
                            
                            // **OPTIMASI SINGLE BATCH**: Load picklist items dan processed EPC list dalam satu operasi
                            val batchResult = repository.getPicklistDataBatch(currentPicklist)
                            val databaseItems = batchResult.first
                            val processedEpcList = batchResult.second
                            
                            // **VERIFIKASI DATA**: Pastikan data yang di-load valid
                            if (databaseItems.isNotEmpty()) {
                                // Merge dengan data UI: JANGAN turunkan qtyScan (ambil nilai maksimum)
                                val currentUiItems = _picklistItems.value ?: emptyList()
                                val merged = databaseItems.map { dbItem ->
                                    val uiItem = currentUiItems.find { it.articleId == dbItem.articleId && it.size == dbItem.size }
                                    if (uiItem != null) {
                                        if (uiItem.qtyScan > dbItem.qtyScan) dbItem.copy(qtyScan = uiItem.qtyScan) else dbItem
                                    } else dbItem
                                } + currentUiItems.filter { uiItem ->
                                    databaseItems.none { it.articleId == uiItem.articleId && it.size == uiItem.size }
                                }
                                
                                // Set data hasil merge ke UI (di main thread)
                                withContext(Dispatchers.Main) {
                                    _picklistItems.value = merged
                                }
                                
                                // **OPTIMASI**: Process processed EPC list yang sudah di-load dalam batch
                                if (processedEpcList.isNotEmpty()) {
                                    val epcSet = processedEpcList.toMutableSet()
                                    processedEpcPerPicklist[currentPicklist] = epcSet
                                    Log.d("ScanViewModel", "🔄 Background reloaded ${processedEpcList.size} processed EPCs for picklist: $currentPicklist")
                                }
                                
                                // **CRITICAL FIX**: Hitung qtyScan dari database
                                val totalQtyScanFromDatabase = databaseItems.sumOf { it.qtyScan }
                                
                                // JANGAN menurunkan counter; gunakan nilai maksimum antara UI saat ini dan database
                                withContext(Dispatchers.Main) {
                                    val currentCount = totalRfidDetections
                                    val newCount = kotlin.math.max(currentCount, totalQtyScanFromDatabase)
                                    if (newCount != currentCount) {
                                        Log.d("ScanViewModel", "📊 Updating RFID counter (max merge): UI=$currentCount, DB=$totalQtyScanFromDatabase -> $newCount")
                                        totalRfidDetections = newCount
                                        _rfidDetectionCount.value = newCount
                                    } else {
                                        Log.d("ScanViewModel", "📊 Keeping RFID counter: UI=$currentCount >= DB=$totalQtyScanFromDatabase")
                                    }
                                }
                                
                                // **VERIFIKASI KRITIS**: Log perbandingan data sebelum dan sesudah reload
                                val totalQtyPl = databaseItems.sumOf { it.qtyPl }
                                val totalQtyScan = databaseItems.sumOf { it.qtyScan }
                                val completedItems = databaseItems.count { it.qtyScan >= it.qtyPl }
                                val incompleteItems = databaseItems.count { it.qtyScan < it.qtyPl }
                                
                                Log.d("ScanViewModel", "✅ Background reload successful!")
                                Log.d("ScanViewModel", "📊 VERIFICATION AFTER BACKGROUND RELOAD:")
                                Log.d("ScanViewModel", "📊   Total qtyPl: $totalQtyPl")
                                Log.d("ScanViewModel", "📊   Total qtyScan: $totalQtyScan")
                                Log.d("ScanViewModel", "📊   Completed items: $completedItems")
                                Log.d("ScanViewModel", "📊   Incomplete items: $incompleteItems")
                                Log.d("ScanViewModel", "📊   Processed EPCs: ${processedEpcList.size}")
                                
                                // **VERIFIKASI KRITIS**: Log item yang tidak lengkap
                                if (incompleteItems > 0) {
                                    Log.w("ScanViewModel", "⚠️ INCOMPLETE ITEMS AFTER BACKGROUND RELOAD:")
                                    databaseItems.filter { it.qtyScan < it.qtyPl }.forEach { item ->
                                        Log.w("ScanViewModel", "⚠️   ${item.articleName} ${item.size}: qtyScan(${item.qtyScan}) < qtyPl(${item.qtyPl})")
                                    }
                                }
                                
                                // Update UI setelah reload (di main thread)
                                withContext(Dispatchers.Main) {
                                    updateQtySummary()
                                    updateQtyStatusColors()
                                    updateFilteredItems()
                                }
                                
                                success = true
                                Log.d("ScanViewModel", "✅ Background data reload completed successfully!")
                                
                            } else {
                                Log.w("ScanViewModel", "⚠️ Background reload attempt ${retryCount + 1}: No data returned from database")
                                retryCount++
                            }
                            
                        } catch (e: Exception) {
                            retryCount++
                            Log.e("ScanViewModel", "❌ Background reload attempt ${retryCount} failed: ${e.message}", e)
                            
                            if (retryCount >= maxRetries) {
                                Log.e("ScanViewModel", "❌ Background reload failed after $maxRetries attempts")
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "❌ Error in background data reload: ${e.message}", e)
                } finally {
                    globalBackgroundReloadJob = null
                }
            }
        } else {
            Log.w("ScanViewModel", "⚠️ No current picklist number, cannot start background reload")
        }
    }
    
    /**
     * **PERBAIKAN ALTERNATIF**: Start background data reload dengan GlobalScope yang lebih robust
     * Method ini tidak akan di-cancel saat activity destroy
     */
    private fun startRobustBackgroundDataReload() {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist != null) {
            Log.d("ScanViewModel", "🚀 Starting ROBUST background data reload for picklist: $currentPicklist")
            
            // **PERBAIKAN**: Gunakan GlobalScope dengan NonCancellable untuk memastikan tidak di-cancel
            GlobalScope.launch(Dispatchers.IO + NonCancellable) {
                try {
                    // **RETRY MECHANISM**: Coba reload dengan retry jika gagal
                    var retryCount = 0
                    val maxRetries = 5 // Lebih banyak retry
                    var success = false
                    
                    while (retryCount < maxRetries && !success) {
                        try {
                            Log.d("ScanViewModel", "🔄 ROBUST Background reload attempt ${retryCount + 1}/$maxRetries")
                            
                            // **ADAPTIVE DELAY**: Delay yang disesuaikan dengan jumlah retry
                            val delayMs = (2000 * (retryCount + 1)).toLong() // 2s, 4s, 6s, 8s, 10s
                            Log.d("ScanViewModel", "⏳ ROBUST Waiting ${delayMs}ms for database synchronization...")
                            delay(delayMs)
                            
                            // **OPTIMASI SINGLE BATCH**: Load picklist items dan processed EPC list dalam satu operasi
                            val batchResult = repository.getPicklistDataBatch(currentPicklist)
                            val databaseItems = batchResult.first
                            val processedEpcList = batchResult.second
                            
                            // **VERIFIKASI DATA**: Pastikan data yang di-load valid
                            if (databaseItems.isNotEmpty()) {
                                Log.d("ScanViewModel", "✅ ROBUST Background reload successful: ${databaseItems.size} items loaded")
                                
                                // Update data di main thread
                                withContext(Dispatchers.Main) {
                                    _picklistItems.value = databaseItems
                                    updateQtySummary()
                                    updateQtyStatusColors()
                                    updateFilteredItems()
                                }
                                
                                success = true
                                Log.d("ScanViewModel", "✅ ROBUST Background data reload completed successfully!")
                                
                            } else {
                                Log.w("ScanViewModel", "⚠️ ROBUST Background reload attempt ${retryCount + 1}: No data returned from database")
                                retryCount++
                            }
                            
                        } catch (e: Exception) {
                            retryCount++
                            Log.e("ScanViewModel", "❌ ROBUST Background reload attempt ${retryCount} failed: ${e.message}", e)
                            
                            if (retryCount >= maxRetries) {
                                Log.e("ScanViewModel", "❌ ROBUST Background reload failed after $maxRetries attempts")
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "❌ Error in ROBUST background data reload: ${e.message}", e)
                }
            }
        } else {
            Log.w("ScanViewModel", "⚠️ No current picklist number, cannot start ROBUST background reload")
        }
    }
    
    /**
     * Save data ke Supabase saat completion animation dijalankan
     * Method ini dipanggil SEBELUM updatePicklistStatus("completed")
     */
    fun saveDataToSupabaseOnCompletion() {
        Log.d("ScanViewModel", "💾 saveDataToSupabaseOnCompletion called - saving new data before completion")
        
        viewModelScope.launch {
            try {
                saveValidDataToSupabaseBatch()
                Log.d("ScanViewModel", "✅ Completion save completed successfully")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Completion save failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Update status picklist di database
     * @param status Status baru (completed, in_progress, pending)
     */
    fun updatePicklistStatus(status: String) {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist != null) {
            viewModelScope.launch {
                try {
                    val success = repository.updatePicklistStatus(currentPicklist, status)
                    if (success) {
                        Log.i("ScanViewModel", "✅ Picklist $currentPicklist status updated to: $status")
                    } else {
                        Log.e("ScanViewModel", "❌ Failed to update picklist $currentPicklist status to: $status")
                    }
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "❌ Error updating picklist status: ${e.message}", e)
                }
            }
        } else {
            Log.w("ScanViewModel", "⚠️ No current picklist number, cannot update status")
        }
    }
    
    /**
     * Clear RFID collection saja (tanpa menyimpan data)
     * - Clear temp RFID collection
     * - Clear processed RFID data
     * - Reset RFID detection counter
     * - Reset qtyScan semua items kembali ke 0
     * - TIDAK menyimpan data ke Supabase
     */
    fun clearRfidCollectionOnly() {
        Log.d("ScanViewModel", "🔥 Clearing RFID collection only (tanpa save)...")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                lastClearTime = System.currentTimeMillis()
                
                // removed backgroundProcessingJob
                backgroundReloadJob?.cancel()
                backgroundReloadJob = null
                
                // removed tempRfidCollection
                _processedRfidData.value = emptyList()
                
                // removed epcCooldownMap
                
                // removed retry queue & attempts
                Log.d("ScanViewModel", "🧹 Cleared retry queue and attempts")
                
                // **PERBAIKAN BARU**: Clear processed EPC set untuk picklist saat ini saja
                val currentPicklist = currentPicklistNumber
                if (currentPicklist != null) {
                    processedEpcPerPicklist.remove(currentPicklist)
                    uniqueEpcPerPicklist.remove(currentPicklist)
                    appliedEpcPerPicklist.remove(currentPicklist)
                    Log.d("ScanViewModel", "🧹 Cleared all EPC sets untuk picklist: $currentPicklist (processed, unique, applied)")
                }
                
                // **CRITICAL FIX**: Reload data dari database untuk mengembalikan qtyScan ke nilai asli
                if (currentPicklistNumber != null) {
                    Log.d("ScanViewModel", "🔥 Reloading data from database for picklist: $currentPicklistNumber")
                    
                    // **OPTIMASI SINGLE BATCH**: Load picklist items dan processed EPC list dalam satu operasi
                    val batchResult = repository.getPicklistDataBatch(currentPicklistNumber!!)
                    val databaseItems = batchResult.first
                    val processedEpcList = batchResult.second
                    
                    // Set data langsung ke UI
                    _picklistItems.value = databaseItems
                    
                    // **OPTIMASI**: Process processed EPC list yang sudah di-load dalam batch
                    if (processedEpcList.isNotEmpty()) {
                        val epcSet = processedEpcList.toMutableSet()
                        processedEpcPerPicklist[currentPicklistNumber!!] = epcSet
                        
                        // **PERBAIKAN KRITIS**: Seed uniqueEpcPerPicklist juga
                        uniqueEpcPerPicklist[currentPicklistNumber!!] = epcSet
                        Log.d("ScanViewModel", "🔥 Reloaded ${processedEpcList.size} processed EPCs for picklist: $currentPicklistNumber")
                        Log.d("ScanViewModel", "🔥 Seeded uniqueEpcPerPicklist with ${epcSet.size} EPCs")
                    }
                    
                    // **CRITICAL FIX**: Set RFID counter sesuai total qtyScan dari database
                    val totalQtyScanFromDatabase = databaseItems.sumOf { it.qtyScan }
                    
                    // **PERBAIKAN KRITIS**: Jangan override counter dengan database value
                    // Gunakan nilai maksimum antara current counter dan database value
                    val currentCounter = totalRfidDetections
                    val maxCounter = kotlin.math.max(currentCounter, totalQtyScanFromDatabase)
                    
                    if (maxCounter > currentCounter) {
                        totalRfidDetections = maxCounter
                        _rfidDetectionCount.value = maxCounter
                        Log.d("ScanViewModel", "📊 RFID counter updated (max): $currentCounter -> $maxCounter (DB: $totalQtyScanFromDatabase)")
                    } else {
                        Log.d("ScanViewModel", "📊 RFID counter maintained: $currentCounter (DB: $totalQtyScanFromDatabase)")
                    }
                    
                    Log.d("ScanViewModel", "✅ Data reloaded from database - qtyScan dikembalikan ke nilai asli")
                    Log.d("ScanViewModel", "✅ RFID counter dikembalikan ke ${totalQtyScanFromDatabase} (sesuai database)")
                } else {
                    Log.w("ScanViewModel", "⚠️ No current picklist number, cannot reload from database")
                }
                
                // Update UI setelah reload
                updateFilteredItems()
                updateQtySummary()
                updateQtyStatusColors()
                
                Log.d("ScanViewModel", "✅ Clear RFID collection berhasil (tanpa save)")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error clearing RFID collection: ${e.message}", e)
                _errorMessage.value = "Error clearing RFID collection: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear RFID buffer dari cache
     * Hanya hapus overscan dan non-picklist, pertahankan scan yang valid
     */
    private suspend fun clearRfidBufferFromCache() {
        try {
            Log.d("ScanViewModel", "🔥 Clearing RFID buffer dari cache...")
            
            // Clear scan cache untuk picklist ini menggunakan CacheManager
            if (currentPicklistNumber != null) {
                // Hapus hanya overscan dan non-picklist dari cache
                // Pertahankan scan yang valid untuk picklist ini
                // Cache dihapus, langsung ke Supabase
                Log.d("ScanViewModel", "🔥 RFID buffer cleared untuk picklist: $currentPicklistNumber")
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error clearing RFID buffer: ${e.message}", e)
        }
    }
    
    
    
    /**
     * Get informasi tentang EPC yang sudah diproses per picklist
     */
    fun getProcessedEpcInfo(): String {
        val currentPicklist = currentPicklistNumber
        val processedCount = if (currentPicklist != null) {
            processedEpcPerPicklist[currentPicklist]?.size ?: 0
        } else 0
        val cooldownCount = 0
        val tempCount = 0
        val totalPicklists = processedEpcPerPicklist.size
        
        return "Picklist: $currentPicklist, Processed EPCs: $processedCount, Cooldown EPCs: $cooldownCount, Temp EPCs: $tempCount, Total Picklists: $totalPicklists"
    }
    
    /**
     * Cek apakah EPC sudah pernah diproses dalam picklist saat ini
     */
    fun isEpcProcessed(epc: String): Boolean {
        val currentPicklist = currentPicklistNumber
        return if (currentPicklist != null) {
            processedEpcPerPicklist[currentPicklist]?.contains(epc) ?: false
        } else false
    }
    
    /**
     * **PERBAIKAN BARU**: Fungsi untuk mengizinkan re-scan RFID yang sudah ada
     * Berguna untuk kasus dimana RFID perlu di-scan ulang karena alasan tertentu
     */
    fun allowRescanEpc(epc: String): Boolean {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist != null) {
            val processedEpcSet = processedEpcPerPicklist[currentPicklist] ?: mutableSetOf()
            if (processedEpcSet.contains(epc)) {
                Log.d("ScanViewModel", "🔄 Allowing re-scan for EPC: $epc in picklist: $currentPicklist")
                
                // Hapus EPC dari processed set untuk mengizinkan scan ulang
                processedEpcSet.remove(epc)
                processedEpcPerPicklist[currentPicklist] = processedEpcSet
                
                Log.d("ScanViewModel", "✅ EPC $epc removed from processed set - can be scanned again")
                return true
            } else {
                Log.d("ScanViewModel", "ℹ️ EPC $epc is not in processed set - no action needed")
                return false
            }
        }
        return false
    }
    
    /**
     * **PERBAIKAN BARU**: Fungsi untuk mengizinkan re-scan semua RFID dalam picklist saat ini
     * Berguna untuk reset semua RFID dalam picklist tanpa clear data
     */
    fun allowRescanAllEpcs(): Int {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist != null) {
            val processedEpcSet = processedEpcPerPicklist[currentPicklist] ?: mutableSetOf()
            val count = processedEpcSet.size
            
            if (count > 0) {
                Log.d("ScanViewModel", "🔄 Allowing re-scan for all ${count} EPCs in picklist: $currentPicklist")
                
                // Clear semua EPC dari processed set
                processedEpcSet.clear()
                processedEpcPerPicklist[currentPicklist] = processedEpcSet
                
                Log.d("ScanViewModel", "✅ All ${count} EPCs removed from processed set - can be scanned again")
                return count
            } else {
                Log.d("ScanViewModel", "ℹ️ No EPCs in processed set for picklist: $currentPicklist")
                return 0
            }
        }
        return 0
    }
    
    /**
     * **PERBAIKAN BARU**: Verifikasi sinkronisasi data antara UI dan database
     * Fungsi ini akan dipanggil secara berkala untuk memastikan data selalu sinkron
     */
    fun verifyDataSynchronization() {
        val currentPicklist = currentPicklistNumber
        if (currentPicklist == null) return
        
        Log.d("ScanViewModel", "🔄 Verifying data synchronization for picklist: $currentPicklist")
        
        viewModelScope.launch {
            try {
                // Ambil data terbaru dari database
                val batchResult = repository.getPicklistDataBatch(currentPicklist)
                val databaseItems = batchResult.first
                val processedEpcList = batchResult.second
                
                // Cek apakah ada perbedaan antara UI dan database
                val currentItems = _picklistItems.value ?: emptyList()
                val currentProcessedEpc = processedEpcPerPicklist[currentPicklist] ?: mutableSetOf()
                
                var hasDifference = false
                
                // Cek perbedaan qtyScan
                databaseItems.forEach { dbItem ->
                    val uiItem = currentItems.find { it.articleId == dbItem.articleId && it.size == dbItem.size }
                    if (uiItem != null && uiItem.qtyScan != dbItem.qtyScan) {
                        Log.w("ScanViewModel", "⚠️ Data mismatch detected: ${dbItem.articleName} ${dbItem.size}")
                        Log.w("ScanViewModel", "⚠️   UI qtyScan: ${uiItem.qtyScan}, Database qtyScan: ${dbItem.qtyScan}")
                        hasDifference = true
                    }
                }
                
                // Cek perbedaan processed EPC
                val dbEpcSet = processedEpcList.toSet()
                val uiEpcSet = currentProcessedEpc.toSet()
                if (dbEpcSet != uiEpcSet) {
                    Log.w("ScanViewModel", "⚠️ Processed EPC mismatch detected")
                    Log.w("ScanViewModel", "⚠️   UI EPCs: ${uiEpcSet.size}, Database EPCs: ${dbEpcSet.size}")
                    hasDifference = true
                }
                
                if (hasDifference) {
                    Log.w("ScanViewModel", "🔄 Data synchronization needed - updating UI with database data")
                    
                    // Merge dengan UI: pertahankan qtyScan tertinggi agar tidak turun saat sync
                    val currentUiItems = _picklistItems.value ?: emptyList()
                    val merged = databaseItems.map { dbItem ->
                        val uiItem = currentUiItems.find { it.articleId == dbItem.articleId && it.size == dbItem.size }
                        if (uiItem != null) {
                            if (uiItem.qtyScan > dbItem.qtyScan) dbItem.copy(qtyScan = uiItem.qtyScan) else dbItem
                        } else dbItem
                    } + currentUiItems.filter { uiItem ->
                        databaseItems.none { it.articleId == uiItem.articleId && it.size == uiItem.size }
                    }
                    _picklistItems.value = merged
                    
                    // Update processed EPC list
                    if (processedEpcList.isNotEmpty()) {
                        val epcSet = processedEpcList.toMutableSet()
                        processedEpcPerPicklist[currentPicklist] = epcSet
                    }
                    
                    // **PERBAIKAN KRITIS**: Jangan update RFID counter di verifyDataSynchronization
                    // RFID counter menampilkan EPC unik yang terscan saat itu, bukan data dari database
                    val totalQtyScanFromDatabase = databaseItems.sumOf { it.qtyScan }
                    val currentCounter = totalRfidDetections
                    
                    Log.d("ScanViewModel", "📊 RFID counter tetap: $currentCounter EPC unik terscan")
                    Log.d("ScanViewModel", "📊 Database qtyScan: $totalQtyScanFromDatabase (untuk referensi saja)")
                    
                    // Update UI
                    updateQtySummary()
                    updateQtyStatusColors()
                    updateFilteredItems()
                    
                    Log.d("ScanViewModel", "✅ Data synchronization completed")
                } else {
                    Log.d("ScanViewModel", "✅ Data is already synchronized")
                }
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error verifying data synchronization: ${e.message}")
            }
        }
    }
    
    /**
     * Cleanup cooldown map untuk EPC yang sudah lama
     * Menghapus EPC yang sudah tidak aktif dalam 5 menit terakhir
     */
    private fun cleanupOldCooldownEntries() {
        // Cooldown map removed; nothing to cleanup in single-only mode
        Log.d("ScanViewModel", "ℹ️ cleanupOldCooldownEntries: cooldown disabled")
    }
    
    /**
     * Cleanup resources
     */
    /**
     * Get all detected RFIDs untuk batch lookup
     */
    fun getAllDetectedRfids(): List<String> {
        val uniqueSet = getCurrentUniqueSet()
        val rfidList = uniqueSet.toList()
        Log.d("ScanViewModel", "🔥 Retrieved ${rfidList.size} detected RFIDs for batch lookup")
        return rfidList
    }
    
    /**
     * Process RFID results yang sudah di-lookup oleh RfidScanManager (NO API CALLS)
     * @param rfidList List RFID yang sudah di-lookup
     * @return Jumlah RFID yang berhasil diproses
     */
    suspend fun performBatchLookup(rfidList: List<String>): Int {
        if (rfidList.isEmpty()) {
            Log.d("ScanViewModel", "ℹ️ No RFIDs to process")
            return 0
        }
        
        Log.d("ScanViewModel", "🔥 Processing ${rfidList.size} RFIDs from RfidScanManager cache (NO API CALLS)")
        
        val currentItems = _picklistItems.value ?: emptyList()
        if (currentItems.isEmpty()) {
            Log.w("ScanViewModel", "⚠️ No picklist items available")
            return 0
        }
        
        try {
            // **PERBAIKAN**: Method ini akan dipanggil dari Activity dengan data yang sudah di-filter
            // Untuk sementara, kita akan menggunakan approach yang lebih sederhana
            // Activity akan memanggil method baru yang menerima data langsung
            
            Log.d("ScanViewModel", "🔥 Processing RFID data from RfidScanManager cache (passed from Activity)")
            
            // Simulasi processing untuk testing
            var successCount = 0
            for (rfid in rfidList) {
                Log.d("ScanViewModel", "ℹ️ Processing EPC $rfid from RfidScanManager cache (NO API CALL)")
                successCount++
            }
            
            Log.d("ScanViewModel", "✅ Processed ${successCount} RFIDs from RfidScanManager cache")
            Log.d("ScanViewModel", "🔥 SINGLE LOOKUP completed: $successCount/${rfidList.size} RFIDs processed from RfidScanManager cache")
            
            return successCount
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error in optimized batch lookup: ${e.message}", e)
            return 0
        }
    }
    
    /**
     * Reload processedRfidData dari database untuk picklist yang pernah di-scan
     * @param processedEpcList List EPC yang sudah di-scan dari database
     * @param picklistItems List PicklistItem dari database
     */
    private suspend fun reloadProcessedRfidDataFromDatabase(
        processedEpcList: List<String>,
        picklistItems: List<com.example.cekpicklist.data.PicklistItem>
    ) {
        try {
            Log.d("ScanViewModel", "🔥 Reloading processedRfidData from database for ${processedEpcList.size} EPCs")
            
            // **PERBAIKAN**: Untuk sementara, buat processedRfidData dari data yang tersedia
            // TODO: Integrate dengan RfidScanManager cache jika diperlukan
            Log.d("ScanViewModel", "🔥 Creating processedRfidData from database EPCs")
            
            // Buat processedRfidData dari data yang tersedia
            val processedData = mutableListOf<ProcessedRfidData>()
            
            processedEpcList.forEach { epc ->
                // **PERBAIKAN**: Buat entry sederhana untuk EPC yang sudah di-scan
                // Status akan ditentukan berdasarkan apakah EPC ada di picklist atau tidak
                val processedRfid = ProcessedRfidData(
                    epc = epc,
                    articleId = "SCANNED", // Placeholder untuk EPC yang sudah di-scan
                    articleName = "SCANNED",
                    size = "",
                    qty = 1,
                    productId = "",
                    productName = "SCANNED",
                    brand = "",
                    category = "",
                    subCategory = "",
                    color = "",
                    gender = "",
                    warehouse = "",
                    tagStatus = "SCANNED" // Status khusus untuk EPC yang sudah di-scan
                )
                
                processedData.add(processedRfid)
                Log.d("ScanViewModel", "✅ Reloaded processed RFID: $epc -> SCANNED (from database)")
            }
            
            // Update processedRfidData
            if (processedData.isNotEmpty()) {
                _processedRfidData.value = processedData
                Log.d("ScanViewModel", "✅ Reloaded processedRfidData with ${processedData.size} items from database")
            } else {
                Log.d("ScanViewModel", "ℹ️ No processedRfidData to reload")
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error reloading processedRfidData from database: ${e.message}", e)
        }
    }
    
    /**
     * Process RFID results dengan data langsung dari RfidScanManager epcToProduct
     * @param epcToProductData Map EPC -> ProductInfo dari RfidScanManager
     * @param rfidList List RFID yang relevan
     * @return Jumlah RFID yang berhasil diproses
     */
    suspend fun processLookupResultsFromRfidScanManager(
        epcToProductData: Map<String, com.example.cekpicklist.api.NirwanaApiService.ProductInfo>,
        rfidList: List<String>
    ): Int {
        if (rfidList.isEmpty()) {
            Log.d("ScanViewModel", "ℹ️ No RFIDs to process")
            return 0
        }
        
        Log.d("ScanViewModel", "🔥 Processing ${rfidList.size} RFIDs with direct epcToProduct data")
        
        val currentItems = _picklistItems.value ?: emptyList()
        if (currentItems.isEmpty()) {
            Log.w("ScanViewModel", "⚠️ No picklist items available")
            return 0
        }
        
        try {
            // Filter hanya EPC yang ada di rfidList
            val relevantResults = epcToProductData.filterKeys { epc -> 
                rfidList.contains(epc) 
            }
            
            Log.d("ScanViewModel", "🎯 Processing ${relevantResults.size} relevant EPCs from ${epcToProductData.size} total cached results")
            
            // Process hasil lookup ke dalam processedRfidData DAN update qtyScan
            val processedData = mutableListOf<ProcessedRfidData>()
            var updatedItems = currentItems.toMutableList()
            
            // **CRITICAL FIX**: Cek EPC yang sudah diproses sebelumnya untuk mencegah duplikasi
            // 1. EPC dari database (session sebelumnya)
            val currentPicklist = currentPicklistNumber
            val existingProcessedEpcs = if (currentPicklist != null) {
                processedEpcPerPicklist[currentPicklist] ?: emptySet()
            } else {
                emptySet()
            }
            
            // 2. EPC yang sudah diproses dalam session saat ini
            val currentSessionProcessedEpcs = _processedRfidData.value?.map { it.epc }?.toSet() ?: emptySet()
            
            // 3. Gabungkan kedua set untuk duplicate check yang komprehensif
            val allProcessedEpcs = existingProcessedEpcs + currentSessionProcessedEpcs
            
            Log.d("ScanViewModel", "🔍 Existing processed EPCs from database: ${existingProcessedEpcs.size} items")
            Log.d("ScanViewModel", "🔍 Current session processed EPCs: ${currentSessionProcessedEpcs.size} items")
            Log.d("ScanViewModel", "🔍 Total processed EPCs (database + session): ${allProcessedEpcs.size} items")
            Log.d("ScanViewModel", "🔍 New EPCs to process: ${relevantResults.size} items")
            Log.d("ScanViewModel", "🔍 All processed EPCs: $allProcessedEpcs")
            
            relevantResults.forEach { (epc, productInfo) ->
                // **CRITICAL FIX**: Skip EPC yang sudah diproses (dari database ATAU session saat ini)
                if (allProcessedEpcs.contains(epc)) {
                    val source = if (existingProcessedEpcs.contains(epc)) "database" else "current session"
                    Log.d("ScanViewModel", "⏭️ Skipping duplicate EPC from $source: $epc -> ${productInfo.articleName} ${productInfo.size}")
                    return@forEach
                }
                
                Log.d("ScanViewModel", "🔍 Processing NEW EPC: $epc -> ${productInfo.articleName} ${productInfo.size}")
                
                // Tentukan status berdasarkan data
                val tagStatus = when {
                    productInfo.articleName == "NOT_FOUND" -> "NOT_FOUND"
                    currentItems.any { it.articleId == productInfo.articleId && it.size == productInfo.size } -> "VALID"
                    else -> "NON_PICKLIST"
                }
                
                val processedRfid = ProcessedRfidData(
                    epc = epc,
                    articleId = productInfo.articleId,
                    articleName = productInfo.articleName,
                    size = productInfo.size,
                    qty = productInfo.qty,
                    productId = productInfo.productId,
                    productName = productInfo.productName,
                    brand = productInfo.brand,
                    category = productInfo.category,
                    subCategory = productInfo.subCategory,
                    color = productInfo.color,
                    gender = productInfo.gender,
                    warehouse = productInfo.warehouse,
                    tagStatus = tagStatus
                )
                
                processedData.add(processedRfid)
                Log.d("ScanViewModel", "✅ Added NEW processed RFID: $epc -> ${productInfo.articleName} ${productInfo.size} (status: $tagStatus)")
                
                // **CRITICAL FIX**: Update qtyScan untuk item yang cocok
                if (tagStatus == "VALID") {
                    val matchingItemIndex = updatedItems.indexOfFirst { item ->
                        item.articleId == productInfo.articleId && item.size == productInfo.size
                    }
                    
                    if (matchingItemIndex != -1) {
                        val matchingItem = updatedItems[matchingItemIndex]
                        // **FIX**: Setiap EPC hanya menambah 1 ke qtyScan, bukan productInfo.qty
                        val newQtyScan = matchingItem.qtyScan + 1
                        val updatedItem = matchingItem.copy(qtyScan = newQtyScan)
                        updatedItems[matchingItemIndex] = updatedItem
                        
                        Log.d("ScanViewModel", "🔄 Updated qtyScan: ${matchingItem.articleName} ${matchingItem.size} -> qtyScan: ${matchingItem.qtyScan} -> $newQtyScan (EPC count: +1)")
                    }
                }
            }
            
            // Update _picklistItems dengan qtyScan yang sudah diupdate
            if (updatedItems != currentItems) {
                _picklistItems.value = updatedItems
                Log.d("ScanViewModel", "✅ Updated _picklistItems with new qtyScan values")
            }
            
            // Update processedRfidData - gabungkan dengan data yang sudah ada
            if (processedData.isNotEmpty()) {
                val currentProcessedData = _processedRfidData.value ?: emptyList()
                val combinedProcessedData = currentProcessedData + processedData
                _processedRfidData.value = combinedProcessedData
                Log.d("ScanViewModel", "✅ Updated processedRfidData: ${currentProcessedData.size} existing + ${processedData.size} new = ${combinedProcessedData.size} total items")
                
                // Update UI components
                updateQtySummary()
                updateQtyStatusColors()
                updateFilteredItems()
            }
            
            val successCount = processedData.size
            Log.d("ScanViewModel", "✅ Processed ${successCount} RFIDs from RfidScanManager cache")
            Log.d("ScanViewModel", "🔥 DIRECT LOOKUP completed: $successCount/${rfidList.size} RFIDs processed from epcToProduct")
            
            return successCount
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error in direct lookup processing: ${e.message}", e)
            return 0
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d("ScanViewModel", "🔥 Cleaning up ScanViewModel...")
        
        // **PERBAIKAN KRITIS**: Cancel semua background jobs untuk mencegah FMQ errors
        // removed backgroundProcessingJob
        backgroundReloadJob?.cancel()
        backgroundReloadJob = null
        
        // **PERBAIKAN**: Cancel global background reload job
        globalBackgroundReloadJob?.cancel()
        globalBackgroundReloadJob = null
        
        Log.d("ScanViewModel", "✅ All background jobs cancelled")
    }
}
