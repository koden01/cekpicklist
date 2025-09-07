package com.example.cekpicklist

import android.app.Application
import android.util.Log
import com.example.cekpicklist.utils.ToastUtils

class CekPicklistApplication : Application() {
    
    companion object {
        private const val TAG = "CekPicklistApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Reset toast counter saat aplikasi dimulai
        ToastUtils.resetToastCounter()
        
        // Log aplikasi dimulai dengan tag yang unik
        Log.e(TAG, "=== APLIKASI DIMULAI ===")
        Log.e(TAG, "Package: ${packageName}")
        
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            Log.e(TAG, "Version: $versionName")
        } catch (e: Exception) {
            Log.e(TAG, "Version: unknown")
        }
        
        // Set up global exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            throwable.printStackTrace()
        }
        
        Log.e(TAG, "=== APLIKASI SIAP ===")
    }
}
