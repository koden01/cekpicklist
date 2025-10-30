package com.example.cekpicklist.repository

import android.content.Context
import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.BatchSupabaseService
import com.example.cekpicklist.api.BarcodeSupabaseService
// PicklistCompletionStatus removed with Room; methods now return simple lists or are no-op
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay

/**
 * Enhanced Repository dengan Local-First Strategy
 * - Prioritas: Local Database → Supabase → Fallback
 * - Smart caching dengan timestamp tracking
 * - Offline capability
 * - Background sync
 */
class EnhancedRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedRepository"
        private const val SYNC_RETRY_DELAY_MS = 2000L
        private const val MAX_SYNC_RETRIES = 3
    }
    
    // Services
    private val supabaseService = SupabaseService()
    private val nirwanaApiService = NirwanaApiService()
    private val batchSupabaseService = BatchSupabaseService()
    private val barcodeSupabaseService = BarcodeSupabaseService()
    
    // Local data management
    // Supabase-only mode: no local repositories or cache manager
    
    // Coroutine scope untuk background operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track active background sync jobs
    private val activeBackgroundSyncs = mutableSetOf<String>()
    
    /**
     * **DAILY FULL SYNC**: Perform full sync semua data hari ini
     * Hanya dilakukan sekali per hari saat pertama kali buka app
     */
    suspend fun performDailyFullSync(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 [Supabase-only] Daily sync start")
            val picklists = supabaseService.getPicklists()
            Log.d(TAG, "✅ [Supabase-only] Picklists fetched: ${picklists.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Daily full sync failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * **DAILY SYNC STRATEGY**: Check apakah sudah ada daily sync hari ini
     */
    suspend fun hasDailySyncToday(): Boolean = withContext(Dispatchers.IO) {
        true
    }
    
    /**
     * **INCREMENTAL UPDATE**: Check apakah perlu incremental update
     */
    suspend fun needsIncrementalUpdate(): Boolean = withContext(Dispatchers.IO) {
        false
    }
    
    /**
     * **INCREMENTAL UPDATE**: Perform incremental update dengan safety checks
     * **SAFE UPDATE**: Multiple safety checks untuk mencegah overload
     */
    suspend fun performIncrementalUpdate(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 [Supabase-only] Skipping incremental update")
        true
    }
    
    /**
     * **MANUAL TRIGGER**: Force incremental update (untuk data baru)
     */
    suspend fun forceIncrementalUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Force incremental update triggered...")
            
            // Skip semua safety checks untuk manual trigger
            val remotePicklists = withTimeout(10000) {
                supabaseService.getPicklists()
            }
            
            val localPicklists = emptyList<String>()
            
            // Compare dan update
            val hasChanges = compareAndUpdatePicklists(remotePicklists, localPicklists)
            
            if (hasChanges) {
                Log.d(TAG, "✅ Force update completed with changes")
            } else {
                Log.d(TAG, "📱 Force update completed - no changes")
            }
            
            // No completion mark in Supabase-only mode
            
            hasChanges
        } catch (e: Exception) {
            Log.e(TAG, "❌ Force incremental update failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * **FORCE REFRESH**: Force full sync tanpa local-first strategy (untuk testing/debugging)
     */
    suspend fun forceFullRefresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 [Supabase-only] Force full refresh: fetching picklists and items...")
            val picklists = supabaseService.getPicklists()
            picklists.forEach { picklistNo ->
                try { supabaseService.getPicklistItems(picklistNo) } catch (_: Exception) {}
            }
            Log.d(TAG, "✅ [Supabase-only] Force full refresh done: ${picklists.size} picklists")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Force full refresh failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * **SMART UPDATE**: Compare dan update picklists jika ada perubahan
     */
    private suspend fun compareAndUpdatePicklists(remotePicklists: List<String>, localPicklists: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            // Cek apakah ada picklist baru
            val newPicklists = remotePicklists.filter { it !in localPicklists }
            val removedPicklists = localPicklists.filter { it !in remotePicklists }
            
            if (newPicklists.isNotEmpty() || removedPicklists.isNotEmpty()) {
                Log.d(TAG, "🔄 Changes detected: +${newPicklists.size} new, -${removedPicklists.size} removed")
                
                // Update local database dengan data terbaru
                for (picklistNo in newPicklists) {
                    try {
                        // Supabase-only: no local persistence or background merge
                        supabaseService.getPicklistItems(picklistNo)
                        supabaseService.getPicklistScans(picklistNo)
                        
                        Log.d(TAG, "✅ Added new picklist: $picklistNo (items, scans)")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to sync new picklist $picklistNo: ${e.message}")
                    }
                }
                
                // Remove deleted picklists
                for (picklistNo in removedPicklists) {
                    try {
                        // Supabase-only: no local clear needed
                        Log.d(TAG, "🗑️ Removed picklist: $picklistNo")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to remove picklist $picklistNo: ${e.message}")
                    }
                }
                
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error comparing picklists: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get list picklist numbers dengan Local-First strategy
     * 1. Cek local database dulu (fast path)
     * 2. Jika tidak ada atau expired, sync dari Supabase
     * 3. Fallback ke local data jika network error
     */
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Supabase-only] Getting picklist numbers list")
            val remotePicklists = supabaseService.getPicklists()
            Log.d(TAG, "✅ Retrieved ${remotePicklists.size} picklists from Supabase")
            remotePicklists
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Propagate cancellation without logging as error
            Log.d(TAG, "⏹️ getPicklists cancelled: ${ce.message}")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklist numbers from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get picklist items dengan Local-First strategy
     * 1. Cek local database dulu
     * 2. Jika perlu refresh, sync dari Supabase
     * 3. Fallback ke local data jika network error
     */
    suspend fun getPicklistItemsWithLocalFirst(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Supabase-only] Getting picklist items for: $picklistNo")
            val remoteItems = supabaseService.getPicklistItems(picklistNo)
            Log.d(TAG, "✅ Retrieved ${remoteItems.size} items from Supabase for: $picklistNo")
            remoteItems
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklist items from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get picklist items dengan Flow (reactive updates)
     */
    fun getPicklistItemsFlow(picklistNo: String): Nothing = throw UnsupportedOperationException("Flow not supported in Supabase-only mode")
    
    /**
     * Get processed EPC list dengan Local-First strategy
     */
    suspend fun getProcessedEpcList(picklistNo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Supabase-only] Getting processed EPC list for: $picklistNo")
            val remoteEpcs = supabaseService.getProcessedEpcList(picklistNo)
            Log.d(TAG, "✅ Retrieved ${remoteEpcs.size} EPCs from Supabase for: $picklistNo")
            remoteEpcs
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting processed EPC list from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Save scan record dengan Local-First strategy
     */
    suspend fun saveScanRecord(scan: PicklistScan) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 [Supabase-only] Saving scan record: ${scan.epc}")
            supabaseService.savePicklistScan(
                picklistNo = scan.noPicklist,
                articleId = scan.articleId,
                epc = scan.epc,
                productId = scan.productId,
                articleName = scan.articleName,
                size = scan.size,
                notrans = scan.notrans
            )
            Log.d(TAG, "✅ Saved scan record to Supabase: ${scan.epc}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving scan record to Supabase: ${e.message}", e)
        }
    }
    
    /**
     * Update scan status dengan Local-First strategy
     */
    suspend fun updateScanStatus(id: String, scan: Boolean) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 [Supabase-only] updateScanStatus not implemented for id=$id scan=$scan")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating scan status: ${e.message}", e)
        }
    }
    
    /**
     * Clear scan data dengan Local-First strategy
     */
    suspend fun clearScanData(picklistNo: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🧹 [Supabase-only] Clearing scan data for: $picklistNo")
            supabaseService.deleteAllScanRecordsForPicklist(picklistNo)
            Log.d(TAG, "✅ Cleared scan data from Supabase for: $picklistNo")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing scan data in Supabase: ${e.message}", e)
        }
    }
    
    /**
     * Background sync untuk picklist numbers list
     * **LOCAL-FIRST**: Hanya sync jika local data kosong
     */
    private suspend fun performPicklistsBackgroundSync() {
        try {
            Log.d(TAG, "🔄 [Supabase-only] Skipping background picklists sync")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background sync failed for picklist numbers: ${e.message}", e)
        }
    }
    
    /**
     * Cek apakah background sync sedang aktif untuk picklist tertentu
     */
    private fun isBackgroundSyncActive(picklistNo: String): Boolean {
        return activeBackgroundSyncs.contains(picklistNo)
    }
    
    /**
     * Background sync untuk update incremental
     */
    private suspend fun performBackgroundSync(picklistNo: String, isPicklist: Boolean) {
        try {
            activeBackgroundSyncs.add(picklistNo)
            Log.d(TAG, "🔄 [Supabase-only] Skipping background sync for: $picklistNo (isPicklist: $isPicklist)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background sync failed for $picklistNo: ${e.message}", e)
        } finally {
            activeBackgroundSyncs.remove(picklistNo)
        }
    }
    
    /**
     * Get picklist items dari Supabase
     */
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📥 Getting picklist items from Supabase for: $picklistNo")
            val items = supabaseService.getPicklistItems(picklistNo)
            Log.d(TAG, "✅ Retrieved ${items.size} picklist items from Supabase")
            items
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklist items: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Save picklist items ke local database
     */
    suspend fun savePicklistItems(items: List<PicklistItem>, picklistNo: String) { /* No-op in Supabase-only mode */ }
    
    /**
     * Get picklist scans dari Supabase
     */
    suspend fun getPicklistScans(picklistNo: String): List<PicklistScan> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📥 Getting picklist scans from Supabase for: $picklistNo")
            
            // Get scan data dari Supabase
            val scanJsonObjects = supabaseService.getPicklistScans(picklistNo)
            Log.d(TAG, "📦 Retrieved ${scanJsonObjects.size} scan records from Supabase")
            
            // Convert JSONObject to PicklistScan
            val scans = scanJsonObjects.mapNotNull { jsonObject ->
                try {
                    PicklistScan(
                        id = jsonObject.optString("id") ?: java.util.UUID.randomUUID().toString(),
                        epc = jsonObject.getString("epc"),
                        noPicklist = jsonObject.getString("no_picklist"),
                        productId = jsonObject.optString("product_id") ?: "",
                        articleId = jsonObject.optString("article_id") ?: "",
                        articleName = jsonObject.optString("article_name") ?: "",
                        size = jsonObject.optString("size") ?: "",
                        notrans = jsonObject.optString("notrans"),
                        createdAt = try {
                            jsonObject.optString("created_at")?.let { 
                                java.time.Instant.parse(it).toEpochMilli()
                            } ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error converting scan JSON: ${e.message}", e)
                    null
                }
            }
            
            Log.d(TAG, "✅ Converted ${scans.size} scan records from JSON")
            scans
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting picklist scans: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Save picklist scans ke local database
     */
    suspend fun savePicklistScans(scans: List<PicklistScan>, picklistNo: String) { /* No-op in Supabase-only mode */ }
    
    /**
     * Get semua picklist completion statuses menggunakan query Room langsung
     */
    suspend fun getAllPicklistCompletionStatuses(): List<Any> = withContext(Dispatchers.IO) { emptyList() }
    
    /**
     * Force sync semua data yang dirty
     */
    suspend fun syncDirtyData() = withContext(Dispatchers.IO) { /* No-op in Supabase-only mode */ }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats() = mapOf<String, Any>()
    
    /**
     * Clear semua data lokal
     */
    suspend fun clearAllLocalData() { /* No-op in Supabase-only mode */ }
    
    /**
     * Check apakah ada data yang dirty
     */
    suspend fun hasDirtyData() = false
    
    /**
     * Check apakah data perlu di-refresh
     */
    suspend fun needsRefresh(picklistNo: String, isPicklist: Boolean = true): Boolean {
        return false
    }
    
    // ==================== BARCODE OPERATIONS ====================
    
    /**
     * Get barcode resi records dengan Local-First strategy
     */
    suspend fun getBarcodeResiRecords(): List<BarcodeSupabaseService.BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Supabase-only] Getting barcode resi records")
            val remoteRecords = barcodeSupabaseService.getAllBarcodeResi()
            Log.d(TAG, "✅ Retrieved ${remoteRecords.size} barcode resi from Supabase")
            remoteRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode resi records from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get barcode expedisi records dengan Local-First strategy
     */
    suspend fun getBarcodeExpedisiRecords(): List<BarcodeSupabaseService.BarcodeSessionRecord> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Supabase-only] Getting barcode expedisi records")
            val remoteRecords = barcodeSupabaseService.getAllBarcodeExpedisi()
            Log.d(TAG, "✅ Retrieved ${remoteRecords.size} barcode expedisi from Supabase")
            remoteRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode expedisi records from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Save barcode resi record dengan Local-First strategy
     */
    suspend fun saveBarcodeResiRecord(record: BarcodeSupabaseService.BarcodeScanRecord) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 [Supabase-only] Saving barcode resi record: ${record.Resi}")
            barcodeSupabaseService.saveBarcodeScan(record)
            Log.d(TAG, "✅ Saved barcode resi record to Supabase: ${record.Resi}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving barcode resi record to Supabase: ${e.message}", e)
        }
    }
    
    /**
     * Update flag di tbl_expedisi berdasarkan resino
     */
    suspend fun updateExpedisiFlag(resino: String, flag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 [Supabase-only] Updating flag in tbl_expedisi: resino=$resino, flag=$flag")
            barcodeSupabaseService.updateExpedisiFlag(resino, flag)
            Log.d(TAG, "✅ Flag updated in Supabase: resino=$resino, flag=$flag")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating expedisi flag in Supabase: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save barcode expedisi record dengan Local-First strategy
     */
    suspend fun saveBarcodeExpedisiRecord(record: BarcodeSupabaseService.BarcodeSessionRecord) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Saving barcode expedisi record: ${record.id}")
            
            // Step 1: Save ke local database dulu (fast)
            barcodeSupabaseService.saveBarcodeSession(record)
            
            // Step 2: Sync ke Supabase (background)
            repositoryScope.launch {
                try {
                    barcodeSupabaseService.saveBarcodeSession(record)
                    // no-op mark clean in Supabase-only mode
                    Log.d(TAG, "✅ Synced barcode expedisi record to Supabase: ${record.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to sync barcode expedisi record to Supabase: ${e.message}", e)
                    // no-op mark dirty in Supabase-only mode
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving barcode expedisi record: ${e.message}", e)
        }
    }
    
    /**
     * Background sync untuk barcode data
     */
    private suspend fun performBarcodeBackgroundSync() {
        try {
            Log.d(TAG, "🔄 Performing background sync for barcode data...")
            // Supabase-only: tidak melakukan sync ke Room
            Log.d(TAG, "✅ Background sync skipped (Supabase-only mode)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Background sync failed for barcode data: ${e.message}", e)
        }
    }
    
    /**
     * Get barcode resi records dengan Flow (reactive updates)
     */
    fun getBarcodeResiRecordsFlow(): Nothing = throw UnsupportedOperationException("Flow not supported in Supabase-only mode")
    
    /**
     * Get barcode expedisi records dengan Flow (reactive updates)
     */
    fun getBarcodeExpedisiRecordsFlow(): Nothing = throw UnsupportedOperationException("Flow not supported in Supabase-only mode")
    
    /**
     * Force clear cache dan sync dari Supabase
     */
    suspend fun forceRefreshBarcodeData(): List<BarcodeSupabaseService.BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 [Supabase-only] Force refreshing barcode data from Supabase...")
            val remoteRecords = barcodeSupabaseService.getAllBarcodeResi()
            Log.d(TAG, "✅ Force refresh completed: ${remoteRecords.size} resi records")
            remoteRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error force refreshing barcode data from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Force refresh expedisi data dari Supabase
     */
    suspend fun forceRefreshExpedisiData(): List<BarcodeSupabaseService.BarcodeSessionRecord> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 [Supabase-only] Force refreshing expedisi data from Supabase...")
            val remoteRecords = barcodeSupabaseService.getAllBarcodeExpedisi()
            Log.d(TAG, "✅ Force refresh expedisi completed: ${remoteRecords.size} records")
            remoteRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error force refreshing expedisi data from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Test join query untuk debug
     */
    suspend fun testJoinQuery(): String {
        return try {
            Log.d(TAG, "🔍 Testing join query...")
            val response = barcodeSupabaseService.testJoinQuery()
            Log.d(TAG, "🔍 Join query response: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing join query: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    
    /**
     * Get unique courier names dengan Local-First strategy
     */
    suspend fun getUniqueCourierNames(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Supabase-only] Getting unique courier names")
            val supabaseCouriers = barcodeSupabaseService.getUniqueCourierNames()
            Log.d(TAG, "✅ Retrieved ${supabaseCouriers.size} courier names from Supabase")
            supabaseCouriers
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting unique courier names from Supabase: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Clear semua data barcode lokal
     */
    suspend fun clearAllBarcodeData() { /* No-op in Supabase-only mode */ }
}
