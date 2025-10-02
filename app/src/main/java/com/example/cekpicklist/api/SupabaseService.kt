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
    
    suspend fun getPicklists(): List<String> = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 Filtering data from today: $todayDate")
            
            val queryUrl = "$supabaseUrl/rest/v1/picklist?select=no_picklist&created_at=gte.$todayDate&order=created_at.desc"
            
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
                
                val jsonArray = JSONArray(response.toString())
                val allPicklists = mutableSetOf<String>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val picklistNo = jsonObject.getString("no_picklist")
                    allPicklists.add(picklistNo)
                }
                
                Log.d("SupabaseService", "✅ Found ${allPicklists.size} unique picklist numbers")
                allPicklists.toList()
            } else {
                Log.e("SupabaseService", "❌ Query failed with code: $responseCode")
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.e("SupabaseService", "❌ Error fetching picklists: ${e.message}", e)
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
            
            // Buat query dengan multiple picklist numbers menggunakan 'in' operator
            val picklistNumbersStr = picklistNumbers.joinToString(",") { "\"$it\"" }
            val queryUrl = "$supabaseUrl/rest/v1/picklist?no_picklist=in.($picklistNumbersStr)&created_at=gte.$todayDate&select=id,no_picklist,article_id,article_name,size,product_id,qty,created_at&order=created_at.asc&limit=10000"
            
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
     * OPTIMASI: Batch fetch untuk scan data multiple picklists
     */
    suspend fun getPicklistScansBatch(picklistNumbers: List<String>): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            Log.d("SupabaseService", "📅 BATCH Filtering scan data from today: $todayDate")
            
            val picklistNumbersStr = picklistNumbers.joinToString(",") { "\"$it\"" }
            val queryUrl = "$supabaseUrl/rest/v1/picklist_scan?no_picklist=in.($picklistNumbersStr)&created_at=gte.$todayDate&select=no_picklist,article_id,size,epc,created_at&order=created_at.asc&limit=10000"
            
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