package com.example.cekpicklist.data

/**
 * Data item untuk fitur Retur.
 * Menyimpan informasi hasil lookup EPC yang berstatus SOLD
 * sebelum dikembalikan menjadi TAGGED.
 */
data class ReturItem(
    val epc: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val productId: String,
    val brand: String,
    val category: String,
    val subCategory: String,
    val color: String,
    val gender: String,
    val sourceWarehouse: String,
    val currentTagStatus: String,
    val isValid: Boolean
)

data class ReturAggregatedItem(
    val articleId: String,
    val articleName: String,
    val size: String,
    val quantity: Int,
    val isValid: Boolean,
    val statusLabel: String,
    val sourceWarehouse: String
)

