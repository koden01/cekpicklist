package com.example.cekpicklist.repository

import android.app.Application
import android.util.Log
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.data.RelocationUpdateRequest
import com.example.cekpicklist.data.OutActivityItem
import com.example.cekpicklist.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OutActivityRepository(application: Application) {
    
    private val supabaseService = SupabaseService()
    private val nirwanaApiService = NirwanaApiService()
    
    companion object {
        private const val TAG = "OutActivityRepository"
    }
    
    /**
     * Submit out activity to database
     */
    suspend fun submitOutActivity(notrans: String, items: List<OutActivityItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.PicklistInput.d("Submitting out activity with ${items.size} items, notrans: $notrans")
            
            // TIDAK menulis ke Room sebagai sumber utama; semua write diarahkan ke Supabase.
            // Jika ingin cache lokal, bisa diaktifkan kembali dengan memasukkan hasil ke picklist_scan.
            
            // Submit langsung ke Supabase (source of truth)
            var allSuccess = true
            for (item in items) {
                val success = supabaseService.savePicklistScan(
                    picklistNo = notrans,
                    articleId = item.articleId,
                    epc = item.epc,
                    productId = item.productId,
                    articleName = item.articleName,
                    size = item.size,
                    notrans = notrans
                )
                
                if (!success) {
                    Logger.PicklistInput.e("Failed to save item to Supabase: ${item.epc}")
                    allSuccess = false
                }
            }
            
            if (allSuccess) {
                Logger.PicklistInput.d("Successfully submitted out activity to Supabase")
            } else {
                Logger.PicklistInput.w("Some items failed to submit to Supabase")
            }
            
            // Setelah submit ke database, update tag status ke SOLD per-warehouse
            val soldPosted = updateTagStatusSold(items)
            if (!soldPosted) {
                Logger.PicklistInput.w("Posting SOLD tag status returned partial/false result")
            }

            allSuccess && soldPosted
            
        } catch (e: Exception) {
            Logger.PicklistInput.e("Error submitting out activity: ${e.message}")
            false
        }
    }
    
    /**
     * Lookup RFID EPC to get product information
     */
    suspend fun lookupRfid(epc: String): OutActivityItem? = withContext(Dispatchers.IO) {
        try {
            Logger.PicklistInput.d("Looking up RFID: $epc")
            
            // Use existing API service to lookup RFID
            val products = nirwanaApiService.batchLookupRfidList(listOf(epc))
            
            if (products.isNotEmpty()) {
                val product = products.first()
                Logger.PicklistInput.d("Found product: ${product.articleName} ${product.size}")
                
                OutActivityItem(
                    epc = epc,
                    articleId = product.articleId,
                    articleName = product.articleName,
                    size = product.size,
                    qty = 1,
                    productId = product.productId,
                    brand = product.brand,
                    category = product.category,
                    subCategory = product.subCategory,
                    color = product.color,
                    gender = product.gender,
                    warehouse = product.warehouse,
                    tagStatus = product.tagStatus
                )
            } else {
                Logger.PicklistInput.w("No product found for EPC: $epc")
                null
            }
            
        } catch (e: Exception) {
            Logger.PicklistInput.e("Error looking up RFID $epc: ${e.message}")
            null
        }
    }

    /**
     * Update tag status menjadi SOLD ke endpoint /tag/flag/update.
     * Dikelompokkan per warehouse_id untuk efisiensi payload.
     */
    suspend fun updateTagStatusSold(items: List<OutActivityItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            if (items.isEmpty()) return@withContext true

            // Group by warehouse (kosongkan group tanpa warehouse)
            val groups = items.filter { !it.warehouse.isNullOrEmpty() }
                .groupBy { it.warehouse ?: "" }

            var allOk = true
            for ((warehouseId, groupItems) in groups) {
                val rfids = groupItems.map { it.epc }.distinct()
                if (rfids.isEmpty()) continue

                val request = RelocationUpdateRequest(
                    warehouse_id = warehouseId,
                    tag_status = "SOLD",
                    rfid_list = rfids
                )

                val response = nirwanaApiService.updateRelocationTagStatus(request)
                if (!response.success) {
                    Logger.PicklistInput.e("Failed to update SOLD status for warehouse=$warehouseId: ${response.message}")
                    allOk = false
                } else {
                    Logger.PicklistInput.d("SOLD status posted for warehouse=$warehouseId, rfids=${rfids.size}")
                }
            }
            allOk
        } catch (e: Exception) {
            Logger.PicklistInput.e("Error posting SOLD tag status: ${e.message}", e)
            false
        }
    }
}
