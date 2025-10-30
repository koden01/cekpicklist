package com.example.cekpicklist.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cekpicklist.data.*
import com.example.cekpicklist.api.NirwanaApiService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class RelocationViewModel(application: Application) : AndroidViewModel(application) {
    // Debug flag: aktifkan untuk mengetes beberapa EPC yang MISSING secara single-lookup
    private val ENABLE_MISSING_EPC_TEST = true
    
    // SharedPreferences untuk cache warehouse
    private val prefs: SharedPreferences = application.getSharedPreferences("warehouse_cache", Context.MODE_PRIVATE)
    private val WAREHOUSE_CACHE_KEY = "warehouse_data"
    private val WAREHOUSE_CACHE_TIMESTAMP_KEY = "warehouse_timestamp"
    
    // LiveData untuk UI
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _successMessage = MutableLiveData<String>()
    val successMessage: LiveData<String> = _successMessage
    
    // Callback untuk clear RfidScanManager
    private var onRfidScanManagerClear: (() -> Unit)? = null
    
    // RFID Detection
    private val _rfidDetectionCount = MutableLiveData<Int>()
    val rfidDetectionCount: LiveData<Int> = _rfidDetectionCount
    
    private val _rfidValidCount = MutableLiveData<Int>()
    val rfidValidCount: LiveData<Int> = _rfidValidCount
    
    private val _rfidNaCount = MutableLiveData<Int>()
    val rfidNaCount: LiveData<Int> = _rfidNaCount
    
    // Relocation Items
    private val _relocationItems = MutableLiveData<List<RelocationItem>>()
    val relocationItems: LiveData<List<RelocationItem>> = _relocationItems
    
    // Warehouses dengan cache
    private val _warehouses = MutableLiveData<List<com.example.cekpicklist.data.Warehouse>>(emptyList())
    val warehouses: LiveData<List<com.example.cekpicklist.data.Warehouse>> = _warehouses
    
    // Cache warehouse dengan timestamp (15 jam)
    private val WAREHOUSE_CACHE_DURATION = 15 * 60 * 60 * 1000L // 15 jam dalam milliseconds
    
    private val _selectedCurrentWarehouse = MutableLiveData<com.example.cekpicklist.data.Warehouse?>()
    val selectedCurrentWarehouse: LiveData<com.example.cekpicklist.data.Warehouse?> = _selectedCurrentWarehouse
    
    private val _selectedTargetWarehouse = MutableLiveData<com.example.cekpicklist.data.Warehouse?>()
    val selectedTargetWarehouse: LiveData<com.example.cekpicklist.data.Warehouse?> = _selectedTargetWarehouse
    
    private val _selectedTagStatus = MutableLiveData<TagStatus?>()
    val selectedTagStatus: LiveData<TagStatus?> = _selectedTagStatus
    
    // State variables
    private val scannedRfids = mutableSetOf<String>()  // **PERBAIKAN**: Kembalikan ke Set untuk menyimpan hanya RFID unik
    private val validRfids = mutableSetOf<String>()
    private var isStopping: Boolean = false
    private var isLookupRunning: Boolean = false
    
    // API Service
    private val apiService = NirwanaApiService()
    
    init {
        Log.d("RelocationViewModel", "🔥 Initializing RelocationViewModel...")
        updateRfidCounters()
        loadWarehouses()
    }
    
    /**
     * Tambahkan RFID yang terdeteksi
     * @param epc EPC yang dideteksi
     * @return Boolean - true jika RFID unik (baru), false jika sudah ada
     */
    fun addRfid(epc: String): Boolean {
        val isNewRfid = scannedRfids.add(epc)  // **PERBAIKAN**: Hanya tambahkan RFID unik
        if (isNewRfid) {
            Log.d("RelocationViewModel", "🔥 RFID added (UNIQUE): $epc (total unique: ${scannedRfids.size})")
            updateRfidCounters()
        } else {
            Log.d("RelocationViewModel", "ℹ️ RFID already exists: $epc (total unique: ${scannedRfids.size})")
        }
        return isNewRfid
    }
    
    /**
     * Hapus RFID dari list
     */
    fun removeRfid(epc: String) {
        val removed = scannedRfids.remove(epc)  // **PERBAIKAN**: Hapus RFID unik
        validRfids.remove(epc)
        Log.d("RelocationViewModel", "🔥 RFID removed: $epc (removed: $removed)")
        updateRfidCounters()
        
        // **PERBAIKAN**: Untuk data yang sudah diagregasi, perlu re-lookup untuk update qty
        // Karena menghapus 1 RFID bisa mempengaruhi qty dari artikel yang sama
        val currentItems = _relocationItems.value ?: emptyList()
        
        // Cari item yang mengandung RFID yang dihapus
        val itemToUpdate = currentItems.find { item ->
            item.epc.split(",").contains(epc)
        }
        
        if (itemToUpdate != null) {
            // Jika item ditemukan, perlu re-lookup untuk update qty yang akurat
            Log.d("RelocationViewModel", "🔥 Item found with RFID $epc, re-applying lookup results")
            // Note: lookupRfids method removed - lookup now handled by RfidScanManager
            // Lookup results will be automatically updated by RfidScanManager
        } else {
            // Jika tidak ditemukan, hapus item secara langsung
            val updatedItems = currentItems.filter { item ->
                !item.epc.split(",").contains(epc)
            }
            
            // **SORTING**: Invalid items (isValid = false) selalu di atas
            val sortedItems = updatedItems.sortedWith(compareBy<RelocationItem> { 
                if (!it.isValid) 0 else 1  // Invalid items (0) di atas, valid items (1) di bawah
            }.thenBy { 
                if (!it.isValid) it.warehouse else ""  // Untuk invalid: urutkan berdasarkan warehouse
            }.thenBy { 
                if (!it.isValid) it.tagStatus else ""  // Untuk invalid: urutkan berdasarkan tagStatus
            }.thenBy { 
                if (it.isValid) it.articleName else ""  // Untuk valid: urutkan berdasarkan articleName
            })
            
            _relocationItems.value = sortedItems
        }
    }
    
    /**
     * Clear semua RFID yang di-scan (untuk memulai ulang scanning)
     */
    fun clearAllRfids() {
        scannedRfids.clear()
        validRfids.clear()
        _relocationItems.value = emptyList()
        updateRfidCounters()
        Log.d("RelocationViewModel", "🔥 All RFIDs cleared - ready for new scanning")
    }
    
    /**
     * Clear hanya RFID yang invalid (tetap simpan yang valid)
     */
    fun clearInvalidRfids() {
        val validItems = _relocationItems.value?.filter { it.isValid } ?: emptyList()
        
        // **PERBAIKAN**: Untuk data yang sudah diagregasi, perlu extract semua RFID dari valid items
        val allValidRfids = validItems.flatMap { item ->
            item.epc.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        
        // Update scannedRfids - hanya simpan yang valid
        scannedRfids.clear()
        scannedRfids.addAll(allValidRfids)
        
        // Update validRfids
        validRfids.clear()
        validRfids.addAll(allValidRfids)
        
        // **SORTING**: Invalid items (isValid = false) selalu di atas
        val sortedValidItems = validItems.sortedWith(compareBy<RelocationItem> { 
            if (!it.isValid) 0 else 1  // Invalid items (0) di atas, valid items (1) di bawah
        }.thenBy { 
            if (!it.isValid) it.warehouse else ""  // Untuk invalid: urutkan berdasarkan warehouse
        }.thenBy { 
            if (!it.isValid) it.tagStatus else ""  // Untuk invalid: urutkan berdasarkan tagStatus
        }.thenBy { 
            if (it.isValid) it.articleName else ""  // Untuk valid: urutkan berdasarkan articleName
        })
        
        // Update UI
        _relocationItems.value = sortedValidItems
        updateRfidCounters()
        
        Log.d("RelocationViewModel", "🔥 Invalid RFIDs cleared - ${validItems.size} valid items kept")
        Log.d("RelocationViewModel", "🔥 Total valid RFIDs: ${allValidRfids.size}")
    }
    
    /**
     * Clear semua data dan reset ke state awal (setelah submit berhasil)
     */
    fun clearAllAndReset() {
        scannedRfids.clear()
        validRfids.clear()
        _relocationItems.value = emptyList()
        _errorMessage.value = ""
        _successMessage.value = ""
        updateRfidCounters()
        Log.d("RelocationViewModel", "🔥 All data cleared and reset - ready for new relocation")
    }
    
    /**
     * Set callback untuk clear RfidScanManager
     */
    fun setOnRfidScanManagerClear(callback: () -> Unit) {
        onRfidScanManagerClear = callback
    }
    
    /**
     * Update RFID counters
     * PERUBAHAN: NA dihitung dari total qty item NA di list, bukan Unique-Valid
     */
    private fun updateRfidCounters() {
        // Definisi baru:
        // - Unique = total RFID unik yang terscan
        // - Valid = total qty valid (sum qty untuk group isValid)
        // - INVALID = total qty tidak valid (unique - valid)
        val items = _relocationItems.value ?: emptyList()
        val totalUnique = scannedRfids.size
        val totalValidQty = items.filter { it.isValid }.sumOf { it.qty }
        val totalInvalidQty = (totalUnique - totalValidQty).coerceAtLeast(0)

        _rfidDetectionCount.value = totalUnique
        _rfidValidCount.value = totalValidQty
        _rfidNaCount.value = totalInvalidQty

        Log.d("RelocationViewModel", "📊 Counters:")
        Log.d("RelocationViewModel", "📊   Unique (EPC unik): $totalUnique")
        Log.d("RelocationViewModel", "📊   Valid (qty EPC valid): $totalValidQty")
        Log.d("RelocationViewModel", "📊   INVALID (qty EPC tidak valid): $totalInvalidQty")
    }

    /**
     * Lookup dengan finalization window agar EPC yang terlambat tetap ter-lookup
     */
    fun lookupWithFinalizationWindow(tagStatus: String) {
        if (scannedRfids.isEmpty()) {
            _errorMessage.value = "Tidak ada RFID yang di-scan"
            return
        }
        val currentWarehouseId = getSelectedCurrentWarehouseId()
        if (currentWarehouseId == null) {
            _errorMessage.value = "Pilih current warehouse terlebih dahulu"
            return
        }

        viewModelScope.launch {
            try {
                if (isLookupRunning) {
                    Log.d("RelocationViewModel", "ℹ️ Finalized lookup ignored - another lookup is running")
                    return@launch
                }
                isLookupRunning = true
                isStopping = true
                _isLoading.value = true
                _errorMessage.value = ""

                val postedSnapshot = scannedRfids.toSet()
                Log.d("RelocationViewModel", "🛑 Finalization start - postedSnapshot size: ${postedSnapshot.size}")

                // Grace period untuk menangkap EPC yang terlambat
                delay(250)

                val finalUnique = scannedRfids.toSet()
                val delta = finalUnique.minus(postedSnapshot)
                Log.d("RelocationViewModel", "🛑 Finalization end - finalUnique: ${finalUnique.size}, delta: ${delta.size}")

                // Fetch products untuk snapshot awal
                val productsAll = mutableListOf<NirwanaApiService.ProductInfo>()
                withContext(Dispatchers.IO) {
                    val p1 = apiService.batchLookupRfidList(postedSnapshot.toList())
                    productsAll.addAll(p1)
                }

                // Jika ada delta, kirim satu request tambahan
                if (delta.isNotEmpty()) {
                    Log.d("RelocationViewModel", "🛑 Sending delta lookup for ${delta.size} EPC(s)")
                    withContext(Dispatchers.IO) {
                        val p2 = apiService.batchLookupRfidList(delta.toList())
                        productsAll.addAll(p2)
                    }
                }

                // Proses dan publish hasil gabungan
                processLookupResults(productsAll, currentWarehouseId, tagStatus)

            } catch (e: Exception) {
                Log.e("RelocationViewModel", "❌ Error during finalized lookup: ${e.message}", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                isStopping = false
                isLookupRunning = false
                _isLoading.value = false
            }
        }
    }

    private fun processLookupResults(products: List<NirwanaApiService.ProductInfo>, currentWarehouseId: String, tagStatus: String) {
        // **DETAILED LOGGING**
        Log.d("RelocationViewModel", "🔥 === API RESPONSE ===")
        Log.d("RelocationViewModel", "🔥 Products count from API: ${products.size}")
        Log.d("RelocationViewModel", "🔥 Total RFIDs scanned: ${scannedRfids.size}")
        products.forEachIndexed { index, product ->
            Log.d("RelocationViewModel", "🔥 API Product $index: EPC=${product.rfidList.firstOrNull()}, ArticleID=${product.articleId}, ArticleName=${product.articleName}")
            if (product.articleId == "NA" || product.articleName == "NA") {
                Log.d("RelocationViewModel", "🔥 ⭐ NA ITEM FROM API: EPC=${product.rfidList.firstOrNull()}, Warehouse=${product.warehouse}, TagStatus=${product.tagStatus}")
            }
        }
        Log.d("RelocationViewModel", "🔥 === END API RESPONSE ===")

        if (products.isNotEmpty()) {
            validRfids.clear()

            // **MAPPING**: Buat mapping dari warehouse ID ke warehouse name untuk validasi
            val warehouseIdToNameMap = _warehouses.value?.associate { 
                it.warehouseId.trim().uppercase() to it.warehouseName.trim().uppercase() 
            } ?: emptyMap()
            
            val groupedProducts = products.groupBy { product ->
                val isRegistered = product.articleName != "NA" && product.articleId != "NA"
                val productWarehouse = product.warehouse?.trim()?.uppercase()
                val productTagStatus = product.tagStatus?.trim()?.uppercase()
                val currentWarehouseId = currentWarehouseId?.trim()?.uppercase()
                val tagStatusUpper = tagStatus?.trim()?.uppercase()
                // **FIX**: Bandingkan dengan ID warehouse yang dipilih user
                val expectedWarehouseName = warehouseIdToNameMap[currentWarehouseId]
                val matchesLocation = (productWarehouse == expectedWarehouseName && productTagStatus == tagStatusUpper)
                val isValid = isRegistered && matchesLocation
                if (isValid) {
                    "${product.articleId}|${product.articleName}|${product.size}"
                } else {
                    // **PERBAIKAN**: Grouping per artikel+size+warehouse+status agar konsisten dengan applyLookupResults
                    "${product.articleName}|${product.size}|${product.warehouse}|${product.tagStatus}"
                }
            }

            Log.d("RelocationViewModel", "🔥 Total products from API: ${products.size}")
            Log.d("RelocationViewModel", "🔥 Grouped into ${groupedProducts.size} unique articles")

            val rawItems = groupedProducts.map { (key, productGroup) ->
                val firstProduct = productGroup.first()
                val isRegistered = firstProduct.articleName != "NA" && firstProduct.articleId != "NA"
                val productWarehouse = firstProduct.warehouse?.trim()?.uppercase()
                val productTagStatus = firstProduct.tagStatus?.trim()?.uppercase()
                val currentWarehouseId = currentWarehouseId?.trim()?.uppercase()
                val tagStatusUpper = tagStatus?.trim()?.uppercase()
                // **FIX**: Bandingkan dengan ID warehouse yang dipilih user
                val expectedWarehouseName = warehouseIdToNameMap[currentWarehouseId]
                val matchesLocation = (productWarehouse == expectedWarehouseName && productTagStatus == tagStatusUpper)
                val isValid = isRegistered && matchesLocation
                
                // **DEBUG LOGGING**: Log validasi detail
                Log.d("RelocationViewModel", "🔍 VALIDATION: ${firstProduct.articleName} ${firstProduct.size}")
                Log.d("RelocationViewModel", "🔍   - isRegistered: $isRegistered (articleName='${firstProduct.articleName}', articleId='${firstProduct.articleId}')")
                Log.d("RelocationViewModel", "🔍   - productWarehouse: '$productWarehouse' vs expectedWarehouse: '$expectedWarehouseName'")
                Log.d("RelocationViewModel", "🔍   - productTagStatus: '$productTagStatus' vs tagStatus: '$tagStatusUpper'")
                Log.d("RelocationViewModel", "🔍   - matchesLocation: $matchesLocation")
                Log.d("RelocationViewModel", "🔍   - FINAL isValid: $isValid")

                val totalQty = productGroup.sumOf { it.qty }
                val allRfids = productGroup.flatMap { it.rfidList }

                if (isValid) {
                    validRfids.addAll(allRfids)
                }

                RelocationItem(
                    epc = allRfids.joinToString(","),
                    articleId = firstProduct.articleId,
                    articleName = firstProduct.articleName,
                    size = firstProduct.size,
                    qty = totalQty,
                    productId = firstProduct.productId,
                    productName = firstProduct.productName,
                    brand = firstProduct.brand,
                    category = firstProduct.category,
                    subCategory = firstProduct.subCategory,
                    color = firstProduct.color,
                    gender = firstProduct.gender,
                    warehouse = firstProduct.warehouse,
                    tagStatus = firstProduct.tagStatus,
                    isValid = isValid
                )
            }
            // **AGGREGATION**: Gabungkan INVALID per warehouse+tagStatus (sum qty, merge EPC)
            val items = aggregateInvalidItems(rawItems)

            // **SORTING**: Invalid items (isValid = false) selalu di atas
            val sortedItems = items.sortedWith(compareBy<RelocationItem> {
                if (!it.isValid) 0 else 1  // Invalid items (0) di atas, valid items (1) di bawah
            }.thenBy {
                if (!it.isValid) it.warehouse else ""  // Untuk invalid: urutkan berdasarkan warehouse
            }.thenBy {
                if (!it.isValid) it.tagStatus else ""  // Untuk invalid: urutkan berdasarkan tagStatus
            }.thenBy {
                if (it.isValid) it.articleName else ""  // Untuk valid: urutkan berdasarkan articleName
            })

            _relocationItems.value = sortedItems
            updateRfidCounters()

            val validCount = items.count { it.isValid }
            val invalidCount = items.size - validCount
            val naCount = items.count { it.articleName == "NA" || it.articleId == "NA" }

            val totalUnique = scannedRfids.size
            val totalValid = validRfids.size
            val totalNa = totalUnique - totalValid

            Log.d("RelocationViewModel", "🔥 === FORMULA VERIFICATION ===")
            Log.d("RelocationViewModel", "🔥 RFID Unique (scanned): $totalUnique")
            Log.d("RelocationViewModel", "🔥 RFID Valid (lookup success): $totalValid")
            Log.d("RelocationViewModel", "🔥 RFID NA (lookup failed): $totalNa")
            Log.d("RelocationViewModel", "🔥 Formula Check: $totalValid + $totalNa = ${totalValid + totalNa} (should equal $totalUnique)")
            Log.d("RelocationViewModel", "🔥 Formula Valid: ${(totalValid + totalNa) == totalUnique}")
            Log.d("RelocationViewModel", "🔥 === END FORMULA VERIFICATION ===")

            _successMessage.value = "Lookup berhasil: $validCount item valid, $invalidCount item invalid, $naCount NA items"

            if (ENABLE_MISSING_EPC_TEST) {
                try {
                    val returnedValid: Set<String> = validRfids.toSet()
                    val missingEpcs: List<String> = scannedRfids.filter { it !in returnedValid }
                    val sampleSize = kotlin.math.min(5, missingEpcs.size)
                    Log.d("RelocationViewModel", "🧪 Single-EPC test for missing RFIDs: taking $sampleSize of ${missingEpcs.size}")

                    if (sampleSize > 0) {
                        val sample = missingEpcs.take(sampleSize)
                        viewModelScope.launch(Dispatchers.IO) {
                            sample.forEachIndexed { idx, epc ->
                                try {
                                    Log.d("RelocationViewModel", "🧪 Testing missing EPC[$idx/$sampleSize]: $epc")
                                    val info = apiService.getProductInfoByEpc(epc)
                                    if (info == null) {
                                        Log.w("RelocationViewModel", "🧪 Result: NOT FOUND for EPC=$epc")
                                    } else {
                                        Log.d(
                                            "RelocationViewModel",
                                            "🧪 Result: FOUND EPC=$epc → articleId='${info.articleId}', articleName='${info.articleName}', " +
                                                "size='${info.size}', warehouse='${info.warehouse}', tagStatus='${info.tagStatus}', qty=${info.qty}"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("RelocationViewModel", "🧪 Error testing EPC=$epc: ${e.message}", e)
                                }
                            }
                            Log.d("RelocationViewModel", "🧪 Single-EPC test completed for $sampleSize EPCs")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("RelocationViewModel", "⚠️ Failed running single-EPC test: ${e.message}")
                }
            }
        } else {
            _errorMessage.value = "Tidak ada data ditemukan untuk RFID yang di-scan"
        }
    }
    
    /**
     * Submit valid RFIDs untuk update tag status
     */
    fun submitRelocation(tagStatus: String) {
        if (validRfids.isEmpty()) {
            _errorMessage.value = "Tidak ada RFID valid untuk di-submit"
            return
        }
        
        val targetWarehouseId = getSelectedTargetWarehouseId()
        if (targetWarehouseId == null) {
            _errorMessage.value = "Pilih target warehouse terlebih dahulu"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = ""
                
                val request = RelocationUpdateRequest(
                    warehouse_id = targetWarehouseId,  // ✅ Menggunakan TARGET warehouse untuk update
                    tag_status = tagStatus,
                    rfid_list = validRfids.toList()
                )
                
                Log.d("RelocationViewModel", "🔥 Submitting relocation: ${validRfids.size} RFIDs")
                Log.d("RelocationViewModel", "🔥 Target Warehouse: $targetWarehouseId, TagStatus: $tagStatus")
                
                val response = withContext(Dispatchers.IO) {
                    apiService.updateRelocationTagStatus(request)
                }
                
                if (response.success) {
                    Log.d("RelocationViewModel", "🔥 Submit successful: ${validRfids.size} items updated")
                    _successMessage.value = response.message
                    // Jangan clear data setelah submit agar bisa lookup ulang untuk perpindahan berikutnya
                    // Biarkan scannedRfids, validRfids, dan _relocationItems tetap ada
                    
                    // Setelah submit berhasil, alihkan konteks ke TARGET sebagai CURRENT dan lakukan lookup ulang
                    try {
                        // 1) Set current warehouse = target warehouse
                        val targetWh = _selectedTargetWarehouse.value
                        if (targetWh != null) {
                            _selectedCurrentWarehouse.value = targetWh
                            Log.d("RelocationViewModel", "🔄 Switched current warehouse to target: ${targetWh.warehouseName} (${targetWh.warehouseId})")
                        } else {
                            Log.w("RelocationViewModel", "⚠️ Target warehouse is null after submit - cannot switch current warehouse")
                        }
                        
                        // 2) Set current tag status = target tag status
                        val newStatusEnum = try { TagStatus.valueOf(tagStatus) } catch (t: Throwable) { null }
                        if (newStatusEnum != null) {
                            setSelectedTagStatus(newStatusEnum)
                            Log.d("RelocationViewModel", "🔄 Switched current tag status to: ${newStatusEnum}")
                        } else {
                            Log.w("RelocationViewModel", "⚠️ Unable to parse target tag status: '$tagStatus'")
                        }
                        
                        // 3) Lakukan lookup ulang menggunakan setting yang baru (CURRENT=TARGET)
                        performLookupWithCurrentSettings()
                        Log.d("RelocationViewModel", "🔄 Re-lookup triggered after submit with updated settings")
                    } catch (t: Throwable) {
                        Log.w("RelocationViewModel", "⚠️ Post-submit refresh failed: ${t.message}")
                    }
                    
                } else {
                    _errorMessage.value = response.message
                }
                
            } catch (e: Exception) {
                Log.e("RelocationViewModel", "❌ Error during submit: ${e.message}", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get list RFID yang ter-scan
     */
    fun getScannedRfids(): List<String> = scannedRfids.toList()
    
    /**
     * Get list RFID yang valid
     */
    fun getValidRfids(): List<String> = validRfids.toList()
    
    /**
     * Load warehouses dari cache (15 jam) atau API
     */
    fun loadWarehouses() {
        viewModelScope.launch {
            try {
                // Cek cache terlebih dahulu
                if (isWarehouseCacheValid()) {
                    val cachedWarehouses = loadWarehousesFromCache()
                    if (cachedWarehouses.isNotEmpty()) {
                        Log.d("RelocationViewModel", "🔥 Loading warehouses from cache: ${cachedWarehouses.size} warehouses (cache age: ${getCacheAgeHours()} hours)")
                        _warehouses.value = cachedWarehouses
                        return@launch
                    }
                }
                
                // Jika cache tidak valid atau kosong, load dari API
                Log.d("RelocationViewModel", "🔥 Loading warehouses from API (cache expired or empty)...")
                
                val warehousesList = withContext(Dispatchers.IO) {
                    apiService.getWarehouses()
                }
                
                // Simpan ke cache dengan timestamp
                saveWarehousesToCache(warehousesList)
                
                _warehouses.value = warehousesList
                Log.d("RelocationViewModel", "🔥 Warehouses loaded from API and cached: ${warehousesList.size} warehouses")
                
            } catch (e: Exception) {
                Log.e("RelocationViewModel", "❌ Error loading warehouses: ${e.message}", e)
                _errorMessage.value = "Error loading warehouses: ${e.message}"
            }
        }
    }
    
    /**
     * Refresh warehouse cache (force reload from API)
     */
    fun refreshWarehouses() {
        clearWarehouseCache()
        loadWarehouses()
    }
    
    /**
     * Cek apakah cache warehouse masih valid (dalam 15 jam)
     */
    private fun isWarehouseCacheValid(): Boolean {
        val cacheTimestamp = prefs.getLong(WAREHOUSE_CACHE_TIMESTAMP_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val isValid = (currentTime - cacheTimestamp) < WAREHOUSE_CACHE_DURATION
        
        Log.d("RelocationViewModel", "🔥 Cache validation: timestamp=$cacheTimestamp, current=$currentTime, duration=$WAREHOUSE_CACHE_DURATION, isValid=$isValid")
        return isValid
    }
    
    /**
     * Hitung umur cache dalam jam
     */
    private fun getCacheAgeHours(): Double {
        val cacheTimestamp = prefs.getLong(WAREHOUSE_CACHE_TIMESTAMP_KEY, 0)
        val currentTime = System.currentTimeMillis()
        return (currentTime - cacheTimestamp) / (1000.0 * 60.0 * 60.0)
    }
    
    /**
     * Load warehouses dari SharedPreferences cache
     */
    private fun loadWarehousesFromCache(): List<com.example.cekpicklist.data.Warehouse> {
        try {
            val cacheData = prefs.getString(WAREHOUSE_CACHE_KEY, null)
            if (cacheData != null) {
                val jsonArray = JSONArray(cacheData)
                val warehouses = mutableListOf<com.example.cekpicklist.data.Warehouse>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val warehouse = com.example.cekpicklist.data.Warehouse(
                        warehouseId = jsonObject.getString("warehouseId"),
                        warehouseName = jsonObject.getString("warehouseName"),
                        address = jsonObject.optString("address", null),
                        city = jsonObject.optString("city", null),
                        country = jsonObject.optString("country", null),
                        isActive = jsonObject.optBoolean("isActive", true)
                    )
                    warehouses.add(warehouse)
                }
                
                Log.d("RelocationViewModel", "🔥 Loaded ${warehouses.size} warehouses from SharedPreferences cache")
                return warehouses
            }
        } catch (e: Exception) {
            Log.e("RelocationViewModel", "❌ Error loading warehouses from cache: ${e.message}", e)
        }
        return emptyList()
    }
    
    /**
     * Simpan warehouses ke SharedPreferences cache
     */
    private fun saveWarehousesToCache(warehouses: List<com.example.cekpicklist.data.Warehouse>) {
        try {
            val jsonArray = JSONArray()
            warehouses.forEach { warehouse ->
                val jsonObject = JSONObject().apply {
                    put("warehouseId", warehouse.warehouseId)
                    put("warehouseName", warehouse.warehouseName)
                    put("address", warehouse.address ?: "")
                    put("city", warehouse.city ?: "")
                    put("country", warehouse.country ?: "")
                    put("isActive", warehouse.isActive)
                }
                jsonArray.put(jsonObject)
            }
            
            val currentTime = System.currentTimeMillis()
            prefs.edit()
                .putString(WAREHOUSE_CACHE_KEY, jsonArray.toString())
                .putLong(WAREHOUSE_CACHE_TIMESTAMP_KEY, currentTime)
                .apply()
                
            Log.d("RelocationViewModel", "🔥 Saved ${warehouses.size} warehouses to SharedPreferences cache (timestamp: $currentTime)")
        } catch (e: Exception) {
            Log.e("RelocationViewModel", "❌ Error saving warehouses to cache: ${e.message}", e)
        }
    }
    
    /**
     * Hapus cache warehouse
     */
    private fun clearWarehouseCache() {
        prefs.edit()
            .remove(WAREHOUSE_CACHE_KEY)
            .remove(WAREHOUSE_CACHE_TIMESTAMP_KEY)
            .apply()
        Log.d("RelocationViewModel", "🔥 Warehouse cache cleared")
    }
    
    /**
     * Get cached warehouses (synchronous)
     */
    fun getCachedWarehouses(): List<com.example.cekpicklist.data.Warehouse>? {
        return if (isWarehouseCacheValid()) {
            val cachedWarehouses = loadWarehousesFromCache()
            if (cachedWarehouses.isNotEmpty()) {
                cachedWarehouses
            } else {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Set selected current warehouse
     */
    fun setSelectedCurrentWarehouse(warehouse: com.example.cekpicklist.data.Warehouse?) {
        _selectedCurrentWarehouse.value = warehouse
        Log.d("RelocationViewModel", "🔥 Current warehouse selected: ${warehouse?.warehouseName} (${warehouse?.warehouseId})")
    }
    
    /**
     * Set selected target warehouse
     */
    fun setSelectedTargetWarehouse(warehouse: com.example.cekpicklist.data.Warehouse?) {
        _selectedTargetWarehouse.value = warehouse
        Log.d("RelocationViewModel", "🔥 Target warehouse selected: ${warehouse?.warehouseName} (${warehouse?.warehouseId})")
    }
    
    /**
     * Get selected current warehouse ID
     */
    fun getSelectedCurrentWarehouseId(): String? {
        return _selectedCurrentWarehouse.value?.warehouseId
    }
    
    /**
     * Get selected target warehouse ID
     */
    fun getSelectedTargetWarehouseId(): String? {
        return _selectedTargetWarehouse.value?.warehouseId
    }
    
    /**
     * Set selected tag status
     */
    fun setSelectedTagStatus(tagStatus: TagStatus?) {
        _selectedTagStatus.value = tagStatus
        Log.d("RelocationViewModel", "🔥 Tag status selected: $tagStatus")
    }
    
    /**
     * Get selected tag status
     */
    fun getSelectedTagStatus(): TagStatus? {
        return _selectedTagStatus.value
    }

    /**
     * Konsumsi hasil lookup lokal dari RfidScanManager dan update UI.
     * Memfilter sesuai warehouse/tag status terpilih. Jika tidak sesuai → tandai invalid (NA)
     */
    fun applyLookupResults(localResults: Map<String, com.example.cekpicklist.api.NirwanaApiService.ProductInfo>) {
        val currentWarehouse = getSelectedCurrentWarehouse()
        val selectedWarehouseId = currentWarehouse?.warehouseId?.trim()?.uppercase()
        val selectedWarehouseName = currentWarehouse?.warehouseName?.trim()?.uppercase()
        val selectedTagStatus = _selectedTagStatus.value?.value?.trim()?.uppercase()
        
        // **DEBUG LOGGING**: Log parameter validasi
        Log.d("RelocationViewModel", "🔍 === VALIDATION PARAMETERS ===")
        Log.d("RelocationViewModel", "🔍 Current Warehouse: ${currentWarehouse?.warehouseName} (ID: ${currentWarehouse?.warehouseId})")
        Log.d("RelocationViewModel", "🔍 Selected Warehouse ID: '$selectedWarehouseId'")
        Log.d("RelocationViewModel", "🔍 Selected Warehouse Name: '$selectedWarehouseName'")
        Log.d("RelocationViewModel", "🔍 Selected Tag Status: '$selectedTagStatus'")
        Log.d("RelocationViewModel", "🔍 Total lookup results: ${localResults.size}")
        
        if ((selectedWarehouseId == null && selectedWarehouseName == null) || selectedTagStatus == null) {
            _errorMessage.value = "Pilih current warehouse dan tag status terlebih dahulu"
            Log.d("RelocationViewModel", "❌ Validation failed: Missing warehouse or tag status")
            return
        }
        
        // **MAPPING**: Buat mapping dari warehouse ID ke warehouse name untuk validasi
        val warehouseIdToNameMap = _warehouses.value?.associate { 
            it.warehouseId.trim().uppercase() to it.warehouseName.trim().uppercase() 
        } ?: emptyMap()
        
        Log.d("RelocationViewModel", "🔍 Warehouse ID to Name mapping: $warehouseIdToNameMap")

        try {
            // Reset validRfids sebelum hitung ulang
            validRfids.clear()

            // Bentuk list dari map EPC → ProductInfo agar mudah diproses
            val products: List<com.example.cekpicklist.api.NirwanaApiService.ProductInfo> = localResults.values.toList()

            // Bangun agregasi per artikel berbasis EPC→ProductInfo untuk mencegah salah hitung antar artikel
            val groupKeyToEpCs: MutableMap<String, MutableSet<String>> = linkedMapOf()
            val groupKeyToFirstProduct: MutableMap<String, com.example.cekpicklist.api.NirwanaApiService.ProductInfo> = linkedMapOf()

			Log.d("RelocationViewModel", "🔎 === APPLY LOOKUP RESULTS (PER EPC) ===")
			localResults.forEach { (epc, p) ->
				Log.d(
					"RelocationViewModel",
					"🔎 EPC=${epc} → article='${p.articleName}' size='${p.size}' wh='${p.warehouse}' status='${p.tagStatus}'"
				)
				// **DETAILED DEBUG**: Log semua field dari API
				Log.d("RelocationViewModel", "🔎   - articleId: '${p.articleId}'")
				Log.d("RelocationViewModel", "🔎   - productId: '${p.productId}'")
				Log.d("RelocationViewModel", "🔎   - productName: '${p.productName}'")
				Log.d("RelocationViewModel", "🔎   - brand: '${p.brand}'")
				Log.d("RelocationViewModel", "🔎   - category: '${p.category}'")
				Log.d("RelocationViewModel", "🔎   - subCategory: '${p.subCategory}'")
				Log.d("RelocationViewModel", "🔎   - color: '${p.color}'")
				Log.d("RelocationViewModel", "🔎   - gender: '${p.gender}'")
				Log.d("RelocationViewModel", "🔎   - qty: ${p.qty}")
				Log.d("RelocationViewModel", "🔎   - rfidList: ${p.rfidList}")
				val kArticle = (p.articleName ?: "").trim().uppercase()
				val kSize = (p.size ?: "").trim().uppercase()
				val kWh = (p.warehouse ?: "").trim().uppercase()
				val kStatus = (p.tagStatus ?: "").trim().uppercase()
				val key = "$kArticle|$kSize|$kWh|$kStatus"
				val set = groupKeyToEpCs.getOrPut(key) { linkedSetOf() }
				set.add(epc)
				groupKeyToFirstProduct.putIfAbsent(key, p)
			}
			Log.d("RelocationViewModel", "🔎 Group count: ${groupKeyToEpCs.size}")
			groupKeyToEpCs.forEach { (key, set) ->
				Log.d("RelocationViewModel", "🔎 GROUP '$key' -> EPCs=${set.size}")
			}

            val rawItems = groupKeyToEpCs.map { (key, epcSet) ->
                val first = groupKeyToFirstProduct[key]!!
                val isRegistered = first.articleName != "NA" && first.articleId != "NA"
                val fWarehouse = first.warehouse?.trim()?.uppercase()
                val fTagStatus = first.tagStatus?.trim()?.uppercase()
                // **FIX**: Bandingkan dengan ID warehouse yang dipilih user
                // API mengembalikan nama warehouse, tapi kita validasi dengan ID warehouse
                // Cari warehouse ID yang sesuai dengan nama warehouse dari API
                val expectedWarehouseName = warehouseIdToNameMap[selectedWarehouseId]
                val matchesWarehouse = fWarehouse == expectedWarehouseName
                val matchesLocation = matchesWarehouse && (fTagStatus == selectedTagStatus)
                val isValid = isRegistered && matchesLocation
                
                // **DETAILED DEBUG LOGGING**: Log setiap item validasi
                Log.d("RelocationViewModel", "🔍 === ITEM VALIDATION ===")
                Log.d("RelocationViewModel", "🔍 Article: ${first.articleName} ${first.size}")
                Log.d("RelocationViewModel", "🔍   - isRegistered: $isRegistered (articleName='${first.articleName}', articleId='${first.articleId}')")
                Log.d("RelocationViewModel", "🔍   - Product Warehouse: '$fWarehouse'")
                Log.d("RelocationViewModel", "🔍   - Product TagStatus: '$fTagStatus'")
                Log.d("RelocationViewModel", "🔍   - Selected Warehouse ID: '$selectedWarehouseId'")
                Log.d("RelocationViewModel", "🔍   - Expected Warehouse Name: '$expectedWarehouseName'")
                Log.d("RelocationViewModel", "🔍   - Selected TagStatus: '$selectedTagStatus'")
                Log.d("RelocationViewModel", "🔍   - matchesWarehouse: $matchesWarehouse (comparing '$fWarehouse' == '$expectedWarehouseName')")
                Log.d("RelocationViewModel", "🔍   - matchesLocation: $matchesLocation")
                Log.d("RelocationViewModel", "🔍   - FINAL isValid: $isValid")
                Log.d("RelocationViewModel", "🔍 === END ITEM VALIDATION ===")

                val allRfids = epcSet.toList()
                val totalQty = allRfids.size

                if (isValid) {
                    validRfids.addAll(allRfids)
                }

                RelocationItem(
                    epc = allRfids.joinToString(","),
                    articleId = (first.articleId ?: "").trim(),
                    articleName = (first.articleName ?: "").trim(),
                    size = (first.size ?: "").trim(),
                    qty = totalQty,
                    productId = first.productId,
                    productName = first.productName,
                    brand = first.brand,
                    category = first.category,
                    subCategory = first.subCategory,
                    color = first.color,
                    gender = first.gender,
                    warehouse = first.warehouse,
                    tagStatus = first.tagStatus,
                    isValid = isValid
                )
            }

            // **AGGREGATION**: Gabungkan INVALID per warehouse+tagStatus (sum qty, merge EPC)
            val items = aggregateInvalidItems(rawItems)

            // **SORTING**: Invalid items (isValid = false) selalu di atas
            val sorted = items.sortedWith(compareBy<RelocationItem> {
                if (!it.isValid) 0 else 1  // Invalid items (0) di atas, valid items (1) di bawah
            }.thenBy {
                if (!it.isValid) it.warehouse else ""  // Untuk invalid: urutkan berdasarkan warehouse
            }.thenBy {
                if (!it.isValid) it.tagStatus else ""  // Untuk invalid: urutkan berdasarkan tagStatus
            }.thenBy {
                if (it.isValid) it.articleName else ""  // Untuk valid: urutkan berdasarkan articleName
            })

            _relocationItems.value = sorted
            updateRfidCounters()

            val totalUnique = scannedRfids.size
            val totalValid = validRfids.size
            val totalNa = totalUnique - totalValid
            
            // **FINAL DEBUG LOGGING**: Log hasil akhir validasi
            Log.d("RelocationViewModel", "🔥 === FINAL VALIDATION RESULTS ===")
            Log.d("RelocationViewModel", "🔥 Total items processed: ${sorted.size}")
            Log.d("RelocationViewModel", "🔥 Valid items: ${sorted.count { it.isValid }}")
            Log.d("RelocationViewModel", "🔥 Invalid items: ${sorted.count { !it.isValid }}")
            Log.d("RelocationViewModel", "🔥 Total unique RFIDs: $totalUnique")
            Log.d("RelocationViewModel", "🔥 Valid RFIDs: $totalValid")
            Log.d("RelocationViewModel", "🔥 Invalid RFIDs: $totalNa")
            
            // Log detail setiap item
            sorted.forEachIndexed { index, item ->
                Log.d("RelocationViewModel", "🔥 Item $index: ${item.articleName} ${item.size} - Valid: ${item.isValid}")
                if (!item.isValid) {
                    Log.d("RelocationViewModel", "🔥   - Warehouse: '${item.warehouse}', TagStatus: '${item.tagStatus}'")
                }
            }
            Log.d("RelocationViewModel", "🔥 === END FINAL VALIDATION RESULTS ===")
        } catch (e: Exception) {
            Log.e("RelocationViewModel", "❌ Error applying lookup results: ${'$'}{e.message}", e)
            _errorMessage.value = "Error: ${'$'}{e.message}"
        }
    }
    
    /**
     * Get selected current warehouse
     */
    fun getSelectedCurrentWarehouse(): com.example.cekpicklist.data.Warehouse? {
        return _selectedCurrentWarehouse.value
    }
    
    /**
     * Get selected target warehouse
     */
    fun getSelectedTargetWarehouse(): com.example.cekpicklist.data.Warehouse? {
        return _selectedTargetWarehouse.value
    }
    
    /**
     * Perform lookup dengan current settings (warehouse dan tag status yang sudah dipilih)
     * Dipanggil saat warehouse atau radio current location berubah untuk auto-refresh
     */
    fun performLookupWithCurrentSettings() {
        val currentWarehouse = getSelectedCurrentWarehouse()
        val tagStatus = getSelectedTagStatus()
        
        if (currentWarehouse == null || tagStatus == null) {
            Log.d("RelocationViewModel", "🔄 Auto-refresh skipped: Missing warehouse or tag status")
            return
        }
        
        if (scannedRfids.isEmpty()) {
            Log.d("RelocationViewModel", "🔄 Auto-refresh skipped: No RFID scanned yet")
            return
        }
        
        Log.d("RelocationViewModel", "🔄 Auto-refresh: Performing lookup with warehouse=${currentWarehouse.warehouseName}, tagStatus=$tagStatus")
        
        // Trigger lookup dengan parameter yang sudah dipilih
        lookupWithFinalizationWindow(tagStatus.name)
    }

    /**
     * Gabungkan item INVALID (isValid=false) per kombinasi warehouse+tagStatus.
     * - qty dijumlahkan
     * - EPC digabung (join)
     * - Field lain diambil dari item pertama (article diabaikan untuk invalid)
     */
    private fun aggregateInvalidItems(source: List<RelocationItem>): List<RelocationItem> {
        if (source.isEmpty()) return source
        val (invalids, valids) = source.partition { !it.isValid }
        if (invalids.isEmpty()) return source
        val grouped = invalids.groupBy { Pair((it.warehouse ?: "").trim().uppercase(), (it.tagStatus ?: "").trim().uppercase()) }
        val mergedInvalids = grouped.map { (key, list) ->
            val first = list.first()
            val totalQty = list.sumOf { it.qty }
            val mergedEpc = list.asSequence().flatMap { it.epc.split(',').asSequence() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
            RelocationItem(
                epc = mergedEpc,
                articleId = "",
                articleName = "",
                size = "",
                qty = totalQty,
                productId = first.productId,
                productName = first.productName,
                brand = first.brand,
                category = first.category,
                subCategory = first.subCategory,
                color = first.color,
                gender = first.gender,
                warehouse = first.warehouse,
                tagStatus = first.tagStatus,
                isValid = false
            )
        }
        return mergedInvalids + valids
    }
}
