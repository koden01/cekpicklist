package com.example.cekpicklist.data

data class OutActivityItem(
    val epc: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val qty: Int,
    val productId: String,
    val brand: String,
    val category: String,
    val subCategory: String,
    val color: String,
    val gender: String,
    val warehouse: String,
    val tagStatus: String
) {
    fun getDisplayName(): String {
        return "$articleName $size"
    }
    
    fun getFullInfo(): String {
        return "$articleName $size - $brand $category"
    }
}
