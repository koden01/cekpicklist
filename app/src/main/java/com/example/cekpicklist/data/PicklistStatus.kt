package com.example.cekpicklist.data

data class PicklistStatus(
    val picklistNumber: String,
    val isScanned: Boolean = false,
    val remainingQty: Int = 0,
    val totalQty: Int = 0,
    val scannedQty: Int = 0,
    val lastScanTime: String? = null,
    val overscanQty: Int = 0 // Jumlah item yang di-scan lebih dari yang direncanakan
)
