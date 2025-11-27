package com.example.cekpicklist.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.cekpicklist.api.BarcodeSupabaseService
import com.example.cekpicklist.data.local.barcode.BarcodeDao
import com.example.cekpicklist.data.local.barcode.BarcodeDatabase
import com.example.cekpicklist.data.local.barcode.BarcodeExpedisiEntity
import com.example.cekpicklist.data.local.barcode.BarcodeResiEntity
import com.example.cekpicklist.data.local.barcode.BarcodeRoomConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache manager yang menyimpan data barcode di memory + Room database.
 * - Room menjaga data tetap persisten (sesuai permintaan pengguna)
 * - Struktur in-memory memastikan lookup tetap cepat seperti implementasi lama
 */
object BarcodeCacheManager {

    private const val TAG = "BarcodeCacheManager"
    private const val PREFS_NAME = "barcode_cache_prefs"
    private const val KEY_LAST_RESI_UPDATE = "last_resi_cache_update"
    private const val KEY_LAST_EXPEDISI_UPDATE = "last_expedisi_cache_update"
    private const val KEY_LAST_COURIERS_UPDATE = "last_couriers_cache_update"
    private const val KEY_LAST_RESI_SYNC_TIME = "last_resi_sync_time"
    private const val KEY_LAST_EXPEDISI_SYNC_TIME = "last_expedisi_sync_time"
    private const val KEY_LAST_RESI_UPDATED_AT = "last_resi_updated_at"
    private const val KEY_LAST_EXPEDISI_UPDATE_AT = "last_expedisi_update_at"
    private const val KEY_LAST_CLEANUP_TIME = "last_cleanup_time"
    private const val RESI_WINDOW_DAYS = 6L
    private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()

    private val processedResiSet = ConcurrentHashMap.newKeySet<String>()
    private val resiRecordsCache = ConcurrentHashMap<String, BarcodeSupabaseService.BarcodeScanRecord>()
    private val processedResinoSet = ConcurrentHashMap.newKeySet<String>()
    private val expedisiRecordsCache = ConcurrentHashMap<String, BarcodeSupabaseService.BarcodeSessionRecord>()
    private val expedisiByOrdernoCache = ConcurrentHashMap<String, BarcodeSupabaseService.BarcodeSessionRecord>()
    private var courierNamesCache: List<String> = emptyList()

