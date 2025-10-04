package com.example.cekpicklist.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.Properties
import java.io.FileInputStream

/**
 * NirwanaApiService - Service untuk komunikasi dengan API Nirwana
 * Digunakan untuk mentranslate EPC menjadi article ID dan mendapatkan informasi produk
 */
class NirwanaApiService {
    
    companion object {
        private const val TAG = "NirwanaApiService"
        
        // API endpoints sesuai spesifikasi yang benar
        private const val DEFAULT_BASE_URL = "https://api.nirwana.revota.id"
        private const val AUTH_ENDPOINT = "/auth/token"
        private const val TAG_PRODUCTS_ENDPOINT = "/tag/products"
        
        // Timeout settings (dinaikkan agar lebih toleran terhadap jaringan lambat)
        private const val CONNECT_TIMEOUT = 30000 // 30 seconds
        private const val READ_TIMEOUT = 45000 // 45 seconds

        // Retry settings untuk error jaringan
        private const val MAX_RETRY_ATTEMPTS = 4
        private const val BASE_BACKOFF_MS = 1000L
        
        // Verbose logging flags to reduce logcat noise
        private const val VERBOSE_LOGS = false
        private const val LOG_HTTP_BODIES = false

        // **PERBAIKAN BARU**: Gson instance untuk parsing JSON
        private val gson = Gson()
    }
    
    private val authMutex = Mutex()

    private var baseUrl: String = DEFAULT_BASE_URL
    private var username: String = "nirwana_rfid"
    private var password: String = "YNCTD7Zw6yR2KePyq5mZXV8AMJGmwjOQ"
    private var authToken: String? = null
    private var tokenExpiryTime: Long = 0
    
    init {
        loadConfiguration()
    }
    
