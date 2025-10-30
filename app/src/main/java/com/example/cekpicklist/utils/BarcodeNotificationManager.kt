package com.example.cekpicklist.utils

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * Notification Manager untuk Barcode Scanner
 * Mengelola notifikasi Toast yang konsisten dengan web app
 */
class BarcodeNotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BarcodeNotificationManager"
    }
    
    /**
     * Show success notification
     */
    fun showSuccess(message: String) {
        try {
            Toast.makeText(context, "✅ $message", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "✅ Success: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing success notification: ${e.message}", e)
        }
    }
    
    /**
     * Show error notification
     */
    fun showError(message: String) {
        try {
            Toast.makeText(context, "❌ $message", Toast.LENGTH_LONG).show()
            Log.d(TAG, "❌ Error: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing error notification: ${e.message}", e)
        }
    }
    
    /**
     * Show warning notification
     */
    fun showWarning(message: String) {
        try {
            Toast.makeText(context, "⚠️ $message", Toast.LENGTH_LONG).show()
            Log.d(TAG, "⚠️ Warning: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing warning notification: ${e.message}", e)
        }
    }
    
    /**
     * Show info notification
     */
    fun showInfo(message: String) {
        try {
            Toast.makeText(context, "ℹ️ $message", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "ℹ️ Info: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing info notification: ${e.message}", e)
        }
    }
    
    /**
     * Show loading notification (dismissible)
     */
    fun showLoading(message: String): Toast {
        return try {
            val toast = Toast.makeText(context, "⏳ $message", Toast.LENGTH_LONG)
            toast.show()
            Log.d(TAG, "⏳ Loading: $message")
            toast
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing loading notification: ${e.message}", e)
            Toast.makeText(context, "Error", Toast.LENGTH_SHORT)
        }
    }
    
    /**
     * Dismiss toast
     */
    fun dismissToast(toast: Toast?) {
        try {
            toast?.cancel()
            Log.d(TAG, "🔇 Toast dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error dismissing toast: ${e.message}", e)
        }
    }
    
    /**
     * Show validation error notifications (konsisten dengan web app)
     */
    fun showValidationError(errorType: BarcodeValidationError, details: String? = null) {
        val message = when (errorType) {
            BarcodeValidationError.EMPTY_INPUT -> "Nomor resi tidak boleh kosong."
            BarcodeValidationError.INVALID_FORMAT -> "Format barcode tidak valid: $details"
            BarcodeValidationError.EXPEDITION_KARUNG_NOT_SELECTED -> "Mohon pilih Expedisi dan No Karung terlebih dahulu."
            BarcodeValidationError.DUPLICATE_PROCESSED -> details ?: "Resi sudah diproses sebelumnya."
            BarcodeValidationError.NOT_FOUND_IN_EXPEDISI -> "Data resi $details tidak ditemukan di database."
            BarcodeValidationError.MISMATCH_EXPEDISI -> details ?: "Resi tidak sesuai dengan expedisi yang dipilih."
            BarcodeValidationError.NETWORK_ERROR -> "Gagal terhubung ke server. Periksa koneksi internet Anda atau coba lagi nanti."
            BarcodeValidationError.SERVER_ERROR -> "Terjadi kesalahan server: $details"
            BarcodeValidationError.UNKNOWN_ERROR -> "Terjadi kesalahan yang tidak diketahui. Silakan coba lagi."
        }
        
        showError(message)
    }
}

/**
 * Enum untuk jenis error validasi barcode
 */
enum class BarcodeValidationError {
    EMPTY_INPUT,
    INVALID_FORMAT,
    EXPEDITION_KARUNG_NOT_SELECTED,
    DUPLICATE_PROCESSED,
    NOT_FOUND_IN_EXPEDISI,
    MISMATCH_EXPEDISI,
    NETWORK_ERROR,
    SERVER_ERROR,
    UNKNOWN_ERROR
}
