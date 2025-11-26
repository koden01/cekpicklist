package com.example.cekpicklist.data.local.barcode

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BarcodeDao {

    @Query("SELECT * FROM tbl_resi")
    suspend fun getAllResi(): List<BarcodeResiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResiInternal(entities: List<BarcodeResiEntity>)

    @Query("DELETE FROM tbl_resi")
    suspend fun clearResi()

    @Query("DELETE FROM tbl_resi WHERE resi = :resi")
    suspend fun deleteResi(resi: String)

    @Query("DELETE FROM tbl_resi WHERE createdEpochMillis IS NOT NULL AND createdEpochMillis < :cutoff")
    suspend fun deleteResiOlderThan(cutoff: Long)

    @Transaction
    suspend fun replaceResi(entities: List<BarcodeResiEntity>) {
        clearResi()
        if (entities.isNotEmpty()) {
            upsertResiInternal(entities)
        }
    }

    @Query("SELECT * FROM tbl_expedisi")
    suspend fun getAllExpedisi(): List<BarcodeExpedisiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpedisiInternal(entities: List<BarcodeExpedisiEntity>)

    @Query("DELETE FROM tbl_expedisi")
    suspend fun clearExpedisi()

    @Query("DELETE FROM tbl_expedisi WHERE resino = :resino")
    suspend fun deleteExpedisi(resino: String)

    @Query("SELECT * FROM tbl_expedisi WHERE resino = :resino LIMIT 1")
    suspend fun getExpedisiByResino(resino: String): BarcodeExpedisiEntity?

    @Query("SELECT * FROM tbl_expedisi WHERE orderno = :orderno LIMIT 1")
    suspend fun getExpedisiByOrderno(orderno: String): BarcodeExpedisiEntity?

    // === Utility queries for reporting ===

    // Catatan: kolom di DB bernama 'deleted_at', bukan 'deletedAt'
    @Query("SELECT COUNT(DISTINCT resino) FROM tbl_expedisi WHERE deleted_at IS NULL AND created >= :todayIso AND flag = 'NO'")
    suspend fun countTodayUniqueResino(todayIso: String): Int

    // Untuk tbl_resi juga gunakan kolom 'deleted_at'
    @Query("SELECT COUNT(*) FROM tbl_resi WHERE deleted_at IS NULL AND created >= :todayIso AND schedule = 'ontime'")
    suspend fun countTodayResiScans(todayIso: String): Int

    @Transaction
    suspend fun replaceExpedisi(entities: List<BarcodeExpedisiEntity>) {
        clearExpedisi()
        if (entities.isNotEmpty()) {
            upsertExpedisiInternal(entities)
        }
    }
}




