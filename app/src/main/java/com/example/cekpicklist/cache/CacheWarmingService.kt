package com.example.cekpicklist.cache

import android.app.Application
import android.util.Log
import com.example.cekpicklist.repository.Repository
import com.example.cekpicklist.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Cache Warming Service untuk pre-load data yang sering digunakan
 * - Pre-load picklist yang sering diakses
 * - Pre-load processed EPC lists
 * - Background warming saat app idle
 */
class CacheWarmingService(private val application: Application) {
    
    private val repository = Repository(application.applicationContext)
    // Use the same CacheManager instance from MyApplication to keep cache consistent
    private val cacheManager: CacheManager = (application as? MyApplication)?.getCacheManager()
        ?: CacheManager(application.applicationContext)
    private val warmingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "CacheWarmingService"
        private const val WARMING_DELAY_MS = 2000L // 2 detik delay setelah app start
        private const val IDLE_WARMING_INTERVAL_MS = 5 * 60 * 1000L // 5 menit
    }
    
    /**
     * Start cache warming saat aplikasi dimulai
     */
    fun startCacheWarming() {
        warmingScope.launch {
            try {
                Log.d(TAG, "🔥 === CACHE WARMING SERVICE START ===")
                Log.d(TAG, "🔍 DEBUG: startCacheWarming() called at ${System.currentTimeMillis()}")
                Log.d(TAG, "🔥 Starting cache warming...")
                
                // Delay untuk memastikan app sudah fully loaded
                delay(WARMING_DELAY_MS)
                
                // Warm cache untuk data yang sering digunakan
                warmFrequentlyUsedData()
                
                // Start idle warming
                startIdleWarming()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in cache warming: ${e.message}", e)
            }
        }
    }
    
    /**
     * Warm cache untuk data yang sering digunakan
     */
    private suspend fun warmFrequentlyUsedData() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 === CACHE WARMING WARM FREQUENTLY USED DATA START ===")
            Log.d(TAG, "🔍 DEBUG: warmFrequentlyUsedData() called at ${System.currentTimeMillis()}")
            Log.d(TAG, "🔥 Warming frequently used data...")
            
            // **OPTIMASI BARU**: Warm SEMUA data hari ini dalam 2 query saja!
            val startTime = System.currentTimeMillis()
            
            // 1. Warm semua picklist numbers (1 query)
            Log.d(TAG, "🔍 DEBUG: Calling repository.getPicklists() from cache warming...")
            val picklists = repository.getPicklists()
            Log.d(TAG, "✅ Warmed picklists: ${picklists.size} items")
            
            // 2. **OPTIMASI BARU**: Warm SEMUA data hari ini dalam 2 query paralel
            Log.d(TAG, "🚀 ULTRA OPTIMASI: Warming ALL today's data in 1 SINGLE query!")
            val allTodayData = repository.getAllTodayDataUltraOptimized()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ ULTRA OPTIMASI: Warmed ${allTodayData.size} picklists in ${duration}ms (1 SINGLE query!)")
            if (allTodayData.size > 0) {
                Log.d(TAG, "📊 Performance: ${duration / allTodayData.size}ms per picklist")
            } else {
                Log.d(TAG, "📊 Performance: No picklists found to calculate performance")
            }
            
            // 3. Log summary
            allTodayData.forEach { (picklistNo, data) ->
                val (items, scans) = data
                Log.d(TAG, "✅ Warmed $picklistNo: ${items.size} items, ${scans.size} scans")
            }
            
            Log.d(TAG, "🎉 ULTRA OPTIMASI Cache warming completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error warming frequently used data: ${e.message}", e)
        }
    }
    
    /**
     * Start idle warming untuk data yang mungkin dibutuhkan
     */
    private fun startIdleWarming() {
        warmingScope.launch {
            while (true) {
                try {
                    delay(IDLE_WARMING_INTERVAL_MS)
                    
                    Log.d(TAG, "🔄 Starting idle cache warming...")
                    
                    // Warm data yang mungkin dibutuhkan
                    warmIdleData()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in idle warming: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Warm data saat aplikasi idle
     */
    private suspend fun warmIdleData() = withContext(Dispatchers.IO) {
        try {
            // Cek cache statistics
            val stats = cacheManager.getDetailedCacheStats()
            Log.d(TAG, "📊 Cache stats: $stats")
            
            // Warm data yang expired atau stale
            if (stats.expiredCount > 0 || stats.staleCount > 0) {
                Log.d(TAG, "🔄 Warming expired/stale data...")
                
                // Refresh data yang expired
                val picklists = repository.getPicklists()
                picklists.take(2).forEach { picklistNo ->
                    try {
                        repository.getPicklistItems(picklistNo)
                        delay(50)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to refresh $picklistNo: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in idle data warming: ${e.message}", e)
        }
    }
    
    /**
     * Warm cache untuk picklist tertentu
     */
    fun warmPicklistCache(picklistNo: String) {
        warmingScope.launch {
            try {
                Log.d(TAG, "🔥 Warming cache for picklist: $picklistNo")
                
                // Warm picklist items
                repository.getPicklistItems(picklistNo)
                
                // Warm processed EPC list
                repository.getProcessedEpcList(picklistNo)
                
                Log.d(TAG, "✅ Cache warmed for picklist: $picklistNo")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error warming cache for $picklistNo: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get cache warming status
     */
    fun getWarmingStatus(): String {
        return "Cache warming service is running"
    }
}
