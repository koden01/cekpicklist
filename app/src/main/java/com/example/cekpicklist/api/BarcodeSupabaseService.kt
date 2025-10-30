package com.example.cekpicklist.api

import android.util.Log
import com.example.cekpicklist.config.BarcodeSupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Barcode Supabase Service
 * Menggunakan struktur database yang konsisten dengan azure-chinchilla-swoop
 * Tabel: tbl_resi, tbl_expedisi
 */
class BarcodeSupabaseService {
    
    companion object {
        private const val TAG = "BarcodeSupabaseService"
    }
    
    // Supabase configuration dari BarcodeSupabaseConfig (database terpisah)
    private val supabaseUrl = BarcodeSupabaseConfig.SUPABASE_URL
    private val supabaseKey = BarcodeSupabaseConfig.SUPABASE_ANON_KEY
    
    /**
     * Data class untuk barcode scan record
     * Hanya menggunakan kolom yang ada di tbl_resi
     */
    data class BarcodeScanRecord(
        val id: String? = null,
        val Resi: String, // Barcode value (Primary Key)
        val created: String? = null, // Created timestamp
        val Keterangan: String? = null, // Description/Scanner type
        val nokarung: String? = null, // Karung number
        val schedule: String? = null // Schedule status: 'ontime', 'late', 'batal'
    )
    
    /**
     * Data class untuk barcode session record
     * Hanya menggunakan kolom yang ada di tbl_expedisi
     */
    data class BarcodeSessionRecord(
        val id: String? = null,
        val orderno: String, // Order number (Primary Key)
        val datetrans: String, // Transaction date (NOT NULL)
        val chanelsales: String? = null, // Channel sales
        val couriername: String? = null, // Courier/Scanner name
        val resino: String, // Alternative barcode identifier (NOT NULL)
        val created: String? = null, // Created timestamp
        val flag: String = "NO", // Flag status
        val cekfu: Boolean = false // Check follow up
    )
    
    
    /**
     * Update flag di tbl_expedisi berdasarkan resino
     */
    suspend fun updateExpedisiFlag(resino: String, flag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Updating flag in Supabase tbl_expedisi: resino=$resino, flag=$flag")
            
            val jsonObject = JSONObject().apply {
                put("flag", flag)
            }
            
            val url = URL("$supabaseUrl/rest/v1/tbl_expedisi?resino=eq.$resino")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "🔄 Update flag response code: $responseCode")
            
