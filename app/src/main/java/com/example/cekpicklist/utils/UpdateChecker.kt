package com.example.cekpicklist.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.cekpicklist.R
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update Checker untuk Cek Picklist
 * 
 * Mekanisme:
 * - Cek update setiap kali aplikasi dibuka
 * - Hanya tampilkan notifikasi/dialog jika ada versi baru
 * - Silent (tidak ada notifikasi) jika versi sudah terbaru
 * - Mengambil informasi dari GitHub Releases API
 */
class UpdateChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API_URL = "https://api.github.com/repos/koden01/cekpicklist/releases/latest"
        @Deprecated("Tidak lagi digunakan - app sekarang cek update setiap kali dibuka")
        private const val MIN_UPDATE_INTERVAL_DAYS = 1 // Legacy: Minimal 1 hari antar cek
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "update_channel"
        private const val CHANNEL_NAME = "Update Notifications"
    }
    
    private val prefs = context.getSharedPreferences("UpdateChecker", Context.MODE_PRIVATE)
    
    /**
     * Cek update setiap kali aplikasi dibuka
     * Hanya tampilkan notifikasi jika ada versi baru (silent jika versi sama)
     */
    fun checkForUpdates(forceCheck: Boolean = false) {
        Log.d(TAG, "Starting update check...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latestVersion = getLatestVersionFromGitHub()
                val currentVersion = getCurrentVersion()
                
                Log.d(TAG, "Current version: $currentVersion, Latest version: $latestVersion")
                
                if (isNewVersionAvailable(currentVersion, latestVersion)) {
                    Log.d(TAG, "✨ New version available: $latestVersion")
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestVersion)
                    }
                } else {
                    Log.d(TAG, "✅ App is up to date (no notification shown)")
                }
                
                // Update last check time
                updateLastCheckTime()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking for updates: ${e.message}", e)
            }
        }
    }
    
    /**
     * Cek apakah sudah waktunya untuk cek update
     * @deprecated Tidak lagi digunakan - app sekarang cek update setiap kali dibuka
     */
    @Deprecated("App sekarang selalu cek update setiap kali dibuka (silent jika tidak ada update)")
    private fun shouldCheckForUpdate(): Boolean {
        val lastCheck = prefs.getLong("last_update_check", 0)
        val currentTime = System.currentTimeMillis()
        val daysSinceLastCheck = (currentTime - lastCheck) / (1000 * 60 * 60 * 24)
        
        return daysSinceLastCheck >= MIN_UPDATE_INTERVAL_DAYS
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
                    
                    // Remove 'v' prefix if exists
                    tagName.removePrefix("v")
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
     * Ambil download URL APK dari GitHub API
     */
    private suspend fun getDownloadUrlFromGitHub(): String {
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
                    val assets = json.getJSONArray("assets")
                    
                    // Cari file APK
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            return@withContext asset.getString("browser_download_url")
                        }
                    }
                    
                    throw Exception("APK file not found in release assets")
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
     */
    private fun isNewVersionAvailable(currentVersion: String, latestVersion: String): Boolean {
        return try {
            val current = parseVersion(currentVersion)
            val latest = parseVersion(latestVersion)
            
            // Bandingkan major, minor, patch
            when {
                latest.major > current.major -> true
                latest.major == current.major && latest.minor > current.minor -> true
                latest.major == current.major && latest.minor == current.minor && latest.patch > current.patch -> true
                else -> false
            }
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
        return Version(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }
    
    /**
     * Tampilkan dialog update
     */
    private fun showUpdateDialog(latestVersion: String) {
        val dialog = AlertDialog.Builder(context, R.style.RoundDialogTheme)
            .setTitle("🔄 Update Tersedia")
            .setMessage("""
                Versi terbaru $latestVersion tersedia!
                
                📱 Fitur baru dan perbaikan bug
                🔧 Performa yang lebih baik
                🛡️ Keamanan yang ditingkatkan
                
                Apakah Anda ingin mengunduh dan menginstall update?
            """.trimIndent())
            .setPositiveButton("📥 Download & Install") { _, _ ->
                downloadAndInstallUpdate(latestVersion)
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
     * Download dan install update
     */
    private fun downloadAndInstallUpdate(latestVersion: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Setup notification channel
                setupNotificationChannel()
                
                // Show download started notification
                showDownloadNotification(0, "Memulai download...")
                
                // Get download URL
                val downloadUrl = getDownloadUrlFromGitHub()
                Log.d(TAG, "Download URL: $downloadUrl")
                
                // Download APK
                val apkFile = downloadApk(downloadUrl, latestVersion)
                
                // Show download completed notification
                showDownloadNotification(100, "Download selesai!")
                
                // Show install confirmation dialog
                withContext(Dispatchers.Main) {
                    showInstallConfirmationDialog(apkFile, latestVersion)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showDownloadErrorDialog(e.message ?: "Unknown error")
                }
            }
        }
    }
    
    /**
     * Download APK file
     */
    private suspend fun downloadApk(downloadUrl: String, version: String): File {
        return withContext(Dispatchers.IO) {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = connection.contentLength
                    val inputStream = connection.inputStream
                    
                    // Create download directory
                    val downloadDir = File(context.getExternalFilesDir(null), "downloads")
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs()
                    }
                    
                    // Create APK file
                    val apkFile = File(downloadDir, "CekPicklist-v$version.apk")
                    val outputStream = FileOutputStream(apkFile)
                    
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress notification
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength)
                            showDownloadNotification(progress, "Downloading... ${progress}%")
                        }
                    }
                    
                    outputStream.close()
                    inputStream.close()
                    
                    Log.d(TAG, "APK downloaded successfully: ${apkFile.absolutePath}")
                    apkFile
                } else {
                    throw Exception("Download failed with response code: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    /**
     * Setup notification channel
     */
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for app updates"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show download progress notification
     */
    private fun showDownloadNotification(progress: Int, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔄 Download Update")
            .setContentText(message)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Show install confirmation dialog
     */
    private fun showInstallConfirmationDialog(apkFile: File, version: String) {
        val dialog = AlertDialog.Builder(context, R.style.RoundDialogTheme)
            .setTitle("✅ Download Selesai")
            .setMessage("""
                APK versi $version berhasil diunduh!
                
                Apakah Anda ingin menginstall update sekarang?
                
                📱 Aplikasi akan restart setelah instalasi
                🔄 Data akan tetap aman
            """.trimIndent())
            .setPositiveButton("🚀 Install Sekarang") { _, _ ->
                installApk(apkFile)
            }
            .setNegativeButton("⏰ Install Nanti") { dialog, _ ->
                dialog.dismiss()
                // Keep notification for later installation
            }
            .setNeutralButton("🗑️ Hapus File") { dialog, _ ->
                apkFile.delete()
                dismissDownloadNotification()
                dialog.dismiss()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Install APK file
     */
    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
            
            // Dismiss notification
            dismissDownloadNotification()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}", e)
            showInstallErrorDialog(e.message ?: "Installation failed")
        }
    }
    
    /**
     * Show download error dialog
     */
    private fun showDownloadErrorDialog(message: String) {
        val dialog = AlertDialog.Builder(context, R.style.RoundDialogTheme)
            .setTitle("❌ Download Gagal")
            .setMessage("""
                Gagal mengunduh update:
                $message
                
                Silakan coba lagi atau download manual dari GitHub.
            """.trimIndent())
            .setPositiveButton("🌐 Buka GitHub") { _, _ ->
                openDownloadPage()
            }
            .setNegativeButton("❌ Tutup") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Show install error dialog
     */
    private fun showInstallErrorDialog(message: String) {
        val dialog = AlertDialog.Builder(context, R.style.RoundDialogTheme)
            .setTitle("❌ Install Gagal")
            .setMessage("""
                Gagal menginstall update:
                $message
                
                Pastikan "Install from unknown sources" diaktifkan di Settings.
            """.trimIndent())
            .setPositiveButton("⚙️ Buka Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("❌ Tutup") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Open app settings
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings: ${e.message}", e)
        }
    }
    
    /**
     * Dismiss download notification
     */
    private fun dismissDownloadNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    /**
     * Buka halaman download (fallback)
     */
    private fun openDownloadPage() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://github.com/koden01/cekpicklist/releases/latest")
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
     * Data class untuk versi
     */
    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int
    )
}
