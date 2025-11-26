package com.example.cekpicklist.repository

import android.content.Context
import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.BatchSupabaseService
import com.example.cekpicklist.api.BarcodeSupabaseService
import com.example.cekpicklist.api.NetworkException
import com.example.cekpicklist.cache.BarcodeCacheManager
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
import kotlinx.coroutines.CancellationException

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
     * Force refresh barcode master data (tbl_resi & tbl_expedisi) dan simpan ke Room melalui cache manager.
     */
    suspend fun forceBarcodeRoomRefresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Force refreshing barcode master data (resi + expedisi)...")
            val resiRecords = barcodeSupabaseService.getAllBarcodeResi()
            val expedisiRecords = barcodeSupabaseService.getAllBarcodeExpedisi()
            BarcodeCacheManager.initializeCache(resiRecords, expedisiRecords)
            Log.d(TAG, "✅ Barcode master data refreshed: ${resiRecords.size} resi, ${expedisiRecords.size} expedisi")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing barcode master data: ${e.message}", e)
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
     * Get barcode resi records dengan Smart Sync Strategy:
     * 1. **OFFLINE-FIRST**: Selalu gunakan cache dulu (instant)
     * 2. **INCREMENTAL SYNC**: Background sync hanya data yang berubah (bandwidth efficient)
     * 3. **MULTI-DEVICE**: Selalu up-to-date dengan Supabase
     * 
     * **PERBAIKAN**: Menggunakan incremental sync untuk menghemat bandwidth
     */
    suspend fun getBarcodeResiRecords(): List<BarcodeSupabaseService.BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            // **ROOM-ONLY**: Hanya return data dari Room (tidak ada auto-sync dari Supabase)
            val cachedRecords = BarcodeCacheManager.getAllResiRecords()
            Log.d(TAG, "📦 [ROOM-ONLY] Returning resi records from Room: ${cachedRecords.size}")
            
            // Return cache dari Room (tidak ada background sync)
            return@withContext cachedRecords
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode resi records: ${e.message}", e)
            // Final fallback: gunakan cache yang ada
            val cachedRecords = BarcodeCacheManager.getAllResiRecords()
            if (cachedRecords.isNotEmpty()) {
                Log.d(TAG, "⚠️ [FALLBACK] Using cached data: ${cachedRecords.size} records")
                return@withContext cachedRecords
            }
            emptyList()
        }
    }
    
    /**
     * **INCREMENTAL SYNC DENGAN BATCH VERIFICATION**: Sync data dan hapus yang dihapus sekaligus
     * - Incremental sync: Hanya data baru/modified (hemat bandwidth)
     * - Batch verification: Verify resi yang "missing" untuk delete detection (akurat & hemat)
     * - Cache selalu up-to-date dengan bandwidth minimal
     */
    internal suspend fun performDailyResiSync() = withContext(Dispatchers.IO) {
        try {
            // 1. Cleanup data > 7 hari dari cache
            BarcodeCacheManager.cleanupOldData()
            
            val now = System.currentTimeMillis()
            val lastUpdatedAt = BarcodeCacheManager.getLastResiUpdatedAt()
            
            val supabaseResi = if (lastUpdatedAt.isNullOrBlank()) {
                Log.d(TAG, "🔄 [RESI SYNC] Cache uninitialized, fetching 7-day window from Supabase")
                barcodeSupabaseService.getAllBarcodeResi()
            } else {
                Log.d(TAG, "🔄 [RESI SYNC] Fetching changes since updated_at=$lastUpdatedAt")
                barcodeSupabaseService.getBarcodeResiSince(lastUpdatedAt)
            }
            
            if (lastUpdatedAt.isNullOrBlank()) {
                if (supabaseResi.isNotEmpty()) {
                    Log.d(TAG, "📥 [RESI SYNC] Retrieved ${supabaseResi.size} records for initial cache fill")
                    BarcodeCacheManager.initializeCache(supabaseResi, emptyList())
                } else {
                    Log.d(TAG, "ℹ️ [RESI SYNC] No data returned for initial window")
                }
            } else {
                if (supabaseResi.isNotEmpty()) {
                    Log.d(TAG, "📥 [RESI SYNC] Retrieved ${supabaseResi.size} delta records")
                }
                supabaseResi.forEach { record ->
                    if (record.deleted_at != null) {
                        BarcodeCacheManager.removeResiRecord(record.Resi)
                    } else {
                        BarcodeCacheManager.addResiRecord(record)
                    }
                }
            }
            
            // Update sync timestamp (digunakan hanya untuk scheduling cron)
            BarcodeCacheManager.setLastResiSyncTime(now)
            
            val finalCacheSize = BarcodeCacheManager.getAllResiRecords().size
            Log.d(TAG, "✅ [RESI SYNC] Cache sync completed, total cached: $finalCacheSize records")
            
        } catch (e: NetworkException) {
            Log.w(TAG, "⚠️ [RESI SYNC] Network error (connection failed): ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ [RESI SYNC] Sync failed (non-critical): ${e.message}")
        }
    }
    
    /**
     * Get barcode expedisi records dengan Smart Sync Strategy:
     * 1. **OFFLINE-FIRST**: Selalu gunakan cache dulu (instant)
     * 2. **INCREMENTAL SYNC**: Background sync hanya data yang berubah (bandwidth efficient)
     * 3. **MULTI-DEVICE**: Selalu up-to-date dengan Supabase
     * 
     * **PERBAIKAN**: Menggunakan incremental sync untuk menghemat bandwidth
     */
    suspend fun getBarcodeExpedisiRecords(): List<BarcodeSupabaseService.BarcodeSessionRecord> = withContext(Dispatchers.IO) {
        try {
            // **ROOM-ONLY**: Hanya return data dari Room (tidak ada auto-sync dari Supabase)
            val cachedRecords = BarcodeCacheManager.getAllExpedisiRecords()
            Log.d(TAG, "📦 [ROOM-ONLY] Returning expedisi records from Room: ${cachedRecords.size}")
                
            // Return cache dari Room (tidak ada background sync)
            return@withContext cachedRecords
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode expedisi records: ${e.message}", e)
            // Final fallback: gunakan cache yang ada
            val cachedRecords = BarcodeCacheManager.getAllExpedisiRecords()
            if (cachedRecords.isNotEmpty()) {
                Log.d(TAG, "⚠️ [FALLBACK] Using cached data: ${cachedRecords.size} records")
                return@withContext cachedRecords
            }
            emptyList()
        }
    }
    
    /**
     * **INCREMENTAL SYNC**: Hanya sync data yang berubah (bandwidth efficient)
     * Pengambilan semua flag="NO" HANYA jika cache kosong (di initial load)
     * Setelah cache ada, hanya sync incremental (data yang berubah)
     */
    internal suspend fun performDailyExpedisiSync() = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val lastUpdateAt = BarcodeCacheManager.getLastExpedisiUpdateAt()
            
            val records = if (lastUpdateAt.isNullOrBlank()) {
                Log.d(TAG, "🔄 [EXPEDISI SYNC] Cache uninitialized, fetching all flag=NO records...")
                barcodeSupabaseService.getAllBarcodeExpedisi()
            } else {
                Log.d(TAG, "🔄 [EXPEDISI SYNC] Fetching changes since update_at=$lastUpdateAt")
                barcodeSupabaseService.getBarcodeExpedisiSince(lastUpdateAt)
            }

            if (lastUpdateAt.isNullOrBlank()) {
                if (records.isNotEmpty()) {
                    records.forEach { BarcodeCacheManager.addExpedisiRecord(it) }
                    Log.d(TAG, "✅ [EXPEDISI SYNC] Initial load cached ${records.size} records")
                } else {
                    Log.d(TAG, "ℹ️ [EXPEDISI SYNC] No expedisi data returned for initial load")
                }
            } else {
                if (records.isNotEmpty()) {
                    Log.d(TAG, "📥 [EXPEDISI SYNC] Retrieved ${records.size} delta records")
                }
                records.forEach { record ->
                    if (record.deleted_at != null || record.flag.uppercase() != "NO") {
                        BarcodeCacheManager.removeExpedisiRecord(record.resino)
                    } else {
                        BarcodeCacheManager.addExpedisiRecord(record)
                    }
                }
            }

            BarcodeCacheManager.setLastExpedisiSyncTime(now)
            val finalSize = BarcodeCacheManager.getAllExpedisiRecords().size
            Log.d(TAG, "✅ [EXPEDISI SYNC] Cache sync completed, total cached: $finalSize records")
        } catch (e: NetworkException) {
            Log.w(TAG, "⚠️ [SYNC] Network error (connection failed): ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Sync failed (non-critical): ${e.message}")
        }
    }
    
    /**
     * Save barcode resi record dengan LOCAL-FIRST Strategy:
     * - Tambah ke cache dulu (INSTAN, non-blocking)
     * - POST ke Supabase di background (non-blocking, tidak menunggu response)
     * Mengembalikan true jika cache berhasil di-update (optimistic), false jika gagal.
     * Network sync terjadi di background - tidak blocking return value.
     */
    suspend fun saveBarcodeResiRecord(record: BarcodeSupabaseService.BarcodeScanRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            // **VALIDASI**: Pastikan created selalu terisi sebelum menyimpan
            val recordWithCreated = if (record.created.isNullOrBlank()) {
                Log.w(TAG, "⚠️ Record ${record.Resi} tidak punya created, menambahkan waktu sekarang")
                record.copy(created = java.time.Instant.now().toString())
            } else {
                record
            }
            
            // **LOCAL-FIRST**: Masukkan ke cache dulu (INSTAN, non-blocking)
            BarcodeCacheManager.addResiRecord(recordWithCreated)
            Log.d(TAG, "💾 [LOCAL-FIRST] Added to cache instantly: ${recordWithCreated.Resi}, created=${recordWithCreated.created}")
            
            // **NON-BLOCKING NETWORK**: POST ke Supabase di background (tidak menunggu response)
            // Gunakan launch terpisah agar tidak blocking return
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val success = barcodeSupabaseService.saveBarcodeScan(recordWithCreated)
                    if (success) {
                        Log.d(TAG, "✅ [Background] Supabase save OK: ${record.Resi}")
                    } else {
                        Log.e(TAG, "❌ [Background] Supabase save FAILED: ${record.Resi}, will be retried by background sync")
                        // Network gagal, tapi cache sudah di-update (local-first)
                        // Background sync akan retry otomatis
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [Background] Error saving to Supabase: ${e.message}", e)
                    // Error di background, tapi cache sudah di-update (local-first)
                    // Background sync akan retry otomatis
                }
            }
            
            // **OPTIMISTIC RETURN**: Return true segera setelah cache update (tidak menunggu network)
            // Cache sudah di-update, UI bisa langsung update
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in local-first save: ${e.message}", e)
            false
        }
    }
    
    /**
     * Hapus barcode resi record dari cache dan Supabase
     */
    suspend fun deleteBarcodeResiRecord(resi: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Deleting barcode resi record: $resi")
            
            // Hapus dari cache terlebih dahulu (optimistic delete)
            BarcodeCacheManager.removeResiRecord(resi)
            Log.d(TAG, "🗑️ Removed from cache: $resi")
            
            // Hapus dari Supabase
            val supabaseOk = barcodeSupabaseService.deleteBarcodeResi(resi)
            if (supabaseOk) {
                Log.d(TAG, "✅ Barcode resi deleted successfully from Supabase: $resi")
                true
            } else {
                Log.w(TAG, "⚠️ Failed to delete from Supabase, but removed from cache: $resi")
                // Jika Supabase gagal, tetap return true karena sudah dihapus dari cache
                // (akan di-sync ulang saat background sync)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting barcode resi record: ${e.message}", e)
            false
        }
    }
    
    /**
     * Update flag di tbl_expedisi berdasarkan resino
     * Jika flag berubah ke "YES", remove dari cache (karena cache hanya menyimpan flag="NO")
     */
    suspend fun updateExpedisiFlag(resino: String, flag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 [Supabase-only] Updating flag in tbl_expedisi: resino=$resino, flag=$flag")
            val success = barcodeSupabaseService.updateExpedisiFlag(resino, flag)
            
            if (success) {
                // Jika flag berubah ke "YES", remove dari cache (cache hanya menyimpan flag="NO")
                if (flag.uppercase() == "YES") {
                    com.example.cekpicklist.cache.BarcodeCacheManager.removeExpedisiRecord(resino)
                    Log.d(TAG, "🗑️ Removed from cache (flag changed to YES): resino=$resino")
                }
                Log.d(TAG, "✅ Flag updated in Supabase and cache: resino=$resino, flag=$flag")
            } else {
                Log.e(TAG, "❌ Failed to update flag in Supabase: resino=$resino, flag=$flag")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating expedisi flag in Supabase: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save barcode expedisi record dengan Light Cache strategy
     */
    suspend fun saveBarcodeExpedisiRecord(record: BarcodeSupabaseService.BarcodeSessionRecord) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Saving barcode expedisi record: ${record.orderno}")
            
            // Step 1: Save ke Supabase
            val sessionId = barcodeSupabaseService.saveBarcodeSession(record)
            
            if (sessionId != null) {
                // Step 2: Update cache immediately (optimistic update)
                BarcodeCacheManager.addExpedisiRecord(record)
                Log.d(TAG, "✅ Saved barcode expedisi record to Supabase and cache: ${record.orderno}")
            } else {
                Log.e(TAG, "❌ Failed to save barcode expedisi record to Supabase: ${record.orderno}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving barcode expedisi record: ${e.message}", e)
        }
    }
    
    /**
     * Helper function untuk check apakah created date dalam window 7 hari terakhir
     */
    private fun isWithinLast7Days(created: String?): Boolean {
        return try {
            if (created.isNullOrBlank()) return true
            val datePart = if (created.length >= 10) created.substring(0, 10) else created
            val recordDate = java.time.LocalDate.parse(datePart)
            val todayUtc = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val sevenDaysWindowStart = todayUtc.minusDays(6) // termasuk hari ini
            !recordDate.isBefore(sevenDaysWindowStart)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Cannot parse created date: '$created'. Keeping record by default.")
            true
        }
    }
    
    /**
     * **DELETE DETECTION**: Deteksi resi yang dihapus dari tbl_resi
     * - Query 7 hari dari tbl_resi untuk compare dengan cache
     * - Batch verification untuk memastikan resi benar-benar tidak ada
     * - Hapus dari cache resi jika resi tidak ada di tbl_resi
     * Dijalankan di background setiap 60 detik untuk menjaga cache tetap sinkron
     */
    suspend fun detectAndRemoveDeletedResi() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 [DELETE DETECTION] Detecting deleted resi from tbl_resi...")
            
            // 1. Ambil cache resi (7 hari terakhir)
            val cachedResi = BarcodeCacheManager.getAllResiRecords()
            val cachedResiSet = cachedResi.map { it.Resi.trim().uppercase() }.toSet()
            
            Log.d(TAG, "📦 Cache resi contains ${cachedResiSet.size} records")
            
            // 2. Query 7 hari dari tbl_resi untuk compare (bukan dari cache expedisi)
            val sevenDaysAgo = java.time.Instant.now()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(6)
                .toString()
            
            val supabaseResi = try {
                Log.d(TAG, "📥 Querying tbl_resi for last 7 days: $sevenDaysAgo")
                barcodeSupabaseService.getBarcodeResiSince(sevenDaysAgo)
            } catch (e: NetworkException) {
                Log.w(TAG, "⚠️ Network error querying tbl_resi: ${e.message}")
                return@withContext
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error querying tbl_resi: ${e.message}")
                return@withContext
            }
            
            val supabaseResiSet = supabaseResi.map { it.Resi.trim().uppercase() }.toSet()
            Log.d(TAG, "☁️ Supabase tbl_resi contains ${supabaseResiSet.size} records (7 days)")
            
            // 3. Compare: Resi yang ada di cache tapi tidak ada di Supabase
            val missingInSupabase = cachedResiSet - supabaseResiSet
            
            if (missingInSupabase.isNotEmpty()) {
                Log.d(TAG, "🔍 Found ${missingInSupabase.size} resi in cache but not in Supabase, verifying...")
                
                // 4. Batch verification: Cek apakah resi benar-benar tidak ada di tbl_resi
                val missingResiList = missingInSupabase.toList()
                val existsInResi = try {
                    barcodeSupabaseService.batchCheckResiExists(missingResiList)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error during batch verification: ${e.message}")
                    // Jika error, assume semua exists (safe default)
                    missingResiList.associateWith { true }
                }
                
                // 5. Hapus resi yang benar-benar tidak ada di tbl_resi
                var removedCount = 0
                missingResiList.forEach { resi ->
                    val exists = existsInResi.getOrDefault(resi, false)
                    if (!exists) {
                        // Resi benar-benar tidak ada di tbl_resi, hapus dari cache
                        val existsInCache = cachedResi.any { 
                            it.Resi.trim().uppercase() == resi 
                        }
                        if (existsInCache) {
                            BarcodeCacheManager.removeResiRecord(resi)
                            removedCount++
                            Log.d(TAG, "🗑️ Removed deleted resi from cache: $resi")
                        }
                    } else {
                        Log.d(TAG, "✅ Resi $resi still exists in tbl_resi (false positive avoided)")
                    }
                }
                
                // 6. ❌ TIDAK update flag (trigger sudah handle)
                // Trigger di Supabase akan otomatis update flag menjadi "NO" saat resi dihapus
                
                if (removedCount > 0) {
                    Log.d(TAG, "✅ Removed $removedCount deleted resi from cache")
                } else {
                    Log.d(TAG, "✅ No deleted resi found (all still exist in tbl_resi)")
                }
            } else {
                Log.d(TAG, "✅ No deleted resi detected (cache in sync with tbl_resi)")
            }
        } catch (e: NetworkException) {
            // Network error: koneksi gagal
            Log.w(TAG, "⚠️ [DELETE DETECTION] Network error (connection failed): ${e.message}")
        } catch (e: CancellationException) {
            // Re-throw cancellation untuk proper coroutine handling
            throw e
        } catch (e: Exception) {
            // Non-critical error, log saja (tidak throw)
            Log.w(TAG, "⚠️ Delete detection failed (non-critical): ${e.message}")
        }
    }
    
    /**
     * **DETEKSI PENGHAPUSAN**: Bandingkan cache expedisi dengan Supabase, hapus data yang tidak ada di Supabase
     * Dijalankan di background untuk menjaga cache tetap sinkron dengan Supabase
     * Catatan: Cache hanya menyimpan expedisi dengan flag="NO", jadi hanya membandingkan data dengan flag="NO"
     */
    suspend fun detectAndRemoveDeletedExpedisi() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 [Background] Detecting deleted expedisi from Supabase (TIMESTAMP-BASED)...")
            
            // **OPTIMASI BANDWIDTH**: Gunakan timestamp-based fetch untuk hanya ambil perubahan
            val lastSyncTime = BarcodeCacheManager.getLastExpedisiSyncTime()
            val sinceDate = BarcodeCacheManager.timestampToIsoDate(lastSyncTime)
            
            Log.d(TAG, "📅 Last sync time: ${if (lastSyncTime == 0L) "Never (initial sync)" else "${(System.currentTimeMillis() - lastSyncTime) / 1000}s ago"}")
            Log.d(TAG, "📅 Fetching data since: $sinceDate")
            
            // 1. Ambil semua expedisi dari cache (hanya flag="NO")
            val cachedExpedisi = BarcodeCacheManager.getAllExpedisiRecords()
            val cachedResinoSet = cachedExpedisi.map { it.resino.trim().uppercase() }.toSet()
            
            Log.d(TAG, "📦 Cache contains ${cachedResinoSet.size} expedisi records (flag=NO)")
            
            // 2. **OPTIMASI**: Ambil HANYA data yang berubah sejak lastSyncTime dari Supabase
            val supabaseExpedisi = try {
                if (lastSyncTime == 0L) {
                    // Initial sync: ambil semua data (flag="NO")
                    Log.d(TAG, "🔄 Initial sync: fetching all data with flag=NO")
                    barcodeSupabaseService.getAllBarcodeExpedisi()
                } else {
                    // Incremental sync: ambil hanya data baru/modified sejak lastSyncTime
                    Log.d(TAG, "🔄 Incremental sync: fetching only changes since $sinceDate")
                    barcodeSupabaseService.getBarcodeExpedisiSince(sinceDate)
                }
            } catch (e: NetworkException) {
                // Network error: koneksi gagal, tidak bisa compare dengan Supabase
                Log.w(TAG, "⚠️ [Background] Network error while fetching expedisi from Supabase: ${e.message}")
                Log.w(TAG, "⚠️ [Background] Skipping deleted expedisi detection (cannot verify with Supabase)")
                // Return early, jangan retry semua record saat koneksi gagal
                return@withContext
            } catch (e: CancellationException) {
                // Re-throw cancellation untuk proper coroutine handling
                throw e
            } catch (e: Exception) {
                // Error lain, log dan skip
                Log.w(TAG, "⚠️ [Background] Error fetching expedisi from Supabase: ${e.message}")
                return@withContext
            }
            
            val supabaseResinoSet = supabaseExpedisi.map { it.resino.trim().uppercase() }.toSet()
            
            Log.d(TAG, "☁️ Supabase contains ${supabaseResinoSet.size} expedisi records (flag=NO, ${if (lastSyncTime == 0L) "full sync" else "incremental"})")
            
            // 3. Bandingkan: resino yang ada di cache tapi tidak ada di Supabase = dihapus
            val deletedResino = cachedResinoSet - supabaseResinoSet
            
            // 4. Deteksi data baru: resino yang ada di Supabase tapi tidak ada di cache = baru
            val newResino = supabaseResinoSet - cachedResinoSet
            
            // 5. **LOGIKA BISNIS**: Deteksi flag berubah dari "NO" ke "YES" dan otomatis tambahkan ke tbl_resi
            if (deletedResino.isNotEmpty()) {
                Log.d(TAG, "🔄 Found ${deletedResino.size} expedisi in cache but not in Supabase (flag=NO) - checking if flag changed to YES...")
                
                var flagChangedToYesCount = 0
                var alreadyInResiCount = 0
                var removedFromCacheCount = 0
                
                deletedResino.forEach { resino ->
                    try {
                        // Cek apakah resino sudah ada di tbl_resi (berarti flag sudah berubah menjadi "YES" dan sudah di-scan)
                        val cachedResi = BarcodeCacheManager.getAllResiRecords()
                        val existsInResi = cachedResi.any { 
                            it.Resi.trim().uppercase() == resino 
                        }
                        
                        if (existsInResi) {
                            // Sudah ada di tbl_resi, berarti flag sudah berubah menjadi "YES" dan sudah di-scan
                            // Hapus dari cache expedisi (karena flag="YES")
                            BarcodeCacheManager.removeExpedisiRecord(resino)
                            alreadyInResiCount++
                            Log.d(TAG, "✅ Resino $resino already in tbl_resi (flag changed to YES and scanned), removed from expedisi cache")
                        } else {
                            // Belum ada di tbl_resi, berarti flag berubah menjadi "YES" tapi belum di-scan
                            // Ambil record expedisi dari cache untuk mendapatkan data lengkap
                            val expedisiRecord = cachedExpedisi.find { 
                                it.resino.trim().uppercase() == resino 
                            }
                            
                            if (expedisiRecord != null) {
                                // **LOGIKA BISNIS**: Flag berubah menjadi "YES", otomatis tambahkan ke tbl_resi
                                val createdTime = expedisiRecord.created ?: java.time.Instant.now().toString()
                                val resiRecord = BarcodeSupabaseService.BarcodeScanRecord(
                                    id = null,
                                    Resi = expedisiRecord.resino,
                                    created = createdTime,
                                    Keterangan = expedisiRecord.couriername,
                                    nokarung = null,
                                    schedule = "ontime" // Default schedule
                                )
                                
                                // Tambahkan ke cache resi dan POST ke Supabase
                                val saveSuccess = saveBarcodeResiRecord(resiRecord)
                                if (saveSuccess) {
                                    flagChangedToYesCount++
                                    Log.d(TAG, "✅ Flag changed to YES for $resino, automatically added to tbl_resi")
                                } else {
                                    Log.w(TAG, "⚠️ Failed to add $resino to tbl_resi (flag changed to YES)")
                                }
                                
                                // Hapus dari cache expedisi (karena flag="YES")
                                BarcodeCacheManager.removeExpedisiRecord(resino)
                                removedFromCacheCount++
                            } else {
                                // Record tidak ditemukan di cache, mungkin sudah dihapus
                                Log.w(TAG, "⚠️ Expedisi record not found in cache for $resino, removing from cache")
                                BarcodeCacheManager.removeExpedisiRecord(resino)
                                removedFromCacheCount++
                            }
                        }
                    } catch (e: NetworkException) {
                        // Network error, skip untuk resino ini
                        Log.w(TAG, "⚠️ Network error processing flag change for $resino: ${e.message} (will retry later)")
                    } catch (e: CancellationException) {
                        // Re-throw cancellation untuk proper coroutine handling
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing flag change for $resino: ${e.message}", e)
                    }
                }
                
                Log.d(TAG, "📊 Flag change summary:")
                Log.d(TAG, "   - Flag changed to YES (auto-added to tbl_resi): $flagChangedToYesCount")
                Log.d(TAG, "   - Already in tbl_resi (scanned): $alreadyInResiCount")
                Log.d(TAG, "   - Removed from expedisi cache: $removedFromCacheCount")
            }
            
            // 6. Tambahkan data baru dari Supabase ke cache (hanya flag="NO")
            if (newResino.isNotEmpty()) {
                Log.d(TAG, "🆕 Found ${newResino.size} new expedisi in Supabase (not in cache), adding to cache...")
                
                // Ambil full record dari Supabase untuk data baru (hanya flag="NO")
                val newExpedisiRecords = supabaseExpedisi.filter { 
                    newResino.contains(it.resino.trim().uppercase()) && 
                    it.flag.uppercase() == "NO"
                }
                
                newExpedisiRecords.forEach { record ->
                    BarcodeCacheManager.addExpedisiRecord(record)
                }
                
                Log.d(TAG, "✅ Added ${newExpedisiRecords.size} new expedisi records to cache:")
                newResino.take(10).forEach { resino ->
                    Log.d(TAG, "   - $resino")
                }
                if (newResino.size > 10) {
                    Log.d(TAG, "   ... and ${newResino.size - 10} more")
                }
            }
            
            // 7. **OPTIMASI**: Update lastSyncTime setelah sync berhasil
            val currentTime = System.currentTimeMillis()
            BarcodeCacheManager.setLastExpedisiSyncTime(currentTime)
            Log.d(TAG, "💾 Updated last sync time: ${java.time.Instant.ofEpochMilli(currentTime).atZone(java.time.ZoneOffset.UTC).toLocalDate()}")
            
            if (deletedResino.isEmpty() && newResino.isEmpty()) {
                Log.d(TAG, "✅ Cache is in sync with Supabase (no deletions, no new data)")
            }
            
        } catch (e: CancellationException) {
            // Re-throw cancellation untuk proper coroutine handling
            // Ini normal behavior saat coroutine di-cancel (misalnya saat app di-close atau sync dihentikan)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error detecting deleted expedisi: ${e.message}", e)
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
            Log.d(TAG, "🔄 [Supabase] Incremental force refresh barcode data...")
            val defaultAnchor = java.time.Instant.now().minusSeconds(6 * 24 * 60 * 60).toString()
            val resiAnchor = BarcodeCacheManager.getLastResiUpdatedAt() ?: defaultAnchor
            val expAnchor = BarcodeCacheManager.getLastExpedisiUpdateAt() ?: defaultAnchor
            val resiDelta = barcodeSupabaseService.getBarcodeResiSince(resiAnchor)
            val expedisiDelta = barcodeSupabaseService.getBarcodeExpedisiSince(expAnchor)

            // Upsert ke cache (tanpa clear)
            resiDelta.forEach { BarcodeCacheManager.addResiRecord(it) }
            expedisiDelta.forEach { BarcodeCacheManager.addExpedisiRecord(it) }

            Log.d(TAG, "✅ Incremental refresh: +${resiDelta.size} resi (since $resiAnchor), +${expedisiDelta.size} expedisi (since $expAnchor)")
            BarcodeCacheManager.getAllResiRecords()
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
            Log.d(TAG, "🔄 [Supabase] Incremental force refresh expedisi data...")
            val defaultAnchor = java.time.Instant.now().minusSeconds(6 * 24 * 60 * 60).toString()
            val sinceDate = BarcodeCacheManager.getLastExpedisiUpdateAt() ?: defaultAnchor
            val expedisiDelta = barcodeSupabaseService.getBarcodeExpedisiSince(sinceDate)
            expedisiDelta.forEach { BarcodeCacheManager.addExpedisiRecord(it) }
            Log.d(TAG, "✅ Incremental expedisi refresh: +${expedisiDelta.size} (since $sinceDate)")
            BarcodeCacheManager.getAllExpedisiRecords()
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
     * Get unique courier names untuk spinner: Supabase-first, lalu simpan ke cache couriers.
     * Fallback ke cache jika offline/failed.
     */
    suspend fun getUniqueCourierNames(): List<String> = withContext(Dispatchers.IO) {
        try {
            // 1) **OPTIMASI**: Jika cache couriers valid, gunakan (FASTEST PATH)
            val cached = BarcodeCacheManager.getCourierNamesCache()
            if (cached.isNotEmpty() && BarcodeCacheManager.isCouriersCacheValid()) {
                Log.d(TAG, "📦 Using couriers cache: ${cached.size} (FAST)")
                return@withContext cached
            }

            // 2) **OPTIMASI**: Fallback ke expedisi cache jika courier cache kosong (FAST)
            val expedisiCache = BarcodeCacheManager.getUniqueCourierNamesFromCache()
            if (expedisiCache.isNotEmpty()) {
                Log.d(TAG, "📦 Using expedisi cache as fallback: ${expedisiCache.size} (FAST)")
                // Update courier cache untuk next time
                BarcodeCacheManager.setCourierNamesCache(expedisiCache)
                return@withContext expedisiCache
            }

            // 3) **ROOM-ONLY**: Tidak ada auto-fetch dari Supabase, hanya return dari cache
            // User harus melakukan sync manual untuk update data dari Supabase
            Log.d(TAG, "📦 [ROOM-ONLY] Courier cache empty - data hanya akan tersedia setelah user melakukan sync manual")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting unique courier names: ${e.message}", e)
            // 4) Fallback: expedisi cache yang ada
            val expedisiCache = BarcodeCacheManager.getUniqueCourierNamesFromCache()
            if (expedisiCache.isNotEmpty()) {
                Log.d(TAG, "⚠️ Using expedisi cache due to error: ${expedisiCache.size}")
                return@withContext expedisiCache
            }
            // 5) Last resort: courier cache yang ada (meski expired)
            val fallback = BarcodeCacheManager.getCourierNamesCache()
            if (fallback.isNotEmpty()) {
                Log.d(TAG, "⚠️ Using expired courier cache: ${fallback.size}")
                return@withContext fallback
            }
            emptyList()
        }
    }
    
    /**
     * Get total qty expedisi (semua flag) untuk courier tertentu pada HARI INI.
     * SEKARANG menggunakan data dari Room (BarcodeCacheManager) sebagai sumber utama.
     */
    suspend fun getExpeditionTotalToday(courier: String): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 [Room] getExpeditionTotalToday: $courier")
            val all = BarcodeCacheManager.getAllExpedisiRecords()
            val today = java.time.LocalDate.now().toString() // YYYY-MM-DD
            val total = all.count { rec ->
                val courierName = rec.couriername?.trim().orEmpty()
                val datePrefix = rec.datetrans.take(10)
                courierName.equals(courier, ignoreCase = true) && datePrefix == today
            }
            Log.d(TAG, "✅ Expedition total today for '$courier' from Room: $total")
            total
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expedition total today from Room: ${e.message}", e)
            0
        }
    }

    /**
     * Get jumlah unik resino hari ini langsung dari Supabase
     */
    suspend fun getTodayUniqueExpedisiCountSupabase(): Int = withContext(Dispatchers.IO) {
        try {
            // GANTI: gunakan data dari Room (BarcodeCacheManager/BarcodeDao) sebagai sumber utama
            val todayIso = java.time.LocalDate.now().toString() // YYYY-MM-DD
            val total = BarcodeCacheManager.getTodayUniqueResinoFromRoom(todayIso)
            Log.d(TAG, "✅ Today unique resino from Room: $total")
            total
        } catch (e: CancellationException) {
            Log.d(TAG, "🛑 getTodayUniqueExpedisiCountSupabase cancelled (normal behavior)")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getTodayUniqueExpedisiCountFromRoom: ${e.message}", e)
            0
        }
    }

    /**
     * Get total scan hari ini (tbl_resi) langsung dari Supabase
     */
    suspend fun getTodayResiScanCountSupabase(): Int = withContext(Dispatchers.IO) {
        try {
            // GANTI: gunakan data dari Room (BarcodeCacheManager/BarcodeDao)
            val todayIso = java.time.LocalDate.now().toString()
            val total = BarcodeCacheManager.getTodayResiScanCountFromRoom(todayIso)
            Log.d(TAG, "✅ Today resi scan count from Room: $total")
            total
        } catch (e: CancellationException) {
            Log.d(TAG, "🛑 getTodayResiScanCountSupabase cancelled (normal behavior)")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getTodayResiScanCountFromRoom: ${e.message}", e)
            0
        }
    }
    
    /**
     * **SUPABASE-FIRST**: Cek apakah resi ada di tbl_expedisi (query langsung ke Supabase, tidak menggunakan cache)
     * Digunakan untuk validasi NOT_FOUND yang kritis - memastikan data tidak disimpan jika resi tidak valid
     */
    suspend fun checkExpedisiByResinoDirect(resino: String): BarcodeSupabaseService.BarcodeSessionRecord? = withContext(Dispatchers.IO) {
        try {
            // GANTI: cek langsung di Room (tbl_expedisi) lewat BarcodeDao
            Log.d(TAG, "🔍 [Room-First] Checking resi '$resino' in tbl_expedisi (Room)...")
            val record = BarcodeCacheManager.getExpedisiRecordByResino(resino)
            if (record != null) {
                Log.d(TAG, "✅ [Room-First] Found resi '$resino' in tbl_expedisi (cache/Room)")
                record
            } else {
                Log.d(TAG, "ℹ️ [Room-First] Resi '$resino' NOT FOUND in tbl_expedisi (cache/Room)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Room-First] Error checking resi: ${e.message}", e)
            null
        }
    }
    
    /**
     * Clear semua data barcode lokal
     */
    suspend fun clearAllBarcodeData() { /* No-op in Supabase-only mode */ }
}
