package com.example.cekpicklist.repository

import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistStatus
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.BatchSupabaseService
import com.example.cekpicklist.cache.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import com.example.cekpicklist.viewmodel.Quadruple
import com.example.cekpicklist.viewmodel.Sextuple

class Repository(private val context: android.content.Context? = null) {
    private val supabaseService = SupabaseService()
    private val nirwanaApiService = NirwanaApiService()
    private val batchSupabaseService = BatchSupabaseService()
    private val cacheManager = CacheManager(context)
    
    // Enhanced Repository untuk Local-First strategy
    private val enhancedRepository = if (context != null) EnhancedRepository(context) else null
    
    // Coroutine scope untuk background operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Tracking untuk mendeteksi query berulang
    private val queryCallCounts = mutableMapOf<String, Int>()
    private val lastQueryTimes = mutableMapOf<String, Long>()
    
    companion object {
        private const val TAG = "Repository"
        private const val DUPLICATE_QUERY_THRESHOLD_MS = 1000L // 1 detik
    }
    
    /**
     * Track query calls untuk mendeteksi duplikasi
     */
    private fun trackQueryCall(queryType: String, picklistNo: String = "") {
        val key = "$queryType:$picklistNo"
        val currentTime = System.currentTimeMillis()
        
        val callCount = queryCallCounts.getOrDefault(key, 0) + 1
        val lastTime = lastQueryTimes.getOrDefault(key, 0L)
        
        queryCallCounts[key] = callCount
        lastQueryTimes[key] = currentTime
        
        val timeSinceLastCall = currentTime - lastTime
        
        if (callCount > 1 && timeSinceLastCall < DUPLICATE_QUERY_THRESHOLD_MS) {
            Log.w(TAG, "⚠️ DUPLICATE QUERY DETECTED: $queryType for '$picklistNo' (call #$callCount, ${timeSinceLastCall}ms since last call)")
        } else if (callCount > 3) {
            Log.w(TAG, "⚠️ FREQUENT QUERY: $queryType for '$picklistNo' (call #$callCount)")
        } else {
            Log.d(TAG, "📊 Query tracked: $queryType for '$picklistNo' (call #$callCount)")
        }
    }
    
    /**
     * OPTIMASI: Get picklists dengan cache dan incremental update
     */
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getPicklists")
            
            Log.d(TAG, "🔥 Getting picklist numbers list")
            
            // Gunakan Enhanced Repository jika tersedia (Local-First strategy)
            if (enhancedRepository != null) {
                Log.d(TAG, "📱 Using Enhanced Repository (Local-First strategy)")
                return@withContext enhancedRepository.getPicklists()
            }
            
            // Fallback ke logic lama jika Enhanced Repository tidak tersedia
            Log.d(TAG, "⚠️ Using legacy cache logic (Enhanced Repository not available)")
            
            // Cek cache dulu
            val cachedPicklists = cacheManager.getAllPicklists()
            if (cachedPicklists != null) {
                Log.d(TAG, "✅ Using cached picklists: ${cachedPicklists.size} items")
                
                // Lakukan background refresh untuk update incremental
                repositoryScope.launch {
                    try {
                        Log.d(TAG, "🔄 Background refresh for incremental update: all picklists")
                        val freshPicklists = supabaseService.getPicklists()
                        cacheManager.updateAllPicklistsIncremental(freshPicklists)
                        Log.d(TAG, "✅ Background incremental update completed for all picklists")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Background incremental update failed for all picklists: ${e.message}")
                    }
                }
                
                return@withContext cachedPicklists
            }
            
            // Jika tidak ada di cache, fetch dari Supabase
            Log.d(TAG, "🔥 Fetching picklists from Supabase...")
            val picklists = supabaseService.getPicklists()
            
            // Simpan ke cache dengan incremental update
            cacheManager.updateAllPicklistsIncremental(picklists)
            
            Log.d(TAG, "✅ Fetched and cached ${picklists.size} picklists")
            picklists
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklists: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMASI: Get picklist items dengan Local-First strategy
     */
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getPicklistItems", picklistNo)
            
            Log.d(TAG, "🔥 Getting picklist items for: $picklistNo")
            
            // Gunakan Enhanced Repository jika tersedia (Local-First strategy)
            if (enhancedRepository != null) {
                Log.d(TAG, "📱 Using Enhanced Repository (Local-First strategy)")
                return@withContext enhancedRepository.getPicklistItems(picklistNo)
            }
            
