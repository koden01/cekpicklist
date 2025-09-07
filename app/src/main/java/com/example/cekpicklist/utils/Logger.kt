package com.example.cekpicklist.utils

import android.util.Log

/**
 * Utility class untuk logging yang terstruktur
 * Menyediakan format log yang konsisten dan mudah dibaca
 */
object Logger {
    
    private const val TAG_PREFIX = "CekPicklist"
    private const val MAX_LOG_LENGTH = 4000
    
    // Log levels
    private const val VERBOSE = Log.VERBOSE
    private const val DEBUG = Log.DEBUG
    private const val INFO = Log.INFO
    private const val WARN = Log.WARN
    private const val ERROR = Log.ERROR
    
    /**
     * Log untuk MainActivity
     */
    object MainActivity {
        private const val TAG = "${TAG_PREFIX}_MainActivity"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun rfidScan(tagId: String, rssi: Int) = d("🔍 RFID Scan: $tagId, RSSI: $rssi")
        fun picklistSelected(picklistNumber: String) = i("📋 Picklist Selected: $picklistNumber")
        fun itemDeleted(articleId: String, size: String) = w("🗑️ Item Deleted: $articleId ($size)")
        fun clearAllConfirmed() = i("🧹 Clear All Confirmed")
        fun settingsChanged(key: String, value: Any) = d("⚙️ Settings Changed: $key = $value")
    }
    
    /**
     * Log untuk PicklistInputActivity
     */
    object PicklistInput {
        private const val TAG = "${TAG_PREFIX}_PicklistInput"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun dialogCreated() = i("📱 Picklist Selection Dialog Created")
        fun dialogShown() = i("👁️ Picklist Selection Dialog Shown")
        fun dialogDismissed() = i("❌ Picklist Selection Dialog Dismissed")
        fun searchQuery(query: String) = d("🔍 Search Query: '$query'")
        fun searchCleared() = d("🧹 Search Cleared")
        fun picklistSelected(picklistNumber: String) = i("✅ Picklist Selected: $picklistNumber")
        fun adapterSetup(itemCount: Int) = d("📊 Adapter Setup: $itemCount items")
        fun windowSized(width: Int, height: Int) = d("📏 Window Sized: ${width}x${height}")
    }
    
    /**
     * Log untuk ScanViewModel
     */
    object ScanViewModel {
        private const val TAG = "${TAG_PREFIX}_ScanViewModel"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun dataLoaded(itemCount: Int) = i("📊 Data Loaded: $itemCount items")
        fun cacheUpdated(itemCount: Int) = d("💾 Cache Updated: $itemCount items")
        fun scanAdded(articleId: String, qty: Int) = i("➕ Scan Added: $articleId (qty: $qty)")
        fun scanRemoved(articleId: String) = w("➖ Scan Removed: $articleId")
        fun picklistChanged(picklistNumber: String) = i("🔄 Picklist Changed: $picklistNumber")
        fun errorLoadingData(error: String) = e("❌ Error Loading Data: $error")
    }
    
    /**
     * Log untuk SupabaseService
     */
    object SupabaseService {
        private const val TAG = "${TAG_PREFIX}_SupabaseService"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun apiCall(endpoint: String, method: String) = d("🌐 API Call: $method $endpoint")
        fun apiSuccess(endpoint: String, responseSize: Int) = i("✅ API Success: $endpoint ($responseSize items)")
        fun apiError(endpoint: String, error: String) = e("❌ API Error: $endpoint - $error")
        fun connectionEstablished() = i("🔗 Supabase Connection Established")
        fun connectionLost() = w("🔌 Supabase Connection Lost")
        fun dataSaved(table: String, recordCount: Int) = i("💾 Data Saved: $table ($recordCount records)")
    }
    
    /**
     * Log untuk CacheManager
     */
    object CacheManager {
        private const val TAG = "${TAG_PREFIX}_CacheManager"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun cacheLoaded(itemCount: Int) = i("📂 Cache Loaded: $itemCount items")
        fun cacheSaved(itemCount: Int) = i("💾 Cache Saved: $itemCount items")
        fun cacheCleared() = w("🧹 Cache Cleared")
        fun itemAdded(itemId: String) = d("➕ Item Added to Cache: $itemId")
        fun itemRemoved(itemId: String) = d("➖ Item Removed from Cache: $itemId")
        fun itemUpdated(itemId: String) = d("🔄 Item Updated in Cache: $itemId")
    }
    
