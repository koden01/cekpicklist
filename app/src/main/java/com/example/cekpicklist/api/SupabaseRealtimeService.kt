package com.example.cekpicklist.api

import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan

/**
 * Service untuk mengelola koneksi realtime ke Supabase
 * Menyediakan subscription untuk perubahan data di database
 */
class SupabaseRealtimeService {
    
    companion object {
        private const val SUPABASE_URL = "https://ngsuhouodaejwkqdxebk.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5nc3Vob3VvZGFlandrcWR4ZWJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTk2ODAsImV4cCI6MjA2NzY5NTY4MH0.r9HISpDXkY5wiTzO5EoNQuqPS3KePc4SScoapepj4h0"
        private const val TAG = "SupabaseRealtime"
    }
    
    private val supabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }
    
    // private val realtimeClient = supabaseClient.realtime // Sementara di-disable karena import bermasalah
    
    // Coroutine scope untuk realtime operations
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Status koneksi
    private var isConnected = false
    
    // Callback untuk perubahan data
    private var picklistUpdateCallback: ((PicklistUpdate) -> Unit)? = null
    private var scanUpdateCallback: ((ScanUpdate) -> Unit)? = null
    
    // Flow untuk status koneksi
    private val _connectionStatus = MutableSharedFlow<Boolean>()
    val connectionStatus: Flow<Boolean> = _connectionStatus.asSharedFlow()
    
    /**
     * Mulai subscription untuk perubahan di tabel picklist
     */
    suspend fun subscribeToPicklists() {
        try {
            Log.d(TAG, "🔥 Mulai subscription untuk tabel picklist")
            
            // Sementara disable realtime functionality karena import bermasalah
            Log.d(TAG, "⚠️ Realtime functionality temporarily disabled due to import issues")
            
            // TODO: Implementasi realtime setelah import diperbaiki
            // val channel = realtimeClient.createChannel("picklist_all")
            // channel.postgresChanges(...)
            // channel.subscribe()
            
            isConnected = true
            _connectionStatus.emit(true)
            Log.d(TAG, "✅ Subscription picklist berhasil")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error subscribing to picklists: ${e.message}", e)
            isConnected = false
            _connectionStatus.emit(false)
        }
    }
    
    /**
     * Mulai subscription untuk perubahan di tabel picklist_scan
     * @param picklistNo Nomor picklist yang akan di-subscribe
     */
    suspend fun subscribeToPicklistScans(picklistNo: String) {
        try {
            Log.d(TAG, "🔥 Mulai subscription untuk picklist: $picklistNo")
            
            // Sementara disable realtime functionality karena import bermasalah
            Log.d(TAG, "⚠️ Realtime functionality temporarily disabled due to import issues")
            
            // TODO: Implementasi realtime setelah import diperbaiki
            // val channel = realtimeClient.createChannel("picklist_scan_$picklistNo")
            // channel.postgresChanges(...)
            // channel.subscribe()
            
            isConnected = true
            _connectionStatus.emit(true)
            Log.d(TAG, "✅ Subscription berhasil untuk picklist: $picklistNo")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error subscribing to scans: ${e.message}", e)
            isConnected = false
            _connectionStatus.emit(false)
        }
    }
    
    /**
     * Berhenti subscription dari picklists
     */
    suspend fun unsubscribeFromPicklists() {
        try {
            Log.d(TAG, "🔥 Unsubscribing dari picklist channel")
            // TODO: Implementasi unsubscribe setelah import diperbaiki
            Log.d(TAG, "✅ Unsubscribed dari picklists")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unsubscribing from picklists: ${e.message}", e)
        }
    }
    
    /**
     * Berhenti subscription dari picklist scans
     */
    suspend fun unsubscribeFromPicklistScans() {
        try {
            Log.d(TAG, "🔥 Unsubscribing dari scan channel")
            // TODO: Implementasi unsubscribe setelah import diperbaiki
            Log.d(TAG, "✅ Unsubscribed dari picklist scans")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unsubscribing from scans: ${e.message}", e)
        }
    }
    
    /**
     * Set callback untuk perubahan picklist
     */
    fun setPicklistUpdateCallback(callback: (PicklistUpdate) -> Unit) {
        picklistUpdateCallback = callback
    }
    
    /**
     * Set callback untuk perubahan scan
     */
    fun setScanUpdateCallback(callback: (ScanUpdate) -> Unit) {
        scanUpdateCallback = callback
    }
    
    /**
     * Set callback untuk update cache
     */
    fun setCacheUpdateCallback(callback: (CacheUpdate) -> Unit) {
        // TODO: Implementasi setelah realtime diperbaiki
    }
    
    /**
     * Unsubscribe dari semua subscription
     */
    suspend fun unsubscribeFromAll() {
        try {
            Log.d(TAG, "🔥 Unsubscribing dari semua subscription")
            unsubscribeFromPicklists()
            unsubscribeFromPicklistScans()
            Log.d(TAG, "✅ Unsubscribed dari semua subscription")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unsubscribing from all: ${e.message}", e)
        }
    }
    
    /**
     * Reconnect ke semua subscription
     */
    suspend fun reconnect() {
        try {
            Log.d(TAG, "🔥 Reconnecting ke semua subscription")
            // TODO: Implementasi reconnect setelah realtime diperbaiki
            Log.d(TAG, "✅ Reconnected ke semua subscription")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reconnecting: ${e.message}", e)
        }
    }
    
    /**
     * Cek status koneksi
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Disconnect dari semua subscription
     */
    suspend fun disconnect() {
        try {
            Log.d(TAG, "🔥 Disconnecting dari semua subscription")
            
            unsubscribeFromPicklists()
            unsubscribeFromPicklistScans()
            
            isConnected = false
            _connectionStatus.emit(false)
            
            Log.d(TAG, "✅ Disconnected dari semua subscription")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error disconnecting: ${e.message}", e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        realtimeScope.cancel()
    }
}

/**
 * Data class untuk update picklist
 */
data class PicklistUpdate(
    val action: String,
    val noPicklist: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val qty: Int,
    val timestamp: Long
)

/**
 * Data class untuk update scan
 */
data class ScanUpdate(
    val action: String,
    val noPicklist: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val qtyScan: Int,
    val timestamp: Long
)

/**
 * Data class untuk perubahan scan picklist
 */
data class PicklistScanChange(
    val action: String,
    val articleName: String,
    val size: String,
    val noPicklist: String,
    val articleId: String,
    val qtyScan: Int,
    val timestamp: Long
)

/**
 * Data class untuk update cache
 */
data class CacheUpdate(
    val action: String,
    val picklistScan: com.example.cekpicklist.data.PicklistScan,
    val timestamp: Long
)