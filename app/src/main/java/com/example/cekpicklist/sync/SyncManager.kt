package com.example.cekpicklist.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Sync Manager untuk mengelola background sync operations
 * - Schedule periodic sync
 * - Handle immediate sync requests
 * - Manage sync constraints
 */
class SyncManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SyncManager"
        
        // Sync intervals
        private const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L // 15 menit
        private const val IMMEDIATE_SYNC_DELAY_SECONDS = 5L // 5 detik
        
        // Work constraints
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BACKOFF_DELAY_MINUTES = 15L
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule periodic background sync
     */
    suspend fun schedulePeriodicSync() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📅 Scheduling periodic background sync...")
            
            // Cancel existing periodic work
            workManager.cancelUniqueWork("periodic_sync")
            
            // Create periodic work request
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val periodicWorkRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .addTag("periodic_sync")
                .setInputData(
                    Data.Builder()
                        .putString(BackgroundSyncWorker.KEY_SYNC_TYPE, "dirty_only")
                        .build()
                )
                .build()
            
            // Enqueue periodic work
            workManager.enqueueUniquePeriodicWork(
                "periodic_sync",
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWorkRequest
            )
            
            Log.d(TAG, "✅ Periodic sync scheduled every $PERIODIC_SYNC_INTERVAL_MINUTES minutes")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling periodic sync: ${e.message}", e)
        }
    }
    
    /**
     * Schedule immediate sync untuk picklist tertentu
     */
    suspend fun scheduleImmediateSync(picklistNo: String, syncType: String = "incremental") = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "⚡ Scheduling immediate sync for picklist: $picklistNo (type: $syncType)")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val immediateWorkRequest = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(IMMEDIATE_SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .addTag("immediate_sync_$picklistNo")
                .setInputData(
                    Data.Builder()
                        .putString(BackgroundSyncWorker.KEY_PICKLIST_NO, picklistNo)
                        .putString(BackgroundSyncWorker.KEY_SYNC_TYPE, syncType)
                        .build()
                )
                .build()
            
            // Enqueue immediate work
            workManager.enqueueUniqueWork(
                "immediate_sync_$picklistNo",
                ExistingWorkPolicy.REPLACE,
                immediateWorkRequest
            )
            
            Log.d(TAG, "✅ Immediate sync scheduled for picklist: $picklistNo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling immediate sync: ${e.message}", e)
        }
    }
    
    /**
     * Schedule full sync untuk picklist tertentu
     */
    suspend fun scheduleFullSync(picklistNo: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Scheduling full sync for picklist: $picklistNo")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val fullSyncWorkRequest = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .addTag("full_sync_$picklistNo")
                .setInputData(
                    Data.Builder()
                        .putString(BackgroundSyncWorker.KEY_PICKLIST_NO, picklistNo)
                        .putString(BackgroundSyncWorker.KEY_SYNC_TYPE, "full")
                        .putBoolean(BackgroundSyncWorker.KEY_FORCE_SYNC, true)
                        .build()
                )
                .build()
            
            // Enqueue full sync work
            workManager.enqueueUniqueWork(
                "full_sync_$picklistNo",
                ExistingWorkPolicy.REPLACE,
                fullSyncWorkRequest
            )
            
            Log.d(TAG, "✅ Full sync scheduled for picklist: $picklistNo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling full sync: ${e.message}", e)
        }
    }
    
    /**
     * Cancel sync untuk picklist tertentu
     */
    suspend fun cancelSync(picklistNo: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "❌ Cancelling sync for picklist: $picklistNo")
            
            workManager.cancelUniqueWork("immediate_sync_$picklistNo")
            workManager.cancelUniqueWork("full_sync_$picklistNo")
            
            Log.d(TAG, "✅ Sync cancelled for picklist: $picklistNo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling sync: ${e.message}", e)
        }
    }
    
    /**
     * Cancel all sync operations
     */
    suspend fun cancelAllSync() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "❌ Cancelling all sync operations...")
            
            workManager.cancelAllWorkByTag("periodic_sync")
            workManager.cancelAllWorkByTag("immediate_sync")
            workManager.cancelAllWorkByTag("full_sync")
            
            Log.d(TAG, "✅ All sync operations cancelled")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling all sync: ${e.message}", e)
        }
    }
    
    /**
     * Get sync status untuk picklist tertentu
     */
    suspend fun getSyncStatus(picklistNo: String): SyncStatus = withContext(Dispatchers.IO) {
        try {
            val immediateWorkInfo = workManager.getWorkInfosForUniqueWork("immediate_sync_$picklistNo").get()
            val fullSyncWorkInfo = workManager.getWorkInfosForUniqueWork("full_sync_$picklistNo").get()
            
            val hasImmediateSync = immediateWorkInfo.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            val hasFullSync = fullSyncWorkInfo.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            
            SyncStatus(
                picklistNo = picklistNo,
                hasImmediateSync = hasImmediateSync,
                hasFullSync = hasFullSync,
                isSyncing = hasImmediateSync || hasFullSync
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting sync status: ${e.message}", e)
            SyncStatus(picklistNo, false, false, false)
        }
    }
    
    /**
     * Get all sync status
     */
    suspend fun getAllSyncStatus(): List<SyncStatus> = withContext(Dispatchers.IO) {
        try {
            val allWorkInfos = workManager.getWorkInfosByTag("immediate_sync").get() +
                    workManager.getWorkInfosByTag("full_sync").get()
            
            val picklistNumbers = allWorkInfos.mapNotNull { workInfo ->
                workInfo.tags.find { it.startsWith("immediate_sync_") || it.startsWith("full_sync_") }
                    ?.substringAfter("_")
            }.distinct()
            
            picklistNumbers.map { picklistNo ->
                getSyncStatus(picklistNo)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting all sync status: ${e.message}", e)
            emptyList()
        }
    }
}

/**
 * Data class untuk sync status
 */
data class SyncStatus(
    val picklistNo: String,
    val hasImmediateSync: Boolean,
    val hasFullSync: Boolean,
    val isSyncing: Boolean
)
