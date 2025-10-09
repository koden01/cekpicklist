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
        
        // **PERBAIKAN BARU**: Start background refresh untuk persistence 15 jam
        startBackgroundRefresh()
        
        Log.d(TAG, "✅ Application initialized successfully")
    }
    
    /**
     * Start cache warming service
     */
    private fun startCacheWarming() {
        try {
            Log.d(TAG, "🔥 === MYAPPLICATION START CACHE WARMING ===")
            Log.d(TAG, "🔍 DEBUG: startCacheWarming() called at ${System.currentTimeMillis()}")
            Log.d(TAG, "🔥 Starting ULTRA OPTIMASI cache warming service...")
            cacheWarmingService.startCacheWarming()
            Log.d(TAG, "✅ ULTRA OPTIMASI Cache warming service started (1 SINGLE query!)")
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
     * Start background refresh untuk persistence 15 jam
     */
    private fun startBackgroundRefresh() {
        try {
            Log.d(TAG, "🚀 Starting background refresh service...")
            cacheManager.startBackgroundRefresh()
            Log.d(TAG, "✅ Background refresh service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting background refresh service: ${e.message}", e)
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
    
    /**
     * Cleanup saat aplikasi di-destroy
     */
    override fun onTerminate() {
        super.onTerminate()
        try {
            Log.d(TAG, "🔄 Terminating application...")
            
            // Stop background refresh
            cacheManager.stopBackgroundRefresh()
            
            // Force save cache sebelum terminate
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    cacheManager.forceSaveCache()
                    Log.d(TAG, "✅ Cache saved before terminate")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error saving cache before terminate: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "✅ Application terminated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during application termination: ${e.message}", e)
        }
    }
}
