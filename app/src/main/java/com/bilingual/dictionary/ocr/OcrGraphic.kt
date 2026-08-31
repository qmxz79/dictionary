package com.bilingual.dictionary.ocr

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.bilingual.dictionary.data.model.DictionaryEntry
import kotlin.math.max

class OcrGraphic(
    private val overlay: GraphicOverlay,
    val originalText: String,
    val translation: String,
    val boundingBox: Rect,
    val dictionaryEntry: DictionaryEntry? = null
) {
    // drawRect is computed at draw-time to tightly cover original text position
    private var drawRect: RectF = RectF()

    private val bgPaint = Paint().apply {
        // Semi-transparent frosted dark slate (~75% opacity)
        color = Color.argb(190, 15, 23, 42)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        color = Color.argb(220, 56, 189, 248) // Sky Blue subtle accent border
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textTransPaint = Paint().apply {
        color = Color.WHITE
        isFakeBoldText = true
        isAntiAlias = true
    }

    fun contains(x: Float, y: Float): Boolean {
        return drawRect.contains(x, y)
    }

    /**
     * Draws the translated text directly over the original text bounding box
     * in a clean, semi-transparent frosted bubble without blocking surrounding content.
     */
    fun draw(canvas: Canvas, overlay: GraphicOverlay) {
        val screenRect = overlay.mapRect(boundingBox)

        // Guard: if overlay dimensions are not ready, skip
        if (screenRect.width() <= 0f && screenRect.height() <= 0f) return

        // Auto-scale font size to fit within original line height
        val fontSize = max(20f, (screenRect.height() * 0.72f).coerceAtMost(38f))
        textTransPaint.textSize = fontSize

        val textWidth = textTransPaint.measureText(translation)
        val paddingH = 10f
        val paddingV = 4f

        val targetWidth = max(screenRect.width(), textWidth + paddingH * 2)
        val targetHeight = max(screenRect.height(), fontSize + paddingV * 2)

        val centerX = screenRect.centerX()
        val centerY = screenRect.centerY()

        val left = centerX - targetWidth / 2f
        val top = centerY - targetHeight / 2f
        val right = left + targetWidth
        val bottom = top + targetHeight

        drawRect.set(left, top, right, bottom)

        // Draw translucent rounded bubble directly over original text
        canvas.drawRoundRect(drawRect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(drawRect, 8f, 8f, strokePaint)

        // Center translated text vertically and horizontally
        val fontMetrics = textTransPaint.fontMetrics
        val textY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val textX = left + paddingH

        canvas.drawText(translation, textX, textY, textTransPaint)
    }
}
