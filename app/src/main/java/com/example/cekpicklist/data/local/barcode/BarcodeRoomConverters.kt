package com.example.cekpicklist.data.local.barcode

import java.time.Instant
import java.time.OffsetDateTime

object BarcodeRoomConverters {
    fun parseIsoToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}









