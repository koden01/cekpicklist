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
 * Custom view untuk menampilkan hasil locating dalam bentuk bar chart
 * Berdasarkan UhfLocationCanvasView dari demo UHF
 */
class LocationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LocationView"
    }

    // Paint objects
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

    // Location data
    private var locationValue = 0 // 0-100
    private var isValid = false

    // View dimensions
    private var viewWidth = 0
    private var viewHeight = 0
    private var barWidth = 100
    private var barLeft = 0
    private var barRight = 0
    private var barTop = 0
    private var barBottom = 0
    private var valueTop = 0f

    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())

    init {
        Log.d(TAG, "🎯 LocationView initialized")
    }

    /**
     * Clear the location data
     */
    fun clear() {
        setLocationData(0, false)
        Log.d(TAG, "🎯 Location data cleared")
    }

    /**
     * Set location data
     * @param value Distance/strength value (0-100)
     * @param valid Whether the reading is valid
     */
    fun setLocationData(value: Int, valid: Boolean) {
        Log.d(TAG, "🎯 Setting location data: value=$value, valid=$valid")
        
        // Clamp value between 0-100
        val clampedValue = value.coerceIn(0, 100)
        
        locationValue = clampedValue
        isValid = valid
        
        // Calculate the top position of the filled bar
        // Higher value = closer = higher bar
        valueTop = (100 - clampedValue) * (barBottom - barTop) / 100f
        
        // Update UI on main thread
        handler.post {
            invalidate()
        }
    }

    /**
     * Get current location value
     */
    fun getLocationValue(): Int = locationValue

    /**
     * Check if current reading is valid
     */
    fun isValidReading(): Boolean = isValid

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        viewWidth = w
        viewHeight = h
        
        // Calculate bar dimensions
        barBottom = h
        barLeft = (w - barWidth) / 2
        barRight = (w + barWidth) / 2
        barTop = 0
        
        Log.d(TAG, "🎯 View size changed: ${w}x${h}")
        Log.d(TAG, "🎯 Bar dimensions: left=$barLeft, right=$barRight, top=$barTop, bottom=$barBottom")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Clear canvas
        clearCanvas(canvas)
        
        // Draw bar border
        canvas.drawRect(barLeft.toFloat(), barTop.toFloat(), barRight.toFloat(), barBottom.toFloat(), peripheryPaint)
        
        // Draw filled bar based on location value
        if (locationValue > 0) {
            // Change color based on validity
            paint.color = if (isValid) Color.GREEN else Color.YELLOW
            
            canvas.drawRect(
                barLeft.toFloat(), 
                valueTop, 
                barRight.toFloat(), 
                barBottom.toFloat(), 
                paint
            )
        }
        
        // Draw value text
        paint.color = Color.BLACK
        paint.textSize = 24f
        val text = "${locationValue}%"
        val textWidth = paint.measureText(text)
        val textX = (viewWidth - textWidth) / 2f
        val textY = barTop + 40f
        canvas.drawText(text, textX, textY, paint)
        
        // Draw status text
        paint.textSize = 16f
        val statusText = if (isValid) "Valid" else "Invalid"
        val statusTextWidth = paint.measureText(statusText)
        val statusTextX = (viewWidth - statusTextWidth) / 2f
        val statusTextY = textY + 30f
        paint.color = if (isValid) Color.GREEN else Color.RED
        canvas.drawText(statusText, statusTextX, statusTextY, paint)
    }

    /**
     * Clear the canvas
     */
    private fun clearCanvas(canvas: Canvas) {
        // Clear canvas with white background
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawPaint(paint)
        canvas.drawARGB(255, 255, 255, 255)
        
        // Reset paint
        paint.xfermode = null
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        paint.color = Color.BLUE
        paint.isFakeBoldText = true
        paint.textSize = 16f
    }
}
