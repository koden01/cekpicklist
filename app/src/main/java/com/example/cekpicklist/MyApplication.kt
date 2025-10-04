package com.example.cekpicklist

import android.app.Application
import android.util.Log
import com.example.cekpicklist.cache.CacheWarmingService
import com.example.cekpicklist.cache.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class untuk inisialisasi cache warming dan persistence
 */
class MyApplication : Application() {
    
    private lateinit var cacheWarmingService: CacheWarmingService
    private lateinit var cacheManager: CacheManager
    
    companion object {
        private const val TAG = "MyApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "🚀 Application starting...")
        
        // Initialize cache manager dengan context
        cacheManager = CacheManager(this)
        
        // Initialize cache warming service
        cacheWarmingService = CacheWarmingService(this)
        
        // Start cache warming
        startCacheWarming()
        
        // Load cache dari database
        loadCacheFromDatabase()
        
        Log.d(TAG, "✅ Application initialized successfully")
    }
    
    /**
     * Start cache warming service
     */
    private fun startCacheWarming() {
        try {
            Log.d(TAG, "🔥 Starting cache warming service...")
            cacheWarmingService.startCacheWarming()
            Log.d(TAG, "✅ Cache warming service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting cache warming service: ${e.message}", e)
        }
    }
    
    /**
     * Load cache dari database saat app start
     */
    private fun loadCacheFromDatabase() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔄 Loading cache from database...")
                cacheManager.loadCacheFromDatabase()
                Log.d(TAG, "✅ Cache loaded from database")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading cache from database: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get cache warming service instance
     */
    fun getCacheWarmingService(): CacheWarmingService {
        return cacheWarmingService
    }
    
    /**
     * Get cache manager instance
     */
    fun getCacheManager(): CacheManager {
        return cacheManager
    }
}
