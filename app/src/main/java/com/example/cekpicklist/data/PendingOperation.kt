package com.example.cekpicklist.data

import android.os.Parcel
import android.os.Parcelable

/**
 * Pending Operation untuk offline support
 * MOCK IMPLEMENTATION - Dependency tidak tersedia
 * Mengikuti pola yang sama dengan azure-chinchilla-swoop
 */
data class PendingOperation(
    val id: String,
    val type: OperationType,
    val payload: OperationPayload,
    val timestamp: Long,
    val retries: Int = 0,
    val lastAttempt: Long = System.currentTimeMillis()
) : Parcelable {
    
    override fun describeContents(): Int = 0
    
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(type.name)
        dest.writeParcelable(payload, flags)
        dest.writeLong(timestamp)
        dest.writeInt(retries)
        dest.writeLong(lastAttempt)
    }
    
    companion object CREATOR : Parcelable.Creator<PendingOperation> {
        override fun createFromParcel(parcel: Parcel): PendingOperation {
            return PendingOperation(
                parcel.readString() ?: "",
                OperationType.valueOf(parcel.readString() ?: "SCAN"),
                parcel.readParcelable(OperationPayload::class.java.classLoader) ?: OperationPayload("", emptyMap()),
                parcel.readLong(),
                parcel.readInt(),
                parcel.readLong()
            )
        }
        
        override fun newArray(size: Int): Array<PendingOperation?> {
            return arrayOfNulls(size)
        }
    }
}

enum class OperationType {
    SCAN, CONFIRM, CANCEL, UPDATE
}

data class OperationPayload(
    val barcode: String,
    val metadata: Map<String, Any>
) : Parcelable {
    
    override fun describeContents(): Int = 0
    
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(barcode)
        // Note: Map<String, Any> tidak bisa di-serialize dengan Parcel
        // Untuk implementasi sederhana, kita skip metadata
    }
    
    companion object CREATOR : Parcelable.Creator<OperationPayload> {
        override fun createFromParcel(parcel: Parcel): OperationPayload {
            return OperationPayload(
                parcel.readString() ?: "",
                emptyMap() // Metadata tidak di-serialize
            )
        }
        
        override fun newArray(size: Int): Array<OperationPayload?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * Factory untuk membuat pending operations
 */
object PendingOperationFactory {
    
    fun createScanOperation(barcode: String, metadata: Map<String, Any> = emptyMap()): PendingOperation {
        return PendingOperation(
            id = generateId(),
            type = OperationType.SCAN,
            payload = OperationPayload(barcode, metadata),
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun createConfirmOperation(barcode: String, metadata: Map<String, Any> = emptyMap()): PendingOperation {
        return PendingOperation(
            id = generateId(),
            type = OperationType.CONFIRM,
            payload = OperationPayload(barcode, metadata),
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun createCancelOperation(barcode: String, metadata: Map<String, Any> = emptyMap()): PendingOperation {
        return PendingOperation(
            id = generateId(),
            type = OperationType.CANCEL,
            payload = OperationPayload(barcode, metadata),
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun createUpdateOperation(barcode: String, metadata: Map<String, Any> = emptyMap()): PendingOperation {
        return PendingOperation(
            id = generateId(),
            type = OperationType.UPDATE,
            payload = OperationPayload(barcode, metadata),
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun generateId(): String {
        return "op_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}