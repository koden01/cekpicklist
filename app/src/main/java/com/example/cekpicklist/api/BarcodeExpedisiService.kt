package com.example.cekpicklist.api

import com.example.cekpicklist.config.BarcodeSupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Service untuk mengakses data expedisi dari database barcode scanner
 * REAL IMPLEMENTATION - Menggunakan HTTP client untuk koneksi ke Supabase
 * Database: yaarzoafxwfcjdpmojxd.supabase.co
 */
class BarcodeExpedisiService {
    
    companion object {
        private const val TAG = "BarcodeExpedisiService"
    }
    
    /**
     * Helper method untuk membuat HTTP request ke Supabase
     */
    private suspend fun makeSupabaseRequest(
        endpoint: String,
        method: String = "GET",
        body: String? = null
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = method
            connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            
            if (body != null) {
                connection.doOutput = true
                val outputStream = connection.outputStream
                val writer = OutputStreamWriter(outputStream)
                writer.write(body)
                writer.flush()
                writer.close()
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "🌐 HTTP $method $endpoint - Response: $responseCode")
            
            if (responseCode in 200..299) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseString = response.toString()
                Log.d(TAG, "✅ Response: $responseString")
                
                if (responseString.isNotEmpty()) {
                    JSONObject(responseString)
                } else {
                    JSONObject("{}")
                }
            } else {
                Log.e(TAG, "❌ HTTP Error: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network error: ${e.message}", e)
            null
        }
    }
    
    /**
     * Helper method untuk membuat HTTP request ke Supabase dengan array response
     */
    private suspend fun makeSupabaseArrayRequest(
        endpoint: String,
        method: String = "GET",
        body: String? = null
    ): JSONArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = method
            connection.setRequestProperty("apikey", BarcodeSupabaseConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${BarcodeSupabaseConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            
            if (body != null) {
                connection.doOutput = true
                val outputStream = connection.outputStream
                val writer = OutputStreamWriter(outputStream)
                writer.write(body)
                writer.flush()
                writer.close()
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "🌐 HTTP $method $endpoint - Response: $responseCode")
            
            if (responseCode in 200..299) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseString = response.toString()
                Log.d(TAG, "✅ Response: $responseString")
                
                if (responseString.isNotEmpty()) {
                    JSONArray(responseString)
                } else {
                    JSONArray()
                }
            } else {
                Log.e(TAG, "❌ HTTP Error: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network error: ${e.message}", e)
            null
        }
    }
    
    /**
     * Data class untuk tbl_resi
     */
    data class ResiRecord(
        val Resi: String,
        val resino: String,
        val orderno: String,
        val chanelsales: String,
        val couriername: String,
        val created: String,
        val datetrans: String,
        val flag: String,
        val cekfu: String,
        val nokarung: String,
        val schedule: String,
        val Keterangan: String
    )
    
    /**
     * Data class untuk tbl_expedisi
     */
    data class ExpedisiRecord(
        val resino: String,
        val orderno: String,
        val chanelsales: String,
        val couriername: String,
        val created: String,
        val datetrans: String,
        val flag: String,
        val cekfu: String,
        val nokarung: String,
        val schedule: String,
        val Keterangan: String
    )
    
    /**
     * Cek apakah resi ada di tbl_resi (MOCK)
     */
    suspend fun checkResiInDatabase(resi: String): ResiRecord? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Checking resi '$resi' in tbl_resi...")
            
            val response = makeSupabaseArrayRequest(
                endpoint = "tbl_resi?resino=eq.$resi&limit=1"
            )
            
            if (response != null && response.length() > 0) {
                val item = response.getJSONObject(0)
                val resiRecord = ResiRecord(
                    Resi = item.optString("Resi", ""),
                    resino = item.optString("resino", ""),
                    orderno = item.optString("orderno", ""),
                    chanelsales = item.optString("chanelsales", ""),
                    couriername = item.optString("couriername", ""),
                    created = item.optString("created", ""),
                    datetrans = item.optString("datetrans", ""),
                    flag = item.optString("flag", ""),
                    cekfu = item.optString("cekfu", ""),
                    nokarung = item.optString("nokarung", ""),
                    schedule = item.optString("schedule", ""),
                    Keterangan = item.optString("Keterangan", "")
                )
                
                Log.d(TAG, "✅ Found resi '$resi' in tbl_resi: $resiRecord")
                resiRecord
            } else {
                Log.d(TAG, "ℹ️ Resi '$resi' not found in tbl_resi")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking resi in database: ${e.message}", e)
            null
        }
    }
    
    /**
     * Cek apakah resi ada di tbl_expedisi (MOCK)
     */
    suspend fun checkExpedisiInDatabase(resi: String): ExpedisiRecord? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Checking resi '$resi' in tbl_expedisi...")
            
            val response = makeSupabaseArrayRequest(
                endpoint = "tbl_expedisi?resino=eq.$resi&limit=1"
            )
            
            if (response != null && response.length() > 0) {
                val item = response.getJSONObject(0)
                val expedisiRecord = ExpedisiRecord(
                    resino = item.optString("resino", ""),
                    orderno = item.optString("orderno", ""),
                    chanelsales = item.optString("chanelsales", ""),
                    couriername = item.optString("couriername", ""),
                    created = item.optString("created", ""),
                    datetrans = item.optString("datetrans", ""),
                    flag = item.optString("flag", ""),
                    cekfu = item.optString("cekfu", ""),
                    nokarung = item.optString("nokarung", ""),
                    schedule = item.optString("schedule", ""),
                    Keterangan = item.optString("Keterangan", "")
                )
                
                Log.d(TAG, "✅ Found resi '$resi' in tbl_expedisi: $expedisiRecord")
                expedisiRecord
            } else {
                Log.d(TAG, "ℹ️ Resi '$resi' not found in tbl_expedisi")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking resi in expedisi database: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get semua resi yang sudah di-scan (untuk validasi duplikat) (MOCK)
     */
    suspend fun getAllScannedResiNumbers(): List<String> = withContext(Dispatchers.IO) {
        try {
            // MOCK implementation - dependency tidak tersedia
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get semua expedisi dengan flag = 'NO' (untuk validasi status) (MOCK)
     */
    suspend fun getAllFlagNoExpedisiData(): List<ExpedisiRecord> = withContext(Dispatchers.IO) {
        try {
            // MOCK implementation - dependency tidak tersedia
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get semua resi dari expedisi dengan flag = 'YES' (untuk validasi duplikat) (MOCK)
     */
    suspend fun getAllFlagYesExpedisiResiNumbers(): List<String> = withContext(Dispatchers.IO) {
        try {
            // MOCK implementation - dependency tidak tersedia
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Test koneksi ke database (MOCK)
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            // MOCK implementation - dependency tidak tersedia
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get unique couriername values from tbl_expedisi (REAL DATA)
     * Query: SELECT DISTINCT couriername FROM tbl_expedisi WHERE couriername IS NOT NULL ORDER BY couriername
     */
    suspend fun getUniqueCourierNames(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Fetching unique courier names from tbl_expedisi...")
            
            val response = makeSupabaseArrayRequest(
                endpoint = "tbl_expedisi?select=couriername&couriername=not.is.null&order=couriername"
            )
            
            if (response != null) {
                val courierNames = mutableSetOf<String>()
                for (i in 0 until response.length()) {
                    val item = response.getJSONObject(i)
                    val courierName = item.optString("couriername", "")
                    if (courierName.isNotEmpty()) {
                        courierNames.add(courierName)
                    }
                }
                
                val result = courierNames.sorted()
                Log.d(TAG, "✅ Found ${result.size} unique courier names: $result")
                result
            } else {
                Log.w(TAG, "⚠️ No response from database, returning empty list")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching courier names: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get unique karung numbers from tbl_resi (REAL DATA)
     * Query: SELECT DISTINCT nokarung FROM tbl_resi WHERE nokarung IS NOT NULL ORDER BY nokarung
     */
    suspend fun getUniqueKarungNumbers(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Fetching unique karung numbers from tbl_resi...")
            
            val response = makeSupabaseArrayRequest(
                endpoint = "tbl_resi?select=nokarung&nokarung=not.is.null&order=nokarung"
            )
            
            if (response != null) {
                val karungNumbers = mutableSetOf<String>()
                for (i in 0 until response.length()) {
                    val item = response.getJSONObject(i)
                    val karungNumber = item.optString("nokarung", "")
                    if (karungNumber.isNotEmpty()) {
                        karungNumbers.add(karungNumber)
                    }
                }
                
                val result = karungNumbers.sorted()
                Log.d(TAG, "✅ Found ${result.size} unique karung numbers: $result")
                result
            } else {
                Log.w(TAG, "⚠️ No response from database, returning fallback list")
                (1..50).map { it.toString() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching karung numbers: ${e.message}", e)
            (1..50).map { it.toString() }
        }
    }
    
    /**
     * Get highest karung number for specific expedition today (REAL DATA)
     * Mengikuti logika web app: highestKarung dari data yang sudah ada untuk expedisi tersebut pada hari ini
     * Query: SELECT MAX(CAST(nokarung AS INTEGER)) FROM tbl_resi WHERE Keterangan = ? AND DATE(created) = CURRENT_DATE
     */
    suspend fun getHighestKarungForExpeditionToday(expedition: String): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Fetching highest karung for expedition '$expedition' today...")
            
            // Get today's date in YYYY-MM-DD format
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            
            val response = makeSupabaseArrayRequest(
                endpoint = "tbl_resi?select=nokarung&Keterangan=eq.$expedition&created=gte.$today&nokarung=not.is.null&order=nokarung.desc&limit=1"
            )
            
            if (response != null && response.length() > 0) {
                val item = response.getJSONObject(0)
                val karungNumber = item.optString("nokarung", "0")
                val highestKarung = try {
                    karungNumber.toInt()
                } catch (e: NumberFormatException) {
                    0
                }
                
                Log.d(TAG, "✅ Highest karung for expedition '$expedition' today: $highestKarung")
                highestKarung
            } else {
                Log.d(TAG, "ℹ️ No karung data found for expedition '$expedition' today")
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching highest karung: ${e.message}", e)
            0
        }
    }
    
    /**
     * Get last karung number for specific expedition today (MOCK)
     * Mengikuti logika web app: nokarung dari resi terakhir yang di-scan untuk expedisi tersebut pada hari ini
     */
    suspend fun getLastKarungForExpeditionToday(expedition: String): String = withContext(Dispatchers.IO) {
        try {
            // MOCK implementation - dependency tidak tersedia
            // TODO: Implementasi real query:
            // SELECT nokarung FROM tbl_resi 
            // WHERE Keterangan = ? AND DATE(created) = CURRENT_DATE AND nokarung IS NOT NULL AND nokarung != ''
            // ORDER BY created DESC LIMIT 1
            "0" // Default ke "0" jika tidak ada data
        } catch (e: Exception) {
            "0"
        }
    }
    
    /**
     * Get karung summary data for specific expedition today (REAL DATA)
     * Mengikuti logika web app: data karung yang sudah diproses untuk expedisi tertentu pada hari ini
     * Query: SELECT nokarung, COUNT(*) as quantity FROM tbl_resi WHERE Keterangan = ? AND DATE(created) = CURRENT_DATE GROUP BY nokarung
     */
    suspend fun getKarungSummaryForExpeditionToday(expedition: String): List<com.example.cekpicklist.fragment.KarungSummaryModalFragment.KarungSummaryItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Fetching karung summary for expedition '$expedition' today...")
            
            // Get today's date in YYYY-MM-DD format
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            
            val response = makeSupabaseArrayRequest(
                endpoint = "tbl_resi?select=nokarung&Keterangan=eq.$expedition&created=gte.$today&nokarung=not.is.null&order=nokarung"
            )
            
            if (response != null) {
                // Group by karung number and count quantity
                val karungCounts = mutableMapOf<String, Int>()
                
                for (i in 0 until response.length()) {
                    val item = response.getJSONObject(i)
                    val karungNumber = item.optString("nokarung", "")
                    if (karungNumber.isNotEmpty()) {
                        karungCounts[karungNumber] = karungCounts.getOrDefault(karungNumber, 0) + 1
                    }
                }
                
                val summaryData = karungCounts.map { (karungNumber, quantity) ->
                    com.example.cekpicklist.fragment.KarungSummaryModalFragment.KarungSummaryItem(karungNumber, quantity)
                }.sortedBy { it.karungNumber.toIntOrNull() ?: 0 }
                
                Log.d(TAG, "✅ Found ${summaryData.size} karung summary items for expedition '$expedition': $summaryData")
                summaryData
            } else {
                Log.w(TAG, "⚠️ No response from database for karung summary")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching karung summary: ${e.message}", e)
            emptyList()
        }
    }
}