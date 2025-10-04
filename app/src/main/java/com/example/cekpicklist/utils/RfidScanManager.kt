package com.example.cekpicklist.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.media.AudioManager
import android.media.ToneGenerator
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.entity.InventoryParameter
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback
import com.example.cekpicklist.api.NirwanaApiService

/**
 * Engine RFID sederhana (stub) agar aplikasi dapat dikompilasi dan berjalan tanpa SDK asli.
 * - Menyediakan API yang dipanggil oleh BaseRfidActivity.
 * - Implementasi saat ini hanya simulasi dan pengelolaan state/callback.
 */
class RfidScanManager(
	private val context: Context,
	private val activity: Activity
) {

	private val tag = "RfidScanManager"

    private var isScanningInternal: Boolean = false
	private var isGraceActive: Boolean = false
	private var settings: RfidScanSettings = RfidScanSettings()
	private var reader: RFIDWithUHFUART? = null

	private var onRfidDetected: ((String, Int) -> Unit)? = null
	private var onScanStateChanged: ((Boolean) -> Unit)? = null
	private var onError: ((String) -> Unit)? = null
	private var onGracePeriodCompleted: (() -> Unit)? = null
	private var onLookupRequired: ((List<String>) -> Unit)? = null
    private var onDataCleared: (() -> Unit)? = null

    // Koleksi EPC unik selama sesi scan (menggunakan struktur indeks cepat)
    // Struktur indeks cepat: EPC -> index
    private val epcIndexMap: HashMap<String, Int> = hashMapOf()
    // Menjaga urutan pemasukan EPC
    private val epcList: MutableList<String> = mutableListOf()
    // Menyimpan hasil lookup per EPC dalam bentuk JSON (agar tidak coupling dengan layer API)
    private val epcToProduct: HashMap<String, NirwanaApiService.ProductInfo> = hashMapOf()
    
    // Lookup processing
    private val inFlightEpcs = mutableSetOf<String>()
    private val lookupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

	init {
		initializeReaderSafely()
		// Initialize settings from SharedPreferences
		initializeSettingsFromSharedPreferences()
	}

	fun setOnRfidDetected(callback: (String, Int) -> Unit) {
		onRfidDetected = callback
	}

	fun setOnScanStateChanged(callback: (Boolean) -> Unit) {
		onScanStateChanged = callback
	}

	fun setOnError(callback: (String) -> Unit) {
		onError = callback
	}

	fun setOnGracePeriodCompleted(callback: () -> Unit) {
		onGracePeriodCompleted = callback
	}

	fun setOnLookupRequired(callback: (List<String>) -> Unit) {
		onLookupRequired = callback
	}

	fun setOnDataCleared(callback: () -> Unit) {
		onDataCleared = callback
	}

	fun startScanning(): Boolean {
		if (isScanningInternal) return true
		
		// **PERBAIKAN KRITIS**: Refresh settings dari SharedPreferences sebelum start scanning
		Log.d(tag, "🔥 Refreshing settings from SharedPreferences before startScanning...")
		refreshSettingsFromSharedPreferences()
		
		isScanningInternal = true
		isGraceActive = false
		onScanStateChanged?.invoke(true)
		val started = startInventorySafely()
		Log.d(tag, "🔥 Scanning started (started=$started, power=${settings.powerLevel}, dupRemoval=${settings.duplicateRemovalEnabled})")
		if (!started) {
			// Revert state if reader failed to start
			isScanningInternal = false
			onScanStateChanged?.invoke(false)
		}
		return started
	}

	fun stopScanning(): Boolean {
		if (!isScanningInternal) return true
		isScanningInternal = false
		isGraceActive = false
		onScanStateChanged?.invoke(false)
		stopInventorySafely()
		Log.d(tag, "🔥 Scanning stopped")
		return true
	}

	fun stopScanningWithGracePeriod(): Boolean {
		if (!isScanningInternal) return true
		isScanningInternal = false
		onScanStateChanged?.invoke(false)
		stopInventorySafely()
		return if (settings.gracePeriodMs <= 0L) {
			isGraceActive = false
            // **NEW**: Trigger lookup saat stop (tanpa grace period)
            triggerLookupForAllUniqueRfids()
            onGracePeriodCompleted?.invoke()
            Log.d(tag, "🔥 Scanning stopped (no grace period)")
			true
		} else {
			isGraceActive = true
			Log.d(tag, "🔥 Scanning stopped with grace period ${settings.gracePeriodMs}ms")
			activity.window?.decorView?.postDelayed({
				isGraceActive = false
				// **NEW**: Trigger lookup saat grace period selesai
				triggerLookupForAllUniqueRfids()
				onGracePeriodCompleted?.invoke()
                Log.d(tag, "🔥 Grace period completed (rfids=${epcIndexMap.size})")
			}, settings.gracePeriodMs)
			true
		}
	}


	fun cancelGracePeriod() {
		if (isGraceActive) {
			isGraceActive = false
			Log.d(tag, "🔥 Grace period cancelled (stub)")
		}
	}

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Keycode trigger umum dari demo: 139, 280, 291, 293, 294, 311, 312, 313, 315
        val isTrigger = keyCode == 139 || keyCode == 280 || keyCode == 291 || keyCode == 293 || keyCode == 294
                || keyCode == 311 || keyCode == 312 || keyCode == 313 || keyCode == 315
        if (!isTrigger) return false
        if (event?.repeatCount == 0) {
            Log.d(tag, "🔑 KeyDown trigger: $keyCode (repeat=0) → startScanning")
            if (!isScanningInternal) startScanning()
        }
        return true
    }

    fun handleKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val isTrigger = keyCode == 139 || keyCode == 280 || keyCode == 291 || keyCode == 293 || keyCode == 294
                || keyCode == 311 || keyCode == 312 || keyCode == 313 || keyCode == 315
        if (!isTrigger) return false
        Log.d(tag, "🔑 KeyUp trigger: $keyCode → stopScanningWithGracePeriod")
        if (isScanningInternal) stopScanningWithGracePeriod()
        return true
    }

