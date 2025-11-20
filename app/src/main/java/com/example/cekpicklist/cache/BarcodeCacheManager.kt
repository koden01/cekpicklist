package com.example.cekpicklist.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.cekpicklist.api.BarcodeSupabaseService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Light cache manager untuk barcode data
 * Menyimpan data dari tbl_barcode_resi dan tbl_expedisi di memory
 * Menggunakan HashSet untuk fast lookup
 * 
 * **PENTING**: Cache tbl_expedisi HANYA menyimpan data dengan flag = "NO"
 * - Data dengan flag = "YES" tidak akan di-cache
 * - Saat flag berubah dari "NO" ke "YES", record otomatis dihapus dari cache
 */
object BarcodeCacheManager {
    
    private const val TAG = "BarcodeCacheManager"
    private const val PREFS_NAME = "barcode_cache_prefs"
    private const val KEY_RESI_RECORDS = "resi_records_cache"
    private const val KEY_EXPEDISI_RECORDS = "expedisi_records_cache"
    private const val KEY_COURIER_NAMES = "courier_names_cache"
    private const val KEY_LAST_RESI_UPDATE = "last_resi_cache_update"
    private const val KEY_LAST_EXPEDISI_UPDATE = "last_expedisi_cache_update"
    private const val KEY_LAST_COURIERS_UPDATE = "last_couriers_cache_update"
    private const val KEY_LAST_RESI_SYNC_TIME = "last_resi_sync_time" // Timestamp untuk sync terakhir
    private const val KEY_LAST_EXPEDISI_SYNC_TIME = "last_expedisi_sync_time" // Timestamp untuk sync terakhir
    private const val KEY_LAST_EXPEDISI_UPDATE_AT = "last_expedisi_update_at" // Timestamp update_at terbaru dari tbl_expedisi
    
    // Context dan SharedPreferences untuk persistent storage
    private var context: Context? = null
    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()
    
    // Mutex untuk thread-safe operations
    private val cacheMutex = Mutex()
    
    // Cache untuk tbl_barcode_resi
    // HashSet untuk fast lookup Resi yang sudah diproses
    private val processedResiSet = ConcurrentHashMap.newKeySet<String>()
    
    // HashMap untuk menyimpan full records (Resi -> Record)
    private val resiRecordsCache = ConcurrentHashMap<String, BarcodeSupabaseService.BarcodeScanRecord>()
    
    // Cache untuk tbl_expedisi
    // HashSet untuk fast lookup resino yang sudah diproses
    private val processedResinoSet = ConcurrentHashMap.newKeySet<String>()
    
    // HashMap untuk menyimpan full records (resino -> Record)
    private val expedisiRecordsCache = ConcurrentHashMap<String, BarcodeSupabaseService.BarcodeSessionRecord>()
    
    // HashMap untuk expedisi by orderno (orderno -> Record)
    private val expedisiByOrdernoCache = ConcurrentHashMap<String, BarcodeSupabaseService.BarcodeSessionRecord>()
    
    // Cache timestamp untuk tracking last update
    private var lastResiCacheUpdate: Long = 0
    private var lastExpedisiCacheUpdate: Long = 0
    private var lastCouriersCacheUpdate: Long = 0
    
    // Timestamp untuk sync terakhir (untuk incremental fetch)
    private var lastResiSyncTime: Long = 0 // Timestamp dalam millis, 0 = belum pernah sync
    private var lastExpedisiSyncTime: Long = 0 // Timestamp dalam millis, 0 = belum pernah sync
    
    // Timestamp update_at terbaru dari tbl_expedisi (untuk cache invalidation)
    private var lastExpedisiUpdateAt: String? = null
    
    // Cache expiration time (15 jam = 15 * 60 * 60 * 1000 ms)
    // **PERUBAHAN**: Tidak digunakan lagi, cache sekarang persistent dengan auto-cleanup data > 7 hari
    @Deprecated("Cache sekarang persistent, tidak ada expiration. Gunakan cleanupOldData() untuk hapus data > 7 hari")
    private const val CACHE_EXPIRATION_MS = 15 * 60 * 60 * 1000L
    
