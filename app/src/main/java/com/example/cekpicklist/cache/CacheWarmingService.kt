package com.example.cekpicklist.cache

import android.app.Application
import android.util.Log
import com.example.cekpicklist.repository.Repository
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
    private val cacheManager = CacheManager(application.applicationContext)
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
            Log.d(TAG, "🔥 Warming frequently used data...")
            
            // 1. Warm semua picklist numbers
            val picklists = repository.getPicklists()
            Log.d(TAG, "✅ Warmed picklists: ${picklists.size} items")
            
            // 2. Warm picklist yang sering digunakan (ambil 3 pertama)
            val frequentPicklists = picklists.take(3)
            frequentPicklists.forEach { picklistNo ->
                try {
                    // Warm picklist items
                    repository.getPicklistItems(picklistNo)
                    Log.d(TAG, "✅ Warmed picklist items: $picklistNo")
                    
                    // Warm processed EPC list
                    repository.getProcessedEpcList(picklistNo)
                    Log.d(TAG, "✅ Warmed processed EPC list: $picklistNo")
                    
                    // Rate limiting
                    delay(100)
                    
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to warm $picklistNo: ${e.message}")
                }
            }
            
            // 3. Warm picklist statuses (skip karena method tidak tersedia)
            // repository.getPicklistStatuses() // Method tidak tersedia
            Log.d(TAG, "✅ Skipped picklist statuses (method not available)")
            
            Log.d(TAG, "🎉 Cache warming completed successfully")
            
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
