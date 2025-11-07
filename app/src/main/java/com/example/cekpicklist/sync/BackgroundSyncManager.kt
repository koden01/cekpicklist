package com.example.cekpicklist.sync

import android.content.Context
import android.util.Log
import com.example.cekpicklist.cache.PendingOperationsManager
import com.example.cekpicklist.data.PendingOperation
import com.example.cekpicklist.repository.EnhancedRepository
import kotlinx.coroutines.*

/**
 * Background Sync Manager
 * Menangani sync pending operations dan deteksi penghapusan data
 */
class BackgroundSyncManager(
    private val context: Context,
    private val pendingOperationsManager: PendingOperationsManager,
    private val enhancedRepository: EnhancedRepository? = null
) {
    
    companion object {
        private const val TAG = "BackgroundSyncManager"
        private const val SYNC_INTERVAL_MS = 60000L // 60 detik (diubah dari 30 detik untuk menghemat bandwidth)
        private const val MAX_RETRIES = 3
    }
    
    private var syncJob: Job? = null
    private var isRunning = false
    
    /**
     * Start background sync
     */
    fun startBackgroundSync() {
        if (isRunning) {
            Log.d(TAG, "⚠️ Background sync already running")
            return
        }
        
        isRunning = true
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    Log.d(TAG, "🔄 Starting background sync...")
                    performSync()
                    delay(SYNC_INTERVAL_MS)
                } catch (e: CancellationException) {
                    // CancellationException adalah normal saat coroutine dibatalkan
                    Log.d(TAG, "🛑 Background sync cancelled (normal behavior)")
                    throw e // Re-throw untuk menghentikan coroutine dengan benar
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in background sync: ${e.message}", e)
                    delay(SYNC_INTERVAL_MS)
                }
            }
        }
        
        Log.d(TAG, "✅ Background sync started")
    }
    
    /**
     * Stop background sync
     */
    fun stopBackgroundSync() {
        isRunning = false
        syncJob?.cancel()
        syncJob = null
        Log.d(TAG, "🛑 Background sync stopped")
    }
    
    /**
     * Perform sync operation
     */
    private suspend fun performSync() {
        try {
            // 1. Sync pending operations
            val pendingOperations = pendingOperationsManager.getPendingOperations()
            
            if (pendingOperations.isNotEmpty()) {
                Log.d(TAG, "📤 Syncing ${pendingOperations.size} pending operations...")
                
                for (operation in pendingOperations) {
                    try {
                        val success = processOperation(operation)
                        
                        if (success) {
                            pendingOperationsManager.removePendingOperation(operation.id)
                            Log.d(TAG, "✅ Successfully synced operation: ${operation.id}")
                        } else {
                            // Increment retry count
                            val updatedOperation = operation.copy(
                                retries = operation.retries + 1,
                                lastAttempt = System.currentTimeMillis()
                            )
                            
                            if (updatedOperation.retries >= MAX_RETRIES) {
                                Log.w(TAG, "⚠️ Max retries reached for operation: ${operation.id}")
                                pendingOperationsManager.removePendingOperation(operation.id)
                            } else {
                                pendingOperationsManager.updatePendingOperation(updatedOperation)
                            }
                        }
                    } catch (e: CancellationException) {
                        // CancellationException adalah normal saat coroutine dibatalkan
                        throw e // Re-throw untuk menghentikan coroutine dengan benar
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing operation ${operation.id}: ${e.message}", e)
                    }
                }
            } else {
                Log.d(TAG, "📭 No pending operations to sync")
            }
            
            // 2. Deteksi penghapusan data di Supabase (jika enhancedRepository tersedia)
            if (enhancedRepository != null) {
                try {
                    // Deteksi penghapusan untuk tbl_resi
                    enhancedRepository.detectAndRemoveDeletedResi()
                } catch (e: CancellationException) {
                    throw e // Re-throw untuk menghentikan coroutine dengan benar
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error detecting deleted resi: ${e.message}", e)
                }
                
                try {
                    // Deteksi penghapusan untuk tbl_expedisi
                    enhancedRepository.detectAndRemoveDeletedExpedisi()
                } catch (e: CancellationException) {
                    throw e // Re-throw untuk menghentikan coroutine dengan benar
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error detecting deleted expedisi: ${e.message}", e)
                }
                
                // 3. Cleanup data lama (> 7 hari) - Cron job style (setiap 24 jam)
                try {
                    if (com.example.cekpicklist.cache.BarcodeCacheManager.shouldCleanup()) {
                        com.example.cekpicklist.cache.BarcodeCacheManager.cleanupOldData()
                    }
                } catch (e: CancellationException) {
                    throw e // Re-throw untuk menghentikan coroutine dengan benar
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error cleaning up old data: ${e.message}", e)
                }
            }
            
        } catch (e: CancellationException) {
            // CancellationException adalah normal saat coroutine dibatalkan
            throw e // Re-throw untuk menghentikan coroutine dengan benar
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in performSync: ${e.message}", e)
        }
    }
    
    /**
     * Process individual operation
     */
    private suspend fun processOperation(operation: PendingOperation): Boolean {
        return try {
            // MOCK implementation - dependency tidak tersedia
            Log.d(TAG, "🔄 Processing operation: ${operation.type} - ${operation.payload.barcode}")
            
            // Simulate processing delay
            delay(1000)
            
            // Mock success rate (80%)
            val success = (1..10).random() <= 8
            Log.d(TAG, "📊 Operation result: ${if (success) "SUCCESS" else "FAILED"}")
            
            success
        } catch (e: CancellationException) {
            // CancellationException adalah normal saat coroutine dibatalkan
            throw e // Re-throw untuk menghentikan coroutine dengan benar
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing operation: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get sync status
     */
    fun getSyncStatus(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning,
            "pendingCount" to pendingOperationsManager.getPendingOperationsCount(),
            "lastSync" to System.currentTimeMillis()
        )
    }
    
    /**
     * Force sync now
     */
    fun forceSyncNow() {
        CoroutineScope(Dispatchers.IO).launch {
            performSync()
        }
    }
}