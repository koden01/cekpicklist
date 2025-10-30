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
    
    fun addScannedItem(epc: String, articleId: String, articleName: String, size: String, qty: Int) {
        Logger.PicklistInput.d("Adding scanned item: $epc -> $articleName $size")
        
        // Check if item already exists
        val existingItem = scannedItemsList.find { it.epc == epc }
        if (existingItem != null) {
            // Replace with updated qty
            val updated = existingItem.copy(qty = existingItem.qty + qty)
            scannedItemsList.replaceAll { if (it.epc == epc) updated else it }
            Logger.PicklistInput.d("Updated existing item: ${existingItem.articleName} qty: ${updated.qty}")
        } else {
            // Add new item
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
                warehouse = "",
                tagStatus = "VALID"
            )
            scannedItemsList.add(newItem)
            Logger.PicklistInput.d("Added new item: ${newItem.articleName} qty: ${newItem.qty}")
        }
        
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
    
    fun submitOutActivity(notrans: String) {
        val items = scannedItemsList.toList()
        if (items.isEmpty()) {
            _errorMessage.value = "Tidak ada item untuk disubmit"
            return
        }
        
        Logger.PicklistInput.d("Submitting out activity with ${items.size} items, notrans: $notrans")
        
        viewModelScope.launch {
            try {
                val success = repository.submitOutActivity(notrans, items)
                if (success) {
                    Logger.PicklistInput.d("Out activity submitted successfully")
                    _submitSuccess.value = true
                } else {
                    Logger.PicklistInput.e("Failed to submit out activity")
                    _errorMessage.value = "Gagal menyimpan out activity"
                }
            } catch (e: Exception) {
                Logger.PicklistInput.e("Error submitting out activity: ${e.message}")
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }
    
    
    fun handleRfidDetected(epc: String) {
        Logger.PicklistInput.d("RFID detected: $epc")
        
        // Perform lookup for the EPC
        viewModelScope.launch {
            try {
                val productInfo = repository.lookupRfid(epc)
                if (productInfo != null) {
                    addScannedItem(
                        epc = epc,
                        articleId = productInfo.articleId,
                        articleName = productInfo.articleName,
                        size = productInfo.size,
                        qty = 1
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
}
