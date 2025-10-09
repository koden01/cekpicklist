package com.example.cekpicklist.repository

import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistStatus
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.BatchSupabaseService
import com.example.cekpicklist.cache.CacheManager
import com.example.cekpicklist.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import com.example.cekpicklist.viewmodel.Quadruple
import com.example.cekpicklist.viewmodel.Sextuple
import com.example.cekpicklist.api.SupabaseService.SimplePicklist

class Repository(private val context: android.content.Context? = null) {
    private val supabaseService = SupabaseService()
    private val nirwanaApiService = NirwanaApiService()
    private val batchSupabaseService = BatchSupabaseService()
    // Use shared CacheManager from MyApplication when available to avoid multiple instances
    private val cacheManager: CacheManager =
        ((context?.applicationContext) as? MyApplication)?.getCacheManager()
            ?: CacheManager(context)
    
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
     * METODE BARU: Ambil picklist unik hari ini dengan status scan
     * Sederhana dan efisien - hanya untuk modal selection
     */
    suspend fun getUniquePicklistsWithScanStatus(): List<SimplePicklist> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getUniquePicklistsWithScanStatus")

            Log.d(TAG, "🔥 === REPOSITORY GET UNIQUE PICKLISTS WITH SCAN STATUS START ===")
            Log.d(TAG, "🔍 DEBUG: getUniquePicklistsWithScanStatus() called at ${System.currentTimeMillis()}")

            // Langsung ambil dari Supabase (tidak perlu cache untuk data sederhana ini)
            Log.d(TAG, "🔍 DEBUG: Calling supabaseService.getUniquePicklistsWithScanStatus()...")
            val simplePicklists = supabaseService.getUniquePicklistsWithScanStatus()
            Log.d(TAG, "🔍 DEBUG: Supabase returned ${simplePicklists.size} unique picklists")

            if (simplePicklists.isNotEmpty()) {
                Log.d(TAG, "🔍 DEBUG: First 3 picklists: ${simplePicklists.take(3).map { "${it.noPicklist} scanned=${it.scannedCount}/${it.totalCount} unscanned=${it.unscannedCount}" }}")
            } else {
                Log.w(TAG, "⚠️ DEBUG: Supabase returned empty picklist list!")
            }

