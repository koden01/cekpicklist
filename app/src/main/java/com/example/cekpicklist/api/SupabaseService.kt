package com.example.cekpicklist.api

import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.PicklistScan
import com.example.cekpicklist.data.Picklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

class SupabaseService {
    private val supabaseUrl = "https://ngsuhouodaejwkqdxebk.supabase.co"
    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5nc3Vob3VvZGFlandrcWR4ZWJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTk2ODAsImV4cCI6MjA2NzY5NTY4MH0.r9HISpDXkY5wiTzO5EoNQuqPS3KePc4SScoapepj4h0"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Fetching picklists from Supabase for today only with pagination...")
            
            // Dapatkan tanggal hari ini dalam format YYYY-MM-DD
            val today = LocalDate.now().toString()
            val now = java.time.LocalDateTime.now()
            val timezone = java.util.TimeZone.getDefault().id
            
            println("📅 Today's date: $today")
            println("🕐 Current time: $now")
            println("🌍 Timezone: $timezone")
            
            val allPicklists = mutableSetOf<String>()
            var offset = 0
            val limit = 1000
            var hasMoreData = true
            
            while (hasMoreData) {
                val queryUrl = "$supabaseUrl/rest/v1/picklist?select=no_picklist,created_at&created_at=gte.$today&order=created_at.desc&limit=$limit&offset=$offset"
                println("🔍 Query URL (offset=$offset): $queryUrl")
                
                val request = Request.Builder()
                    .url(queryUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .build()
                
                val response = client.newCall(request).execute()
                println("📦 Supabase getPicklists response code: ${response.code}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(responseBody)
                    val batchSize = jsonArray.length()
                    
                    println("📦 Batch ${(offset/limit) + 1} - Response length: ${responseBody.length} characters")
                    println("📊 Batch ${(offset/limit) + 1} - Records found: $batchSize")
                    
                    if (batchSize == 0) {
                        hasMoreData = false
                        break
                    }
                    
                    for (i in 0 until batchSize) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val noPicklist = jsonObject.getString("no_picklist")
                        val createdAt = if (jsonObject.has("created_at") && !jsonObject.isNull("created_at")) {
                            jsonObject.getString("created_at")
                        } else {
                            "null"
                        }
                        allPicklists.add(noPicklist)
                        println("📋 Picklist: $noPicklist, Created: $createdAt")
                    }
                    
                    // Jika batch size kurang dari limit, berarti sudah habis
                    if (batchSize < limit) {
                        hasMoreData = false
                    } else {
                        offset += limit
                    }
                    
                    // Delay antar batch untuk menghindari rate limiting
                    if (hasMoreData) {
                        kotlinx.coroutines.delay(100) // 100ms delay
                    }
                } else {
                    println("❌ Supabase getPicklists request failed with code: ${response.code}")
                    break
                }
            }
            
            println("✅ Found ${allPicklists.size} unique picklist numbers total: ${allPicklists.take(10)}")
            allPicklists.toList() // Konversi kembali ke List
            
        } catch (e: Exception) {
            println("❌ Error fetching picklists: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Fetching picklist items for: $picklistNo")
            
            // Menggunakan pola yang sama dengan readrfiddetekted
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            // Sesuai dengan struktur tabel picklist yang diberikan
            val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=eq.$encodedPicklistNo&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=created_at.asc"
            
            println("🔗 Query URL: $queryUrl")
            
            val request = Request.Builder()
                .url(queryUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            
            val response = client.newCall(request).execute()
            println("📦 Query response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "[]"
                println("📦 Query response body: $responseBody")
                
                if (responseBody != "[]") {
                    val jsonArray = JSONArray(responseBody)
                    val items = mutableListOf<PicklistItem>()
                    // Ambil data scan untuk picklist ini
                    val scanData = getPicklistScans(picklistNo)
                    println("🔍 Scan data untuk $picklistNo: ${scanData.size} items")
                    
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val articleId = jsonObject.getString("article_id")
                        val size = jsonObject.getString("size")
                        
                        // Hitung qtyScan dari data scan
                        val qtyScan = scanData.count { scan ->
                            scan.getString("article_id") == articleId && scan.getString("size") == size
                        }
                        
                        println("🔍 Article: ${jsonObject.getString("article_name")} $size - qtyPl: ${jsonObject.getInt("qty")}, qtyScan: $qtyScan")
                        
                        items.add(
                            PicklistItem(
                                id = jsonObject.getString("id"), // UUID dari Supabase
                                noPicklist = jsonObject.getString("no_picklist"),
                                articleId = articleId,
                                articleName = jsonObject.getString("article_name"),
                                size = size,
                                productId = if (jsonObject.has("product_id") && !jsonObject.isNull("product_id")) jsonObject.getString("product_id") else null,
                                qtyPl = jsonObject.getInt("qty"),
                                qtyScan = qtyScan, // Hitung dari data scan
                                createdAt = if (jsonObject.has("created_at") && !jsonObject.isNull("created_at")) jsonObject.getString("created_at") else null
                            )
                        )
                    }
                    println("✅ SUCCESS! Loaded ${items.size} items for picklist: $picklistNo")
                    
                    // **DEBUGGING**: Log semua item yang dikembalikan dari Supabase
                    println("🔥 === SUPABASE ITEMS DEBUG ===")
                    items.forEach { item ->
                        println("🔥 Supabase item: ${item.articleName} ${item.size} - qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}")
                    }
                    println("🔥 === END SUPABASE ITEMS DEBUG ===")
                    
                    return@withContext items
                } else {
                    println("⚠️ Empty response for picklist: $picklistNo")
                }
            } else {
                println("❌ Query failed with code: ${response.code}")
                val errorBody = response.body?.string()
                println("❌ Error response: $errorBody")
            }
            
            println("❌ No data found for picklist: $picklistNo")
            emptyList()
            
        } catch (e: Exception) {
            println("❌ Error loading picklist items: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun savePicklistScan(picklistScan: PicklistScan): Boolean = withContext(Dispatchers.IO) {
        try {
            println("💾 Saving picklist scan: $picklistScan")
            
            // Sesuai dengan struktur tabel picklist_scan yang sebenarnya
            val jsonObject = JSONObject().apply {
                put("no_picklist", picklistScan.noPicklist)  // Menggunakan no_picklist sebagai primary key
                put("product_id", picklistScan.productId)
                put("article_id", picklistScan.articleId)
                put("article_name", picklistScan.articleName)
                put("size", picklistScan.size)
                put("epc", picklistScan.epc)
                put("notrans", picklistScan.notrans)
            }
            
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/picklist_scan")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(jsonObject.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            println("📦 Supabase savePicklistScan response code: ${response.code}")
            if (response.isSuccessful) {
                println("✅ Picklist scan saved successfully")
            } else {
                println("❌ Failed to save picklist scan, response: ${response.body?.string()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            println("❌ Error saving picklist scan: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    // Fungsi baru untuk mendapatkan data scan berdasarkan picklist number (HANYA HARI INI)
    suspend fun getPicklistScans(picklistNumber: String): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            // Dapatkan tanggal hari ini dalam format YYYY-MM-DD
            val today = LocalDate.now().toString()
            println("📅 Fetching picklist scans for today: $today")
            
            val encodedPicklistNumber = URLEncoder.encode(picklistNumber, "UTF-8")
            // Filter hanya data hari ini dengan created_at >= hari ini
            val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=eq.$encodedPicklistNumber&created_at=gte.$today&order=created_at.desc"
            
            println("🔍 Query URL: $queryUrl")
            
            val request = Request.Builder()
                .url(queryUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            
            val response = client.newCall(request).execute()
            println("📦 Supabase getPicklistScans response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "[]"
                
                if (responseBody != "[]") {
                    val jsonArray = JSONArray(responseBody)
                    val scans = mutableListOf<JSONObject>()
                    for (i in 0 until jsonArray.length()) {
                        scans.add(jsonArray.getJSONObject(i))
                    }
                    println("✅ Found ${scans.size} scan records for today")
                    return@withContext scans
                } else {
                    println("⚠️ No scan records found for today")
                }
            } else {
                println("❌ Query failed with code: ${response.code}")
                val errorBody = response.body?.string()
                println("❌ Error response: $errorBody")
            }
            
            emptyList()
            
        } catch (e: Exception) {
            println("❌ Error loading picklist scans: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Fungsi baru untuk mendapatkan data picklist dengan struktur yang benar
    suspend fun getPicklistData(picklistNo: String): List<Picklist> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Fetching picklist data for: $picklistNo")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=eq.$encodedPicklistNo&order=created_at.asc"
            
            println("🔗 Query URL: $queryUrl")
            
            val request = Request.Builder()
                .url(queryUrl)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            
            val response = client.newCall(request).execute()
            println("📦 Query response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "[]"
                println("📦 Query response body: $responseBody")
                
                if (responseBody != "[]") {
                    val jsonArray = JSONArray(responseBody)
                    val picklists = mutableListOf<Picklist>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        picklists.add(
                            Picklist(
                                id = jsonObject.getString("id"),
                                noPicklist = jsonObject.getString("no_picklist"),
                                articleId = jsonObject.getString("article_id"),
                                size = if (jsonObject.has("size")) jsonObject.getString("size") else null,
                                productId = if (jsonObject.has("product_id")) jsonObject.getString("product_id") else null,
                                qty = jsonObject.getInt("qty"),
                                createdAt = if (jsonObject.has("created_at")) jsonObject.getString("created_at") else null,
                                articleName = if (jsonObject.has("article_name")) jsonObject.getString("article_name") else null
                            )
                        )
                    }
                    println("✅ SUCCESS! Loaded ${picklists.size} picklist items for: $picklistNo")
                    return@withContext picklists
                } else {
                    println("⚠️ Empty response for picklist: $picklistNo")
                }
            } else {
                println("❌ Query failed with code: ${response.code}")
                val errorBody = response.body?.string()
                println("❌ Error response: $errorBody")
            }
            
            println("❌ No data found for picklist: $picklistNo")
            emptyList()
            
        } catch (e: Exception) {
            println("❌ Error loading picklist data: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}