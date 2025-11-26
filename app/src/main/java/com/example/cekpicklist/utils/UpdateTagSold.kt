package com.example.cekpicklist.utils

import android.util.Log
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.data.TagStatusUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service untuk update tag status RFID ke SOLD
 * Dapat dipanggil dari activity mana saja tanpa perlu instance
 * 
 * Contoh penggunaan:
 * 
 * 1. Update single warehouse:
 * ```kotlin
 * lifecycleScope.launch {
 *     val (success, message) = UpdateTagSold.updateTagSold(
 *         warehouseId = "WH001",
 *         rfidList = listOf("EPC001", "EPC002", "EPC003")
 *     )
 *     if (success) {
 *         Toast.makeText(context, "Tag status berhasil di-update", Toast.LENGTH_SHORT).show()
 *     } else {
 *         Toast.makeText(context, "Error: $message", Toast.LENGTH_SHORT).show()
 *     }
 * }
 * ```
 * 
 * 2. Update multiple warehouses (batch):
 * ```kotlin
 * lifecycleScope.launch {
 *     val items = mapOf(
 *         "WH001" to listOf("EPC001", "EPC002"),
 *         "WH002" to listOf("EPC003", "EPC004")
 *     )
 *     val results = UpdateTagSold.updateTagSoldBatch(items)
 *     results.forEach { (warehouse, result) ->
 *         Log.d(TAG, "Warehouse $warehouse: ${if (result.first) "Success" else "Failed: ${result.second}"}")
 *     }
 * }
 * ```
 * 
 * 3. Update dari list items dengan warehouse:
 * ```kotlin
 * lifecycleScope.launch {
 *     val items = listOf(
 *         "WH001" to "EPC001",
 *         "WH001" to "EPC002",
 *         "WH002" to "EPC003"
 *     )
 *     val results = UpdateTagSold.updateTagSoldFromItems(items)
 *     // Results akan dikelompokkan per warehouse secara otomatis
 * }
 * ```
 */
object UpdateTagSold {
    
    private const val TAG = "UpdateTagSold"
    private val apiService = NirwanaApiService()
    
