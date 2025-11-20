package com.example.cekpicklist.repository

import android.app.Application
import android.content.Context
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.data.RelocationUpdateRequest
import com.example.cekpicklist.data.OutActivityItem
import com.example.cekpicklist.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutActivityRepository(application: Application) {
    
    private val nirwanaApiService = NirwanaApiService()
    private val supabaseService = SupabaseService()
    private val appContext = application.applicationContext
    private val prefs = appContext.getSharedPreferences("out_activity", Context.MODE_PRIVATE)
    
    /**
     * Submit out activity to database
     */
    suspend fun submitOutActivity(notrans: String, items: List<OutActivityItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.PicklistInput.d("Submitting out activity (SOLD only) with ${items.size} items, notrans: $notrans")
            
            val supabaseSaved = supabaseService.saveOutActivityScans(notrans, items)
            if (!supabaseSaved) {
                Logger.PicklistInput.e("Failed to save out activity to Supabase")
                return@withContext false
            }

            incrementTransactionCounterForToday()

            // Setelah submit, langsung update tag status ke SOLD per-warehouse
            val soldPosted = updateTagStatusSold(items)
            if (!soldPosted) {
                Logger.PicklistInput.w("Posting SOLD tag status returned partial/false result")
            }

            supabaseSaved && soldPosted
            
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

    suspend fun fetchNextNotrans(): String = withContext(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())
        try {
            val nextNotrans = supabaseService.getNextOutTransactionId(dateStr)
            val sequence = extractSequence(nextNotrans)
            cacheTransactionNumberForToday(sequence)
            Logger.PicklistInput.d("Next OUT notrans from Supabase: $nextNotrans (seq=$sequence)")
            nextNotrans
        } catch (e: Exception) {
            Logger.PicklistInput.e("Failed to fetch next notrans from Supabase: ${e.message}")
            val localNumber = getCurrentTransactionNumberForToday()
            val fallback = buildNotrans(localNumber, dateStr)
            Logger.PicklistInput.w("Using local fallback notrans: $fallback")
            fallback
        }
    }

    private fun buildNotrans(number: Int, dateStr: String): String {
        return "OUT${String.format("%02d", number)}$dateStr"
    }

    private fun extractSequence(notrans: String): Int {
        val datePartLength = 8
        return try {
            if (!notrans.startsWith("OUT") || notrans.length <= 3 + datePartLength) return 0
            val numberPart = notrans.substring(3, notrans.length - datePartLength)
            numberPart.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun getCurrentTransactionNumberForToday(): Int {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_date", null)
        val lastCount = prefs.getInt("transaction_count", 0)
        return if (lastDate == today && lastCount > 0) lastCount else 1
    }

    private fun incrementTransactionCounterForToday() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString("last_date", null)
        val lastCount = prefs.getInt("transaction_count", 0)
        if (lastDate == today) {
            prefs.edit().putInt("transaction_count", if (lastCount > 0) lastCount + 1 else 2).apply()
        } else {
            prefs.edit().putString("last_date", today).putInt("transaction_count", 2).apply()
        }
    }

    private fun cacheTransactionNumberForToday(nextNumber: Int) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        prefs.edit().putString("last_date", today).putInt("transaction_count", nextNumber).apply()
    }
}
