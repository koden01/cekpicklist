package com.example.cekpicklist.utils

import android.util.Log

object LogUtils {
    
    // Flag untuk mengontrol logging level
    private const val ENABLE_VERBOSE_LOGGING = false
    private const val ENABLE_DEBUG_LOGGING = true
    private const val ENABLE_INFO_LOGGING = true
    
    fun v(tag: String, message: String) {
        if (ENABLE_VERBOSE_LOGGING) {
            Log.v(tag, message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (ENABLE_DEBUG_LOGGING) {
            Log.d(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (ENABLE_INFO_LOGGING) {
            Log.i(tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
    
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}