    private var lastResiCacheUpdate: Long = 0
    private var lastExpedisiCacheUpdate: Long = 0
    private var lastCouriersCacheUpdate: Long = 0
    private var lastResiSyncTime: Long = 0
    private var lastExpedisiSyncTime: Long = 0
    private var lastResiUpdatedAt: String? = null
    private var lastExpedisiUpdateAt: String? = null
    private var lastCleanupTime: Long = 0

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var barcodeDao: BarcodeDao
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        barcodeDao = BarcodeDatabase.getInstance(appContext).barcodeDao()
        loadMetadata()
        scope.launch { loadFromRoom() }
        initialized = true
        Log.d(TAG, "✅ Initialized with Room backend")
    }

    private fun loadMetadata() {
        lastResiCacheUpdate = sharedPreferences.getLong(KEY_LAST_RESI_UPDATE, 0)
        lastExpedisiCacheUpdate = sharedPreferences.getLong(KEY_LAST_EXPEDISI_UPDATE, 0)
        lastCouriersCacheUpdate = sharedPreferences.getLong(KEY_LAST_COURIERS_UPDATE, 0)
        lastResiSyncTime = sharedPreferences.getLong(KEY_LAST_RESI_SYNC_TIME, 0)
        lastExpedisiSyncTime = sharedPreferences.getLong(KEY_LAST_EXPEDISI_SYNC_TIME, 0)
        lastResiUpdatedAt = sharedPreferences.getString(KEY_LAST_RESI_UPDATED_AT, null)
        lastExpedisiUpdateAt = sharedPreferences.getString(KEY_LAST_EXPEDISI_UPDATE_AT, null)
        lastCleanupTime = sharedPreferences.getLong(KEY_LAST_CLEANUP_TIME, 0)
    }

    private fun persistMetadata() {
        sharedPreferences.edit()
            .putLong(KEY_LAST_RESI_UPDATE, lastResiCacheUpdate)
            .putLong(KEY_LAST_EXPEDISI_UPDATE, lastExpedisiCacheUpdate)
            .putLong(KEY_LAST_COURIERS_UPDATE, lastCouriersCacheUpdate)
            .putLong(KEY_LAST_RESI_SYNC_TIME, lastResiSyncTime)
            .putLong(KEY_LAST_EXPEDISI_SYNC_TIME, lastExpedisiSyncTime)
            .putString(KEY_LAST_RESI_UPDATED_AT, lastResiUpdatedAt)
            .putString(KEY_LAST_EXPEDISI_UPDATE_AT, lastExpedisiUpdateAt)
            .putLong(KEY_LAST_CLEANUP_TIME, lastCleanupTime)
            .apply()
    }

    private suspend fun loadFromRoom() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            val resiEntities = barcodeDao.getAllResi()
            val expedisiEntities = barcodeDao.getAllExpedisi()

            processedResiSet.clear()
            resiRecordsCache.clear()
            processedResinoSet.clear()
            expedisiRecordsCache.clear()
            expedisiByOrdernoCache.clear()

            resiEntities.forEach { entity ->
                val record = entity.toRecord()
                val normalized = normalize(record.Resi)
                processedResiSet.add(normalized)
                resiRecordsCache[normalized] = record
                trackResiUpdatedAt(record.updated_at ?: record.created)
            }

            expedisiEntities.forEach { entity ->
                val record = entity.toRecord()
                val normalized = normalize(record.resino)
                processedResinoSet.add(normalized)
                expedisiRecordsCache[normalized] = record
                if (record.orderno.isNotEmpty()) {
                    expedisiByOrdernoCache[record.orderno] = record
                }
                trackExpedisiUpdatedAt(record.update_at ?: record.created)
            }

            courierNamesCache = getUniqueCourierNamesFromCache()
            Log.d(TAG, "📦 Loaded ${resiRecordsCache.size} resi & ${expedisiRecordsCache.size} expedisi from Room")
        }
    }

    fun isResiCacheValid(): Boolean = resiRecordsCache.isNotEmpty()
    fun isExpedisiCacheValid(): Boolean = expedisiRecordsCache.isNotEmpty()
    fun isCouriersCacheValid(): Boolean = courierNamesCache.isNotEmpty()

    /**
     * Hitung jumlah unik resino HARI INI dari Room (tbl_expedisi).
     * todayIso dalam format "YYYY-MM-DD" sesuai kolom created.
     */
    suspend fun getTodayUniqueResinoFromRoom(todayIso: String): Int = withContext(Dispatchers.IO) {
        try {
            barcodeDao.countTodayUniqueResino(todayIso)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error counting today unique resino from Room: ${e.message}", e)
            0
        }
    }

    /**
     * Hitung total scan hari ini dari Room (tbl_resi), schedule = ontime/batal.
     * todayIso dalam format "YYYY-MM-DD".
     */
    suspend fun getTodayResiScanCountFromRoom(todayIso: String): Int = withContext(Dispatchers.IO) {
        try {
            barcodeDao.countTodayResiScans(todayIso)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error counting today resi scans from Room: ${e.message}", e)
            0
        }
    }

    suspend fun initializeCache(
        resiRecords: List<BarcodeSupabaseService.BarcodeScanRecord>,
        expedisiRecords: List<BarcodeSupabaseService.BarcodeSessionRecord>
    ) {
        // Optimasi: Gunakan Dispatchers.Default untuk operasi CPU-bound (filter/map)
        val filteredResi = withContext(Dispatchers.Default) {
            filterResiRecords(resiRecords)
        }
        val filteredExpedisi = withContext(Dispatchers.Default) {
            filterExpedisiRecords(expedisiRecords)
        }
        
        cacheMutex.withLock {

            processedResiSet.clear()
            resiRecordsCache.clear()
            processedResinoSet.clear()
            expedisiRecordsCache.clear()
            expedisiByOrdernoCache.clear()

            filteredResi.forEach { storeResiInMemory(it) }
            filteredExpedisi.forEach { storeExpedisiInMemory(it) }

            courierNamesCache = getUniqueCourierNamesFromCache()
            lastCouriersCacheUpdate = System.currentTimeMillis()
            lastResiCacheUpdate = lastCouriersCacheUpdate
            lastExpedisiCacheUpdate = lastCouriersCacheUpdate
            persistMetadata()

            scope.launch {
                // Optimasi: Gunakan Dispatchers.Default untuk operasi CPU-bound (map conversion)
                val resiEntities = withContext(Dispatchers.Default) {
                    filteredResi.map { BarcodeResiEntity.fromRecord(it) }
                }
                val expedisiEntities = withContext(Dispatchers.Default) {
                    filteredExpedisi.map { BarcodeExpedisiEntity.fromRecord(it) }
                }
                // Operasi database tetap di IO dispatcher (scope sudah menggunakan Dispatchers.IO)
                barcodeDao.replaceResi(resiEntities)
                barcodeDao.replaceExpedisi(expedisiEntities)
            }

            Log.d(TAG, "✅ Cache initialized: ${filteredResi.size} resi, ${filteredExpedisi.size} expedisi")
        }
    }

    suspend fun refreshCache(
        resiRecords: List<BarcodeSupabaseService.BarcodeScanRecord>,
        expedisiRecords: List<BarcodeSupabaseService.BarcodeSessionRecord>
    ) {
        cacheMutex.withLock {
            val resiToUpsert = mutableListOf<BarcodeResiEntity>()
            val resiToDelete = mutableListOf<String>()

            resiRecords.forEach { record ->
                val normalized = normalize(record.Resi)
                if (record.deleted_at != null) {
                    resiRecordsCache.remove(normalized)
                    processedResiSet.remove(normalized)
                    resiToDelete.add(normalized)
                } else if (isWithinLast7Days(record.created)) {
                    val sanitized = record.copy(Resi = normalized)
                    storeResiInMemory(sanitized)
                    resiToUpsert.add(BarcodeResiEntity.fromRecord(sanitized))
                }
            }

        val expedisiToUpsert = mutableListOf<BarcodeExpedisiEntity>()
        val expedisiToDelete = mutableListOf<String>()

            expedisiRecords.forEach { record ->
                val normalized = normalize(record.resino)
                if (record.deleted_at != null || record.flag.uppercase() != "NO") {
                    val removed = expedisiRecordsCache.remove(normalized)
                    processedResinoSet.remove(normalized)
                    removed?.orderno?.let { expedisiByOrdernoCache.remove(it) }
                    expedisiToDelete.add(normalized)
                } else {
                    val sanitized = record.copy(resino = normalized)
                    storeExpedisiInMemory(sanitized)
                    expedisiToUpsert.add(BarcodeExpedisiEntity.fromRecord(sanitized))
                }
            }

            courierNamesCache = getUniqueCourierNamesFromCache()
            lastCouriersCacheUpdate = System.currentTimeMillis()
            lastResiCacheUpdate = lastCouriersCacheUpdate
            lastExpedisiCacheUpdate = lastCouriersCacheUpdate
            persistMetadata()

            scope.launch {
                if (resiToDelete.isNotEmpty()) resiToDelete.forEach { barcodeDao.deleteResi(it) }
                if (expedisiToDelete.isNotEmpty()) expedisiToDelete.forEach { barcodeDao.deleteExpedisi(it) }
                if (resiToUpsert.isNotEmpty()) barcodeDao.upsertResiInternal(resiToUpsert)
                if (expedisiToUpsert.isNotEmpty()) barcodeDao.upsertExpedisiInternal(expedisiToUpsert)
            }
        }
    }

    fun isResiProcessed(resi: String): Boolean = processedResiSet.contains(normalize(resi))

    fun getResiRecord(resi: String): BarcodeSupabaseService.BarcodeScanRecord? =
        resiRecordsCache[normalize(resi)]

    suspend fun addResiRecord(record: BarcodeSupabaseService.BarcodeScanRecord) {
        cacheMutex.withLock {
            val normalized = normalize(record.Resi)
            val sanitized = record.copy(Resi = normalized)
            storeResiInMemory(sanitized)
            lastResiCacheUpdate = System.currentTimeMillis()
            persistMetadata()
            scope.launch {
                barcodeDao.upsertResiInternal(listOf(BarcodeResiEntity.fromRecord(sanitized)))
            }
        }
    }

    suspend fun removeResiRecord(resi: String) {
        cacheMutex.withLock {
            val normalized = normalize(resi)
            resiRecordsCache.remove(normalized)
            processedResiSet.remove(normalized)
            scope.launch { barcodeDao.deleteResi(normalized) }
        }
    }

    fun isResinoProcessed(resino: String): Boolean = processedResinoSet.contains(normalize(resino))

    fun getExpedisiRecordByResino(resino: String): BarcodeSupabaseService.BarcodeSessionRecord? =
        expedisiRecordsCache[normalize(resino)]

    fun getExpedisiRecordByOrderno(orderno: String): BarcodeSupabaseService.BarcodeSessionRecord? =
        expedisiByOrdernoCache[orderno.trim()]

    suspend fun addExpedisiRecord(record: BarcodeSupabaseService.BarcodeSessionRecord) {
        cacheMutex.withLock {
            val normalized = normalize(record.resino)
            val sanitized = record.copy(resino = normalized)
            storeExpedisiInMemory(sanitized)
            lastExpedisiCacheUpdate = System.currentTimeMillis()
            courierNamesCache = getUniqueCourierNamesFromCache()
            lastCouriersCacheUpdate = lastExpedisiCacheUpdate
            persistMetadata()
            scope.launch {
                barcodeDao.upsertExpedisiInternal(listOf(BarcodeExpedisiEntity.fromRecord(sanitized)))
            }
        }
    }

    suspend fun removeExpedisiRecord(resino: String) {
        cacheMutex.withLock {
            val normalized = normalize(resino)
            val removed = expedisiRecordsCache.remove(normalized)
            processedResinoSet.remove(normalized)
            if (removed != null && removed.orderno.isNotEmpty()) {
                expedisiByOrdernoCache.remove(removed.orderno)
            }
            scope.launch { barcodeDao.deleteExpedisi(normalized) }
        }
    }

    fun getProcessedResiSet(): Set<String> = processedResiSet.toSet()
    fun getProcessedResinoSet(): Set<String> = processedResinoSet.toSet()

    fun getAllResiRecords(): List<BarcodeSupabaseService.BarcodeScanRecord> =
        resiRecordsCache.values.toList()

    fun getAllExpedisiRecords(): List<BarcodeSupabaseService.BarcodeSessionRecord> =
        expedisiRecordsCache.values.toList()

    fun getUniqueCourierNamesFromCache(): List<String> {
        val expedisiNames = expedisiRecordsCache.values
            .mapNotNull { it.couriername?.trim()?.takeIf(String::isNotEmpty) }
        val resiNames = resiRecordsCache.values
            .mapNotNull { it.Keterangan?.trim()?.takeIf(String::isNotEmpty) }

        return (expedisiNames + resiNames)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun setCourierNamesCache(courierNames: List<String>) {
        courierNamesCache = courierNames.distinct().sorted()
        lastCouriersCacheUpdate = System.currentTimeMillis()
        persistMetadata()
    }

    fun getCourierNamesCache(): List<String> = courierNamesCache

    fun getLastResiSyncTime(): Long = lastResiSyncTime

    suspend fun setLastResiSyncTime(timestamp: Long) {
        cacheMutex.withLock {
            lastResiSyncTime = timestamp
            persistMetadata()
        }
    }

    fun getLastExpedisiSyncTime(): Long = lastExpedisiSyncTime

    suspend fun setLastExpedisiSyncTime(timestamp: Long) {
        cacheMutex.withLock {
            lastExpedisiSyncTime = timestamp
            persistMetadata()
        }
    }

    fun getLastExpedisiUpdateAt(): String? = lastExpedisiUpdateAt

    suspend fun setLastExpedisiUpdateAt(updateAt: String?) {
        cacheMutex.withLock {
            lastExpedisiUpdateAt = updateAt
            persistMetadata()
        }
    }

    fun getLastResiUpdatedAt(): String? = lastResiUpdatedAt

    suspend fun setLastResiUpdatedAt(updatedAt: String?) {
        cacheMutex.withLock {
            lastResiUpdatedAt = updatedAt
            persistMetadata()
        }
    }

    fun timestampToIsoDate(timestamp: Long): String {
        return if (timestamp == 0L) {
            Instant.now()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(RESI_WINDOW_DAYS)
                .toString()
        } else {
            Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString()
        }
    }

    suspend fun cleanupOldData() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            val cutoff = Instant.now()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(RESI_WINDOW_DAYS)
                .toString()

            val toRemove = resiRecordsCache
                .filterValues { !isWithinLast7Days(it.created) }
                .keys
            toRemove.forEach {
                resiRecordsCache.remove(it)
                processedResiSet.remove(it)
            }

            lastCleanupTime = System.currentTimeMillis()
            persistMetadata()

            scope.launch {
                val cutoffEpoch = BarcodeRoomConverters.parseIsoToEpochMillis("${cutoff}T00:00:00Z")
                cutoffEpoch?.let { barcodeDao.deleteResiOlderThan(it) }
            }
        }
    }

    fun shouldCleanup(): Boolean {
        val now = System.currentTimeMillis()
        return lastCleanupTime == 0L || (now - lastCleanupTime) >= CLEANUP_INTERVAL_MS
    }

    suspend fun clearCache() {
        cacheMutex.withLock {
            processedResiSet.clear()
            resiRecordsCache.clear()
            processedResinoSet.clear()
            expedisiRecordsCache.clear()
            expedisiByOrdernoCache.clear()
            courierNamesCache = emptyList()
            lastResiCacheUpdate = 0
            lastExpedisiCacheUpdate = 0
            lastCouriersCacheUpdate = 0
            lastResiSyncTime = 0
            lastExpedisiSyncTime = 0
            lastResiUpdatedAt = null
            lastExpedisiUpdateAt = null
            lastCleanupTime = 0
            persistMetadata()

            scope.launch {
                barcodeDao.clearResi()
                barcodeDao.clearExpedisi()
            }
        }
    }

    fun getCacheStats(): String {
        return """
            📊 Barcode Cache (Room)
            Resi cached: ${resiRecordsCache.size}
            Expedisi cached: ${expedisiRecordsCache.size}
            Couriers cached: ${courierNamesCache.size}
            Last resi update: ${if (lastResiCacheUpdate == 0L) "never" else "${(System.currentTimeMillis() - lastResiCacheUpdate) / 1000}s ago"}
            Last expedisi update: ${if (lastExpedisiCacheUpdate == 0L) "never" else "${(System.currentTimeMillis() - lastExpedisiCacheUpdate) / 1000}s ago"}
        """.trimIndent()
    }

    private fun storeResiInMemory(record: BarcodeSupabaseService.BarcodeScanRecord) {
        val normalized = normalize(record.Resi)
        processedResiSet.add(normalized)
        resiRecordsCache[normalized] = record
        trackResiUpdatedAt(record.updated_at ?: record.created)
    }

    private fun storeExpedisiInMemory(record: BarcodeSupabaseService.BarcodeSessionRecord) {
        val normalized = normalize(record.resino)
        processedResinoSet.add(normalized)
        expedisiRecordsCache[normalized] = record
        if (record.orderno.isNotEmpty()) {
            expedisiByOrdernoCache[record.orderno] = record
        }
        trackExpedisiUpdatedAt(record.update_at ?: record.created)
    }

    private fun filterResiRecords(records: List<BarcodeSupabaseService.BarcodeScanRecord>): List<BarcodeSupabaseService.BarcodeScanRecord> {
        return records.filter { it.deleted_at == null && isWithinLast7Days(it.created) }
            .map { it.copy(Resi = normalize(it.Resi)) }
    }

    private fun filterExpedisiRecords(records: List<BarcodeSupabaseService.BarcodeSessionRecord>): List<BarcodeSupabaseService.BarcodeSessionRecord> {
        return records.filter { it.deleted_at == null && it.flag.uppercase() == "NO" }
            .map { it.copy(resino = normalize(it.resino)) }
    }

    private fun isWithinLast7Days(created: String?): Boolean {
        return try {
            if (created.isNullOrBlank()) return true
            val datePart = if (created.length >= 10) created.substring(0, 10) else created
            val recordDate = java.time.LocalDate.parse(datePart)
            val todayUtc = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val windowStart = todayUtc.minusDays(RESI_WINDOW_DAYS)
            !recordDate.isBefore(windowStart)
        } catch (_: Exception) {
            true
        }
    }

    private fun normalize(value: String): String = value.trim().uppercase()

    private fun parseIsoInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: Exception) {
            try {
                Instant.parse(value)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isCandidateNewer(current: String?, candidate: String?): Boolean {
        val candidateInstant = parseIsoInstant(candidate)
        val currentInstant = parseIsoInstant(current)
        return when {
            candidateInstant == null -> false
            currentInstant == null -> true
            else -> candidateInstant.isAfter(currentInstant)
        }
    }

    private fun trackResiUpdatedAt(candidate: String?) {
        if (isCandidateNewer(lastResiUpdatedAt, candidate)) {
            lastResiUpdatedAt = candidate
        }
    }

    private fun trackExpedisiUpdatedAt(candidate: String?) {
        if (isCandidateNewer(lastExpedisiUpdateAt, candidate)) {
            lastExpedisiUpdateAt = candidate
        }
    }
}

