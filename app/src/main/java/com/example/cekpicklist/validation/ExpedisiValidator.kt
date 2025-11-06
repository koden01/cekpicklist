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
     * Validate barcode/resi dengan urutan validasi:
     * 1. Scan resi (input)
     * 2. Cek Duplicate (jika duplikat, toast + sound, berhenti)
     * 3. Cek di cache tbl_expedisi (flag=NO) (jika tidak ada, toast + sound, berhenti)
     * 4. Cek expedisi mismatch (jika tidak sesuai, toast + sound, berhenti)
     * 5. VALIDASI OK
     */
    suspend fun validateBarcode(
        barcode: String,
        selectedExpedisi: String? = null,
        selectedKarung: String? = null
    ): ValidationResult = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "🔍 Validating barcode: $barcode, expedisi: $selectedExpedisi, karung: $selectedKarung")
        
        try {
            val trimmedBarcode = barcode.trim()
            
            // 1. Check expedition and karung selection (VALIDASI PENTING: HARUS DIPILIH DULU)
            val expedisiError = when {
                selectedExpedisi.isNullOrEmpty() -> "Mohon pilih Expedisi terlebih dahulu."
                selectedExpedisi == "Pilih Expedisi" -> "Mohon pilih Expedisi terlebih dahulu."
                else -> null
            }
            
            val karungError = when {
                selectedKarung.isNullOrEmpty() -> "Mohon pilih No Karung terlebih dahulu."
                selectedKarung == "Pilih Karung" -> "Mohon pilih No Karung terlebih dahulu."
                else -> null
            }
            
            if (expedisiError != null || karungError != null) {
                val errorMessage = when {
                    expedisiError != null && karungError != null -> "Mohon pilih Expedisi dan No Karung terlebih dahulu."
                    expedisiError != null -> expedisiError
                    karungError != null -> karungError
                    else -> "Mohon pilih Expedisi dan No Karung terlebih dahulu."
                }
                
                Log.w(TAG, "🛑 [VALIDASI BERHENTI] $errorMessage - proses berhenti, data TIDAK disimpan")
                return@withContext ValidationResult(
                    status = ValidationStatus.INVALID_FORMAT,
                    message = errorMessage,
                    actualCourierName = normalizeExpeditionName(selectedExpedisi ?: "")
                )
            }
            
            // 2. Check if already processed (in tbl_resi) - Cek Duplicate
            // **CEK DUPLICATE DI SEMUA DATA CACHE tbl_resi** (4 hari terakhir, tidak hanya hari ini)
            // Tidak query langsung ke Supabase untuk performa cepat
            val resiDetails = checkResiInDatabase(trimmedBarcode)
            if (resiDetails != null) {
                val processedDate = resiDetails.created?.let { createdStr ->
                    try {
                        // Support format dengan offset (+00:00) dan format dengan Z
                        // Coba parse sebagai OffsetDateTime dulu, jika gagal baru parse sebagai Instant
                        val instant = try {
                            java.time.OffsetDateTime.parse(createdStr).toInstant()
                        } catch (_: Exception) {
                            java.time.Instant.parse(createdStr)
                        }
                        val date = Date.from(instant)
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                    } catch (e: Exception) {
                        // Fallback: tampilkan original string jika tidak bisa diparse
                        Log.w(TAG, "⚠️ Failed to parse created for schedule: $createdStr, ${e.message}")
                        createdStr
                    }
                } ?: "Tidak Diketahui"
                
                val message = if (resiDetails.schedule == "batal") {
                    "BATAL! Resi ini sudah dibatalkan pada $processedDate."
                } else {
                    val keterangan = resiDetails.Keterangan ?: "Tidak Diketahui"
                    val nokarung = resiDetails.nokarung ?: "Tidak Diketahui"
                    "DOUBLE! Resi ini $keterangan sudah diproses pada $processedDate di karung $nokarung."
                }
                
                // UI feedback (sound/toast) ditangani di layer UI (observer)
                
                return@withContext ValidationResult(
                    status = ValidationStatus.DUPLICATE_PROCESSED,
                    message = message,
                    resiDetails = resiDetails,
                    actualCourierName = normalizeExpeditionName(selectedExpedisi)
                )
            }
            
            // 3. Check in tbl_expedisi (cache, flag=NO) - menggunakan cache-first
            val expedisiRecord = checkExpedisiInDatabase(trimmedBarcode)
            if (expedisiRecord == null) {
                // UI feedback (sound/toast) ditangani di layer UI (observer)
                Log.w(TAG, "🛑 [VALIDASI BERHENTI] Resi '$trimmedBarcode' NOT_FOUND - proses berhenti, data TIDAK disimpan")
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
                // UI feedback (sound/toast) ditangani di layer UI (observer)
                return@withContext ValidationResult(
                    status = ValidationStatus.MISMATCH_EXPEDISI,
                    message = message,
                    expedisiRecord = expedisiRecord,
                    actualCourierName = normalizeExpeditionName(expedisiRecord.couriername)
                )
            }
            
            // 5. Determine schedule based on expedisi created date - VALIDASI OK
            val actualSchedule = determineSchedule(expedisiRecord.created)
            val actualCourierName = normalizeExpeditionName(expedisiRecord.couriername)
            val wasFlagNo = expedisiRecord.flag == "NO"
            
            Log.d(TAG, "✅ Validation successful: $trimmedBarcode")
            
            // UI feedback (sound/toast) ditangani di layer UI (observer)
            
            return@withContext ValidationResult(
                status = ValidationStatus.OK,
                expedisiRecord = expedisiRecord,
                actualCourierName = actualCourierName,
                actualSchedule = actualSchedule,
                wasFlagNo = wasFlagNo
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error validating barcode: ${e.message}", e)
            // UI feedback (sound/toast) ditangani di layer UI (observer)
            return@withContext ValidationResult(
                status = ValidationStatus.INVALID_FORMAT,
                message = "Terjadi kesalahan validasi: ${e.message}",
                actualCourierName = normalizeExpeditionName(selectedExpedisi)
            )
        }
    }
    
    /**
     * Check if resi exists in tbl_resi
     * **PENTING**: Menggunakan cache-first strategy (tidak query langsung ke Supabase)
     * - Cache tbl_resi berisi data 4 hari terakhir
     * - Fast lookup dari memory (ConcurrentHashMap)
     */
    private suspend fun checkResiInDatabase(resi: String): ResiDetails? = withContext(Dispatchers.IO) {
        try {
            // Gunakan EnhancedRepository untuk Local-First strategy (cache-first)
            // getBarcodeResiRecords() akan:
            // 1. Cek cache dulu (BarcodeCacheManager.getAllResiRecords())
            // 2. Jika cache valid, langsung return dari cache
            // 3. Jika cache expired/kosong, baru fetch dari Supabase dan update cache
            val resiRecord = if (enhancedRepository != null) {
                enhancedRepository.getBarcodeResiRecords().find { it.Resi == resi }
            } else {
                // Fallback: query langsung via service (jarang digunakan)
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
     * Cek apakah created date adalah hari ini
     */
    private fun isCreatedToday(createdDate: String?): Boolean {
        if (createdDate.isNullOrEmpty()) return false
        return try {
            // Support multiple ISO 8601 formats (dari Supabase):
            // Format 1: "2024-11-01T10:30:00.000Z" (dengan Z)
            // Format 2: "2025-10-29T09:15:26.678+00:00" (dengan offset)
            // Format 3: "2025-11-01T08:31:14" (tanpa timezone - perlu dianggap sebagai UTC)
            val created = if (createdDate.contains("T")) {
                // Coba parse dengan berbagai strategi
                val instant = try {
                    // Coba parse sebagai OffsetDateTime (untuk format dengan +00:00 atau -00:00)
                    java.time.OffsetDateTime.parse(createdDate).toInstant()
                } catch (_: Exception) {
                    try {
                        // Coba parse sebagai Instant (untuk format dengan Z)
                        java.time.Instant.parse(createdDate)
                    } catch (_: Exception) {
                        // Fallback: parse sebagai LocalDateTime dan konversi ke Instant (untuk format tanpa timezone)
                        // Anggap sebagai UTC untuk konsistensi dengan Supabase
                        java.time.LocalDateTime.parse(createdDate)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toInstant()
                    }
                }
                Date.from(instant)
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(createdDate)
                    ?: return false
            }
            val today = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateFormat.format(created) == dateFormat.format(today)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing created date: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if resi exists in tbl_expedisi (cache-first, flag=NO)
     * Menggunakan cache untuk performa cepat
     */
    private suspend fun checkExpedisiInDatabase(resi: String): ExpedisiRecord? = withContext(Dispatchers.IO) {
        try {
            // Gunakan EnhancedRepository untuk Local-First strategy (cache-first)
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
            
            // Support multiple ISO 8601 formats:
            // Format 1: "2024-11-01T10:30:00.000Z" (dengan Z)
            // Format 2: "2025-10-29T09:15:26.678+00:00" (dengan offset)
            // Format 3: "2025-11-01T08:31:14" (tanpa timezone - perlu dianggap sebagai UTC atau local time)
            val instant = if (createdDate.contains("T")) {
                try {
                    // Coba parse sebagai OffsetDateTime (untuk format dengan +00:00 atau -00:00)
                    java.time.OffsetDateTime.parse(createdDate).toInstant()
                } catch (_: Exception) {
                    try {
                        // Coba parse sebagai Instant (untuk format dengan Z)
                        java.time.Instant.parse(createdDate)
                    } catch (_: Exception) {
                        // Fallback: parse sebagai LocalDateTime dan konversi ke Instant (untuk format tanpa timezone)
                        // Anggap sebagai UTC untuk konsistensi dengan Supabase
                        java.time.LocalDateTime.parse(createdDate)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toInstant()
                    }
                }
            } else {
                // Fallback ke SimpleDateFormat untuk format lama
                val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(createdDate)
                parsed?.toInstant() ?: throw IllegalArgumentException("Cannot parse date: $createdDate")
            }
            val created = Date.from(instant)
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