fun playBeepSound() {
		try {
			val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
			tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
			activity.window?.decorView?.postDelayed({
				try { tone.release() } catch (_: Throwable) {}
			}, 120)
		} catch (t: Throwable) {
			Log.w(tag, "⚠️ playBeepSound failed: ${t.message}")
		}
	}

	fun isScanning(): Boolean = isScanningInternal

	fun isGracePeriodActive(): Boolean = isGraceActive

	fun getSettings(): RfidScanSettings = settings

	fun updateSettings(newSettings: RfidScanSettings) {
		settings = newSettings
		applySettingsToReader()
		Log.d(tag, "🔥 Settings updated: $settings")
	}
	
	/**
	 * Refresh settings from SharedPreferences
	 */
	fun refreshSettingsFromSharedPreferences() {
		try {
			val sharedPreferences = activity.getSharedPreferences("RFIDSettings", android.content.Context.MODE_PRIVATE)
			val powerLevel = sharedPreferences.getInt("power_level", 20)
			val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -70)
			val gracePeriod = sharedPreferences.getLong("grace_period", 0L)
			
			Log.d(tag, "🔥 === REFRESHING SETTINGS FROM SHAREDPREFERENCES ===")
			Log.d(tag, "🔥 Raw SharedPreferences values:")
			Log.d(tag, "🔥   - power_level: $powerLevel")
			Log.d(tag, "🔥   - rssi_threshold: $rssiThreshold")
			Log.d(tag, "🔥   - grace_period: $gracePeriod")
			
			val newSettings = RfidScanSettings(
				powerLevel = powerLevel,
				duplicateRemovalEnabled = true, // Always enabled
				gracePeriodMs = gracePeriod,
				rssiThreshold = rssiThreshold
			)
			
			Log.d(tag, "🔥 New settings object: $newSettings")
			updateSettings(newSettings)
			Log.d(tag, "🔥 Settings refreshed from SharedPreferences: $newSettings")
		} catch (e: Exception) {
			Log.e(tag, "❌ Error refreshing settings from SharedPreferences: ${e.message}", e)
		}
	}
	
	/**
	 * Set power level ke minimum untuk mengurangi deteksi RFID
	 */
	fun setMinimumPower() {
		val minSettings = settings.copy(powerLevel = 1, rssiThreshold = -40)  // **SELARASKAN**: Min power = 1 (sesuai uhf-uart-demo)
		updateSettings(minSettings)
		Log.d(tag, "🔥 Power set to minimum: power=1, rssiThreshold=-40")
	}
	
	/**
	 * Set power level ke maksimum untuk deteksi jarak jauh
	 */
	fun setMaximumPower() {
		val maxSettings = settings.copy(powerLevel = 30, rssiThreshold = -120)  // **SELARASKAN**: Max power = 30 (sesuai uhf-uart-demo)
		updateSettings(maxSettings)
		Log.d(tag, "🔥 Power set to maximum: power=30, rssiThreshold=-120")
	}
	
	/**
	 * Set power level custom dengan RSSI threshold yang ketat
	 */
	fun setCustomPower(powerLevel: Int, rssiThreshold: Int = -50) {
		val customSettings = settings.copy(
			powerLevel = powerLevel.coerceIn(1, 30),  // **SELARASKAN**: Range 1-30 (sesuai uhf-uart-demo)
			rssiThreshold = rssiThreshold.coerceIn(-120, 0)
		)
		updateSettings(customSettings)
		Log.d(tag, "🔥 Custom power set: power=$powerLevel, rssiThreshold=$rssiThreshold")
	}

    fun clearAllData() {
        val beforeEpcCount = epcIndexMap.size
        val beforeProductCount = epcToProduct.size
        
        epcIndexMap.clear()
        epcList.clear()
        epcToProduct.clear()  // **PERBAIKAN KRITIS**: Clear lookup cache juga
        inFlightEpcs.clear()
        onDataCleared?.invoke()
        
        Log.d(tag, "🔥 All data cleared: EPCs=$beforeEpcCount, Products=$beforeProductCount")
        Log.d(tag, "🔥 After clear: epcIndexMap=${epcIndexMap.size}, epcToProduct=${epcToProduct.size}")
    }
    
    /**
     * Clear hanya data EPC tapi tetap simpan hasil lookup untuk efisiensi
     * PERHATIAN: Method ini TIDAK menghapus epcToProduct, jadi EPC yang sama tidak akan berbunyi beep lagi
     */
    fun clearEpcDataOnly() {
        val beforeEpcCount = epcIndexMap.size
        val beforeProductCount = epcToProduct.size
        
        epcIndexMap.clear()
        epcList.clear()
        // epcToProduct tetap dipertahankan untuk cache lookup
        inFlightEpcs.clear()
        onDataCleared?.invoke()
        
        Log.d(tag, "🔥 EPC data cleared, lookup cache preserved: EPCs=$beforeEpcCount->0, Products=$beforeProductCount")
        Log.d(tag, "⚠️ WARNING: EPC yang sama tidak akan berbunyi beep lagi karena masih ada di cache")
    }
    
    /**
     * Seed RfidScanManager dengan data EPC dari database
     * Dipanggil saat load picklist yang sudah pernah di-scan
     */
    fun seedWithDatabaseEpcs(epcList: List<String>) {
        Log.d(tag, "🔥 Seeding RfidScanManager with ${epcList.size} EPCs from database")
        
        // Seed epcIndexMap dan epcList
        epcList.forEachIndexed { index, epc ->
            if (!epcIndexMap.containsKey(epc)) {
                epcIndexMap[epc] = index
                this.epcList.add(epc)
            }
        }
        
        Log.d(tag, "✅ Seeded RfidScanManager: ${epcIndexMap.size} EPCs in manager (no placeholder cache)")
        if (epcList.isNotEmpty()) {
            Log.d(tag, "🔥 Seeded EPCs: ${epcList.take(5).joinToString(", ")}${if (epcList.size > 5) "..." else ""}")
        }
    }
    
    /**
     * Seed RfidScanManager dengan lookup results dari database
     * Dipanggil saat load picklist yang sudah pernah di-scan untuk mencegah duplicate processing
     */
    fun seedWithDatabaseLookupResults(epcToProductMap: Map<String, NirwanaApiService.ProductInfo>) {
        Log.d(tag, "🔥 Seeding RfidScanManager with ${epcToProductMap.size} lookup results from database")
        
        // Seed epcToProduct dengan data dari database
        epcToProductMap.forEach { (epc, productInfo) ->
            epcToProduct[epc] = productInfo
        }
        
        Log.d(tag, "✅ Seeded lookup results: ${epcToProduct.size} EPCs in cache")
        if (epcToProductMap.isNotEmpty()) {
            Log.d(tag, "🔥 Seeded lookup results: ${epcToProductMap.keys.take(5).joinToString(", ")}${if (epcToProductMap.size > 5) "..." else ""}")
        }
    }
    
    /**
     * Clear cache lookup yang sudah lama (untuk memory management)
     */
    fun clearOldLookupCache() {
        val beforeSize = epcToProduct.size
        // Hapus cache untuk EPC yang tidak ada di current session
        val currentEpcs = epcIndexMap.keys.toSet()
        epcToProduct.keys.removeAll { epc -> !currentEpcs.contains(epc) }
        val afterSize = epcToProduct.size
        Log.d(tag, "🧹 Cleaned old lookup cache: $beforeSize -> $afterSize items")
    }
    
    /**
     * Remove specific EPCs from both epcIndexMap and epcToProduct
     * Used when items are deleted to prevent re-processing
     */
    fun removeEpcs(epcsToRemove: Set<String>) {
        if (epcsToRemove.isEmpty()) return
        
        val beforeIndexSize = epcIndexMap.size
        val beforeProductSize = epcToProduct.size
        
        // Remove from epcIndexMap and epcList
        epcsToRemove.forEach { epc ->
            epcIndexMap.remove(epc)
            epcList.remove(epc)
        }
        
        // Remove from epcToProduct
        epcToProduct.keys.removeAll { epc -> epcsToRemove.contains(epc) }
        
        val afterIndexSize = epcIndexMap.size
        val afterProductSize = epcToProduct.size
        
        Log.d(tag, "🗑️ Removed EPCs from RfidScanManager: ${epcsToRemove.size} EPCs")
        Log.d(tag, "🗑️   - epcIndexMap: $beforeIndexSize -> $afterIndexSize")
        Log.d(tag, "🗑️   - epcToProduct: $beforeProductSize -> $afterProductSize")
        Log.d(tag, "🗑️   - Removed EPCs: ${epcsToRemove.take(3).joinToString(", ")}${if (epcsToRemove.size > 3) "..." else ""}")
    }

    fun getAllUniqueRfids(): List<String> = epcList.toList()

    fun getUniqueRfidCount(): Int = epcIndexMap.size
    
    /**
     * Trigger lookup untuk semua RFID unik yang tersimpan
     * Dipanggil saat stop scanning atau grace period selesai
     */
    private fun triggerLookupForAllUniqueRfids() {
        val allUniqueRfids = getAllUniqueRfids()
        if (allUniqueRfids.isNotEmpty()) {
            Log.d(tag, "🔥 Triggering lookup for ${allUniqueRfids.size} unique RFIDs after stop")
            
            // **OPTIMASI**: Cek apakah semua EPC sudah ada di cache
            val cachedCount = allUniqueRfids.count { epcToProduct.containsKey(it) }
            if (cachedCount == allUniqueRfids.size) {
                Log.d(tag, "⚡ All EPCs already cached - immediate UI update")
                // Langsung notify UI tanpa API call
                activity.runOnUiThread {
                    try { 
                        onLookupRequired?.invoke(allUniqueRfids) 
                    } catch (t: Throwable) { 
                        Log.e(tag, "❌ UI notify failed: ${t.message}") 
                    }
                }
            } else {
                Log.d(tag, "🔄 ${cachedCount}/${allUniqueRfids.size} EPCs cached - performing lookup")
                performBatchLookup(allUniqueRfids)
            }
        } else {
            Log.d(tag, "ℹ️ No unique RFIDs to lookup")
        }
    }

	/**
	 * Tambahkan EPC yang terbaca. Duplicate removal ditangani dengan Set dan opsi threshold RSSI.
	 */
    fun addRfid(epc: String, rssi: Int) {
        // **PERBAIKAN**: Logging RSSI yang lebih detail untuk debugging
        Log.d(tag, "📡 RFID detected: $epc (rssi=$rssi, threshold=${settings.rssiThreshold})")
        
        // Terapkan threshold bila diset (log sebelum filter)
        if (rssi < settings.rssiThreshold) {
            Log.d(tag, "❌ RFID below RSSI threshold ignored: $epc (rssi=$rssi < threshold=${settings.rssiThreshold})")
            return
        }
        
        Log.d(tag, "✅ RFID passed RSSI filter: $epc (rssi=$rssi >= threshold=${settings.rssiThreshold})")
        
        // **PERBAIKAN KRITIS**: Cek duplicate di epcIndexMap DAN di epcToProduct (data dari database)
        val alreadyExistsInManager = epcIndexMap.containsKey(epc)
        val alreadyExistsInDatabase = epcToProduct.containsKey(epc)
        val alreadyExists = alreadyExistsInManager || alreadyExistsInDatabase
        
        Log.d(tag, "🔍 EPC duplicate check: $epc")
        Log.d(tag, "🔍   - In manager: $alreadyExistsInManager")
        Log.d(tag, "🔍   - In database: $alreadyExistsInDatabase")
        Log.d(tag, "🔍   - Final result: $alreadyExists")
        
        // **DEBUG**: Log isi epcToProduct untuk debugging
        if (epcToProduct.isNotEmpty()) {
            Log.d(tag, "🔍   - Database EPCs: ${epcToProduct.keys.take(5).joinToString(", ")}${if (epcToProduct.size > 5) "..." else ""}")
        } else {
            Log.d(tag, "🔍   - Database EPCs: EMPTY")
        }
        
        if (!alreadyExistsInManager) {
            epcIndexMap[epc] = epcList.size
            epcList.add(epc)
        }
        val isNew = !alreadyExists

        if (isNew) {
            // Hanya invoke callback dan beep untuk RFID baru
            onRfidDetected?.invoke(epc, rssi)
            Log.d(tag, "🔥 RFID NEW: $epc (RSSI=$rssi) total=${epcIndexMap.size}")
            try { playBeepSound() } catch (_: Throwable) {}
            
            // **FIXED**: Tidak melakukan automatic lookup, lookup akan dilakukan saat stop
            Log.d(tag, "ℹ️ EPC $epc stored, lookup will be performed when stop is pressed")
        } else {
            // RFID duplicate - tidak invoke callback dan tidak beep
            Log.d(tag, "ℹ️ Duplicate RFID ignored: $epc (RSSI=$rssi) - already exists in ${if (alreadyExistsInManager) "manager" else "database"}")
        }
    }

    // ====== Lookup results bridge (optional) ======
    fun putLookupResults(epcToInfo: Map<String, NirwanaApiService.ProductInfo>) {
        var newCount = 0
        var duplicateCount = 0
        
        epcToInfo.forEach { (epc, productInfo) ->
            if (!epcToProduct.containsKey(epc)) {
                epcToProduct[epc] = productInfo
                newCount++
            } else {
                duplicateCount++
                Log.d(tag, "ℹ️ Duplicate EPC ignored in lookup results: $epc")
            }
        }
        
        Log.d(tag, "🔥 Stored lookup results: +$newCount new, $duplicateCount duplicates ignored (total=${epcToProduct.size})")
    }

    fun getLookupResult(epc: String): NirwanaApiService.ProductInfo? = epcToProduct[epc]

    fun getAllLookupResults(): Map<String, NirwanaApiService.ProductInfo> = HashMap(epcToProduct)
    
    /**
     * Get hasil lookup yang tersimpan di lokal storage
     * Nama variabel lokal: epcToProduct
     */
    fun getLocalLookupResults(): Map<String, NirwanaApiService.ProductInfo> = HashMap(epcToProduct)
    
    /**
     * Get hasil lookup untuk EPC tertentu
     */
    fun getLookupResultForEpc(epc: String): NirwanaApiService.ProductInfo? = epcToProduct[epc]
    
    /**
     * Get statistics tentang data lokal yang tersimpan
     */
    fun getLocalDataStats(): Map<String, Any> {
        val totalEpc = epcIndexMap.size
        val totalLookupResults = epcToProduct.size
        val inFlightCount = inFlightEpcs.size
        val foundCount = epcToProduct.values.count { it.articleName != "NOT_FOUND" }
        val notFoundCount = epcToProduct.values.count { it.articleName == "NOT_FOUND" }
        
        return mapOf(
            "totalEpc" to totalEpc,
            "totalLookupResults" to totalLookupResults,
            "inFlightLookups" to inFlightCount,
            "foundProducts" to foundCount,
            "notFoundProducts" to notFoundCount,
            "lookupSuccessRate" to if (totalLookupResults > 0) (foundCount.toDouble() / totalLookupResults * 100) else 0.0
        )
    }
    
    /**
     * Check apakah EPC sudah ada di cache lokal
     */
    fun isEpcInLocalCache(epc: String): Boolean = epcToProduct.containsKey(epc)
    
    /**
     * Get semua EPC yang sedang dalam proses lookup
     */
    fun getInFlightEpcs(): Set<String> = inFlightEpcs.toSet()
    
    /**
     * Batch lookup untuk multiple EPC sekaligus dengan optimasi
     */
    fun performBatchLookup(epcList: List<String>) {
        if (epcList.isEmpty()) return
        
        // Filter EPC yang belum ada di cache dan tidak sedang dalam proses
        val epcsToLookup = epcList.filter { epc ->
            !epcToProduct.containsKey(epc) && !inFlightEpcs.contains(epc)
        }
        
        if (epcsToLookup.isEmpty()) {
            Log.d(tag, "ℹ️ All EPCs already in cache or in progress")
            // Notify UI dengan data yang sudah ada
            activity.runOnUiThread {
                try { 
                    onLookupRequired?.invoke(epcList) 
                } catch (t: Throwable) { 
                    Log.e(tag, "❌ UI notify failed: ${t.message}") 
                }
            }
            return
        }
        
        Log.d(tag, "🔥 Starting batch lookup for ${epcsToLookup.size} EPCs (${epcList.size - epcsToLookup.size} already cached)")
        
        // Mark EPCs as in-flight
        inFlightEpcs.addAll(epcsToLookup)
        
        lookupExecutor.execute {
            try {
                val service = NirwanaApiService()
                val products = kotlinx.coroutines.runBlocking { 
                    service.batchLookupRfidList(epcsToLookup) 
                }
                
                // Process results
                val resultMap = HashMap<String, NirwanaApiService.ProductInfo>()
                products.forEach { product ->
                    product.rfidList.forEach { rfidEpc -> 
                        resultMap[rfidEpc] = product
                    }
                }
                
                // Store lookup results
                putLookupResults(resultMap)
                
                // Handle EPCs that were not found
                epcsToLookup.forEach { epc ->
                    if (!resultMap.containsKey(epc)) {
                        epcToProduct[epc] =                         NirwanaApiService.ProductInfo(
                            productId = "",
                            productName = "NOT_FOUND",
                            articleId = "",
                            articleName = "NOT_FOUND",
                            brand = "",
                            category = "",
                            subCategory = "",
                            color = "",
                            gender = "",
                            size = "",
                            warehouse = "",
                            tagStatus = "",
                            qty = 0,
                            rfidList = listOf(epc)
                        )
                    }
                }
                
                // Notify UI
                activity.runOnUiThread {
                    try { 
                        onLookupRequired?.invoke(epcList) 
                    } catch (t: Throwable) { 
                        Log.e(tag, "❌ UI notify failed: ${t.message}") 
                    }
                }
                
                Log.d(tag, "✅ Batch lookup completed: ${products.size} products found for ${epcsToLookup.size} EPCs")
                
            } catch (t: Throwable) {
                Log.e(tag, "❌ Batch lookup error: ${t.message}")
            } finally {
                // Remove from in-flight
                inFlightEpcs.removeAll(epcsToLookup)
            }
        }
    }
    
    /**
     * Perform lookup untuk EPC baru secara background dengan optimasi
     */
    private fun performLookupForEpc(epc: String) {
        // Cek apakah EPC sudah ada di cache lokal
        if (epcToProduct.containsKey(epc)) {
            Log.d(tag, "ℹ️ EPC already in local cache: $epc")
            // Notify UI dengan data yang sudah ada
            activity.runOnUiThread {
                try { 
                    onLookupRequired?.invoke(listOf(epc)) 
                } catch (t: Throwable) { 
                    Log.e(tag, "❌ UI notify failed: ${t.message}") 
                }
            }
            return
        }
        
        if (inFlightEpcs.contains(epc)) {
            Log.d(tag, "ℹ️ Lookup already in progress for EPC: $epc")
            return
        }
        
        inFlightEpcs.add(epc)
        lookupExecutor.execute {
            try {
                Log.d(tag, "🔥 Starting lookup for EPC: $epc")
                
                val service = NirwanaApiService()
                val products = kotlinx.coroutines.runBlocking { 
                    service.batchLookupRfidList(listOf(epc)) 
                }
                
                if (products.isNotEmpty()) {
                    val product = products.first()
                    val map = HashMap<String, NirwanaApiService.ProductInfo>()
                    product.rfidList.forEach { rfidEpc -> 
                        map[rfidEpc] = product
                    }
                    
                    // Store lookup results di lokal storage
                    putLookupResults(map)
                    
                    // Notify UI dengan hasil lookup
                    activity.runOnUiThread {
                        try { 
                            onLookupRequired?.invoke(listOf(epc)) 
                        } catch (t: Throwable) { 
                            Log.e(tag, "❌ UI notify failed: ${t.message}") 
                        }
                    }
                    
                    Log.d(tag, "✅ Lookup completed for EPC: $epc -> ${product.articleName}")
                } else {
                    Log.w(tag, "⚠️ No product found for EPC: $epc")
                    // Simpan EPC yang tidak ditemukan untuk menghindari lookup berulang
                    epcToProduct[epc] =                         NirwanaApiService.ProductInfo(
                            productId = "",
                            productName = "NOT_FOUND",
                            articleId = "",
                            articleName = "NOT_FOUND",
                            brand = "",
                            category = "",
                            subCategory = "",
                            color = "",
                            gender = "",
                            size = "",
                            warehouse = "",
                            tagStatus = "",
                            qty = 0,
                            rfidList = listOf(epc)
                        )
                }
                
            } catch (t: Throwable) {
                Log.e(tag, "❌ Lookup error for EPC $epc: ${t.message}")
            } finally {
                inFlightEpcs.remove(epc)
            }
        }
    }

	fun cleanup() {
		try {
			reader?.setInventoryCallback(null)
			reader?.stopInventory()
			reader?.free()
			
			// Shutdown lookup executor
			lookupExecutor.shutdownNow()
			
		} catch (e: Exception) {
			Log.w(tag, "⚠️ Error during cleanup: ${e.message}")
		}
		reader = null
		Log.d(tag, "🔥 Cleanup done")
	}

	// ===================== SDK Integration =====================

	private fun initializeReaderSafely() {
		try {
            reader = RFIDWithUHFUART.getInstance()
            val ok = reader?.init(context) == true
            Log.d(tag, "🔥 Reader init: $ok")
            applySettingsToReader()
            // set callback
            reader?.setInventoryCallback(object : IUHFInventoryCallback {
                override fun callback(uhfTagInfo: UHFTAGInfo) {
					// Ignore any callbacks when not actively scanning or during grace period
					if (!isScanningInternal || isGraceActive) {
						Log.d(tag, "ℹ️ Callback ignored (scanning=$isScanningInternal, grace=$isGraceActive)")
						return
					}
                    val epc = try { uhfTagInfo.getEPC() } catch (t: Throwable) { "" }
                    val rssiStr = try { uhfTagInfo.getRssi() } catch (t: Throwable) { null }
                    val rssi = parseRssiToDbm(rssiStr)
                    Log.d(tag, "📡 RAW CALLBACK: epc='$epc', rssiRaw='${rssiStr}', rssiDbm=$rssi")
                    if (epc.isNotEmpty()) addRfid(epc, rssi) else Log.w(tag, "⚠️ Empty EPC received")
                }
            })
            // Coba set mode EPC agar output standar EPC
            try {
                reader?.setEPCMode()
                Log.d(tag, "🔥 EPC mode set")
            } catch (t: Throwable) {
                Log.w(tag, "⚠️ setEPCMode not available: ${t.message}")
            }
		} catch (e: ConfigurationException) {
			onError?.invoke("Device not supported: ${e.message}")
			Log.e(tag, "❌ ConfigurationException: ${e.message}")
		} catch (t: Throwable) {
			onError?.invoke("Init error: ${t.message}")
			Log.e(tag, "❌ Init error: ${t.message}")
		}
	}
	
	/**
	 * Initialize settings from SharedPreferences
	 */
	private fun initializeSettingsFromSharedPreferences() {
		try {
			val sharedPreferences = activity.getSharedPreferences("RFIDSettings", android.content.Context.MODE_PRIVATE)
			val powerLevel = sharedPreferences.getInt("power_level", 20)
			val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -70)
			val gracePeriod = sharedPreferences.getLong("grace_period", 0L)
			
			settings = RfidScanSettings(
				powerLevel = powerLevel,
				duplicateRemovalEnabled = true, // Always enabled
				gracePeriodMs = gracePeriod,
				rssiThreshold = rssiThreshold
			)
			
			Log.d(tag, "🔥 Settings initialized from SharedPreferences: $settings")
		} catch (e: Exception) {
			Log.e(tag, "❌ Error initializing settings from SharedPreferences: ${e.message}", e)
			// Keep default settings if error occurs
		}
	}

    private fun parseRssiToDbm(raw: String?): Int {
        if (raw.isNullOrBlank()) return -60
        // Ambil angka float bertanda pertama, contoh: "-33.70dBm" → -33.70
        val match = Regex("-?\\d+\\.?\\d*").find(raw)
        val asFloat = match?.value?.toFloatOrNull()
        val value = asFloat?.toInt() ?: raw.toIntOrNull() ?: -60
        // Clamp agar tetap dalam rentang wajar dBm
        return value.coerceIn(-120, 0)
    }

	private fun applySettingsToReader() {
		try {
            // **PERBAIKAN**: Power control dengan validasi dan logging yang lebih detail
            val powerLevel = settings.powerLevel.coerceIn(1, 30) // **SELARASKAN**: Clamp power level 1-30 (sesuai uhf-uart-demo)
            reader?.setPower(powerLevel)
            Log.d(tag, "🔥 Power level set to: $powerLevel (requested: ${settings.powerLevel})")
            
            // **VERIFIKASI**: Coba baca power level yang benar-benar diterapkan
            try {
                val actualPower = reader?.getPower()
                Log.d(tag, "🔥 Power level verification: requested=$powerLevel, actual=$actualPower")
                if (actualPower != null && actualPower != powerLevel) {
                    Log.w(tag, "⚠️ Power level mismatch! Requested: $powerLevel, Actual: $actualPower")
                }
            } catch (t: Throwable) {
                Log.w(tag, "⚠️ Cannot verify power level: ${t.message}")
            }
            
            // Gunakan refleksi untuk kompatibilitas berbagai versi SDK
            try {
                val method = reader?.javaClass?.getMethod(
                    "setDuplicateRemoval",
                    Boolean::class.javaPrimitiveType
                )
                method?.invoke(reader, settings.duplicateRemovalEnabled)
                Log.d(tag, "🔥 setDuplicateRemoval applied via reflection: ${settings.duplicateRemovalEnabled}")
            } catch (t: Throwable) {
                Log.w(tag, "⚠️ setDuplicateRemoval not available: ${t.message}")
            }
            
            // **PERBAIKAN**: Coba set power lagi setelah duplicate removal untuk memastikan
            reader?.setPower(powerLevel)
            Log.d(tag, "🔥 Power level re-applied: $powerLevel")
            
            // **VERIFIKASI FINAL**: Baca power level sekali lagi
            try {
                val finalPower = reader?.getPower()
                Log.d(tag, "🔥 Final power level verification: $finalPower")
            } catch (t: Throwable) {
                Log.w(tag, "⚠️ Cannot verify final power level: ${t.message}")
            }
            
		} catch (t: Throwable) {
			Log.w(tag, "⚠️ Apply settings failed: ${t.message}")
		}
	}

	private fun startInventorySafely(): Boolean {
        return try {
            // **PERBAIKAN KRITIS**: Apply settings sebelum start inventory untuk memastikan power level diterapkan
            applySettingsToReader()
            Log.d(tag, "🔥 Settings applied before starting inventory")
            
            // **VERIFIKASI SETTINGS**: Log settings yang akan digunakan untuk scanning
            Log.d(tag, "🔥 === SCANNING SETTINGS VERIFICATION ===")
            Log.d(tag, "🔥 Power Level: ${settings.powerLevel}")
            Log.d(tag, "🔥 RSSI Threshold: ${settings.rssiThreshold}")
            Log.d(tag, "🔥 Duplicate Removal: ${settings.duplicateRemovalEnabled}")
            Log.d(tag, "🔥 Grace Period: ${settings.gracePeriodMs}ms")
            
            // **VERIFIKASI HARDWARE**: Coba baca power level dari hardware
            try {
                val hardwarePower = reader?.getPower()
                Log.d(tag, "🔥 Hardware Power Level: $hardwarePower")
                if (hardwarePower != null && hardwarePower != settings.powerLevel) {
                    Log.w(tag, "⚠️ WARNING: Settings power (${settings.powerLevel}) != Hardware power ($hardwarePower)")
                }
            } catch (t: Throwable) {
                Log.w(tag, "⚠️ Cannot read hardware power level: ${t.message}")
            }
            Log.d(tag, "🔥 === END SETTINGS VERIFICATION ===")
            
            // Coba gunakan InventoryParameter seperti pada demo untuk hasil yang lebih stabil
            val param = try {
                InventoryParameter().apply {
                    // Minimal: aktifkan result data default (tanpa phase)
                    try { setResultData(InventoryParameter.ResultData()) } catch (_: Throwable) {}
                }
            } catch (t: Throwable) {
                null
            }
            val started = if (param != null) {
                try { reader?.startInventoryTag(param) == true } catch (_: Throwable) { reader?.startInventoryTag() == true }
            } else {
                reader?.startInventoryTag() == true
            }
            Log.d(tag, "🔥 Inventory started: $started (power=${settings.powerLevel})")
            started
		} catch (t: Throwable) {
			Log.e(tag, "❌ startInventoryTag failed: ${t.message}")
			onError?.invoke("Start scan failed: ${t.message}")
			false
		}
	}

	private fun stopInventorySafely() {
		try {
			reader?.stopInventory()
		} catch (t: Throwable) {
			Log.w(tag, "⚠️ stopInventory failed: ${t.message}")
		}
	}
}


