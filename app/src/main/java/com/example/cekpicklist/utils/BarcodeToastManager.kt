package com.example.cekpicklist.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.example.cekpicklist.R

/**
 * Toast Manager untuk Barcode Scanner
 * Menduplikasi sistem toast dari web app (sonner)
 */
object BarcodeToastManager {
    
    private const val TAG = "BarcodeToastManager"
    private var currentToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Show success toast (like web app showSuccess)
     * Thread-safe: automatically switches to main thread
     * Custom layout dengan background hijau
     */
    fun showSuccess(context: Context, message: String) {
        mainHandler.post {
            dismissToast()
            
            // Custom toast dengan background hijau
            val inflater = LayoutInflater.from(context)
            val layout = inflater.inflate(R.layout.toast_success, null)
            val textView = layout.findViewById<TextView>(R.id.toast_text)
            textView.text = "✅ $message"
            
            currentToast = Toast(context).apply {
                duration = Toast.LENGTH_SHORT
                view = layout
            }
            currentToast?.show()
            Log.d(TAG, "✅ Success toast (green): $message")
        }
    }
    
    /**
     * Show error toast (like web app showError)
     * Thread-safe: automatically switches to main thread
     * Custom layout dengan background merah
     */
    fun showError(context: Context, message: String) {
        mainHandler.post {
            dismissToast()
            
            // Custom toast dengan background merah
            val inflater = LayoutInflater.from(context)
            val layout = inflater.inflate(R.layout.toast_error, null)
            val textView = layout.findViewById<TextView>(R.id.toast_text)
            textView.text = "❌ $message"
            
            currentToast = Toast(context).apply {
                duration = Toast.LENGTH_LONG
                view = layout
            }
            currentToast?.show()
            Log.d(TAG, "❌ Error toast (red): $message")
        }
    }
    
    /**
     * Show loading toast (like web app showLoading)
     * Thread-safe: automatically switches to main thread
     */
    fun showLoading(context: Context, message: String) {
        mainHandler.post {
            dismissToast()
            currentToast = Toast.makeText(context, "⏳ $message", Toast.LENGTH_LONG)
            currentToast?.show()
            Log.d(TAG, "⏳ Loading toast: $message")
        }
    }
    
    /**
     * Dismiss current toast (like web app dismissToast)
     */
    fun dismissToast() {
        currentToast?.cancel()
        currentToast = null
        Log.d(TAG, "🚫 Toast dismissed")
    }
}
