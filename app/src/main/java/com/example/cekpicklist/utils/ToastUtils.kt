package com.example.cekpicklist.utils

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.widget.Toast

object ToastUtils {
    
    /**
     * Menampilkan toast dengan posisi yang lebih tinggi untuk menghindari tombol scan
     * Menggunakan pendekatan yang kompatibel dengan Android modern
     */
    fun showHighToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            val toast = Toast.makeText(context, message, duration)
            
            // Hapus setGravity untuk menghindari warning pada Android modern
            // Pada Android API 30+, setGravity tidak efektif untuk text toast
            // toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 200)
            
            toast.show()
        } catch (e: Exception) {
            // Fallback ke toast normal jika ada error
            Toast.makeText(context, message, duration).show()
        }
    }
    
    /**
     * Menampilkan toast dengan posisi yang lebih tinggi untuk pesan panjang
     */
    fun showHighLongToast(context: Context, message: String) {
        showHighToast(context, message, Toast.LENGTH_LONG)
    }
    
    /**
     * Menampilkan toast normal untuk pesan yang tidak menghalangi tombol
     */
    fun showNormalToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
    
    /**
     * Menampilkan toast dengan frekuensi terbatas untuk menghindari spamming
     * Menggunakan cooldown dan counter untuk mencegah toast berlebihan
     */
    private var lastToastTime = 0L
    private var toastCounter = 0
    private const val TOAST_COOLDOWN = 3000L // 3 detik cooldown
    private const val MAX_TOAST_PER_SESSION = 10 // Maksimal 10 toast per session
    
    fun showHighToastWithCooldown(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val currentTime = System.currentTimeMillis()
        
        // Cek apakah sudah mencapai batas maksimum toast
        if (toastCounter >= MAX_TOAST_PER_SESSION) {
            Log.w("ToastUtils", "Toast limit reached (${MAX_TOAST_PER_SESSION}), skipping: $message")
            return
        }
        
        // Cek cooldown
        if (currentTime - lastToastTime > TOAST_COOLDOWN) {
            try {
                showHighToast(context, message, duration)
                lastToastTime = currentTime
                toastCounter++
                Log.d("ToastUtils", "Toast shown: $message (${toastCounter}/${MAX_TOAST_PER_SESSION})")
            } catch (e: Exception) {
                Log.e("ToastUtils", "Error showing toast: ${e.message}")
            }
        } else {
            Log.d("ToastUtils", "Toast skipped due to cooldown: $message")
        }
    }
    
    /**
     * Reset toast counter (panggil saat aplikasi restart atau activity baru)
     */
    fun resetToastCounter() {
        toastCounter = 0
        lastToastTime = 0L
        Log.d("ToastUtils", "Toast counter reset")
    }
}