    // Interval untuk cleanup data lama (setiap 24 jam = 24 * 60 * 60 * 1000 ms)
    private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private var lastCleanupTime: Long = 0
    
    // Cache khusus untuk daftar courier unik (untuk spinner expedisi)
    private var courierNamesCache: List<String> = emptyList()
    
    /**
     * Initialize BarcodeCacheManager dengan Context untuk persistent storage
     * HARUS dipanggil sebelum menggunakan cache (biasanya di Application.onCreate)
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        this.sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "✅ BarcodeCacheManager initialized with persistent storage (auto-cleanup data > 7 days)")
        
        // Load cache dari SharedPreferences saat init
        CoroutineScope(Dispatchers.IO).launch {
            loadCacheFromStorage()
        }
    }
    
    // Window sinkronisasi resi: 7 hari terakhir (hari ini + 6 hari sebelumnya)
    private fun isWithinLast7Days(created: String?): Boolean {
        return try {
            if (created.isNullOrBlank()) return true // jika tidak ada timestamp, jangan dibuang
            // Ambil bagian tanggal saja (YYYY-MM-DD) agar robust untuk ISO 8601 dengan waktu
            val datePart = if (created.length >= 10) created.substring(0, 10) else created
            val recordDate = java.time.LocalDate.parse(datePart)
            val todayUtc = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val sevenDaysWindowStart = todayUtc.minusDays(6) // termasuk hari ini
            !recordDate.isBefore(sevenDaysWindowStart)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Cannot parse created date for resi: '$created'. Keeping record by default.")
            true
        }
    }
    
    /**
     * Check apakah cache masih valid (belum expired)
     * **PERUBAHAN**: Cache sekarang selalu valid (persistent), hanya cleanup data > 7 hari
     */
    fun isResiCacheValid(): Boolean {
        // Cache selalu valid jika ada data (persistent storage)
        return resiRecordsCache.isNotEmpty() || lastResiCacheUpdate > 0
    }
    
    fun isExpedisiCacheValid(): Boolean {
        // Cache selalu valid jika ada data (persistent storage)
        return expedisiRecordsCache.isNotEmpty() || lastExpedisiCacheUpdate > 0
    }
    
    fun isCouriersCacheValid(): Boolean {
        // Cache selalu valid jika ada data (persistent storage)
        return courierNamesCache.isNotEmpty() || lastCouriersCacheUpdate > 0
    }
    
