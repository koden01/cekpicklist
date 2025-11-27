package com.example.cekpicklist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cekpicklist.data.ReturAggregatedItem
import com.example.cekpicklist.data.ReturItem
import com.example.cekpicklist.data.Warehouse
import com.example.cekpicklist.repository.ReturRepository
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ReturViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReturRepository(application)

    private val scannedItemsMap: MutableMap<String, ReturItem> = LinkedHashMap()
    private val invalidEpcSet: MutableSet<String> = HashSet()
    private val inFlightLookups: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _scannedItems = MutableLiveData<List<ReturAggregatedItem>>(emptyList())
    val scannedItems: LiveData<List<ReturAggregatedItem>> = _scannedItems

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _warehouses = MutableLiveData<List<Warehouse>>(emptyList())
    val warehouses: LiveData<List<Warehouse>> = _warehouses

    private val _selectedWarehouse = MutableLiveData<Warehouse?>()
    val selectedWarehouse: LiveData<Warehouse?> = _selectedWarehouse

    private val _invalidScanCount = MutableLiveData(0)
    val invalidScanCount: LiveData<Int> = _invalidScanCount

    fun startScanning() {
        _isScanning.value = true
    }

    fun stopScanning() {
        _isScanning.value = false
    }

    fun handleRfidDetected(epc: String) {
        processBatchLookup(listOf(epc))
    }

    fun processBatchLookup(rfidList: List<String>) {
        if (rfidList.isEmpty()) return

        val normalizedList = rfidList.mapNotNull { epc ->
            val trimmed = epc.trim()
            trimmed.takeIf { it.isNotEmpty() }
        }
        if (normalizedList.isEmpty()) return

        val newEpcs = mutableListOf<String>()
        normalizedList.forEach { epc ->
            when {
                scannedItemsMap.containsKey(epc) -> {
                    // Sudah ada di daftar, abaikan tanpa menampilkan toast
                }
                invalidEpcSet.contains(epc) -> {
                    // Sudah tercatat invalid sebelumnya, abaikan
                }
                !inFlightLookups.add(epc) -> {
                    // Lookup masih berjalan, abaikan
                }
                else -> newEpcs.add(epc)
            }
        }

        if (newEpcs.isEmpty()) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val products = repository.batchLookupRfidList(newEpcs)

                val infoByEpc = mutableMapOf<String, com.example.cekpicklist.api.NirwanaApiService.ProductInfo>()
                products.forEach { product ->
                    product.rfidList.forEach { rf ->
                        val key = rf.trim()
                        if (key.isNotEmpty()) {
                            infoByEpc[key] = product
                        }
                    }
                }

                newEpcs.forEach { epc ->
                    val productInfo = infoByEpc[epc]
                    if (productInfo == null) {
                        invalidEpcSet.add(epc)
                        return@forEach
                    }

                    val status = productInfo.tagStatus.trim().uppercase()
                    val isValid = status == "SOLD"
                    if (!isValid) {
                        invalidEpcSet.add(epc)
                    }

                    val item = ReturItem(
                        epc = epc,
                        articleId = productInfo.articleId,
                        articleName = productInfo.articleName,
                        size = productInfo.size,
                        productId = productInfo.productId,
                        brand = productInfo.brand,
                        category = productInfo.category,
                        subCategory = productInfo.subCategory,
                        color = productInfo.color,
                        gender = productInfo.gender,
                        sourceWarehouse = productInfo.warehouse,
                        currentTagStatus = productInfo.tagStatus,
                        isValid = isValid
                    )

                    scannedItemsMap[epc] = item
                }

                _invalidScanCount.value = invalidEpcSet.size
                publishAggregatedItems()

            } catch (e: Exception) {
                newEpcs.forEach { epc ->
                    invalidEpcSet.add(epc)
                }
                _invalidScanCount.value = invalidEpcSet.size
                _errorMessage.value = "Lookup batch gagal: ${e.message}"
            } finally {
                newEpcs.forEach { inFlightLookups.remove(it) }
                _isLoading.value = false
            }
        }
    }

    fun removeItem(epc: String) {
        scannedItemsMap.remove(epc)
                publishAggregatedItems()
    }

    fun clearAll() {
        scannedItemsMap.clear()
        invalidEpcSet.clear()
        inFlightLookups.clear()
        _invalidScanCount.value = 0
        publishAggregatedItems()
    }

    fun submitRetur() {
        val targetWarehouse = _selectedWarehouse.value
        if (targetWarehouse == null) {
            _errorMessage.value = "Pilih warehouse tujuan terlebih dahulu."
            return
        }
        val validEpcs = scannedItemsMap.values.filter { it.isValid }.map { it.epc }
        if (validEpcs.isEmpty()) {
            _errorMessage.value = "Belum ada RFID SOLD yang siap dikirim."
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val response = repository.markAsTagged(targetWarehouse.warehouseId, validEpcs)
                if (response.success) {
                    _successMessage.value =
                        response.message.ifBlank { "Berhasil mengubah ${validEpcs.size} RFID menjadi TAGGED." }
                    // Pertahankan item invalid, hapus yang valid
                    val iterator = scannedItemsMap.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (entry.value.isValid) {
                            iterator.remove()
                        }
                    }
                    publishAggregatedItems()
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                _errorMessage.value = "Gagal submit retur: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSelectedWarehouse(warehouse: Warehouse) {
        _selectedWarehouse.value = warehouse
    }

    fun removeGroup(group: ReturAggregatedItem) {
        val iterator = scannedItemsMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val value = entry.value

            val shouldRemove = if (group.isValid) {
                val sameArticle = when {
                    group.articleId.isNotBlank() && value.articleId.isNotBlank() ->
                        value.articleId == group.articleId
                    else -> value.articleName.equals(group.articleName, true)
                }
                sameArticle &&
                        value.size.equals(group.size, true) &&
                        value.isValid
            } else {
                !value.isValid &&
                        value.sourceWarehouse.equals(group.sourceWarehouse, true) &&
                        value.currentTagStatus.equals(group.statusLabel, true)
            }

            if (shouldRemove) {
                iterator.remove()
                if (!value.isValid) {
                    invalidEpcSet.remove(value.epc)
                }
            }
        }
        _invalidScanCount.value = invalidEpcSet.size
        publishAggregatedItems()
    }

    fun loadWarehouses() {
        if (_warehouses.value?.isNotEmpty() == true) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                val list = repository.getWarehouses().filter { it.isActive }
                _warehouses.value = list
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memuat warehouse: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
    }

    fun consumeSuccess() {
        _successMessage.value = null
    }

    private fun publishAggregatedItems() {
        val aggregated = scannedItemsMap.values
            .groupBy { item ->
                if (item.isValid) {
                    listOf(
                        "VALID",
                        item.articleId.ifBlank { item.articleName.lowercase() },
                        item.size.lowercase()
                    )
                } else {
                    listOf(
                        "INVALID",
                        item.sourceWarehouse.lowercase(),
                        item.currentTagStatus.uppercase()
                    )
                }
            }
            .map { (_, items) ->
                val first = items.first()
                ReturAggregatedItem(
                    articleId = if (first.isValid) first.articleId else "",
                    articleName = if (first.isValid) first.articleName else "",
                    size = if (first.isValid) first.size else "",
                    quantity = items.size,
                    isValid = first.isValid,
                    statusLabel = first.currentTagStatus,
                    sourceWarehouse = first.sourceWarehouse
                )
            }
            .sortedWith(
                compareBy<ReturAggregatedItem> { !it.isValid }
                    .thenBy {
                        if (it.isValid) it.articleName.lowercase()
                        else it.sourceWarehouse.lowercase()
                    }
                    .thenBy {
                        if (it.isValid) it.size.lowercase()
                        else it.statusLabel.uppercase()
                    }
            )
        _scannedItems.value = aggregated
    }
}

