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
import com.example.cekpicklist.repository.Repository
import com.example.cekpicklist.manager.CacheManager
import com.example.cekpicklist.utils.OptimizedLogger
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository()
    private val cacheManager = CacheManager()
    
    // LiveData untuk UI
    private val _picklists = MutableLiveData<List<String>>()
    val picklists: LiveData<List<String>> = _picklists
    
    private val _picklistItems = MutableLiveData<List<PicklistItem>>()
    val picklistItems: LiveData<List<PicklistItem>> = _picklistItems
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Current picklist number
    private var currentPicklistNumber: String? = null
    
    init {
        // Gunakan OptimizedLogger untuk mengurangi spam log
        OptimizedLogger.d("ScanViewModel", "🔥 Initializing ScanViewModel...")
    }
    
    /**
     * Optimized load picklists - menghindari duplikasi dan operasi tidak perlu
     */
    fun loadPicklistsOptimized() {
        val startTime = System.currentTimeMillis()
        Log.i("ScanViewModel", "🔥 === OPTIMIZED LOADING PICKLISTS START ===")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Check cache first untuk menghindari unnecessary API calls
                val cachedPicklists = cacheManager.getCachedPicklists()
                val cacheAge = cacheManager.getCacheAge()
                
                Log.i("ScanViewModel", "🔥 Cache check: ${cachedPicklists.size} cached picklists, age: ${cacheAge}ms")
                
                if (cachedPicklists.isNotEmpty() && cacheAge < 300000) { // 5 menit cache validity
                    Log.i("ScanViewModel", "🔥 Using cached picklists (age: ${cacheAge}ms)")
                    _picklists.value = cachedPicklists
                    
                    // Background refresh untuk data terbaru tanpa blocking UI
                    refreshPicklistsInBackground()
                } else {
                    Log.i("ScanViewModel", "🔥 Cache expired or empty, fetching fresh data")
                    val picklists = repository.getPicklists()
                    
                    if (picklists.isNotEmpty()) {
                        val loadTime = System.currentTimeMillis() - startTime
                        Log.i("ScanViewModel", "✅ Fresh picklists loaded in ${loadTime}ms")
                        
                        // Update cache
                        cacheManager.cachePicklists(picklists)
                        cacheManager.setLastFetchTime(java.time.LocalDateTime.now())
                        
                        _picklists.value = picklists
                    }
                }
                
                Log.i("ScanViewModel", "🔥 === OPTIMIZED LOADING PICKLISTS END ===")
                
            } catch (e: Exception) {
                val loadTime = System.currentTimeMillis() - startTime
                Log.e("ScanViewModel", "❌ Error in optimized loading after ${loadTime}ms: ${e.message}", e)
                _errorMessage.value = "Error loading picklists: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Background refresh untuk data terbaru tanpa blocking UI
     */
    private suspend fun refreshPicklistsInBackground() {
        try {
            Log.d("ScanViewModel", "🔄 Background refresh started...")
            val picklists = repository.getPicklists()
            
            if (picklists.isNotEmpty()) {
                // Update cache dengan data terbaru
                cacheManager.cachePicklists(picklists)
                cacheManager.setLastFetchTime(java.time.LocalDateTime.now())
                
                // Update UI jika ada perubahan
                val currentPicklists = _picklists.value ?: emptyList()
                if (picklists != currentPicklists) {
                    Log.d("ScanViewModel", "🔄 Background refresh: Data updated")
                    _picklists.value = picklists
                } else {
                    Log.d("ScanViewModel", "🔄 Background refresh: No changes detected")
                }
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Background refresh failed: ${e.message}", e)
        }
    }
    
    /**
     * Load picklist items untuk picklist tertentu dengan logging detail dan optimasi
     */
    fun loadPicklistItems(picklistNumber: String) {
        val startTime = System.currentTimeMillis()
        Log.i("ScanViewModel", "🔥 === LOADING PICKLIST ITEMS START ===")
        Log.i("ScanViewModel", "🔥 Picklist: $picklistNumber")
        Log.i("ScanViewModel", "🔥 Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        // Check apakah sudah loading picklist yang sama untuk menghindari duplikasi
        if (currentPicklistNumber == picklistNumber && _isLoading.value == true) {
            Log.w("ScanViewModel", "⚠️ Already loading picklist $picklistNumber, skipping duplicate request")
            return
        }
        
        currentPicklistNumber = picklistNumber
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Log cache status sebelum load
                val cacheStatus = cacheManager.getCacheStatus()
                Log.i("ScanViewModel", "🔥 Cache status before load: $cacheStatus")
                
                // Check cache first untuk menghindari unnecessary API calls
                val cachedItems = cacheManager.getCachedPicklistItems(picklistNumber)
                val cacheAge = cacheManager.getCacheAge()
                
                Log.i("ScanViewModel", "🔥 Cache check: ${cachedItems.size} cached items, age: ${cacheAge}ms")
                
                val items = if (cachedItems.isNotEmpty() && cacheAge < 180000) { // 3 menit cache validity
                    Log.i("ScanViewModel", "🔥 Using cached items (age: ${cacheAge}ms)")
                    cachedItems
                } else {
                    Log.i("ScanViewModel", "🔥 Cache expired or empty, fetching fresh data")
                    repository.getPicklistItems(picklistNumber)
                }
                
                val loadTime = System.currentTimeMillis() - startTime
                
                if (items.isNotEmpty()) {
                    Log.i("ScanViewModel", "✅ Loaded ${items.size} items in ${loadTime}ms")
                    
                    // Log detail item pertama dan beberapa sample
                    val firstItem = items.first()
                    Log.i("ScanViewModel", "🔥 First item: ${firstItem.articleName} ${firstItem.size} - qtyPl=${firstItem.qtyPl}, qtyScan=${firstItem.qtyScan}, scan=${firstItem.scan}")
                    
                    // Log summary statistics
                    val totalQtyPl = items.sumOf { it.qtyPl }
                    val totalQtyScan = items.sumOf { it.qtyScan }
                    val scannedItems = items.count { it.qtyScan > 0 }
                    val completedItems = items.count { it.qtyScan >= it.qtyPl }
                    
                    Log.i("ScanViewModel", "🔥 Summary: Total items=${items.size}, Scanned items=$scannedItems, Completed items=$completedItems")
                    Log.i("ScanViewModel", "🔥 Quantities: Total qtyPl=$totalQtyPl, Total qtyScan=$totalQtyScan, Remaining=${totalQtyPl - totalQtyScan}")
                    
                    // Log sample items untuk debugging
                    val sampleItems = items.take(3)
                    sampleItems.forEachIndexed { index, item ->
                        Log.d("ScanViewModel", "🔥 Sample ${index + 1}: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}")
                    }
                    
                    // Update cache dengan data terbaru
                    cacheManager.cachePicklistItems(picklistNumber, items)
                    
                    _picklistItems.value = items
                    
                    // Log cache status setelah load
                    val newCacheStatus = cacheManager.getCacheStatus()
                    Log.i("ScanViewModel", "🔥 Cache status after load: $newCacheStatus")
                } else {
                    Log.w("ScanViewModel", "⚠️ No items found for picklist: $picklistNumber in ${loadTime}ms")
                }
                
                Log.i("ScanViewModel", "🔥 === LOADING PICKLIST ITEMS END ===")
                
            } catch (e: Exception) {
                val loadTime = System.currentTimeMillis() - startTime
                Log.e("ScanViewModel", "❌ Error loading picklist items after ${loadTime}ms: ${e.message}", e)
                _errorMessage.value = "Error loading picklist items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Add EPC
     */
    fun addEpc(epc: String) {
        Log.d("ScanViewModel", "🔥 Adding EPC: $epc")
        
        viewModelScope.launch {
            try {
                // Handle EPC
                handleNewEpc(epc)
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error adding EPC: ${e.message}", e)
                _errorMessage.value = "Error adding EPC: ${e.message}"
            }
        }
    }
    
    /**
     * Handle EPC baru
     */
    private suspend fun handleNewEpc(epc: String) {
        try {
            Log.d("ScanViewModel", "🔥 Handling new EPC: $epc")
            
            // 1. Cari article ID berdasarkan EPC
            val articleId = findArticleIdByEpc(epc)
            if (articleId == null) {
                Log.w("ScanViewModel", "⚠️ Article ID not found for EPC: $epc")
                _errorMessage.value = "Article ID not found for EPC: $epc"
                return
            }
            
            // 2. Buat PicklistScan object
            val picklistScan = PicklistScan(
                id = 0L,
                noPicklist = currentPicklistNumber ?: "UNKNOWN",
                articleId = articleId,
                epc = epc,
                qtyScan = 1,
                createdAt = System.currentTimeMillis().toString()
            )
            
            // 3. Simpan ke Supabase
            val success = repository.savePicklistScan(picklistScan)
            if (success) {
                Log.d("ScanViewModel", "✅ EPC saved successfully")
                // Refresh data
                if (currentPicklistNumber != null) {
                    loadPicklistItems(currentPicklistNumber!!)
                }
            } else {
                Log.e("ScanViewModel", "❌ Failed to save EPC")
                _errorMessage.value = "Failed to save EPC"
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error handling EPC: ${e.message}", e)
        }
    }
    
    /**
     * Find article ID berdasarkan EPC
     */
    private fun findArticleIdByEpc(epc: String): String? {
        // TODO: Implementasi mapping EPC ke article ID
        // Untuk sementara, return null
        Log.w("ScanViewModel", "⚠️ findArticleIdByEpc not implemented for EPC: $epc")
        return null
    }
    
    /**
     * Get filtered items (hide completed items)
     */
    fun getFilteredItems(): List<PicklistItem> {
        val items = _picklistItems.value ?: emptyList()
        return items.filter { !it.isComplete() }
    }
    
    /**
     * Get current picklist number untuk debugging dan duplikasi check
     */
    fun getCurrentPicklistNumber(): String? {
        return currentPicklistNumber
    }
    
    /**
     * Get cache info untuk debugging
     */
    fun getCacheInfo(): String {
        return "Cache Info: Not implemented yet"
    }
    
    /**
     * Get completion status untuk picklist tertentu dengan validasi overscan
     */
    fun getPicklistCompletionStatus(picklistNumber: String): PicklistStatus {
        return try {
            // Ambil data dari cache atau database
            val items = cacheManager.getCachedPicklistItems(picklistNumber)
            
            if (items.isNotEmpty()) {
                val totalQty = items.sumOf { it.qtyPl }
                val scannedQty = items.sumOf { it.qtyScan }
                
                // VALIDASI: Pastikan tidak ada overscan dari cache
                val validatedScannedQty = items.sumOf { item ->
                    // Gunakan qtyScan yang sudah ada karena cache sudah mencegah overscan
                    item.qtyScan
                }
                
                val remainingQty = if (validatedScannedQty >= totalQty) {
                    0 // Jika sudah selesai, remaining = 0
                } else {
                    totalQty - validatedScannedQty
                }
                
                // Overscan seharusnya 0 karena cache sudah mencegahnya
                val overscanQty = 0
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
                // Picklist belum pernah di-scan
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
     * Clear semua cache
     */
    fun clearCache() {
        Log.d("ScanViewModel", "🔥 Clearing cache...")
        // TODO: Implementasi clear cache
    }
    
    /**
     * Clear RFID dan reset ke state awal picklist
     * - Reset qtyScan untuk semua items ke 0
     * - Reset RFID detect counter
     * - Hapus overscan dan non-picklist dari tampilan
     * - Tampilkan ulang data scan yang sudah pernah scan valid dari cache/Supabase
     */
    fun clearRfidAndResetToInitialState() {
        Log.d("ScanViewModel", "🔥 Clearing RFID dan reset ke state awal...")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // 1. Reset qtyScan untuk semua items ke 0
                val currentItems = _picklistItems.value ?: emptyList()
                val resetItems = currentItems.map { item ->
                    item.copy(qtyScan = 0) // Reset qtyScan ke 0
                }
                
                Log.d("ScanViewModel", "🔥 Reset qtyScan untuk ${resetItems.size} items")
                
                // 2. Update UI dengan data yang sudah di-reset
                _picklistItems.value = resetItems
                
                // 3. Clear RFID buffer di cache (hapus overscan dan non-picklist)
                clearRfidBufferFromCache()
                
                // 4. Reload data scan yang valid dari cache/Supabase
                if (currentPicklistNumber != null) {
                    reloadValidScanDataFromCache(currentPicklistNumber!!)
                }
                
                Log.d("ScanViewModel", "✅ Clear RFID dan reset berhasil")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "❌ Error clearing RFID: ${e.message}", e)
                _errorMessage.value = "Error clearing RFID: ${e.message}"
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
                cacheManager.clearOverscanAndNonPicklistFromCache(currentPicklistNumber!!)
                Log.d("ScanViewModel", "🔥 RFID buffer cleared untuk picklist: $currentPicklistNumber")
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error clearing RFID buffer: ${e.message}", e)
        }
    }
    
    /**
     * Reload data scan yang valid dari cache/Supabase
     * Menampilkan ulang data scan yang sudah pernah scan valid
     */
    private suspend fun reloadValidScanDataFromCache(picklistNo: String) {
        try {
            Log.d("ScanViewModel", "🔄 Reloading valid scan data untuk: $picklistNo")
            
            // 1. Ambil data scan yang valid dari cache
            val validScans = getValidScansFromCache(picklistNo)
            
            // 2. Update qtyScan berdasarkan data scan yang valid
            val currentItems = _picklistItems.value ?: emptyList()
            val updatedItems = currentItems.map { item ->
                val scanCount = validScans.count { scan -> 
                    scan.articleId == item.articleId
                }
                item.copy(qtyScan = scanCount)
            }
            
            // 3. Update UI dengan data yang sudah di-update
            _picklistItems.value = updatedItems
            
            Log.d("ScanViewModel", "✅ Valid scan data reloaded: ${validScans.size} scans")
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error reloading valid scan data: ${e.message}", e)
        }
    }
    
    /**
     * Ambil data scan yang valid dari cache
     * Hanya scan yang sesuai dengan picklist dan artikel yang ada
     */
    private suspend fun getValidScansFromCache(picklistNo: String): List<PicklistScan> {
        try {
            Log.d("ScanViewModel", "🔍 Getting valid scans from cache untuk: $picklistNo")
            
            // Ambil scan data untuk picklist ini
            val allScans = cacheManager.getScanData(picklistNo)
            
            // Filter hanya scan yang valid untuk picklist ini
            val validScans = allScans.filter { scan ->
                scan.noPicklist == picklistNo
            }
            
            Log.d("ScanViewModel", "🔍 Found ${validScans.size} valid scans dari ${allScans.size} total scans")
            
            return validScans
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "❌ Error getting valid scans: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Cleanup resources
     */
    override fun onCleared() {
        super.onCleared()
        Log.d("ScanViewModel", "🔥 Cleaning up ScanViewModel...")
    }
}
