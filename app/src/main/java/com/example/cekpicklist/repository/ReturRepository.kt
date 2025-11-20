package com.example.cekpicklist.repository

import android.app.Application
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.data.RelocationUpdateRequest
import com.example.cekpicklist.data.RelocationUpdateResponse
import com.example.cekpicklist.data.Warehouse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReturRepository(application: Application) {

    private val apiService = NirwanaApiService()

    /**
     * Lookup informasi produk berdasarkan EPC tunggal.
     * Menggunakan batch endpoint untuk konsistensi dengan service lain.
     */
    suspend fun lookupRfid(epc: String): NirwanaApiService.ProductInfo? = withContext(Dispatchers.IO) {
        val results = apiService.batchLookupRfidList(listOf(epc))
        if (results.isEmpty()) return@withContext null

        // Hasil batch bisa berisi lebih dari satu produk, pastikan mapping EPC -> produk tepat
        results.firstOrNull { info ->
            info.rfidList.contains(epc) || info.articleId.equals(epc, ignoreCase = true)
        } ?: results.first()
    }

    /**
     * Update status tag menjadi TAGGED untuk daftar EPC pada warehouse tertentu.
     */
    suspend fun markAsTagged(warehouseId: String, epcs: List<String>): RelocationUpdateResponse = withContext(Dispatchers.IO) {
        val request = RelocationUpdateRequest(
            warehouse_id = warehouseId,
            tag_status = "TAGGED",
            rfid_list = epcs
        )
        apiService.updateRelocationTagStatus(request)
    }

    /**
     * Batch lookup untuk daftar EPC Retur, digunakan saat tombol Stop ditekan.
     */
    suspend fun batchLookupRfidList(rfidList: List<String>): List<NirwanaApiService.ProductInfo> = withContext(Dispatchers.IO) {
        if (rfidList.isEmpty()) return@withContext emptyList()
        apiService.batchLookupRfidList(rfidList)
    }

    /**
     * Ambil daftar warehouse dari API.
     */
    suspend fun getWarehouses(): List<Warehouse> = withContext(Dispatchers.IO) {
        apiService.getWarehouses()
    }
}

