package com.example.cekpicklist.data

/**
 * Data class untuk summary expedisi (sama dengan web app)
 */
data class ExpeditionSummary(
    val name: String,
    val totalTransaksi: Int,
    val totalScan: Int,
    val sisa: Int,
    val jumlahKarung: Int,
    val totalBatal: Int,
    val totalScanFollowUp: Int
) {
    /**
     * Hitung persentase scan
     */
    val scanPercentage: Int
        get() = if (totalTransaksi > 0) (totalScan * 100 / totalTransaksi) else 0
    
    /**
     * Hitung persentase sisa
     */
    val sisaPercentage: Int
        get() = if (totalTransaksi > 0) (sisa * 100 / totalTransaksi) else 0
}
