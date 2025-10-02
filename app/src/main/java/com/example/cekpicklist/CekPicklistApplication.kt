package com.example.cekpicklist

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.example.cekpicklist.utils.ToastUtils
// import com.example.cekpicklist.utils.PerformanceOptimizer

class CekPicklistApplication : MultiDexApplication() {
    
    companion object {
        private const val TAG = "CekPicklistApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize MultiDex untuk mengatasi masalah classloader
        MultiDex.install(this)
        
        // Performance optimizer di-nonaktifkan (module dihapus)
        
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
        
        // Set up global exception handler dengan optimasi
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            // Jangan print stack trace untuk mengurangi overhead
            // throwable.printStackTrace()
        }
        
        Log.e(TAG, "=== APLIKASI SIAP ===")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Performance optimizer di-nonaktifkan (module dihapus)
    }
}
