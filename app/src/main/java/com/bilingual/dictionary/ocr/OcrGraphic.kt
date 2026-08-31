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
    private val screenRect: RectF = overlay.mapRect(boundingBox)

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#E61E293B") // Deep slate dark translucent
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        color = Color.parseColor("#3B82F6") // Vibrant Blue accent
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val textOriginalPaint = Paint().apply {
        color = Color.WHITE
        textSize = max(24f, screenRect.height() * 0.42f).coerceAtMost(36f)
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val textTransPaint = Paint().apply {
        color = Color.parseColor("#38BDF8") // Sky Blue for translation
        textSize = max(22f, screenRect.height() * 0.38f).coerceAtMost(34f)
        isAntiAlias = true
    }

    private var drawRect: RectF = RectF()

    fun contains(x: Float, y: Float): Boolean {
        return drawRect.contains(x, y) || screenRect.contains(x, y)
    }

    fun draw(canvas: Canvas) {
        val origWidth = textOriginalPaint.measureText(originalText)
        val transWidth = textTransPaint.measureText(translation)
        val maxTextWidth = max(origWidth, transWidth)

        val paddingH = 16f
        val paddingV = 10f
        val totalWidth = max(screenRect.width(), maxTextWidth + paddingH * 2)
        val totalHeight = max(screenRect.height(), (textOriginalPaint.textSize + textTransPaint.textSize + paddingV * 2.5f))

        val left = screenRect.left
        val top = screenRect.top
        drawRect.set(left, top, left + totalWidth, top + totalHeight)

        // Draw pill background & stroke
        canvas.drawRoundRect(drawRect, 12f, 12f, bgPaint)
        canvas.drawRoundRect(drawRect, 12f, 12f, strokePaint)

        // Draw original text (Line 1)
        val line1Y = drawRect.top + paddingV + textOriginalPaint.textSize * 0.85f
        canvas.drawText(originalText, drawRect.left + paddingH, line1Y, textOriginalPaint)

        // Draw translation text (Line 2)
        val line2Y = line1Y + textTransPaint.textSize + 6f
        canvas.drawText(translation, drawRect.left + paddingH, line2Y, textTransPaint)
    }
}
