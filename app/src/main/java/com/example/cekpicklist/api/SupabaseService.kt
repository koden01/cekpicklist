package com.example.cekpicklist.api

import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.config.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import com.example.cekpicklist.viewmodel.Sextuple

class SupabaseService {
    
    // Supabase configuration dari SupabaseConfig
    private val supabaseUrl = SupabaseConfig.SUPABASE_URL
    private val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
    
    /**
     * Mendapatkan tanggal hari ini dalam format ISO 8601 untuk filter Supabase
     */
    private fun getTodayDate(): String {
        val today = java.time.LocalDate.now()
        return today.toString() // Format: YYYY-MM-DD
    }
    
    /**
     * Data class untuk picklist sederhana dengan status scan
     */
    data class SimplePicklist(
        val noPicklist: String,
        val isScanned: Boolean,
        val totalCount: Int = 0,
        val scannedCount: Int = 0,
        val unscannedCount: Int = 0
    )
    
    /**
     * METODE BARU: Ambil picklist unik hari ini dengan status scan
     * Sederhana dan efisien - hanya mengambil data yang diperlukan untuk modal
     */
    suspend fun getUniquePicklistsWithScanStatus(): List<SimplePicklist> = withContext(Dispatchers.IO) {
        try {
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "🔥 === GET UNIQUE PICKLISTS WITH SCAN STATUS START ===")
            Log.d("SupabaseService", "🔍 DEBUG: getUniquePicklistsWithScanStatus() called at ${System.currentTimeMillis()}")
            Log.d("SupabaseService", "📅 Filtering picklists (UTC): $startUtc .. $endUtc")
            
            // Ambil semua baris picklist hari ini dengan kolom minimal untuk hitung cepat
            // Kita akan hitung total dan unscanned(false) dari tabel picklist BERDASARKAN qty
            val queryUrl = "$supabaseUrl/rest/v1/picklist?select=no_picklist,scan,qty&created_at=gte.$startUtc&created_at=lt.$endUtc&order=no_picklist.asc&limit=50000"
            
            Log.d("SupabaseService", "🚀 Simple Picklist Query URL: $queryUrl")
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", supabaseKey)
                setRequestProperty("Authorization", "Bearer $supabaseKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
                connectTimeout = 15000 // 15 seconds timeout
                readTimeout = 15000 // 15 seconds timeout
            }
            
            Log.d("SupabaseService", "🔍 DEBUG: Making HTTP request...")
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🔍 DEBUG: Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                Log.d("SupabaseService", "🔍 DEBUG: Raw response length: ${response.length}")
                Log.d("SupabaseService", "🔍 DEBUG: Raw response: $response")
                
                val jsonArray = JSONArray(response)
                Log.d("SupabaseService", "🔍 DEBUG: Parsed JSON array length: ${jsonArray.length()}")
                
                // Group by no_picklist dan hitung total/scanned/unscanned DENGAN MENJUMLAHKAN qty
                // Map: picklist -> Triple(totalQty, scannedQtyFromPicklistFlag, unscannedQtyFromPicklistFlag)
                val countsMap = mutableMapOf<String, Triple<Int, Int, Int>>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val noPicklist = item.getString("no_picklist")
                    val scanAny = item.opt("scan")
                    val qtyVal = item.optInt("qty", 0)
                    val isScanned = when (scanAny) {
                        is Boolean -> scanAny
                        is String -> scanAny.equals("true", ignoreCase = true) || scanAny == "1"
                        is Number -> scanAny.toInt() != 0
                        else -> false
                    }
                    val (total, scanned, unscanned) = countsMap[noPicklist] ?: Triple(0, 0, 0)
                    countsMap[noPicklist] = Triple(
                        total + qtyVal,
                        scanned + if (isScanned) qtyVal else 0,
                        unscanned + if (isScanned) 0 else qtyVal
                    )
                }

