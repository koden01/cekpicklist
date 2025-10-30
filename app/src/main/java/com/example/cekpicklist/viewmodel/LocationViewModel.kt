package com.example.cekpicklist.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "LocationViewModel"
    }
    
    // LiveData for location value (0-100)
    private val _locationValue = MutableLiveData<Int>()
    val locationValue: LiveData<Int> = _locationValue
    
    // LiveData for scanning state
    private val _isScanning = MutableLiveData<Boolean>()
    val isScanning: LiveData<Boolean> = _isScanning
    
    // LiveData for validity
    private val _isValid = MutableLiveData<Boolean>()
    val isValid: LiveData<Boolean> = _isValid
    
    // Current target EPC
    private var currentTargetEpc = ""
    
    init {
        _locationValue.value = 0
        _isScanning.value = false
        _isValid.value = false
        
        Log.d(TAG, "🎯 LocationViewModel initialized")
    }
    
    /**
     * Start location scanning
     * @param targetEpc Target EPC to locate
     */
    fun startLocation(targetEpc: String): Boolean {
        return try {
            Log.d(TAG, "🎯 Starting location for EPC: $targetEpc")
            
            currentTargetEpc = targetEpc
            _isScanning.value = true
            
            // Clear previous data
            _locationValue.value = 0
            _isValid.value = false
            
            Log.d(TAG, "✅ Location request processed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting location: ${e.message}", e)
            _isScanning.value = false
            false
        }
    }
    
    /**
     * Stop location scanning
     */
    fun stopLocation(): Boolean {
        return try {
            Log.d(TAG, "🎯 Stopping location")
            
            _isScanning.value = false
            
            // Clear location data
            _locationValue.value = 0
            _isValid.value = false
            currentTargetEpc = ""
            
            Log.d(TAG, "✅ Location stop request processed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping location: ${e.message}", e)
            false
        }
    }
    
    /**
     * Update location data (called by RFID reader callback)
     * @param value Distance/strength value (0-100)
     * @param valid Whether the reading is valid
     */
    fun updateLocationData(value: Int, valid: Boolean) {
        _locationValue.value = value
        _isValid.value = valid
        Log.d(TAG, "🎯 Location data updated: value=$value, valid=$valid")
    }
    
    /**
     * Clear location data
     */
    fun clearLocationData() {
        Log.d(TAG, "🎯 Clearing location data")
        _locationValue.value = 0
        _isValid.value = false
    }
    
    /**
     * Get current target EPC
     */
    fun getCurrentTargetEpc(): String = currentTargetEpc
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🎯 LocationViewModel cleared")
    }
}
