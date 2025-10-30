package com.example.cekpicklist.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * Duplikasi dari CircleSeekBar.java dari demo UHF
 * Custom SeekBar dengan tampilan circle dan ruler
 */
class CircleSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    /**
     * Draw style: 0 for circle, 1 for ruler
     */
    private var drawStyle = 1

    /**
     * Whether to draw text
     */
    private var drawText = false

    /**
     * Ruler line paint
     */
    private var mRulerPaint: Paint

    /**
     * Circle paint
     */
    private var mCirclePaint: Paint

    /**
     * Text paint
     */
    private var mTestPaint: Paint

    /**
     * Number of divisions, equal to ruler lines count plus 1
     */
    private var mCount = 10

    /**
     * Width of each ruler line
     */
    private var mRulerWidth = 2

    /**
     * Ruler line color
     */
    private var mRulerColor = Color.WHITE

    /**
     * Circle color
     */
    private var mCircleColor = Color.WHITE

    /**
     * Text color
     */
    private var mTextColor = Color.WHITE

    /**
     * Whether to show ruler lines above thumb
     */
    private var isShowTopOfThumb = false

    init {
        // Create ruler line paint
        mRulerPaint = Paint().apply {
            color = mRulerColor
            isAntiAlias = true
        }

        // Create circle paint
        mCirclePaint = Paint().apply {
            color = mCircleColor
            isAntiAlias = true
        }

        // Create text paint
        mTestPaint = Paint().apply {
            textSize = 35f
            color = mTextColor
            isAntiAlias = true
        }

        // For API 21 and above, remove background behind thumb
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            splitTrack = false
        }
    }

    /**
     * Override onDraw method to draw ruler lines
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Limit condition check
        if (width <= 0 || mCount <= 0) {
            return
        }

        // Calculate length of each division
        val length = when (drawStyle) {
            0 -> width / (mCount + 1)
            1 -> (width - paddingLeft - paddingRight - mCount * mRulerWidth) / (mCount + 1)
            else -> 0
        }

        // Calculate top and bottom coordinates of ruler lines
        val rulerTop = height / 2 - minimumHeight / 2
        val rulerBottom = rulerTop + minimumHeight

        val drawY = (rulerTop + rulerBottom) / 2f
        val drawTestY = (rulerTop + rulerBottom) / 2f - 50

        // Get thumb position information
        val thumbRect = thumb?.bounds

        var rulerLeft: Int
        var rulerRight: Int
        var drawX: Float

        // Draw ruler lines
        for (i in 1..mCount) {
            // Calculate left and right coordinates of ruler line
            when (drawStyle) {
                0 -> {
                    rulerLeft = i * length
                    rulerRight = rulerLeft
                    drawX = rulerLeft.toFloat()
                }
                1 -> {
                    rulerLeft = i * length + paddingLeft
                    rulerRight = rulerLeft + mRulerWidth
                    drawX = (rulerLeft + rulerRight) / 2f
                }
                else -> {
                    rulerLeft = 0
                    rulerRight = 0
                    drawX = 0f
                }
            }

            val drawTestX = rulerLeft - 10f

            // Check if ruler line should be drawn
            if (!isShowTopOfThumb && thumbRect != null && 
                rulerLeft - paddingLeft > thumbRect.left && 
                rulerRight - paddingLeft < thumbRect.right) {
                continue
            }

            // Draw
            when (drawStyle) {
                0 -> canvas.drawCircle(drawX, drawY, 8.0f, mCirclePaint)
                1 -> canvas.drawRect(
                    rulerLeft.toFloat(), 
                    rulerTop.toFloat(), 
                    rulerRight.toFloat(), 
                    rulerBottom.toFloat(), 
                    mRulerPaint
                )
            }

            // Draw text if needed
            if (drawText) {
                canvas.drawText((i - 1).toString(), drawTestX, drawTestY, mTestPaint)
            }
        }
    }

    /**
     * Set number of divisions
     */
    fun setCount(count: Int) {
        this.mCount = count
        requestLayout()
    }

    /**
     * Set draw style
     */
    fun setDrawStyle(style: Int) {
        this.drawStyle = style
        requestLayout()
    }

    /**
     * Set whether to draw text
     */
    fun setDrawText(drawText: Boolean) {
        this.drawText = drawText
        requestLayout()
    }

    /**
     * Set ruler line width in pixels
     */
    fun setRulerWidth(rulerWidth: Int) {
        this.mRulerWidth = rulerWidth
        requestLayout()
    }

    /**
     * Set ruler line color
     */
    fun setRulerColor(rulerColor: Int) {
        this.mRulerColor = rulerColor
        mRulerPaint.color = rulerColor
        requestLayout()
    }

    /**
     * Set circle color
     */
    fun setCircleColor(circleColor: Int) {
        this.mCircleColor = circleColor
        mCirclePaint.color = circleColor
        requestLayout()
    }

    /**
     * Set text color
     */
    fun setTextColor(textColor: Int) {
        this.mTextColor = textColor
        mTestPaint.color = textColor
        requestLayout()
    }

    /**
     * Set all colors
     */
    fun setAllColor(circleColor: Int, textColor: Int) {
        this.mCircleColor = circleColor
        this.mTextColor = textColor
        mCirclePaint.color = circleColor
        mTestPaint.color = textColor
        requestLayout()
    }

    /**
     * Set whether to show ruler lines above thumb
     */
    fun setShowTopOfThumb(showTopOfThumb: Boolean) {
        this.isShowTopOfThumb = showTopOfThumb
        requestLayout()
    }
}
