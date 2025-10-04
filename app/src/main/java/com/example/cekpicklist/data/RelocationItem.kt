package com.example.cekpicklist.data

/**
 * Data class untuk item hasil lookup relocation
 */
data class RelocationItem(
    val epc: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val qty: Int,
    val productId: String,
    val productName: String,
    val brand: String,
    val category: String,
    val subCategory: String,
    val color: String,
    val gender: String,
    val warehouse: String,
    val tagStatus: String, // TAGGED atau MASTER
    val isValid: Boolean = true // true jika bukan NA
)

// Data classes untuk request/response lookup sudah tidak digunakan
// karena sekarang menggunakan endpoint /tag/products yang sama dengan MainActivity

/**
 * Data class untuk request update tag status
 */
data class RelocationUpdateRequest(
    val warehouse_id: String,
    val tag_status: String,
    val rfid_list: List<String>
)

/**
 * Data class untuk response update tag status
 */
data class RelocationUpdateResponse(
    val success: Boolean,
    val message: String,
    val updated_count: Int? = null
)

/**
 * Enum untuk tag status
 */
enum class TagStatus(val value: String, val displayName: String) {
    TAGGED("TAGGED", "TAGGED"),
    MASTER("MASTER", "MASTER")
}
