package com.example.cekpicklist.repository

import android.util.Log
import com.example.cekpicklist.api.NirwanaApiService
import com.example.cekpicklist.api.SupabaseService
import com.example.cekpicklist.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class Repository {
    private val supabaseService = SupabaseService()
    private var nirwanaToken: String? = null
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val nirwanaRetrofit = Retrofit.Builder()
        .baseUrl("https://api.nirwana.revota.id/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val nirwanaApiService = nirwanaRetrofit.create(NirwanaApiService::class.java)
    
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        supabaseService.getPicklists()
    }
    
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        supabaseService.getPicklistItems(picklistNo)
    }
    
    suspend fun authenticateNirwana(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🔐 Memulai autentikasi Nirwana API...")
            val request = NirwanaAuthRequest(
                username = "nirwana_rfid",
                password = "YNCTD7Zw6yR2KePyq5mZXV8AMJGmwjOQ"
            )
            Log.d("Repository", "🔐 Request auth: username=${request.username}")
            
            val response = nirwanaApiService.authenticate(request)
            Log.d("Repository", "🔐 Response code: ${response.code()}")
            
            if (response.isSuccessful) {
                val authResponse = response.body()
                nirwanaToken = authResponse?.access_token
                Log.d("Repository", "🔐 Auth berhasil! Token: ${nirwanaToken?.take(10)}...")
                true
            } else {
                Log.e("Repository", "🔐 Auth gagal! Code: ${response.code()}, Body: ${response.errorBody()?.string()}")
                false
            }
        } catch (e: Exception) {
            Log.e("Repository", "🔐 Error saat auth: ${e.message}", e)
            false
        }
    }
    
    suspend fun getProductInfoFromRFID(rfidList: List<String>): List<ScanResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🔍 Memulai getProductInfoFromRFID dengan ${rfidList.size} RFID")
            Log.d("Repository", "🔍 RFID List: ${rfidList.joinToString(", ")}")
            
            if (nirwanaToken == null) {
                Log.d("Repository", "🔍 Token kosong, mencoba autentikasi...")
                if (!authenticateNirwana()) {
                    Log.e("Repository", "🔍 Autentikasi gagal, tidak bisa melanjutkan")
                    return@withContext emptyList()
                }
            }
            
            Log.d("Repository", "🔍 Token tersedia: ${nirwanaToken?.take(10)}...")
            
            // Process dengan limit 100 dan loop jika diperlukan
            val limit = 100
            val allScanResults = mutableListOf<ScanResult>()
            var offset = 0
            
            while (offset < rfidList.size) {
                val endIndex = minOf(offset + limit, rfidList.size)
                val batch = rfidList.subList(offset, endIndex)
                val batchNumber = (offset / limit) + 1
                
                Log.d("Repository", "🔍 Processing batch $batchNumber dengan ${batch.size} RFID (offset: $offset)")
                
                val request = NirwanaScanRequest(
                    rfid_list = batch.joinToString(","),
                    limit = limit.toString()
                )
                Log.d("Repository", "🔍 Request body: ${request.rfid_list}")
                Log.d("Repository", "🔍 Request limit: ${request.limit}")
                
                val response = nirwanaApiService.getProductInfo("Bearer $nirwanaToken", request)
                Log.d("Repository", "🔍 Response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d("Repository", "🔍 Batch $batchNumber - Response body length: ${responseBody?.length}")
                    Log.d("Repository", "🔍 Batch $batchNumber - Response data size: ${responseBody?.data?.size ?: 0}")
                    
                    if (responseBody?.data != null) {
                        Log.d("Repository", "🔍 Batch $batchNumber - Response data detail:")
                        responseBody.data.forEachIndexed { index, item ->
                            Log.d("Repository", "🔍 Item $index: ProductID=${item.product_id}, Article=${item.article_name}, Size=${item.size}, RFID=${item.rfid_list.joinToString(", ")}")
                        }
                    }
                    
                    val batchScanResults = responseBody?.data?.flatMap { nirwanaItem ->
                        // Convert setiap RFID dalam rfid_list menjadi ScanResult
                        nirwanaItem.rfid_list.map { rfid ->
                            ScanResult(
                                rfid = rfid,
                                productId = nirwanaItem.product_id,
                                articleId = nirwanaItem.article_id,
                                articleName = nirwanaItem.article_name,
                                size = nirwanaItem.size,
                                warehouse = nirwanaItem.warehouse,
                                tagStatus = nirwanaItem.tag_status
                            )
                        }
                    } ?: emptyList()
                    
                    Log.d("Repository", "🔍 Batch $batchNumber - ScanResults created: ${batchScanResults.size} items")
                    allScanResults.addAll(batchScanResults)
                    
                    // Jika hasil kurang dari limit, berarti sudah habis
                    if (responseBody?.data?.size ?: 0 < limit) {
                        Log.d("Repository", "🔍 Batch $batchNumber - Hasil kurang dari limit, tidak ada data lagi")
                        break
                    }
                    
                    // Delay antar batch untuk menghindari rate limiting
                    if (endIndex < rfidList.size) {
                        kotlinx.coroutines.delay(200) // 200ms delay
                    }
                } else {
                    Log.e("Repository", "🔍 Batch $batchNumber failed with code: ${response.code()}")
                    break
                }
                
                offset += limit
            }
            
            Log.d("Repository", "🔍 Total ScanResults created: ${allScanResults.size} items")
            allScanResults.forEachIndexed { index, result ->
                Log.d("Repository", "🔍 ScanResult $index: RFID=${result.rfid}, ProductID=${result.productId}, Article=${result.articleName}, Size=${result.size}")
            }
            
            allScanResults
        } catch (e: Exception) {
            Log.e("Repository", "🔍 Error saat getProductInfoFromRFID: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun savePicklistScan(picklistScan: PicklistScan): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "💾 Memulai savePicklistScan ke Supabase")
            Log.d("Repository", "💾 PicklistNumber: ${picklistScan.noPicklist}")
            Log.d("Repository", "💾 ProductID: ${picklistScan.productId}")
            Log.d("Repository", "💾 ArticleName: ${picklistScan.articleName}")
            Log.d("Repository", "💾 Size: ${picklistScan.size}")
            Log.d("Repository", "💾 EPC: ${picklistScan.epc}")
            
            val result = supabaseService.savePicklistScan(picklistScan)
            Log.d("Repository", "💾 Save result: $result")
            result
        } catch (e: Exception) {
            Log.e("Repository", "💾 Error saat savePicklistScan: ${e.message}", e)
            false
        }
    }
    
    suspend fun getPicklistScans(picklistNumber: String): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🔍 Memulai getPicklistScans untuk: $picklistNumber")
            val result = supabaseService.getPicklistScans(picklistNumber)
            Log.d("Repository", "🔍 getPicklistScans result: ${result.size} scans")
            result
        } catch (e: Exception) {
            Log.e("Repository", "🔍 Error saat getPicklistScans: ${e.message}", e)
            emptyList()
        }
    }
}
