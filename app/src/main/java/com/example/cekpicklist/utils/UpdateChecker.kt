package com.example.cekpicklist.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.example.cekpicklist.R
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update Checker untuk Cek Picklist
 * Mengecek versi terbaru dari GitHub API dan menampilkan dialog update
 */
class UpdateChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateChecker"
        // Pastikan repo sudah benar (koden01/cekpicklist)
        private const val GITHUB_API_URL = "https://api.github.com/repos/koden01/cekpicklist/releases/latest"
        // Interval per jam
        private const val MIN_UPDATE_INTERVAL_MS = 60L * 60L * 1000L
    }
    
    private val prefs = context.getSharedPreferences("UpdateChecker", Context.MODE_PRIVATE)
    
    /**
     * Cek update dengan interval yang ditentukan
     */
    fun checkForUpdates(forceCheck: Boolean = false) {
        if (!forceCheck && !shouldCheckForUpdate()) {
            Log.d(TAG, "Skip update check - masih dalam interval")
            return
        }
        
        Log.d(TAG, "Starting update check...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latestVersion = getLatestVersionFromGitHub()
                val currentVersion = getCurrentVersion()
                
                Log.d(TAG, "Current version: $currentVersion, Latest version: $latestVersion")
                
                // Guard: jika latest kosong atau versi sama, jangan tampilkan notifikasi
                if (latestVersion.isBlank() || latestVersion.trim() == currentVersion.trim()) {
                    Log.d(TAG, "Skip update dialog: empty latest version or same version (current: $currentVersion, latest: $latestVersion)")
                    updateLastCheckTime()
                    return@launch
                }

                val isNewVersion = isNewVersionAvailable(currentVersion, latestVersion)
                Log.d(TAG, "Version comparison result: isNewVersion=$isNewVersion")
                
                if (isNewVersion) {
                    Log.d(TAG, "Showing update dialog for version $latestVersion")
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestVersion)
                    }
                } else {
                    Log.d(TAG, "App is up to date - no update needed")
                }
                
                // Update last check time
                updateLastCheckTime()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
            }
        }
    }
    
    /**
     * Cek apakah sudah waktunya untuk cek update
     */
    private fun shouldCheckForUpdate(): Boolean {
        val lastCheck = prefs.getLong("last_update_check", 0)
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastCheck
        return elapsed >= MIN_UPDATE_INTERVAL_MS
    }
    
    /**
     * Ambil versi terbaru dari GitHub API
     */
    private suspend fun getLatestVersionFromGitHub(): String {
        return withContext(Dispatchers.IO) {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val latest = tagName.removePrefix("v")
                    Log.d(TAG, "Latest tag: $tagName -> parsed: $latest")
                    latest
                } else {
                    Log.e(TAG, "GitHub API error: $responseCode")
                    throw Exception("GitHub API error: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    /**
     * Ambil versi aplikasi saat ini
     */
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found: ${e.message}", e)
            "1.0.0"
        }
    }
    
    /**
     * Bandingkan versi untuk menentukan apakah ada update
     * Hanya mengembalikan true jika versi lebih baru (bukan sama)
     */
    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        return try {
            val current = parseVersion(currentVersion)
            val latest = parseVersion(latestVersion)
            
            Log.d(TAG, "Comparing versions: current=$current, latest=$latest")
            
            // Bandingkan major, minor, patch - hanya versi yang lebih baru
            val result = when {
                latest.major > current.major -> {
                    Log.d(TAG, "Major version is newer: ${latest.major} > ${current.major}")
                    true
                }
                latest.major == current.major && latest.minor > current.minor -> {
                    Log.d(TAG, "Minor version is newer: ${latest.minor} > ${current.minor}")
                    true
                }
                latest.major == current.major && latest.minor == current.minor && latest.patch > current.patch -> {
                    Log.d(TAG, "Patch version is newer: ${latest.patch} > ${current.patch}")
                    true
                }
                else -> {
                    Log.d(TAG, "No newer version available")
                    false
                }
            }
            
            Log.d(TAG, "Version comparison result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: ${e.message}", e)
            false
        }
    }
    
    /**
     * Parse versi string menjadi major.minor.patch
     */
    private fun parseVersion(version: String): Version {
        val parts = version.split(".")
        val parsed = Version(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
        Log.d(TAG, "Parsed version '$version' -> $parsed")
        return parsed
    }
    
    /**
     * Tampilkan dialog update
     */
    private fun showUpdateDialog(latestVersion: String) {
        val currentVersion = getCurrentVersion()
        
        val dialog = AlertDialog.Builder(context, R.style.RoundDialogTheme)
            .setTitle("🔄 Update Tersedia")
            .setMessage("""
                Versi terbaru $latestVersion tersedia!
                (Versi saat ini: $currentVersion)
                
                📱 Fitur baru dan perbaikan bug
                🔧 Performa yang lebih baik
                🛡️ Keamanan yang ditingkatkan
                
                Apakah Anda ingin mengunduh update?
            """.trimIndent())
            .setPositiveButton("📥 Download & Install") { _, _ ->
                downloadAndInstallAPK()
            }
            .setNegativeButton("⏰ Nanti") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("❌ Jangan Tampilkan Lagi") { dialog, _ ->
                disableUpdateCheck()
                dialog.dismiss()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Download APK langsung dari GitHub releases
     */
    private fun downloadAndInstallAPK() {
        try {
            Log.d(TAG, "Starting APK download...")
            
            // Get latest release info to get download URL
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val latestVersion = getLatestVersionFromGitHub()
                    val downloadUrl = getAPKDownloadUrl(latestVersion)
                    
                    if (downloadUrl.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            startAPKDownload(downloadUrl, latestVersion)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            // Fallback ke browser jika tidak bisa dapat download URL
                            openDownloadPage()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting download URL: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        openDownloadPage()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download: ${e.message}", e)
            openDownloadPage()
        }
    }
    
    /**
     * Get APK download URL dari GitHub API
     */
    private suspend fun getAPKDownloadUrl(version: String): String {
        return try {
            val url = URL("https://api.github.com/repos/koden01/cekpicklist/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val assets = json.getJSONArray("assets")
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        val downloadUrl = asset.getString("browser_download_url")
                        Log.d(TAG, "Found APK download URL: $downloadUrl")
                        return downloadUrl
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting APK download URL: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Start APK download menggunakan DownloadManager
     */
    private fun startAPKDownload(downloadUrl: String, version: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
            request.setTitle("Cek Picklist Update v$version")
            request.setDescription("Downloading APK update...")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CekPicklist-v$version-release.apk")
            
            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "APK download started with ID: $downloadId")
            
            // Show success message with install option
            showDownloadSuccessDialog(downloadId, version)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download: ${e.message}", e)
            openDownloadPage()
        }
    }
    
    /**
     * Show dialog setelah download selesai
     */
    private fun showDownloadSuccessDialog(downloadId: Long, version: String) {
        AlertDialog.Builder(context)
            .setTitle("📥 Download Started")
            .setMessage("""
                APK update v$version sedang diunduh...
                
                Setelah download selesai, Anda akan mendapat notifikasi.
                Klik notifikasi untuk menginstall APK.
                
                Atau buka folder Downloads untuk install manual.
            """.trimIndent())
            .setPositiveButton("✅ OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("📁 Buka Downloads") { dialog, _ ->
                openDownloadsFolder()
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    /**
     * Buka folder Downloads
     */
    private fun openDownloadsFolder() {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening downloads folder: ${e.message}", e)
            // Fallback ke file manager
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()), "resource/folder")
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening file manager: ${e2.message}", e2)
            }
        }
    }
    
    /**
     * Buka halaman download (fallback)
     */
    private fun openDownloadPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://github.com/koden01/cekpicklist/releases/latest")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening download page: ${e.message}", e)
        }
    }
    
    /**
     * Update waktu terakhir cek
     */
    private fun updateLastCheckTime() {
        prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
    }
    
    /**
     * Nonaktifkan update check
     */
    private fun disableUpdateCheck() {
        prefs.edit().putBoolean("update_check_disabled", true).apply()
        Log.d(TAG, "Update check disabled by user")
    }
    
    /**
     * Cek apakah update check dinonaktifkan
     */
    fun isUpdateCheckDisabled(): Boolean {
        return prefs.getBoolean("update_check_disabled", false)
    }
    
    /**
     * Aktifkan kembali update check
     */
    fun enableUpdateCheck() {
        prefs.edit().putBoolean("update_check_disabled", false).apply()
        Log.d(TAG, "Update check enabled")
    }
    
    /**
     * Force check update (untuk testing)
     */
    fun forceCheckUpdate() {
        Log.d(TAG, "Force checking for updates...")
        checkForUpdates(forceCheck = true)
    }
    
    /**
     * Reset last check time (untuk testing)
     */
    fun resetLastCheckTime() {
        prefs.edit().remove("last_update_check").apply()
        Log.d(TAG, "Last check time reset")
    }
    
    /**
     * Get debug info (untuk testing)
     */
    fun getDebugInfo(): String {
        val currentVersion = getCurrentVersion()
        val lastCheck = prefs.getLong("last_update_check", 0)
        val isDisabled = prefs.getBoolean("update_check_disabled", false)
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastCheck
        val shouldCheck = elapsed >= MIN_UPDATE_INTERVAL_MS
        
        return """
            UpdateChecker Debug Info:
            - Current Version: $currentVersion
            - Last Check: ${if (lastCheck == 0L) "Never" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCheck))}
            - Elapsed: ${elapsed / 1000 / 60} minutes
            - Should Check: $shouldCheck
            - Update Check Disabled: $isDisabled
            - Min Interval: ${MIN_UPDATE_INTERVAL_MS / 1000 / 60} minutes
        """.trimIndent()
    }
    
    /**
     * Data class untuk versi
     */
    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int
    )
}
