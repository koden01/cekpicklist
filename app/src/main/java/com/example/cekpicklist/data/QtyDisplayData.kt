package com.example.cekpicklist.data

/**
 * Data class untuk summary statistics qty
 * POIN 1: Summary Statistics untuk Qty
 */
data class QtySummary(
    val totalQtyPl: Int,           // Total quantity planned
    val totalQtyScan: Int,         // Total quantity scanned
    val remainingQty: Int,         // Remaining quantity
    val scannedItems: Int,         // Jumlah item yang sudah di-scan
    val completedItems: Int,       // Jumlah item yang sudah selesai
    val totalItems: Int,           // Total items dalam picklist
    val progressPercentage: Int    // Persentase completion (0-100)
)

/**
 * Data class untuk real-time qty updates
 * POIN 5: LiveData untuk Real-time Qty Updates
 */
data class QtyUpdate(
    val articleId: String,         // Article ID yang di-update
    val articleName: String,       // Article name
    val oldQtyScan: Int,           // Qty scan lama
    val newQtyScan: Int,           // Qty scan baru
    val qtyPl: Int,                // Qty planned
    val status: QtyStatus,         // Status baru (RED/YELLOW/GREEN)
    val message: String,           // Message status
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data class untuk qty status dengan warna
 * POIN 2: Status Colors untuk Qty
 */
data class QtyStatusInfo(
    val articleId: String,
    val status: QtyStatus,
    val message: String,
    val progressPercentage: Int,
    val isComplete: Boolean
)
