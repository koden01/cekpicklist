package com.example.cekpicklist.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.cekpicklist.data.PendingOperation
import com.example.cekpicklist.data.OperationType
import com.example.cekpicklist.data.OperationPayload
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manager untuk pending operations
 * MOCK IMPLEMENTATION - Dependency tidak tersedia
 */
class PendingOperationsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PendingOperationsManager"
        private const val PREFS_NAME = "pending_operations"
        private const val KEY_OPERATIONS = "operations"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Add pending operation
     */
    fun addPendingOperation(operation: PendingOperation) {
        try {
            val operations = getPendingOperations().toMutableList()
            operations.add(operation)
            savePendingOperations(operations)
            Log.d(TAG, "✅ Added pending operation: ${operation.id}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding pending operation: ${e.message}", e)
        }
    }
    
    /**
     * Get all pending operations
     */
    fun getPendingOperations(): List<PendingOperation> {
        return try {
            val json = prefs.getString(KEY_OPERATIONS, null)
            if (json != null) {
                val jsonArray = JSONArray(json)
                val operations = mutableListOf<PendingOperation>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val operation = parsePendingOperationFromJson(jsonObject)
                    operations.add(operation)
                }
                
                operations
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting pending operations: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Remove pending operation
     */
    fun removePendingOperation(operationId: String) {
        try {
            val operations = getPendingOperations().toMutableList()
            operations.removeAll { it.id == operationId }
            savePendingOperations(operations)
            Log.d(TAG, "✅ Removed pending operation: $operationId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing pending operation: ${e.message}", e)
        }
    }
    
    /**
     * Update pending operation
     */
    fun updatePendingOperation(operation: PendingOperation) {
        try {
            val operations = getPendingOperations().toMutableList()
            val index = operations.indexOfFirst { it.id == operation.id }
            if (index != -1) {
                operations[index] = operation
                savePendingOperations(operations)
                Log.d(TAG, "✅ Updated pending operation: ${operation.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating pending operation: ${e.message}", e)
        }
    }
    
    /**
     * Clear all pending operations
     */
    fun clearPendingOperations() {
        try {
            prefs.edit().remove(KEY_OPERATIONS).apply()
            Log.d(TAG, "✅ Cleared all pending operations")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing pending operations: ${e.message}", e)
        }
    }
    
    /**
     * Get pending operations count
     */
    fun getPendingOperationsCount(): Int {
        return getPendingOperations().size
    }
    
    private fun savePendingOperations(operations: List<PendingOperation>) {
        try {
            val jsonArray = JSONArray()
            
            for (operation in operations) {
                val jsonObject = convertPendingOperationToJson(operation)
                jsonArray.put(jsonObject)
            }
            
            prefs.edit().putString(KEY_OPERATIONS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving pending operations: ${e.message}", e)
        }
    }
    
    /**
     * Convert PendingOperation to JSONObject
     */
    private fun convertPendingOperationToJson(operation: PendingOperation): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("id", operation.id)
        jsonObject.put("type", operation.type.name)
        jsonObject.put("timestamp", operation.timestamp)
        jsonObject.put("retries", operation.retries)
        jsonObject.put("lastAttempt", operation.lastAttempt)
        
        // Convert payload
        val payloadObject = JSONObject()
        payloadObject.put("barcode", operation.payload.barcode)
        
        // Convert metadata map to JSONObject
        val metadataObject = JSONObject()
        for ((key, value) in operation.payload.metadata) {
            metadataObject.put(key, value.toString())
        }
        payloadObject.put("metadata", metadataObject)
        
        jsonObject.put("payload", payloadObject)
        return jsonObject
    }
    
    /**
     * Parse JSONObject to PendingOperation
     */
    private fun parsePendingOperationFromJson(jsonObject: JSONObject): PendingOperation {
        val id = jsonObject.getString("id")
        val type = OperationType.valueOf(jsonObject.getString("type"))
        val timestamp = jsonObject.getLong("timestamp")
        val retries = jsonObject.getInt("retries")
        val lastAttempt = jsonObject.getLong("lastAttempt")
        
        // Parse payload
        val payloadObject = jsonObject.getJSONObject("payload")
        val barcode = payloadObject.getString("barcode")
        
        // Parse metadata
        val metadataObject = payloadObject.getJSONObject("metadata")
        val metadata = mutableMapOf<String, Any>()
        val keys = metadataObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            metadata[key] = metadataObject.getString(key)
        }
        
        val payload = OperationPayload(barcode, metadata)
        
        return PendingOperation(id, type, payload, timestamp, retries, lastAttempt)
    }
}