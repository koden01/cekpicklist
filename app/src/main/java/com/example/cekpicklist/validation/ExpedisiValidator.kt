package com.example.cekpicklist.validation

import android.content.Context
import android.util.Log
import com.example.cekpicklist.api.BarcodeExpedisiService
import com.example.cekpicklist.api.BarcodeSupabaseService
import com.example.cekpicklist.repository.EnhancedRepository
import com.example.cekpicklist.utils.BarcodeToastManager
import com.example.cekpicklist.utils.BarcodeAudioManager
import com.example.cekpicklist.utils.BarcodeNotificationManager
import com.example.cekpicklist.utils.BarcodeValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Expedisi Validator dengan Local-First strategy
 * Mengikuti logika validasi yang sama dengan azure-chinchilla-swoop
 */
class ExpedisiValidator(
    private val context: Context,
    private val enhancedRepository: EnhancedRepository? = null,
    private val barcodeExpedisiService: BarcodeExpedisiService? = null,
    private val audioManager: BarcodeAudioManager? = null,
    private val notificationManager: BarcodeNotificationManager? = null
) {
    
    companion object {
        private const val TAG = "ExpedisiValidator"
    }
    
    /**
     * Validation status enum
     */
    enum class ValidationStatus {
        OK,
        DUPLICATE_PROCESSED,
        MISMATCH_EXPEDISI,
        NOT_FOUND_IN_EXPEDISI,
        INVALID_FORMAT
    }
    
    /**
     * Validation result
     */
    data class ValidationResult(
        val status: ValidationStatus,
        val message: String? = null,
        val expedisiRecord: ExpedisiRecord? = null,
        val resiDetails: ResiDetails? = null,
        val actualCourierName: String? = null,
        val actualSchedule: String? = null,
        val wasFlagNo: Boolean = false
    )
    
    /**
     * Expedisi record data class
     */
    data class ExpedisiRecord(
        val resino: String,
        val orderno: String?,
        val chanelsales: String?,
        val couriername: String?,
        val created: String,
        val datetrans: String?,
        val flag: String?,
        val cekfu: Boolean?
    )
    
    /**
     * Resi details data class
     */
    data class ResiDetails(
        val Resi: String,
        val created: String?,
        val Keterangan: String?,
        val nokarung: String?,
        val schedule: String?
    )
    
    /**
     * Validate barcode/resi dengan logika yang sama dengan web app
     */
    suspend fun validateBarcode(
        barcode: String,
        selectedExpedisi: String? = null,
        selectedKarung: String? = null
    ): ValidationResult = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "🔍 Validating barcode: $barcode, expedisi: $selectedExpedisi, karung: $selectedKarung")
        
        try {
            val trimmedBarcode = barcode.trim()
            
            // 1. Check expedition and karung selection
            if (selectedExpedisi.isNullOrEmpty() || selectedKarung.isNullOrEmpty()) {
                audioManager?.playFailureBeep()
                BarcodeToastManager.showError(context, "Mohon pilih Expedisi dan No Karung terlebih dahulu.")
                return@withContext ValidationResult(
                    status = ValidationStatus.INVALID_FORMAT,
                    message = "Mohon pilih Expedisi dan No Karung terlebih dahulu.",
                    actualCourierName = normalizeExpeditionName(selectedExpedisi)
                )
            }
            
            // 2. Check if already processed (in tbl_resi)
            val resiDetails = checkResiInDatabase(trimmedBarcode)
            if (resiDetails != null) {
                val processedDate = resiDetails.created?.let { 
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .format(Date(it))
                } ?: "Tidak Diketahui"
                
                val message = if (resiDetails.schedule == "batal") {
                    "BATAL! Resi ini sudah dibatalkan pada $processedDate."
                } else {
                    val keterangan = resiDetails.Keterangan ?: "Tidak Diketahui"
                    val nokarung = resiDetails.nokarung ?: "Tidak Diketahui"
                    "DOUBLE! Resi ini $keterangan sudah diproses pada $processedDate di karung $nokarung."
                }
                
                audioManager?.playDoubleBeep()
                BarcodeToastManager.showError(context, message)
                
                return@withContext ValidationResult(
                    status = ValidationStatus.DUPLICATE_PROCESSED,
                    message = message,
                    resiDetails = resiDetails,
                    actualCourierName = normalizeExpeditionName(selectedExpedisi)
                )
            }
            
            // 3. Check in tbl_expedisi
            val expedisiRecord = checkExpedisiInDatabase(trimmedBarcode)
            if (expedisiRecord == null) {
                audioManager?.playFailureBeep()
                BarcodeToastManager.showError(context, "Data resi $trimmedBarcode tidak ditemukan di database.")
                return@withContext ValidationResult(
                    status = ValidationStatus.NOT_FOUND_IN_EXPEDISI,
                    message = "Data resi $trimmedBarcode tidak ditemukan di database.",
                    actualCourierName = normalizeExpeditionName(selectedExpedisi)
                )
            }
            
            // 4. Check expedition mismatch
            val normalizedExpedisiCourierName = normalizeExpeditionName(expedisiRecord.couriername)
            val normalizedSelectedExpedition = normalizeExpeditionName(selectedExpedisi)
            
            if (normalizedExpedisiCourierName != normalizedSelectedExpedition) {
                val message = "Resi ini terdaftar untuk ekspedisi ${expedisiRecord.couriername}, bukan $selectedExpedisi."
                audioManager?.playFailureBeep()
                BarcodeToastManager.showError(context, message)
                return@withContext ValidationResult(
                    status = ValidationStatus.MISMATCH_EXPEDISI,
                    message = message,
                    expedisiRecord = expedisiRecord,
                    actualCourierName = normalizeExpeditionName(expedisiRecord.couriername)
                )
            }
            
            // 5. Determine schedule based on expedisi created date
            val actualSchedule = determineSchedule(expedisiRecord.created)
            val actualCourierName = normalizeExpeditionName(expedisiRecord.couriername)
            val wasFlagNo = expedisiRecord.flag == "NO"
            
            Log.d(TAG, "✅ Validation successful: $trimmedBarcode")
            
            // Play success beep and show success notification
            audioManager?.playSuccessBeep()
            BarcodeToastManager.showSuccess(context, "Resi $trimmedBarcode berhasil divalidasi")
            
            return@withContext ValidationResult(
                status = ValidationStatus.OK,
                expedisiRecord = expedisiRecord,
                actualCourierName = actualCourierName,
                actualSchedule = actualSchedule,
                wasFlagNo = wasFlagNo
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error validating barcode: ${e.message}", e)
            audioManager?.playFailureBeep()
            BarcodeToastManager.showError(context, "Terjadi kesalahan: ${e.message}")
            return@withContext ValidationResult(
                status = ValidationStatus.INVALID_FORMAT,
                message = "Terjadi kesalahan validasi: ${e.message}",
                actualCourierName = normalizeExpeditionName(selectedExpedisi)
            )
        }
    }
    
    /**
     * Check if resi exists in tbl_resi
     */
    private suspend fun checkResiInDatabase(resi: String): ResiDetails? = withContext(Dispatchers.IO) {
        try {
            // Gunakan EnhancedRepository untuk Local-First strategy
            val resiRecord = if (enhancedRepository != null) {
                enhancedRepository.getBarcodeResiRecords().find { it.Resi == resi }
            } else {
                barcodeExpedisiService?.checkResiInDatabase(resi)
            }
            
            when (resiRecord) {
                is BarcodeSupabaseService.BarcodeScanRecord -> {
                    ResiDetails(
                        Resi = resiRecord.Resi,
                        created = resiRecord.created,
                        Keterangan = resiRecord.Keterangan,
                        nokarung = resiRecord.nokarung,
                        schedule = resiRecord.schedule
                    )
                }
                is BarcodeExpedisiService.ResiRecord -> {
                    ResiDetails(
                        Resi = resiRecord.Resi,
                        created = resiRecord.created,
                        Keterangan = resiRecord.Keterangan,
                        nokarung = resiRecord.nokarung,
                        schedule = resiRecord.schedule
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking resi in database: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if resi exists in tbl_expedisi
     */
    private suspend fun checkExpedisiInDatabase(resi: String): ExpedisiRecord? = withContext(Dispatchers.IO) {
        try {
            // Gunakan EnhancedRepository untuk Local-First strategy
            val expedisiRecord = if (enhancedRepository != null) {
                enhancedRepository.getBarcodeExpedisiRecords().find { it.resino == resi }
            } else {
                barcodeExpedisiService?.checkExpedisiInDatabase(resi)
            }
            
            when (expedisiRecord) {
                is BarcodeSupabaseService.BarcodeSessionRecord -> {
                    ExpedisiRecord(
                        resino = expedisiRecord.resino ?: "",
                        orderno = expedisiRecord.orderno ?: "",
                        chanelsales = expedisiRecord.chanelsales ?: "",
                        couriername = expedisiRecord.couriername ?: "",
                        created = expedisiRecord.created ?: "",
                        datetrans = expedisiRecord.datetrans ?: "",
                        flag = expedisiRecord.flag,
                        cekfu = expedisiRecord.cekfu
                    )
                }
                is BarcodeExpedisiService.ExpedisiRecord -> {
                    ExpedisiRecord(
                        resino = expedisiRecord.resino,
                        orderno = expedisiRecord.orderno,
                        chanelsales = expedisiRecord.chanelsales,
                        couriername = expedisiRecord.couriername,
                        created = expedisiRecord.created,
                        datetrans = expedisiRecord.datetrans,
                        flag = expedisiRecord.flag,
                        cekfu = expedisiRecord.cekfu.toBoolean()
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking expedisi in database: ${e.message}", e)
            null
        }
    }
    
    /**
     * Determine schedule based on created date
     */
    private fun determineSchedule(createdDate: String?): String {
        return try {
            if (createdDate.isNullOrEmpty()) return "ontime"
            
            val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(createdDate)
            val today = Date()
            
            // Check if created date is today
            val isToday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(created) == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)
            
            if (isToday) "ontime" else "late"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error determining schedule: ${e.message}", e)
            "ontime" // Default to ontime
        }
    }
    
    /**
     * Normalize expedition name (mengikuti pola web app)
     */
    private fun normalizeExpeditionName(expeditionName: String?): String {
        return expeditionName?.uppercase()?.trim() ?: ""
    }
    
}
