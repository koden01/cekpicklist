package com.example.cekpicklist.utils

/**
 * Konfigurasi dasar untuk scanning RFID.
 * Nilai default dipilih aman agar bisa dikompilasi dan dijalankan tanpa device.
 */
data class RfidScanSettings(
    val powerLevel: Int = 1,  // **SELARASKAN**: Default power = 1 (sesuai uhf-uart-demo)
    val duplicateRemovalEnabled: Boolean = true,
    val gracePeriodMs: Long = 0L,
    val rssiThreshold: Int = -60  // **PERBAIKAN**: RSSI threshold lebih tinggi (-60 instead of -120)
)


