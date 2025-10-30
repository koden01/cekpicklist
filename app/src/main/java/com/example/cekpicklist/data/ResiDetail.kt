package com.example.cekpicklist.data

/**
 * Data class untuk detail resi (sama dengan web app)
 */
data class ResiDetail(
    val Resi: String,
    val resino: String?,
    val orderno: String?,
    val chanelsales: String?,
    val couriername: String?,
    val created: String?,
    val datetrans: String?,
    val flag: String?,
    val cekfu: Boolean?,
    val nokarung: String?,
    val schedule: String?,
    val Keterangan: String?
) {
    /**
     * Status resi berdasarkan flag dan schedule
     */
    val status: String
        get() = when {
            schedule == "batal" -> "Batal"
            flag == "YES" -> "Sudah Kirim"
            flag == "NO" -> "Belum Kirim"
            else -> "Unknown"
        }
    
    /**
     * Status CEKFU
     */
    val cekfuStatus: String
        get() = when (cekfu) {
            true -> "Ya"
            false -> "Tidak"
            null -> "-"
        }
    
    /**
     * Warna status untuk UI
     */
    val statusColor: String
        get() = when (status) {
            "Batal" -> "#EF4444"
            "Sudah Kirim" -> "#10B981"
            "Belum Kirim" -> "#F59E0B"
            else -> "#6B7280"
        }
}
