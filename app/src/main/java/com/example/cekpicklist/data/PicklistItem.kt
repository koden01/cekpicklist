package com.example.cekpicklist.data

import java.util.UUID

data class PicklistItem(
    val id: String, // UUID dari Supabase
    val noPicklist: String, // no_picklist dari Supabase
    val articleId: String, // article_id dari Supabase
    val articleName: String, // article_name dari Supabase
    val size: String, // size dari Supabase
    val productId: String?, // product_id dari Supabase (nullable)
    val qtyPl: Int, // qty dari Supabase (planned quantity)
    val qtyScan: Int = 0, // scanned quantity, default 0
    val scan: Boolean = false, // status scan dari Supabase (true/false)
    val createdAt: String? = null, // created_at dari Supabase
    val warehouse: String? = null, // warehouse untuk item NA
    val tagStatus: String? = null, // tag_status untuk item NA
    val lastUpdated: Long = System.currentTimeMillis() // timestamp untuk cache incremental update
) {
    fun getQtyStatus(): QtyStatus {
        val status = when {
            // qtyPl=0, qtyScan=1 -> KUNING (semua item dengan qtyPl=0)
            qtyPl == 0 && qtyScan > 0 -> QtyStatus.YELLOW
            
            // qtyPl=2, qtyScan=1 -> MERAH (kurang dari rencana)
            qtyScan < qtyPl -> QtyStatus.RED
            
            // qtyPl=2, qtyScan=3 -> KUNING (lebih dari rencana)
            qtyScan > qtyPl -> QtyStatus.YELLOW
            
            // qtyPl=2, qtyScan=2 -> HIJAU (sesuai rencana, akan di-hide)
            qtyScan == qtyPl -> QtyStatus.GREEN
            
            else -> QtyStatus.GREEN
        }
        
        // Debug logging
        android.util.Log.d("PicklistItem", "🔥 getQtyStatus: Article=${articleName}, qtyPl=$qtyPl, qtyScan=$qtyScan, tagStatus=$tagStatus, Status=$status")
        
        return status
    }
    
    fun getQtyDifference(): Int {
        return qtyScan - qtyPl
    }
    
    fun getQtyStatusMessage(): String {
        return when (getQtyStatus()) {
            QtyStatus.RED -> "Kurang ${qtyPl - qtyScan} pcs"
            QtyStatus.YELLOW -> {
                when {
                    qtyPl == 0 -> "Tidak ada di picklist"
                    qtyScan > qtyPl -> "Lebih ${qtyScan - qtyPl} pcs"
                    else -> "Overscan"
                }
            }
            QtyStatus.GREEN -> "Sesuai"
        }
    }
    
    fun isComplete(): Boolean {
        // Item dianggap selesai jika qty_scan == qty_pl (sesuai dengan sistem baru)
        // Item dengan qty_pl = 0 (tidak ada di picklist) tidak pernah dianggap selesai
        val isComplete = qtyPl > 0 && qtyScan == qtyPl
        
        // Debug logging
        android.util.Log.d("PicklistItem", "🔥 isComplete: Article=${articleName} ${size}, qtyPl=$qtyPl, qtyScan=$qtyScan, isComplete=$isComplete")
        
        return isComplete
    }
    
    fun getProgressPercentage(): Int {
        return if (qtyPl > 0) {
            ((qtyScan.toFloat() / qtyPl.toFloat()) * 100).toInt()
        } else {
            // Untuk item yang tidak ada di picklist, progress = 0%
            0
        }
    }
    
    /**
     * Check if this item has NA values (unknown product/article)
     */
    fun isNAItem(): Boolean {
        return productId == "NA" || articleId == "NA" || articleName == "NA"
    }
    
    /**
     * Get warehouse and tag status info for NA items
     */
    fun getNAInfo(): String? {
        return if (isNAItem() && warehouse != null && tagStatus != null) {
            "$warehouse • $tagStatus"
        } else null
    }
    
    // Helper method untuk mendapatkan picklist number (untuk kompatibilitas)
    fun getPicklistNo(): String {
        return noPicklist
    }
}

enum class QtyStatus {
    RED,    // qty_scan < qty_pl (kurang dari yang direncanakan)
    YELLOW, // qty_scan > qty_pl ATAU qty_pl = 0 && qty_scan > 0 (lebih dari yang direncanakan atau tidak ada di picklist)
    GREEN   // qty_scan == qty_pl (sesuai)
}