            Log.d(TAG, "✅ Retrieved ${simplePicklists.size} unique picklists with scan status")
            simplePicklists
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting unique picklists with scan status: ${e.message}", e)
            Log.e(TAG, "🔍 DEBUG: Exception details", e)
            emptyList()
        }
    }
    
    /**
     * LAZY LOADING: Ambil data detail picklist saat dipilih
     */
    suspend fun getPicklistDetails(picklistNumber: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getPicklistDetails", picklistNumber)

            Log.d(TAG, "🔥 === REPOSITORY GET PICKLIST DETAILS START ===")
            Log.d(TAG, "🔍 DEBUG: getPicklistDetails() called for picklist: $picklistNumber")
            Log.d(TAG, "🔍 DEBUG: getPicklistDetails() called at ${System.currentTimeMillis()}")

            // Langsung ambil dari Supabase (lazy loading)
            Log.d(TAG, "🔍 DEBUG: Calling supabaseService.getPicklistDetails($picklistNumber)...")
            val picklistItems = supabaseService.getPicklistDetails(picklistNumber)
            Log.d(TAG, "🔍 DEBUG: Supabase returned ${picklistItems.size} items for picklist $picklistNumber")

            if (picklistItems.isNotEmpty()) {
                Log.d(TAG, "🔍 DEBUG: First 3 items: ${picklistItems.take(3).map { "${it.articleName}(${it.qtyPl})" }}")
            } else {
                Log.w(TAG, "⚠️ DEBUG: Supabase returned empty items list for picklist $picklistNumber!")
            }

            Log.d(TAG, "✅ Retrieved ${picklistItems.size} items for picklist $picklistNumber")
            picklistItems
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklist details for $picklistNumber: ${e.message}", e)
            Log.e(TAG, "🔍 DEBUG: Exception details", e)
            emptyList()
        }
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
            
            Log.d(TAG, "🔥 === REPOSITORY GETPICKLISTS START ===")
            Log.d(TAG, "🔍 DEBUG: Starting getPicklists() - checking cache first...")
            Log.d(TAG, "🔍 DEBUG: getPicklists() called at ${System.currentTimeMillis()}")
            
                // Cek cache dulu (treat empty list as MISS agar tidak menahan keadaan kosong)
                Log.d(TAG, "🔍 DEBUG: Calling cacheManager.getAllPicklists()...")
                val cachedPicklists = try {
                    withTimeout(4000) { // relax timeout to reduce false timeouts
                        cacheManager.getAllPicklists()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "❌ Cache check timeout after 2 seconds: ${e.message}")
                    // **PERBAIKAN**: Try to get cached data directly without timeout
                    try {
                        Log.d(TAG, "🔄 FALLBACK: Trying to get cached data directly...")
                        val fallbackData = cacheManager.getAllPicklists()
                        if (fallbackData != null) {
                            Log.d(TAG, "✅ FALLBACK: Got cached data directly: ${fallbackData.size} items")
                            return@withContext fallbackData
                        }
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "❌ Fallback cache access failed: ${fallbackError.message}", fallbackError)
                    }
                    null
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Cache check error: ${e.message}", e)
                    // **PERBAIKAN**: Try to get cached data directly even on error
                    try {
                        Log.d(TAG, "🔄 FALLBACK: Trying to get cached data despite error...")
                        val fallbackData = cacheManager.getAllPicklists()
                        if (fallbackData != null) {
                            Log.d(TAG, "✅ FALLBACK: Got cached data despite error: ${fallbackData.size} items")
                            return@withContext fallbackData
                        }
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "❌ Fallback cache access failed: ${fallbackError.message}", fallbackError)
                    }
                    null
                }
                Log.d(TAG, "🔍 DEBUG: Cache check result - cachedPicklists: ${cachedPicklists?.size ?: "null"} items")
            
            if (cachedPicklists != null && cachedPicklists.isNotEmpty()) {
                Log.d(TAG, "✅ Using cached picklists: ${cachedPicklists.size} items")
                Log.d(TAG, "🔍 DEBUG: Cached picklists: ${cachedPicklists.take(3).joinToString(", ")}${if (cachedPicklists.size > 3) "..." else ""}")
                
                // **PERBAIKAN**: Lakukan background refresh untuk update incremental dengan persistence
                repositoryScope.launch {
                    try {
                        Log.d(TAG, "🔄 Background refresh for incremental update: all picklists")
                        val freshPicklists = supabaseService.getPicklists()
                        Log.d(TAG, "🔍 DEBUG: Background refresh got ${freshPicklists.size} fresh picklists")
                        cacheManager.updateAllPicklistsIncremental(freshPicklists)
                        
                        // **PERBAIKAN BARU**: Force save cache untuk memastikan persistence
                        cacheManager.forceSaveCache()
                        
                        Log.d(TAG, "✅ Background incremental update completed for all picklists")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Background incremental update failed for all picklists: ${e.message}")
                    }
                }
                
                return@withContext cachedPicklists
            }
            
            // Jika tidak ada di cache ATAU cache kosong, fetch dari Supabase (force refresh)
            Log.d(TAG, "🔥 Fetching picklists from Supabase (cache miss or empty)...")
            Log.d(TAG, "🔍 DEBUG: Cache was ${if (cachedPicklists == null) "null" else "empty"}, fetching from Supabase...")
            
            // **PERBAIKAN**: Test koneksi dulu sebelum fetch
            Log.d(TAG, "🔍 DEBUG: Testing Supabase connection...")
            val isConnected = supabaseService.testConnection()
            Log.d(TAG, "🔍 DEBUG: Supabase connection test result: $isConnected")
            
            if (!isConnected) {
                Log.e(TAG, "❌ No internet connection to Supabase, returning empty list")
                return@withContext emptyList()
            }
            
            Log.d(TAG, "🔍 DEBUG: Calling supabaseService.getPicklists()...")
            val picklists = supabaseService.getPicklists()
            Log.d(TAG, "🔍 DEBUG: Supabase returned ${picklists.size} picklists")
            
            if (picklists.isNotEmpty()) {
                Log.d(TAG, "🔍 DEBUG: First 3 picklists: ${picklists.take(3).joinToString(", ")}")
            } else {
                Log.w(TAG, "⚠️ DEBUG: Supabase returned empty picklist list!")
            }
            
                // Simpan ke cache dengan incremental update
                Log.d(TAG, "🔍 DEBUG: Saving to cache...")
                try {
                    withTimeout(4000) { // relax timeout to reduce false timeouts
                        cacheManager.updateAllPicklistsIncremental(picklists)
                    }
                    Log.d(TAG, "✅ Cache update completed")
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "❌ Cache update timeout after 2 seconds: ${e.message}")
                    // **PERBAIKAN**: Try to save without timeout
                    try {
                        Log.d(TAG, "🔄 FALLBACK: Trying to save cache without timeout...")
                        cacheManager.updateAllPicklistsIncremental(picklists)
                        Log.d(TAG, "✅ Fallback cache update completed")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "❌ Fallback cache update failed: ${fallbackError.message}", fallbackError)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Cache update error: ${e.message}", e)
                    // **PERBAIKAN**: Try to save without timeout
                    try {
                        Log.d(TAG, "🔄 FALLBACK: Trying to save cache despite error...")
                        cacheManager.updateAllPicklistsIncremental(picklists)
                        Log.d(TAG, "✅ Fallback cache update completed")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "❌ Fallback cache update failed: ${fallbackError.message}", fallbackError)
                    }
                }

                // **PERBAIKAN BARU**: Force save cache untuk memastikan persistence
                Log.d(TAG, "🔍 DEBUG: Force saving cache...")
                try {
                    withTimeout(5000) { // 5 second timeout (increased from 3 seconds)
                        cacheManager.forceSaveCache()
                    }
                    Log.d(TAG, "✅ Cache force save completed")
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "❌ Cache force save timeout after 5 seconds: ${e.message}")
                    // **PERBAIKAN**: Try to save without timeout
                    try {
                        Log.d(TAG, "🔄 FALLBACK: Trying to force save cache without timeout...")
                        cacheManager.forceSaveCache()
                        Log.d(TAG, "✅ Fallback cache force save completed")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "❌ Fallback cache force save failed: ${fallbackError.message}", fallbackError)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Cache force save error: ${e.message}", e)
                    // **PERBAIKAN**: Try to save without timeout
                    try {
                        Log.d(TAG, "🔄 FALLBACK: Trying to force save cache despite error...")
                        cacheManager.forceSaveCache()
                        Log.d(TAG, "✅ Fallback cache force save completed")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "❌ Fallback cache force save failed: ${fallbackError.message}", fallbackError)
                    }
                }
            
            Log.d(TAG, "✅ Fetched and cached ${picklists.size} picklists")
            picklists
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklists: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMASI: Get picklist items dengan cache dan incremental update
     */
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getPicklistItems", picklistNo)

            Log.d(TAG, "🔥 SIMPLE PATH: Fetching picklist items directly for: $picklistNo")

            // Fetch langsung agar pasti terbaru
            var items = supabaseService.getPicklistItems(picklistNo)

            // Fallback: jika kosong, coba via 'in' operator (batch API dengan 1 picklist)
            if (items.isEmpty()) {
                Log.w(TAG, "⚠️ Direct fetch returned 0 items for $picklistNo, trying fallback via IN operator (batch)...")
                try {
                    val viaBatch = supabaseService.getPicklistItemsBatch(listOf(picklistNo))
                    val fallbackItems = viaBatch[picklistNo] ?: emptyList()
                    if (fallbackItems.isNotEmpty()) {
                        Log.d(TAG, "✅ Fallback via IN operator succeeded: ${fallbackItems.size} items")
                        items = fallbackItems
                    } else {
                        Log.w(TAG, "⚠️ Fallback via IN operator also returned 0 items for $picklistNo")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Fallback via IN operator failed: ${e.message}", e)
                }
            }

            // Update cache sinkron agar layar berikutnya cepat
            try {
                cacheManager.smartUpdatePicklistItems(picklistNo, items)
                cacheManager.forceSaveCache()
            } catch (cacheError: Exception) {
                Log.e(TAG, "❌ Cache update failed for $picklistNo: ${cacheError.message}", cacheError)
            }

            Log.d(TAG, "✅ SIMPLE PATH: Retrieved ${items.size} items for $picklistNo")
            if (items.isNotEmpty()) {
                val firstItem = items.first()
                Log.d(TAG, "🔥 First item: ${firstItem.articleName} ${firstItem.size} - qtyPl=${firstItem.qtyPl}, qtyScan=${firstItem.qtyScan}")
            }

            items
        } catch (ce: CancellationException) {
            Log.d(TAG, "ℹ️ getPicklistItems cancelled (normal on navigation)")
            throw ce
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
        } catch (ce: CancellationException) {
            Log.d(TAG, "ℹ️ getPicklistItemsBatch cancelled (normal on navigation)")
            throw ce
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
                // **PERBAIKAN**: Pastikan items adalah List<PicklistItem> yang valid
                val validItems = items.filterIsInstance<PicklistItem>()
                if (validItems.isEmpty()) {
                    Log.e(TAG, "❌ Error calculating status for $picklistNo: No valid PicklistItem objects found")
                    return@withContext PicklistStatus(
                        picklistNumber = picklistNo,
                        isScanned = false,
                        remainingQty = 0,
                        totalQty = 0,
                        scannedQty = 0,
                        lastScanTime = null,
                        overscanQty = 0
                    )
                }
                
                val totalQty = validItems.sumOf { it.qtyPl }
                val scannedQty = validItems.sumOf { it.qtyScan }
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

            // Ambil scanned count HARI INI untuk setiap picklist secara paralel agar status akurat meski items masih loading
            val scannedCountsToday = picklistNumbers.associateWith { pickNo ->
                async {
                    try {
                        // Gunakan DISTINCT EPC untuk akurasi scannedQty
                        val scans = supabaseService.getPicklistScans(pickNo)
                        val distinctCount = scans.mapNotNull { scan ->
                            try { scan.getString("epc") } catch (_: Exception) { null }
                        }.distinct().size
                        distinctCount
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching scans for $pickNo: ${e.message}")
                        0
                    }
                }
            }.mapValues { it.value.await() }

            batchResults.forEach { (picklistNumber, items) ->
                try {
                    val scannedToday = scannedCountsToday[picklistNumber] ?: 0

                    if (items.isNotEmpty()) {
                        // **PERBAIKAN**: Pastikan items adalah List<PicklistItem> yang valid
                        val validItems = items.filterIsInstance<PicklistItem>()
                        if (validItems.isEmpty()) {
                            Log.e(TAG, "❌ Error calculating status for $picklistNumber: No valid PicklistItem objects found")
                            return@forEach
                        }
                        
                        val totalQty = validItems.sumOf { it.qtyPl }
                        // Gunakan hasil terbesar antara qtyScan dari items dan jumlah scan hari ini dari tabel scan
                        val scannedQtyFromItems = validItems.sumOf { it.qtyScan }
                        val scannedQty = maxOf(scannedQtyFromItems, scannedToday)
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
                        // Jika items belum tersedia tapi ada scan hari ini, tandai sebagai sudah discan
                        val status = if (scannedToday > 0) {
                            PicklistStatus(
                                picklistNumber = picklistNumber,
                                isScanned = true,
                                remainingQty = 0,
                                totalQty = 0,
                                scannedQty = scannedToday,
                                lastScanTime = null,
                                overscanQty = 0
                            )
                        } else {
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
                        
                        statuses.add(status)
                        cacheManager.setPicklistStatus(picklistNumber, status)
                        Log.d(TAG, "⚠️ Picklist $picklistNumber: items not found, scansToday=$scannedToday, isScanned=${status.isScanned}")
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
            
        } catch (ce: CancellationException) {
            Log.d(TAG, "ℹ️ getAllPicklistCompletionStatuses cancelled (normal on navigation)")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating all completion statuses: ${e.message}")
            emptyList()
        }
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
            
            // Jika salah satu ATAU kedua data ada di cache, kembalikan yang tersedia dulu
            if (cachedItems != null || cachedEpcList != null) {
                Log.d(TAG, "✅ Using cached data for $picklistNo: ${cachedItems?.size ?: 0} items, ${cachedEpcList?.size ?: 0} EPCs")
                
                // Lakukan background refresh untuk melengkapi atau memperbarui data yang kurang
                repositoryScope.launch {
                    try {
                        Log.d(TAG, "🔄 Background refresh for incremental update: batch data for $picklistNo")
                        val freshItems = supabaseService.getPicklistItems(picklistNo)
                        val freshEpcList = supabaseService.getProcessedEpcList(picklistNo)
                        
                        cacheManager.updatePicklistItemsIncremental(picklistNo, freshItems)
                        cacheManager.updateProcessedEpcListIncremental(picklistNo, freshEpcList)
                        
                        // **PERBAIKAN BARU**: Force save cache untuk memastikan persistence
                        cacheManager.forceSaveCache()
                        
                        Log.d(TAG, "✅ Background incremental update completed for batch data: $picklistNo")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Background incremental update failed for batch data $picklistNo: ${e.message}")
                    }
                }
                // Kembalikan pasangan dengan data yang tersedia (gunakan emptyList() bila null)
                return@withContext Pair(cachedItems ?: emptyList(), cachedEpcList ?: emptyList())
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
            
            // **PERBAIKAN BARU**: Force save cache untuk memastikan persistence
            cacheManager.forceSaveCache()
            
            Log.d(TAG, "✅ Batch fetch completed: ${items.size} items, ${epcList.size} EPCs for picklist: $picklistNo")
            Pair(items, epcList)
        } catch (ce: CancellationException) {
            Log.d(TAG, "ℹ️ getPicklistDataBatch cancelled (normal on navigation)")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch fetch: ${e.message}", e)
            Pair(emptyList(), emptyList())
        }
    }
    
    /**
     * OPTIMASI: Get processed EPC list dengan cache dan incremental update
     */
    suspend fun getProcessedEpcList(picklistNo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            // Track query call
            trackQueryCall("getProcessedEpcList", picklistNo)
            
            Log.d(TAG, "🔥 Getting processed EPC list for picklist: $picklistNo")
            
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
                        
                        // **PERBAIKAN BARU**: Force save cache untuk memastikan persistence
                        cacheManager.forceSaveCache()
                        
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
            
            // **PERBAIKAN BARU**: Force save cache untuk memastikan persistence
            cacheManager.forceSaveCache()
            
            Log.d(TAG, "✅ Found ${epcList.size} processed EPCs for picklist: $picklistNo")
            epcList
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting processed EPC list: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Force refresh picklist completion statuses: bypass cache dan langsung fetch dari jaringan
     * Digunakan untuk memastikan data terbaru saat modal dibuka
     */
    suspend fun forceRefreshPicklistCompletionStatuses(picklistNumbers: List<String>): List<PicklistStatus> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 FORCE REFRESH: Calculating completion statuses for ${picklistNumbers.size} picklists (bypass cache)")
            
            // **OPTIMASI**: Langsung fetch dari Supabase tanpa cache
            val batchResults = getPicklistItemsBatchForceRefresh(picklistNumbers)
            
            val statuses = mutableListOf<PicklistStatus>()

            // Ambil scanned count HARI INI untuk setiap picklist secara paralel
            val scannedCountsToday = picklistNumbers.associateWith { pickNo ->
                async {
                    try {
                        supabaseService.getPicklistScans(pickNo).size
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching scans for $pickNo: ${e.message}")
                        0
                    }
                }
            }.mapValues { it.value.await() }

            batchResults.forEach { (picklistNumber, items) ->
                try {
                    val scannedToday = scannedCountsToday[picklistNumber] ?: 0

                    if (items.isNotEmpty()) {
                        // **PERBAIKAN**: Pastikan items adalah List<PicklistItem> yang valid
                        val validItems = items.filterIsInstance<PicklistItem>()
                        if (validItems.isEmpty()) {
                            Log.e(TAG, "❌ Error calculating status for $picklistNumber: No valid PicklistItem objects found")
                            return@forEach
                        }
                        
                        val totalQty = validItems.sumOf { it.qtyPl }
                        val scannedQty = validItems.sumOf { it.qtyScan }
                        val remainingQty = totalQty - scannedQty
                        val isScanned = scannedQty > 0
                        
                        val status = PicklistStatus(
                            picklistNumber = picklistNumber,
                            isScanned = isScanned,
                            remainingQty = remainingQty,
                            totalQty = totalQty,
                            scannedQty = scannedQty,
                            lastScanTime = null,
                            overscanQty = kotlin.math.max(0, scannedQty - totalQty)
                        )
                        
                        statuses.add(status)
                        Log.d(TAG, "🔥 FORCE REFRESH Status: $picklistNumber - scanned=$isScanned, total=$totalQty, scanned=$scannedQty, remaining=$remainingQty")
                    } else {
                        // Picklist belum pernah di-scan atau tidak ada data
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
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing status for $picklistNumber: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ FORCE REFRESH: Calculated ${statuses.size} completion statuses")
            statuses
            
        } catch (ce: CancellationException) {
            Log.d(TAG, "ℹ️ forceRefreshPicklistCompletionStatuses cancelled (normal on navigation)")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in force refresh completion statuses: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Force refresh picklist items batch: bypass cache dan langsung fetch dari Supabase
     */
    private suspend fun getPicklistItemsBatchForceRefresh(picklistNumbers: List<String>): Map<String, List<PicklistItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 FORCE REFRESH: Batch fetching items for ${picklistNumbers.size} picklists (bypass cache)")
            
            val deferredResults = picklistNumbers.map { picklistNo ->
                async {
                    try {
                        // **OPTIMASI**: Langsung fetch dari Supabase tanpa cache
                        val items = supabaseService.getPicklistItems(picklistNo)
                        picklistNo to items
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error force fetching items for $picklistNo: ${e.message}")
                        picklistNo to emptyList<PicklistItem>()
                    }
                }
            }
            
            val results = deferredResults.awaitAll().associate { it.first to it.second }
            
            Log.d(TAG, "✅ FORCE REFRESH: Batch items fetch completed")
            results
            
        } catch (ce: CancellationException) {
            Log.d(TAG, "ℹ️ getPicklistItemsBatchForceRefresh cancelled (normal on navigation)")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in force refresh batch items fetch: ${e.message}", e)
            emptyMap()
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
            Log.d(TAG, "🔥 === GET CACHE STATS START ===")
            Log.d(TAG, "🔍 DEBUG: getCacheStats() called at ${System.currentTimeMillis()}")
            val stats = cacheManager.getCacheStats()
            Log.d(TAG, "🔍 DEBUG: Cache stats result: $stats")
            stats
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting cache stats: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Test koneksi ke Supabase
     */
    suspend fun testSupabaseConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 === TEST SUPABASE CONNECTION START ===")
            Log.d(TAG, "🔍 DEBUG: testSupabaseConnection() called at ${System.currentTimeMillis()}")
            val result = supabaseService.testConnection()
            Log.d(TAG, "🔍 DEBUG: Supabase connection test result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing Supabase connection: ${e.message}", e)
            false
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
     * ULTRA OPTIMASI: Ambil SEMUA data hari ini dalam 1 SINGLE query saja (ULTRA CEPAT!)
     * - 1 query dengan JOIN untuk semua data (picklist items + scan data)
     * - Local processing untuk group dan olah data
     * - Total: 1 query untuk SEMUA data hari ini
     */
    suspend fun getAllTodayDataUltraOptimized(): Map<String, Pair<List<PicklistItem>, List<String>>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 === GET ALL TODAY DATA ULTRA OPTIMIZED START ===")
            Log.d(TAG, "🔍 DEBUG: getAllTodayDataUltraOptimized() called at ${System.currentTimeMillis()}")
            Log.d(TAG, "🚀 ULTRA OPTIMASI: Getting ALL today's data in 1 SINGLE query!")
            
            val startTime = System.currentTimeMillis()
            
            // **ULTRA OPTIMASI**: Ambil SEMUA data dalam 1 query dengan JOIN
            val allData = supabaseService.getAllTodayDataSingleQuery()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ ULTRA OPTIMASI: Retrieved ${allData.size} picklists in ${duration}ms (1 SINGLE query!)")
            if (allData.size > 0) {
                Log.d(TAG, "📊 Performance: ${duration / allData.size}ms per picklist")
            } else {
                Log.d(TAG, "📊 Performance: No picklists found to calculate performance")
            }
            
            // Simpan ke cache
            allData.forEach { (picklistNo, data) ->
                val (items, scans) = data
                cacheManager.smartUpdatePicklistItems(picklistNo, items)
                cacheManager.updateProcessedEpcListIncremental(picklistNo, scans)
            }
            
            // Force save cache
            cacheManager.forceSaveCache()
            
            allData
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in ultra optimized today data fetch: ${e.message}", e)
            
            // **FALLBACK**: Jika ultra optimasi gagal, coba optimasi lama
            Log.d(TAG, "🔄 FALLBACK: Trying optimized approach...")
            try {
                val fallbackResult = getAllTodayDataOptimized()
                Log.d(TAG, "✅ FALLBACK: Retrieved ${fallbackResult.size} picklists from optimized approach")
                return@withContext fallbackResult
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ FALLBACK: Error in optimized approach: ${fallbackError.message}", fallbackError)
                
                // **FALLBACK 2**: Jika masih gagal, coba ambil dari cache yang ada
                Log.d(TAG, "🔄 FALLBACK 2: Trying to get data from cache...")
                try {
                    val cachedData = mutableMapOf<String, Pair<List<PicklistItem>, List<String>>>()
                    
                    val cachedPicklists = cacheManager.getAllPicklists()
                    if (cachedPicklists != null && cachedPicklists.isNotEmpty()) {
                        Log.d(TAG, "✅ FALLBACK 2: Found ${cachedPicklists.size} cached picklists")
                        
                        cachedPicklists.forEach { picklistNo ->
                            val cachedItems = cacheManager.getPicklistItems(picklistNo)
                            val cachedScans = cacheManager.getProcessedEpcList(picklistNo)
                            
                            if (cachedItems != null && cachedScans != null) {
                                cachedData[picklistNo] = Pair(cachedItems, cachedScans)
                                Log.d(TAG, "✅ FALLBACK 2: Loaded $picklistNo from cache")
                            }
                        }
                        
                        Log.d(TAG, "✅ FALLBACK 2: Retrieved ${cachedData.size} picklists from cache")
                        return@withContext cachedData
                    } else {
                        Log.d(TAG, "❌ FALLBACK 2: No cached picklists available")
                    }
                } catch (fallback2Error: Exception) {
                    Log.e(TAG, "❌ FALLBACK 2: Error getting cached data: ${fallback2Error.message}", fallback2Error)
                }
                
                emptyMap()
            }
        }
    }
    
    /**
     * OPTIMASI BARU: Ambil SEMUA data hari ini dalam 2 query saja (SANGAT CEPAT!)
     * - 1 query untuk semua picklist items hari ini
     * - 1 query untuk semua scan data hari ini
     * - Total: 2 queries untuk SEMUA data hari ini
     */
    suspend fun getAllTodayDataOptimized(): Map<String, Pair<List<PicklistItem>, List<String>>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 === GET ALL TODAY DATA OPTIMIZED START ===")
            Log.d(TAG, "🔍 DEBUG: getAllTodayDataOptimized() called at ${System.currentTimeMillis()}")
            Log.d(TAG, "🚀 OPTIMASI BARU: Getting ALL today's data in 2 queries only!")
            
            val startTime = System.currentTimeMillis()
            
            // **OPTIMASI BARU**: Ambil SEMUA data hari ini dalam 2 query paralel
            val itemsDeferred = async { supabaseService.getAllTodayData() }
            val scansDeferred = async { supabaseService.getAllTodayScanData() }
            
            // Tunggu kedua query selesai
            val allItems = itemsDeferred.await()
            val allScans = scansDeferred.await()
            
            // Gabungkan data items dan scans per picklist
            val combinedData = mutableMapOf<String, Pair<List<PicklistItem>, List<String>>>()
            
            // Ambil semua picklist numbers yang ada
            val allPicklistNumbers = (allItems.keys + allScans.keys).distinct()
            
            allPicklistNumbers.forEach { picklistNo ->
                val items = allItems[picklistNo] ?: emptyList()
                val scans = allScans[picklistNo] ?: emptyList()
                combinedData[picklistNo] = Pair(items, scans)
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ OPTIMASI BARU: Retrieved ${combinedData.size} picklists in ${duration}ms (2 queries only!)")
            if (combinedData.size > 0) {
                Log.d(TAG, "📊 Performance: ${duration / combinedData.size}ms per picklist")
            } else {
                Log.d(TAG, "📊 Performance: No picklists found to calculate performance")
            }
            
            // Simpan ke cache
            combinedData.forEach { (picklistNo, data) ->
                val (items, scans) = data
                cacheManager.smartUpdatePicklistItems(picklistNo, items)
                cacheManager.updateProcessedEpcListIncremental(picklistNo, scans)
            }
            
            // Force save cache
            cacheManager.forceSaveCache()
            
            combinedData
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in optimized today data fetch: ${e.message}", e)
            
            // **FALLBACK**: Jika network error, coba ambil dari cache yang ada
            Log.d(TAG, "🔄 FALLBACK: Trying to get data from cache due to network error...")
            try {
                val cachedData = mutableMapOf<String, Pair<List<PicklistItem>, List<String>>>()
                
                // Ambil picklist numbers dari cache
                val cachedPicklists = cacheManager.getAllPicklists()
                if (cachedPicklists != null && cachedPicklists.isNotEmpty()) {
                    Log.d(TAG, "✅ FALLBACK: Found ${cachedPicklists.size} cached picklists")
                    
                    cachedPicklists.forEach { picklistNo ->
                        val cachedItems = cacheManager.getPicklistItems(picklistNo)
                        val cachedScans = cacheManager.getProcessedEpcList(picklistNo)
                        
                        if (cachedItems != null && cachedScans != null) {
                            cachedData[picklistNo] = Pair(cachedItems, cachedScans)
                            Log.d(TAG, "✅ FALLBACK: Loaded $picklistNo from cache")
                        }
                    }
                    
                    Log.d(TAG, "✅ FALLBACK: Retrieved ${cachedData.size} picklists from cache")
                    return@withContext cachedData
                } else {
                    Log.d(TAG, "❌ FALLBACK: No cached picklists available")
                }
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ FALLBACK: Error getting cached data: ${fallbackError.message}", fallbackError)
            }
            
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