    /**
     * Log untuk SettingsActivity
     */
    object SettingsActivity {
        private const val TAG = "${TAG_PREFIX}_SettingsActivity"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun settingsLoaded() = i("⚙️ Settings Loaded")
        fun settingChanged(key: String, oldValue: Any, newValue: Any) = i("🔄 Setting Changed: $key ($oldValue → $newValue)")
        fun settingsSaved() = i("💾 Settings Saved")
        fun resetToDefault() = w("🔄 Settings Reset to Default")
    }
    
    /**
     * Log untuk Adapters
     */
    object Adapter {
        private const val TAG = "${TAG_PREFIX}_Adapter"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun adapterCreated(adapterName: String, itemCount: Int) = i("📊 $adapterName Created: $itemCount items")
        fun itemBound(position: Int, itemId: String) = d("🔗 Item Bound: position $position, id $itemId")
        fun itemClicked(position: Int, itemId: String) = i("👆 Item Clicked: position $position, id $itemId")
        fun itemDeleted(position: Int, itemId: String) = w("🗑️ Item Deleted: position $position, id $itemId")
        fun animationStarted(animationType: String) = d("🎬 Animation Started: $animationType")
        fun animationCompleted(animationType: String) = d("✅ Animation Completed: $animationType")
    }
    
    /**
     * Log untuk Dialog Operations
     */
    object Dialog {
        private const val TAG = "${TAG_PREFIX}_Dialog"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun dialogShown(dialogType: String) = i("📱 Dialog Shown: $dialogType")
        fun dialogDismissed(dialogType: String) = i("❌ Dialog Dismissed: $dialogType")
        fun dialogConfirmed(dialogType: String) = i("✅ Dialog Confirmed: $dialogType")
        fun dialogCancelled(dialogType: String) = w("🚫 Dialog Cancelled: $dialogType")
        fun dialogError(dialogType: String, error: String) = e("❌ Dialog Error: $dialogType - $error")
    }
    
    /**
     * Log untuk Performance Monitoring
     */
    object Performance {
        private const val TAG = "${TAG_PREFIX}_Performance"
        
        fun d(message: String) = log(DEBUG, TAG, message)
        fun i(message: String) = log(INFO, TAG, message)
        fun w(message: String) = log(WARN, TAG, message)
        fun e(message: String, throwable: Throwable? = null) = log(ERROR, TAG, message, throwable)
        
        // Specific log methods
        fun operationStarted(operation: String) = d("⏱️ Operation Started: $operation")
        fun operationCompleted(operation: String, duration: Long) = i("✅ Operation Completed: $operation (${duration}ms)")
        fun operationSlow(operation: String, duration: Long) = w("🐌 Slow Operation: $operation (${duration}ms)")
        fun memoryUsage(used: Long, total: Long) = d("💾 Memory Usage: ${used}MB / ${total}MB")
    }
    
    /**
     * Core logging method
     */
    private fun log(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        // Split long messages
        if (message.length > MAX_LOG_LENGTH) {
            val chunks = message.chunked(MAX_LOG_LENGTH)
            chunks.forEachIndexed { index, chunk ->
                val chunkMessage = if (chunks.size > 1) "[$index/${chunks.size}] $chunk" else chunk
                Log.println(level, tag, chunkMessage)
            }
        } else {
            Log.println(level, tag, message)
        }
        
        // Log throwable if provided
        throwable?.let {
            Log.println(level, tag, "Exception: ${it.message}")
            Log.println(level, tag, it.stackTraceToString())
        }
    }
    
    /**
     * Log untuk debugging umum
     */
    fun debug(message: String) = log(DEBUG, "${TAG_PREFIX}_Debug", message)
    fun info(message: String) = log(INFO, "${TAG_PREFIX}_Info", message)
    fun warning(message: String) = log(WARN, "${TAG_PREFIX}_Warning", message)
    fun error(message: String, throwable: Throwable? = null) = log(ERROR, "${TAG_PREFIX}_Error", message, throwable)
}
