package com.example.cekpicklist.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * Duplikasi dari UhfLocationCanvasView.java dari demo UHF
 * Menampilkan bar chart untuk location data
 */
class UhfLocationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "UhfLocationCanvasView"
    }

    private var valueTop = 0f
    private val paint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.BLUE
        isFakeBoldText = true
        textSize = 16f
    }
    
    private val peripheryPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        color = Color.RED
        isFakeBoldText = true
        textSize = 16f
    }

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == 0) {
            invalidate()
        }
        true
    }

    private var width = 100
    private var left = 0
    private var right = 0
    private var top = 0
    private var bottom = 0

    fun clean() {
        setData(0)
    }
    
    // Method clean() dengan parameter canvas untuk internal use
    private fun clean(canvas: Canvas?) {
        if (canvas != null) {
            // Clear canvas
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawPaint(paint)
            canvas.drawARGB(255, 255, 255, 255)
            
            // Reset paint
            paint.reset()
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = true
            paint.color = Color.BLUE
            paint.isFakeBoldText = true
            paint.textSize = 16f
        }
    }

    fun setData(value: Int) {
        val adjustedValue = if (value < 0) 0 else value
        valueTop = (100 - adjustedValue) * ((bottom - top) / 100f)
        handler.sendEmptyMessage(0) // Send empty message to refresh
    }

    override fun onDraw(canvas: Canvas) {
        // super.onDraw(canvas) // ← HAPUS ini seperti demo UHF
        
        bottom = measuredHeight
        left = measuredWidth / 2 - width / 2
        right = measuredWidth / 2 + width / 2
        
        clean(canvas)
        // Pastikan default kosong: jika valueTop belum ter-inisialisasi (0) saat ukuran belum tersedia
        if (valueTop == 0f) {
            valueTop = bottom.toFloat()
        }
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), peripheryPaint)
        canvas.drawRect(left.toFloat(), valueTop, right.toFloat(), bottom.toFloat(), paint)
    }
}
