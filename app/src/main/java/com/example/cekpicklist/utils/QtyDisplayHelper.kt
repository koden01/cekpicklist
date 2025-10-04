package com.example.cekpicklist.utils

import com.example.cekpicklist.data.QtySummary
import com.example.cekpicklist.data.QtyUpdate
import com.example.cekpicklist.data.QtyStatusInfo
import com.example.cekpicklist.data.QtyStatus

/**
 * Helper class untuk menampilkan data qty di UI
 * Menggunakan poin 1, 2, 4, 5 dari ViewModel
 */
object QtyDisplayHelper {
    
    /**
     * POIN 1: Format summary statistics untuk ditampilkan
     */
    fun formatQtySummary(summary: QtySummary): String {
        return """
            📊 Summary Picklist:
            • Total Items: ${summary.totalItems}
            • Scanned Items: ${summary.scannedItems}
            • Completed Items: ${summary.completedItems}
            • Total Qty Planned: ${summary.totalQtyPl}
            • Total Qty Scanned: ${summary.totalQtyScan}
            • Remaining Qty: ${summary.remainingQty}
            • Progress: ${summary.progressPercentage}%
        """.trimIndent()
    }
    
    /**
     * POIN 2: Get color resource berdasarkan status
     */
    fun getStatusColor(status: QtyStatus): Int {
        return when (status) {
            QtyStatus.RED -> android.R.color.holo_red_light
            QtyStatus.YELLOW -> android.R.color.holo_orange_light
            QtyStatus.GREEN -> android.R.color.holo_green_light
        }
    }
    
    /**
     * POIN 2: Get status text color
     */
    fun getStatusTextColor(status: QtyStatus): Int {
        return when (status) {
            QtyStatus.RED -> android.R.color.white
            QtyStatus.YELLOW -> android.R.color.black
            QtyStatus.GREEN -> android.R.color.white
        }
    }
    
    /**
     * POIN 2: Get status icon
     */
    fun getStatusIcon(status: QtyStatus): String {
        return when (status) {
            QtyStatus.RED -> "🔴" // Kurang
            QtyStatus.YELLOW -> "🟡" // Overscan/Non-picklist
            QtyStatus.GREEN -> "🟢" // Sesuai
        }
    }
    
    /**
     * POIN 4: Format filtered items info
     */
    fun formatFilteredItemsInfo(totalItems: Int, filteredItems: Int): String {
        val completedItems = totalItems - filteredItems
        return "📋 Items: $filteredItems remaining, $completedItems completed"
    }
    
    /**
     * POIN 5: Format real-time qty update
     */
    fun formatQtyUpdate(update: QtyUpdate): String {
        val statusIcon = getStatusIcon(update.status)
        return """
            $statusIcon ${update.articleName}
            Qty: ${update.oldQtyScan} → ${update.newQtyScan} / ${update.qtyPl}
            Status: ${update.message}
        """.trimIndent()
    }
    
    /**
     * POIN 5: Get update animation type berdasarkan status
     */
    fun getUpdateAnimationType(status: QtyStatus): String {
        return when (status) {
            QtyStatus.RED -> "shake" // Animasi shake untuk kurang
            QtyStatus.YELLOW -> "pulse" // Animasi pulse untuk overscan
            QtyStatus.GREEN -> "fade_in" // Animasi fade in untuk sesuai
        }
    }
    
    /**
     * Format qty status info untuk display
     */
    fun formatQtyStatusInfo(info: QtyStatusInfo): String {
        val statusIcon = getStatusIcon(info.status)
        return """
            $statusIcon ${info.articleId}
            ${info.message}
            Progress: ${info.progressPercentage}%
            Complete: ${if (info.isComplete) "✅" else "⏳"}
        """.trimIndent()
    }
    
    /**
     * Get progress bar color berdasarkan progress percentage
     */
    fun getProgressBarColor(percentage: Int): Int {
        return when {
            percentage < 50 -> android.R.color.holo_red_light
            percentage < 80 -> android.R.color.holo_orange_light
            else -> android.R.color.holo_green_light
        }
    }
    
    /**
     * Format completion message
     */
    fun getCompletionMessage(summary: QtySummary): String {
        return when {
            summary.progressPercentage == 100 -> "🎉 Picklist selesai!"
            summary.progressPercentage >= 80 -> "🔥 Hampir selesai!"
            summary.progressPercentage >= 50 -> "📈 Progress baik!"
            else -> "⏳ Mulai scanning..."
        }
    }
}
