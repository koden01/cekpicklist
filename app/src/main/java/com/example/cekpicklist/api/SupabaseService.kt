package com.example.cekpicklist.api

import android.util.Log
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.OutActivityItem
import com.example.cekpicklist.config.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import com.example.cekpicklist.viewmodel.Sextuple

class SupabaseService {
    
    // Supabase configuration dari SupabaseConfig
    private val supabaseUrl = SupabaseConfig.SUPABASE_URL
    private val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
    
    companion object {
        // Retry settings untuk network errors
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 1000L // 1 second base delay
    }
    
    /**
     * Helper function untuk setup HttpURLConnection dengan timeout
     */
    private fun setupConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000 // 30 detik connection timeout
        connection.readTimeout = 45000 // 45 detik read timeout
        return connection
    }
    
    /**
     * Helper function untuk menentukan apakah exception bisa di-retry
     */
    private fun isRetriableException(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is IOException -> true
            else -> false
        }
    }
    
    /**
     * Helper function untuk retry dengan exponential backoff
     */
    private suspend fun <T> retryOnNetworkError(
        operation: String,
        maxRetries: Int = MAX_RETRY_ATTEMPTS,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var lastError: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                attempt++
                
                if (isRetriableException(e) && attempt <= maxRetries) {
                    val backoff = BASE_BACKOFF_MS * (1L shl (attempt - 1)) // Exponential backoff
                    Log.w("SupabaseService", "⚠️ Retry $operation attempt $attempt/$maxRetries in ${backoff}ms due to: ${e.message}")
                    delay(backoff)
                } else {
                    // Non-retriable error or max retries reached
                    throw e
                }
            }
        }
        
        throw lastError ?: Exception("Unknown error after $maxRetries attempts")
    }
    
    /**
     * Mendapatkan tanggal hari ini dalam format ISO 8601 untuk filter Supabase
     * **PERBAIKAN**: Gunakan UTC timezone untuk konsistensi dengan Supabase
     */
    private fun getTodayDate(): String {
        val utcNow = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        val today = utcNow.toLocalDate()
        return today.toString() // Format: YYYY-MM-DD (UTC)
    }
    
    /**
     * Mendapatkan tanggal kemarin dalam format ISO 8601 untuk filter Supabase
     * **PERBAIKAN**: Gunakan UTC timezone untuk konsistensi dengan Supabase
     * Digunakan untuk clear operations yang membutuhkan data yang lebih fleksibel
     */
    private fun getYesterdayDate(): String {
        val utcNow = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        val yesterday = utcNow.toLocalDate().minusDays(1)
        return yesterday.toString() // Format: YYYY-MM-DD (UTC)
    }
    
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        try {
            return@withContext retryOnNetworkError("getPicklists") {
                val todayDate = getTodayDate()
                Log.d("SupabaseService", "📅 Filtering data from today: $todayDate")
                
                // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
                val allPicklists = mutableSetOf<String>()
                var offset = 0
                val limit = 1000 // Supabase limit maksimal
                var hasMoreData = true
                
                while (hasMoreData) {
                    val queryUrl = "$supabaseUrl/rest/v1/picklist?select=no_picklist&created_at=gte.$todayDate&order=created_at.desc&limit=$limit&offset=$offset"
                    
                    Log.d("SupabaseService", "🔥 Picklist Pagination Query URL (offset=$offset, limit=$limit): $queryUrl")
                    
                    val url = URL(queryUrl)
                    val connection = setupConnection(url)
                    
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
                        
                        if (responseBody != "[]") {
                            val jsonArray = JSONArray(responseBody)
                            Log.d("SupabaseService", "📦 Picklist Pagination Batch Records: ${jsonArray.length()} picklists (offset=$offset)")
                            
                            for (i in 0 until jsonArray.length()) {
                                val jsonObject = jsonArray.getJSONObject(i)
                                val picklistNo = jsonObject.getString("no_picklist")
                                allPicklists.add(picklistNo)
                            }
                            
                            Log.d("SupabaseService", "📦 Added ${jsonArray.length()} picklists to collection. Total unique: ${allPicklists.size}")
                            
                            // Cek apakah masih ada data lagi
                            hasMoreData = jsonArray.length() == limit
                            offset += limit
                            
                        } else {
                            // Tidak ada data lagi
                            hasMoreData = false
                            Log.d("SupabaseService", "📦 No more picklist data found at offset $offset")
                        }
                    } else {
                        Log.e("SupabaseService", "❌ Picklist Pagination Query failed with code: $responseCode")
                        hasMoreData = false
                    }
                }
                
                Log.d("SupabaseService", "✅ Found ${allPicklists.size} unique picklist numbers after pagination")
                allPicklists.toList()
            }
        } catch (e: Exception) {
            // Final catch after all retries exhausted
            Log.e("SupabaseService", "❌ Error fetching picklists after retries: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMASI: Batch fetch untuk multiple picklists sekaligus
     */
    suspend fun getPicklistItemsBatch(picklistNumbers: List<String>): Map<String, List<PicklistItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🚀 BATCH Getting picklist items for ${picklistNumbers.size} picklists")
            
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 Filtering data from today: $todayDate")
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val picklistNumbersStr = picklistNumbers.joinToString(",") { "\"$it\"" }
            val allItems = mutableListOf<PicklistItem>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal per request
            var hasMoreData = true
            
            while (hasMoreData) {
                val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=in.($picklistNumbersStr)&created_at=gte.$todayDate&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=created_at.asc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "🚀 BATCH Query URL (offset=$offset, limit=$limit): $queryUrl")
                
                val url = URL(queryUrl)
                val connection = setupConnection(url)
                
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
                    
                    if (responseBody != "[]" && responseBody.isNotEmpty()) {
                        val jsonArray = JSONArray(responseBody)
                        Log.d("SupabaseService", "📦 BATCH Retrieved ${jsonArray.length()} items (offset=$offset, total=${allItems.size + jsonArray.length()})")
                        
                        // Process all raw records in this batch
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
                            
                            allItems.add(item)
                        }
                        
                        // Cek apakah masih ada data lagi
                        hasMoreData = jsonArray.length() == limit
                        offset += limit
                    } else {
                        // Tidak ada data lagi
                        hasMoreData = false
                    }
                } else {
                    Log.e("SupabaseService", "❌ BATCH Query failed with code: $responseCode")
                    hasMoreData = false
                }
            }
            
            // Setelah semua data dikumpulkan, process items
            if (allItems.isNotEmpty()) {
                Log.d("SupabaseService", "📦 BATCH Total items collected: ${allItems.size}")
                
                // Ambil data scan untuk semua picklists sekaligus
                val scanData = getPicklistScansBatch(picklistNumbers)
                Log.d("SupabaseService", "BATCH Scan data: ${scanData.size} items")
                
                // Group items by picklist number
                val groupedItems = mutableMapOf<String, MutableList<PicklistItem>>()
                
                // Initialize empty lists for each picklist
                picklistNumbers.forEach { picklistNo ->
                    groupedItems[picklistNo] = mutableListOf()
                }
                
                // Process all collected items
                allItems.forEach { item ->
                    groupedItems[item.noPicklist]?.add(item)
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
                            // **PERBAIKAN**: Gunakan totalQtyPl (bukan firstItem.qtyPl) untuk konsistensi
                            Log.d("SupabaseService", "🔍 BATCH Grouped Article: ${firstItem.articleName} ${firstItem.size} - qtyPl: $totalQtyPl (from ${itemGroup.size} records), qtyScan: $qtyScan")
                            
                            // **VERIFIKASI KRITIS**: Cek apakah qtyScan sesuai dengan totalQtyPl (bukan firstItem.qtyPl)
                            if (qtyScan < totalQtyPl) {
                                Log.w("SupabaseService", "⚠️ BATCH INCOMPLETE ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) < qtyPl($totalQtyPl)")
                            } else if (qtyScan > totalQtyPl) {
                                Log.w("SupabaseService", "⚠️ BATCH OVERSCAN ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) > qtyPl($totalQtyPl)")
                            } else {
                                Log.d("SupabaseService", "✅ BATCH COMPLETE ITEM: ${firstItem.articleName} ${firstItem.size} - qtyScan($qtyScan) = qtyPl($totalQtyPl)")
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
                
                Log.d("SupabaseService", "🚀 BATCH Successfully processed ${result.size} picklists (total items: ${allItems.size})")
                result
            } else {
                Log.d("SupabaseService", "⚠️ BATCH No items found for any picklist")
                picklistNumbers.associateWith { emptyList<PicklistItem>() }
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ BATCH Error fetching picklist items: ${e.message}", e)
            picklistNumbers.associateWith { emptyList<PicklistItem>() }
        }
    }
    
    /**
     * OPTIMASI: Batch fetch untuk scan data multiple picklists
     */
    suspend fun getPicklistScansBatch(picklistNumbers: List<String>): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 BATCH Filtering scan data from today: $todayDate")
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val picklistNumbersStr = picklistNumbers.joinToString(",") { "\"$it\"" }
            val allScans = mutableListOf<JSONObject>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal per request
            var hasMoreData = true
            
            while (hasMoreData) {
                val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=in.($picklistNumbersStr)&created_at=gte.$todayDate&select=no_picklist,article_id,size,epc,created_at&order=created_at.asc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "📦 BATCH Scan Query URL (offset=$offset, limit=$limit)")
                
                val url = URL(queryUrl)
                val connection = setupConnection(url)
                
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
                    
                    if (responseBody != "[]" && responseBody.isNotEmpty()) {
                        val jsonArray = JSONArray(responseBody)
                        Log.d("SupabaseService", "📦 BATCH Retrieved ${jsonArray.length()} scan records (offset=$offset, total=${allScans.size + jsonArray.length()})")
                        
                        for (i in 0 until jsonArray.length()) {
                            allScans.add(jsonArray.getJSONObject(i))
                        }
                        
                        // Cek apakah masih ada data lagi
                        hasMoreData = jsonArray.length() == limit
                        offset += limit
                    } else {
                        // Tidak ada data lagi
                        hasMoreData = false
                    }
                } else {
                    Log.e("SupabaseService", "❌ BATCH Query failed with code: $responseCode")
                    hasMoreData = false
                }
            }
            
            Log.d("SupabaseService", "✅ BATCH Found ${allScans.size} total scan records")
            
            // **VERIFIKASI DETAIL**: Log semua EPC yang ditemukan di database
            val allEpcsInDb = allScans.map { it.getString("epc") }.distinct()
            Log.d("SupabaseService", "🔍 BATCH VERIFICATION: Total EPCs in database: ${allEpcsInDb.size}")
            if (allEpcsInDb.size <= 20) {
                Log.d("SupabaseService", "🔍 BATCH VERIFICATION: All EPCs in database: ${allEpcsInDb.joinToString(", ")}")
            } else {
                Log.d("SupabaseService", "🔍 BATCH VERIFICATION: Sample EPCs (first 20): ${allEpcsInDb.take(20).joinToString(", ")}...")
            }
            
            allScans
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ BATCH Error getting picklist scans: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun getPicklistItems(picklistNo: String): List<PicklistItem> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔥 Getting picklist items for: $picklistNo")
            
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 Filtering data from today: $todayDate")
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val allItems = mutableListOf<PicklistItem>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal
            var hasMoreData = true
            
            while (hasMoreData) {
                val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
                val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=eq.$encodedPicklistNo&created_at=gte.$todayDate&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=created_at.asc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "🔥 Pagination Query URL (offset=$offset, limit=$limit): $queryUrl")
                
                val url = URL(queryUrl)
                val connection = setupConnection(url)
                
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
            
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 Filtering processed EPC data from today: $todayDate")
            
            // **PERBAIKAN**: Gunakan pagination untuk mengatasi limit 1000 Supabase
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val allEpcs = mutableListOf<String>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal per request
            var hasMoreData = true
            
            while (hasMoreData) {
                val selectUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=eq.$encodedPicklistNo&created_at=gte.$todayDate&select=epc&order=created_at.asc&limit=$limit&offset=$offset"
                
                Log.d("SupabaseService", "📦 Processed EPC Pagination Query (offset=$offset, limit=$limit)")
                
                val url = URL(selectUrl)
                val connection = setupConnection(url)
                
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("Accept", "application/json")
                
                val responseCode = connection.responseCode
                Log.d("SupabaseService", "📦 Processed EPC list response code: $responseCode")
                
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    
                    if (response != "[]" && response.isNotEmpty()) {
                        try {
                            val jsonArray = org.json.JSONArray(response)
                            Log.d("SupabaseService", "📦 Retrieved ${jsonArray.length()} EPCs (offset=$offset, total=${allEpcs.size + jsonArray.length()})")
                            
                            for (i in 0 until jsonArray.length()) {
                                val jsonObject = jsonArray.getJSONObject(i)
                                val epc = jsonObject.getString("epc")
                                allEpcs.add(epc)
                            }
                            
                            // Cek apakah masih ada data lagi
                            hasMoreData = jsonArray.length() == limit
                            offset += limit
                        } catch (e: Exception) {
                            Log.e("SupabaseService", "❌ Error parsing processed EPC list: ${e.message}", e)
                            hasMoreData = false
                        }
                    } else {
                        // Tidak ada data lagi
                        hasMoreData = false
                    }
                } else {
                    Log.e("SupabaseService", "❌ Error getting processed EPC list: HTTP $responseCode")
                    hasMoreData = false
                }
            }
            
            // **PERBAIKAN KRITIS**: Log semua EPC yang ditemukan untuk debugging
            Log.d("SupabaseService", "✅ Found ${allEpcs.size} total processed EPCs for picklist: $picklistNo")
            if (allEpcs.isNotEmpty()) {
                if (allEpcs.size <= 20) {
                    Log.d("SupabaseService", "🔥 Processed EPCs: ${allEpcs.joinToString(", ")}")
                } else {
                    Log.d("SupabaseService", "🔥 Processed EPCs (first 20): ${allEpcs.take(20).joinToString(", ")}... (total: ${allEpcs.size})")
                }
            } else {
                Log.w("SupabaseService", "⚠️ No processed EPCs found in database for picklist: $picklistNo")
                Log.w("SupabaseService", "⚠️ This could mean: 1) No EPCs were ever scanned for this picklist, or 2) Database query returned empty result")
            }
            
            allEpcs
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting processed EPC list: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * DEBUGGING: Get all scan records for a picklist (without date filter)
     */
    suspend fun getAllScanRecordsForPicklist(picklistNo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🔍 Getting all scan records for picklist: $picklistNo (no date filter)")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val selectUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=eq.$encodedPicklistNo&select=epc"
            
            val url = URL(selectUrl)
            val connection = setupConnection(url)
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Accept", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 All scan records response code: $responseCode")
            Log.d("SupabaseService", "📦 All scan records query URL: $selectUrl")
            
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                Log.d("SupabaseService", "📦 All scan records response length: ${response.length}")
                Log.d("SupabaseService", "📦 All scan records raw response: $response")
                
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
                    Log.e("SupabaseService", "❌ Error parsing all scan records: ${e.message}", e)
                }
                
                Log.d("SupabaseService", "✅ Found ${epcList.size} total scan records for picklist: $picklistNo")
                if (epcList.isNotEmpty()) {
                    Log.d("SupabaseService", "🔥 All scan record EPCs: ${epcList.joinToString(", ")}")
                } else {
                    Log.w("SupabaseService", "⚠️ No scan records found for picklist: $picklistNo")
                }
                
                epcList
            } else {
                Log.e("SupabaseService", "❌ Failed to get all scan records: HTTP $responseCode")
                val errorResponse = connection.errorStream?.let { 
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "No error message"
                Log.e("SupabaseService", "❌ Error response: $errorResponse")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error getting all scan records: ${e.message}", e)
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
            val connection = setupConnection(url)
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Accept", "application/json")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "📦 Batch check response code: $responseCode")
            
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
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
                    connection.errorStream?.let { 
                        BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                    } ?: "No error response"
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
    
    suspend fun savePicklistScan(picklistNo: String, articleId: String, epc: String, productId: String, articleName: String = "", size: String = "", notrans: String? = null): Boolean = withContext(Dispatchers.IO) {
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
                    "epc": "$epc",
                    "notrans": ${if (notrans != null) "\"$notrans\"" else "null"},
                    "created_at": "${getCurrentTimestamp()}"
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
                val errorResponse = connection.errorStream?.let { 
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "No error message"
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

    suspend fun saveOutActivityScans(notrans: String, items: List<OutActivityItem>): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true
        try {
            return@withContext retryOnNetworkError("saveOutActivityScans") {
                val insertUrl = "$supabaseUrl/rest/v1/picklist_scan"
                val jsonArray = org.json.JSONArray()

                items.forEach { item ->
                    // **PERBAIKAN**: Hanya kirim kolom yang ada di tabel picklist_scan
                    // Kolom yang ada: no_picklist, product_id, article_id, article_name, size, epc, notrans, created_at
                    val jsonObject = org.json.JSONObject().apply {
                        put("no_picklist", notrans)
                        put("notrans", notrans)
                        put("product_id", item.productId ?: "")
                        put("article_id", item.articleId)
                        put("article_name", item.articleName)
                        put("size", item.size)
                        put("epc", item.epc)
                        put("created_at", getCurrentTimestamp())
                        // **HAPUS**: brand, category, sub_category, color, gender, warehouse, tag_status
                        // (kolom-kolom ini tidak ada di tabel picklist_scan)
                    }
                    jsonArray.put(jsonObject)
                }

                val url = URL(insertUrl)
                val connection = setupConnection(url)
                connection.requestMethod = "POST"
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "return=minimal")
                connection.doOutput = true

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonArray.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                val success = responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK
                if (!success) {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                    Log.e("SupabaseService", "❌ Failed to save out activity scans: HTTP $responseCode, body=$errorResponse")
                } else {
                    Log.d("SupabaseService", "✅ Saved ${items.size} out activity scans with notrans=$notrans")
                }
                success
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error saving out activity scans: ${e.message}", e)
            false
        }
    }

    suspend fun getNextOutTransactionId(dateString: String): String = withContext(Dispatchers.IO) {
        retryOnNetworkError("getNextOutTransactionId") {
            val latest = fetchLatestOutTransactionId(dateString)
            val nextSequence = (latest?.let { extractOutSequence(it) } ?: 0) + 1
            buildOutNotrans(nextSequence, dateString)
        }
    }

    private fun buildOutNotrans(sequence: Int, dateString: String): String {
        return "OUT${String.format("%02d", sequence)}$dateString"
    }

    private fun extractOutSequence(notrans: String): Int {
        return try {
            val datePartLength = 8
            if (!notrans.startsWith("OUT") || notrans.length <= 3 + datePartLength) return 0
            val numberPart = notrans.substring(3, notrans.length - datePartLength)
            numberPart.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun fetchLatestOutTransactionId(dateString: String): String? {
        return try {
            val todayIso = getTodayDate()
            val pattern = URLEncoder.encode("OUT*$dateString", "UTF-8")
            val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?select=no_picklist&no_picklist=like.$pattern&created_at=gte.$todayIso&order=created_at.desc&limit=1"
            val url = URL(queryUrl)
            val connection = setupConnection(url)
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(response)
                if (jsonArray.length() > 0) {
                    val latest = jsonArray.getJSONObject(0).optString("no_picklist", null)
                    Log.d("SupabaseService", "✅ Latest OUT notrans from Supabase: $latest")
                    latest
                } else {
                    null
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("SupabaseService", "❌ Failed to fetch latest OUT notrans: HTTP $responseCode, body=$errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error fetching latest OUT notrans: ${e.message}", e)
            null
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
                val connection = setupConnection(url)
                
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
                val errorResponse = connection.errorStream?.let { 
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "No error message"
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
     * Get current timestamp in ISO format (UTC)
     */
    private fun getCurrentTimestamp(): String {
        return java.time.Instant.now().toString() // ISO 8601 format in UTC
    }
    
    /**
     * PERBAIKAN: Hapus SEMUA data scan dari tabel picklist_scan untuk picklist tertentu
     * @param picklistNo Nomor picklist
     * @return true jika berhasil, false jika gagal
     */
    suspend fun deleteAllScanRecordsForPicklist(picklistNo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🧹 Deleting ALL scan records for picklist: $picklistNo")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val deleteUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=eq.$encodedPicklistNo"
            
            Log.d("SupabaseService", "🔥 Delete all scan records URL: $deleteUrl")
            
            val url = URL(deleteUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Prefer", "return=minimal")
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🔥 Delete all scan records response code: $responseCode")
            
            val success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT
            
            if (success) {
                Log.d("SupabaseService", "✅ All scan records deleted for picklist: $picklistNo")
            } else {
                val errorMessage = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("SupabaseService", "❌ Failed to delete all scan records: $responseCode - $errorMessage")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error deleting all scan records: ${e.message}", e)
            false
        }
    }

    /**
     * PERBAIKAN: Reset qtyScan untuk item picklist tertentu (untuk cleanup overscan)
     * @param picklistNo Nomor picklist
     * @param articleId ID artikel
     * @param size Ukuran
     * @param targetQtyScan Target qtyScan yang diinginkan (biasanya qtyPl)
     * @return true jika berhasil, false jika gagal
     */
    suspend fun resetPicklistItemQtyScan(picklistNo: String, articleId: String, size: String, targetQtyScan: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SupabaseService", "🧹 Resetting qtyScan for picklist: $picklistNo, article: $articleId, size: $size")
            
            val encodedPicklistNo = URLEncoder.encode(picklistNo, "UTF-8")
            val encodedArticleId = URLEncoder.encode(articleId, "UTF-8")
            val encodedSize = URLEncoder.encode(size, "UTF-8")
            
            val updateUrl = "$supabaseUrl/rest/v1/picklist_item?no_picklist=eq.$encodedPicklistNo&article_id=eq.$encodedArticleId&size=eq.$encodedSize"
            
            Log.d("SupabaseService", "🔥 Reset qtyScan URL: $updateUrl")
            
            val url = URL(updateUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            
            // Reset qtyScan ke targetQtyScan (biasanya qtyPl)
            val updateData = """
                {
                    "qty_scan": $targetQtyScan,
                    "updated_at": "${getCurrentTimestamp()}"
                }
            """.trimIndent()
            
            connection.doOutput = true
            connection.outputStream.use { outputStream ->
                outputStream.write(updateData.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = connection.responseCode
            Log.d("SupabaseService", "🔥 Reset qtyScan response code: $responseCode")
            
            if (responseCode in 200..299) {
                Log.d("SupabaseService", "✅ Successfully reset qtyScan for $articleId $size in picklist $picklistNo to $targetQtyScan")
                true
            } else {
                val errorMessage = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("SupabaseService", "❌ Failed to reset qtyScan: $responseCode - $errorMessage")
                false
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error resetting qtyScan: ${e.message}", e)
            false
        }
    }
}