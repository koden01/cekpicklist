package com.example.cekpicklist.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cekpicklist.repository.EnhancedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background Sync Worker untuk melakukan sync data secara berkala
 * - Sync data yang dirty (belum di-sync)
 * - Update cache metadata
 * - Handle conflict resolution
 */
class BackgroundSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "BackgroundSyncWorker"
        
        // Work constraints
        const val WORK_NAME = "background_sync_work"
        const val WORK_TAG = "background_sync"
        
        // Input data keys
        const val KEY_PICKLIST_NO = "picklist_no"
        const val KEY_SYNC_TYPE = "sync_type" // "full", "incremental", "dirty_only"
        const val KEY_FORCE_SYNC = "force_sync"
    }
    
    private val enhancedRepository = EnhancedRepository(applicationContext)
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Starting background sync work...")
            
            val picklistNo = inputData.getString(KEY_PICKLIST_NO)
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: "dirty_only"
            val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)
            
            Log.d(TAG, "📱 Sync parameters: picklistNo=$picklistNo, syncType=$syncType, forceSync=$forceSync")
            
            when (syncType) {
                "full" -> {
                    if (picklistNo != null) {
                        performFullSync(picklistNo)
                    } else {
                        Log.w(TAG, "⚠️ Full sync requested but no picklist number provided")
                        Result.failure()
                    }
                }
                "incremental" -> {
                    if (picklistNo != null) {
                        performIncrementalSync(picklistNo)
                    } else {
                        Log.w(TAG, "⚠️ Incremental sync requested but no picklist number provided")
                        Result.failure()
                    }
                }
                "dirty_only" -> {
                    performDirtyDataSync()
                }
                else -> {
                    Log.w(TAG, "⚠️ Unknown sync type: $syncType")
                    Result.failure()
                }
            }
            
            Log.d(TAG, "✅ Background sync work completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background sync work failed: ${e.message}", e)
            Result.retry()
        }
    }
    
    /**
     * Perform full sync untuk picklist tertentu
     */
    private suspend fun performFullSync(picklistNo: String) {
        try {
            Log.d(TAG, "🔄 Performing full sync for picklist: $picklistNo")
            
            // Sync picklist items
            val items = enhancedRepository.getPicklistItems(picklistNo)
            Log.d(TAG, "✅ Synced ${items.size} picklist items for: $picklistNo")
            
            // Sync scan data
            val epcs = enhancedRepository.getProcessedEpcList(picklistNo)
            Log.d(TAG, "✅ Synced ${epcs.size} EPCs for: $picklistNo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Full sync failed for $picklistNo: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Perform incremental sync untuk picklist tertentu
     */
    private suspend fun performIncrementalSync(picklistNo: String) {
        try {
            Log.d(TAG, "🔄 Performing incremental sync for picklist: $picklistNo")
            
            // Check if data needs refresh
            val needsRefresh = enhancedRepository.needsRefresh(picklistNo, true)
            if (needsRefresh) {
                val items = enhancedRepository.getPicklistItems(picklistNo)
                Log.d(TAG, "✅ Incremental sync completed: ${items.size} items for: $picklistNo")
            } else {
                Log.d(TAG, "📱 No refresh needed for picklist: $picklistNo")
            }
            
            // Check scan data
            val needsScanRefresh = enhancedRepository.needsRefresh(picklistNo, false)
            if (needsScanRefresh) {
                val epcs = enhancedRepository.getProcessedEpcList(picklistNo)
                Log.d(TAG, "✅ Incremental scan sync completed: ${epcs.size} EPCs for: $picklistNo")
            } else {
                Log.d(TAG, "📱 No scan refresh needed for picklist: $picklistNo")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Incremental sync failed for $picklistNo: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Perform sync untuk semua data yang dirty
     */
    private suspend fun performDirtyDataSync() {
        try {
            Log.d(TAG, "🔄 Performing dirty data sync...")
            
            // Check if there's any dirty data
            val hasDirtyData = enhancedRepository.hasDirtyData()
            if (!hasDirtyData) {
                Log.d(TAG, "📱 No dirty data to sync")
                return
            }
            
            // Sync all dirty data
            enhancedRepository.syncDirtyData()
            
            // Get cache stats
            val stats = enhancedRepository.getCacheStats()
            val dirtyCount = (stats["dirtyItems"] as? Int) ?: 0
            Log.d(TAG, "✅ Dirty data sync completed: ${dirtyCount} items synced")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Dirty data sync failed: ${e.message}", e)
            throw e
        }
    }
}
