package com.example.cekpicklist.data.local.barcode

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cekpicklist.api.BarcodeSupabaseService

@Entity(
    tableName = "tbl_expedisi",
    indices = [Index(value = ["orderno"], unique = false)]
)
data class BarcodeExpedisiEntity(
    @PrimaryKey val resino: String,
    val orderno: String,
    val datetrans: String,
    val chanelsales: String?,
    val couriername: String?,
    val created: String?,
    val flag: String,
    val cekfu: Boolean,
    @ColumnInfo(name = "update_at") val updateAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    val version: Int?,
    @ColumnInfo(name = "last_modified_by") val lastModifiedBy: String?
) {
    fun toRecord(): BarcodeSupabaseService.BarcodeSessionRecord {
        return BarcodeSupabaseService.BarcodeSessionRecord(
            id = null,
            orderno = orderno,
            datetrans = datetrans,
            chanelsales = chanelsales,
            couriername = couriername,
            resino = resino,
            created = created,
            flag = flag,
            cekfu = cekfu,
            update_at = updateAt,
            deleted_at = deletedAt,
            version = version,
            last_modified_by = lastModifiedBy
        )
    }

    companion object {
        fun fromRecord(record: BarcodeSupabaseService.BarcodeSessionRecord): BarcodeExpedisiEntity {
            return BarcodeExpedisiEntity(
                resino = record.resino.trim().uppercase(),
                orderno = record.orderno.trim(),
                datetrans = record.datetrans,
                chanelsales = record.chanelsales,
                couriername = record.couriername,
                created = record.created,
                flag = record.flag,
                cekfu = record.cekfu,
                updateAt = record.update_at,
                deletedAt = record.deleted_at,
                version = record.version,
                lastModifiedBy = record.last_modified_by
            )
        }
    }
}


