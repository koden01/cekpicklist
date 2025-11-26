package com.example.cekpicklist.data.local.barcode

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.cekpicklist.api.BarcodeSupabaseService

@Entity(tableName = "tbl_resi")
data class BarcodeResiEntity(
    @PrimaryKey val resi: String,
    val created: String?,
    val createdEpochMillis: Long?, // Kolom lokal untuk cleanup (tidak ada di Supabase)
    val keterangan: String?,
    val nokarung: String?,
    val schedule: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    val version: Int?,
    @ColumnInfo(name = "last_modified_by") val lastModifiedBy: String?
) {
    fun toRecord(): BarcodeSupabaseService.BarcodeScanRecord {
        return BarcodeSupabaseService.BarcodeScanRecord(
            id = null,
            Resi = resi,
            created = created,
            Keterangan = keterangan,
            nokarung = nokarung,
            schedule = schedule,
            updated_at = updatedAt,
            deleted_at = deletedAt,
            version = version,
            last_modified_by = lastModifiedBy
        )
    }

    companion object {
        fun fromRecord(record: BarcodeSupabaseService.BarcodeScanRecord): BarcodeResiEntity {
            return BarcodeResiEntity(
                resi = record.Resi.trim().uppercase(),
                created = record.created,
                createdEpochMillis = record.created?.let { BarcodeRoomConverters.parseIsoToEpochMillis(it) },
                keterangan = record.Keterangan,
                nokarung = record.nokarung,
                schedule = record.schedule,
                updatedAt = record.updated_at,
                deletedAt = record.deleted_at,
                version = record.version,
                lastModifiedBy = record.last_modified_by
            )
        }
    }
}