    /**
     * Update tag status menjadi SOLD untuk list RFID tertentu
     * 
     * @param rfidList List EPC/RFID yang akan di-update (required, tidak boleh kosong)
     * @return Pair<Boolean, String> - (success, message)
     */
    suspend fun updateTagSold(
        rfidList: List<String>
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Tidak perlu warehouseId untuk UpdateTagSold - context warehouse ditentukan oleh backend

            if (rfidList.isEmpty()) {
                val errorMsg = "RFID list tidak boleh kosong"
                Log.e(TAG, "❌ $errorMsg")
                return@withContext Pair(false, errorMsg)
            }

            // Hapus duplikat dan filter yang kosong
            // **CATATAN**: uniqueRfids adalah hasil pembersihan dari rfidList
            // - rfidList: input parameter (bisa ada duplikat atau string kosong)
            // - uniqueRfids: hasil distinct() dan filter isNotBlank() (sudah unik dan tidak kosong)
            // - rfid_list di RelocationUpdateRequest: menggunakan uniqueRfids
            val uniqueRfids = rfidList.distinct().filter { it.isNotBlank() }
            if (uniqueRfids.isEmpty()) {
                val errorMsg = "Tidak ada RFID yang valid"
                Log.e(TAG, "❌ $errorMsg")
                return@withContext Pair(false, errorMsg)
            }
            
            // Log perbandingan jika ada perbedaan
            if (rfidList.size != uniqueRfids.size) {
                Log.d(TAG, "📊 Input rfidList: ${rfidList.size} items, uniqueRfids: ${uniqueRfids.size} items (removed duplicates/blanks)")
            }
            
            Log.d(TAG, "🔄 Updating tag status to SOLD for ${uniqueRfids.size} RFIDs (no warehouse_id in payload)")
            
            val request = TagStatusUpdateRequest(
                tag_status = "SOLD",
                rfid_list = uniqueRfids  // Menggunakan uniqueRfids (bukan rfidList)
            )
            
            val response = apiService.updateTagStatusByRfids(request)
            
            if (response.success) {
                Log.d(TAG, "✅ SOLD status updated successfully for rfids=${uniqueRfids.size}")
                Pair(true, response.message ?: "Tag status berhasil di-update ke SOLD")
            } else {
                Log.e(TAG, "❌ Failed to update SOLD status: ${response.message}")
                Pair(false, response.message ?: "Gagal update tag status ke SOLD")
            }
            
        } catch (e: Exception) {
            val errorMsg = "Error updating tag status to SOLD: ${e.message}"
            Log.e(TAG, "❌ $errorMsg", e)
            Pair(false, errorMsg)
        }
    }
    
    /**
     * Update tag status menjadi SOLD untuk multiple warehouses
     * Items akan dikelompokkan per warehouse untuk efisiensi
     * 
     * @param items Map<warehouseId, List<rfid>> - items yang akan di-update, dikelompokkan per warehouse
     * @return Map<warehouseId, Pair<Boolean, String>> - hasil update per warehouse
     */
    suspend fun updateTagSoldBatch(
        items: Map<String, List<String>>
    ): Map<String, Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Pair<Boolean, String>>()

        if (items.isEmpty()) {
            Log.w(TAG, "⚠️ No items to update")
            return@withContext results
        }

        // Flatten all RFIDs from all warehouses - backend akan menentukan warehouse dari EPC
        val allRfids = items.values.flatten().distinct()
        Log.d(TAG, "🔄 Batch updating SOLD status for ${allRfids.size} RFIDs (warehouse ignored in payload)")

        val (success, message) = updateTagSold(allRfids)
        // Simpan hasil yang sama untuk setiap warehouse key, agar kompatibel dengan caller lama
        items.keys.forEach { warehouseKey ->
            results[warehouseKey.trim()] = Pair(success, message)
        }

        val successCount = results.values.count { it.first }
        val totalCount = results.size
        Log.d(TAG, "📊 Batch update completed: $successCount/$totalCount warehouse(s) succeeded")

        results
    }
    
    /**
     * Update tag status menjadi SOLD untuk list items dengan warehouse
     * Items akan dikelompokkan per warehouse secara otomatis
     * 
     * @param items List<Pair<warehouseId, rfid>> - items yang akan di-update
     * @return Map<warehouseId, Pair<Boolean, String>> - hasil update per warehouse
     */
    suspend fun updateTagSoldFromItems(
        items: List<Pair<String, String>>
    ): Map<String, Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            Log.w(TAG, "⚠️ No items to update")
            return@withContext emptyMap()
        }
        
        // Filter items yang valid (warehouse dan rfid tidak kosong)
        val validItems = items.filter { (warehouse, rfid) -> 
            warehouse.isNotBlank() && rfid.isNotBlank() 
        }
        
        if (validItems.isEmpty()) {
            Log.w(TAG, "⚠️ No valid items to update (all items have empty warehouse or rfid)")
            return@withContext emptyMap()
        }
        
        // Log items tanpa warehouse
        val invalidItems = items.filter { (warehouse, _) -> warehouse.isBlank() }
        if (invalidItems.isNotEmpty()) {
            Log.w(TAG, "⚠️ ${invalidItems.size} items without warehouse (will be skipped): ${invalidItems.map { it.second }}")
        }
        
        // Group by warehouse hanya untuk logging; payload tetap hanya EPC list
        val groupedByWarehouse = validItems.groupBy { (warehouse, _) -> warehouse.trim() }

        Log.d(TAG, "🔄 Updating SOLD status for ${validItems.size} items across ${groupedByWarehouse.size} warehouse(s) (payload = EPC only)")

        // Gabungkan semua EPC unik
        val allRfids = validItems.map { it.second }.distinct()

        // Update dengan satu panggilan (tanpa warehouse_id)
        val (success, message) = updateTagSold(allRfids)

        // Kembalikan Map per-warehouse dengan hasil yang sama (kompatibel dengan caller lama)
        groupedByWarehouse.keys.associateWith { Pair(success, message) }
    }
}

