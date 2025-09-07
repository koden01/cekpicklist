package com.example.cekpicklist.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import com.example.cekpicklist.api.SupabaseRealtimeService
import com.example.cekpicklist.api.PicklistScanChange
import com.example.cekpicklist.api.CacheUpdate
import com.example.cekpicklist.api.PicklistUpdate
import com.example.cekpicklist.cache.CacheManager
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan
import com.example.cekpicklist.data.PicklistStatus
import com.example.cekpicklist.data.ScanResult
import com.example.cekpicklist.repository.Repository
import com.example.cekpicklist.utils.LogUtils
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.*

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository()
    private val cacheManager = CacheManager(application)
    private val realtimeService = SupabaseRealtimeService()
    
    // LiveData untuk UI
    private val _picklists = MutableLiveData<List<String>>()
    val picklists: LiveData<List<String>> = _picklists
    
    private val _picklistItems = MutableLiveData<List<PicklistItem>>()
    val picklistItems: LiveData<List<PicklistItem>> = _picklistItems
    
    private val _scanCounter = MutableLiveData<Int>()
    val scanCounter: LiveData<Int> = _scanCounter
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Auto-post timer untuk RFID
    private var autoPostTimer: Job? = null
    private val autoPostInterval = 1000L // 1 detik
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _isComplete = MutableLiveData<Boolean>()
    val isComplete: LiveData<Boolean> = _isComplete
    
    // Realtime connection status
    private val _isRealtimeConnected = MutableLiveData<Boolean>()
    val isRealtimeConnected: LiveData<Boolean> = _isRealtimeConnected
    
    // Buffer EPC untuk menghindari duplikat
    private val epcBuffer = HashSet<String>()
    private var currentPicklistNumber: String = ""
    
    // Buffer EPC yang sudah diproses untuk disimpan saat Submit
    private val processedEpcBuffer = mutableListOf<String>()
    
    // Flag untuk mencegah pemanggilan berulang
    private var isPicklistsLoading = false
    private var isPicklistItemsLoading = false
    
    // List untuk menyimpan item yang tidak sesuai qty
    private val _qtyMismatchItems = MutableLiveData<List<PicklistItem>>()
    val qtyMismatchItems: LiveData<List<PicklistItem>> = _qtyMismatchItems
    
    // LiveData untuk picklist statuses
    private val _picklistStatuses = MutableLiveData<List<PicklistStatus>>()
    val picklistStatuses: LiveData<List<PicklistStatus>> = _picklistStatuses
    
    init {
        Log.i("ScanViewModel", "🔥 ScanViewModel init dimulai")
        // Hapus loadPicklists() dari init untuk menghindari duplikasi
        // loadPicklists() akan dipanggil dari PicklistInputActivity
        _scanCounter.value = 0
        
        
        Log.i("ScanViewModel", "🔥 ScanViewModel init selesai")
    }
    
    fun loadPicklists() {
        Log.i("ScanViewModel", "🔥 loadPicklists dipanggil")
        
        // Cek apakah sudah dalam proses loading
        if (isPicklistsLoading) {
            Log.i("ScanViewModel", "🔥 loadPicklists sudah dalam proses, skip")
            return
        }
        
        viewModelScope.launch {
            try {
                isPicklistsLoading = true
                Log.i("ScanViewModel", "🔥 loadPicklists mulai")
                _isLoading.value = true
                
                // SELALU fetch fresh data dari Supabase
                Log.i("ScanViewModel", "🔥 Fetching fresh picklists dari Supabase...")
                fetchPicklistsFromSupabase()
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 loadPicklists error: ${e.message}", e)
                _errorMessage.value = "Gagal memuat daftar picklist: ${e.message}"
            } finally {
                _isLoading.value = false
                isPicklistsLoading = false
                Log.i("ScanViewModel", "🔥 loadPicklists selesai")
            }
        }
    }
    
    /**
     * Fetch picklists dari Supabase dan update cache
     */
    private suspend fun fetchPicklistsFromSupabase() {
        try {
            Log.i("ScanViewModel", "🔥 Fetching fresh picklists dari Supabase...")
            val picklists = repository.getPicklists()
            
            // Clear cache lama dan update dengan data fresh
            cacheManager.clearPicklistsCache()
            cacheManager.cachePicklists(picklists)
            cacheManager.setLastFetchTime(LocalDateTime.now())
            
            Log.i("ScanViewModel", "🔥 Fresh picklists berhasil di-fetch dan di-cache: ${picklists.size} items")
            _picklists.value = picklists
            
            // Update picklist statuses untuk dialog
            updatePicklistStatuses(picklists)
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "🔥 Error fetching picklists: ${e.message}", e)
            throw e
        }
    }
    
    fun loadPicklistItems(picklistNo: String) {
        Log.i("ScanViewModel", "🔥 loadPicklistItems dipanggil untuk: $picklistNo")
        
        // Cek apakah sudah dalam proses loading
        if (isPicklistItemsLoading) {
            Log.i("ScanViewModel", "🔥 loadPicklistItems sudah dalam proses, skip")
            return
        }
        
        currentPicklistNumber = picklistNo
        
        viewModelScope.launch {
            try {
                isPicklistItemsLoading = true
                _isLoading.value = true
                Log.i("ScanViewModel", "🔥 loadPicklistItems mulai")
                
                // Cache-first strategy: Coba load dari cache dulu
                val cachedItems = cacheManager.getCachedArticlesForPicklist(picklistNo)
                if (cachedItems.isNotEmpty()) {
                    Log.i("ScanViewModel", "🔥 Menggunakan cached data untuk: $picklistNo (${cachedItems.size} items)")
                    _picklistItems.value = cachedItems
                    
                    // Load qty scan dari cache
                    val itemsWithQtyScan = loadQtyScanFromCache(cachedItems)
                    _picklistItems.value = itemsWithQtyScan
                    
                    // Background refresh untuk data terbaru
                    refreshPicklistDataInBackground(picklistNo)
                } else {
                    Log.i("ScanViewModel", "🔥 Cache kosong, fetch fresh data untuk: $picklistNo")
                    fetchPicklistItemsFromSupabase(picklistNo)
                }
                
                // Mulai subscription realtime untuk picklist ini
                startRealtimeSubscription(picklistNo)
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 loadPicklistItems error: ${e.message}", e)
                _errorMessage.value = "Gagal memuat detail picklist: ${e.message}"
            } finally {
                _isLoading.value = false
                isPicklistItemsLoading = false
                Log.i("ScanViewModel", "🔥 loadPicklistItems selesai")
            }
        }
    }
    
    /**
     * Background refresh untuk data terbaru tanpa blocking UI
     */
    private fun refreshPicklistDataInBackground(picklistNo: String) {
        viewModelScope.launch {
            try {
                Log.i("ScanViewModel", "🔥 Background refresh untuk: $picklistNo")
                fetchPicklistItemsFromSupabase(picklistNo)
                Log.i("ScanViewModel", "🔥 Background refresh selesai untuk: $picklistNo")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Background refresh error untuk $picklistNo: ${e.message}", e)
            }
        }
    }
    
    /**
     * Fetch picklist items dari Supabase dan update cache
     */
    private suspend fun fetchPicklistItemsFromSupabase(picklistNo: String) {
        try {
            Log.i("ScanViewModel", "🔥 Fetching fresh picklist items dari Supabase untuk: $picklistNo")
            val items = repository.getPicklistItems(picklistNo)
            
            // Load qty scan dari cache jika ada
            val itemsWithQtyScan = loadQtyScanFromCache(items)
            
            // Clear cache lama untuk picklist ini dan update dengan data fresh
            cacheManager.refreshPicklistCache(picklistNo, itemsWithQtyScan)
            cacheManager.setLastFetchTime(LocalDateTime.now())
            
            Log.i("ScanViewModel", "🔥 Fresh picklist items berhasil di-fetch dan di-cache: ${itemsWithQtyScan.size} items")
            _picklistItems.value = itemsWithQtyScan
            currentPicklistNumber = picklistNo
            Log.i("ScanViewModel", "🔥 currentPicklistNumber diset ke: $currentPicklistNumber")
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "🔥 Error fetching picklist items: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Prefetch semua data saat aplikasi dibuka
     * Implementasi konsep prefetch dengan stale-time dan realtime subscription
     */
    fun prefetchData() {
        Log.i("ScanViewModel", "🔥 Prefetch data dimulai - SMART STALE-TIME LOGIC")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Cek apakah cache masih fresh (belum 15 jam)
                if (cacheManager.isCacheFresh()) {
                    Log.i("ScanViewModel", "🔥 Cache masih fresh (< 15 jam), menggunakan cache")
                    
                    // Load data dari cache
                    val cachedPicklists = cacheManager.getCachedPicklists()
                    if (cachedPicklists != null && cachedPicklists.isNotEmpty()) {
                        _picklists.value = cachedPicklists
                        Log.i("ScanViewModel", "🔥 Loaded ${cachedPicklists.size} picklists dari cache")
                    }
                    
                    // Mulai realtime subscription untuk semua data
                    startGlobalRealtimeSubscription()
                    
                    Log.i("ScanViewModel", "🔥 Prefetch dari cache selesai")
                    
                } else {
                    Log.i("ScanViewModel", "🔥 Cache sudah stale (>= 15 jam), fetch fresh data")
                    
                    // Cleanup cache yang expired
                    cacheManager.cleanupExpiredCache()
                    
                    // Fetch fresh data dari Supabase
                    val picklists = repository.getPicklists()
                    cacheManager.cachePicklists(picklists)
                    _picklists.value = picklists
                    
                    Log.i("ScanViewModel", "🔥 Fresh picklists fetched: ${picklists.size}")
                    
                    // Fetch SEMUA articles untuk semua picklists
                    Log.i("ScanViewModel", "🔥 Fetching fresh articles untuk semua picklists...")
                    val allArticles = mutableListOf<PicklistItem>()
                    
                    picklists.forEach { picklistNo ->
                        try {
                            Log.i("ScanViewModel", "🔥 Fetching articles untuk picklist: $picklistNo")
                            val articles = repository.getPicklistItems(picklistNo)
                            allArticles.addAll(articles)
                            Log.i("ScanViewModel", "🔥 Articles untuk $picklistNo: ${articles.size} items")
                        } catch (e: Exception) {
                            Log.e("ScanViewModel", "🔥 Error fetching articles untuk $picklistNo: ${e.message}")
                        }
                    }
                    
                    // Cache semua articles fresh
                    cacheManager.cacheArticles(allArticles)
                    Log.i("ScanViewModel", "🔥 Total fresh articles di-cache: ${allArticles.size}")
                    
                    // Update timestamp
                    cacheManager.setLastFetchTime(LocalDateTime.now())
                    
                    // Mulai realtime subscription untuk semua data
                    startGlobalRealtimeSubscription()
                    
                    Log.i("ScanViewModel", "🔥 Prefetch fresh data selesai")
                }
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Prefetch error: ${e.message}", e)
                _errorMessage.value = "Gagal melakukan prefetch: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Force refresh cache (manual fetch via swipe to refresh)
     * Data lama dihapus dan diganti dengan data baru
     */
    fun forceRefreshCache() {
        Log.i("ScanViewModel", "🔥 Force refresh cache dimulai - CLEAR OLD DATA & FETCH NEW")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // HAPUS SEMUA DATA LAMA dari cache
                Log.i("ScanViewModel", "🔥 Clearing semua data lama dari cache...")
                cacheManager.clearAllCache()
                
                Log.i("ScanViewModel", "🔥 Manual fetch dari Supabase (swipe to refresh)...")
                
                // Fetch fresh data dari Supabase
                val picklists = repository.getPicklists()
                cacheManager.cachePicklists(picklists)
                _picklists.value = picklists
                
                Log.i("ScanViewModel", "🔥 Fresh picklists fetched: ${picklists.size}")
                
                // Fetch SEMUA articles untuk semua picklists
                Log.i("ScanViewModel", "🔥 Fetching fresh articles untuk semua picklists...")
                val allArticles = mutableListOf<PicklistItem>()
                
                picklists.forEach { picklistNo ->
                    try {
                        Log.i("ScanViewModel", "🔥 Fetching articles untuk picklist: $picklistNo")
                        val articles = repository.getPicklistItems(picklistNo)
                        allArticles.addAll(articles)
                        Log.i("ScanViewModel", "🔥 Articles untuk $picklistNo: ${articles.size} items")
                    } catch (e: Exception) {
                        Log.e("ScanViewModel", "🔥 Error fetching articles untuk $picklistNo: ${e.message}")
                    }
                }
                
                // Cache semua articles fresh
                cacheManager.cacheArticles(allArticles)
                Log.i("ScanViewModel", "🔥 Total fresh articles di-cache: ${allArticles.size}")
                
                // Update timestamp untuk force refresh
                cacheManager.setLastFetchTime(LocalDateTime.now())
                
                // Update picklist statuses untuk dialog
                updatePicklistStatuses(picklists)
                
                Log.i("ScanViewModel", "🔥 Force refresh cache selesai - Data lama dihapus, data baru di-cache")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Force refresh error: ${e.message}", e)
                _errorMessage.value = "Gagal melakukan force refresh: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Dapatkan info cache untuk debugging
     */
    fun getCacheInfo(): String {
        return cacheManager.getCacheInfo()
    }
    
    /**
     * Clear semua cache
     */
    fun clearCache() {
        cacheManager.clearCache()
        Log.i("ScanViewModel", "🔥 Cache dibersihkan")
    }
    
    /**
     * Mulai global realtime subscription untuk semua data
     */
    private fun startGlobalRealtimeSubscription() {
        Log.i("ScanViewModel", "🔥 Starting global realtime subscription")
        
        viewModelScope.launch {
            try {
                // Set callback untuk update cache
                realtimeService.setCacheUpdateCallback { cacheUpdate ->
                    handleCacheUpdate(cacheUpdate)
                }
                
                // Set callback untuk update picklist
                realtimeService.setPicklistUpdateCallback { picklistUpdate ->
                    handlePicklistUpdate(picklistUpdate)
                }
                
                // Subscribe ke perubahan picklist (semua picklist)
                realtimeService.subscribeToPicklists()
                
                // Update status koneksi
                _isRealtimeConnected.value = realtimeService.isConnected()
                
                Log.i("ScanViewModel", "🔥 Global realtime subscription started")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error starting global realtime subscription: ${e.message}", e)
                _isRealtimeConnected.value = false
            }
        }
    }
    
    /**
     * Mulai subscription realtime untuk picklist tertentu
     */
    private fun startRealtimeSubscription(picklistNo: String) {
        Log.i("ScanViewModel", "🔥 Starting realtime subscription for: $picklistNo")
        
        viewModelScope.launch {
            try {
                // Subscribe ke perubahan picklist scan untuk picklist tertentu
                realtimeService.subscribeToPicklistScans(picklistNo)
                
                // Update status koneksi
                _isRealtimeConnected.value = realtimeService.isConnected()
                
                Log.i("ScanViewModel", "🔥 Picklist-specific realtime subscription started")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error starting picklist realtime subscription: ${e.message}", e)
                _isRealtimeConnected.value = false
            }
        }
    }
    
    /**
     * Handle perubahan data dari realtime subscription
     */
    private fun handleRealtimeChange(change: PicklistScanChange) {
        Log.i("ScanViewModel", "🔥 Realtime change received: ${change.action} - ${change.articleName} ${change.size}")
        
        when (change.action) {
            "INSERT" -> {
                // Ada scan baru dari device lain, refresh data
                Log.i("ScanViewModel", "🔥 New scan detected from another device, refreshing data...")
                refreshPicklistData()
            }
            "UPDATE" -> {
                // Ada update scan, refresh data
                Log.i("ScanViewModel", "🔥 Scan updated from another device, refreshing data...")
                refreshPicklistData()
            }
            "DELETE" -> {
                // Ada scan yang dihapus, refresh data
                Log.i("ScanViewModel", "🔥 Scan deleted from another device, refreshing data...")
                refreshPicklistData()
            }
        }
    }
    
    /**
     * Handle picklist update dari realtime
     */
    private fun handlePicklistUpdate(picklistUpdate: PicklistUpdate) {
        Log.i("ScanViewModel", "🔥 Picklist update received: ${picklistUpdate.action} - ${picklistUpdate.articleName} ${picklistUpdate.size}")
        
        when (picklistUpdate.action) {
            "INSERT" -> {
                // Tambah picklist item baru ke cache
                val newItem = PicklistItem(
                    id = "", // ID akan di-generate oleh Supabase
                    noPicklist = picklistUpdate.noPicklist,
                    articleId = picklistUpdate.articleId,
                    articleName = picklistUpdate.articleName,
                    size = picklistUpdate.size,
                    productId = null,
                    qtyPl = picklistUpdate.qty,
                    qtyScan = 0,
                    createdAt = null,
                    warehouse = null,
                    tagStatus = null
                )
                
                cacheManager.addPicklistItemToCache(newItem)
                cacheManager.addPicklistToCache(picklistUpdate.noPicklist)
                cacheManager.updateCacheTimestamp()
                
                Log.i("ScanViewModel", "🔥 Picklist item added to cache via realtime")
            }
            "UPDATE" -> {
                // Update picklist item di cache
                val updatedItem = PicklistItem(
                    id = "", // ID akan di-generate oleh Supabase
                    noPicklist = picklistUpdate.noPicklist,
                    articleId = picklistUpdate.articleId,
                    articleName = picklistUpdate.articleName,
                    size = picklistUpdate.size,
                    productId = null,
                    qtyPl = picklistUpdate.qty,
                    qtyScan = 0,
                    createdAt = null,
                    warehouse = null,
                    tagStatus = null
                )
                
                cacheManager.updatePicklistItemInCache(updatedItem)
                cacheManager.updateCacheTimestamp()
                
                Log.i("ScanViewModel", "🔥 Picklist item updated in cache via realtime")
            }
            "DELETE" -> {
                // Hapus picklist item dari cache
                val itemToRemove = PicklistItem(
                    id = "", // ID akan di-generate oleh Supabase
                    noPicklist = picklistUpdate.noPicklist,
                    articleId = picklistUpdate.articleId,
                    articleName = picklistUpdate.articleName,
                    size = picklistUpdate.size,
                    productId = null,
                    qtyPl = picklistUpdate.qty,
                    qtyScan = 0,
                    createdAt = null,
                    warehouse = null,
                    tagStatus = null
                )
                
                cacheManager.removePicklistItemFromCache(itemToRemove)
                cacheManager.updateCacheTimestamp()
                
                Log.i("ScanViewModel", "🔥 Picklist item removed from cache via realtime")
            }
        }
        
        // Refresh UI dengan data terbaru dari cache
        refreshPicklistDataFromCache()
    }
    
    /**
     * Handle cache update dari realtime
     */
    private fun handleCacheUpdate(cacheUpdate: CacheUpdate) {
        Log.i("ScanViewModel", "🔥 Cache update received: ${cacheUpdate.action} - ${cacheUpdate.picklistScan.articleName} ${cacheUpdate.picklistScan.size}")
        
        when (cacheUpdate.action) {
            "ADD_SCAN" -> {
                // Tambah scan baru ke cache
                cacheManager.addScanToCache(cacheUpdate.picklistScan)
                cacheManager.updateCacheTimestamp() // Update timestamp untuk stale-time 15 jam
                Log.i("ScanViewModel", "🔥 Scan added to cache via realtime")
            }
            "UPDATE_SCAN" -> {
                // Update scan di cache
                cacheManager.updateScanInCache(cacheUpdate.picklistScan)
                cacheManager.updateCacheTimestamp() // Update timestamp untuk stale-time 15 jam
                Log.i("ScanViewModel", "🔥 Scan updated in cache via realtime")
            }
            "REMOVE_SCAN" -> {
                // Hapus scan dari cache
                cacheManager.removeScanFromCache(cacheUpdate.picklistScan)
                cacheManager.updateCacheTimestamp() // Update timestamp untuk stale-time 15 jam
                Log.i("ScanViewModel", "🔥 Scan removed from cache via realtime")
            }
        }
        
        // Refresh UI dengan data terbaru dari cache
        refreshPicklistDataFromCache()
    }
    
    /**
     * Refresh data picklist dari cache (bukan dari Supabase)
     */
    private fun refreshPicklistDataFromCache() {
        viewModelScope.launch {
            if (currentPicklistNumber.isNotEmpty()) {
                Log.i("ScanViewModel", "🔥 Refreshing picklist data from cache for: $currentPicklistNumber")
                
                // Ambil data dari cache
                val cachedItems = cacheManager.getCachedArticlesForPicklist(currentPicklistNumber)
                if (cachedItems.isNotEmpty()) {
                    // Load qty scan dari cache
                    val itemsWithQtyScan = loadQtyScanFromCache(cachedItems)
                    
                    // Update UI dengan data dari cache
                    _picklistItems.value = itemsWithQtyScan
                    
                    Log.i("ScanViewModel", "🔥 Picklist data refreshed from cache: ${itemsWithQtyScan.size} items")
                } else {
                    Log.i("ScanViewModel", "🔥 No cached data found for picklist: $currentPicklistNumber")
                }
            }
        }
    }
    
    /**
     * Refresh data picklist dari Supabase
     */
    private fun refreshPicklistData() {
        if (currentPicklistNumber.isNotEmpty()) {
            Log.i("ScanViewModel", "🔥 Refreshing picklist data for: $currentPicklistNumber")
            viewModelScope.launch {
                try {
                    fetchPicklistItemsFromSupabase(currentPicklistNumber)
                } catch (e: Exception) {
                    Log.e("ScanViewModel", "🔥 Error refreshing picklist data: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Stop realtime subscription
     */
    fun stopRealtimeSubscription() {
        Log.i("ScanViewModel", "🔥 Stopping realtime subscription")
        viewModelScope.launch {
            try {
                realtimeService.unsubscribeFromAll()
                _isRealtimeConnected.value = false
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error stopping realtime subscription: ${e.message}", e)
            }
        }
    }
    
    /**
     * Reconnect realtime service
     */
    fun reconnectRealtime() {
        Log.i("ScanViewModel", "🔥 Reconnecting realtime service")
        viewModelScope.launch {
            try {
                realtimeService.reconnect()
                startGlobalRealtimeSubscription()
                if (currentPicklistNumber.isNotEmpty()) {
                    startRealtimeSubscription(currentPicklistNumber)
                }
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error reconnecting realtime: ${e.message}", e)
            }
        }
    }
    
    fun addEpc(epc: String, rssi: Int = 0): Boolean {
        val isNewEpc = epcBuffer.add(epc)
        if (isNewEpc) {
            // EPC baru - gunakan postValue untuk background thread
            _scanCounter.postValue(epcBuffer.size)
            
            // Mulai auto-post timer jika belum aktif
            startAutoPostTimer()
        }
        return isNewEpc
    }
    
    /**
     * Mulai auto-post timer untuk memproses RFID setiap detik
     */
    private fun startAutoPostTimer() {
        if (autoPostTimer?.isActive == true) return
        
        autoPostTimer = viewModelScope.launch {
            while (isActive) {
                delay(autoPostInterval)
                if (epcBuffer.isNotEmpty()) {
                    processScannedEPCsAuto()
                }
            }
        }
    }
    
    /**
     * Hentikan auto-post timer
     */
    fun stopAutoPostTimer() {
        autoPostTimer?.cancel()
        autoPostTimer = null
    }
    
    /**
     * Proses RFID secara otomatis dan simpan ke Supabase jika kondisi terpenuhi
     */
    private suspend fun processScannedEPCsAuto() {
        if (epcBuffer.isEmpty()) return
        
        Log.i("ScanViewModel", "🔥 Auto-processing ${epcBuffer.size} EPC")
        
        try {
            // Ambil informasi produk dari Nirwana API
            val unprocessedEpc = epcBuffer.filter { epc ->
                !processedEpcBuffer.contains(epc)
            }.toList()
            
            if (unprocessedEpc.isEmpty()) return
            
            val scanResults = repository.getProductInfoFromRFID(unprocessedEpc)
            Log.i("ScanViewModel", "🔥 Auto-processed ${scanResults.size} items")
            
            // Update picklist items
            val currentItems = _picklistItems.value?.toMutableList() ?: mutableListOf()
            val updatedItems = mutableListOf<PicklistItem>()
            val processedEpcInBatch = mutableSetOf<String>()
            
            scanResults.forEach { scanResult ->
                if (processedEpcInBatch.contains(scanResult.rfid)) return@forEach
                
                val existingItem = currentItems.find { it.productId == scanResult.productId }
                
                if (existingItem != null) {
                    // Update quantity scan untuk item yang sudah ada
                    val updatedItem = existingItem.copy(qtyScan = existingItem.qtyScan + 1)
                    updatedItems.add(updatedItem)
                    currentItems.remove(existingItem)
                } else {
                    // Tambahkan item baru
                    val newItem = PicklistItem(
                        id = java.util.UUID.randomUUID().toString(),
                        noPicklist = currentPicklistNumber,
                        articleId = scanResult.articleId,
                        articleName = scanResult.articleName,
                        size = scanResult.size,
                        productId = scanResult.productId,
                        qtyPl = 0,
                        qtyScan = 1,
                        warehouse = scanResult.warehouse,
                        tagStatus = scanResult.tagStatus
                    )
                    updatedItems.add(newItem)
                }
                
                processedEpcInBatch.add(scanResult.rfid)
            }
            
            // Update items
            val finalItems = currentItems + updatedItems
            _picklistItems.postValue(finalItems)
            
            // Simpan EPC yang valid ke Supabase
            saveValidEPCsToSupabase(scanResults)
            
            // Update processed buffer
            processedEpcBuffer.addAll(processedEpcInBatch)
            
            // Cek apakah semua item sudah complete
            checkCompletionStatus()
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "🔥 Error in auto-process: ${e.message}", e)
        }
    }
    
    /**
     * Cek status completion dan trigger animasi jika semua complete
     */
    private fun checkCompletionStatus() {
        val items = _picklistItems.value ?: emptyList()
        val picklistItems = items.filter { it.qtyPl > 0 }
        
        if (picklistItems.isEmpty()) {
            _isComplete.value = false
            return
        }
        
        // Cek apakah semua item sudah complete (qtyScan == qtyPl)
        val allComplete = picklistItems.all { item ->
            !item.isNAItem() && item.qtyScan == item.qtyPl
        }
        
        if (allComplete && !(_isComplete.value ?: false)) {
            Log.i("ScanViewModel", "🔥 Semua item sudah complete! Trigger animasi")
            _isComplete.value = true
            
            // Hentikan auto-post timer
            stopAutoPostTimer()
        } else {
            _isComplete.value = false
        }
    }
    
    /**
     * Kembali ke halaman input picklist
     */
    fun returnToPicklistInput() {
        // Clear semua data
        epcBuffer.clear()
        processedEpcBuffer.clear()
        _scanCounter.value = 0
        _picklistItems.value = emptyList()
        _isComplete.value = false
        currentPicklistNumber = ""
        
        Log.i("ScanViewModel", "🔥 Data cleared, siap untuk picklist baru")
    }
    
    /**
     * Simpan EPC yang valid ke Supabase
     * Hanya simpan EPC yang:
     * 1. Ada di picklist (qtyPl > 0)
     * 2. Tidak NA (productId dan articleId valid)
     * 3. Tidak over-scan (qtyScan <= qtyPl)
     * 
     * LOGIKA BARU: Jika ada over-scan untuk suatu artikel, SEMUA EPC untuk artikel tersebut TIDAK DISIMPAN
     */
    private suspend fun saveValidEPCsToSupabase(scanResults: List<ScanResult>) {
        val currentItems = _picklistItems.value ?: emptyList()
        
        // Kelompokkan scan results berdasarkan productId
        val scanResultsByProduct = scanResults.groupBy { it.productId }
        
        scanResultsByProduct.forEach { (productId, productScanResults) ->
            val existingItem = currentItems.find { it.productId == productId }
            
            // Hanya proses jika item ada di picklist (qtyPl > 0)
            if (existingItem != null && existingItem.qtyPl > 0) {
                // Hitung total scan untuk artikel ini setelah batch ini
                val currentQtyScan = existingItem.qtyScan
                val newQtyScan = currentQtyScan + productScanResults.size
                
                // Cek apakah akan terjadi over-scan
                val willOverScan = newQtyScan > existingItem.qtyPl
                
                if (willOverScan) {
                    // OVER-SCAN DETECTED: Tidak simpan SEMUA EPC untuk artikel ini
                    Log.i("ScanViewModel", "🔥 OVER-SCAN DETECTED untuk ${existingItem.articleName}: qtyPl=${existingItem.qtyPl}, qtyScan akan menjadi $newQtyScan")
                    Log.i("ScanViewModel", "🔥 SEMUA ${productScanResults.size} EPC untuk artikel ini TIDAK DISIMPAN")
                    productScanResults.forEach { scanResult ->
                        Log.i("ScanViewModel", "🔥 EPC ${scanResult.rfid} tidak disimpan - over-scan untuk ${scanResult.articleName}")
                    }
                } else {
                    // Tidak over-scan: Simpan semua EPC untuk artikel ini
                    Log.i("ScanViewModel", "🔥 Tidak over-scan untuk ${existingItem.articleName}: qtyPl=${existingItem.qtyPl}, qtyScan akan menjadi $newQtyScan")
                    
                    productScanResults.forEach { scanResult ->
                        // Cek kondisi valid (tidak NA)
                        if (!existingItem.isNAItem()) {
                            val picklistScan = PicklistScan(
                                noPicklist = currentPicklistNumber,
                                productId = scanResult.productId,
                                articleId = scanResult.articleId,
                                articleName = scanResult.articleName,
                                size = scanResult.size,
                                epc = scanResult.rfid,
                                notrans = null
                            )
                            
                            try {
                                repository.savePicklistScan(picklistScan)
                                Log.i("ScanViewModel", "🔥 Auto-saved EPC: ${scanResult.rfid} untuk ${scanResult.articleName}")
                            } catch (e: Exception) {
                                Log.e("ScanViewModel", "🔥 Error auto-saving EPC ${scanResult.rfid}: ${e.message}", e)
                            }
                        } else {
                            Log.i("ScanViewModel", "🔥 EPC ${scanResult.rfid} tidak disimpan - NA item untuk ${scanResult.articleName}")
                        }
                    }
                }
            }
        }
    }
    
        fun processScannedEPCs() {
        LogUtils.i("ScanViewModel", "🔥 processScannedEPCs dipanggil")
        if (epcBuffer.isEmpty()) {
            return
        }
        
        LogUtils.i("ScanViewModel", "🔥 Memproses ${epcBuffer.size} EPC")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                LogUtils.i("ScanViewModel", "🔥 Mulai memproses EPC...")
                
                // Ambil informasi produk dari Nirwana API
                // Hanya proses EPC yang belum pernah diproses
                val unprocessedEpc = epcBuffer.filter { epc ->
                    !processedEpcBuffer.contains(epc)
                }.toList()
                
                if (unprocessedEpc.isEmpty()) {
                    Log.i("ScanViewModel", "🔥 Semua EPC sudah diproses, tidak ada yang baru")
                    return@launch
                }
                
                LogUtils.i("ScanViewModel", "🔥 API: ${unprocessedEpc.size} RFID")
                val scanResults = repository.getProductInfoFromRFID(unprocessedEpc)
                LogUtils.i("ScanViewModel", "🔥 Results: ${scanResults.size} items")
                
                // Update picklist items berdasarkan hasil scan
                val currentItems = _picklistItems.value?.toMutableList() ?: mutableListOf()
                val updatedItems = mutableListOf<PicklistItem>()
                
                Log.i("ScanViewModel", "🔥 Update ${currentItems.size} items")
                
                // Buat set untuk tracking EPC yang sudah diproses dalam batch ini
                val processedEpcInBatch = mutableSetOf<String>()
                
                scanResults.forEach { scanResult ->
                    Log.i("ScanViewModel", "🔥 Memproses scanResult: RFID=${scanResult.rfid}, ProductID=${scanResult.productId}, RSSI=${scanResult.rssi}")
                    
                    // Cek apakah EPC ini sudah diproses dalam batch ini
                    if (processedEpcInBatch.contains(scanResult.rfid)) {
                        Log.i("ScanViewModel", "🔥 EPC ${scanResult.rfid} sudah diproses dalam batch ini, skip")
                        return@forEach
                    }
                    
                    val existingItem = currentItems.find { it.productId == scanResult.productId }
                    
                    if (existingItem != null) {
                        // Update quantity scan untuk item yang sudah ada
                        Log.i("ScanViewModel", "🔥 Item sudah ada, update qty scan dari ${existingItem.qtyScan} ke ${existingItem.qtyScan + 1}")
                        val updatedItem = existingItem.copy(qtyScan = existingItem.qtyScan + 1)
                        updatedItems.add(updatedItem)
                        currentItems.remove(existingItem)
                        
                        // Mark EPC sebagai sudah diproses
                        processedEpcInBatch.add(scanResult.rfid)
                    } else {
                        // Tambahkan item baru dengan qty_pl = 0 dan qty_scan = 1
                        Log.i("ScanViewModel", "🔥 Item baru, menambahkan: ${scanResult.articleName} ${scanResult.size} (RSSI: ${scanResult.rssi})")
                        val newItem = PicklistItem(
                            id = java.util.UUID.randomUUID().toString(), // Generate UUID
                            noPicklist = currentPicklistNumber,
                            articleId = scanResult.articleId,
                            articleName = scanResult.articleName,
                            size = scanResult.size,
                            productId = scanResult.productId,
                            qtyPl = 0,
                            qtyScan = 1,
                            warehouse = scanResult.warehouse,
                            tagStatus = scanResult.tagStatus
                        )
                        updatedItems.add(newItem)
                        
                        // Mark EPC sebagai sudah diproses
                        processedEpcInBatch.add(scanResult.rfid)
                    }
                    
                                         // Simpan ke Supabase - HAPUS bagian ini karena akan disimpan saat Submit
                     // Log.i("ScanViewModel", "🔥 Menyimpan ke Supabase: ${scanResult.articleName}")
                     // val picklistScan = PicklistScan(
                     //     picklistNumber = currentPicklistNumber,
                     //     epcList = listOf(scanResult.rfid),
                     //     productId = scanResult.productId,
                     //     articleId = scanResult.articleId,
                     //     articleName = scanResult.articleName,
                     //     size = scanResult.size,
                     //     scannedQuantity = 1
                     // )
                     // repository.savePicklistScan(picklistScan)
                     
                    // Simpan EPC ke buffer yang sudah diproses untuk disimpan saat Submit
                    processedEpcBuffer.add(scanResult.rfid)
                }
                
                // Tambahkan item yang tidak di-scan
                updatedItems.addAll(currentItems)
                
                Log.i("ScanViewModel", "🔥 Processed ${processedEpcInBatch.size} EPCs")
                
                // Update processed EPC buffer
                processedEpcBuffer.addAll(processedEpcInBatch)
                
                _picklistItems.value = updatedItems
                
                // Update qty mismatch items
                updateQtyMismatchItems(updatedItems)
                
                // JANGAN clear buffer di sini - buffer akan di-clear hanya saat Submit
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error saat memproses EPC: ${e.message}", e)
                _errorMessage.value = "Gagal memproses EPC: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.i("ScanViewModel", "🔥 processScannedEPCs selesai")
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun getTotalQty(): Int {
        return _picklistItems.value?.sumOf { it.qtyPl } ?: 0
    }
    
    fun getScannedQty(): Int {
        return _picklistItems.value?.sumOf { it.qtyScan } ?: 0
    }
    
    fun getRemainingQty(): Int {
        return getTotalQty() - getScannedQty()
    }
    
    /**
     * Update qty mismatch items (item yang tidak sesuai)
     */
    private fun updateQtyMismatchItems(items: List<PicklistItem>) {
        val mismatchItems = items.filter { !it.isComplete() }
        _qtyMismatchItems.value = mismatchItems
        Log.i("ScanViewModel", "🔥 Qty mismatch items updated: ${mismatchItems.size} items")
    }
    
    /**
     * Get filtered items - hide completed items (qty_scan == qty_pl)
     */
    fun getFilteredItems(): List<PicklistItem> {
        val items = _picklistItems.value ?: emptyList()
        
        // Filter: hanya tampilkan item yang BELUM selesai menggunakan isComplete()
        val incompleteItems = items.filter { !it.isComplete() }
        
        // Urutkan: item tidak ada di picklist di atas, lalu item picklist yang belum selesai
        val sortedItems = incompleteItems.sortedWith(compareBy<PicklistItem> { it.qtyPl == 0 }.thenBy { it.articleName })
        
        // Log untuk debugging
        Log.i("ScanViewModel", "🔥 getFilteredItems: Total=${items.size}, Incomplete=${incompleteItems.size}, Final=${sortedItems.size}")
        
        // Debug log untuk setiap item
        items.forEach { item ->
            val status = item.getQtyStatus()
            val isComplete = item.isComplete()
            Log.d("ScanViewModel", "🔥 Item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}, Status=$status, isComplete=$isComplete")
        }
        
        return sortedItems
    }
    
    /**
     * Remove RFID for specific article - hapus artikel non-picklist, reset qty scan untuk artikel picklist
     */
    fun removeRfidForArticle(articleName: String, size: String) {
        Log.i("ScanViewModel", "🔥 removeRfidForArticle dipanggil: $articleName $size")
        
        try {
            val currentItems = _picklistItems.value?.toMutableList() ?: mutableListOf()
            
            // Cari item yang akan dihapus/direset
            val targetItem = currentItems.find { item ->
                item.articleName == articleName && item.size == size
            }
            
            if (targetItem != null) {
                if (targetItem.qtyPl == 0) {
                    // Artikel non-picklist - hapus sepenuhnya
                    Log.i("ScanViewModel", "🔥 Menghapus artikel non-picklist: ${targetItem.articleName} ${targetItem.size}")
                    currentItems.remove(targetItem)
                    Log.i("ScanViewModel", "🔥 Artikel non-picklist berhasil dihapus: $articleName $size")
                } else {
                    // Artikel picklist - reset qty scan saja
                    Log.i("ScanViewModel", "🔥 Reset qty scan untuk artikel picklist: ${targetItem.articleName} ${targetItem.size} dari ${targetItem.qtyScan} ke 0")
                    val updatedItem = targetItem.copy(qtyScan = 0)
                    val index = currentItems.indexOf(targetItem)
                    currentItems[index] = updatedItem
                    Log.i("ScanViewModel", "🔥 Qty scan artikel picklist berhasil direset: $articleName $size")
                }
                
                _picklistItems.value = currentItems
                updateQtyMismatchItems(currentItems)
                
                Log.i("ScanViewModel", "🔥 Swipe-to-delete berhasil untuk article: $articleName $size")
            } else {
                Log.w("ScanViewModel", "🔥 Artikel tidak ditemukan: $articleName $size")
            }
            
        } catch (e: Exception) {
            Log.e("ScanViewModel", "🔥 Error saat swipe-to-delete: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Clear RFID buffer and reset qty scan - artikel dari load picklist tetap ada
     */
    fun clearAllData() {
        Log.i("ScanViewModel", "🔥 clearAllData dipanggil")
        
        // Clear RFID buffer
        val bufferSize = epcBuffer.size
        epcBuffer.clear()
        _scanCounter.value = 0
        
        // Clear processed EPC buffer
        val processedBufferSize = processedEpcBuffer.size
        processedEpcBuffer.clear()
        
        // Reset qty scan untuk semua items, tapi artikel tetap ada
        val currentItems = _picklistItems.value?.toMutableList() ?: mutableListOf()
        val resetItems = currentItems.map { item ->
            Log.i("ScanViewModel", "🔥 Reset qty scan untuk ${item.articleName} ${item.size} dari ${item.qtyScan} ke 0")
            item.copy(qtyScan = 0)
        }
        _picklistItems.value = resetItems
        
        // Update qty mismatch items
        updateQtyMismatchItems(resetItems)
        
        Log.i("ScanViewModel", "🔥 Clear RFID selesai: ${bufferSize} EPC dihapus, ${processedBufferSize} processed EPC dihapus, ${resetItems.size} artikel tetap ada dengan qty scan = 0")
    }
    
    /**
     * Clear RFID buffer and remove non-picklist articles - hanya artikel dari load picklist yang tetap
     */
    fun clearRfidAndNonPicklistArticles() {
        Log.i("ScanViewModel", "🔥 clearRfidAndNonPicklistArticles dipanggil")
        
        // Clear RFID buffer
        val bufferSize = epcBuffer.size
        epcBuffer.clear()
        _scanCounter.value = 0
        
        // Clear processed EPC buffer
        val processedBufferSize = processedEpcBuffer.size
        processedEpcBuffer.clear()
        
        // Hanya simpan artikel yang ada di picklist (qtyPl > 0) dan reset qty scan
        val currentItems = _picklistItems.value?.toMutableList() ?: mutableListOf()
        val picklistItems = currentItems.filter { item ->
            item.qtyPl > 0 // Hanya artikel yang ada di picklist
        }.map { item ->
            Log.i("ScanViewModel", "🔥 Reset qty scan untuk artikel picklist: ${item.articleName} ${item.size} dari ${item.qtyScan} ke 0")
            item.copy(qtyScan = 0)
        }
        
        val removedItems = currentItems.filter { it.qtyPl == 0 }
        Log.i("ScanViewModel", "🔥 Menghapus ${removedItems.size} artikel non-picklist: ${removedItems.map { "${it.articleName} ${it.size}" }}")
        
        _picklistItems.value = picklistItems
        
        // Update qty mismatch items
        updateQtyMismatchItems(picklistItems)
        
        Log.i("ScanViewModel", "🔥 Clear RFID & non-picklist selesai: ${bufferSize} EPC dihapus, ${processedBufferSize} processed EPC dihapus, ${removedItems.size} artikel non-picklist dihapus, ${picklistItems.size} artikel picklist tetap")
    }
    
    /**
     * Submit scanned EPCs - clear buffer dan simpan ke Supabase
     */
    fun submitScannedEPCs() {
        Log.i("ScanViewModel", "🔥 submitScannedEPCs dipanggil")
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.i("ScanViewModel", "🔥 Mulai menyimpan data ke Supabase...")
                
                // Ambil semua item yang sudah di-scan
                val currentItems = _picklistItems.value ?: emptyList()
                val scannedItems = currentItems.filter { it.qtyScan > 0 }
                
                Log.i("ScanViewModel", "🔥 Menyimpan ${scannedItems.size} item ke Supabase")
                
                // Simpan setiap EPC sebagai record terpisah
                scannedItems.forEach { item ->
                    Log.i("ScanViewModel", "🔥 Menyimpan item: ${item.articleName} ${item.size} (qty scan: ${item.qtyScan})")
                    
                    // Simpan setiap EPC sebagai record terpisah sesuai struktur tabel
                    repeat(item.qtyScan) { index ->
                        val epc = if (processedEpcBuffer.isNotEmpty() && index < processedEpcBuffer.size) {
                            processedEpcBuffer[index]
                        } else {
                            "EPC_${item.id}_$index" // Fallback jika tidak ada EPC
                        }
                        
                        val picklistScan = PicklistScan(
                            noPicklist = currentPicklistNumber,
                            productId = item.productId ?: "UNKNOWN",
                            articleId = item.articleId,
                            articleName = item.articleName,
                            size = item.size,
                            epc = epc,
                            notrans = null
                        )
                        
                        try {
                            repository.savePicklistScan(picklistScan)
                            Log.i("ScanViewModel", "🔥 Berhasil menyimpan EPC: $epc untuk ${item.articleName}")
                        } catch (e: Exception) {
                            Log.e("ScanViewModel", "🔥 Error menyimpan EPC $epc untuk ${item.articleName}: ${e.message}", e)
                        }
                    }
                }
                
                // Clear buffer setelah submit
                val bufferSize = epcBuffer.size
                val processedBufferSize = processedEpcBuffer.size
                epcBuffer.clear()
                processedEpcBuffer.clear()
                _scanCounter.value = 0
                
                // Update cache dengan qty scan yang baru
                updateCacheWithScannedQty()
                
                Log.i("ScanViewModel", "🔥 Submit selesai: ${scannedItems.size} item disimpan, ${bufferSize} EPC dibersihkan, ${processedBufferSize} processed EPC dibersihkan")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error saat submit: ${e.message}", e)
                _errorMessage.value = "Gagal menyimpan data: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.i("ScanViewModel", "🔥 submitScannedEPCs selesai")
            }
        }
    }
    
    /**
     * Check if all articles are complete (qty scan == qty planned)
     * Tombol Submit aktif jika:
     * - qty scan kurang (belum complete)
     * - qty scan cocok (sudah complete)
     * Tombol Submit tidak aktif jika:
     * - ada item dengan nilai NA
     * - ada item dengan qty scan lebih (plus)
     */
    fun areAllArticlesComplete(): Boolean {
        val items = _picklistItems.value ?: emptyList()
        val picklistItems = items.filter { it.qtyPl > 0 } // Hanya artikel dari picklist
        
        Log.i("ScanViewModel", "🔥 === ARE ALL ARTIKEL COMPLETE DEBUG ===")
        Log.i("ScanViewModel", "🔥 Total items: ${items.size}")
        Log.i("ScanViewModel", "🔥 Picklist items (qtyPl > 0): ${picklistItems.size}")
        
        if (picklistItems.isEmpty()) {
            Log.i("ScanViewModel", "🔥 Tidak ada artikel picklist, Submit tidak tersedia")
            return false
        }
        
        // Cek apakah ada item dengan nilai NA
        val naItems = picklistItems.filter { it.isNAItem() }
        if (naItems.isNotEmpty()) {
            Log.i("ScanViewModel", "🔥 Ada ${naItems.size} item dengan nilai NA, Submit tidak tersedia")
            naItems.forEach { item ->
                Log.i("ScanViewModel", "🔥 Item NA: ${item.articleName} ${item.size} - productId: ${item.productId}, articleId: ${item.articleId}")
            }
            return false
        }
        
        // Cek apakah ada item dengan qty scan lebih (plus)
        val plusItems = picklistItems.filter { it.qtyScan > it.qtyPl }
        if (plusItems.isNotEmpty()) {
            Log.i("ScanViewModel", "🔥 Ada ${plusItems.size} item dengan qty scan lebih (plus), Submit tidak tersedia")
            plusItems.forEach { item ->
                Log.i("ScanViewModel", "🔥 Item Plus: ${item.articleName} ${item.size} - qtyPl: ${item.qtyPl}, qtyScan: ${item.qtyScan}")
            }
            return false
        }
        
        // Jika tidak ada item NA dan tidak ada item plus, maka Submit aktif
        Log.i("ScanViewModel", "🔥 Tidak ada item NA dan tidak ada item plus, Submit tersedia")
        
        // Debug logging untuk setiap item
        Log.i("ScanViewModel", "🔥 === DETAIL ARTIKEL PICKLIST ===")
        picklistItems.forEach { item ->
            val status = item.getQtyStatus()
            val isComplete = item.isComplete()
            Log.i("ScanViewModel", "🔥 Artikel: ${item.articleName} ${item.size}, qtyPl: ${item.qtyPl}, qtyScan: ${item.qtyScan}, Status: $status, Complete: $isComplete")
        }
        Log.i("ScanViewModel", "🔥 === END DETAIL ARTIKEL ===")
        
        Log.i("ScanViewModel", "🔥 Submit tersedia: true (tidak ada NA dan tidak ada plus)")
        Log.i("ScanViewModel", "🔥 === END ARE ALL ARTIKEL COMPLETE DEBUG ===")
        
        return true
    }
    
    /**
     * Manual refresh cache untuk picklist tertentu
     * Bisa dipanggil dari UI untuk memaksa update data
     */
    fun refreshPicklistData(picklistNo: String) {
        Log.i("ScanViewModel", "🔥 Manual refresh picklist data untuk: $picklistNo")
        viewModelScope.launch {
            try {
                val items = repository.getPicklistItems(picklistNo)
                cacheManager.refreshPicklistCache(picklistNo, items)
                
                // Update UI jika ini adalah picklist yang sedang aktif
                if (currentPicklistNumber == picklistNo) {
                    _picklistItems.value = items
                }
                
                Log.i("ScanViewModel", "🔥 Manual refresh selesai untuk $picklistNo: ${items.size} items")
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error manual refresh untuk $picklistNo: ${e.message}", e)
                _errorMessage.value = "Gagal refresh data: ${e.message}"
            }
        }
    }
    
    /**
     * Manual refresh semua cache
     * Bisa dipanggil dari UI untuk memaksa update semua data
     */
    fun refreshAllCache() {
        Log.i("ScanViewModel", "🔥 Manual refresh semua cache")
        viewModelScope.launch {
            try {
                // Refresh picklists
                val picklists = repository.getPicklists()
                cacheManager.cachePicklists(picklists)
                _picklists.value = picklists
                
                // Refresh articles untuk semua picklists
                val allArticles = mutableListOf<PicklistItem>()
                picklists.forEach { picklistNo ->
                    try {
                        val articles = repository.getPicklistItems(picklistNo)
                        allArticles.addAll(articles)
                    } catch (e: Exception) {
                        Log.e("ScanViewModel", "🔥 Error fetching articles untuk $picklistNo: ${e.message}")
                    }
                }
                
                cacheManager.cacheArticles(allArticles)
                cacheManager.setLastFetchTime(LocalDateTime.now())
                
                // Update picklist statuses
                updatePicklistStatuses(picklists)
                
                Log.i("ScanViewModel", "🔥 Manual refresh semua cache selesai:")
                Log.i("ScanViewModel", "   Picklists: ${picklists.size}")
                Log.i("ScanViewModel", "   Articles: ${allArticles.size}")
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error manual refresh semua cache: ${e.message}", e)
                _errorMessage.value = "Gagal refresh semua data: ${e.message}"
            }
        }
    }
    
    /**
     * Update picklist statuses dengan data dari cache dan Supabase
     */
    private suspend fun updatePicklistStatuses(picklists: List<String>) {
        Log.i("ScanViewModel", "🔥 Update picklist statuses untuk ${picklists.size} picklists")
        
        val statuses = mutableListOf<PicklistStatus>()
        
        picklists.forEach { picklistNo ->
            try {
                // Dapatkan data picklist items dari cache
                val cachedItems = cacheManager.getCachedArticles()?.filter { it.noPicklist == picklistNo } ?: emptyList()
                
                if (cachedItems.isNotEmpty()) {
                    val totalQty = cachedItems.sumOf { it.qtyPl }
                    val scannedQty = cachedItems.sumOf { it.qtyScan }
                    val remainingQty = totalQty - scannedQty
                    val isScanned = scannedQty > 0
                    
                    val status = PicklistStatus(
                        picklistNumber = picklistNo,
                        isScanned = isScanned,
                        remainingQty = remainingQty,
                        totalQty = totalQty,
                        scannedQty = scannedQty,
                        lastScanTime = if (isScanned) LocalDateTime.now().toString() else null
                    )
                    
                    statuses.add(status)
                    Log.i("ScanViewModel", "🔥 Picklist $picklistNo: total=$totalQty, scanned=$scannedQty, remaining=$remainingQty, isScanned=$isScanned")
                } else {
                    // Jika tidak ada data di cache, buat status default
                    val status = PicklistStatus(
                        picklistNumber = picklistNo,
                        isScanned = false,
                        remainingQty = 0,
                        totalQty = 0,
                        scannedQty = 0
                    )
                    statuses.add(status)
                    Log.i("ScanViewModel", "🔥 Picklist $picklistNo: tidak ada data di cache")
                }
                
            } catch (e: Exception) {
                Log.e("ScanViewModel", "🔥 Error update status untuk $picklistNo: ${e.message}")
            }
        }
        
        _picklistStatuses.value = statuses
        Log.i("ScanViewModel", "🔥 Picklist statuses updated: ${statuses.size} statuses")
    }
    
    /**
     * Get picklist statuses untuk UI
     */
    fun getPicklistStatuses(): List<PicklistStatus> {
        return _picklistStatuses.value ?: emptyList()
    }
    
    /**
     * Update cache dengan qty scan yang baru setelah Submit
     */
    private fun updateCacheWithScannedQty() {
        Log.i("ScanViewModel", "🔥 Update cache dengan qty scan yang baru")
        
        val currentItems = _picklistItems.value ?: emptyList()
        if (currentItems.isNotEmpty()) {
            // Update cache dengan data yang sudah di-scan
            cacheManager.cacheArticles(currentItems)
            
            // Update picklist statuses
            val picklists = _picklists.value ?: emptyList()
            if (picklists.isNotEmpty()) {
                viewModelScope.launch {
                    updatePicklistStatuses(picklists)
                }
            }
            
            Log.i("ScanViewModel", "🔥 Cache updated dengan ${currentItems.size} items")
        }
    }
    
    /**
     * Load qty scan dari cache untuk items yang sudah pernah di-scan
     * Juga fetch dari tabel picklist_scan jika data tidak up to date
     */
    private suspend fun loadQtyScanFromCache(freshItems: List<PicklistItem>): List<PicklistItem> {
        Log.i("ScanViewModel", "🔥 Load qty scan dari cache untuk ${freshItems.size} items")
        
        val cachedItems = cacheManager.getCachedArticles() ?: emptyList()
        val itemsWithQtyScan = mutableListOf<PicklistItem>()
        
        // Fetch data dari tabel picklist_scan untuk picklist ini
        val picklistScans = try {
            if (currentPicklistNumber.isNotEmpty()) {
                Log.i("ScanViewModel", "🔥 Fetching data dari tabel picklist_scan untuk: $currentPicklistNumber")
                repository.getPicklistScans(currentPicklistNumber)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ScanViewModel", "🔥 Error fetching picklist scans: ${e.message}", e)
            emptyList()
        }
        
        Log.i("ScanViewModel", "🔥 Ditemukan ${picklistScans.size} scan records dari Supabase")
        
        freshItems.forEach { freshItem ->
            // Cari item yang sama di cache berdasarkan article_name dan size
            val cachedItem = cachedItems.find { cached ->
                cached.articleName == freshItem.articleName && 
                cached.size == freshItem.size &&
                cached.noPicklist == freshItem.noPicklist
            }
            
            // Hitung qty scan dari tabel picklist_scan berdasarkan article_name dan size
            val scanQty = picklistScans.count { scan ->
                scan.getString("article_name") == freshItem.articleName &&
                scan.getString("size") == freshItem.size
            }
            
            val cacheQty = cachedItem?.qtyScan ?: 0
            
            // Gunakan qty scan yang lebih tinggi antara cache dan Supabase
            val finalQtyScan = maxOf(scanQty, cacheQty)
            
            if (finalQtyScan > 0) {
                val itemWithQtyScan = freshItem.copy(qtyScan = finalQtyScan)
                itemsWithQtyScan.add(itemWithQtyScan)
                
                if (scanQty > cacheQty) {
                    Log.d("ScanViewModel", "🔥 Supabase: ${freshItem.articleName} = $scanQty")
                }
            } else {
                // Gunakan item fresh tanpa qty scan
                itemsWithQtyScan.add(freshItem)
            }
        }
        
        Log.i("ScanViewModel", "🔥 Load qty scan selesai: ${itemsWithQtyScan.size} items")
        return itemsWithQtyScan
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Stop realtime subscription
        stopRealtimeSubscription()
        
        // Cleanup realtime service
        realtimeService.cleanup()
        
        Log.i("ScanViewModel", "🔥 ScanViewModel cleared")
    }
}

/**
 * Data class untuk statistik completion
 */
data class CompletionStats(
    val total: Int,
    val completed: Int,
    val incomplete: Int,
    val completionPercentage: Int
)