            val success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT
            Log.d(TAG, "🔄 Update flag result: $success")
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating flag in Supabase: ${e.message}", e)
            false
        }
    }
    
    /**
     * Simpan barcode scan ke database
     */
    suspend fun saveBarcodeScan(record: BarcodeScanRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Saving barcode scan: ${record.Resi}")
            
            val jsonObject = JSONObject().apply {
                put("Resi", record.Resi)
                record.created?.let { put("created", it) }
                record.Keterangan?.let { put("Keterangan", it) }
                record.nokarung?.let { put("nokarung", it) }
                record.schedule?.let { put("schedule", it) }
            }
            
            val url = URL("$supabaseUrl/rest/v1/tbl_resi")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "💾 Save barcode scan response code: $responseCode")
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ Barcode scan saved successfully")
                true
            } else {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else "No error details"
                Log.e(TAG, "❌ Failed to save barcode scan: $errorResponse")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving barcode scan: ${e.message}", e)
            false
        }
    }
    
    /**
     * Simpan barcode session ke database
     */
    suspend fun saveBarcodeSession(record: BarcodeSessionRecord): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Saving barcode session: ${record.orderno}")
            
            val jsonObject = JSONObject().apply {
                put("orderno", record.orderno)
                put("datetrans", record.datetrans)
                record.chanelsales?.let { put("chanelsales", it) }
                record.couriername?.let { put("couriername", it) }
                put("resino", record.resino)
                record.created?.let { put("created", it) }
                put("flag", record.flag)
                put("cekfu", record.cekfu)
            }
            
            val url = URL("$supabaseUrl/rest/v1/tbl_expedisi")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            connection.doOutput = true
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "💾 Save barcode session response code: $responseCode")
            
            if (responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val createdRecord = jsonArray.getJSONObject(0)
                    val sessionId = createdRecord.getString("id")
                    Log.d(TAG, "✅ Barcode session saved successfully with ID: $sessionId")
                    return@withContext sessionId
                }
            } else {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else "No error details"
                Log.e(TAG, "❌ Failed to save barcode session: $errorResponse")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving barcode session: ${e.message}", e)
            null
        }
    }
    
    /**
     * Update barcode session
     */
    suspend fun updateBarcodeSession(sessionId: String, record: BarcodeSessionRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Updating barcode session: $sessionId")
            
            val jsonObject = JSONObject().apply {
                put("datetrans", record.datetrans)
                record.chanelsales?.let { put("chanelsales", it) }
                record.couriername?.let { put("couriername", it) }
                put("resino", record.resino)
                record.created?.let { put("created", it) }
                put("flag", record.flag)
                put("cekfu", record.cekfu)
            }
            
            val url = URL("$supabaseUrl/rest/v1/tbl_expedisi?orderno=eq.$sessionId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.doOutput = true
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "💾 Update barcode session response code: $responseCode")
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ Barcode session updated successfully")
                true
            } else {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else "No error details"
                Log.e(TAG, "❌ Failed to update barcode session: $errorResponse")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating barcode session: ${e.message}", e)
            false
        }
    }
    
    
    /**
     * Ambil barcode scan history untuk tanggal tertentu
     */
    suspend fun getBarcodeScanHistory(date: String, limit: Int = 100): List<BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📜 Getting barcode scan history for date: $date, limit: $limit")
            
            val queryUrl = "$supabaseUrl/rest/v1/tbl_resi?created=gte.$date&order=created.desc&limit=$limit"
            
            val url = URL(queryUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "📜 Barcode scan history response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val jsonArray = JSONArray(response)
                
                val records = mutableListOf<BarcodeScanRecord>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val record = BarcodeScanRecord(
                        id = jsonObject.optString("id")?.takeIf { it.isNotEmpty() },
                        Resi = jsonObject.getString("Resi"),
                        created = jsonObject.optString("created")?.takeIf { it.isNotEmpty() },
                        Keterangan = jsonObject.optString("Keterangan")?.takeIf { it.isNotEmpty() },
                        nokarung = jsonObject.optString("nokarung")?.takeIf { it.isNotEmpty() },
                        schedule = jsonObject.optString("schedule")?.takeIf { it.isNotEmpty() }
                    )
                    records.add(record)
                }
                
                Log.d(TAG, "📜 Retrieved ${records.size} barcode scan records")
                records
            } else {
                Log.e(TAG, "❌ Failed to get barcode scan history")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode scan history: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Ambil tanggal hari ini dalam format ISO 8601
     */
    private fun getTodayDate(): String {
        val utcNow = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        val today = utcNow.toLocalDate()
        return today.toString() // Format: YYYY-MM-DD (UTC)
    }
    
    /**
     * Ambil tanggal 4 hari terakhir (hari ini + 3 hari yang lalu) dalam format ISO 8601
     */
    private fun getFourDaysAgoDate(): String {
        val utcNow = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        val fourDaysAgo = utcNow.toLocalDate().minusDays(3)
        return fourDaysAgo.toString() // Format: YYYY-MM-DD (UTC)
    }
    
    /**
     * Ambil timestamp saat ini dalam format ISO 8601
     */
    private fun getCurrentTimestamp(): String {
        return java.time.Instant.now().toString()
    }
    
    /**
     * Get unique courier names from tbl_expedisi
     */
    suspend fun getUniqueCourierNames(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Fetching unique courier names from tbl_expedisi...")
            
            val todayDate = getTodayDate()
            val queryUrl = "$supabaseUrl/rest/v1/tbl_expedisi?select=couriername&couriername=not.is.null&created=gte.$todayDate&order=couriername.asc"
            
            val connection = URL(queryUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val jsonArray = JSONArray(response)
                
                val courierNames = mutableSetOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val courierName = jsonObject.optString("couriername", "").trim()
                    if (courierName.isNotEmpty()) {
                        courierNames.add(courierName)
                    }
                }
                
                val uniqueCouriers = courierNames.toList().sorted()
                Log.d(TAG, "✅ Fetched ${uniqueCouriers.size} unique courier names from tbl_expedisi")
                return@withContext uniqueCouriers
                
            } else {
                Log.e(TAG, "❌ Error fetching courier names: HTTP $responseCode")
                return@withContext emptyList<String>()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching unique courier names: ${e.message}", e)
            return@withContext emptyList<String>()
        }
    }
    
    /**
     * **4-DAY SYNC**: Get semua barcode resi data dari 4 hari terakhir (hari ini + 3 hari yang lalu)
     */
    suspend fun getAllBarcodeResi(): List<BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            val fourDaysAgoDate = getFourDaysAgoDate()
            // **PERBAIKAN**: Menggunakan pagination untuk mengatasi limit 1000 Supabase
            val allRecords = mutableListOf<BarcodeScanRecord>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal per request
            var hasMoreData = true
            
            while (hasMoreData) {
                val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_resi?created=gte.$fourDaysAgoDate&order=created.desc&limit=$limit&offset=$offset")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000 // 30 detik connection timeout
                connection.readTimeout = 45000 // 45 detik read timeout
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    
                    val batchRecords = mutableListOf<BarcodeScanRecord>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        
                        val record = BarcodeScanRecord(
                            id = jsonObject.optString("id")?.takeIf { it.isNotEmpty() },
                            Resi = jsonObject.optString("Resi") ?: "",
                            created = jsonObject.optString("created")?.takeIf { it.isNotEmpty() },
                            Keterangan = jsonObject.optString("Keterangan")?.takeIf { it.isNotEmpty() },
                            nokarung = jsonObject.optString("nokarung")?.takeIf { it.isNotEmpty() },
                            schedule = jsonObject.optString("schedule")?.takeIf { it.isNotEmpty() }
                        )
                        batchRecords.add(record)
                    }
                    
                    allRecords.addAll(batchRecords)
                    Log.d(TAG, "📦 Retrieved ${batchRecords.size} resi records (offset=$offset, total=${allRecords.size})")
                    
                    // Cek apakah masih ada data lagi
                    hasMoreData = batchRecords.size == limit
                    offset += limit
                    
                } else {
                    Log.e(TAG, "❌ Error getting barcode resi batch: HTTP $responseCode")
                    hasMoreData = false
                }
                
                connection.disconnect()
            }
            
            Log.d(TAG, "✅ Retrieved total ${allRecords.size} barcode resi records from last 4 days")
            if (allRecords.isNotEmpty()) {
                Log.d(TAG, "📊 Sample barcode resi records from Supabase:")
                allRecords.take(5).forEach { record ->
                    Log.d(TAG, "  - Resi: '${record.Resi}', Keterangan: '${record.Keterangan}', nokarung: '${record.nokarung}', schedule: '${record.schedule}'")
                }
            }
            allRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode resi: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * **DEBUG**: Test join query untuk cek apakah join berhasil
     */
    suspend fun testJoinQuery(): String = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_resi?created=gte.$todayDate&limit=5")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 45000
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "🔍 Test join query response: $response")
                return@withContext response
            } else {
                Log.e(TAG, "❌ Error testing join query: HTTP $responseCode")
                return@withContext "Error: HTTP $responseCode"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error testing join query: ${e.message}", e)
            return@withContext "Error: ${e.message}"
        }
    }
    
    /**
     * **FLAG-BASED SYNC**: Get semua barcode expedisi data dengan flag = "NO"
     */
    suspend fun getAllBarcodeExpedisi(): List<BarcodeSessionRecord> = withContext(Dispatchers.IO) {
        try {
            // **PERBAIKAN**: Mengambil semua data dengan flag = "NO" menggunakan pagination untuk mengatasi limit 1000 Supabase
            val allRecords = mutableListOf<BarcodeSessionRecord>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal per request
            var hasMoreData = true
            
            while (hasMoreData) {
                val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_expedisi?flag=eq.NO&order=created.desc&limit=$limit&offset=$offset")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000 // 30 detik connection timeout
                connection.readTimeout = 45000 // 45 detik read timeout
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    
                    val batchRecords = mutableListOf<BarcodeSessionRecord>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val record = BarcodeSessionRecord(
                            id = jsonObject.optString("id")?.takeIf { it.isNotEmpty() },
                            orderno = jsonObject.optString("orderno") ?: "",
                            datetrans = jsonObject.optString("datetrans") ?: "",
                            chanelsales = jsonObject.optString("chanelsales")?.takeIf { it.isNotEmpty() },
                            couriername = jsonObject.optString("couriername")?.takeIf { it.isNotEmpty() },
                            resino = jsonObject.optString("resino") ?: "",
                            created = jsonObject.optString("created")?.takeIf { it.isNotEmpty() },
                            flag = jsonObject.optString("flag") ?: "NO",
                            cekfu = jsonObject.optBoolean("cekfu", false)
                        )
                        batchRecords.add(record)
                    }
                    
                    allRecords.addAll(batchRecords)
                    Log.d(TAG, "📦 Retrieved ${batchRecords.size} expedisi records (offset=$offset, total=${allRecords.size})")
                    
                    // Cek apakah masih ada data lagi
                    hasMoreData = batchRecords.size == limit
                    offset += limit
                    
                } else {
                    Log.e(TAG, "❌ Error getting barcode expedisi batch: HTTP $responseCode")
                    hasMoreData = false
                }
                
                connection.disconnect()
            }
            
            Log.d(TAG, "✅ Retrieved total ${allRecords.size} barcode expedisi records with flag=NO")
            allRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode expedisi: ${e.message}", e)
            emptyList()
        }
    }
}
