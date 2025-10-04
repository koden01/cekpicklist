package com.example.cekpicklist.data

/**
 * Minimal model untuk data hasil pemrosesan RFID yang dipakai oleh ScanViewModel.
 * Disesuaikan dengan field yang diakses di ScanViewModel.
 */
data class ProcessedRfidData(
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
    val tagStatus: String
)