            // Fallback ke logic lama jika Enhanced Repository tidak tersedia
            Log.d(TAG, "⚠️ Using legacy cache logic (Enhanced Repository not available)")
            
            // Cek cache dulu
            val cachedItems = cacheManager.getPicklistItems(picklistNo)
            if (cachedItems != null) {
                Log.d(TAG, "✅ Using cached items for $picklistNo: ${cachedItems.size} items")
                
                // Lakukan background refresh dengan smart update
                repositoryScope.launch {
                    try {
                        Log.d(TAG, "🔄 Background refresh with smart update: $picklistNo")
                        val freshItems = supabaseService.getPicklistItems(picklistNo)
                        cacheManager.smartUpdatePicklistItems(picklistNo, freshItems)
                        Log.d(TAG, "✅ Background smart update completed for: $picklistNo")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Background smart update failed for $picklistNo: ${e.message}")
                    }
                }
                
                return@withContext cachedItems
            }
            
            // Jika tidak ada di cache, fetch dari Supabase
            val items = supabaseService.getPicklistItems(picklistNo)
            
            // Simpan ke cache dengan smart update
            cacheManager.smartUpdatePicklistItems(picklistNo, items)
            
            Log.d(TAG, "🔥 Retrieved ${items.size} items from Supabase")
            
            // Debug log untuk item pertama saja
            if (items.isNotEmpty()) {
                val firstItem = items.first()
                Log.d(TAG, "🔥 First item: ${firstItem.articleName} ${firstItem.size} - qtyPl=${firstItem.qtyPl}, qtyScan=${firstItem.qtyScan}")
            }
            
            items
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklist items: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMASI: Batch get picklist items untuk multiple picklists
     */
    suspend fun getPicklistItemsBatch(picklistNumbers: List<String>): Map<String, List<PicklistItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 BATCH Getting picklist items for ${picklistNumbers.size} picklists")
            
            // Cek cache untuk setiap picklist
            val cachedResults = mutableMapOf<String, List<PicklistItem>>()
            val uncachedPicklists = mutableListOf<String>()
            
            picklistNumbers.forEach { picklistNo ->
                val cachedItems = cacheManager.getPicklistItems(picklistNo)
                if (cachedItems != null) {
                    cachedResults[picklistNo] = cachedItems
                    Log.d(TAG, "✅ Using cached items for $picklistNo: ${cachedItems.size} items")
                } else {
                    uncachedPicklists.add(picklistNo)
                }
            }
            
            // Jika semua sudah di cache, return hasil cache
            if (uncachedPicklists.isEmpty()) {
                Log.d(TAG, "✅ All items retrieved from cache")
                return@withContext cachedResults
            }
            
            // Fetch yang belum di cache menggunakan batch processing
            Log.d(TAG, "🔥 Fetching ${uncachedPicklists.size} uncached picklists from Supabase...")
            val batchResults = supabaseService.getPicklistItemsBatch(uncachedPicklists)
            
            // Simpan hasil batch ke cache
            batchResults.forEach { (picklistNo, items) ->
                cacheManager.setPicklistItems(picklistNo, items)
                cachedResults[picklistNo] = items
            }
            
