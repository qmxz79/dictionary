package com.bilingual.dictionary.ocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private val graphics = mutableListOf<OcrGraphic>()

    private var imageWidth = 0
    private var imageHeight = 0
    private var isFlipped = false
    private var isFitCenter = false
    private val transformationMatrix = Matrix()

    var onGraphicClickListener: ((OcrGraphic) -> Unit)? = null

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: OcrGraphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }

    fun updateGraphics(newGraphics: List<OcrGraphic>) {
        synchronized(lock) {
            graphics.clear()
            graphics.addAll(newGraphics)
        }
        postInvalidate()
    }

    /**
     * Set image source dimensions and scaling mode.
     * @param isFitCenter true for static ImageView (FIT_CENTER), false for CameraX PreviewView (FILL_CENTER)
     */
    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean, isFitCenter: Boolean = false) {
        synchronized(lock) {
            this.imageWidth = imageWidth
            this.imageHeight = imageHeight
            this.isFlipped = isFlipped
            this.isFitCenter = isFitCenter
            updateTransformationMatrix()
        }
        postInvalidate()
    }

    private fun updateTransformationMatrix() {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return

        transformationMatrix.reset()

        val scale = if (isFitCenter) {
            // FIT_CENTER: scale so entire image is visible (letterbox/pillarbox)
            min(width.toFloat() / imageWidth.toFloat(), height.toFloat() / imageHeight.toFloat())
        } else {
            // FILL_CENTER: scale so entire view is covered (CameraX PreviewView)
            max(width.toFloat() / imageWidth.toFloat(), height.toFloat() / imageHeight.toFloat())
        }

        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f

        transformationMatrix.postScale(scale, scale)
        if (isFlipped) {
            transformationMatrix.postScale(-1f, 1f, imageWidth * scale / 2f, imageHeight * scale / 2f)
        }
        transformationMatrix.postTranslate(dx, dy)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateTransformationMatrix()
    }

    fun mapRect(sourceRect: Rect): RectF {
        val src = RectF(sourceRect)
        val dst = RectF()
        transformationMatrix.mapRect(dst, src)
        return dst
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            for (graphic in graphics) {
                graphic.draw(canvas, this)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true // Consume to receive ACTION_UP
            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y
                synchronized(lock) {
                    for (graphic in graphics.reversed()) {
                        if (graphic.contains(x, y)) {
                            performClick()
                            onGraphicClickListener?.invoke(graphic)
                            return true
                        }
                    }
                }
                performClick()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
