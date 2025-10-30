package com.example.cekpicklist.utils

import android.content.Context
import android.widget.Toast

/**
 * UIHelper utility class untuk menampilkan pesan error dan toast
 * Sama seperti UIHelper di demo UHF asli
 */
object UIHelper {
    
    /**
     * Menampilkan toast message dengan string resource
     */
    fun ToastMessage(context: Context, resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Menampilkan toast message dengan string langsung
     */
    fun ToastMessage(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
