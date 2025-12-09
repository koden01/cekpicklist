package com.example.cekpicklist.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.cekpicklist.data.OutActivityItem
import com.example.cekpicklist.repository.OutActivityRepository
import com.example.cekpicklist.utils.Logger
import kotlinx.coroutines.launch

class OutActivityViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = OutActivityRepository(application)
    
    private val _scannedItems = MutableLiveData<List<OutActivityItem>>()
    val scannedItems: LiveData<List<OutActivityItem>> = _scannedItems
    
    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _submitSuccess = MutableLiveData<Boolean>()
    val submitSuccess: LiveData<Boolean> = _submitSuccess

    private val scannedItemsList = mutableListOf<OutActivityItem>()
    
    companion object {
        private const val TAG = "OutActivityViewModel"
    }
    
    init {
        _scannedItems.value = emptyList()
        _isScanning.value = false
        _submitSuccess.value = false
    }
    
    fun startScanning() {
        Logger.PicklistInput.d("Starting RFID scanning")
        _isScanning.value = true
        // RFID scanning is handled by BaseRfidActivity
    }
    
    fun stopScanning() {
        Logger.PicklistInput.d("Stopping RFID scanning")
        _isScanning.value = false
    }
    
    fun addScannedItem(epc: String, articleId: String, articleName: String, size: String, qty: Int, warehouse: String = "", tagStatus: String = "VALID") {
        Logger.PicklistInput.d("Adding scanned item: $epc -> $articleName $size (warehouse: $warehouse)")
        
        // **PERBAIKAN**: Cek apakah item sudah ada - hanya tambah item baru (tidak ada penghitungan ulang)
        val existingItem = scannedItemsList.find { it.epc == epc }
        if (existingItem != null) {
            // Item sudah ada, tidak perlu ditambahkan lagi (tidak ada penghitungan ulang)
            Logger.PicklistInput.d("Item already exists, skipping duplicate: ${existingItem.articleName} qty: ${existingItem.qty}")
            return
        }
        
        // Add new item (hanya untuk EPC unik)
        val newItem = OutActivityItem(
            epc = epc,
            articleId = articleId,
            articleName = articleName,
            size = size,
            qty = qty,
            productId = "", // Will be filled by lookup
            brand = "",
            category = "",
            subCategory = "",
            color = "",
            gender = "",
            warehouse = warehouse,
            tagStatus = tagStatus
        )
        scannedItemsList.add(newItem)
        Logger.PicklistInput.d("Added new unique item: ${newItem.articleName} qty: ${newItem.qty} warehouse: ${newItem.warehouse}")
        
        _scannedItems.value = scannedItemsList.toList()
    }
    
    fun clearAllItems() {
        Logger.PicklistInput.d("Clearing all scanned items")
        scannedItemsList.clear()
        _scannedItems.value = emptyList()
    }

    fun removeGroupByArticleAndSize(articleId: String, size: String) {
        Logger.PicklistInput.d("Removing group: articleId=$articleId size=$size")
        val before = scannedItemsList.size
        val newList = scannedItemsList.filterNot { it.articleId == articleId && it.size == size }
        scannedItemsList.clear()
        scannedItemsList.addAll(newList)
        _scannedItems.value = scannedItemsList.toList()
        Logger.PicklistInput.d("Removed ${(before - newList.size)} items for group")
    }
    
    fun submitOutActivity() {
        val items = scannedItemsList.toList()
        if (items.isEmpty()) {
            _errorMessage.value = "Tidak ada item untuk disubmit"
            return
        }
        
        Logger.PicklistInput.d("Submitting out activity - updating tag status to SOLD for ${items.size} items")
        
        viewModelScope.launch {
            try {
                val success = repository.submitOutActivity(items)
                if (success) {
                    Logger.PicklistInput.d("Out activity submitted successfully - tag status updated to SOLD")
                    _submitSuccess.value = true
                } else {
                    Logger.PicklistInput.e("Failed to update tag status to SOLD")
                    Logger.PicklistInput.e("Items count: ${items.size}")
                    _errorMessage.value = "Gagal mengubah tag status ke SOLD. Cek log untuk detail error."
                }
            } catch (e: Exception) {
                Logger.PicklistInput.e("Exception submitting out activity: ${e.javaClass.simpleName}: ${e.message}", e)
                _errorMessage.value = "Error: ${e.message ?: "Unknown error"}"
            }
        }
    }
    
    
    fun handleRfidDetected(epc: String) {
        Logger.PicklistInput.d("RFID detected: $epc")
        
        // **PERBAIKAN**: Cek apakah EPC sudah ada di scannedItemsList
        if (scannedItemsList.any { it.epc == epc }) {
            Logger.PicklistInput.d("RFID already in scanned items, skipping: $epc")
            return
        }
        
        // Perform lookup for the EPC
        viewModelScope.launch {
            try {
                val productInfo = repository.lookupRfid(epc)
                if (productInfo != null) {
                    // **PERBAIKAN**: Pass warehouse dan tagStatus dari lookup result
                    addScannedItem(
                        epc = epc,
                        articleId = productInfo.articleId,
                        articleName = productInfo.articleName,
                        size = productInfo.size,
                        qty = 1,
                        warehouse = productInfo.warehouse,
                        tagStatus = productInfo.tagStatus
                    )
                } else {
                    Logger.PicklistInput.w("No product found for EPC: $epc")
                    _errorMessage.value = "Produk tidak ditemukan untuk EPC: $epc"
                }
            } catch (e: Exception) {
                Logger.PicklistInput.e("Error looking up EPC $epc: ${e.message}")
                _errorMessage.value = "Error lookup: ${e.message}"
            }
        }
    }
    
    /**
     * Add scanned item from cache (RfidScanManager lookup result)
     * Hanya menambahkan item baru, tidak menambah qty untuk item yang sudah ada
     */
    fun addScannedItemFromCache(epc: String, productInfo: com.example.cekpicklist.api.NirwanaApiService.ProductInfo) {
        Logger.PicklistInput.d("Adding scanned item from cache: $epc -> ${productInfo.articleName} ${productInfo.size}")
        
        // Check if item already exists
        val existingItem = scannedItemsList.find { it.epc == epc }
        if (existingItem != null) {
            // Item sudah ada, tidak perlu ditambahkan lagi (qty tetap sama)
            Logger.PicklistInput.d("Item already exists, skipping duplicate: ${existingItem.articleName} qty: ${existingItem.qty}")
            return
        }
        
        // Add new item from cache (hanya jika belum ada)
        val newItem = OutActivityItem(
            epc = epc,
            articleId = productInfo.articleId,
            articleName = productInfo.articleName,
            size = productInfo.size,
            qty = 1,
            productId = productInfo.productId,
            brand = productInfo.brand,
            category = productInfo.category,
            subCategory = productInfo.subCategory,
            color = productInfo.color,
            gender = productInfo.gender,
            warehouse = productInfo.warehouse,
            tagStatus = productInfo.tagStatus
        )
        scannedItemsList.add(newItem)
        Logger.PicklistInput.d("Added new item from cache: ${newItem.articleName} qty: ${newItem.qty}")
        
        _scannedItems.value = scannedItemsList.toList()
    }
}
