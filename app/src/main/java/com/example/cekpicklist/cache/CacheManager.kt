package com.example.cekpicklist.cache

import android.content.Context
import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache Manager untuk mengoptimalkan performa fetch data dari SUPABASE (bukan API Nirwana).
 * - Bersifat in-memory cache dengan TTL (Time To Live) 15 jam
 * - EXTENDED status dihilangkan - cache langsung expired setelah 15 jam
 * - Fresh data (< 7.5 jam) tetap diperbarui via background refresh untuk memastikan data up-to-date
 * - Lapisan pengambil data (repository/DAO Supabase) yang bertugas memanggil method set/update di sini
 * - Tidak melakukan network call; hanya menyimpan dan menyajikan kembali data yang sudah dipopulasi
 */
class CacheManager(private val context: Context? = null) {
    
    companion object {
        private const val CACHE_TTL_MS = 15 * 60 * 60 * 1000L // 15 jam
        private const val TAG = "CacheManager"
    }
    
    // SharedPreferences untuk cache persistence sederhana
    private val sharedPreferences = context?.getSharedPreferences("cache_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Analytics untuk monitoring
    private val analytics = CacheAnalytics()
    
    // Cache untuk processed EPC list
    private val processedEpcCache = ConcurrentHashMap<String, CacheEntry<List<String>>>()
    
    // Cache untuk picklist items
    private val picklistItemsCache = ConcurrentHashMap<String, CacheEntry<List<PicklistItem>>>()
    
    // Cache untuk picklist statuses
    private val picklistStatusCache = ConcurrentHashMap<String, CacheEntry<PicklistStatus>>()
    
    // Cache untuk semua picklist numbers
    private var allPicklistsCache: CacheEntry<List<String>>? = null
    
    // Mutex untuk thread safety
    private val cacheMutex = Mutex()
    
    /**
     * Load cache dari SharedPreferences saat app start
     */
    suspend fun loadCacheFromDatabase() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Loading cache from SharedPreferences...")
            
            // Load picklist items cache
            val picklistItemsJson = sharedPreferences?.getString("picklist_items_cache", null)
            if (picklistItemsJson != null) {
                val type = object : TypeToken<Map<String, CacheEntry<List<PicklistItem>>>>() {}.type
                val loadedCache = gson.fromJson<Map<String, CacheEntry<List<PicklistItem>>>>(picklistItemsJson, type)
                if (loadedCache != null) {
                    picklistItemsCache.putAll(loadedCache)
                    Log.d(TAG, "✅ Loaded ${loadedCache.size} picklist items from cache")
                }
            }
            
            // Load processed EPC list cache
            val processedEpcJson = sharedPreferences?.getString("processed_epc_cache", null)
            if (processedEpcJson != null) {
                val type = object : TypeToken<Map<String, CacheEntry<List<String>>>>() {}.type
                val loadedCache = gson.fromJson<Map<String, CacheEntry<List<String>>>>(processedEpcJson, type)
                if (loadedCache != null) {
                    processedEpcCache.putAll(loadedCache)
                    Log.d(TAG, "✅ Loaded ${loadedCache.size} processed EPC lists from cache")
                }
            }
            
            // Load picklist status cache
            val picklistStatusJson = sharedPreferences?.getString("picklist_status_cache", null)
            if (picklistStatusJson != null) {
                val type = object : TypeToken<Map<String, CacheEntry<PicklistStatus>>>() {}.type
                val loadedCache = gson.fromJson<Map<String, CacheEntry<PicklistStatus>>>(picklistStatusJson, type)
                if (loadedCache != null) {
                    picklistStatusCache.putAll(loadedCache)
                    Log.d(TAG, "✅ Loaded ${loadedCache.size} picklist statuses from cache")
                }
            }
            
            // Load all picklists cache
            val allPicklistsJson = sharedPreferences?.getString("all_picklists_cache", null)
            if (allPicklistsJson != null) {
                val type = object : TypeToken<CacheEntry<List<String>>>() {}.type
                val loadedCache = gson.fromJson<CacheEntry<List<String>>>(allPicklistsJson, type)
                if (loadedCache != null) {
                    allPicklistsCache = loadedCache
                    Log.d(TAG, "✅ Loaded all picklists from cache")
                }
            }
            
            Log.d(TAG, "🎉 Cache loading completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading cache from SharedPreferences: ${e.message}", e)
        }
    }
    
    /**
     * Save cache ke SharedPreferences
     */
    private suspend fun saveCacheToDatabase() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Saving cache to SharedPreferences...")
            
            // Save picklist items cache
            val picklistItemsJson = gson.toJson(picklistItemsCache)
            sharedPreferences?.edit()?.putString("picklist_items_cache", picklistItemsJson)?.apply()
            
            // Save processed EPC list cache
            val processedEpcJson = gson.toJson(processedEpcCache)
            sharedPreferences?.edit()?.putString("processed_epc_cache", processedEpcJson)?.apply()
            
            // Save picklist status cache
            val picklistStatusJson = gson.toJson(picklistStatusCache)
            sharedPreferences?.edit()?.putString("picklist_status_cache", picklistStatusJson)?.apply()
            
            // Save all picklists cache
            val allPicklistsJson = gson.toJson(allPicklistsCache)
            sharedPreferences?.edit()?.putString("all_picklists_cache", allPicklistsJson)?.apply()
            
            Log.d(TAG, "✅ Cache saved to SharedPreferences successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving cache to SharedPreferences: ${e.message}", e)
        }
    }
    
    /**
     * Cleanup expired data dari SharedPreferences
     */
    suspend fun cleanupExpiredData() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 Cleaning up expired cache data...")
            
            val currentTime = System.currentTimeMillis()
            var cleanedCount = 0
            
            // Cleanup expired picklist items
            val expiredPicklistItems = picklistItemsCache.filter { (_, entry) -> 
                entry.isExpired() 
            }.keys.toList()
            expiredPicklistItems.forEach { key ->
                picklistItemsCache.remove(key)
                cleanedCount++
            }
            
            // Cleanup expired processed EPC lists
            val expiredProcessedEpc = processedEpcCache.filter { (_, entry) -> 
                entry.isExpired() 
            }.keys.toList()
            expiredProcessedEpc.forEach { key ->
                processedEpcCache.remove(key)
                cleanedCount++
            }
            
            // Cleanup expired picklist statuses
            val expiredPicklistStatus = picklistStatusCache.filter { (_, entry) -> 
                entry.isExpired() 
            }.keys.toList()
            expiredPicklistStatus.forEach { key ->
                picklistStatusCache.remove(key)
                cleanedCount++
            }
            
            // Cleanup expired all picklists
            if (allPicklistsCache?.isExpired() == true) {
                allPicklistsCache = null
                cleanedCount++
            }
            
            // Save updated cache to SharedPreferences
            if (cleanedCount > 0) {
                saveCacheToDatabase()
            }
            
            Log.d(TAG, "✅ Cleaned up $cleanedCount expired cache entries")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up expired cache data: ${e.message}", e)
        }
    }
    
    /**
     * Get analytics report
     */
    suspend fun getAnalyticsReport(): CacheAnalytics.CacheAnalyticsReport = withContext(Dispatchers.IO) {
        analytics.getAnalyticsReport()
    }
    
    /**
     * Get cache hit rate
     */
    fun getCacheHitRate(): Double {
        return analytics.getCacheHitRate()
    }
    
    /**
     * Get cache miss rate
     */
    fun getCacheMissRate(): Double {
        return analytics.getCacheMissRate()
    }
    
    /**
     * Get most popular cache keys
     */
    fun getMostPopularKeys(limit: Int = 10): List<Pair<String, Long>> {
        return analytics.getMostPopularKeys(limit)
    }
    
    /**
     * Reset analytics data
     */
    fun resetAnalytics() {
        analytics.reset()
    }
    
    /**
     * Cache entry dengan timestamp dan informasi freshness
     */
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS
        }
        
        // Removed isExpiredWithExtension() - EXTENDED status dihilangkan
        
        // Removed canBeExtended() - EXTENDED status dihilangkan
        
        fun isFresh(): Boolean {
            val age = System.currentTimeMillis() - timestamp
            val freshThreshold = CACHE_TTL_MS / 2 // Fresh jika kurang dari 7.5 jam
            // Data fresh tetap diperbarui via background refresh untuk memastikan data up-to-date
            return age < freshThreshold
        }
        
        fun isStale(): Boolean {
            val age = System.currentTimeMillis() - timestamp
            val staleThreshold = CACHE_TTL_MS * 3 / 4 // Stale jika lebih dari 11.25 jam
            return age > staleThreshold
        }
        
        fun getAge(): Long {
            return System.currentTimeMillis() - timestamp
        }
        
        fun getAgeFormatted(): String {
            val age = getAge()
            return when {
                age < 1000 -> "${age}ms"
                age < 60000 -> "${age / 1000}s"
                age < 3600000 -> "${age / 60000}m"
                else -> "${age / 3600000}h"
            }
        }
        
        fun getFreshnessStatus(): String {
            return when {
                isExpired() -> "EXPIRED"
                isStale() -> "STALE"
                isFresh() -> "FRESH"
                else -> "AGING"
            }
        }
    }
    
    /**
     * Ambil processed EPC list dari cache atau null jika tidak ada/expired
     */
    suspend fun getProcessedEpcList(picklistNumber: String): List<String>? = cacheMutex.withLock {
        val entry = processedEpcCache[picklistNumber]
        return@withLock if (entry != null && !entry.isExpired()) {
            val freshness = entry.getFreshnessStatus()
            val age = entry.getAgeFormatted()
            Log.d(TAG, "✅ Cache HIT for processed EPC list: $picklistNumber [$freshness, age: $age]")
            entry.data
        } else {
            val reason = if (entry == null) "NOT_FOUND" else "EXPIRED"
            Log.d(TAG, "❌ Cache MISS for processed EPC list: $picklistNumber [$reason]")
            null
        }
    }
    
    /**
     * Simpan processed EPC list ke cache
     */
    suspend fun setProcessedEpcList(picklistNumber: String, epcList: List<String>) = cacheMutex.withLock {
        processedEpcCache[picklistNumber] = CacheEntry(epcList)
        Log.d(TAG, "💾 Cached processed EPC list for: $picklistNumber (${epcList.size} EPCs)")
        
        // Save to SharedPreferences
        withContext(Dispatchers.IO) {
            saveCacheToDatabase()
        }
    }
    
    /**
     * Update processed EPC list secara incremental
     * Merge EPC baru dengan yang sudah ada di cache
     */
    suspend fun updateProcessedEpcListIncremental(picklistNumber: String, newEpcList: List<String>) = cacheMutex.withLock {
        val existingEntry = processedEpcCache[picklistNumber]
        
        if (existingEntry != null && !existingEntry.isExpired()) {
            val existingEpcList = existingEntry.data
            val mergedEpcList = mergeEpcLists(existingEpcList, newEpcList)
            
            processedEpcCache[picklistNumber] = CacheEntry(mergedEpcList)
            Log.d(TAG, "🔄 Incremental update for processed EPC list: $picklistNumber (${existingEpcList.size} -> ${mergedEpcList.size} EPCs)")
        } else {
            // Tidak ada data di cache atau expired, simpan sebagai data baru
            processedEpcCache[picklistNumber] = CacheEntry(newEpcList)
            Log.d(TAG, "💾 Fresh cache for processed EPC list: $picklistNumber (${newEpcList.size} EPCs)")
        }
    }
    
    /**
     * Merge EPC lists: gabungkan yang sudah ada dengan yang baru
     */
    private fun mergeEpcLists(existingEpcList: List<String>, newEpcList: List<String>): List<String> {
        val mergedSet = existingEpcList.toMutableSet()
        val addedCount = newEpcList.count { mergedSet.add(it) }
        
        Log.d(TAG, "🔄 Merged EPC lists: ${existingEpcList.size} existing + $addedCount new = ${mergedSet.size} total")
        return mergedSet.toList()
    }
    
    /**
     * Ambil picklist items dari cache atau null jika tidak ada/expired
     */
    suspend fun getPicklistItems(picklistNumber: String): List<PicklistItem>? = cacheMutex.withLock {
        val entry = picklistItemsCache[picklistNumber]
        return@withLock if (entry != null && !entry.isExpired()) {
            val freshness = entry.getFreshnessStatus()
            val age = entry.getAgeFormatted()
            Log.d(TAG, "✅ Cache HIT for picklist items: $picklistNumber [$freshness, age: $age]")
            entry.data
        } else {
            val reason = if (entry == null) "NOT_FOUND" else "EXPIRED"
            Log.d(TAG, "❌ Cache MISS for picklist items: $picklistNumber [$reason]")
            null
        }
    }
    
    /**
     * Simpan picklist items ke cache
     */
    suspend fun setPicklistItems(picklistNumber: String, items: List<PicklistItem>) = cacheMutex.withLock {
        picklistItemsCache[picklistNumber] = CacheEntry(items)
        Log.d(TAG, "💾 Cached picklist items for: $picklistNumber (${items.size} items)")
        
        // Save to SharedPreferences
        withContext(Dispatchers.IO) {
            saveCacheToDatabase()
        }
    }
    
    /**
     * Update picklist items secara incremental
     * Merge data baru dengan data yang sudah ada di cache
     */
    suspend fun updatePicklistItemsIncremental(picklistNumber: String, newItems: List<PicklistItem>) = cacheMutex.withLock {
        val existingEntry = picklistItemsCache[picklistNumber]
        
        if (existingEntry != null && !existingEntry.isExpired()) {
            // Ada data di cache, lakukan merge
            val existingItems = existingEntry.data
            val existingFreshness = existingEntry.getFreshnessStatus()
            val existingAge = existingEntry.getAgeFormatted()
            val mergedItems = mergePicklistItems(existingItems, newItems)
            
            picklistItemsCache[picklistNumber] = CacheEntry(mergedItems)
            Log.d(TAG, "🔄 Incremental update for picklist items: $picklistNumber (${existingItems.size} -> ${mergedItems.size} items) [was: $existingFreshness, age: $existingAge]")
        } else {
            // Tidak ada data di cache atau expired, simpan sebagai data baru
            picklistItemsCache[picklistNumber] = CacheEntry(newItems)
            val reason = if (existingEntry == null) "NOT_FOUND" else "EXPIRED"
            Log.d(TAG, "💾 Fresh cache for picklist items: $picklistNumber (${newItems.size} items) [reason: $reason]")
        }
    }
    
    /**
     * Merge picklist items: update existing items dan tambahkan yang baru
     */
    private fun mergePicklistItems(existingItems: List<PicklistItem>, newItems: List<PicklistItem>): List<PicklistItem> {
        val mergedMap = mutableMapOf<String, PicklistItem>()
        
        // Tambahkan semua existing items
        existingItems.forEach { item ->
            val key = "${item.articleName}_${item.size}"
            mergedMap[key] = item
        }
        
        // Update atau tambahkan new items
        newItems.forEach { newItem ->
            val key = "${newItem.articleName}_${newItem.size}"
            val existingItem = mergedMap[key]
            
            if (existingItem != null) {
                // Update existing item dengan data terbaru
                val updatedItem = existingItem.copy(
                    qtyScan = newItem.qtyScan,
                    // Update field lainnya jika diperlukan
                    lastUpdated = System.currentTimeMillis()
                )
                mergedMap[key] = updatedItem
                Log.d(TAG, "🔄 Updated item: ${newItem.articleName} ${newItem.size} (qtyScan: ${existingItem.qtyScan} -> ${newItem.qtyScan})")
            } else {
                // Tambahkan item baru
                mergedMap[key] = newItem
                Log.d(TAG, "➕ Added new item: ${newItem.articleName} ${newItem.size}")
            }
        }
        
        return mergedMap.values.toList()
    }
    
    /**
     * Ambil picklist status dari cache atau null jika tidak ada/expired
     */
    suspend fun getPicklistStatus(picklistNumber: String): PicklistStatus? = cacheMutex.withLock {
        val entry = picklistStatusCache[picklistNumber]
        return@withLock if (entry != null && !entry.isExpired()) {
            val freshness = entry.getFreshnessStatus()
            val age = entry.getAgeFormatted()
            Log.d(TAG, "✅ Cache HIT for picklist status: $picklistNumber [$freshness, age: $age]")
            entry.data
        } else {
            val reason = if (entry == null) "NOT_FOUND" else "EXPIRED"
            Log.d(TAG, "❌ Cache MISS for picklist status: $picklistNumber [$reason]")
            null
        }
    }
    
    /**
     * Simpan picklist status ke cache
     */
    suspend fun setPicklistStatus(picklistNumber: String, status: PicklistStatus) = cacheMutex.withLock {
        picklistStatusCache[picklistNumber] = CacheEntry(status)
        Log.d(TAG, "💾 Cached picklist status for: $picklistNumber")
        
        // Save to SharedPreferences
        withContext(Dispatchers.IO) {
            saveCacheToDatabase()
        }
    }
    
    /**
     * Update picklist status secara incremental
     * Update status jika ada perubahan atau tambahkan yang baru
     */
    suspend fun updatePicklistStatusIncremental(picklistNumber: String, newStatus: PicklistStatus) = cacheMutex.withLock {
        val existingEntry = picklistStatusCache[picklistNumber]
        
        if (existingEntry != null && !existingEntry.isExpired()) {
            val existingStatus = existingEntry.data
            val existingFreshness = existingEntry.getFreshnessStatus()
            val existingAge = existingEntry.getAgeFormatted()
            
            // Cek apakah ada perubahan
            if (existingStatus != newStatus) {
                picklistStatusCache[picklistNumber] = CacheEntry(newStatus)
                Log.d(TAG, "🔄 Updated picklist status: $picklistNumber (scanned: ${existingStatus.isScanned} -> ${newStatus.isScanned}) [was: $existingFreshness, age: $existingAge]")
            } else {
                Log.d(TAG, "✅ Picklist status unchanged: $picklistNumber [$existingFreshness, age: $existingAge]")
            }
        } else {
            // Tidak ada data di cache atau expired, simpan sebagai data baru
            picklistStatusCache[picklistNumber] = CacheEntry(newStatus)
            val reason = if (existingEntry == null) "NOT_FOUND" else "EXPIRED"
            Log.d(TAG, "💾 Fresh cache for picklist status: $picklistNumber [reason: $reason]")
        }
    }
    
    /**
     * Ambil semua picklist numbers dari cache atau null jika tidak ada/expired
     */
    suspend fun getAllPicklists(): List<String>? = cacheMutex.withLock {
        val entry = allPicklistsCache
        return@withLock if (entry != null && !entry.isExpired()) {
            val freshness = entry.getFreshnessStatus()
            val age = entry.getAgeFormatted()
            Log.d(TAG, "✅ Cache HIT for all picklists [$freshness, age: $age]")
            entry.data
        } else {
            val reason = if (entry == null) "NOT_FOUND" else "EXPIRED"
            Log.d(TAG, "❌ Cache MISS for all picklists [$reason]")
            null
        }
    }
    
    /**
     * Simpan semua picklist numbers ke cache
     */
    suspend fun setAllPicklists(picklists: List<String>) = cacheMutex.withLock {
        allPicklistsCache = CacheEntry(picklists)
        Log.d(TAG, "💾 Cached all picklists (${picklists.size} items)")
        
        // Save to SharedPreferences
        withContext(Dispatchers.IO) {
            saveCacheToDatabase()
        }
    }
    
    /**
     * Update all picklists secara incremental
     * Merge picklist numbers baru dengan yang sudah ada
     */
    suspend fun updateAllPicklistsIncremental(newPicklists: List<String>) = cacheMutex.withLock {
        val existingEntry = allPicklistsCache
        
        if (existingEntry != null && !existingEntry.isExpired()) {
            val existingPicklists = existingEntry.data
            val mergedPicklists = mergePicklistNumbers(existingPicklists, newPicklists)
            
            allPicklistsCache = CacheEntry(mergedPicklists)
            Log.d(TAG, "🔄 Incremental update for all picklists (${existingPicklists.size} -> ${mergedPicklists.size} items)")
        } else {
            // Tidak ada data di cache atau expired, simpan sebagai data baru
            allPicklistsCache = CacheEntry(newPicklists)
            Log.d(TAG, "💾 Fresh cache for all picklists (${newPicklists.size} items)")
        }
    }
    
    /**
     * Invalidate cache untuk picklist tertentu
     * Digunakan saat ada perubahan data di Supabase
     */
    suspend fun invalidatePicklist(picklistNumber: String) = cacheMutex.withLock {
        picklistItemsCache.remove(picklistNumber)
        picklistStatusCache.remove(picklistNumber)
        processedEpcCache.remove(picklistNumber)
        Log.d(TAG, "🗑️ Invalidated cache for picklist: $picklistNumber")
    }
    
    /**
     * Invalidate semua cache
     * Digunakan saat ada perubahan besar di sistem
     */
    suspend fun invalidateAllCache() = cacheMutex.withLock {
        picklistItemsCache.clear()
        picklistStatusCache.clear()
        processedEpcCache.clear()
        allPicklistsCache = null
        Log.d(TAG, "🗑️ Invalidated all cache")
    }
    
    /**
     * Get cache statistics untuk monitoring
     */
    suspend fun getCacheStats(): Map<String, Any> = cacheMutex.withLock {
        val stats = mutableMapOf<String, Any>()
        
        stats["picklistItemsCacheSize"] = picklistItemsCache.size
        stats["picklistStatusCacheSize"] = picklistStatusCache.size
        stats["processedEpcCacheSize"] = processedEpcCache.size
        stats["allPicklistsCacheExists"] = allPicklistsCache != null
        
        // Hitung expired entries
        val now = System.currentTimeMillis()
        val expiredPicklistItems = picklistItemsCache.count { it.value.isExpired() }
        val expiredPicklistStatus = picklistStatusCache.count { it.value.isExpired() }
        val expiredProcessedEpc = processedEpcCache.count { it.value.isExpired() }
        
        stats["expiredPicklistItems"] = expiredPicklistItems
        stats["expiredPicklistStatus"] = expiredPicklistStatus
        stats["expiredProcessedEpc"] = expiredProcessedEpc
        
        Log.d(TAG, "📊 Cache stats: $stats")
        stats
    }
    
    /**
     * Cleanup expired entries untuk menghemat memory
     */
    suspend fun cleanupExpiredEntries() = cacheMutex.withLock {
        val now = System.currentTimeMillis()
        var cleanedCount = 0
        
        // Cleanup picklist items cache
        val expiredPicklistItems = picklistItemsCache.filter { it.value.isExpired() }
        expiredPicklistItems.forEach { picklistItemsCache.remove(it.key) }
        cleanedCount += expiredPicklistItems.size
        
        // Cleanup picklist status cache
        val expiredPicklistStatus = picklistStatusCache.filter { it.value.isExpired() }
        expiredPicklistStatus.forEach { picklistStatusCache.remove(it.key) }
        cleanedCount += expiredPicklistStatus.size
        
        // Cleanup processed EPC cache
        val expiredProcessedEpc = processedEpcCache.filter { it.value.isExpired() }
        expiredProcessedEpc.forEach { processedEpcCache.remove(it.key) }
        cleanedCount += expiredProcessedEpc.size
        
        // Cleanup all picklists cache
        if (allPicklistsCache?.isExpired() == true) {
            allPicklistsCache = null
            cleanedCount++
        }
        
        Log.d(TAG, "🧹 Cleaned up $cleanedCount expired cache entries")
    }
    
    /**
     * Merge picklist numbers: gabungkan yang sudah ada dengan yang baru
     */
    private fun mergePicklistNumbers(existingPicklists: List<String>, newPicklists: List<String>): List<String> {
        val mergedSet = existingPicklists.toMutableSet()
        val addedCount = newPicklists.count { newPicklist ->
            if (mergedSet.add(newPicklist)) {
                Log.d(TAG, "➕ Added new picklist: $newPicklist")
                true
            } else {
                Log.d(TAG, "✅ Picklist already exists: $newPicklist")
                false
            }
        }
        
        Log.d(TAG, "🔄 Merge result: ${existingPicklists.size} existing + $addedCount new = ${mergedSet.size} total")
        return mergedSet.toList().sorted()
    }
    
    /**
     * Invalidate semua cache
     */
    suspend fun invalidateAll() = cacheMutex.withLock {
        picklistItemsCache.clear()
        picklistStatusCache.clear()
        allPicklistsCache = null
        Log.d(TAG, "🗑️ Invalidated all cache")
    }
    
    /**
     * Smart update - hanya update data yang berubah (EXTENDED status dihilangkan)
     */
    suspend fun smartUpdatePicklistItems(picklistNumber: String, newItems: List<PicklistItem>) = cacheMutex.withLock {
        val existingEntry = picklistItemsCache[picklistNumber]
        
        if (existingEntry != null) {
            if (!existingEntry.isExpired()) {
                // Cache masih valid, lakukan incremental update
                updatePicklistItemsIncremental(picklistNumber, newItems)
            } else {
                // Cache expired, simpan fresh data
                picklistItemsCache[picklistNumber] = CacheEntry(newItems)
                Log.d(TAG, "💾 Fresh cache (expired): $picklistNumber (${newItems.size} items)")
            }
        } else {
            // Tidak ada cache, simpan fresh data
            picklistItemsCache[picklistNumber] = CacheEntry(newItems)
            Log.d(TAG, "💾 Fresh cache (new): $picklistNumber (${newItems.size} items)")
        }
    }
    
    /**
     * Find items yang berubah antara existing dan new data
     */
    private fun findChangedItems(existing: List<PicklistItem>, new: List<PicklistItem>): List<PicklistItem> {
        val existingMap = existing.associateBy { "${it.articleId}_${it.size}" }
        val changedItems = mutableListOf<PicklistItem>()
        
        new.forEach { newItem ->
            val key = "${newItem.articleId}_${newItem.size}"
            val existingItem = existingMap[key]
            
            if (existingItem == null) {
                // Item baru
                changedItems.add(newItem)
            } else if (existingItem.qtyScan != newItem.qtyScan || 
                      existingItem.qtyPl != newItem.qtyPl ||
                      existingItem.tagStatus != newItem.tagStatus) {
                // Item berubah
                changedItems.add(newItem)
            }
        }
        
        return changedItems
    }
    
    /**
     * Real-time update detection - cek apakah ada perubahan data di Supabase
     */
    suspend fun detectDataChanges(picklistNumber: String, newItems: List<PicklistItem>): ChangeDetectionResult {
        val existingEntry = picklistItemsCache[picklistNumber]
        
        if (existingEntry == null) {
            return ChangeDetectionResult(
                hasChanges = true,
                changeType = "NEW_DATA",
                changedItems = newItems,
                totalItems = newItems.size
            )
        }
        
        val existingItems = existingEntry.data
        val changedItems = findChangedItems(existingItems, newItems)
        
        return ChangeDetectionResult(
            hasChanges = changedItems.isNotEmpty(),
            changeType = if (changedItems.isEmpty()) "NO_CHANGES" else "UPDATED_DATA",
            changedItems = changedItems,
            totalItems = newItems.size,
            existingItems = existingItems.size
        )
    }
    
    /**
     * Data class untuk hasil deteksi perubahan
     */
    data class ChangeDetectionResult(
        val hasChanges: Boolean,
        val changeType: String, // "NEW_DATA", "UPDATED_DATA", "NO_CHANGES"
        val changedItems: List<PicklistItem>,
        val totalItems: Int,
        val existingItems: Int = 0
    )
    
    /**
     * Auto-refresh dengan polling untuk mendeteksi perubahan data di Supabase
     * Dengan TTL 15 jam, polling interval bisa lebih jarang
     */
    suspend fun startAutoRefresh(
        picklistNumber: String,
        refreshIntervalMs: Long = 5 * 60 * 1000L, // 5 menit (disesuaikan dengan TTL 15 jam)
        maxRefreshCount: Int = 10, // Maksimal 10 kali refresh
        onDataChanged: (ChangeDetectionResult) -> Unit
    ) {
        var refreshCount = 0
        
        while (refreshCount < maxRefreshCount) {
            try {
                // Simulasi fetch data dari Supabase (dalam implementasi nyata, ini akan memanggil Repository)
                // val freshData = repository.getPicklistItems(picklistNumber)
                
                // Untuk demo, kita skip implementasi nyata
                Log.d(TAG, "🔄 Auto-refresh check #${refreshCount + 1} for: $picklistNumber")
                
                // Tunggu interval sebelum refresh berikutnya
                kotlinx.coroutines.delay(refreshIntervalMs)
                refreshCount++
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Auto-refresh error: ${e.message}")
                break
            }
        }
        
        Log.d(TAG, "⏹️ Auto-refresh completed for: $picklistNumber (${refreshCount} checks)")
    }
    
    /**
     * Refresh cache secara manual untuk picklist tertentu (EXTENDED status dihilangkan)
     * Method ini tetap ada untuk kompatibilitas tapi tidak menggunakan extension logic
     */
    suspend fun extendCache(picklistNumber: String, extensionMs: Long = 0L) = cacheMutex.withLock {
        val entry = picklistItemsCache[picklistNumber]
        if (entry != null && !entry.isExpired()) {
            // Refresh cache dengan timestamp baru (tidak ada extension)
            picklistItemsCache[picklistNumber] = CacheEntry(entry.data)
            Log.d(TAG, "🔄 Cache refreshed for: $picklistNumber (no extension)")
            return@withLock true
        }
        Log.d(TAG, "❌ Cannot refresh cache for: $picklistNumber (not found or expired)")
        return@withLock false
    }
    
    /**
     * Force refresh cache untuk picklist tertentu (bypass cache)
     */
    suspend fun forceRefreshCache(picklistNumber: String) = cacheMutex.withLock {
        picklistItemsCache.remove(picklistNumber)
        Log.d(TAG, "🔄 Cache force refreshed for: $picklistNumber")
    }
    
    /**
     * Clear expired entries
     */
    suspend fun clearExpiredEntries() = cacheMutex.withLock {
        val now = System.currentTimeMillis()
        
        // Clear expired picklist items
        picklistItemsCache.entries.removeAll { it.value.isExpired() }
        
        // Clear expired picklist statuses
        picklistStatusCache.entries.removeAll { it.value.isExpired() }
        
        // Clear expired all picklists
        if (allPicklistsCache?.isExpired() == true) {
            allPicklistsCache = null
        }
        
        Log.d(TAG, "🧹 Cleared expired cache entries")
    }
    
    /**
     * Estimate memory usage (rough calculation)
     */
    private fun estimateMemoryUsage(): Long {
        var totalSize = 0L
        
        // Estimate picklist items cache size
        picklistItemsCache.values.forEach { entry ->
            totalSize += entry.data.size * 200L // Rough estimate per item
        }
        
        // Estimate picklist status cache size
        totalSize += picklistStatusCache.size * 100L // Rough estimate per status
        
        // Estimate all picklists cache size
        allPicklistsCache?.let { entry ->
            totalSize += entry.data.size * 50L // Rough estimate per picklist number
        }
        
        return totalSize
    }
    
    /**
     * Cache statistics data class
     */
    data class CacheStats(
        val picklistItemsCount: Int,
        val picklistStatusCount: Int,
        val hasAllPicklists: Boolean,
        val totalMemoryUsage: Long,
        val freshCount: Int = 0,
        val staleCount: Int = 0,
        val expiredCount: Int = 0
    )
    
    /**
     * Get detailed cache statistics
     */
    suspend fun getDetailedCacheStats(): CacheStats = cacheMutex.withLock {
        val picklistItemsCount = picklistItemsCache.size
        val picklistStatusCount = picklistStatusCache.size
        val hasAllPicklists = allPicklistsCache != null
        val totalMemoryUsage = estimateMemoryUsage()
        
        // Hitung status cache (EXTENDED dihilangkan)
        var freshCount = 0
        var staleCount = 0
        var expiredCount = 0
        
        picklistItemsCache.values.forEach { entry ->
            when (entry.getFreshnessStatus()) {
                "FRESH" -> freshCount++
                "STALE" -> staleCount++
                "EXPIRED" -> expiredCount++
            }
        }
        
        CacheStats(
            picklistItemsCount = picklistItemsCount,
            picklistStatusCount = picklistStatusCount,
            hasAllPicklists = hasAllPicklists,
            totalMemoryUsage = totalMemoryUsage,
            freshCount = freshCount,
            staleCount = staleCount,
            expiredCount = expiredCount
        )
    }
}
