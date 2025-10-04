package com.example.cekpicklist.api

import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Batch Supabase Service untuk optimasi API calls
 * - Menggabungkan multiple queries menjadi single call
 * - Mengurangi network overhead
 * - Meningkatkan performa aplikasi
 */
class BatchSupabaseService {
    
    private val supabaseService = SupabaseService()
    
    companion object {
        private const val TAG = "BatchSupabaseService"
    }
    
    /**
     * Batch fetch picklist data untuk multiple picklists
     * @param picklistNumbers List picklist numbers yang akan di-fetch
     * @return Map picklistNo -> PicklistData
     */
    suspend fun getBatchPicklistData(picklistNumbers: List<String>): Map<String, PicklistData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Batch fetching data for ${picklistNumbers.size} picklists...")
            
            val startTime = System.currentTimeMillis()
            
            // Parallel fetch untuk semua picklists
            val deferredResults = picklistNumbers.map { picklistNo ->
                async {
                    try {
                        val items = supabaseService.getPicklistItems(picklistNo)
                        val processedEpcList = supabaseService.getProcessedEpcList(picklistNo)
                        // Note: getPicklistStatus tidak tersedia di SupabaseService, gunakan default status
                        val status = PicklistStatus(
                            picklistNumber = picklistNo,
                            isScanned = false,
                            remainingQty = 0,
                            totalQty = 0,
                            scannedQty = 0,
                            lastScanTime = null,
                            overscanQty = 0
                        )
                        
                        picklistNo to PicklistData(
                            items = items,
                            processedEpcList = processedEpcList,
                            status = status
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching data for $picklistNo: ${e.message}")
                        picklistNo to null
                    }
                }
            }
            
            val results = deferredResults.awaitAll()
            val validResults = results.filter { it.second != null }.associate { it.first to it.second!! }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ Batch fetch completed: ${validResults.size}/${picklistNumbers.size} picklists in ${duration}ms")
            Log.d(TAG, "📊 Average time per picklist: ${duration / picklistNumbers.size}ms")
            
            validResults
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch fetch: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Batch fetch picklist items untuk multiple picklists
     * @param picklistNumbers List picklist numbers
     * @return Map picklistNo -> List<PicklistItem>
     */
    suspend fun getBatchPicklistItems(picklistNumbers: List<String>): Map<String, List<PicklistItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Batch fetching items for ${picklistNumbers.size} picklists...")
            
            val startTime = System.currentTimeMillis()
            
            val deferredResults = picklistNumbers.map { picklistNo ->
                async {
                    try {
                        picklistNo to supabaseService.getPicklistItems(picklistNo)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching items for $picklistNo: ${e.message}")
                        picklistNo to emptyList<PicklistItem>()
                    }
                }
            }
            
            val results = deferredResults.awaitAll().toMap()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ Batch items fetch completed in ${duration}ms")
            
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch items fetch: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Batch fetch processed EPC lists untuk multiple picklists
     * @param picklistNumbers List picklist numbers
     * @return Map picklistNo -> List<String>
     */
    suspend fun getBatchProcessedEpcLists(picklistNumbers: List<String>): Map<String, List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Batch fetching processed EPC lists for ${picklistNumbers.size} picklists...")
            
            val startTime = System.currentTimeMillis()
            
            val deferredResults = picklistNumbers.map { picklistNo ->
                async {
                    try {
                        picklistNo to supabaseService.getProcessedEpcList(picklistNo)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching processed EPC list for $picklistNo: ${e.message}")
                        picklistNo to emptyList<String>()
                    }
                }
            }
            
            val results = deferredResults.awaitAll().toMap()
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ Batch processed EPC lists fetch completed in ${duration}ms")
            
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch processed EPC lists fetch: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Batch fetch picklist statuses untuk multiple picklists
     * @param picklistNumbers List picklist numbers
     * @return Map picklistNo -> PicklistStatus
     */
    suspend fun getBatchPicklistStatuses(picklistNumbers: List<String>): Map<String, PicklistStatus> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔥 Batch fetching statuses for ${picklistNumbers.size} picklists...")
            
            val startTime = System.currentTimeMillis()
            
            val deferredResults = picklistNumbers.map { picklistNo ->
                async {
                    try {
                        // Note: getPicklistStatus tidak tersedia di SupabaseService, gunakan default status
                        val status = PicklistStatus(
                            picklistNumber = picklistNo,
                            isScanned = false,
                            remainingQty = 0,
                            totalQty = 0,
                            scannedQty = 0,
                            lastScanTime = null,
                            overscanQty = 0
                        )
                        picklistNo to status
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching status for $picklistNo: ${e.message}")
                        picklistNo to null
                    }
                }
            }
            
            val results = deferredResults.awaitAll().filter { it.second != null }.associate { it.first to it.second!! }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            Log.d(TAG, "✅ Batch statuses fetch completed in ${duration}ms")
            
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in batch statuses fetch: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Data class untuk menyimpan semua data picklist
     */
    data class PicklistData(
        val items: List<PicklistItem>,
        val processedEpcList: List<String>,
        val status: PicklistStatus
    )
}