            Log.d(TAG, "🚀 BATCH Successfully processed ${cachedResults.size} picklists")
            cachedResults
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch get picklist items: ${e.message}", e)
            picklistNumbers.associateWith { emptyList<PicklistItem>() }
        }
    }
    
    /**
     * OPTIMASI: Get all picklist completion statuses dengan batch processing
     */
    suspend fun getPicklistScanStatus(picklistNo: String): PicklistStatus = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Getting scan status for picklist: $picklistNo")
            
            // Check cache first
            val cachedStatus = cacheManager.getPicklistStatus(picklistNo)
            if (cachedStatus != null) {
                Log.d(TAG, "✅ Cache HIT for picklist status: $picklistNo")
                return@withContext cachedStatus
            }
            
            // Get items for this picklist
            val items = getPicklistItems(picklistNo)
            
            if (items.isNotEmpty()) {
                val totalQty = items.sumOf { it.qtyPl }
                val scannedQty = items.sumOf { it.qtyScan }
                val remainingQty = if (scannedQty >= totalQty) 0 else totalQty - scannedQty
                val isScanned = scannedQty > 0
                
                val status = PicklistStatus(
                    picklistNumber = picklistNo,
                    isScanned = isScanned,
                    remainingQty = remainingQty,
                    totalQty = totalQty,
                    scannedQty = scannedQty,
                    lastScanTime = null, // TODO: Implementasi last scan time
                    overscanQty = 0
                )
                
                // Cache the status
                cacheManager.setPicklistStatus(picklistNo, status)
                
                Log.d(TAG, "✅ Picklist $picklistNo: total=$totalQty, scanned=$scannedQty, remaining=$remainingQty, isScanned=$isScanned")
                return@withContext status
            } else {
                val status = PicklistStatus(
                    picklistNumber = picklistNo,
                    isScanned = false,
                    remainingQty = 0,
                    totalQty = 0,
                    scannedQty = 0,
                    lastScanTime = null,
                    overscanQty = 0
                )
                
                cacheManager.setPicklistStatus(picklistNo, status)
                Log.d(TAG, "⚠️ Picklist $picklistNo: No items found")
                return@withContext status
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting scan status for $picklistNo: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun getAllPicklistCompletionStatuses(picklistNumbers: List<String>): List<PicklistStatus> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 BATCH Calculating completion statuses for ${picklistNumbers.size} picklists")
            
            // Gunakan batch processing untuk mendapatkan semua items sekaligus
            val batchResults = getPicklistItemsBatch(picklistNumbers)
            
            val statuses = mutableListOf<PicklistStatus>()
            
            batchResults.forEach { (picklistNumber, items) ->
                try {
                    if (items.isNotEmpty()) {
                        val totalQty = items.sumOf { it.qtyPl }
                        val scannedQty = items.sumOf { it.qtyScan }
                        val remainingQty = if (scannedQty >= totalQty) 0 else totalQty - scannedQty
                        val isScanned = scannedQty > 0
                        
                        val status = PicklistStatus(
                            picklistNumber = picklistNumber,
                            isScanned = isScanned,
                            remainingQty = remainingQty,
                            totalQty = totalQty,
                            scannedQty = scannedQty,
                            lastScanTime = null, // TODO: Implementasi last scan time
                            overscanQty = 0
                        )
                        
                        statuses.add(status)
                        
                        // Simpan status ke cache
                        cacheManager.setPicklistStatus(picklistNumber, status)
                        
                        Log.d(TAG, "🔥 Picklist $picklistNumber: total=$totalQty, scanned=$scannedQty, remaining=$remainingQty, isScanned=$isScanned")
                    } else {
                        val status = PicklistStatus(
                            picklistNumber = picklistNumber,
                            isScanned = false,
                            remainingQty = 0,
                            totalQty = 0,
                            scannedQty = 0,
                            lastScanTime = null,
                            overscanQty = 0
                        )
                        
                        statuses.add(status)
                        cacheManager.setPicklistStatus(picklistNumber, status)
                        Log.d(TAG, "⚠️ Picklist $picklistNumber: No items found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error calculating status for $picklistNumber: ${e.message}")
                    
                    val status = PicklistStatus(
                        picklistNumber = picklistNumber,
                        isScanned = false,
                        remainingQty = 0,
                        totalQty = 0,
                        scannedQty = 0,
                        lastScanTime = null,
                        overscanQty = 0
                    )
                    
                    statuses.add(status)
                }
            }
            
            Log.d(TAG, "✅ BATCH Calculated completion statuses for ${statuses.size} picklists")
            statuses
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating all completion statuses: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * **NEW**: Get picklist completion status menggunakan query Room langsung
     * Lebih efisien karena menggunakan SQL query langsung dari database
     */
    suspend fun getAllPicklistCompletionStatusesFromRoom(): List<PicklistStatus> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 [Supabase-only] Skipping Room completion statuses")
        emptyList()
    }
    
    /**
     * OPTIMASI: Single batch fetch untuk picklist items dan processed EPC list
     * Menggabungkan kedua operasi dalam satu call untuk menghindari fetch berulang
     */
    suspend fun getPicklistDataBatch(picklistNo: String): Pair<List<PicklistItem>, List<String>> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getPicklistDataBatch", picklistNo)
            
            Log.d(TAG, "🚀 SINGLE BATCH Fetching picklist data for: $picklistNo")
            
            // Cek cache untuk kedua data
            val cachedItems = cacheManager.getPicklistItems(picklistNo)
            val cachedEpcList = cacheManager.getProcessedEpcList(picklistNo)
            
            // Jika kedua data ada di cache, return dari cache
            if (cachedItems != null && cachedEpcList != null) {
                Log.d(TAG, "✅ Using cached data for $picklistNo: ${cachedItems.size} items, ${cachedEpcList.size} EPCs")
                
                // Lakukan background refresh untuk update incremental
                repositoryScope.launch {
                    try {
                        Log.d(TAG, "🔄 Background refresh for incremental update: batch data for $picklistNo")
                        val freshItems = supabaseService.getPicklistItems(picklistNo)
                        val freshEpcList = supabaseService.getProcessedEpcList(picklistNo)
                        
                        cacheManager.updatePicklistItemsIncremental(picklistNo, freshItems)
                        cacheManager.updateProcessedEpcListIncremental(picklistNo, freshEpcList)
                        
                        Log.d(TAG, "✅ Background incremental update completed for batch data: $picklistNo")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Background incremental update failed for batch data $picklistNo: ${e.message}")
                    }
                }
                
                return@withContext Pair(cachedItems, cachedEpcList)
            }
            
            // Jika tidak ada di cache atau salah satu missing, fetch dari Supabase
            Log.d(TAG, "🔥 Fetching batch data from Supabase for: $picklistNo")
            
            // **OPTIMASI PARALLEL**: Fetch kedua data secara bersamaan
            val itemsDeferred = async { supabaseService.getPicklistItems(picklistNo) }
            val epcListDeferred = async { supabaseService.getProcessedEpcList(picklistNo) }
            
            // Tunggu kedua operasi selesai
            val items = itemsDeferred.await()
            val epcList = epcListDeferred.await()
            
            // Simpan ke cache dengan smart update
            cacheManager.smartUpdatePicklistItems(picklistNo, items)
            cacheManager.updateProcessedEpcListIncremental(picklistNo, epcList)
            
            Log.d(TAG, "✅ Batch fetch completed: ${items.size} items, ${epcList.size} EPCs for picklist: $picklistNo")
            Pair(items, epcList)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch fetch: ${e.message}", e)
            Pair(emptyList(), emptyList())
        }
    }
    
    /**
     * OPTIMASI: Get processed EPC list dengan Local-First strategy
     */
    suspend fun getProcessedEpcList(picklistNo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getProcessedEpcList", picklistNo)
            
            Log.d(TAG, "🔥 Getting processed EPC list for picklist: $picklistNo")
            
            // Gunakan Enhanced Repository jika tersedia (Local-First strategy)
            if (enhancedRepository != null) {
                Log.d(TAG, "📱 Using Enhanced Repository for EPC list (Local-First strategy)")
                return@withContext enhancedRepository.getProcessedEpcList(picklistNo)
            }
            
            // Fallback ke logic lama jika Enhanced Repository tidak tersedia
            Log.d(TAG, "⚠️ Using legacy cache logic for EPC list (Enhanced Repository not available)")
            
            // Cek cache dulu
            val cachedEpcList = cacheManager.getProcessedEpcList(picklistNo)
            if (cachedEpcList != null) {
                Log.d(TAG, "✅ Using cached processed EPC list for $picklistNo: ${cachedEpcList.size} EPCs")
                
                // Lakukan background refresh untuk update incremental
                repositoryScope.launch {
                    try {
                        Log.d(TAG, "🔄 Background refresh for incremental update: processed EPC list for $picklistNo")
                        val freshEpcList = supabaseService.getProcessedEpcList(picklistNo)
                        cacheManager.updateProcessedEpcListIncremental(picklistNo, freshEpcList)
                        Log.d(TAG, "✅ Background incremental update completed for processed EPC list: $picklistNo")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Background incremental update failed for processed EPC list $picklistNo: ${e.message}")
                    }
                }
                
                return@withContext cachedEpcList
            }
            
            // Jika tidak ada di cache, fetch dari Supabase
            Log.d(TAG, "🔥 Fetching processed EPC list from Supabase for: $picklistNo")
            val epcList = supabaseService.getProcessedEpcList(picklistNo)
            
            // Simpan ke cache dengan incremental update
            cacheManager.updateProcessedEpcListIncremental(picklistNo, epcList)
            
            Log.d(TAG, "✅ Found ${epcList.size} processed EPCs for picklist: $picklistNo")
            epcList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting processed EPC list: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * DEBUGGING: Get all scan records for a picklist (without date filter)
     */
    suspend fun getAllScanRecordsForPicklist(picklistNo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Getting all scan records for picklist: $picklistNo (no date filter)")
            
            // Fetch dari Supabase tanpa filter tanggal
            val allRecords = supabaseService.getAllScanRecordsForPicklist(picklistNo)
            
            Log.d(TAG, "✅ Found ${allRecords.size} total scan records for picklist: $picklistNo")
            allRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting all scan records: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMASI: Batch save multiple picklist scans untuk mengurangi API calls
     */
    suspend fun savePicklistScansBatch(scans: List<Sextuple<String, String, String, String, String, String>>): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 BATCH Saving ${scans.size} picklist scans with pre-filtering and chunking")
            
            var totalSuccessCount = 0
            var totalFailCount = 0
            var totalSkippedCount = 0
            
            // **OPTIMASI BARU**: Pre-filter EPC yang sudah ada untuk menghindari duplicate saves
            val allEpcs = scans.map { it.third } // third = epc
            Log.d(TAG, "🔍 Pre-checking ${allEpcs.size} EPCs for existing records...")
            
            val existingEpcs = supabaseService.batchCheckExistingEpcs(allEpcs)
            Log.d(TAG, "✅ Found ${existingEpcs.size} existing EPCs, ${allEpcs.size - existingEpcs.size} new EPCs")
            
            // Filter scans untuk hanya menyimpan EPC yang belum ada
            val newScans = scans.filter { scan ->
                val epc = scan.third
                val isNew = !existingEpcs.contains(epc)
                if (!isNew) {
                    totalSkippedCount++
                    Log.d(TAG, "⏭️ Skipping existing EPC: $epc")
                }
                isNew
            }
            
            Log.d(TAG, "📊 Pre-filtering result: ${scans.size} total, ${newScans.size} new, ${totalSkippedCount} skipped")
            
            if (newScans.isEmpty()) {
                Log.d(TAG, "✅ All EPCs already exist - no saves needed")
                return@withContext true
            }
            
            // **PERBAIKAN**: Chunking untuk batch besar untuk menghindari timeout
            val chunkSize = 50 // Process 50 items at a time
            val chunks = newScans.chunked(chunkSize)
            
            Log.d(TAG, "📦 Processing ${chunks.size} chunks of max $chunkSize items each")
            
            chunks.forEachIndexed { chunkIndex, chunk ->
                Log.d(TAG, "🔥 Processing chunk ${chunkIndex + 1}/${chunks.size} with ${chunk.size} items")
                
                // Group scans by picklist untuk optimasi cache update
                val scansByPicklist = chunk.groupBy { it.first }
                
                // **OPTIMASI MAJOR**: True batch insert untuk semua scans dalam chunk dengan 1 API call
                try {
                    Log.d(TAG, "🚀 TRUE BATCH Inserting ${chunk.size} scans in 1 API call")
                    
                    val batchSuccess = supabaseService.batchInsertPicklistScans(chunk)
                    
                    if (batchSuccess) {
                        totalSuccessCount += chunk.size
                        Log.d(TAG, "✅ Batch insert successful: ${chunk.size} scans saved")
                    } else {
                        totalFailCount += chunk.size
                        Log.e(TAG, "❌ Batch insert failed: ${chunk.size} scans failed")
                    }
                    
                    // **OPTIMASI**: Update cache untuk semua EPC yang diproses (berhasil + skipped)
                    val scansByPicklist = chunk.groupBy { it.first }
                    
                    scansByPicklist.forEach { (picklistNo, picklistScans) ->
                        val cachedEpcList = cacheManager.getProcessedEpcList(picklistNo)
                        
                        if (cachedEpcList != null) {
                            val updatedEpcList = cachedEpcList + picklistScans.map { it.third }
                            cacheManager.setProcessedEpcList(picklistNo, updatedEpcList)
                            Log.d(TAG, "🔄 Batch updated processed EPC cache for $picklistNo: ${cachedEpcList.size} -> ${updatedEpcList.size} EPCs")
                        } else {
                            cacheManager.setProcessedEpcList(picklistNo, picklistScans.map { it.third })
                            Log.d(TAG, "💾 Batch added new processed EPC cache for $picklistNo: ${picklistScans.size} EPCs")
                        }
                        
                        // Invalidate cache untuk picklist ini
                        cacheManager.invalidatePicklist(picklistNo)
                    }
                    
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "🔥 Batch save cancelled (normal behavior): ${e.message}")
                    totalFailCount += chunk.size
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in batch insert chunk ${chunkIndex + 1}: ${e.message}", e)
                    totalFailCount += chunk.size
                }
            }
            
            Log.d(TAG, "✅ Batch save completed: $totalSuccessCount success, $totalFailCount failed, $totalSkippedCount skipped")
            totalSuccessCount > 0 || totalSkippedCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch save: ${e.message}", e)
            false
        }
    }
    
    /**
     * OPTIMASI: Get cache statistics untuk monitoring
     */
    suspend fun getCacheStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            cacheManager.getCacheStats()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting cache stats: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Batch fetch picklist data untuk multiple picklists
     * @param picklistNumbers List picklist numbers yang akan di-fetch
     * @return Map picklistNo -> PicklistData
     */
    suspend fun getBatchPicklistData(picklistNumbers: List<String>): Map<String, BatchSupabaseService.PicklistData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Batch fetching picklist data for ${picklistNumbers.size} picklists...")
            
            val startTime = System.currentTimeMillis()
            
            // Cek cache terlebih dahulu
            val cachedData = mutableMapOf<String, BatchSupabaseService.PicklistData>()
            val uncachedPicklists = mutableListOf<String>()
            
            picklistNumbers.forEach { picklistNo ->
                val cachedItems = cacheManager.getPicklistItems(picklistNo)
                val cachedProcessedEpcList = cacheManager.getProcessedEpcList(picklistNo)
                val cachedStatus = cacheManager.getPicklistStatus(picklistNo)
                
                if (cachedItems != null && cachedProcessedEpcList != null && cachedStatus != null) {
                    cachedData[picklistNo] = BatchSupabaseService.PicklistData(
                        items = cachedItems,
                        processedEpcList = cachedProcessedEpcList,
                        status = cachedStatus
                    )
                    Log.d(TAG, "✅ Using cached data for $picklistNo")
                } else {
                    uncachedPicklists.add(picklistNo)
                    Log.d(TAG, "❌ Cache MISS for $picklistNo")
                }
            }
            
            // Fetch data yang tidak ada di cache
            val fetchedData = if (uncachedPicklists.isNotEmpty()) {
                batchSupabaseService.getBatchPicklistData(uncachedPicklists)
            } else {
                emptyMap()
            }
            
            // Update cache dengan data yang baru di-fetch
            fetchedData.forEach { (picklistNo, data) ->
                cacheManager.smartUpdatePicklistItems(picklistNo, data.items)
                cacheManager.updateProcessedEpcListIncremental(picklistNo, data.processedEpcList)
                cacheManager.updatePicklistStatusIncremental(picklistNo, data.status)
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ Batch fetch completed: ${cachedData.size} cached + ${fetchedData.size} fetched in ${duration}ms")
            
            // Gabungkan cached dan fetched data
            cachedData + fetchedData
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch fetch: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * OPTIMASI: Cleanup expired cache entries
     */
    suspend fun cleanupCache() = withContext(Dispatchers.IO) {
        try {
            cacheManager.cleanupExpiredEntries()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up cache: ${e.message}", e)
        }
    }
    
    /**
     * Clear cache untuk picklist tertentu
     */
    suspend fun clearCacheForPicklist(picklistNo: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Clearing cache for picklist: $picklistNo")
            cacheManager.clearCacheForPicklist(picklistNo)
            Log.d(TAG, "✅ Cache cleared for picklist: $picklistNo")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing cache for picklist $picklistNo: ${e.message}", e)
        }
    }

    /**
     * PERBAIKAN: Clear data scan hanya di aplikasi (TIDAK menghapus data di Supabase)
     * Digunakan saat tombol clear ditekan untuk mereset tampilan aplikasi saja
     */
    suspend fun removeAllScanDataForPicklist(picklistNo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Clearing scan data display for picklist: $picklistNo (NOT deleting from Supabase)")
            
            // **STEP 1**: Clear cache untuk memastikan data fresh dari Supabase
            Log.d(TAG, "🧹 Step 1: Clearing cache to refresh data from Supabase...")
            try {
                cacheManager.clearCacheForPicklist(picklistNo)
                Log.d(TAG, "✅ Cache cleared for picklist: $picklistNo")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing cache: ${e.message}", e)
            }
            
            // **STEP 2**: Data di Supabase TIDAK dihapus, hanya cache yang dibersihkan
            // Ini akan memaksa aplikasi untuk reload data fresh dari Supabase
            // dengan qtyScan yang masih ada (karena tidak dihapus dari database)
            
            Log.d(TAG, "✅ Scan data display cleared (Supabase data preserved):")
            Log.d(TAG, "   - Cache cleared: true")
            Log.d(TAG, "   - Supabase data: PRESERVED (not deleted)")
            Log.d(TAG, "   - App will reload fresh data from Supabase")
            
            true // Selalu return true karena ini hanya clear cache
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "🔥 Scan data display clear cancelled (normal behavior): ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing scan data display: ${e.message}", e)
            false
        }
    }

    /**
     * PERBAIKAN: Hapus overscan data dari database untuk picklist tertentu
     * Digunakan saat back button ditekan untuk membersihkan overscan
     */
    suspend fun removeOverscanDataForPicklist(picklistNo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Removing overscan data for picklist: $picklistNo")
            
            // Dapatkan semua item picklist
            val picklistItems = getPicklistItems(picklistNo)
            val overscanItems = picklistItems.filter { it.qtyScan > it.qtyPl }
            
            if (overscanItems.isEmpty()) {
                Log.d(TAG, "✅ No overscan items found for picklist: $picklistNo")
                return@withContext true
            }
            
            Log.d(TAG, "🔍 Found ${overscanItems.size} overscan items to clean up")
            
            // Reset qtyScan untuk item yang overscan ke qtyPl (bukan 0)
            var successCount = 0
            for (item in overscanItems) {
                try {
                    val success = supabaseService.resetPicklistItemQtyScan(picklistNo, item.articleId, item.size, item.qtyPl)
                    if (success) {
                        successCount++
                        Log.d(TAG, "✅ Reset qtyScan for overscan item: ${item.articleName} ${item.size} (was ${item.qtyScan}, now ${item.qtyPl})")
                    } else {
                        Log.w(TAG, "⚠️ Failed to reset qtyScan for: ${item.articleName} ${item.size}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error resetting qtyScan for ${item.articleName} ${item.size}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "✅ Overscan cleanup completed: $successCount/${overscanItems.size} items reset")
            successCount == overscanItems.size
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "🔥 Overscan cleanup cancelled (normal behavior): ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing overscan data: ${e.message}", e)
            false
        }
    }
    
    suspend fun savePicklistScan(picklistNo: String, articleId: String, epc: String, productId: String, articleName: String = "", size: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Saving picklist scan: $picklistNo, $articleId, $epc, $productId, $articleName, $size")
            val success = supabaseService.savePicklistScan(picklistNo, articleId, epc, productId, articleName, size)
            
            if (success) {
                // **OPTIMASI**: Update cache secara incremental untuk processed EPC list
                val cachedEpcList = cacheManager.getProcessedEpcList(picklistNo)
                if (cachedEpcList != null) {
                    val updatedEpcList = cachedEpcList + epc
                    cacheManager.setProcessedEpcList(picklistNo, updatedEpcList)
                    Log.d(TAG, "🔄 Updated processed EPC cache for $picklistNo: ${cachedEpcList.size} -> ${updatedEpcList.size} EPCs")
                } else {
                    // Jika tidak ada di cache, tambahkan EPC baru
                    cacheManager.setProcessedEpcList(picklistNo, listOf(epc))
                    Log.d(TAG, "💾 Added new processed EPC cache for $picklistNo: 1 EPC")
                }
                
                // Invalidate cache untuk picklist ini karena data sudah berubah
                cacheManager.invalidatePicklist(picklistNo)
                Log.d(TAG, "🗑️ Invalidated cache for picklist: $picklistNo")
            }
            
            Log.d(TAG, "🔥 Save result: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving picklist scan: ${e.message}", e)
            false
        }
    }
    
    /**
     * Lookup EPC menggunakan API Nirwana untuk mendapatkan article ID
     * @param epc EPC yang akan di-lookup
     * @return Article ID jika ditemukan, null jika tidak ditemukan
     */
    suspend fun lookupEpcToArticleId(epc: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Looking up EPC using Nirwana API: $epc")
            val articleId = nirwanaApiService.lookupEpcToArticleId(epc)
            Log.d(TAG, "🔥 EPC lookup result: $epc -> $articleId")
            articleId
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error looking up EPC: ${e.message}", e)
            null
        }
    }
    
    /**
     * Batch lookup untuk multiple RFID tags menggunakan API Nirwana
     * @param rfidList List RFID yang akan di-lookup
     * @return List ProductInfo jika ditemukan
     */
    suspend fun batchLookupRfidList(rfidList: List<String>): List<NirwanaApiService.ProductInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Batch lookup for ${rfidList.size} RFID tags using Nirwana API")
            val products = nirwanaApiService.batchLookupRfidList(rfidList)
            Log.d(TAG, "🔥 Batch lookup completed: ${products.size} products found")
            products
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during batch RFID lookup: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Single lookup untuk 1 EPC menggunakan API Nirwana
     */
    suspend fun lookupSingleRfid(epc: String): NirwanaApiService.ProductInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Single lookup for EPC using Nirwana API: $epc")
            nirwanaApiService.getProductInfoByEpc(epc)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during single RFID lookup: ${e.message}", e)
            null
        }
    }
    
    /**
     * Dapatkan informasi produk berdasarkan article ID menggunakan API Nirwana
     * @param articleId Article ID yang akan di-lookup
     * @return ProductInfo jika ditemukan, null jika tidak ditemukan
     */
    suspend fun getProductInfo(articleId: String): NirwanaApiService.ProductInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Getting product info using Nirwana API: $articleId")
            val productInfo = nirwanaApiService.getProductInfoByEpc(articleId)
            Log.d(TAG, "🔥 Product info result: $productInfo")
            productInfo
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting product info: ${e.message}", e)
            null
        }
    }
    
    /**
     * Test koneksi ke API Nirwana
     * @return true jika koneksi berhasil, false jika gagal
     */
    suspend fun testNirwanaApiConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Testing Nirwana API connection...")
            val isConnected = nirwanaApiService.testConnection()
            Log.d(TAG, "🔥 Nirwana API connection test result: $isConnected")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing Nirwana API connection: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get query statistics untuk monitoring
     */
    fun getQueryStatistics(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val stats = mutableMapOf<String, Any>()
        
        // Total calls per query type
        stats["totalCalls"] = queryCallCounts.values.sum()
        stats["uniqueQueries"] = queryCallCounts.size
        
        // Most frequent queries
        val frequentQueries = queryCallCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .associate { it.key to it.value }
        stats["frequentQueries"] = frequentQueries
        
        // Recent queries (within last 5 minutes)
        val recentQueries = lastQueryTimes.entries
            .filter { currentTime - it.value < 300000 } // 5 minutes
            .map { it.key }
        stats["recentQueries"] = recentQueries
        
        // Duplicate queries detected
        val duplicateQueries = queryCallCounts.entries
            .filter { it.value > 1 }
            .associate { it.key to it.value }
        stats["duplicateQueries"] = duplicateQueries
        
        Log.d(TAG, "📊 Query Statistics: $stats")
        return stats
    }
    
    /**
     * Reset query statistics
     */
    fun resetQueryStatistics() {
        queryCallCounts.clear()
        lastQueryTimes.clear()
        Log.d(TAG, "🔄 Query statistics reset")
    }
    
    /**
     * Clear semua cache
     */
    suspend fun clearCache() {
        cacheManager.invalidateAll()
        Log.d(TAG, "🗑️ Cleared all cache")
    }
    
    /**
     * Batch check existing EPCs untuk duplicate prevention
     * @param epcs List EPC yang akan dicek
     * @return Set EPC yang sudah ada di database
     */
    suspend fun batchCheckExistingEpcs(epcs: List<String>): Set<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Batch checking ${epcs.size} EPCs for existing records")
            val existingEpcs = supabaseService.batchCheckExistingEpcs(epcs)
            Log.d(TAG, "✅ Found ${existingEpcs.size} existing EPCs out of ${epcs.size} checked")
            existingEpcs
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error batch checking EPCs: ${e.message}", e)
            emptySet()
        }
    }
    
    /**
     * **PERBAIKAN**: Membersihkan data duplikasi di database untuk picklist tertentu
     */
    suspend fun cleanupDuplicateScans(picklistNo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Cleaning up duplicate scans for picklist: $picklistNo")
            val success = supabaseService.cleanupDuplicateScans(picklistNo)
            Log.d(TAG, "✅ Cleanup result: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up duplicate scans: ${e.message}", e)
            false
        }
    }
    
    /**
     * Update status picklist di database
     * @param picklistNumber Nomor picklist
     * @param status Status baru (completed, in_progress, pending)
     * @return true jika berhasil
     */
    suspend fun updatePicklistStatus(picklistNumber: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Updating picklist status: $picklistNumber -> $status")
            val success = supabaseService.updatePicklistStatus(picklistNumber, status)
            
            if (success) {
                // Invalidate cache untuk picklist ini
                cacheManager.invalidatePicklist(picklistNumber)
                Log.d(TAG, "🗑️ Invalidated cache for picklist: $picklistNumber")
            }
            
            Log.d(TAG, "🔥 Update status result: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating picklist status: ${e.message}", e)
            false
        }
    }
}