    /**
     * Initialize cache dengan data dari Supabase
     */
    suspend fun initializeCache(
        resiRecords: List<BarcodeSupabaseService.BarcodeScanRecord>,
        expedisiRecords: List<BarcodeSupabaseService.BarcodeSessionRecord>
    ) {
        cacheMutex.withLock {
            try {
                Log.d(TAG, "🔄 Initializing cache with ${resiRecords.size} resi records and ${expedisiRecords.size} expedisi records")
                
                // Clear existing cache
                processedResiSet.clear()
                resiRecordsCache.clear()
                processedResinoSet.clear()
                expedisiRecordsCache.clear()
                expedisiByOrdernoCache.clear()
                
                // Populate resi cache - hanya 7 hari terakhir termasuk hari ini
                resiRecords.forEach { record ->
                    if (!isWithinLast7Days(record.created)) return@forEach
                    val resi = record.Resi.trim().uppercase()
                    processedResiSet.add(resi)
                    resiRecordsCache[resi] = record
                }
                
                // Populate expedisi cache - HANYA data dengan flag = "NO"
                var latestUpdateAt: String? = null
                expedisiRecords.forEach { record ->
                    // **VALIDASI**: Hanya cache data dengan flag = "NO"
                    if (record.flag.uppercase() != "NO") {
                        Log.d(TAG, "⚠️ Skipping expedisi record with flag='${record.flag}' (only caching flag='NO'): ${record.resino}")
                        return@forEach
                    }
                    
                    val resino = record.resino.trim().uppercase()
                    val orderno = record.orderno.trim()
                    
                    processedResinoSet.add(resino)
                    expedisiRecordsCache[resino] = record
                    
                    if (orderno.isNotEmpty()) {
                        expedisiByOrdernoCache[orderno] = record
                    }
                    
                    // Track timestamp update_at terbaru
                    if (record.update_at != null) {
                        if (latestUpdateAt == null || record.update_at > latestUpdateAt) {
                            latestUpdateAt = record.update_at
                        }
                    }
                }
                
                lastResiCacheUpdate = System.currentTimeMillis()
                lastExpedisiCacheUpdate = System.currentTimeMillis()
                lastExpedisiUpdateAt = latestUpdateAt
                
                // **OPTIMASI**: Auto-populate courier names cache dari expedisi records
                val courierNames = getUniqueCourierNamesFromCache()
                if (courierNames.isNotEmpty()) {
                    courierNamesCache = courierNames.distinct().sorted()
                    lastCouriersCacheUpdate = System.currentTimeMillis()
                    Log.d(TAG, "⚡ Auto-populated courier names cache: ${courierNamesCache.size} couriers")
                }
                
                Log.d(TAG, "✅ Cache initialized:")
                Log.d(TAG, "   - Resi records: ${resiRecordsCache.size} (${processedResiSet.size} unique)")
                Log.d(TAG, "   - Expedisi records (flag=NO only): ${expedisiRecordsCache.size} (${processedResinoSet.size} unique)")
                Log.d(TAG, "   - Courier names cache: ${courierNamesCache.size} couriers")
                
                // Auto-save ke persistent storage
                CoroutineScope(Dispatchers.IO).launch {
                    saveCacheToStorage()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing cache: ${e.message}", e)
            }
        }
    }
    
    /**
     * Refresh cache dari Supabase
     */
    suspend fun refreshCache(
        resiRecords: List<BarcodeSupabaseService.BarcodeScanRecord>,
        expedisiRecords: List<BarcodeSupabaseService.BarcodeSessionRecord>
    ) {
        cacheMutex.withLock {
            try {
                Log.d(TAG, "🔄 Refreshing cache...")
                
                // Update resi cache - hanya 7 hari terakhir termasuk hari ini
                resiRecords.forEach { record ->
                    if (!isWithinLast7Days(record.created)) return@forEach
                    val resi = record.Resi.trim().uppercase()
                    processedResiSet.add(resi)
                    resiRecordsCache[resi] = record
                }
                
            // Update expedisi cache - HANYA data dengan flag = "NO"
            var latestUpdateAt: String? = lastExpedisiUpdateAt
            expedisiRecords.forEach { record ->
                // **VALIDASI**: Hanya cache data dengan flag = "NO"
                if (record.flag.uppercase() != "NO") {
                    Log.d(TAG, "⚠️ Skipping expedisi record with flag='${record.flag}' (only caching flag='NO'): ${record.resino}")
                    return@forEach
                }
                
                val resino = record.resino.trim().uppercase()
                val orderno = record.orderno.trim()
                
                processedResinoSet.add(resino)
                expedisiRecordsCache[resino] = record
                
                if (orderno.isNotEmpty()) {
                    expedisiByOrdernoCache[orderno] = record
                }
                
                // Track timestamp update_at terbaru
                if (record.update_at != null) {
                    if (latestUpdateAt == null || record.update_at > latestUpdateAt) {
                        latestUpdateAt = record.update_at
                    }
                }
            }
                
                lastResiCacheUpdate = System.currentTimeMillis()
                lastExpedisiCacheUpdate = System.currentTimeMillis()
                lastExpedisiUpdateAt = latestUpdateAt
                
                // **OPTIMASI**: Auto-update courier names cache dari expedisi records
                val courierNames = getUniqueCourierNamesFromCache()
                if (courierNames.isNotEmpty()) {
                    courierNamesCache = courierNames.distinct().sorted()
                    lastCouriersCacheUpdate = System.currentTimeMillis()
                    Log.d(TAG, "⚡ Auto-updated courier names cache: ${courierNamesCache.size} couriers")
                }
                
                Log.d(TAG, "✅ Cache refreshed:")
                Log.d(TAG, "   - Resi records: ${resiRecordsCache.size}")
                Log.d(TAG, "   - Expedisi records (flag=NO only): ${expedisiRecordsCache.size}")
                Log.d(TAG, "   - Courier names cache: ${courierNamesCache.size} couriers")
                
                // Auto-save ke persistent storage
                CoroutineScope(Dispatchers.IO).launch {
                    saveCacheToStorage()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error refreshing cache: ${e.message}", e)
            }
        }
    }
    
    /**
     * Check apakah Resi sudah diproses (fast lookup dengan HashSet)
     */
    fun isResiProcessed(resi: String): Boolean {
        val normalizedResi = resi.trim().uppercase()
        return processedResiSet.contains(normalizedResi)
    }
    
    /**
     * Get Resi record dari cache
     */
    fun getResiRecord(resi: String): BarcodeSupabaseService.BarcodeScanRecord? {
        val normalizedResi = resi.trim().uppercase()
        return resiRecordsCache[normalizedResi]
    }
    
    /**
     * Add Resi record ke cache
     */
    suspend fun addResiRecord(record: BarcodeSupabaseService.BarcodeScanRecord) {
        cacheMutex.withLock {
            // Hanya tambahkan jika dalam window 7 hari terakhir
            if (!isWithinLast7Days(record.created)) {
                Log.d(TAG, "⚠️ Skipping resi outside 7-day window: ${record.Resi} (${record.created})")
                return@withLock
            }
            val normalizedResi = record.Resi.trim().uppercase()
            processedResiSet.add(normalizedResi)
            resiRecordsCache[normalizedResi] = record
            lastResiCacheUpdate = System.currentTimeMillis()
            
            // Auto-save ke persistent storage
            CoroutineScope(Dispatchers.IO).launch {
                saveCacheToStorage()
            }
        }
    }
    
    /**
     * Check apakah resino sudah diproses (fast lookup dengan HashSet)
     */
    fun isResinoProcessed(resino: String): Boolean {
        val normalizedResino = resino.trim().uppercase()
        return processedResinoSet.contains(normalizedResino)
    }
    
    /**
     * Get Expedisi record dari cache berdasarkan resino
     */
    fun getExpedisiRecordByResino(resino: String): BarcodeSupabaseService.BarcodeSessionRecord? {
        val normalizedResino = resino.trim().uppercase()
        return expedisiRecordsCache[normalizedResino]
    }
    
    /**
     * Get Expedisi record dari cache berdasarkan orderno
     */
    fun getExpedisiRecordByOrderno(orderno: String): BarcodeSupabaseService.BarcodeSessionRecord? {
        val normalizedOrderno = orderno.trim()
        return expedisiByOrdernoCache[normalizedOrderno]
    }
    
    /**
     * Add Expedisi record ke cache - HANYA jika flag = "NO"
     */
    suspend fun addExpedisiRecord(record: BarcodeSupabaseService.BarcodeSessionRecord) {
        cacheMutex.withLock {
            // **VALIDASI**: Hanya cache data dengan flag = "NO"
            if (record.flag.uppercase() != "NO") {
                Log.d(TAG, "⚠️ Skipping expedisi record with flag='${record.flag}' (only caching flag='NO'): ${record.resino}")
                return@withLock
            }
            
            val normalizedResino = record.resino.trim().uppercase()
            val orderno = record.orderno.trim()
            
            processedResinoSet.add(normalizedResino)
            expedisiRecordsCache[normalizedResino] = record
            
            if (orderno.isNotEmpty()) {
                expedisiByOrdernoCache[orderno] = record
            }
            
            lastExpedisiCacheUpdate = System.currentTimeMillis()
            
            // Auto-save ke persistent storage
            CoroutineScope(Dispatchers.IO).launch {
                saveCacheToStorage()
            }
        }
    }
    
    /**
     * Remove Expedisi record dari cache saat flag berubah ke "YES"
     */
    suspend fun removeExpedisiRecord(resino: String) {
        cacheMutex.withLock {
            try {
                val normalizedResino = resino.trim().uppercase()
                
                // Remove dari semua cache maps
                val record = expedisiRecordsCache.remove(normalizedResino)
                processedResinoSet.remove(normalizedResino)
                
                // Remove dari orderno cache jika ada
                record?.orderno?.let { orderno ->
                    expedisiByOrdernoCache.remove(orderno.trim())
                }
                
                Log.d(TAG, "🗑️ Removed expedisi record from cache (flag changed to YES): $normalizedResino")
                
                // Auto-save ke persistent storage
                CoroutineScope(Dispatchers.IO).launch {
                    saveCacheToStorage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error removing expedisi record from cache: ${e.message}", e)
            }
        }
    }
    
    /**
     * Remove Resi record dari cache (untuk deteksi penghapusan di Supabase)
     */
    suspend fun removeResiRecord(resi: String) {
        cacheMutex.withLock {
            try {
                val normalizedResi = resi.trim().uppercase()
                
                // Remove dari semua cache maps
                resiRecordsCache.remove(normalizedResi)
                processedResiSet.remove(normalizedResi)
                
                Log.d(TAG, "🗑️ Removed resi record from cache: $normalizedResi")
                
                // Auto-save ke persistent storage
                CoroutineScope(Dispatchers.IO).launch {
                    saveCacheToStorage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error removing resi record from cache: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get all processed Resi (HashSet) untuk digunakan di aplikasi
     */
    fun getProcessedResiSet(): Set<String> {
        return processedResiSet.toSet()
    }
    
    /**
     * Get all processed Resino (HashSet) untuk digunakan di aplikasi
     */
    fun getProcessedResinoSet(): Set<String> {
        return processedResinoSet.toSet()
    }
    
    /**
     * Get all Resi records dari cache
     */
    fun getAllResiRecords(): List<BarcodeSupabaseService.BarcodeScanRecord> {
        return resiRecordsCache.values.toList()
    }
    
    /**
     * Get all Expedisi records dari cache
     * HANYA mengembalikan data dengan flag = "NO" (sesuai dengan aturan cache)
     */
    fun getAllExpedisiRecords(): List<BarcodeSupabaseService.BarcodeSessionRecord> {
        return expedisiRecordsCache.values.toList()
    }
    
    /**
     * Get unique courier names dari cache expedisi (flag=NO only)
     */
    fun getUniqueCourierNamesFromCache(): List<String> {
        val names = HashSet<String>()
        expedisiRecordsCache.values.forEach { record ->
            val name = record.couriername?.trim()
            if (!name.isNullOrEmpty()) names.add(name)
        }
        return names.toList().sorted()
    }

    /**
     * Set dan simpan daftar courier unik ke cache khusus couriers
     */
    fun setCourierNamesCache(courierNames: List<String>) {
        courierNamesCache = courierNames.distinct().sorted()
        lastCouriersCacheUpdate = System.currentTimeMillis()
        Log.d(TAG, "💾 Couriers cache updated: ${courierNamesCache.size} names")
        
        // Auto-save ke persistent storage
        CoroutineScope(Dispatchers.IO).launch {
            saveCacheToStorage()
        }
    }

    /**
     * Ambil daftar courier unik dari cache khusus couriers
     */
    fun getCourierNamesCache(): List<String> {
        return courierNamesCache
    }
    
    /**
     * Get last sync time untuk resi (timestamp dalam millis)
     * Returns 0 jika belum pernah sync
     */
    fun getLastResiSyncTime(): Long {
        return lastResiSyncTime
    }
    
    /**
     * Set last sync time untuk resi (update timestamp setelah sync berhasil)
     */
    suspend fun setLastResiSyncTime(timestamp: Long) {
        cacheMutex.withLock {
            lastResiSyncTime = timestamp
        }
        // Auto-save ke persistent storage (diluar lock untuk menghindari deadlock)
        withContext(Dispatchers.IO) {
            saveCacheToStorage()
        }
    }
    
    /**
     * Get last sync time untuk expedisi (timestamp dalam millis)
     * Returns 0 jika belum pernah sync
     */
    fun getLastExpedisiSyncTime(): Long {
        return lastExpedisiSyncTime
    }
    
    /**
     * Set last sync time untuk expedisi (update timestamp setelah sync berhasil)
     */
    suspend fun setLastExpedisiSyncTime(timestamp: Long) {
        cacheMutex.withLock {
            lastExpedisiSyncTime = timestamp
        }
        // Auto-save ke persistent storage (diluar lock untuk menghindari deadlock)
        withContext(Dispatchers.IO) {
            saveCacheToStorage()
        }
    }
    
    /**
     * Get last update_at timestamp dari tbl_expedisi (untuk cache invalidation)
     * Returns null jika belum pernah di-set
     */
    fun getLastExpedisiUpdateAt(): String? {
        return lastExpedisiUpdateAt
    }
    
    /**
     * Set last update_at timestamp dari tbl_expedisi (untuk cache invalidation)
     */
    suspend fun setLastExpedisiUpdateAt(updateAt: String?) {
        cacheMutex.withLock {
            lastExpedisiUpdateAt = updateAt
        }
        // Auto-save ke persistent storage (diluar lock untuk menghindari deadlock)
        withContext(Dispatchers.IO) {
            saveCacheToStorage()
        }
    }
    
    /**
     * Convert timestamp (millis) ke ISO date string (YYYY-MM-DD) untuk query Supabase
     * Jika timestamp = 0, return tanggal 7 hari yang lalu (untuk initial sync)
     */
    fun timestampToIsoDate(timestamp: Long): String {
        return if (timestamp == 0L) {
            // Initial sync: ambil dari 7 hari yang lalu
            val sevenDaysAgo = java.time.Instant.now()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(6)
            sevenDaysAgo.toString()
        } else {
            // Convert timestamp ke ISO date
            java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString()
        }
    }
    
    /**
     * Load cache dari SharedPreferences (persistent storage)
     */
    private suspend fun loadCacheFromStorage() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            try {
                if (sharedPreferences == null) {
                    Log.w(TAG, "⚠️ SharedPreferences not initialized, skipping load")
                    return@withLock
                }
                
                Log.d(TAG, "🔄 Loading cache from persistent storage...")
                
                // Load timestamps
                lastResiCacheUpdate = sharedPreferences!!.getLong(KEY_LAST_RESI_UPDATE, 0)
                lastExpedisiCacheUpdate = sharedPreferences!!.getLong(KEY_LAST_EXPEDISI_UPDATE, 0)
                lastCouriersCacheUpdate = sharedPreferences!!.getLong(KEY_LAST_COURIERS_UPDATE, 0)
                lastResiSyncTime = sharedPreferences!!.getLong(KEY_LAST_RESI_SYNC_TIME, 0)
                lastExpedisiSyncTime = sharedPreferences!!.getLong(KEY_LAST_EXPEDISI_SYNC_TIME, 0)
                lastExpedisiUpdateAt = sharedPreferences!!.getString(KEY_LAST_EXPEDISI_UPDATE_AT, null)
                lastCleanupTime = sharedPreferences!!.getLong("last_cleanup_time", 0)
                
                // **PERUBAHAN**: Cache sekarang persistent, tidak ada expiration
                // Hanya skip jika cache benar-benar kosong
                val now = System.currentTimeMillis()
                
                // Load Resi records (persistent, tidak ada expiration)
                val resiJson = sharedPreferences!!.getString(KEY_RESI_RECORDS, null)
                if (resiJson != null && resiJson.isNotEmpty()) {
                    val type = object : TypeToken<Map<String, BarcodeSupabaseService.BarcodeScanRecord>>() {}.type
                    val loadedResi = gson.fromJson<Map<String, BarcodeSupabaseService.BarcodeScanRecord>>(resiJson, type)
                    if (loadedResi != null) {
                        // Filter hanya data dalam window 7 hari saat load (cleanup akan handle yang > 7 hari)
                        loadedResi.forEach { (resi, record) ->
                            if (isWithinLast7Days(record.created)) {
                                val normalizedResi = resi.trim().uppercase()
                                processedResiSet.add(normalizedResi)
                                resiRecordsCache[normalizedResi] = record
                            }
                        }
                        Log.d(TAG, "✅ Loaded ${resiRecordsCache.size} resi records from storage (filtered to 7 days)")
                    }
                }
                
                // Load Expedisi records (persistent, tidak ada expiration)
                val expedisiJson = sharedPreferences!!.getString(KEY_EXPEDISI_RECORDS, null)
                if (expedisiJson != null && expedisiJson.isNotEmpty()) {
                    val type = object : TypeToken<Map<String, BarcodeSupabaseService.BarcodeSessionRecord>>() {}.type
                    val loadedExpedisi = gson.fromJson<Map<String, BarcodeSupabaseService.BarcodeSessionRecord>>(expedisiJson, type)
                    if (loadedExpedisi != null) {
                        // **PERBAIKAN**: Expedisi cache menyimpan SEMUA flag="NO" tanpa filter tanggal
                        // Filter hanya flag="NO" (tidak ada filter tanggal)
                        loadedExpedisi.forEach { (resino, record) ->
                            if (record.flag.uppercase() == "NO") {
                                // **TIDAK ADA FILTER TANGGAL**: Simpan semua flag="NO" tanpa peduli tanggalnya
                                val normalizedResino = resino.trim().uppercase()
                                processedResinoSet.add(normalizedResino)
                                expedisiRecordsCache[normalizedResino] = record
                                
                                record.orderno?.let { orderno ->
                                    if (orderno.isNotEmpty()) {
                                        expedisiByOrdernoCache[orderno.trim()] = record
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "✅ Loaded ${expedisiRecordsCache.size} expedisi records from storage (flag=NO only, no date filter)")
                    }
                }
                
                // Load courier names
                val couriersJson = sharedPreferences!!.getString(KEY_COURIER_NAMES, null)
                if (couriersJson != null && couriersJson.isNotEmpty()) {
                    val type = object : TypeToken<List<String>>() {}.type
                    val loadedCouriers = gson.fromJson<List<String>>(couriersJson, type)
                    if (loadedCouriers != null) {
                        courierNamesCache = loadedCouriers
                        Log.d(TAG, "✅ Loaded ${courierNamesCache.size} courier names from storage")
                    }
                }
                
                Log.d(TAG, "✅ Cache loaded from storage successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading cache from storage: ${e.message}", e)
            }
        }
    }
    
    /**
     * Save cache ke SharedPreferences (persistent storage)
     */
    private suspend fun saveCacheToStorage() = withContext(Dispatchers.IO) {
        try {
            if (sharedPreferences == null) {
                Log.w(TAG, "⚠️ SharedPreferences not initialized, skipping save")
                return@withContext
            }
            
            Log.d(TAG, "💾 Saving cache to persistent storage...")
            
            // Save timestamps
            sharedPreferences!!.edit()
                .putLong(KEY_LAST_RESI_UPDATE, lastResiCacheUpdate)
                .putLong(KEY_LAST_EXPEDISI_UPDATE, lastExpedisiCacheUpdate)
                .putLong(KEY_LAST_COURIERS_UPDATE, lastCouriersCacheUpdate)
                .putLong(KEY_LAST_RESI_SYNC_TIME, lastResiSyncTime)
                .putLong(KEY_LAST_EXPEDISI_SYNC_TIME, lastExpedisiSyncTime)
                .putString(KEY_LAST_EXPEDISI_UPDATE_AT, lastExpedisiUpdateAt)
                .putLong("last_cleanup_time", lastCleanupTime)
                .apply()
            
            // Save Resi records
            val resiJson = gson.toJson(resiRecordsCache)
            sharedPreferences!!.edit().putString(KEY_RESI_RECORDS, resiJson).apply()
            
            // Save Expedisi records
            val expedisiJson = gson.toJson(expedisiRecordsCache)
            sharedPreferences!!.edit().putString(KEY_EXPEDISI_RECORDS, expedisiJson).apply()
            
            // Save courier names
            val couriersJson = gson.toJson(courierNamesCache)
            sharedPreferences!!.edit().putString(KEY_COURIER_NAMES, couriersJson).apply()
            
            Log.d(TAG, "✅ Cache saved to persistent storage (${resiRecordsCache.size} resi, ${expedisiRecordsCache.size} expedisi)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving cache to storage: ${e.message}", e)
        }
    }
    
    /**
     * Cleanup data lama (> 7 hari) - Cron job style
     * Dijalankan secara otomatis untuk menjaga cache tetap bersih
     */
    suspend fun cleanupOldData() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            try {
                val now = System.currentTimeMillis()
                val todayUtc = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                val sevenDaysWindowStart = todayUtc.minusDays(6) // termasuk hari ini
                
                Log.d(TAG, "🧹 [CLEANUP] Starting cleanup for resi data older than 7 days (expedisi tidak di-cleanup berdasarkan tanggal)...")
                
                var cleanedResiCount = 0
                
                // Cleanup resi records > 7 hari
                val resiToRemove = mutableListOf<String>()
                resiRecordsCache.forEach { (resi, record) ->
                    if (!isWithinLast7Days(record.created)) {
                        resiToRemove.add(resi)
                    }
                }
                
                resiToRemove.forEach { resi ->
                    resiRecordsCache.remove(resi)
                    processedResiSet.remove(resi)
                    cleanedResiCount++
                }
                
                // **PERBAIKAN**: Expedisi cache TIDAK di-cleanup berdasarkan tanggal
                // Expedisi hanya dihapus saat flag berubah menjadi "YES" (dilakukan saat sync)
                // Cache expedisi menyimpan SEMUA flag="NO" tanpa filter tanggal
                // Tidak perlu cleanup expedisi berdasarkan tanggal
                
                // Update last cleanup time
                lastCleanupTime = now
                
                // Save ke persistent storage jika ada data yang dihapus
                if (cleanedResiCount > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        saveCacheToStorage()
                    }
                    
                    Log.d(TAG, "✅ [CLEANUP] Cleaned up $cleanedResiCount resi records older than 7 days (expedisi tidak di-cleanup berdasarkan tanggal, hanya berdasarkan flag)")
                } else {
                    Log.d(TAG, "✅ [CLEANUP] No old data to clean up (all data within 7 days)")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ [CLEANUP] Error cleaning up old data: ${e.message}", e)
            }
        }
    }
    
    /**
     * Check apakah perlu cleanup (cron job style)
     * Cleanup dijalankan setiap 24 jam sekali
     */
    fun shouldCleanup(): Boolean {
        val now = System.currentTimeMillis()
        return if (lastCleanupTime == 0L) {
            // Belum pernah cleanup, jalankan pertama kali
            true
        } else {
            // Cleanup setiap 24 jam
            (now - lastCleanupTime) >= CLEANUP_INTERVAL_MS
        }
    }
    
    /**
     * Clear all cache
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            processedResiSet.clear()
            resiRecordsCache.clear()
            processedResinoSet.clear()
            expedisiRecordsCache.clear()
            expedisiByOrdernoCache.clear()
            lastResiCacheUpdate = 0
            lastExpedisiCacheUpdate = 0
            lastCleanupTime = 0
            
            // Clear dari SharedPreferences juga
            sharedPreferences?.edit()?.clear()?.apply()
            
            Log.d(TAG, "🧹 Cache cleared")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        return """
            📊 Barcode Cache Statistics:
            - Resi records cached: ${resiRecordsCache.size}
            - Processed Resi (HashSet): ${processedResiSet.size}
            - Expedisi records cached: ${expedisiRecordsCache.size}
            - Processed Resino (HashSet): ${processedResinoSet.size}
            - Expedisi by Orderno: ${expedisiByOrdernoCache.size}
            - Last Resi cache update: ${if (lastResiCacheUpdate > 0) "${(System.currentTimeMillis() - lastResiCacheUpdate) / 1000}s ago" else "Never"}
            - Last Expedisi cache update: ${if (lastExpedisiCacheUpdate > 0) "${(System.currentTimeMillis() - lastExpedisiCacheUpdate) / 1000}s ago" else "Never"}
            - Resi cache valid: ${isResiCacheValid()}
            - Expedisi cache valid: ${isExpedisiCacheValid()}
        """.trimIndent()
    }
}

