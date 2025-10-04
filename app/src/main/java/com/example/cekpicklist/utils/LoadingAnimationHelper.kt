package com.example.cekpicklist.utils

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.cekpicklist.R

/**
 * Utility class untuk standardisasi animasi loading seperti SwipeRefreshLayout
 * Menggunakan ProgressBar dengan warna dan style yang sama di seluruh aplikasi
 */
object LoadingAnimationHelper {
    
    // Warna yang sama dengan SwipeRefreshLayout di MainActivity
    private val LOADING_COLORS = intArrayOf(
        android.R.color.holo_blue_bright,
        android.R.color.holo_green_light,
        android.R.color.holo_orange_light,
        android.R.color.holo_red_light
    )
    
    /**
     * Tampilkan loading dialog dengan animasi seperti SwipeRefreshLayout
     * @param context Context untuk menampilkan dialog
     * @param message Pesan yang ditampilkan
     * @param cancelable Apakah dialog bisa di-cancel
     * @return AlertDialog yang ditampilkan
     */
    fun showLoadingDialog(
        context: Context,
        message: String = "Loading...",
        cancelable: Boolean = false
    ): androidx.appcompat.app.AlertDialog {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        
        // Set message
        messageText.text = message
        
        // Set progress bar colors seperti SwipeRefreshLayout
        progressBar.indeterminateTintList = ContextCompat.getColorStateList(context, LOADING_COLORS[0])
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(cancelable)
            .create()
        
        // Set background transparan
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        dialog.show()
        return dialog
    }
    
    /**
     * Tampilkan loading overlay dengan animasi seperti SwipeRefreshLayout
     * @param activity Activity untuk menampilkan overlay
     * @param message Pesan yang ditampilkan
     * @return View overlay yang ditampilkan
     */
    fun showLoadingOverlay(
        activity: Activity,
        message: String = "Loading..."
    ): View {
        val overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_loading, null)
        
        val progressBar = overlayView.findViewById<ProgressBar>(R.id.progressBar)
        val messageText = overlayView.findViewById<TextView>(R.id.messageText)
        
        // Set message
        messageText.text = message
        
        // Set progress bar colors seperti SwipeRefreshLayout
        progressBar.indeterminateTintList = ContextCompat.getColorStateList(activity, LOADING_COLORS[0])
        
        // Add overlay to activity
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(overlayView)
        
        return overlayView
    }
    
    /**
     * Sembunyikan loading overlay
     * @param overlayView View overlay yang akan disembunyikan
     */
    fun hideLoadingOverlay(overlayView: View) {
        val parent = overlayView.parent as? ViewGroup
        parent?.removeView(overlayView)
    }
    
    /**
     * Tampilkan loading toast dengan animasi seperti SwipeRefreshLayout
     * @param context Context untuk menampilkan toast
     * @param message Pesan yang ditampilkan
     * @param duration Durasi toast
     */
    fun showLoadingToast(
        context: Context,
        message: String = "Loading...",
        duration: Int = android.widget.Toast.LENGTH_SHORT
    ) {
        val toastView = LayoutInflater.from(context).inflate(R.layout.toast_loading, null)
        
        val progressBar = toastView.findViewById<ProgressBar>(R.id.progressBar)
        val messageText = toastView.findViewById<TextView>(R.id.messageText)
        
        // Set message
        messageText.text = message
        
        // Set progress bar colors seperti SwipeRefreshLayout
        progressBar.indeterminateTintList = ContextCompat.getColorStateList(context, LOADING_COLORS[0])
        
        val toast = android.widget.Toast(context)
        toast.view = toastView
        toast.duration = duration
        toast.show()
    }

    /**
     * Placeholder agar kompatibel dengan pemanggilan hideLoadingToast() lama.
     * Saat ini tidak menyimpan instance toast, jadi method ini no-op.
     */
    fun hideLoadingToast() {
        // No-op (kompatibilitas)
    }
    
    /**
     * Get loading colors yang sama dengan SwipeRefreshLayout
     */
    fun getLoadingColors(): IntArray = LOADING_COLORS
    
    /**
     * Get primary loading color
     */
    fun getPrimaryLoadingColor(context: Context): Int {
        return ContextCompat.getColor(context, LOADING_COLORS[0])
    }
}
