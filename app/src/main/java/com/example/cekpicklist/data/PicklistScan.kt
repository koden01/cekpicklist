package com.example.cekpicklist.data

/**
 * Data class untuk picklist scan record
 */
data class PicklistScan(
    val id: String,
    val noPicklist: String,
    val productId: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val epc: String,
    val notrans: String?,
    val createdAt: Long
)