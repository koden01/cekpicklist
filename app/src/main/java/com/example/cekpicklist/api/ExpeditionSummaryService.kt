package com.example.cekpicklist.api

import android.util.Log
import com.example.cekpicklist.data.ExpeditionSummary
import com.example.cekpicklist.data.ResiDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service untuk mengambil data expedition summary (sama dengan web app)
 */
class ExpeditionSummaryService {
    
    companion object {
        private const val TAG = "ExpeditionSummaryService"
    }
    
    /**
     * Ambil summary semua expedisi untuk tanggal tertentu
     * Sama dengan web app: get_expedition_summaries
     */
    suspend fun getExpeditionSummaries(selectedDate: String): List<ExpeditionSummary> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 Getting expedition summaries for date: $selectedDate")
            
            // MOCK implementation - dependency tidak tersedia
            // TODO: Implementasi real query:
            // SELECT 
            //   couriername as name,
            //   COUNT(*) as totalTransaksi,
            //   COUNT(CASE WHEN flag = 'YES' THEN 1 END) as totalScan,
            //   COUNT(CASE WHEN flag = 'NO' THEN 1 END) as sisa,
            //   COUNT(DISTINCT nokarung) as jumlahKarung,
            //   COUNT(CASE WHEN schedule = 'batal' THEN 1 END) as totalBatal,
            //   COUNT(CASE WHEN cekfu = true THEN 1 END) as totalScanFollowUp
            // FROM tbl_resi 
            // WHERE DATE(created) = ? AND couriername IS NOT NULL
            // GROUP BY couriername
            // ORDER BY totalTransaksi DESC
            
            // Mock data untuk testing
            val mockSummaries = listOf(
                ExpeditionSummary(
                    name = "JNE",
                    totalTransaksi = 150,
                    totalScan = 120,
                    sisa = 30,
                    jumlahKarung = 8,
                    totalBatal = 5,
                    totalScanFollowUp = 15
                ),
                ExpeditionSummary(
                    name = "TIKI",
                    totalTransaksi = 98,
                    totalScan = 85,
                    sisa = 13,
                    jumlahKarung = 5,
                    totalBatal = 2,
                    totalScanFollowUp = 8
                ),
                ExpeditionSummary(
                    name = "POS",
                    totalTransaksi = 75,
                    totalScan = 60,
                    sisa = 15,
                    jumlahKarung = 4,
                    totalBatal = 3,
                    totalScanFollowUp = 5
                ),
                ExpeditionSummary(
                    name = "J&T",
                    totalTransaksi = 45,
                    totalScan = 40,
                    sisa = 5,
                    jumlahKarung = 3,
                    totalBatal = 1,
                    totalScanFollowUp = 2
                )
            )
            
            Log.d(TAG, "✅ Loaded ${mockSummaries.size} expedition summaries")
            mockSummaries
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expedition summaries: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Ambil detail resi untuk expedisi tertentu
     * Sama dengan web app: get_expedition_detail_records
     */
    suspend fun getExpeditionDetailRecords(courierName: String, selectedDate: String): List<ResiDetail> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Getting expedition detail records for $courierName on $selectedDate")
            
            // MOCK implementation - dependency tidak tersedia
            // TODO: Implementasi real query:
            // SELECT * FROM tbl_resi 
            // WHERE couriername = ? AND DATE(created) = ?
            // ORDER BY created DESC
            
            // Mock data untuk testing
            val mockDetails = listOf(
                ResiDetail(
                    Resi = "JNE123456789",
                    resino = "JNE123456789",
                    orderno = "ORD001",
                    chanelsales = "Online",
                    couriername = courierName,
                    created = "2024-01-15 10:30:00",
                    datetrans = "2024-01-15",
                    flag = "NO",
                    cekfu = false,
                    nokarung = "1",
                    schedule = "ontime",
                    Keterangan = "Belum dikirim"
                ),
                ResiDetail(
                    Resi = "JNE123456790",
                    resino = "JNE123456790",
                    orderno = "ORD002",
                    chanelsales = "Offline",
                    couriername = courierName,
                    created = "2024-01-15 11:15:00",
                    datetrans = "2024-01-15",
                    flag = "YES",
                    cekfu = true,
                    nokarung = "1",
                    schedule = "ontime",
                    Keterangan = "Sudah dikirim"
                ),
                ResiDetail(
                    Resi = "JNE123456791",
                    resino = "JNE123456791",
                    orderno = "ORD003",
                    chanelsales = "Online",
                    couriername = courierName,
                    created = "2024-01-15 12:00:00",
                    datetrans = "2024-01-15",
                    flag = "NO",
                    cekfu = false,
                    nokarung = "2",
                    schedule = "late",
                    Keterangan = "Terlambat"
                )
            )
            
            Log.d(TAG, "✅ Loaded ${mockDetails.size} detail records for $courierName")
            mockDetails
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expedition detail records: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Update status resi (batal, confirm, toggle CEKFU)
     * Sama dengan web app actions
     */
    suspend fun updateResiStatus(resiNumber: String, action: String, value: Any? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Updating resi $resiNumber: $action = $value")
            
            // MOCK implementation - dependency tidak tersedia
            // TODO: Implementasi real update queries:
            // - Batal: UPDATE tbl_resi SET schedule = 'batal' WHERE resino = ?
            // - Confirm: UPDATE tbl_resi SET flag = 'YES' WHERE resino = ?
            // - Toggle CEKFU: UPDATE tbl_resi SET cekfu = ? WHERE resino = ?
            
            // Simulate network delay
            kotlinx.coroutines.delay(500)
            
            Log.d(TAG, "✅ Successfully updated resi $resiNumber")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating resi status: ${e.message}")
            false
        }
    }
}
