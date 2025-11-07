package com.example.cekpicklist.api

import android.util.Log
import com.example.cekpicklist.config.BarcodeSupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
        // Flag untuk mengontrol verbose logging (set false untuk mengurangi log noise)
        private const val VERBOSE_LOGGING = false
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
        val cekfu: Boolean = false, // Check follow up
        val update_at: String? = null // Update timestamp untuk cache invalidation
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
            // Hanya log jika verbose logging enabled
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "💾 Saving barcode scan: ${record.Resi}")
            }
            
            // **VALIDASI**: Pastikan created selalu terisi, jika tidak, gunakan waktu sekarang
            val createdValue = record.created ?: java.time.Instant.now().toString()
            if (record.created.isNullOrBlank()) {
                Log.w(TAG, "⚠️ Record ${record.Resi} tidak punya created, menggunakan waktu sekarang: $createdValue")
            }
            
            val jsonObject = JSONObject().apply {
                put("Resi", record.Resi)
                put("created", createdValue) // **PERBAIKAN**: Selalu isi created (wajib)
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
            
            if (responseCode in 200..299) {
                // Hanya log jika verbose logging enabled
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "✅ Barcode scan saved successfully: ${record.Resi}")
                }
                true
            } else if (responseCode == 409) {
                // **PERBAIKAN**: Error 409 (Conflict) = duplicate key = data sudah ada
                // Ini bukan error, tapi indikasi data sudah ter-sync dengan benar
                // Gunakan Log.v untuk mengurangi noise (hanya terlihat jika verbose level diaktifkan)
                if (VERBOSE_LOGGING) {
                    val errorStream = connection.errorStream
                    val errorResponse = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                    } else "No error details"
                    Log.v(TAG, "ℹ️ Resi already exists (409): ${record.Resi}")
                    Log.v(TAG, "ℹ️ Error details: $errorResponse")
                }
                true // Treat 409 as success
            } else {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else "No error details"
                Log.e(TAG, "❌ Failed to save barcode scan: ${record.Resi} - $errorResponse")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving barcode scan: ${record.Resi} - ${e.message}", e)
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
     * Hapus barcode resi dari database
     */
    suspend fun deleteBarcodeResi(resi: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Deleting barcode resi: $resi")
            
            val url = URL("$supabaseUrl/rest/v1/tbl_resi?Resi=eq.$resi")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=minimal")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "🗑️ Delete barcode resi response code: $responseCode")
            
            val success = responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == 204
            if (success) {
                Log.d(TAG, "✅ Barcode resi deleted successfully: $resi")
            } else {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else "No error details"
                Log.e(TAG, "❌ Failed to delete barcode resi: $errorResponse")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting barcode resi: ${e.message}", e)
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
     * Ambil tanggal 7 hari terakhir (hari ini + 6 hari yang lalu) dalam format ISO 8601
     */
    private fun getSevenDaysAgoDate(): String {
        val utcNow = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        val sevenDaysAgo = utcNow.toLocalDate().minusDays(6)
        return sevenDaysAgo.toString() // Format: YYYY-MM-DD (UTC)
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
     * Get total qty expedisi (semua flag) untuk courier tertentu pada HARI INI
     */
    suspend fun getExpeditionTotalToday(courier: String): Int = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            var total = 0
            var offset = 0
            val limit = 1000
            var hasMore = true
            
            while (hasMore) {
                val url = URL("$supabaseUrl/rest/v1/tbl_expedisi?couriername=eq.${java.net.URLEncoder.encode(courier, "UTF-8")}&created=gte.$todayDate&order=created.desc&limit=$limit&offset=$offset")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
                connection.setRequestProperty("Content-Type", "application/json")
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    total += jsonArray.length()
                    hasMore = jsonArray.length() == limit
                    offset += limit
                } else {
                    hasMore = false
                }
                connection.disconnect()
            }
            Log.d(TAG, "📊 Total expedisi today for '$courier': $total")
            total
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expedition total today: ${e.message}", e)
            0
        }
    }
    
    /**
     * **7-DAY SYNC**: Get semua barcode resi data dari 7 hari terakhir (hari ini + 6 hari yang lalu)
     */
    suspend fun getAllBarcodeResi(): List<BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            val sevenDaysAgoDate = getSevenDaysAgoDate()
            // **PERBAIKAN**: Menggunakan pagination untuk mengatasi limit 1000 Supabase
            val allRecords = mutableListOf<BarcodeScanRecord>()
            var offset = 0
            val limit = 1000 // Supabase limit maksimal per request
            var hasMoreData = true
            
            while (hasMoreData) {
                val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_resi?created=gte.$sevenDaysAgoDate&order=created.desc&limit=$limit&offset=$offset")
                
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
            
            Log.d(TAG, "✅ Retrieved total ${allRecords.size} barcode resi records from last 7 days")
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
     * Incremental: ambil barcode resi sejak tanggal (YYYY-MM-DD) tertentu
     */
    suspend fun getBarcodeResiSince(sinceDate: String): List<BarcodeScanRecord> = withContext(Dispatchers.IO) {
        try {
            val allRecords = mutableListOf<BarcodeScanRecord>()
            var offset = 0
            val limit = 1000
            var hasMoreData = true
            while (hasMoreData) {
                val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_resi?created=gte.$sinceDate&order=created.desc&limit=$limit&offset=$offset")
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
                    val jsonArray = JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        allRecords.add(
                            BarcodeScanRecord(
                                id = jsonObject.optString("id").takeIf { it.isNotEmpty() },
                                Resi = jsonObject.optString("Resi") ?: "",
                                created = jsonObject.optString("created").takeIf { it.isNotEmpty() },
                                Keterangan = jsonObject.optString("Keterangan").takeIf { it.isNotEmpty() },
                                nokarung = jsonObject.optString("nokarung").takeIf { it.isNotEmpty() },
                                schedule = jsonObject.optString("schedule").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                    hasMoreData = jsonArray.length() == limit
                    offset += limit
                } else {
                    hasMoreData = false
                }
                connection.disconnect()
            }
            allRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode resi since $sinceDate: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Incremental: ambil expedisi (flag=NO) sejak tanggal (YYYY-MM-DD) tertentu
     */
    suspend fun getBarcodeExpedisiSince(sinceDate: String): List<BarcodeSessionRecord> = withContext(Dispatchers.IO) {
        try {
            val allRecords = mutableListOf<BarcodeSessionRecord>()
            var offset = 0
            val limit = 1000
            var hasMoreData = true
            val selectCols = "orderno,datetrans,chanelsales,couriername,resino,created,flag,cekfu"
            while (hasMoreData) {
                val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_expedisi?select=$selectCols&flag=eq.NO&created=gte.$sinceDate&order=created.desc&limit=$limit&offset=$offset")
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
                    val jsonArray = JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        allRecords.add(
                            BarcodeSessionRecord(
                                id = jsonObject.optString("id").takeIf { it.isNotEmpty() },
                                orderno = jsonObject.optString("orderno") ?: "",
                                datetrans = jsonObject.optString("datetrans") ?: "",
                                chanelsales = jsonObject.optString("chanelsales").takeIf { it.isNotEmpty() },
                                couriername = jsonObject.optString("couriername").takeIf { it.isNotEmpty() },
                                resino = jsonObject.optString("resino") ?: "",
                                created = jsonObject.optString("created").takeIf { it.isNotEmpty() },
                                flag = jsonObject.optString("flag") ?: "NO",
                                cekfu = jsonObject.optBoolean("cekfu", false)
                            )
                        )
                    }
                    hasMoreData = jsonArray.length() == limit
                    offset += limit
                } else {
                    hasMoreData = false
                }
                connection.disconnect()
            }
            allRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting barcode expedisi since $sinceDate: ${e.message}", e)
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
     * Get jumlah unik resino (tbl_expedisi) untuk HARI INI langsung dari Supabase
     */
    suspend fun getTodayUniqueExpedisiCountSupabase(): Int = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            val unique = mutableSetOf<String>()
            var offset = 0
            val limit = 1000
            var hasMore = true
            val selectCols = "resino"

            while (hasMore) {
                val url = URL("$supabaseUrl/rest/v1/tbl_expedisi?select=$selectCols&created=gte.$todayDate&order=created.desc&limit=$limit&offset=$offset")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
                conn.setRequestProperty("Content-Type", "application/json")

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(response)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val resino = obj.optString("resino").trim()
                        if (resino.isNotEmpty()) unique.add(resino.uppercase())
                    }
                    hasMore = arr.length() == limit
                    offset += limit
                } else {
                    hasMore = false
                }
                conn.disconnect()
            }

            Log.d(TAG, "📊 Today unique resino from Supabase: ${unique.size}")
            unique.size
        } catch (e: CancellationException) {
            Log.d(TAG, "🛑 getTodayUniqueExpedisiCountSupabase cancelled (normal behavior)")
            throw e // Re-throw untuk proper coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching today unique resino: ${e.message}", e)
            0
        }
    }

    /**
     * Get total scan hari ini (tbl_resi) langsung dari Supabase
     * Hanya menghitung scan dengan schedule = "ontime" atau "batal" (kecualikan "late")
     */
    suspend fun getTodayResiScanCountSupabase(): Int = withContext(Dispatchers.IO) {
        try {
            val todayDate = getTodayDate()
            var total = 0
            var offset = 0
            val limit = 1000
            var hasMore = true
            val selectCols = "id"

            // Filter: created >= todayDate AND schedule IN ('ontime', 'batal')
            // PostgREST syntax: or(schedule.eq.ontime,schedule.eq.batal)
            while (hasMore) {
                val filterParams = "select=$selectCols&created=gte.$todayDate&or=(schedule.eq.ontime,schedule.eq.batal)&order=created.desc&limit=$limit&offset=$offset"
                val url = URL("$supabaseUrl/rest/v1/tbl_resi?$filterParams")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
                conn.setRequestProperty("Content-Type", "application/json")

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(response)
                    total += arr.length()
                    hasMore = arr.length() == limit
                    offset += limit
                } else {
                    hasMore = false
                }
                conn.disconnect()
            }

            Log.d(TAG, "📊 Today resi scan count from Supabase (ontime + batal only): $total")
            total
        } catch (e: CancellationException) {
            Log.d(TAG, "🛑 getTodayResiScanCountSupabase cancelled (normal behavior)")
            throw e // Re-throw untuk proper coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching today resi scan count: ${e.message}", e)
            0
        }
    }
    
    /**
     * **SUPABASE-FIRST**: Cek apakah resi ada di tbl_expedisi (query langsung ke Supabase, tidak menggunakan cache)
     * Digunakan untuk validasi NOT_FOUND yang kritis
     */
    suspend fun checkExpedisiByResinoDirect(resino: String): BarcodeSessionRecord? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 [Supabase-First] Checking resi '$resino' in tbl_expedisi (direct query)...")
            
            val selectCols = "orderno,datetrans,chanelsales,couriername,resino,created,flag,cekfu,update_at"
            val url = URL("$supabaseUrl/rest/v1/tbl_expedisi?select=$selectCols&resino=eq.$resino&limit=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000 // 10 detik untuk validasi cepat
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                if (jsonArray.length() > 0) {
                    val jsonObject = jsonArray.getJSONObject(0)
                    val record = BarcodeSessionRecord(
                        id = jsonObject.optString("id")?.takeIf { it.isNotEmpty() },
                        orderno = jsonObject.optString("orderno") ?: "",
                        datetrans = jsonObject.optString("datetrans") ?: "",
                        chanelsales = jsonObject.optString("chanelsales")?.takeIf { it.isNotEmpty() },
                        couriername = jsonObject.optString("couriername")?.takeIf { it.isNotEmpty() },
                        resino = jsonObject.optString("resino") ?: "",
                        created = jsonObject.optString("created")?.takeIf { it.isNotEmpty() },
                        flag = jsonObject.optString("flag") ?: "NO",
                        cekfu = jsonObject.optBoolean("cekfu", false),
                        update_at = jsonObject.optString("update_at")?.takeIf { it.isNotEmpty() }
                    )
                    Log.d(TAG, "✅ [Supabase-First] Found resi '$resino' in tbl_expedisi: flag=${record.flag}")
                    connection.disconnect()
                    return@withContext record
                } else {
                    Log.d(TAG, "ℹ️ [Supabase-First] Resi '$resino' NOT FOUND in tbl_expedisi")
                    connection.disconnect()
                    return@withContext null
                }
            } else {
                Log.e(TAG, "❌ [Supabase-First] Error checking resi: HTTP $responseCode")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [Supabase-First] Error checking resi in expedisi: ${e.message}", e)
            null
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
                val selectCols = "orderno,datetrans,chanelsales,couriername,resino,created,flag,cekfu,update_at"
                val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_expedisi?select=$selectCols&flag=eq.NO&order=created.desc&limit=$limit&offset=$offset")
                
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
                            cekfu = jsonObject.optBoolean("cekfu", false),
                            update_at = jsonObject.optString("update_at")?.takeIf { it.isNotEmpty() }
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
    
    /**
     * Get timestamp update_at terbaru dari tbl_expedisi (untuk cache invalidation)
     * Query hanya mengambil 1 record dengan update_at terbesar
     */
    suspend fun getLatestExpedisiUpdateAt(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Fetching latest update_at from tbl_expedisi...")
            
            val selectCols = "update_at"
            val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_expedisi?select=$selectCols&flag=eq.NO&order=update_at.desc&limit=1")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000 // 10 detik
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                if (jsonArray.length() > 0) {
                    val jsonObject = jsonArray.getJSONObject(0)
                    val updateAt = jsonObject.optString("update_at")?.takeIf { it.isNotEmpty() }
                    Log.d(TAG, "✅ Latest update_at from tbl_expedisi: $updateAt")
                    connection.disconnect()
                    return@withContext updateAt
                } else {
                    Log.d(TAG, "ℹ️ No records found in tbl_expedisi")
                    connection.disconnect()
                    return@withContext null
                }
            } else {
                Log.e(TAG, "❌ Error fetching latest update_at: HTTP $responseCode")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching latest update_at: ${e.message}", e)
            null
        }
    }
}
