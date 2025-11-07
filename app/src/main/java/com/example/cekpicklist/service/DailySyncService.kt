package com.example.cekpicklist.service

import android.content.Context
import android.util.Log
import com.example.cekpicklist.repository.EnhancedRepository
import kotlinx.coroutines.*

/**
 * Service untuk mengelola daily sync dan incremental update
 * - Daily full sync saat pertama kali buka app hari ini
 * - Incremental update setiap 5 menit
 * - Retry mechanism untuk failed sync
 */
class DailySyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "DailySyncService"
        // Default 120 detik; dapat diubah dinamis via setIncrementalInterval
        private const val DEFAULT_INCREMENTAL_UPDATE_INTERVAL_MS = 120_000L
        const val ACTION_SYNC_COMPLETED = "com.example.cekpicklist.SYNC_COMPLETED"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L // 5 detik
        // Extended sync interval: setiap 5 menit untuk catch missed data
        // Bukan full sync (7 hari), tapi extended window (1 jam) - lebih efisien bandwidth
        private const val FULL_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 menit
    }
    
    private val enhancedRepository = EnhancedRepository(context)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var incrementalUpdateJob: Job? = null
    @Volatile private var incrementalIntervalMs: Long = DEFAULT_INCREMENTAL_UPDATE_INTERVAL_MS
    private var lastFullSyncTime: Long = 0L // Track last full sync time

    fun setIncrementalInterval(intervalMs: Long) {
        incrementalIntervalMs = intervalMs
        // Restart job dengan interval baru jika sudah berjalan
        if (incrementalUpdateJob?.isActive == true) {
            startIncrementalUpdate()
        }
    }
    
    /**
     * **DAILY SYNC STRATEGY**: Initialize daily sync saat pertama kali buka app
     */
    suspend fun initializeDailySync(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Initializing daily sync...")
            
            // Cek apakah sudah ada daily sync hari ini
            val hasDailySync = enhancedRepository.hasDailySyncToday()
            
            if (hasDailySync) {
                Log.d(TAG, "✅ Daily sync already completed today")
                startIncrementalUpdate()
                return@withContext true
            }
            
            // Lakukan daily full sync dengan retry mechanism
            val syncSuccess = performDailyFullSyncWithRetry()
            
            if (syncSuccess) {
                Log.d(TAG, "✅ Daily sync completed successfully")
                startIncrementalUpdate()
                return@withContext true
            } else {
                Log.e(TAG, "❌ Daily sync failed after all retries")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing daily sync: ${e.message}", e)
            false
        }
    }
    
    /**
     * **DAILY FULL SYNC**: Perform full sync dengan retry mechanism
     */
    private suspend fun performDailyFullSyncWithRetry(): Boolean = withContext(Dispatchers.IO) {
        var attempt = 1
        
        while (attempt <= MAX_RETRY_ATTEMPTS) {
            try {
                Log.d(TAG, "🔄 Daily full sync attempt $attempt/$MAX_RETRY_ATTEMPTS")
                
                val success = enhancedRepository.performDailyFullSync()
                
                if (success) {
                    Log.d(TAG, "✅ Daily full sync succeeded on attempt $attempt")
                    return@withContext true
                } else {
                    Log.w(TAG, "⚠️ Daily full sync failed on attempt $attempt")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Daily full sync error on attempt $attempt: ${e.message}", e)
            }
            
            attempt++
            
            if (attempt <= MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "⏳ Waiting ${RETRY_DELAY_MS}ms before retry...")
                delay(RETRY_DELAY_MS)
            }
        }
        
        Log.e(TAG, "❌ Daily full sync failed after $MAX_RETRY_ATTEMPTS attempts")
        false
    }
    
    /**
     * **INCREMENTAL UPDATE**: Start incremental update setiap 10 menit
     */
    private fun startIncrementalUpdate() {
        // Cancel previous job jika ada
        incrementalUpdateJob?.cancel()
        
        incrementalUpdateJob = serviceScope.launch {
            val rnd = java.util.Random()
            while (isActive) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastFullSync = currentTime - lastFullSyncTime
                    
                    // Lakukan extended sync setiap 5 menit untuk catch missed data (resi batal dari device lain)
                    // Extended sync = fetch 1 jam terakhir (bukan 7 hari) untuk hemat bandwidth
                    val shouldDoFullSync = timeSinceLastFullSync >= FULL_SYNC_INTERVAL_MS
                    
                    if (shouldDoFullSync) {
                        Log.d(TAG, "🔄 Starting EXTENDED sync (periodic, every 5 minutes)...")
                        Log.d(TAG, "   Reason: Catch missed data like cancelled resi from other devices")
                        
                        // Mundurkan lastSyncTime 1 jam untuk catch missed data (bukan full sync)
                        // Ini lebih efisien: hanya ambil data 1 jam terakhir, bukan 7 hari penuh
                        val oneHourAgo = currentTime - (60 * 60 * 1000L) // 1 jam yang lalu
                        com.example.cekpicklist.cache.BarcodeCacheManager.setLastResiSyncTime(oneHourAgo)
                        
                        val success = enhancedRepository.performIncrementalUpdate()
                        
                        if (success) {
                            lastFullSyncTime = currentTime
                            Log.d(TAG, "✅ Extended sync completed (fetched data from last 1 hour)")
                            // Beritahu UI bahwa sync selesai
                            try {
                                val intent = android.content.Intent(ACTION_SYNC_COMPLETED)
                                context.sendBroadcast(intent)
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Failed to broadcast sync completion: ${e.message}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Extended sync failed")
                        }
                    } else {
                        Log.d(TAG, "🔄 Starting incremental update...")
                        Log.d(TAG, "   Next full sync in: ${(FULL_SYNC_INTERVAL_MS - timeSinceLastFullSync) / 1000}s")
                        
                        val success = enhancedRepository.performIncrementalUpdate()
                        
                        if (success) {
                            Log.d(TAG, "✅ Incremental update completed")
                            // Beritahu UI bahwa sync selesai
                            try {
                                val intent = android.content.Intent(ACTION_SYNC_COMPLETED)
                                context.sendBroadcast(intent)
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Failed to broadcast sync completion: ${e.message}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Incremental update failed")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Update error: ${e.message}", e)
                }
                
                // Tunggu sesuai interval dinamis + jitter kecil (±10%) untuk hemat bandwidth dan menghindari burst
                val jitterFactor = 0.9 + rnd.nextDouble() * 0.2 // 0.9..1.1
                val nextDelay = (incrementalIntervalMs * jitterFactor).toLong().coerceAtLeast(15_000L)
                delay(nextDelay)
            }
        }
        
        Log.d(TAG, "🔄 Incremental update started (every ${incrementalIntervalMs}ms)")
    }
    
    /**
     * **MANUAL SYNC**: Force sync manual (untuk testing atau emergency)
     */
    suspend fun forceFullSync(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Force full sync requested...")
            
            val success = performDailyFullSyncWithRetry()
            
            if (success) {
                Log.d(TAG, "✅ Force full sync completed successfully")
            } else {
                Log.e(TAG, "❌ Force full sync failed")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in force full sync: ${e.message}", e)
            false
        }
    }
    
    /**
     * **QUICK UPDATE**: Force incremental update (untuk data baru)
     */
    suspend fun forceQuickUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "⚡ Force quick update requested...")
            
            val success = enhancedRepository.forceIncrementalUpdate()
            
            if (success) {
                Log.d(TAG, "✅ Force quick update completed successfully")
            } else {
                Log.d(TAG, "📱 Force quick update completed - no changes")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in force quick update: ${e.message}", e)
            false
        }
    }
    
    /**
     * **FULL REFRESH**: Force full sync tanpa local-first strategy (untuk debugging/testing)
     */
    suspend fun forceFullRefresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Force full refresh requested...")
            
            val success = enhancedRepository.forceFullRefresh()
            
            if (success) {
                Log.d(TAG, "✅ Force full refresh completed successfully")
            } else {
                Log.e(TAG, "❌ Force full refresh failed")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in force full refresh: ${e.message}", e)
            false
        }
    }
    
    /**
     * **CLEANUP**: Stop semua sync operations
     */
    fun stopSync() {
        try {
            incrementalUpdateJob?.cancel()
            incrementalUpdateJob = null
            Log.d(TAG, "🛑 Daily sync service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping sync service: ${e.message}", e)
        }
    }
    
    /**
     * **STATUS**: Get sync status information
     */
    suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        try {
            val hasDailySync = enhancedRepository.hasDailySyncToday()
            val needsIncremental = enhancedRepository.needsIncrementalUpdate()
            
            SyncStatus(
                hasDailySync = hasDailySync,
                needsIncrementalUpdate = needsIncremental,
                isIncrementalRunning = incrementalUpdateJob?.isActive == true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting sync status: ${e.message}", e)
            SyncStatus(
                hasDailySync = false,
                needsIncrementalUpdate = true,
                isIncrementalRunning = false
            )
        }
    }
    
    /**
     * Data class untuk sync status
     */
    data class SyncStatus(
        val hasDailySync: Boolean,
        val needsIncrementalUpdate: Boolean,
        val isIncrementalRunning: Boolean
    )
}
