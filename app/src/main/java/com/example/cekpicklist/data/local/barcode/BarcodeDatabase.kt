package com.example.cekpicklist.data.local.barcode

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BarcodeResiEntity::class, BarcodeExpedisiEntity::class],
    version = 2, // Increased from 1 to 2 due to @ColumnInfo mapping changes
    exportSchema = false
)
abstract class BarcodeDatabase : RoomDatabase() {

    abstract fun barcodeDao(): BarcodeDao

    companion object {
        @Volatile
        private var INSTANCE: BarcodeDatabase? = null

        fun getInstance(context: Context): BarcodeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BarcodeDatabase::class.java,
                    "barcode_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}


