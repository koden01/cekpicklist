package com.example.cekpicklist.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * CacheManager untuk mengelola cache dengan stale-time
 * Implementasi konsep cache seperti React Query/Apollo dengan stale-time 15 jam
 */
class CacheManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "cekpicklist_cache"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_CACHED_PICKLISTS = "cached_picklists"
        private const val KEY_CACHED_ARTICLES = "cached_articles"
        private const val KEY_CACHED_SCANS = "cached_scans"
        private const val STALE_TIME_HOURS = 15L // 15 jam stale time
        
        private val TAG = "CacheManager"
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Cek apakah cache masih fresh (belum stale)
     * @return true jika cache masih fresh (< 15 jam), false jika sudah stale
     */
    fun isCacheFresh(): Boolean {
        val lastFetchTime = getLastFetchTime()
        if (lastFetchTime == null) {
            Log.d(TAG, "🔥 Cache tidak ada - perlu fetch baru")
            return false
        }
        
        val now = LocalDateTime.now()
        val hoursSinceLastFetch = ChronoUnit.HOURS.between(lastFetchTime, now)
        
        Log.d(TAG, "🔥 Cek cache freshness:")
        Log.d(TAG, "   Last fetch: $lastFetchTime")
        Log.d(TAG, "   Now: $now")
        Log.d(TAG, "   Hours since last fetch: $hoursSinceLastFetch")
        Log.d(TAG, "   Stale time: $STALE_TIME_HOURS hours")
        Log.d(TAG, "   Cache fresh: ${hoursSinceLastFetch < STALE_TIME_HOURS}")
        
        return hoursSinceLastFetch < STALE_TIME_HOURS
    }
    
    /**
     * Cek apakah cache sudah expired (lebih dari 15 jam)
     * @return true jika cache sudah expired, false jika masih valid
     */
    fun isCacheExpired(): Boolean {
        val lastFetchTime = getLastFetchTime()
        if (lastFetchTime == null) {
            Log.d(TAG, "🔥 Cache tidak ada - dianggap expired")
            return true
        }
        
        val now = LocalDateTime.now()
        val hoursSinceLastFetch = ChronoUnit.HOURS.between(lastFetchTime, now)
        
        Log.d(TAG, "🔥 Cek cache expiration:")
        Log.d(TAG, "   Last fetch: $lastFetchTime")
        Log.d(TAG, "   Now: $now")
        Log.d(TAG, "   Hours since last fetch: $hoursSinceLastFetch")
        Log.d(TAG, "   Stale time: $STALE_TIME_HOURS hours")
        Log.d(TAG, "   Cache expired: ${hoursSinceLastFetch >= STALE_TIME_HOURS}")
        
        return hoursSinceLastFetch >= STALE_TIME_HOURS
    }
    
    /**
     * Cleanup cache yang sudah expired (lebih dari 15 jam)
     * Data dianggap tidak segar dan dihapus dari cache
     */
    fun cleanupExpiredCache() {
        if (isCacheExpired()) {
            Log.d(TAG, "🔥 Cache sudah expired, melakukan cleanup...")
            clearAllCache()
            Log.d(TAG, "🔥 Expired cache berhasil dibersihkan")
        } else {
            Log.d(TAG, "🔥 Cache masih valid, tidak perlu cleanup")
        }
    }
    
    
    /**
     * Simpan timestamp fetch terakhir
     */
    fun setLastFetchTime(time: LocalDateTime) {
        val timeString = time.format(DATE_FORMATTER)
        sharedPrefs.edit()
            .putString(KEY_LAST_FETCH_TIME, timeString)
            .apply()
        
        Log.d(TAG, "🔥 Last fetch time disimpan: $timeString")
    }
    
    /**
     * Ambil timestamp fetch terakhir
     */
    fun getLastFetchTime(): LocalDateTime? {
        val timeString = sharedPrefs.getString(KEY_LAST_FETCH_TIME, null)
        return if (timeString != null) {
            try {
                LocalDateTime.parse(timeString, DATE_FORMATTER)
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Error parsing last fetch time: $timeString", e)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Simpan picklist data ke cache
     */
    fun cachePicklists(picklists: List<String>) {
        val json = gson.toJson(picklists)
        sharedPrefs.edit()
            .putString(KEY_CACHED_PICKLISTS, json)
            .apply()
        
        Log.d(TAG, "🔥 Picklists di-cache: ${picklists.size} items")
    }
    
    /**
     * Ambil picklist data dari cache
     */
    fun getCachedPicklists(): List<String>? {
        val json = sharedPrefs.getString(KEY_CACHED_PICKLISTS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Error parsing cached picklists", e)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Clear semua cache data
     */
    fun clearAllCache() {
        Log.d(TAG, "🔥 Clearing all cache data...")
        sharedPrefs.edit()
            .remove(KEY_CACHED_PICKLISTS)
            .remove(KEY_CACHED_ARTICLES)
            .remove(KEY_LAST_FETCH_TIME)
            .apply()
        Log.d(TAG, "🔥 All cache data cleared")
    }
    
    /**
     * Clear cache articles saja
     */
    fun clearArticlesCache() {
        Log.d(TAG, "🔥 Clearing articles cache...")
        sharedPrefs.edit()
            .remove(KEY_CACHED_ARTICLES)
            .apply()
        Log.d(TAG, "🔥 Articles cache cleared")
    }
    
    /**
     * Clear cache picklists saja
     */
    fun clearPicklistsCache() {
        Log.d(TAG, "🔥 Clearing picklists cache...")
        sharedPrefs.edit()
            .remove(KEY_CACHED_PICKLISTS)
            .apply()
        Log.d(TAG, "🔥 Picklists cache cleared")
    }
    
    /**
     * Simpan article data ke cache (overwrite existing)
     */
    /**
     * Cache articles dengan incremental update
     * Menambah data baru tanpa menghapus data lama
     */
    fun cacheArticlesIncremental(newArticles: List<PicklistItem>) {
        val existingArticles = getCachedArticles()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Cache incremental update:")
        Log.d(TAG, "   Existing articles: ${existingArticles.size}")
        Log.d(TAG, "   New articles: ${newArticles.size}")
        
        // Merge data baru dengan data lama
        val mergedArticles = mergeArticles(existingArticles, newArticles)
        
        // Cache data yang sudah di-merge
        cacheArticles(mergedArticles)
        
        Log.d(TAG, "🔥 Cache incremental update selesai:")
        Log.d(TAG, "   Total articles setelah merge: ${mergedArticles.size}")
    }
    
    /**
     * Cache picklists dengan incremental update
     * Menambah data baru tanpa menghapus data lama
     */
    fun cachePicklistsIncremental(newPicklists: List<String>) {
        val existingPicklists = getCachedPicklists()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Cache incremental update picklists:")
        Log.d(TAG, "   Existing picklists: ${existingPicklists.size}")
        Log.d(TAG, "   New picklists: ${newPicklists.size}")
        
        // Merge data baru dengan data lama
        val mergedPicklists = mergePicklists(existingPicklists, newPicklists)
        
        // Cache data yang sudah di-merge
        cachePicklists(mergedPicklists)
        
        Log.d(TAG, "🔥 Cache incremental update picklists selesai:")
        Log.d(TAG, "   Total picklists setelah merge: ${mergedPicklists.size}")
    }
    
    /**
     * Merge articles dengan menghindari duplikasi
     * Menggunakan kombinasi articleId + size sebagai unique key
     */
    private fun mergeArticles(existing: List<PicklistItem>, new: List<PicklistItem>): List<PicklistItem> {
        val mergedMap = mutableMapOf<String, PicklistItem>()
        
        // Tambahkan existing articles
        existing.forEach { article ->
            val key = "${article.articleId}_${article.size}_${article.noPicklist}"
            mergedMap[key] = article
        }
        
        // Tambahkan/update dengan new articles
        new.forEach { article ->
            val key = "${article.articleId}_${article.size}_${article.noPicklist}"
            mergedMap[key] = article // New articles akan override existing jika ada
        }
        
        return mergedMap.values.toList()
    }
    
    /**
     * Merge picklists dengan menghindari duplikasi
     */
    private fun mergePicklists(existing: List<String>, new: List<String>): List<String> {
        val mergedSet = existing.toMutableSet()
        mergedSet.addAll(new)
        return mergedSet.toList()
    }
    
    /**
     * Force refresh cache untuk picklist tertentu
     * Menghapus data lama untuk picklist tertentu dan menambah data baru
     */
    fun refreshPicklistCache(picklistNo: String, newArticles: List<PicklistItem>) {
        val existingArticles = getCachedArticles()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Force refresh cache untuk picklist: $picklistNo")
        Log.d(TAG, "   Existing articles: ${existingArticles.size}")
        Log.d(TAG, "   New articles: ${newArticles.size}")
        
        // Hapus data lama untuk picklist ini
        existingArticles.removeAll { it.noPicklist == picklistNo }
        
        // Tambahkan data baru
        existingArticles.addAll(newArticles)
        
        // Cache data yang sudah di-update
        cacheArticles(existingArticles)
        
        Log.d(TAG, "🔥 Force refresh cache selesai:")
        Log.d(TAG, "   Total articles setelah refresh: ${existingArticles.size}")
    }
    
    /**
     * Cek apakah ada data untuk picklist tertentu di cache
     */
    fun hasPicklistData(picklistNo: String): Boolean {
        val cachedArticles = getCachedArticles()
        return cachedArticles?.any { it.noPicklist == picklistNo } == true
    }
    
    /**
     * Get articles untuk picklist tertentu dari cache
     */
    fun getCachedArticlesForPicklist(picklistNo: String): List<PicklistItem> {
        val cachedArticles = getCachedArticles() ?: return emptyList()
        return cachedArticles.filter { it.noPicklist == picklistNo }
    }
    
    fun cacheArticles(articles: List<PicklistItem>) {
        val json = gson.toJson(articles)
        sharedPrefs.edit()
            .putString(KEY_CACHED_ARTICLES, json)
            .apply()
        
        Log.d(TAG, "🔥 Articles di-cache: ${articles.size} items")
    }
    
    /**
     * Ambil article data dari cache
     */
    fun getCachedArticles(): List<PicklistItem>? {
        val json = sharedPrefs.getString(KEY_CACHED_ARTICLES, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<PicklistItem>>() {}.type
                val articles = gson.fromJson<List<PicklistItem>>(json, type)
                Log.d(TAG, "🔥 getCachedArticles: Found ${articles.size} articles in cache")
                return articles
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Error parsing cached articles", e)
                null
            }
        } else {
            Log.d(TAG, "🔥 getCachedArticles: No cached articles found")
            null
        }
    }
    
    /**
     * Clear semua cache
     */
    fun clearCache() {
        sharedPrefs.edit()
            .remove(KEY_LAST_FETCH_TIME)
            .remove(KEY_CACHED_PICKLISTS)
            .remove(KEY_CACHED_ARTICLES)
            .apply()
        
        Log.d(TAG, "🔥 Cache dibersihkan")
    }
    
    /**
     * Dapatkan info cache untuk debugging
     */
    fun getCacheInfo(): String {
        val lastFetch = getLastFetchTime()
        val picklists = getCachedPicklists()
        val articles = getCachedArticles()
        val scans = getCachedScans()
        
        return buildString {
            appendLine("📊 Cache Info:")
            appendLine("   Last fetch: ${lastFetch ?: "Never"}")
            appendLine("   Cached picklists: ${picklists?.size ?: 0}")
            appendLine("   Cached articles: ${articles?.size ?: 0}")
            appendLine("   Cached scans: ${scans?.size ?: 0}")
            appendLine("   Cache fresh: ${isCacheFresh()}")
            appendLine("   Stale time: $STALE_TIME_HOURS hours")
        }
    }
    
    // ========== SCAN CACHE MANAGEMENT ==========
    
    /**
     * Simpan scan data ke cache
     */
    fun cacheScans(scans: List<PicklistScan>) {
        val json = gson.toJson(scans)
        sharedPrefs.edit()
            .putString(KEY_CACHED_SCANS, json)
            .apply()
        
        Log.d(TAG, "🔥 Scans di-cache: ${scans.size} items")
    }
    
    /**
     * Ambil scan data dari cache
     */
    fun getCachedScans(): List<PicklistScan>? {
        val json = sharedPrefs.getString(KEY_CACHED_SCANS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<PicklistScan>>() {}.type
                val scans = gson.fromJson<List<PicklistScan>>(json, type)
                Log.d(TAG, "🔥 getCachedScans: Found ${scans.size} scans in cache")
                return scans
            } catch (e: Exception) {
                Log.e(TAG, "🔥 Error parsing cached scans", e)
                null
            }
        } else {
            Log.d(TAG, "🔥 getCachedScans: No cached scans found")
            null
        }
    }
    
    /**
     * Tambah scan baru ke cache (incremental update)
     */
    fun addScanToCache(newScan: PicklistScan) {
        val existingScans = getCachedScans()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Adding scan to cache:")
        Log.d(TAG, "   Existing scans: ${existingScans.size}")
        Log.d(TAG, "   New scan: ${newScan.epc}")
        
        // Tambahkan scan baru
        existingScans.add(newScan)
        
        // Cache data yang sudah di-update
        cacheScans(existingScans)
        
        Log.d(TAG, "🔥 Scan added to cache successfully")
        Log.d(TAG, "   Total scans after add: ${existingScans.size}")
    }
    
    /**
     * Update scan di cache
     */
    fun updateScanInCache(updatedScan: PicklistScan) {
        val existingScans = getCachedScans()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Updating scan in cache:")
        Log.d(TAG, "   Existing scans: ${existingScans.size}")
        Log.d(TAG, "   Updated scan: ${updatedScan.epc}")
        
        // Cari dan update scan yang ada berdasarkan kombinasi noPicklist, articleId, dan epc
        val index = existingScans.indexOfFirst { 
            it.noPicklist == updatedScan.noPicklist && 
            it.articleId == updatedScan.articleId && 
            it.epc == updatedScan.epc
        }
        if (index != -1) {
            existingScans[index] = updatedScan
            Log.d(TAG, "🔥 Scan updated in cache")
        } else {
            // Jika tidak ditemukan, tambahkan sebagai scan baru
            existingScans.add(updatedScan)
            Log.d(TAG, "🔥 Scan not found, added as new scan")
        }
        
        // Cache data yang sudah di-update
        cacheScans(existingScans)
        
        Log.d(TAG, "🔥 Scan update in cache completed")
        Log.d(TAG, "   Total scans after update: ${existingScans.size}")
    }
    
    /**
     * Hapus scan dari cache
     */
    fun removeScanFromCache(scanToRemove: PicklistScan) {
        val existingScans = getCachedScans()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Removing scan from cache:")
        Log.d(TAG, "   Existing scans: ${existingScans.size}")
        Log.d(TAG, "   Scan to remove: ${scanToRemove.epc}")
        
        // Hapus scan yang sesuai berdasarkan kombinasi noPicklist, articleId, dan epc
        val removed = existingScans.removeAll { 
            it.noPicklist == scanToRemove.noPicklist && 
            it.articleId == scanToRemove.articleId && 
            it.epc == scanToRemove.epc
        }
        
        if (removed) {
            Log.d(TAG, "🔥 Scan removed from cache")
        } else {
            Log.d(TAG, "🔥 Scan not found in cache")
        }
        
        // Cache data yang sudah di-update
        cacheScans(existingScans)
        
        Log.d(TAG, "🔥 Scan removal from cache completed")
        Log.d(TAG, "   Total scans after removal: ${existingScans.size}")
    }
    
    /**
     * Get scans untuk picklist tertentu dari cache
     */
    fun getCachedScansForPicklist(picklistNo: String): List<PicklistScan> {
        val cachedScans = getCachedScans() ?: return emptyList()
        return cachedScans.filter { it.noPicklist == picklistNo }
    }
    
    /**
     * Clear scan cache saja
     */
    fun clearScansCache() {
        Log.d(TAG, "🔥 Clearing scans cache...")
        sharedPrefs.edit()
            .remove(KEY_CACHED_SCANS)
            .apply()
        Log.d(TAG, "🔥 Scans cache cleared")
    }
    
    /**
     * Clear scan cache untuk picklist tertentu
     */
    fun clearScansCacheForPicklist(picklistNo: String) {
        Log.d(TAG, "🔥 Clearing scans cache untuk picklist: $picklistNo")
        
        try {
            val cachedScans = getCachedScans()?.toMutableList() ?: mutableListOf()
            
            // Filter out scans untuk picklist ini
            val filteredScans = cachedScans.filter { it.noPicklist != picklistNo }
            
            // Cache data yang sudah di-filter
            cacheScans(filteredScans)
            
            val removedCount = cachedScans.size - filteredScans.size
            Log.d(TAG, "🔥 Removed $removedCount scans untuk picklist: $picklistNo")
            Log.d(TAG, "🔥 Remaining scans: ${filteredScans.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error clearing scans cache untuk picklist: ${e.message}", e)
        }
    }
    
    /**
     * Clear hanya overscan dan non-picklist dari cache
     * Pertahankan scan yang valid untuk picklist tertentu
     */
    fun clearOverscanAndNonPicklistFromCache(picklistNo: String) {
        Log.d(TAG, "🔥 Clearing overscan dan non-picklist untuk picklist: $picklistNo")
        
        try {
            val cachedScans = getCachedScans()?.toMutableList() ?: mutableListOf()
            
            // Filter hanya scan yang valid untuk picklist ini
            val validScans = cachedScans.filter { scan ->
                scan.noPicklist == picklistNo
            }
            
            // Update cache dengan hanya scan yang valid
            cacheScans(validScans)
            
            Log.d(TAG, "🔥 Overscan dan non-picklist cleared untuk picklist: $picklistNo")
            Log.d(TAG, "   Before: ${cachedScans.size} scans")
            Log.d(TAG, "   After: ${validScans.size} valid scans")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔥 Error clearing overscan dan non-picklist: ${e.message}", e)
        }
    }
    
    /**
     * Update cache timestamp saat ada perubahan realtime
     */
    fun updateCacheTimestamp() {
        setLastFetchTime(LocalDateTime.now())
        Log.d(TAG, "🔥 Cache timestamp updated for realtime change")
    }
    
    // ========== PICKLIST CACHE MANAGEMENT ==========
    
    /**
     * Tambah picklist item baru ke cache (incremental update)
     */
    fun addPicklistItemToCache(newItem: PicklistItem) {
        val existingItems = getCachedArticles()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Adding picklist item to cache:")
        Log.d(TAG, "   Existing items: ${existingItems.size}")
        Log.d(TAG, "   New item: ${newItem.articleName} ${newItem.size}")
        
        // Tambahkan item baru
        existingItems.add(newItem)
        
        // Cache data yang sudah di-update
        cacheArticles(existingItems)
        
        Log.d(TAG, "🔥 Picklist item added to cache successfully")
        Log.d(TAG, "   Total items after add: ${existingItems.size}")
    }
    
    /**
     * Update picklist item di cache
     */
    fun updatePicklistItemInCache(updatedItem: PicklistItem) {
        val existingItems = getCachedArticles()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Updating picklist item in cache:")
        Log.d(TAG, "   Existing items: ${existingItems.size}")
        Log.d(TAG, "   Updated item: ${updatedItem.articleName} ${updatedItem.size}")
        
        // Cari dan update item yang ada
        val index = existingItems.indexOfFirst { 
            it.articleId == updatedItem.articleId && 
            it.size == updatedItem.size && 
            it.noPicklist == updatedItem.noPicklist 
        }
        
        if (index != -1) {
            existingItems[index] = updatedItem
            Log.d(TAG, "🔥 Picklist item updated in cache")
        } else {
            // Jika tidak ditemukan, tambahkan sebagai item baru
            existingItems.add(updatedItem)
            Log.d(TAG, "🔥 Picklist item not found, added as new item")
        }
        
        // Cache data yang sudah di-update
        cacheArticles(existingItems)
        
        Log.d(TAG, "🔥 Picklist item update in cache completed")
        Log.d(TAG, "   Total items after update: ${existingItems.size}")
    }
    
    /**
     * Hapus picklist item dari cache
     */
    fun removePicklistItemFromCache(itemToRemove: PicklistItem) {
        val existingItems = getCachedArticles()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Removing picklist item from cache:")
        Log.d(TAG, "   Existing items: ${existingItems.size}")
        Log.d(TAG, "   Item to remove: ${itemToRemove.articleName} ${itemToRemove.size}")
        
        // Hapus item yang sesuai
        val removed = existingItems.removeAll { 
            it.articleId == itemToRemove.articleId && 
            it.size == itemToRemove.size && 
            it.noPicklist == itemToRemove.noPicklist 
        }
        
        if (removed) {
            Log.d(TAG, "🔥 Picklist item removed from cache")
        } else {
            Log.d(TAG, "🔥 Picklist item not found in cache")
        }
        
        // Cache data yang sudah di-update
        cacheArticles(existingItems)
        
        Log.d(TAG, "🔥 Picklist item removal from cache completed")
        Log.d(TAG, "   Total items after removal: ${existingItems.size}")
    }
    
    /**
     * Tambah picklist baru ke cache (incremental update)
     */
    fun addPicklistToCache(newPicklist: String) {
        val existingPicklists = getCachedPicklists()?.toMutableList() ?: mutableListOf()
        
        Log.d(TAG, "🔥 Adding picklist to cache:")
        Log.d(TAG, "   Existing picklists: ${existingPicklists.size}")
        Log.d(TAG, "   New picklist: $newPicklist")
        
        // Tambahkan picklist baru jika belum ada
        if (!existingPicklists.contains(newPicklist)) {
            existingPicklists.add(newPicklist)
            
            // Cache data yang sudah di-update
            cachePicklists(existingPicklists)
            
            Log.d(TAG, "🔥 Picklist added to cache successfully")
            Log.d(TAG, "   Total picklists after add: ${existingPicklists.size}")
        } else {
            Log.d(TAG, "🔥 Picklist already exists in cache")
        }
    }
}