    /**
     * Load konfigurasi dari local.properties
     */
    private fun loadConfiguration() {
        try {
            val properties = Properties()
            val localPropertiesFile = java.io.File("local.properties")
            
            if (localPropertiesFile.exists()) {
                properties.load(FileInputStream(localPropertiesFile))
                
                baseUrl = properties.getProperty("NIRWANA_BASE_URL", DEFAULT_BASE_URL)
                username = properties.getProperty("NIRWANA_USERNAME", "nirwana_rfid")
                password = properties.getProperty("NIRWANA_PASSWORD", "YNCTD7Zw6yR2KePyq5mZXV8AMJGmwjOQ")
                
                v("Nirwana API configuration loaded. Base URL: $baseUrl, Username: $username")
            } else {
                Log.w(TAG, "⚠️ local.properties file not found, using default credentials")
                v("Using default configuration. Base URL: $baseUrl, Username: $username")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading Nirwana API configuration: ${e.message}", e)
            v("Using default configuration due to error")
        }
    }
    
    /**
     * Authenticate dengan API Nirwana dan dapatkan token
     */
    private suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        authMutex.withLock {
            if (isTokenValid()) return@withLock true
            var attempt = 0
            val authUrl = "$baseUrl$AUTH_ENDPOINT"
            while (attempt <= MAX_RETRY_ATTEMPTS) {
                try {
                    Log.d(TAG, "🔥 Authenticating with Nirwana API (attempt ${attempt + 1}/${MAX_RETRY_ATTEMPTS + 1})...")
                    val url = URL(authUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = CONNECT_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.doOutput = true
                    val jsonBody = """
                        {
                            "username": "$username",
                            "password": "$password"
                        }
                    """.trimIndent()
                    if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                        Log.i(TAG, "🔥 ===== NIRWANA API AUTH REQUEST START =====")
                        Log.i(TAG, "🔥 Request URL: $authUrl")
                        Log.i(TAG, "🔥 Request Method: POST")
                        Log.i(TAG, "🔥 Attempt: ${attempt + 1}/${MAX_RETRY_ATTEMPTS + 1}")
                        Log.i(TAG, "🔥 Request Headers: Content-Type=application/json")
                        Log.i(TAG, "🔥 Request Body:")
                        Log.i(TAG, jsonBody)
                        Log.i(TAG, "🔥 ===== NIRWANA API AUTH REQUEST END =====")
                    }
                    val writer = java.io.OutputStreamWriter(connection.outputStream)
                    writer.write(jsonBody)
                    writer.flush()
                    writer.close()
                    val responseCode = connection.responseCode
                    Log.d(TAG, "📦 Auth response code: $responseCode (attempt ${attempt + 1})")
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().readText()
                        if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                            Log.i(TAG, "🔥 ===== NIRWANA API AUTH RESPONSE START =====")
                            Log.i(TAG, "🔥 Request: Authentication")
                            Log.i(TAG, "🔥 Response Code: $responseCode")
                            Log.i(TAG, "🔥 Response Headers: ${connection.headerFields}")
                            Log.i(TAG, "🔥 Response Body:")
                            Log.i(TAG, response)
                            Log.i(TAG, "🔥 ===== NIRWANA API AUTH RESPONSE END =====")
                        }
                        val tokenRegex = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val tokenMatch = tokenRegex.find(response)
                        if (tokenMatch != null) {
                            authToken = tokenMatch.groupValues[1]
                            tokenExpiryTime = System.currentTimeMillis() + (3600 * 1000)
                            Log.d(TAG, "✅ Authentication successful")
                            Log.d(TAG, "🔥 Token expires in: 3600s")
                            return@withLock true
                        } else {
                            Log.e(TAG, "❌ Failed to parse access_token from response")
                            return@withLock false
                        }
                    } else {
                        val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
                        Log.e(TAG, "❌ Auth failed with code $responseCode: $errorResponse")
                        if (responseCode in 500..599) throw java.io.IOException("Server error $responseCode") else return@withLock false
                    }
                } catch (e: Exception) {
                    attempt++
                    if (attempt > MAX_RETRY_ATTEMPTS) {
                        Log.e(TAG, "❌ Error during authentication after ${attempt} attempts: ${e.message}", e)
                        return@withLock false
                    } else {
                        val backoff = BASE_BACKOFF_MS * (1L shl (attempt - 1))
                        Log.w(TAG, "⚠️ Retry auth attempt ${attempt}/${MAX_RETRY_ATTEMPTS} in ${backoff}ms due to: ${e.message}")
                        delay(backoff)
                    }
                }
            }
            return@withLock false
        }
    }
    
    /**
     * Cek apakah token masih valid
     */
    private fun isTokenValid(): Boolean {
        return authToken != null && System.currentTimeMillis() < tokenExpiryTime
    }
    
    /**
     * Dapatkan authorization header
     */
    private suspend fun getAuthHeader(): String? {
        if (!isTokenValid()) {
            if (!authenticate()) {
                return null
            }
        }
        return "Bearer $authToken"
    }
    
    /**
     * Lookup EPC untuk mendapatkan article ID menggunakan API Nirwana
     * @param epc EPC yang akan di-lookup
     * @return Article ID jika ditemukan, null jika tidak ditemukan
     */
    suspend fun lookupEpcToArticleId(epc: String): String? = withContext(Dispatchers.IO) {
        try {
            v("Looking up EPC: $epc")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to get authentication token")
                return@withContext null
            }
            
            val lookupUrl = "$baseUrl$TAG_PRODUCTS_ENDPOINT"
            
            val url = URL(lookupUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true
            
            val jsonBody = """
                {
                    "rfid_list": "$epc"
                }
            """.trimIndent()
            
            // **DETAILED LOGGING**: Log request body
            if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                Log.i(TAG, "🔥 ===== NIRWANA API REQUEST START =====")
                Log.i(TAG, "🔥 Request URL: $lookupUrl")
                Log.i(TAG, "🔥 Request Method: POST")
                Log.i(TAG, "🔥 Request Headers: Authorization=$authHeader")
                Log.i(TAG, "🔥 Request Body:")
                Log.i(TAG, jsonBody)
                Log.i(TAG, "🔥 ===== NIRWANA API REQUEST END =====")
            }
            
            val writer = java.io.OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📦 EPC lookup response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                
                // **DETAILED LOGGING**: Log response API Nirwana secara lengkap
                if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                    Log.i(TAG, "🔥 ===== NIRWANA API RESPONSE START =====")
                    Log.i(TAG, "🔥 Request: EPC lookup for '$epc'")
                    Log.i(TAG, "🔥 Response Code: $responseCode")
                    Log.i(TAG, "🔥 Response Headers: ${connection.headerFields}")
                    Log.i(TAG, "🔥 Response Body:")
                    Log.i(TAG, response)
                    Log.i(TAG, "🔥 ===== NIRWANA API RESPONSE END =====")
                }
                
                // Parse article ID dari response sesuai format API Nirwana
                // Expected response format:
                // {
                //   "length": 1,
                //   "data": [
                //     {
                //       "article_id": "HM.HMN-SJ001",
                //       "product_name": "T-SHIRT DARK GREY",
                //       ...
                //     }
                //   ]
                // }
                val articleIdRegex = "\"article_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val articleIdMatch = articleIdRegex.find(response)
                
                if (articleIdMatch != null) {
                    val articleId = articleIdMatch.groupValues[1]
                    
                    // **PERBAIKAN**: Biarkan item "NA" sampai ke UI untuk ditampilkan di RelocationActivity
                    if (articleId == "NA") {
                        Log.d(TAG, "ℹ️ NA response from Nirwana API for EPC: $epc - will be displayed in UI")
                    }
                    
                    // **DETAILED LOGGING**: Log parsed data
                    if (VERBOSE_LOGS) {
                        Log.i(TAG, "🔥 ===== PARSED DATA START =====")
                        Log.i(TAG, "🔥 EPC: $epc")
                        Log.i(TAG, "🔥 Article ID: $articleId")
                        Log.i(TAG, "🔥 ===== PARSED DATA END =====")
                    }
                    
                    Log.d(TAG, "✅ EPC $epc mapped to article ID: $articleId")
                    return@withContext articleId
                } else {
                    Log.w(TAG, "⚠️ No article_id found in response for EPC: $epc")
                    return@withContext null
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.w(TAG, "⚠️ EPC not found: $epc")
                return@withContext null
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
                
                // **DETAILED LOGGING**: Log error response API Nirwana
                Log.e(TAG, "🔥 ===== NIRWANA API ERROR RESPONSE START =====")
                Log.e(TAG, "🔥 Request: EPC lookup for '$epc'")
                Log.e(TAG, "🔥 Error Response Code: $responseCode")
                Log.e(TAG, "🔥 Error Response Headers: ${connection.headerFields}")
                Log.e(TAG, "🔥 Error Response Body:")
                Log.e(TAG, errorResponse)
                Log.e(TAG, "🔥 ===== NIRWANA API ERROR RESPONSE END =====")
                
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during EPC lookup: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Dapatkan informasi produk berdasarkan EPC menggunakan API Nirwana
     * @param epc EPC yang akan di-lookup
     * @return ProductInfo jika ditemukan, null jika tidak ditemukan
     */
    suspend fun getProductInfoByEpc(epc: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            v("Getting product info for EPC: $epc")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to get authentication token")
                return@withContext null
            }
            
            val lookupUrl = "$baseUrl$TAG_PRODUCTS_ENDPOINT"
            
            val url = URL(lookupUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true
            
            val jsonBody = """
                {
                    "rfid_list": "$epc"
                }
            """.trimIndent()
            
            // **DETAILED LOGGING**: Log request body
            if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                Log.i(TAG, "🔥 ===== NIRWANA API REQUEST START =====")
                Log.i(TAG, "🔥 Request URL: $lookupUrl")
                Log.i(TAG, "🔥 Request Method: POST")
                Log.i(TAG, "🔥 Request Headers: Authorization=$authHeader")
                Log.i(TAG, "🔥 Request Body:")
                Log.i(TAG, jsonBody)
                Log.i(TAG, "🔥 ===== NIRWANA API REQUEST END =====")
            }
            
            val writer = java.io.OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📦 Product info response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                
                // **DETAILED LOGGING**: Log response API Nirwana secara lengkap
                if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                    Log.i(TAG, "🔥 ===== NIRWANA API RESPONSE START =====")
                    Log.i(TAG, "🔥 Request: Product info lookup for EPC '$epc'")
                    Log.i(TAG, "🔥 Response Code: $responseCode")
                    Log.i(TAG, "🔥 Response Headers: ${connection.headerFields}")
                    Log.i(TAG, "🔥 Response Body:")
                    Log.i(TAG, response)
                    Log.i(TAG, "🔥 ===== NIRWANA API RESPONSE END =====")
                }
                
                // Parse product info dari response sesuai format API Nirwana
                val productIdRegex = "\"product_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val productNameRegex = "\"product_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val articleIdRegex = "\"article_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val articleNameRegex = "\"article_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val brandRegex = "\"brand\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val categoryRegex = "\"category\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val subCategoryRegex = "\"sub_category\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val colorRegex = "\"color\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val genderRegex = "\"gender\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val sizeRegex = "\"size\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val warehouseRegex = "\"warehouse\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val tagStatusRegex = "\"tag_status\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val qtyRegex = "\"qty\"\\s*:\\s*(\\d+)".toRegex()
                
                val productIdMatch = productIdRegex.find(response)
                val productNameMatch = productNameRegex.find(response)
                val articleIdMatch = articleIdRegex.find(response)
                val articleNameMatch = articleNameRegex.find(response)
                val brandMatch = brandRegex.find(response)
                val categoryMatch = categoryRegex.find(response)
                val subCategoryMatch = subCategoryRegex.find(response)
                val colorMatch = colorRegex.find(response)
                val genderMatch = genderRegex.find(response)
                val sizeMatch = sizeRegex.find(response)
                val warehouseMatch = warehouseRegex.find(response)
                val tagStatusMatch = tagStatusRegex.find(response)
                val qtyMatch = qtyRegex.find(response)
                
                if (articleIdMatch != null) {
                    val articleId = articleIdMatch.groupValues[1]
                    
                    // **PERBAIKAN**: Biarkan item "NA" sampai ke UI untuk ditampilkan di RelocationActivity
                    if (articleId == "NA") {
                        Log.d(TAG, "ℹ️ NA response from Nirwana API for EPC: $epc - will be displayed in UI")
                    }
                    
                    val productInfo = ProductInfo(
                        productId = productIdMatch?.groupValues?.get(1) ?: "",
                        productName = productNameMatch?.groupValues?.get(1) ?: "Unknown Product",
                        articleId = articleIdMatch.groupValues[1],
                        articleName = articleNameMatch?.groupValues?.get(1) ?: "",
                        brand = brandMatch?.groupValues?.get(1) ?: "",
                        category = categoryMatch?.groupValues?.get(1) ?: "",
                        subCategory = subCategoryMatch?.groupValues?.get(1) ?: "",
                        color = colorMatch?.groupValues?.get(1) ?: "",
                        gender = genderMatch?.groupValues?.get(1) ?: "",
                        size = sizeMatch?.groupValues?.get(1) ?: "",
                        warehouse = warehouseMatch?.groupValues?.get(1) ?: "",
                        tagStatus = tagStatusMatch?.groupValues?.get(1) ?: "",
                        qty = qtyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                        rfidList = listOf(epc) // **PERBAIKAN**: Untuk single lookup, gunakan EPC yang di-lookup
                    )
                    
                    // **DETAILED LOGGING**: Log parsed product info
                    if (VERBOSE_LOGS) {
                        Log.i(TAG, "🔥 ===== PARSED PRODUCT INFO START =====")
                        Log.i(TAG, "🔥 EPC: $epc")
                        Log.i(TAG, "🔥 Product ID: ${productInfo.productId}")
                        Log.i(TAG, "🔥 Product Name: ${productInfo.productName}")
                        Log.i(TAG, "🔥 Article ID: ${productInfo.articleId}")
                        Log.i(TAG, "🔥 Article Name: ${productInfo.articleName}")
                        Log.i(TAG, "🔥 Brand: ${productInfo.brand}")
                        Log.i(TAG, "🔥 Category: ${productInfo.category}")
                        Log.i(TAG, "🔥 Sub Category: ${productInfo.subCategory}")
                        Log.i(TAG, "🔥 Color: ${productInfo.color}")
                        Log.i(TAG, "🔥 Gender: ${productInfo.gender}")
                        Log.i(TAG, "🔥 Size: ${productInfo.size}")
                        Log.i(TAG, "🔥 Warehouse: ${productInfo.warehouse}")
                        Log.i(TAG, "🔥 Tag Status: ${productInfo.tagStatus}")
                        Log.i(TAG, "🔥 Quantity: ${productInfo.qty}")
                        Log.i(TAG, "🔥 ===== PARSED PRODUCT INFO END =====")
                    }
                    
                    Log.d(TAG, "✅ Product info retrieved: ${productInfo.productName} ${productInfo.size}")
                    return@withContext productInfo
                } else {
                    Log.w(TAG, "⚠️ No product info found for EPC: $epc")
                    return@withContext null
                }
                
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.w(TAG, "⚠️ Product not found for EPC: $epc")
                return@withContext null
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
                
                // **DETAILED LOGGING**: Log error response API Nirwana
                Log.e(TAG, "🔥 ===== NIRWANA API ERROR RESPONSE START =====")
                Log.e(TAG, "🔥 Request: Product info lookup for EPC '$epc'")
                Log.e(TAG, "🔥 Error Response Code: $responseCode")
                Log.e(TAG, "🔥 Error Response Headers: ${connection.headerFields}")
                Log.e(TAG, "🔥 Error Response Body:")
                Log.e(TAG, errorResponse)
                Log.e(TAG, "🔥 ===== NIRWANA API ERROR RESPONSE END =====")
                
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during product info lookup: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Batch lookup untuk multiple EPCs menggunakan API Nirwana dengan limit dan loop
     * @param rfidList List EPC yang akan di-lookup
     * @return List ProductInfo jika ditemukan
     */
    suspend fun batchLookupRfidList(rfidList: List<String>): List<ProductInfo> = withContext(Dispatchers.IO) {
        try {
            v("Batch lookup for ${rfidList.size} RFID tags")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to get authentication token")
                return@withContext emptyList()
            }
            
            val lookupUrl = "$baseUrl$TAG_PRODUCTS_ENDPOINT"
            val allProducts = mutableListOf<ProductInfo>()
            
            // **PERBAIKAN BARU**: Split EPC list menjadi chunks dengan limit 1000
            val chunkSize = 1000
            val epcChunks = rfidList.chunked(chunkSize)
            
            v("Processing ${epcChunks.size} chunks with limit $chunkSize")
            
            epcChunks.forEachIndexed { chunkIndex, epcChunk ->
                try {
                    v("Processing chunk ${chunkIndex + 1}/${epcChunks.size} with ${epcChunk.size} EPCs")
                    
                    // Format sebagai CSV string sesuai spesifikasi API Nirwana
                    val rfidListString = epcChunk.joinToString(",")
                    val jsonBody = """
                        {
                            "rfid_list": "$rfidListString",
                            "limit": "1000",
                            "page": "1"
                        }
                    """.trimIndent()

                    var attempt = 0
                    var lastError: Exception? = null
                    while (attempt <= MAX_RETRY_ATTEMPTS) {
                        try {
                            val url = URL(lookupUrl)
                            val connection = url.openConnection() as HttpURLConnection
                            
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Authorization", authHeader)
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.connectTimeout = CONNECT_TIMEOUT
                            connection.readTimeout = READ_TIMEOUT
                            connection.doOutput = true

                            // **DETAILED LOGGING**: Log request body
                            if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                                Log.i(TAG, "🔥 ===== NIRWANA API REQUEST START =====")
                                Log.i(TAG, "🔥 Request URL: $lookupUrl")
                                Log.i(TAG, "🔥 Request Method: POST")
                                Log.i(TAG, "🔥 Request Headers: Authorization=$authHeader")
                                Log.i(TAG, "🔥 Attempt: ${attempt + 1}/${MAX_RETRY_ATTEMPTS + 1}")
                                Log.i(TAG, "🔥 Request Body:")
                                Log.i(TAG, jsonBody)
                                Log.i(TAG, "🔥 ===== NIRWANA API REQUEST END =====")
                            }

                            val writer = java.io.OutputStreamWriter(connection.outputStream)
                            writer.write(jsonBody)
                            writer.flush()
                            writer.close()

                            val responseCode = connection.responseCode
                            Log.d(TAG, "📦 Chunk ${chunkIndex + 1} response code: $responseCode (attempt ${attempt + 1})")
                    
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().readText()
                        
                        // **DETAILED LOGGING**: Log response API Nirwana secara lengkap
                        if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                            Log.i(TAG, "🔥 ===== NIRWANA API RESPONSE START =====")
                            Log.i(TAG, "🔥 Request: Batch lookup for ${epcChunk.size} RFID tags (chunk ${chunkIndex + 1})")
                            Log.i(TAG, "🔥 RFID List: ${epcChunk.joinToString(", ")}")
                            Log.i(TAG, "🔥 Response Code: $responseCode")
                            Log.i(TAG, "🔥 Response Headers: ${connection.headerFields}")
                            Log.i(TAG, "🔥 Response Body Length: ${response.length}")
                            Log.i(TAG, "🔥 Response Body:")
                            Log.i(TAG, response)
                            Log.i(TAG, "🔥 ===== NIRWANA API RESPONSE END =====")
                        }
                        
                        // **PERBAIKAN BARU**: Gunakan Gson untuk parsing JSON yang lebih robust
                        v("Starting Gson JSON parsing for chunk ${chunkIndex + 1}... Response length: ${response.length}")
                        
                        val chunkProducts = mutableListOf<ProductInfo>()
                        
                        try {
                            val nirwanaResponse = gson.fromJson(response, NirwanaBatchResponse::class.java)
                            
                            v("GSON parsing success (Chunk ${chunkIndex + 1}), products: ${nirwanaResponse.data.size}")
                            
                            // **LOGGING**: Tampilkan semua EPC yang ditemukan dalam response
                            val allEpcsInResponse = nirwanaResponse.data.flatMap { it.rfidList }
                            v("All EPCs found in response: ${allEpcsInResponse.size}")

                            // === PER-CHUNK EPC COMPARISON LOGS ===
                            try {
                                val sentEpcs = epcChunk
                                val returnedEpcs = allEpcsInResponse
                                val missingEpcs = sentEpcs.filter { it !in returnedEpcs }

                                Log.d(TAG, "🔎 Chunk ${chunkIndex + 1} SENT EPCs (${sentEpcs.size}): ${sentEpcs.joinToString(",")}")
                                Log.d(TAG, "🔎 Chunk ${chunkIndex + 1} RETURNED EPCs (${returnedEpcs.size}): ${returnedEpcs.joinToString(",")}")
                                Log.d(TAG, "🔎 Chunk ${chunkIndex + 1} MISSING EPCs (${missingEpcs.size}): ${missingEpcs.joinToString(",")}")
                                Log.d(TAG, "🔎 Chunk ${chunkIndex + 1} CHECK: returned + missing = ${returnedEpcs.size + missingEpcs.size} (should equal sent ${sentEpcs.size})")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Failed to compute per-chunk EPC comparison for chunk ${chunkIndex + 1}: ${e.message}")
                            }
                            
                            // Convert NirwanaProductData ke ProductInfo dengan EPC mapping yang benar
                            nirwanaResponse.data.forEachIndexed { index, productData ->
                                val productInfo = ProductInfo(
                                    productId = productData.productId,
                                    productName = productData.productName,
                                    articleId = productData.articleId,
                                    articleName = productData.articleName,
                                    brand = productData.brand,
                                    category = productData.category,
                                    subCategory = productData.subCategory,
                                    color = productData.color,
                                    gender = productData.gender,
                                    size = productData.size,
                                    warehouse = productData.warehouse,
                                    tagStatus = productData.tagStatus,
                                    qty = productData.qty,
                                    rfidList = productData.rfidList // **PERBAIKAN**: Tambahkan rfidList untuk mapping yang akurat
                                )
                                
                                chunkProducts.add(productInfo)
                                v("Product #${index + 1} parsed: ${productInfo.articleName} ${productInfo.size}, rfids: ${productData.rfidList}")
                            }
                            
                            v("GSON parsing complete (Chunk ${chunkIndex + 1}), total products: ${chunkProducts.size}")
                            
                        } catch (e: JsonSyntaxException) {
                            Log.e(TAG, "❌ Gson parsing failed for chunk ${chunkIndex + 1}: ${e.message}", e)
                            Log.w(TAG, "⚠️ Falling back to manual parsing for chunk ${chunkIndex + 1}...")
                            
                            // Fallback ke manual parsing jika Gson gagal
                            parseResponseManually(response, chunkProducts)
                        }
                        
                        // Tambahkan produk dari chunk ini ke total
                        allProducts.addAll(chunkProducts)
                        v("Chunk ${chunkIndex + 1} completed: ${chunkProducts.size} products found")
                        break // sukses, keluar dari retry loop
                        
                            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                                Log.w(TAG, "⚠️ No products found for chunk ${chunkIndex + 1}")
                                break
                            } else {
                                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
                                Log.e(TAG, "❌ Chunk ${chunkIndex + 1} failed with code $responseCode: $errorResponse")
                                // Untuk 5xx atau connection issue, kita retry; untuk 4xx selain 404, jangan retry
                                if (responseCode in 500..599) {
                                    throw java.io.IOException("Server error $responseCode")
                                } else {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            lastError = e
                            attempt++
                            if (attempt > MAX_RETRY_ATTEMPTS) {
                                Log.e(TAG, "❌ Chunk ${chunkIndex + 1} failed after ${attempt} attempts: ${e.message}")
                                break
                            } else {
                                val backoff = BASE_BACKOFF_MS * (1L shl (attempt - 1))
                                Log.w(TAG, "⚠️ Retry chunk ${chunkIndex + 1} attempt ${attempt}/${MAX_RETRY_ATTEMPTS} in ${backoff}ms due to: ${e.message}")
                                delay(backoff)
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing chunk ${chunkIndex + 1}: ${e.message}", e)
                    // **PERBAIKAN**: Lanjutkan ke chunk berikutnya meskipun ada error
                    Log.w(TAG, "⚠️ Continuing to next chunk despite error in chunk ${chunkIndex + 1}")
                }
            }
            
            v("Batch lookup completed: ${allProducts.size} products found from ${epcChunks.size} chunks")
            return@withContext allProducts
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during batch lookup: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Manual reconstruction untuk response yang terpotong
     */
    private fun manualReconstructTruncatedResponse(response: String): String? {
        try {
            v("Attempting manual reconstruction...")
            
            // **PERBAIKAN**: Cari pola rfid_list yang terpotong dengan regex yang lebih spesifik
            // Pattern: "rfid_list":["20250E000000000000013491" (tanpa closing bracket)
            val rfidListPattern = "\"rfid_list\"\\s*:\\s*\\[\"([0-9A-F]+)\"?(?:\"|\\]|$)".toRegex()
            val rfidMatch = rfidListPattern.find(response)
            
            if (rfidMatch != null) {
                val rfidValue = rfidMatch.groupValues[1].trim()
                v("Found truncated RFID value: '$rfidValue'")
                
                // **PERBAIKAN**: Validasi RFID value sebelum reconstruct
                if (rfidValue.isNotEmpty() && rfidValue.matches("[0-9A-F]+".toRegex())) {
                    // Reconstruct dengan menambahkan closing bracket dan quote yang hilang
                    val reconstructed = response.replace(
                        "\"rfid_list\"\\s*:\\s*\\[\"([0-9A-F]+)\"?(?:\"|\\]|$)".toRegex(),
                        "\"rfid_list\":[\"$rfidValue\"]"
                    )
                    
                    // Pastikan response diakhiri dengan bracket yang benar
                    val finalReconstructed = if (!reconstructed.trim().endsWith("}")) {
                        reconstructed.trimEnd() + "}]}"
                    } else {
                        reconstructed
                    }
                    
                    v("Manual reconstruction result produced")
                    return finalReconstructed
                } else {
                    Log.w(TAG, "⚠️ Invalid RFID value in manual reconstruction: '$rfidValue'")
                    return null
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in manual reconstruction: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Fallback function untuk manual parsing jika Gson gagal
     */
    private fun parseResponseManually(response: String, products: MutableList<ProductInfo>) {
        Log.d(TAG, "🔍 Starting manual parsing fallback...")
        Log.d(TAG, "🔍 Response structure check:")
        Log.d(TAG, "🔍 - Contains 'data': ${response.contains("\"data\"")}")
        Log.d(TAG, "🔍 - Contains 'success': ${response.contains("\"success\"")}")
        
        try {
            // Coba extract data array dengan substring approach
            val dataArrayStart = response.indexOf("\"data\":[")
            val dataArrayEnd = response.lastIndexOf("]}")
            
            Log.d(TAG, "🔍 Data array positions: start=$dataArrayStart, end=$dataArrayEnd")
            
            if (dataArrayStart != -1 && dataArrayEnd != -1 && dataArrayEnd > dataArrayStart) {
                val dataArrayContent = response.substring(dataArrayStart + 8, dataArrayEnd + 1)
                Log.d(TAG, "🔍 Extracted data array content for manual parsing")
                Log.d(TAG, "🔍 Data array content length: ${dataArrayContent.length}")
                Log.d(TAG, "🔍 Data array preview: ${dataArrayContent.take(200)}")
                
                // Gunakan fungsi parsing yang sudah ada
                parseTruncatedDataArrayContent(dataArrayContent, products)
                Log.d(TAG, "🔍 Manual parsing completed: ${products.size} products found")
            } else {
                Log.e(TAG, "❌ Could not extract data array for manual parsing")
                Log.d(TAG, "🔍 Full response for debugging: $response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Manual parsing failed: ${e.message}", e)
        }
    }
    
    /**
     * Helper function untuk parse data array content yang terpotong
     * Parsing semua produk yang tersedia meskipun response tidak lengkap
     */
    private fun parseTruncatedDataArrayContent(dataArrayContent: String, products: MutableList<ProductInfo>) {
        v("Parsing truncated data array content...")
        
        // Split berdasarkan pattern "},{" untuk mendapatkan setiap produk
        val productPattern = "\\},\\{".toRegex()
        val productStrings = dataArrayContent.split(productPattern)
        
        v("Found ${productStrings.size} potential product strings")
        
        productStrings.forEachIndexed { index, productString ->
            try {
                // Bersihkan string dan tambahkan bracket jika perlu
                var cleanProductString = productString.trim()
                
                // Tambahkan bracket pembuka jika tidak ada
                if (!cleanProductString.startsWith("{")) {
                    cleanProductString = "{$cleanProductString"
                }
                
                // Tambahkan bracket penutup jika tidak ada
                if (!cleanProductString.endsWith("}")) {
                    cleanProductString = "$cleanProductString}"
                }
                
                v("Processing truncated product #${index + 1}")
                
                // Parse produk individual
                parseSingleProduct(cleanProductString, products, "")
                
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error parsing truncated product #${index + 1}: ${e.message}")
            }
        }
        
        v("Truncated parsing complete. Total products parsed: ${products.size}")
    }
    
    /**
     * Helper function untuk parse single product dari JSON string
     */
    private fun parseSingleProduct(productJson: String, products: MutableList<ProductInfo>, epc: String = "") {
        try {
            val productIdRegex = "\"product_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val productNameRegex = "\"product_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val articleIdRegex = "\"article_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val articleNameRegex = "\"article_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val brandRegex = "\"brand\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val categoryRegex = "\"category\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val subCategoryRegex = "\"sub_category\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val colorRegex = "\"color\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val genderRegex = "\"gender\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val sizeRegex = "\"size\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val warehouseRegex = "\"warehouse\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val tagStatusRegex = "\"tag_status\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val qtyRegex = "\"qty\"\\s*:\\s*(\\d+)".toRegex()
            
            val productIdMatch = productIdRegex.find(productJson)
            val productNameMatch = productNameRegex.find(productJson)
            val articleIdMatch = articleIdRegex.find(productJson)
            val articleNameMatch = articleNameRegex.find(productJson)
            val brandMatch = brandRegex.find(productJson)
            val categoryMatch = categoryRegex.find(productJson)
            val subCategoryMatch = subCategoryRegex.find(productJson)
            val colorMatch = colorRegex.find(productJson)
            val genderMatch = genderRegex.find(productJson)
            val sizeMatch = sizeRegex.find(productJson)
            val warehouseMatch = warehouseRegex.find(productJson)
            val tagStatusMatch = tagStatusRegex.find(productJson)
            val qtyMatch = qtyRegex.find(productJson)
            
            if (articleIdMatch != null) {
                val articleId = articleIdMatch.groupValues[1]
                
                // **PERBAIKAN**: Biarkan item "NA" sampai ke UI untuk ditampilkan di RelocationActivity
                if (articleId == "NA") {
                    Log.d(TAG, "ℹ️ NA response from Nirwana API - will be displayed in UI")
                }
                
                val productInfo = ProductInfo(
                    productId = productIdMatch?.groupValues?.get(1) ?: "",
                    productName = productNameMatch?.groupValues?.get(1) ?: "Unknown Product",
                    articleId = articleIdMatch.groupValues[1],
                    articleName = articleNameMatch?.groupValues?.get(1) ?: "",
                    brand = brandMatch?.groupValues?.get(1) ?: "",
                    category = categoryMatch?.groupValues?.get(1) ?: "",
                    subCategory = subCategoryMatch?.groupValues?.get(1) ?: "",
                    color = colorMatch?.groupValues?.get(1) ?: "",
                    gender = genderMatch?.groupValues?.get(1) ?: "",
                    size = sizeMatch?.groupValues?.get(1) ?: "",
                    warehouse = warehouseMatch?.groupValues?.get(1) ?: "",
                    tagStatus = tagStatusMatch?.groupValues?.get(1) ?: "",
                    qty = qtyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                    rfidList = listOf(epc) // **PERBAIKAN**: Untuk single lookup, gunakan EPC yang di-lookup
                )
                
                products.add(productInfo)
                v("Product #${products.size} added: ${productInfo.articleName} ${productInfo.size}")
            } else {
                Log.w(TAG, "⚠️ No article_id found in product JSON: $productJson")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing single product: ${e.message}", e)
        }
    }
    
    /**
     * Helper function untuk parse data array content
     */
    private fun parseDataArrayContent(dataArrayContent: String, products: MutableList<ProductInfo>) {
        v("Parsing data array content (length=${dataArrayContent.length})")
        
        // Manual parsing untuk object yang lebih robust
        val objects = mutableListOf<String>()
        var braceCount = 0
        var currentObject = StringBuilder()
        var inString = false
        var escapeNext = false
        
        for (char in dataArrayContent) {
            when {
                escapeNext -> {
                    currentObject.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    currentObject.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    currentObject.append(char)
                    inString = !inString
                }
                !inString && char == '{' -> {
                    currentObject.append(char)
                    braceCount++
                }
                !inString && char == '}' -> {
                    currentObject.append(char)
                    braceCount--
                    if (braceCount == 0) {
                        objects.add(currentObject.toString())
                        currentObject = StringBuilder()
                    }
                }
                else -> {
                    currentObject.append(char)
                }
            }
        }
        
        v("Found ${objects.size} objects in data array")
        
        // **PERBAIKAN**: Gunakan fungsi parseSingleProduct untuk konsistensi
        objects.forEachIndexed { index, objContent ->
            v("Parsing object #${index + 1}")
            parseSingleProduct(objContent, products, "")
        }
        
        v("Parsing complete. Objects=${objects.size}, Products=${products.size}")
    }
    
    /**
     * Test koneksi ke API Nirwana
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            v("Testing Nirwana API connection...")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to authenticate for connection test")
                return@withContext false
            }
            
            val testUrl = "$baseUrl/api/v1/health"
            val url = URL(testUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            val responseCode = connection.responseCode
            v("Connection test response code: $responseCode")
            
            val isConnected = responseCode == HttpURLConnection.HTTP_OK
            if (!isConnected) {
                Log.e(TAG, "❌ Nirwana API connection test failed")
            }
            
            return@withContext isConnected
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during connection test: ${e.message}", e)
            return@withContext false
        }
    }
    
    // === RELOCATION API ENDPOINTS ===
    
    /**
     * Lookup RFID untuk relocation menggunakan endpoint /tag/products
     * POST /tag/products dengan parameter warehouse_id dan tag_status
     */
    suspend fun lookupRelocationRfid(rfidList: String, warehouseId: String, tagStatus: String): List<ProductInfo> = withContext(Dispatchers.IO) {
        try {
            v("Relocation lookup for RFID list: $rfidList")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to get authentication token")
                return@withContext emptyList()
            }
            
            val lookupUrl = "$baseUrl$TAG_PRODUCTS_ENDPOINT"
            val allProducts = mutableListOf<ProductInfo>()
            
            // **PERBAIKAN**: Gunakan chunking untuk mengatasi batasan API
            val chunkSize = 100
            val epcArray = rfidList.split(",")
            val epcList = mutableListOf<String>()
            for (epc in epcArray) {
                val trimmedEpc = epc.trim()
                if (trimmedEpc.isNotEmpty()) {
                    epcList.add(trimmedEpc)
                }
            }
            val epcChunks = epcList.chunked(chunkSize)
            
            v("Processing ${epcChunks.size} chunks with limit $chunkSize from ${epcList.size} total EPCs")
            
            epcChunks.forEachIndexed { chunkIndex, epcChunk ->
                try {
                    v("Processing chunk ${chunkIndex + 1}/${epcChunks.size} with ${epcChunk.size} EPCs")
                    
                    // Format sebagai CSV string sesuai spesifikasi API Nirwana
                    val rfidListString = epcChunk.joinToString(",")
                    val jsonBody = """
                        {
                            "rfid_list": "$rfidListString",
                            "warehouse_id": "$warehouseId",
                            "tag_status": "$tagStatus",
                            "limit": "100",
                            "page": "1"
                        }
                    """.trimIndent()

                    var attempt = 0
                    var lastError: Exception? = null
                    var connection: HttpURLConnection? = null
                    while (attempt <= MAX_RETRY_ATTEMPTS) {
                        try {
                            val url = URL(lookupUrl)
                            connection = url.openConnection() as HttpURLConnection
                            
                            connection.requestMethod = "POST"
                            connection.setRequestProperty("Authorization", authHeader)
                            connection.setRequestProperty("Accept", "application/json")
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.connectTimeout = CONNECT_TIMEOUT
                            connection.readTimeout = READ_TIMEOUT
                            connection.doOutput = true
                            
                            // **DETAILED LOGGING**: Log request body
                            if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                                Log.i(TAG, "🔥 ===== RELOCATION API REQUEST START =====")
                                Log.i(TAG, "🔥 Request URL: $lookupUrl")
                                Log.i(TAG, "🔥 Request Method: POST")
                                Log.i(TAG, "🔥 Request Headers: Authorization=$authHeader")
                                Log.i(TAG, "🔥 Request Body:")
                                Log.i(TAG, jsonBody)
                                Log.i(TAG, "🔥 ===== RELOCATION API REQUEST END =====")
                            }
                            
                            val writer = java.io.OutputStreamWriter(connection.outputStream)
                            writer.write(jsonBody)
                            writer.flush()
                            writer.close()
                            
                            val responseCode = connection.responseCode
                            Log.d(TAG, "📦 Relocation lookup response code: $responseCode")
                            
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                                
                                // **ALWAYS LOG**: Log response body untuk relocation lookup (penting untuk debugging)
                                Log.i(TAG, "🔥 ===== RELOCATION API RESPONSE START =====")
                                Log.i(TAG, "🔥 Response Code: $responseCode")
                                Log.i(TAG, "🔥 Response Body:")
                                Log.i(TAG, responseBody)
                                Log.i(TAG, "🔥 ===== RELOCATION API RESPONSE END =====")
                                
                                val chunkProducts = mutableListOf<ProductInfo>()
                                
                                // **DETAILED LOGGING**: Log parsing process
                                Log.d(TAG, "🔍 Starting to parse relocation response with Gson...")
                                Log.d(TAG, "🔍 Response length: ${responseBody.length} characters")
                                
                                try {
                                    // **PERBAIKAN**: Gunakan Gson untuk parsing JSON yang lebih robust
                                    Log.d(TAG, "🔍 Starting Gson JSON parsing for relocation...")
                                    
                                    val nirwanaResponse = gson.fromJson(responseBody, NirwanaBatchResponse::class.java)
                                    
                                    Log.d(TAG, "🔍 GSON parsing success, products: ${nirwanaResponse.data.size}")
                                    
                                    // **LOGGING**: Tampilkan semua EPC yang ditemukan dalam response
                                    val allEpcsInResponse = nirwanaResponse.data.flatMap { it.rfidList }
                                    Log.d(TAG, "🔍 All EPCs found in response: ${allEpcsInResponse.size}")

                                    // === PER-CHUNK EPC COMPARISON LOGS (Relocation) ===
                                    try {
                                        val sentEpcs = epcChunk
                                        val returnedEpcs = allEpcsInResponse
                                        val missingEpcs = sentEpcs.filter { it !in returnedEpcs }

                                        Log.d(TAG, "🔎 [Relocation] Chunk ${chunkIndex + 1} SENT EPCs (${sentEpcs.size}): ${sentEpcs.joinToString(",")}")
                                        Log.d(TAG, "🔎 [Relocation] Chunk ${chunkIndex + 1} RETURNED EPCs (${returnedEpcs.size}): ${returnedEpcs.joinToString(",")}")
                                        Log.d(TAG, "🔎 [Relocation] Chunk ${chunkIndex + 1} MISSING EPCs (${missingEpcs.size}): ${missingEpcs.joinToString(",")}")
                                        Log.d(TAG, "🔎 [Relocation] Chunk ${chunkIndex + 1} CHECK: returned + missing = ${returnedEpcs.size + missingEpcs.size} (should equal sent ${sentEpcs.size})")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "⚠️ Failed to compute per-chunk EPC comparison (Relocation) for chunk ${chunkIndex + 1}: ${e.message}")
                                    }
                                    
                                    // Convert NirwanaProductData ke ProductInfo dengan EPC mapping yang benar
                                    nirwanaResponse.data.forEachIndexed { index, productData ->
                                        val productInfo = ProductInfo(
                                            rfidList = productData.rfidList,
                                            articleId = productData.articleId,
                                            articleName = productData.articleName,
                                            size = productData.size,
                                            qty = productData.qty,
                                            productId = productData.productId,
                                            productName = productData.productName,
                                            brand = productData.brand,
                                            category = productData.category,
                                            subCategory = productData.subCategory,
                                            color = productData.color,
                                            gender = productData.gender,
                                            warehouse = productData.warehouse,
                                            tagStatus = productData.tagStatus
                                        )
                                        
                                        chunkProducts.add(productInfo)
                                        // Log lengkap data yang dikembalikan API per item
                                        Log.d(TAG, "📦 [Relocation] Product[$index]: " +
                                                "productId='${productData.productId}', " +
                                                "productName='${productData.productName}', " +
                                                "articleId='${productData.articleId}', " +
                                                "articleName='${productData.articleName}', " +
                                                "brand='${productData.brand}', " +
                                                "category='${productData.category}', " +
                                                "subCategory='${productData.subCategory}', " +
                                                "color='${productData.color}', " +
                                                "gender='${productData.gender}', " +
                                                "size='${productData.size}', " +
                                                "warehouse='${productData.warehouse}', " +
                                                "tagStatus='${productData.tagStatus}', " +
                                                "qty=${productData.qty}, " +
                                                "rfidList=[${productData.rfidList.joinToString(",")}]")
                                    }
                                    
                                    Log.d(TAG, "🔍 GSON parsing complete, total products: ${chunkProducts.size}")
                                    
                                } catch (e: JsonSyntaxException) {
                                    Log.e(TAG, "❌ Gson parsing failed for relocation: ${e.message}", e)
                                    Log.w(TAG, "⚠️ Falling back to manual parsing for relocation...")
                                    
                                    // Fallback ke manual parsing jika Gson gagal
                                    parseResponseManually(responseBody, chunkProducts)
                                }
                                
                                Log.d(TAG, "🔍 Parsing completed: ${chunkProducts.size} products found")
                                if (chunkProducts.isEmpty()) {
                                    Log.w(TAG, "⚠️ No products found in response - checking response structure...")
                                    Log.d(TAG, "🔍 Response preview (first 500 chars): ${responseBody.take(500)}")
                                }
                                
                                // **PERBAIKAN**: Tambahkan produk dari chunk ini ke total
                                allProducts.addAll(chunkProducts)
                                Log.d(TAG, "✅ Chunk ${chunkIndex + 1} completed: ${chunkProducts.size} products found")
                                break // sukses, keluar dari retry loop
                                
                            } else {
                                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                                Log.e(TAG, "❌ Relocation lookup failed with code $responseCode: $errorBody")
                                lastError = Exception("HTTP $responseCode: $errorBody")
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error processing chunk ${chunkIndex + 1}: ${e.message}", e)
                            lastError = e
                        } finally {
                            try {
                                connection?.disconnect()
                            } catch (e: Exception) {
                                // Ignore disconnect errors
                            }
                        }
                        
                        attempt++
                        if (attempt <= MAX_RETRY_ATTEMPTS) {
                            Log.w(TAG, "⚠️ Retrying chunk ${chunkIndex + 1} (attempt $attempt/${MAX_RETRY_ATTEMPTS + 1})")
                            delay(1000) // 1 second delay
                        }
                    }
                    
                    if (lastError != null) {
                        Log.e(TAG, "❌ Failed to process chunk ${chunkIndex + 1} after ${MAX_RETRY_ATTEMPTS + 1} attempts: ${lastError?.message}")
                        Log.w(TAG, "⚠️ Continuing to next chunk despite error in chunk ${chunkIndex + 1}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing chunk ${chunkIndex + 1}: ${e.message}", e)
                    Log.w(TAG, "⚠️ Continuing to next chunk despite error in chunk ${chunkIndex + 1}")
                }
            }
            
            Log.d(TAG, "✅ Batch lookup completed: ${allProducts.size} products found from ${epcChunks.size} chunks")
            return@withContext allProducts
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during relocation lookup: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Update tag status untuk relocation
     * PUT /tag/flag/update
     */
    suspend fun updateRelocationTagStatus(request: com.example.cekpicklist.data.RelocationUpdateRequest): com.example.cekpicklist.data.RelocationUpdateResponse = withContext(Dispatchers.IO) {
        try {
            v("Updating relocation tag status: $request")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to get authentication token")
                return@withContext com.example.cekpicklist.data.RelocationUpdateResponse(false, "Failed to get auth token")
            }
            
            val updateUrl = "$baseUrl/tag/flag/update"
            
            val url = URL(updateUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true
            
            val jsonBody = """
                {
                    "warehouse_id": "${request.warehouse_id}",
                    "tag_status": "${request.tag_status}",
                    "rfid_list": [${request.rfid_list.joinToString(",") { "\"$it\"" }}]
                }
            """.trimIndent()
            
            // **DETAILED LOGGING**: Log request body
            if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                Log.i(TAG, "🔥 ===== RELOCATION UPDATE API REQUEST START =====")
                Log.i(TAG, "🔥 Request URL: $updateUrl")
                Log.i(TAG, "🔥 Request Method: PUT")
                Log.i(TAG, "🔥 Request Headers: Authorization=$authHeader")
                Log.i(TAG, "🔥 Request Body:")
                Log.i(TAG, jsonBody)
                Log.i(TAG, "🔥 ===== RELOCATION UPDATE API REQUEST END =====")
            }
            
            val writer = java.io.OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📦 Relocation update response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                
                // **DETAILED LOGGING**: Log response body
                if (VERBOSE_LOGS && LOG_HTTP_BODIES) {
                    Log.i(TAG, "🔥 ===== RELOCATION UPDATE API RESPONSE START =====")
                    Log.i(TAG, "🔥 Response Code: $responseCode")
                    Log.i(TAG, "🔥 Response Body:")
                    Log.i(TAG, responseBody)
                    Log.i(TAG, "🔥 ===== RELOCATION UPDATE API RESPONSE END =====")
                }
                
                // Parse response
                val jsonObject = org.json.JSONObject(responseBody)
                val message = jsonObject.optString("message", "Update successful")
                val status = jsonObject.optString("status", "success")
                
                Log.d(TAG, "✅ Relocation update successful: $message")
                return@withContext com.example.cekpicklist.data.RelocationUpdateResponse(true, message)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                Log.e(TAG, "❌ Relocation update failed with code $responseCode: $errorBody")
                return@withContext com.example.cekpicklist.data.RelocationUpdateResponse(false, "Update failed: $errorBody")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during relocation update: ${e.message}", e)
                return@withContext com.example.cekpicklist.data.RelocationUpdateResponse(false, "Error: ${e.message}")
        }
    }
    
    /**
     * Get list warehouses dari master data
     * GET /master/warehouses
     */
    suspend fun getWarehouses(): List<com.example.cekpicklist.data.Warehouse> = withContext(Dispatchers.IO) {
        try {
            v("Getting warehouses list")
            
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                Log.e(TAG, "❌ Failed to get authentication token")
                return@withContext emptyList()
            }
            
            val warehousesUrl = "$baseUrl/master/warehouses?limit=1000&page=1"
            
            val url = URL(warehousesUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            // **ALWAYS LOG**: Log request untuk warehouses (penting untuk debugging)
            Log.i(TAG, "🔥 ===== WAREHOUSES API REQUEST START =====")
            Log.i(TAG, "🔥 Request URL: $warehousesUrl")
            Log.i(TAG, "🔥 Request Method: GET")
            Log.i(TAG, "🔥 Request Headers: Authorization=$authHeader")
            Log.i(TAG, "🔥 ===== WAREHOUSES API REQUEST END =====")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📦 Warehouses response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                
                // **ALWAYS LOG**: Log response body untuk warehouses (penting untuk debugging)
                Log.i(TAG, "🔥 ===== WAREHOUSES API RESPONSE START =====")
                Log.i(TAG, "🔥 Response Code: $responseCode")
                Log.i(TAG, "🔥 Response Body Length: ${responseBody.length}")
                Log.i(TAG, "🔥 Response Body:")
                Log.i(TAG, responseBody)
                Log.i(TAG, "🔥 ===== WAREHOUSES API RESPONSE END =====")
                
                val warehouses = parseWarehousesFromResponse(responseBody)
                Log.d(TAG, "✅ Warehouses loaded successfully: ${warehouses.size} warehouses found")
                
                // **DETAILED LOGGING**: Log setiap warehouse yang berhasil di-parse
                warehouses.forEachIndexed { index, warehouse ->
                    Log.d(TAG, "📦 Warehouse[$index]: ID=${warehouse.warehouseId}, Name=${warehouse.warehouseName}, Active=${warehouse.isActive}")
                }
                
                return@withContext warehouses
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                Log.e(TAG, "❌ ===== WAREHOUSES API ERROR RESPONSE =====")
                Log.e(TAG, "❌ Response Code: $responseCode")
                Log.e(TAG, "❌ Error Body: $errorBody")
                Log.e(TAG, "❌ ===== WAREHOUSES API ERROR END =====")
                return@withContext emptyList()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting warehouses: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Parse warehouses dari response JSON
     */
    private fun parseWarehousesFromResponse(responseBody: String): List<com.example.cekpicklist.data.Warehouse> {
        return try {
            Log.d(TAG, "🔍 Starting to parse warehouses response...")
            val warehouses = mutableListOf<com.example.cekpicklist.data.Warehouse>()
            
            // Parse JSON response untuk warehouses
            val jsonObject = org.json.JSONObject(responseBody)
            Log.d(TAG, "🔍 JSON Object keys: ${jsonObject.keys().asSequence().toList()}")
            
            if (jsonObject.has("data") && !jsonObject.isNull("data")) {
                val dataArray = jsonObject.getJSONArray("data")
                Log.d(TAG, "🔍 Found data array with ${dataArray.length()} items")
                
                for (i in 0 until dataArray.length()) {
                    val warehouseObj = dataArray.getJSONObject(i)
                    Log.d(TAG, "🔍 Parsing warehouse[$i]: ${warehouseObj.keys().asSequence().toList()}")
                    
                    val warehouse = com.example.cekpicklist.data.Warehouse(
                        warehouseId = warehouseObj.optString("warehouse_id", ""),
                        warehouseName = warehouseObj.optString("warehouse_name", ""),
                        address = warehouseObj.optString("address", null),
                        city = warehouseObj.optString("city", null),
                        country = warehouseObj.optString("country", null),
                        isActive = warehouseObj.optBoolean("is_active", true)
                    )
                    
                    Log.d(TAG, "🔍 Created warehouse: ID=${warehouse.warehouseId}, Name=${warehouse.warehouseName}, Active=${warehouse.isActive}")
                    warehouses.add(warehouse)
                }
            } else {
                Log.w(TAG, "⚠️ No 'data' field found in response or data is null")
            }
            
            Log.d(TAG, "📦 Parsed ${warehouses.size} warehouses from response")
            warehouses
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parsing warehouses response: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Data class untuk menyimpan informasi produk sesuai format API Nirwana
     */
    data class ProductInfo(
        val productId: String,
        val productName: String,
        val articleId: String,
        val articleName: String,
        val brand: String,
        val category: String,
        val subCategory: String,
        val color: String,
        val gender: String,
        val size: String,
        val warehouse: String,
        val tagStatus: String,
        val qty: Int,
        val rfidList: List<String> = emptyList() // **PERBAIKAN**: Tambahkan rfidList untuk mapping yang akurat
    )
}

// Helper for gated verbose logs
private fun v(message: String) {
    // Mirror the flag defined in companion (copied since top-level helper)
    val verbose = false
    if (verbose) Log.d("NirwanaApiService", message)
}