                // Ambil jumlah SCAN aktual per picklist dari tabel picklist_scan berdasarkan DISTINCT EPC HARI INI (gunakan rentang UTC yang sama)
                val allPicklistNumbers = countsMap.keys.toList()
                val scannedQtyByPicklist: Map<String, Int> = try {
                    if (allPicklistNumbers.isEmpty()) emptyMap() else {
                        // URL-encode setiap nilai no_picklist untuk keamanan (mengandung spasi, dsb.)
                        val encodedValues = allPicklistNumbers.map { java.net.URLEncoder.encode(it, "UTF-8") }
                        val picklistFilter = encodedValues.joinToString(",")
                        val scanQueryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=in.($picklistFilter)&created_at=gte.$startUtc&created_at=lt.$endUtc&select=no_picklist,epc&limit=50000"
                        val scanUrl = URL(scanQueryUrl)
                        val scanConn = scanUrl.openConnection() as HttpURLConnection
                        scanConn.requestMethod = "GET"
                        scanConn.setRequestProperty("apikey", supabaseKey)
                        scanConn.setRequestProperty("Authorization", "Bearer $supabaseKey")
                        scanConn.setRequestProperty("Content-Type", "application/json")
                        val scanCode = scanConn.responseCode
                        if (scanCode == HttpURLConnection.HTTP_OK) {
                            val scanResp = BufferedReader(InputStreamReader(scanConn.inputStream)).use { it.readText() }
                            val scanArr = JSONArray(scanResp)
                            // Hitung distinct EPC per no_picklist
                            val map = mutableMapOf<String, MutableSet<String>>()
                            for (i in 0 until scanArr.length()) {
                                val obj = scanArr.getJSONObject(i)
                                val pl = obj.getString("no_picklist")
                                val epc = obj.optString("epc", "")
                                if (epc.isNotEmpty()) {
                                    map.getOrPut(pl) { mutableSetOf() }.add(epc)
                                }
                            }
                            map.mapValues { it.value.size }
                        } else emptyMap()
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseService", "❌ Error fetching today's scan counts: ${e.message}")
                    emptyMap()
                }

                val result = countsMap.map { (noPicklist, t) ->
                    val (totalFromPicklist, scannedFromFlag, unscannedFromFlag) = t
                    val scannedFromScans = scannedQtyByPicklist[noPicklist] ?: 0
                    val finalScanned = maxOf(scannedFromFlag, scannedFromScans)
                    val finalUnscanned = (totalFromPicklist - finalScanned).coerceAtLeast(0)
                    SimplePicklist(
                        noPicklist = noPicklist,
                        isScanned = finalScanned > 0,
                        totalCount = totalFromPicklist,
                        scannedCount = finalScanned,
                        unscannedCount = finalUnscanned
                    )
                }.sortedBy { it.noPicklist }
                
                Log.d("SupabaseService", "✅ Found ${result.size} unique picklists")
                Log.d("SupabaseService", "🔍 DEBUG: First 3 picklists: ${result.take(3).map { "${it.noPicklist} scanned=${it.scannedCount}/${it.totalCount} unscanned=${it.unscannedCount}" }}")
                
                return@withContext result
            } else {
                val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                Log.e("SupabaseService", "❌ Error response: $errorResponse")
                return@withContext emptyList()
            }
            
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("SupabaseService", "❌ Timeout getting unique picklists: ${e.message}", e)
            return@withContext emptyList()
        } catch (e: java.net.ConnectException) {
            Log.e("SupabaseService", "❌ Connection error getting unique picklists: ${e.message}", e)
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting unique picklists: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * LAZY LOADING: Ambil data detail picklist saat dipilih
     * Hanya mengambil data untuk picklist yang dipilih
     */
    suspend fun getPicklistDetails(picklistNumber: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "🔥 === GET PICKLIST DETAILS START ===")
            Log.d("SupabaseService", "🔍 DEBUG: getPicklistDetails() called for picklist: $picklistNumber")
            Log.d("SupabaseService", "📅 Filtering data (UTC): $startUtc .. $endUtc")
            
            // Query untuk mengambil detail picklist yang dipilih
            val queryUrl = "$supabaseUrl/rest/v1/picklist?select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at,scan&no_picklist=eq.$picklistNumber&created_at=gte.$startUtc&created_at=lt.$endUtc&order=created_at.asc"
            
            Log.d("SupabaseService", "🚀 Picklist Details Query URL: $queryUrl")
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", supabaseKey)
                setRequestProperty("Authorization", "Bearer $supabaseKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🔍 DEBUG: Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                Log.d("SupabaseService", "🔍 DEBUG: Raw response length: ${response.length}")
                
                val jsonArray = JSONArray(response)
                Log.d("SupabaseService", "🔍 DEBUG: Parsed JSON array length: ${jsonArray.length()}")
                
                val picklistItems = mutableListOf<PicklistItem>()
                
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val picklistItem = PicklistItem(
                        id = item.getString("id"),
                        noPicklist = item.getString("no_picklist"),
                        articleId = item.getString("article_id"),
                        articleName = item.getString("article_name"),
                        size = item.optString("size", ""),
                        productId = item.getString("product_id"),
                        qtyPl = item.getInt("qty"),
                        createdAt = item.getString("created_at"),
                        isScanned = item.optBoolean("scan", false)
                    )
                    picklistItems.add(picklistItem)
                }
                
                Log.d("SupabaseService", "✅ Found ${picklistItems.size} items for picklist $picklistNumber")
                return@withContext picklistItems
            } else {
                val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
                Log.e("SupabaseService", "❌ Error response: $errorResponse")
                return@withContext emptyList()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting picklist details: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * OPTIMASI BARU: Ambil SEMUA data hari ini dalam 1 query saja!
     * Menggabungkan picklist items dan scan data dalam satu query
     */
    suspend fun getAllTodayDataSingleQuery(): Map<String, Pair<List<PicklistItem>, List<String>>> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 === GET ALL TODAY DATA SINGLE QUERY START ===")
            Log.d("SupabaseService", "🔍 DEBUG: getAllTodayDataSingleQuery() called at ${System.currentTimeMillis()}")
            Log.d("SupabaseService", "🚀 ULTRA OPTIMASI: Getting ALL today's data in 1 SINGLE query!")
            
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "📅 Filtering ALL data (UTC): $startUtc .. $endUtc")
            
            // **ULTRA OPTIMASI**: Ambil SEMUA data dalam 1 query dengan JOIN yang lebih sederhana
            val queryUrl = "$supabaseUrl/rest/v1/picklist?created_at=gte.$startUtc&created_at=lt.$endUtc&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=no_picklist.asc,created_at.asc&limit=10000"
            
            Log.d("SupabaseService", "🚀 ULTRA OPTIMASI SINGLE QUERY URL: $queryUrl")
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🚀 ULTRA OPTIMASI Response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                Log.d("SupabaseService", "✅ ULTRA OPTIMASI: Retrieved ${jsonArray.length()} total records")
                
                // **LOCAL PROCESSING**: Olah data di lokal
                val groupedData = mutableMapOf<String, Pair<MutableList<PicklistItem>, MutableSet<String>>>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val picklistNo = jsonObject.getString("no_picklist")
                    
                    // Proses picklist item
                    val item = PicklistItem(
                        id = jsonObject.getString("id"),
                        noPicklist = picklistNo,
                        articleId = jsonObject.getString("article_id"),
                        articleName = jsonObject.getString("article_name"),
                        size = jsonObject.optString("size", ""),
                        productId = if (jsonObject.has("product_id")) jsonObject.getString("product_id") else null,
                        qtyPl = jsonObject.getInt("qty"),
                        qtyScan = 0,
                        tagStatus = "UNSCANNED",
                        lastUpdated = System.currentTimeMillis()
                    )
                    
                    // Group data per picklist (scan data akan diambil terpisah)
                    val existingData = groupedData[picklistNo]
                    if (existingData != null) {
                        existingData.first.add(item)
                    } else {
                        groupedData[picklistNo] = Pair(mutableListOf(item), mutableSetOf())
                    }
                }
                
                // **OPTIMASI**: Ambil scan data untuk semua picklist dalam 1 query
                val allPicklistNumbers = groupedData.keys.toList()
                if (allPicklistNumbers.isNotEmpty()) {
                    Log.d("SupabaseService", "🔍 Getting scan data for ${allPicklistNumbers.size} picklists...")
                    
                    // Buat query untuk semua picklist sekaligus
                    val picklistFilter = allPicklistNumbers.joinToString(",") { "\"$it\"" }
                    val scanQueryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=in.($picklistFilter)&created_at=gte.$startUtc&select=no_picklist,epc&limit=50000"
                    
                    Log.d("SupabaseService", "🔍 Scan Query URL: $scanQueryUrl")
                    
                    try {
                        val scanUrl = URL(scanQueryUrl)
                        val scanConnection = scanUrl.openConnection() as HttpURLConnection
                        scanConnection.requestMethod = "GET"
                        scanConnection.setRequestProperty("apikey", supabaseKey)
                        scanConnection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                        scanConnection.setRequestProperty("Content-Type", "application/json")
                        
                        val scanResponseCode = scanConnection.responseCode
                        Log.d("SupabaseService", "🔍 Scan Query response code: $scanResponseCode")
                        
                        if (scanResponseCode == 200) {
                            val scanResponse = scanConnection.inputStream.bufferedReader().use { it.readText() }
                            val scanJsonArray = JSONArray(scanResponse)
                            
                            Log.d("SupabaseService", "🔍 Retrieved ${scanJsonArray.length()} scan records")
                            
                            // Proses scan data
                            for (j in 0 until scanJsonArray.length()) {
                                val scanObject = scanJsonArray.getJSONObject(j)
                                val picklistNo = scanObject.getString("no_picklist")
                                val epc = scanObject.getString("epc")
                                
                                groupedData[picklistNo]?.second?.add(epc)
                            }
                        } else {
                            Log.e("SupabaseService", "❌ Scan query failed with code: $scanResponseCode")
                        }
                    } catch (e: Exception) {
                        Log.e("SupabaseService", "❌ Error fetching scan data: ${e.message}", e)
                    }
                }
                
                // Convert Set to List untuk konsistensi
                val finalData = groupedData.mapValues { (_, data) ->
                    Pair(data.first, data.second.toList())
                }
                
                Log.d("SupabaseService", "✅ ULTRA OPTIMASI: Processed ${finalData.size} picklists locally")
                Log.d("SupabaseService", "📊 Performance: ${jsonArray.length()} records → ${finalData.size} picklists")
                
                finalData
                
            } else {
                Log.e("SupabaseService", "❌ ULTRA OPTIMASI failed with code: $responseCode")
                emptyMap()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ ULTRA OPTIMASI Error: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Test koneksi ke Supabase
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 === TEST CONNECTION START ===")
            Log.d("SupabaseService", "🔍 DEBUG: testConnection() called at ${System.currentTimeMillis()}")
            Log.d("SupabaseService", "🔍 Testing Supabase connection...")
            val testUrl = "$supabaseUrl/rest/v1/picklist?select=count&limit=1"
            
            val url = URL(testUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000 // 10 seconds timeout
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🔍 Connection test response code: $responseCode")
            
            val isConnected = responseCode in 200..299
            if (isConnected) {
                Log.d("SupabaseService", "✅ Supabase connection successful")
            } else {
                Log.e("SupabaseService", "❌ Supabase connection failed with code: $responseCode")
            }
            
            isConnected
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Supabase connection test failed: ${e.message}", e)
            false
        }
    }
    
    /**
     * Mendapatkan rentang hari ini dalam UTC: [startOfTodayUTC, startOfTomorrowUTC)
     */
    private fun getTodayUtcRange(): Pair<String, String> {
        val todayStartUtc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(java.time.ZoneOffset.UTC)
        val tomorrowStartUtc = todayStartUtc.plusDays(1)
        val start = todayStartUtc.toInstant().toString() // e.g. 2025-10-07T00:00:00Z
        val end = tomorrowStartUtc.toInstant().toString()
        return start to end
    }
    
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        try {
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "🔥 === SUPABASE SERVICE GETPICKLISTS START ===")
            Log.d("SupabaseService", "🔍 DEBUG: getPicklists() called at ${System.currentTimeMillis()}")
            Log.d("SupabaseService", "📅 Filtering picklists (UTC): $startUtc .. $endUtc")
            Log.d("SupabaseService", "🔍 DEBUG: Starting getPicklists() with date range: $startUtc to $endUtc")
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val allPicklists = mutableSetOf<String>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal
            var hasMoreData = true
            
            Log.d("SupabaseService", "🔍 DEBUG: Starting pagination loop with limit=$limit")
            
            while (hasMoreData) {
                val queryUrl = "$supabaseUrl/rest/v1/picklist?select=no_picklist&created_at=gte.$startUtc&created_at=lt.$endUtc&order=created_at.desc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "🔥 Picklist Pagination Query URL (offset=$offset, limit=$limit): $queryUrl")
                
                val url = URL(queryUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                Log.d("SupabaseService", "📦 Picklist Pagination Query response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val responseBody = response.toString()
                    Log.d("SupabaseService", "📦 Picklist Pagination Query response body length: ${responseBody.length}")
                    Log.d("SupabaseService", "🔍 DEBUG: Response body content: $responseBody")
                    
                    if (responseBody != "[]") {
                        val jsonArray = JSONArray(responseBody)
                        Log.d("SupabaseService", "📦 Picklist Pagination Batch Records: ${jsonArray.length()} picklists (offset=$offset)")
                        Log.d("SupabaseService", "🔍 DEBUG: Processing ${jsonArray.length()} picklist records...")
                        
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            val picklistNo = jsonObject.getString("no_picklist")
                            Log.d("SupabaseService", "🔍 DEBUG: Found picklist: $picklistNo")
                            allPicklists.add(picklistNo)
                        }
                        
                        Log.d("SupabaseService", "📦 Added ${jsonArray.length()} picklists to collection. Total unique: ${allPicklists.size}")
                        Log.d("SupabaseService", "🔍 DEBUG: Current unique picklists: ${allPicklists.take(3).joinToString(", ")}${if (allPicklists.size > 3) "..." else ""}")
                        
                        // Cek apakah masih ada data lagi
                        hasMoreData = jsonArray.length() == limit
                        offset += limit
                        
                    } else {
                        // Tidak ada data lagi
                        hasMoreData = false
                        Log.d("SupabaseService", "📦 No more picklist data found at offset $offset")
                        Log.d("SupabaseService", "🔍 DEBUG: Empty response body, stopping pagination")
                    }
                } else {
                    Log.e("SupabaseService", "❌ Picklist Pagination Query failed with code: $responseCode")
                    
                    // **PERBAIKAN**: Coba query yang lebih sederhana jika 400 error
                    if (responseCode == 400) {
                        Log.d("SupabaseService", "🔄 Trying simplified query due to 400 error...")
                        val simpleQueryUrl = "$supabaseUrl/rest/v1/picklist?select=no_picklist&limit=100"
                        Log.d("SupabaseService", "🔄 Simple Query URL: $simpleQueryUrl")
                        
                        try {
                            val simpleUrl = URL(simpleQueryUrl)
                            val simpleConnection = simpleUrl.openConnection() as HttpURLConnection
                            simpleConnection.requestMethod = "GET"
                            simpleConnection.setRequestProperty("apikey", supabaseKey)
                            simpleConnection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                            simpleConnection.setRequestProperty("Content-Type", "application/json")
                            
                            val simpleResponseCode = simpleConnection.responseCode
                            Log.d("SupabaseService", "🔄 Simple Query response code: $simpleResponseCode")
                            
                            if (simpleResponseCode == 200) {
                                val simpleResponse = simpleConnection.inputStream.bufferedReader().use { it.readText() }
                                Log.d("SupabaseService", "🔄 Simple Query response: $simpleResponse")
                                
                                if (simpleResponse != "[]") {
                                    val simpleJsonArray = JSONArray(simpleResponse)
                                    for (i in 0 until simpleJsonArray.length()) {
                                        val jsonObject = simpleJsonArray.getJSONObject(i)
                                        val picklistNo = jsonObject.getString("no_picklist")
                                        allPicklists.add(picklistNo)
                                    }
                                    Log.d("SupabaseService", "🔄 Simple Query found ${simpleJsonArray.length()} picklists")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SupabaseService", "❌ Simple query also failed: ${e.message}")
                        }
                    }
                    
                    hasMoreData = false
                }
            }
            
            Log.d("SupabaseService", "✅ Found ${allPicklists.size} unique picklist numbers after pagination")
            val result = allPicklists.toList()
            Log.d("SupabaseService", "🔍 DEBUG: Final result - ${result.size} picklists: ${result.take(3).joinToString(", ")}${if (result.size > 3) "..." else ""}")
            result
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error fetching picklists: ${e.message}", e)
            Log.e("SupabaseService", "🔍 DEBUG: Exception details", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMASI BARU: Ambil SEMUA data hari ini dalam 1 query saja
     */
    suspend fun getAllTodayData(): Map<String, List<PicklistItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 === GET ALL TODAY DATA START ===")
            Log.d("SupabaseService", "🔍 DEBUG: getAllTodayData() called at ${System.currentTimeMillis()}")
            Log.d("SupabaseService", "🚀 SINGLE QUERY: Getting ALL today's data")
            
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "📅 Filtering ALL items (UTC): $startUtc .. $endUtc")
            
            // **OPTIMASI BARU**: Ambil SEMUA data hari ini dalam 1 query
            val queryUrl = "$supabaseUrl/rest/v1/picklist?created_at=gte.$startUtc&created_at=lt.$endUtc&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=no_picklist.asc,created_at.asc&limit=50000"
            
            Log.d("SupabaseService", "🚀 SINGLE QUERY URL: $queryUrl")
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🚀 SINGLE QUERY Response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                Log.d("SupabaseService", "✅ SINGLE QUERY: Retrieved ${jsonArray.length()} total items")
                
                // Group by picklist number
                val groupedData = mutableMapOf<String, MutableList<PicklistItem>>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val picklistNo = jsonObject.getString("no_picklist")
                    
                    val item = PicklistItem(
                        id = jsonObject.getString("id"),
                        noPicklist = picklistNo,
                        articleId = jsonObject.getString("article_id"),
                        articleName = jsonObject.getString("article_name"),
                        size = jsonObject.optString("size", ""),
                        productId = if (jsonObject.has("product_id")) jsonObject.getString("product_id") else null,
                        qtyPl = jsonObject.getInt("qty"),
                        qtyScan = 0, // Default, akan di-update dari scan data
                        tagStatus = "UNSCANNED",
                        lastUpdated = System.currentTimeMillis()
                    )
                    
                    groupedData.getOrPut(picklistNo) { mutableListOf() }.add(item)
                }
                
                Log.d("SupabaseService", "✅ SINGLE QUERY: Grouped into ${groupedData.size} picklists")
                groupedData
                
            } else {
                Log.e("SupabaseService", "❌ SINGLE QUERY failed with code: $responseCode")
                emptyMap()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ SINGLE QUERY Error: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * OPTIMASI: Batch fetch untuk multiple picklists sekaligus (LEGACY - masih digunakan untuk backward compatibility)
     */
    suspend fun getPicklistItemsBatch(picklistNumbers: List<String>): Map<String, List<PicklistItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🚀 BATCH Getting picklist items for ${picklistNumbers.size} picklists")
            
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "📅 Filtering items (UTC): $startUtc .. $endUtc")
            
            // Buat query dengan multiple picklist numbers menggunakan 'in' operator
            val picklistNumbersStr = picklistNumbers.joinToString(",") { "\"$it\"" }
            val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=in.($picklistNumbersStr)&created_at=gte.$startUtc&created_at=lt.$endUtc&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=created_at.asc&limit=10000"
            
            Log.d("SupabaseService", "🚀 BATCH Query URL: $queryUrl")
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 BATCH Query response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseBody = response.toString()
                Log.d("SupabaseService", "📦 BATCH Query response body: $responseBody")
                
                if (responseBody != "[]") {
                    val jsonArray = JSONArray(responseBody)
                    Log.d("SupabaseService", "Raw DB Records: ${jsonArray.length()} total")
                    
                    // Ambil data scan untuk semua picklists sekaligus
                    val scanData = getPicklistScansBatch(picklistNumbers)
                    Log.d("SupabaseService", "BATCH Scan data: ${scanData.size} items")
                    
                    // Group items by picklist number
                    val groupedItems = mutableMapOf<String, MutableList<PicklistItem>>()
                    
                    // Initialize empty lists for each picklist
                    picklistNumbers.forEach { picklistNo ->
                        groupedItems[picklistNo] = mutableListOf()
                    }
                    
                    // Process all raw records
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val picklistNo = jsonObject.getString("no_picklist")
                        
                        val item = PicklistItem(
                            id = jsonObject.getString("id"),
                            noPicklist = picklistNo,
                            articleId = jsonObject.getString("article_id"),
                            articleName = jsonObject.getString("article_name"),
                            size = jsonObject.getString("size"),
                            productId = if (jsonObject.has("product_id") && !jsonObject.isNull("product_id")) jsonObject.getString("product_id") else null,
                            qtyPl = jsonObject.getInt("qty"),
                            qtyScan = 0, // Will be calculated after grouping
                            createdAt = if (jsonObject.has("created_at") && !jsonObject.isNull("created_at")) jsonObject.getString("created_at") else null
                        )
                        
                        groupedItems[picklistNo]?.add(item)
                    }
                    
                    // Process each picklist's items
                    val result = mutableMapOf<String, List<PicklistItem>>()
                    
                    groupedItems.forEach { (picklistNo, items) ->
                        if (items.isNotEmpty()) {
                            // Group by article_id + size dan sum qtyPl
                            val groupedByArticle = items.groupBy { "${it.articleId}_${it.size}" }
                            val processedItems = mutableListOf<PicklistItem>()
                            
                            groupedByArticle.forEach { (key, itemGroup) ->
                                val firstItem = itemGroup.first()
                                val totalQtyPl = itemGroup.sumOf { it.qtyPl }
                                
                                // Hitung qtyScan dari scan data
                                val picklistScans = scanData.filter { it.getString("no_picklist") == picklistNo }
                                val articleScans = picklistScans.filter { 
                                    it.getString("article_id") == firstItem.articleId && 
                                    it.getString("size") == firstItem.size 
                                }
                                // **PERBAIKAN KRITIS**: Gunakan distinct EPCs untuk menghitung qtyScan yang akurat
                                val distinctEpcs = articleScans.map { it.getString("epc") }.distinct()
                                val qtyScan = distinctEpcs.size
                                
                                // **VERIFIKASI MAPPING**: Log EPC-article mapping saat batch load
                                Log.d("SupabaseService", "🔍 BATCH Grouped Article: ${firstItem.articleName} ${firstItem.size} - qtyPl: ${firstItem.qtyPl}, qtyScan: $qtyScan")
                                
                                // **VERIFIKASI KRITIS**: Cek apakah qtyScan sesuai dengan qtyPl
                                if (qtyScan < firstItem.qtyPl) {
                                    Log.w("SupabaseService", "⚠️ BATCH INCOMPLETE ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) < qtyPl(${firstItem.qtyPl})")
                                } else if (qtyScan > firstItem.qtyPl) {
                                    Log.w("SupabaseService", "⚠️ BATCH OVERSCAN ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) > qtyPl(${firstItem.qtyPl})")
                                } else {
                                    Log.d("SupabaseService", "✅ BATCH COMPLETE ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) = qtyPl(${firstItem.qtyPl})")
                                }
                                
                                if (articleScans.isNotEmpty()) {
                                    Log.d("SupabaseService", "🔍 BATCH EPC-Article mapping verification for ${firstItem.articleName} ${firstItem.size}:")
                                    Log.d("SupabaseService", "🔍   Total scans: ${articleScans.size}, Distinct EPCs: ${distinctEpcs.size}")
                                    articleScans.forEach { scan ->
                                        val epc = scan.getString("epc")
                                        val articleId = scan.getString("article_id")
                                        val size = scan.getString("size")
                                        Log.d("SupabaseService", "🔍   EPC: $epc -> Article: $articleId, Size: $size")
                                    }
                                    Log.d("SupabaseService", "🔍   Distinct EPCs: $distinctEpcs")
                                }
                                
                                val processedItem = firstItem.copy(
                                    qtyPl = totalQtyPl,
                                    qtyScan = qtyScan
                                )
                                
                                processedItems.add(processedItem)
                                
                                Log.d("SupabaseService", "🔍 Grouped Article: ${firstItem.articleName} ${firstItem.size} - qtyPl: $totalQtyPl (from ${itemGroup.size} records), qtyScan: $qtyScan")
                            }
                            
                            result[picklistNo] = processedItems
                            Log.d("SupabaseService", "✅ Processed ${processedItems.size} grouped items for $picklistNo")
                        } else {
                            result[picklistNo] = emptyList()
                            Log.d("SupabaseService", "⚠️ No items found for picklist: $picklistNo")
                        }
                    }
                    
                    Log.d("SupabaseService", "🚀 BATCH Successfully processed ${result.size} picklists")
                    result
                } else {
                    Log.d("SupabaseService", "⚠️ BATCH No data found for any picklist")
                    picklistNumbers.associateWith { emptyList<PicklistItem>() }
                }
            } else {
                Log.e("SupabaseService", "❌ BATCH Query failed with code: $responseCode")
                picklistNumbers.associateWith { emptyList<PicklistItem>() }
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ BATCH Error fetching picklist items: ${e.message}", e)
            picklistNumbers.associateWith { emptyList<PicklistItem>() }
        }
    }
    
    /**
     * OPTIMASI BARU: Ambil SEMUA scan data hari ini dalam 1 query saja
     */
    suspend fun getAllTodayScanData(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 === GET ALL TODAY SCAN DATA START ===")
            Log.d("SupabaseService", "🔍 DEBUG: getAllTodayScanData() called at ${System.currentTimeMillis()}")
            Log.d("SupabaseService", "🚀 SINGLE QUERY: Getting ALL today's scan data")
            
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "📅 Filtering ALL scans (UTC): $startUtc .. $endUtc")
            
            // **OPTIMASI BARU**: Ambil SEMUA scan data hari ini dalam 1 query
            val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?created_at=gte.$startUtc&created_at=lt.$endUtc&select=no_picklist,article_id,size,epc,created_at&order=no_picklist.asc,created_at.asc&limit=50000"
            
            Log.d("SupabaseService", "🚀 SINGLE QUERY SCAN URL: $queryUrl")
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🚀 SINGLE QUERY SCAN Response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                Log.d("SupabaseService", "✅ SINGLE QUERY SCAN: Retrieved ${jsonArray.length()} total scans")
                
                // Group by picklist number
                val groupedData = mutableMapOf<String, MutableList<String>>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val picklistNo = jsonObject.getString("no_picklist")
                    val epc = jsonObject.getString("epc")
                    
                    groupedData.getOrPut(picklistNo) { mutableListOf() }.add(epc)
                }
                
                Log.d("SupabaseService", "✅ SINGLE QUERY SCAN: Grouped into ${groupedData.size} picklists")
                groupedData
                
            } else {
                Log.e("SupabaseService", "❌ SINGLE QUERY SCAN failed with code: $responseCode")
                emptyMap()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ SINGLE QUERY SCAN Error: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * OPTIMASI: Batch fetch untuk scan data multiple picklists (LEGACY)
     */
    suspend fun getPicklistScansBatch(picklistNumbers: List<String>): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val (startUtc, endUtc) = getTodayUtcRange()
            Log.d("SupabaseService", "📅 BATCH Filtering scans (UTC): $startUtc .. $endUtc")
            
            val picklistNumbersStr = picklistNumbers.joinToString(",") { "\"$it\"" }
            val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=in.($picklistNumbersStr)&created_at=gte.$startUtc&created_at=lt.$endUtc&select=no_picklist,article_id,size,epc,created_at&order=created_at.asc&limit=10000"
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseBody = response.toString()
                val jsonArray = JSONArray(responseBody)
                val scans = mutableListOf<JSONObject>()
                
                for (i in 0 until jsonArray.length()) {
                    scans.add(jsonArray.getJSONObject(i))
                }
                
                Log.d("SupabaseService", "✅ BATCH Found ${scans.size} scan records")
                
                // **VERIFIKASI DETAIL**: Log semua EPC yang ditemukan di database
                val allEpcsInDb = scans.map { it.getString("epc") }.distinct()
                Log.d("SupabaseService", "🔍 BATCH VERIFICATION: Total EPCs in database: ${allEpcsInDb.size}")
                Log.d("SupabaseService", "🔍 BATCH VERIFICATION: All EPCs in database: ${allEpcsInDb.joinToString(", ")}")
                
                scans
            } else {
                Log.e("SupabaseService", "❌ BATCH Query failed with code: $responseCode")
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ BATCH Error getting picklist scans: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 Getting picklist items for: $picklistNo")
            
            // Hapus filter tanggal untuk fetch semua baris picklist nomor ini (menghindari kosong karena perbedaan timezone)
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val allItems = mutableListOf<PicklistItem>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal
            var hasMoreData = true
            
            while (hasMoreData) {
                val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
                val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=eq.$encodedPicklistNo&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=created_at.asc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "🔥 Pagination Query URL (offset=$offset, limit=$limit): $queryUrl")
                
                val url = URL(queryUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                Log.d("SupabaseService", "📦 Pagination Query response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val responseBody = response.toString()
                    Log.d("SupabaseService", "📦 Pagination Query response body length: ${responseBody.length}")
                    
                    if (responseBody != "[]") {
                        val jsonArray = JSONArray(responseBody)
                        val batchItems = mutableListOf<PicklistItem>()
                        
                        Log.d("SupabaseService", "📦 Pagination Batch Records: ${jsonArray.length()} items (offset=$offset)")
                        
                        // Proses batch records
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            
                            batchItems.add(
                                PicklistItem(
                                    id = jsonObject.getString("id"),
                                    noPicklist = jsonObject.getString("no_picklist"),
                                    articleId = jsonObject.getString("article_id"),
                                    articleName = jsonObject.getString("article_name"),
                                    size = jsonObject.getString("size"),
                                    productId = if (jsonObject.has("product_id") && !jsonObject.isNull("product_id")) jsonObject.getString("product_id") else null,
                                    qtyPl = jsonObject.getInt("qty"),
                                    qtyScan = 0, // Akan dihitung setelah semua data terkumpul
                                    createdAt = if (jsonObject.has("created_at") && !jsonObject.isNull("created_at")) jsonObject.getString("created_at") else null
                                )
                            )
                        }
                        
                        // Tambahkan batch items ke allItems
                        allItems.addAll(batchItems)
                        Log.d("SupabaseService", "📦 Added ${batchItems.size} items to collection. Total: ${allItems.size}")
                        
                        // Cek apakah masih ada data lagi
                        hasMoreData = jsonArray.length() == limit
                        offset += limit
                        
                    } else {
                        // Tidak ada data lagi
                        hasMoreData = false
                        Log.d("SupabaseService", "📦 No more data found at offset $offset")
                    }
                } else {
                    Log.e("SupabaseService", "❌ Pagination Query failed with code: $responseCode")
                    hasMoreData = false
                }
            }
            
            // **PERBAIKAN**: Proses semua data yang sudah terkumpul setelah pagination selesai
            if (allItems.isNotEmpty()) {
                Log.d("SupabaseService", "📦 Processing ${allItems.size} total items after pagination")
                
                // Ambil data scan untuk picklist ini (sekali saja)
                val scanData = getPicklistScans(picklistNo)
                Log.d("SupabaseService", "📦 Scan data: ${scanData.size} items")
                
                // **DEBUG**: Log semua scan data untuk verifikasi
                Log.d("SupabaseService", "🔍 DEBUG: All scan data:")
                scanData.forEach { scan ->
                    Log.d("SupabaseService", "🔍   Scan: EPC=${scan.getString("epc")}, Article=${scan.getString("article_id")}, Size=${scan.getString("size")}")
                }
                
                // **DEBUG**: Log semua picklist items untuk verifikasi
                Log.d("SupabaseService", "🔍 DEBUG: All picklist items:")
                allItems.forEach { item ->
                    Log.d("SupabaseService", "🔍   Picklist: Article=${item.articleId}, Size=${item.size}, Name=${item.articleName}")
                }
                
                // Group by article_id + size dan sum qtyPl
                val groupedItems = allItems.groupBy { "${it.articleId}_${it.size}" }
                    .map { (_, items) ->
                        val firstItem = items.first()
                        val totalQtyPl = items.sumOf { it.qtyPl }
                        
                        // Hitung qtyScan dari scan data
                        val articleScans = scanData.filter { scan ->
                            scan.getString("article_id") == firstItem.articleId && 
                            scan.getString("size") == firstItem.size
                        }
                        
                        // **FALLBACK**: Jika tidak ada match exact, tidak ada fallback (untuk menghindari false positive)
                        val fallbackScans: List<JSONObject> = emptyList()
                        
                        val finalScans = if (articleScans.isNotEmpty()) articleScans else fallbackScans
                        
                        // **PERBAIKAN KRITIS**: Gunakan distinct EPCs untuk menghitung qtyScan yang akurat
                        val distinctEpcs = finalScans.map { scan -> scan.getString("epc") }.distinct()
                        val qtyScan = distinctEpcs.size
                        
                        // **DEBUG**: Log exact match usage
                        if (articleScans.isNotEmpty()) {
                            Log.d("SupabaseService", "✅ EXACT MATCH: ${firstItem.articleName} ${firstItem.size} - matched ${articleScans.size} scans via article_id + size")
                        }
                        
                        // **VERIFIKASI MAPPING**: Log EPC-article mapping saat load
                        Log.d("SupabaseService", "🔍 Grouped Article: ${firstItem.articleName} ${firstItem.size} - qtyPl: $totalQtyPl (from ${items.size} records), qtyScan: $qtyScan")
                        
                        // **VERIFIKASI KRITIS**: Cek apakah qtyScan sesuai dengan qtyPl
                        if (qtyScan < totalQtyPl) {
                            Log.w("SupabaseService", "⚠️ INCOMPLETE ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) < qtyPl($totalQtyPl)")
                        } else if (qtyScan > totalQtyPl) {
                            Log.w("SupabaseService", "⚠️ OVERSCAN ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) > qtyPl($totalQtyPl)")
                        } else {
                            Log.d("SupabaseService", "✅ COMPLETE ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) = qtyPl($totalQtyPl)")
                        }
                        if (finalScans.isNotEmpty()) {
                            Log.d("SupabaseService", "🔍 EPC-Article mapping verification:")
                            Log.d("SupabaseService", "🔍   Total scans: ${finalScans.size}, Distinct EPCs: ${distinctEpcs.size}")
                            finalScans.forEach { scan ->
                                val epc = scan.getString("epc")
                                val articleId = scan.getString("article_id")
                                val size = scan.getString("size")
                                Log.d("SupabaseService", "🔍   EPC: $epc -> Article: $articleId, Size: $size")
                            }
                            Log.d("SupabaseService", "🔍 Distinct EPCs: $distinctEpcs")
                        }
                        
                        firstItem.copy(
                            qtyPl = totalQtyPl,
                            qtyScan = qtyScan
                        )
                    }
                
                Log.d("SupabaseService", "✅ Processed ${groupedItems.size} grouped items from ${allItems.size} total records")
                groupedItems
            } else {
                Log.w("SupabaseService", "⚠️ No data found for picklist: $picklistNo")
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting picklist items: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get processed EPC list untuk picklist tertentu
     */
    suspend fun getProcessedEpcList(picklistNo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 Getting processed EPC list for picklist: $picklistNo")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val selectUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=eq.$encodedPicklistNo&select=epc"
            
            val url = URL(selectUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Accept", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 Processed EPC list response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("SupabaseService", "📦 Processed EPC list response length: ${response.length}")
                
                // Parse JSON response untuk mendapatkan list EPC
                val epcList = mutableListOf<String>()
                try {
                    val jsonArray = org.json.JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val epc = jsonObject.getString("epc")
                        epcList.add(epc)
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseService", "❌ Error parsing processed EPC list: ${e.message}", e)
                }
                
                // **PERBAIKAN KRITIS**: Log semua EPC yang ditemukan untuk debugging
                Log.d("SupabaseService", "✅ Found ${epcList.size} processed EPCs for picklist: $picklistNo")
                if (epcList.isNotEmpty()) {
                    Log.d("SupabaseService", "🔥 Processed EPCs: ${epcList.joinToString(", ")}")
                }
                
                epcList
            } else {
                Log.e("SupabaseService", "❌ Error getting processed EPC list: HTTP $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting processed EPC list: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * **OPTIMASI**: Batch check multiple EPCs untuk menghindari duplicate saves
     * @param epcs List EPC yang akan dicek
     * @return Set EPC yang sudah ada di database
     */
    suspend fun batchCheckExistingEpcs(epcs: List<String>): Set<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔍 Batch checking ${epcs.size} EPCs for existing records")
            
            if (epcs.isEmpty()) {
                return@withContext emptySet()
            }
            
            // **OPTIMASI**: Gunakan 'in' operator untuk batch check
            val epcsStr = epcs.joinToString(",") { "\"$it\"" }
            val checkUrl = "$supabaseUrl/rest/v1/picklist_scan?epc=in.($epcsStr)&select=epc"
            
            Log.d("SupabaseService", "🔍 Batch check URL: $checkUrl")
            
            val url = URL(checkUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Accept", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 Batch check response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("SupabaseService", "📦 Batch check response: $response")
                
                // Parse JSON response untuk mendapatkan EPC yang sudah ada
                val existingEpcs = mutableSetOf<String>()
                try {
                    val jsonArray = org.json.JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val epc = jsonObject.getString("epc")
                        existingEpcs.add(epc)
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseService", "❌ Error parsing batch check response: ${e.message}", e)
                }
                
                Log.d("SupabaseService", "✅ Found ${existingEpcs.size} existing EPCs out of ${epcs.size} checked")
                existingEpcs
            } else {
                Log.e("SupabaseService", "❌ Error batch checking EPCs: HTTP $responseCode")
                emptySet()
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error batch checking EPCs: ${e.message}", e)
            emptySet()
        }
    }
    
    /**
     * **OPTIMASI MAJOR**: True batch insert untuk multiple picklist scans dalam 1 API call
     * @param scans List of Sextuple(picklistNo, articleId, epc, productId, articleName, size)
     * @return Boolean success status
     */
    suspend fun batchInsertPicklistScans(scans: List<Sextuple<String, String, String, String, String, String>>): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🚀 TRUE BATCH Inserting ${scans.size} picklist scans in 1 API call")
            
            if (scans.isEmpty()) {
                return@withContext true
            }
            
            val insertUrl = "$supabaseUrl/rest/v1/picklist_scan"
            
            // **OPTIMASI**: Build JSON array untuk batch insert
            val jsonArray = scans.joinToString(",") { scan ->
                val (picklistNo, articleId, epc, productId, articleName, size) = scan
                """
                {
                    "no_picklist": "$picklistNo",
                    "product_id": "$productId",
                    "article_id": "$articleId",
                    "article_name": "$articleName",
                    "size": "$size",
                    "epc": "$epc"
                }
                """.trimIndent()
            }
            
            val jsonBody = "[$jsonArray]"
            
            Log.d("SupabaseService", "🚀 Batch insert JSON body length: ${jsonBody.length}")
            Log.d("SupabaseService", "🚀 Batch insert URL: $insertUrl")
            
            val url = URL(insertUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true
            
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 Batch insert response code: $responseCode")
            
            val success = responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK
            Log.d("SupabaseService", "📦 Batch insert result: $success")
            
            if (!success) {
                val errorResponse = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error response"
                } catch (e: Exception) {
                    "Error reading error response: ${e.message}"
                }
                Log.e("SupabaseService", "❌ Batch insert failed with code $responseCode: $errorResponse")
            }
            
            success
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error in batch insert: ${e.message}", e)
            false
        }
    }
    
    suspend fun savePicklistScan(picklistNo: String, articleId: String, epc: String, productId: String, articleName: String = "", size: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 Saving picklist scan: $picklistNo, $articleId, $epc")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val encodedArticleId = URLEncoder.encode(articleId, "UTF-8")
            val encodedEpc = URLEncoder.encode(epc, "UTF-8")
            
            val insertUrl = "$supabaseUrl/rest/v1/picklist_scan"
            
            val jsonBody = """
                {
                    "no_picklist": "$picklistNo",
                    "product_id": "$productId",
                    "article_id": "$articleId",
                    "article_name": "$articleName",
                    "size": "$size",
                    "epc": "$epc"
                }
            """.trimIndent()
            
            val url = URL(insertUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true
            
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 Save response code: $responseCode")
            
            val success = responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK
            Log.d("SupabaseService", "📦 Save result: $success")
            
            if (!success) {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
                Log.e("SupabaseService", "❌ Save failed with code $responseCode: $errorResponse")
                Log.e("SupabaseService", "❌ Request body was: $jsonBody")
            } else {
                Log.i("SupabaseService", "✅ Successfully saved scan: $picklistNo, $articleId, $epc")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error saving picklist scan: ${e.message}", e)
            false
        }
    }
    
    suspend fun getPicklistScans(picklistNumber: String): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 Filtering scan data from today: $todayDate")
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val allScans = mutableListOf<JSONObject>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal
            var hasMoreData = true
            
            while (hasMoreData) {
                val encodedPicklistNo = URLEncoder.encode(picklistNumber, "UTF-8")
                val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=eq.$encodedPicklistNo&created_at=gte.$todayDate&select=no_picklist,article_id,size,epc,created_at&order=created_at.asc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "📦 Scan Pagination Query URL (offset=$offset, limit=$limit): $queryUrl")
                
                val url = URL(queryUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                Log.d("SupabaseService", "📦 Scan Pagination Query response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val responseBody = response.toString()
                    Log.d("SupabaseService", "📦 Scan Pagination Query response body length: ${responseBody.length}")
                    
                    if (responseBody != "[]") {
                        val jsonArray = JSONArray(responseBody)
                        val batchScans = mutableListOf<JSONObject>()
                        
                        Log.d("SupabaseService", "📦 Scan Pagination Batch Records: ${jsonArray.length()} scans (offset=$offset)")
                        
                        for (i in 0 until jsonArray.length()) {
                            batchScans.add(jsonArray.getJSONObject(i))
                        }
                        
                        // Tambahkan batch scans ke allScans
                        allScans.addAll(batchScans)
                        Log.d("SupabaseService", "📦 Added ${batchScans.size} scans to collection. Total: ${allScans.size}")
                        
                        // Cek apakah masih ada data lagi
                        hasMoreData = jsonArray.length() == limit
                        offset += limit
                        
                    } else {
                        // Tidak ada data lagi
                        hasMoreData = false
                        Log.d("SupabaseService", "📦 No more scan data found at offset $offset")
                    }
                } else {
                    Log.e("SupabaseService", "❌ Scan Pagination Query failed with code: $responseCode")
                    hasMoreData = false
                }
            }
            
            Log.d("SupabaseService", "✅ Found ${allScans.size} total scan records after pagination")
            
            // **VERIFIKASI DETAIL**: Log semua EPC yang ditemukan di database
            val allEpcsInDb = allScans.map { it.getString("epc") }.distinct()
            Log.d("SupabaseService", "🔍 SINGLE VERIFICATION: Total EPCs in database: ${allEpcsInDb.size}")
            Log.d("SupabaseService", "🔍 SINGLE VERIFICATION: All EPCs in database: ${allEpcsInDb.joinToString(", ")}")
            
            allScans
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting picklist scans: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Update status picklist di database ketika selesai
     * @param picklistNumber Nomor picklist yang akan diupdate
     * @param status Status baru (completed, in_progress, pending)
     * @return true jika berhasil, false jika gagal
     */
    suspend fun updatePicklistStatus(picklistNumber: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 Updating picklist status: $picklistNumber -> $status")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNumber, "UTF-8")
            val updateUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=eq.$encodedPicklistNo"
            
            Log.d("SupabaseService", "🔥 Update URL: $updateUrl")
            
            val url = URL(updateUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true
            
            val jsonBody = """
                {
                    "status": "$status",
                    "updated_at": "${getCurrentTimestamp()}"
                }
            """.trimIndent()
            
            Log.d("SupabaseService", "🔥 Update body: $jsonBody")
            
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 Update status response code: $responseCode")
            
            val success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT
            Log.d("SupabaseService", "📦 Update status result: $success")
            
            if (success) {
                Log.i("SupabaseService", "✅ Picklist $picklistNumber status updated to: $status")
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
                Log.e("SupabaseService", "❌ Update status failed: $errorResponse")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error updating picklist status: ${e.message}", e)
            false
        }
    }
    
    /**
     * **PERBAIKAN**: Membersihkan data duplikasi di database untuk picklist tertentu
     * @param picklistNo Nomor picklist yang akan dibersihkan
     * @return Boolean success status
     */
    suspend fun cleanupDuplicateScans(picklistNo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🧹 Cleaning up duplicate scans for picklist: $picklistNo")
            
            // Ambil semua scan data untuk picklist ini
            val scanData = getPicklistScans(picklistNo)
            Log.d("SupabaseService", "🧹 Found ${scanData.size} total scan records")
            
            // Group by EPC dan cari duplikasi
            val epcGroups = scanData.groupBy { it.getString("epc") }
            val duplicateEpcs = epcGroups.filter { it.value.size > 1 }
            
            if (duplicateEpcs.isNotEmpty()) {
                Log.w("SupabaseService", "⚠️ Found ${duplicateEpcs.size} EPCs with duplicates:")
                duplicateEpcs.forEach { (epc, records) ->
                    Log.w("SupabaseService", "⚠️   EPC $epc has ${records.size} records")
                    records.forEach { record ->
                        val articleId = record.getString("article_id")
                        val size = record.getString("size")
                        val createdAt = record.getString("created_at")
                        Log.w("SupabaseService", "⚠️     -> Article: $articleId, Size: $size, Created: $createdAt")
                    }
                }
                
                // Hapus duplikasi (keep yang pertama, hapus yang lain)
                var removedCount = 0
                duplicateEpcs.forEach { (epc, records) ->
                    val sortedRecords = records.sortedBy { it.getString("created_at") }
                    val toKeep = sortedRecords.first()
                    val toRemove = sortedRecords.drop(1)
                    
                    Log.d("SupabaseService", "🧹 Keeping first record for EPC $epc, removing ${toRemove.size} duplicates")
                    
                    toRemove.forEach { record ->
                        val recordId = record.getString("id")
                        if (removeScanRecord(recordId)) {
                            removedCount++
                        }
                    }
                }
                
                Log.i("SupabaseService", "✅ Cleanup completed: removed $removedCount duplicate records")
                true
            } else {
                Log.d("SupabaseService", "✅ No duplicates found for picklist: $picklistNo")
                true
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error cleaning up duplicate scans: ${e.message}", e)
            false
        }
    }
    
    /**
     * Helper function untuk menghapus record scan berdasarkan ID
     */
    private suspend fun removeScanRecord(recordId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleteUrl = "$supabaseUrl/rest/v1/picklist_scan?id=eq.$recordId"
            
            val url = URL(deleteUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Prefer", "return=minimal")
            
            val responseCode = connection.responseCode
            val success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT
            
            if (success) {
                Log.d("SupabaseService", "✅ Removed duplicate record: $recordId")
            } else {
                Log.e("SupabaseService", "❌ Failed to remove record $recordId: HTTP $responseCode")
            }
            
            success
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error removing scan record: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get current timestamp in ISO format
     */
    private fun getCurrentTimestamp(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return formatter.format(java.util.Date())
    }
}