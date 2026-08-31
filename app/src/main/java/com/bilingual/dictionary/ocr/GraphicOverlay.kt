package com.bilingual.dictionary.ocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private val graphics = mutableListOf<OcrGraphic>()

    private var imageWidth = 0
    private var imageHeight = 0
    private var isFlipped = false
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

    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean) {
        synchronized(lock) {
            this.imageWidth = imageWidth
            this.imageHeight = imageHeight
            this.isFlipped = isFlipped
            updateTransformationMatrix()
        }
        postInvalidate()
    }

    private fun updateTransformationMatrix() {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return

        transformationMatrix.reset()
        val viewAspectRatio = width.toFloat() / height.toFloat()
        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()

        var scale: Float
        var dx = 0f
        var dy = 0f

        if (viewAspectRatio > imageAspectRatio) {
            // View is wider than image
            scale = width.toFloat() / imageWidth.toFloat()
            dy = (height - imageHeight * scale) / 2f
        } else {
            // View is taller than image
            scale = height.toFloat() / imageHeight.toFloat()
            dx = (width - imageWidth * scale) / 2f
        }

        transformationMatrix.postScale(scale, scale)
        if (isFlipped) {
            transformationMatrix.postScale(-1f, 1f, width / 2f, height / 2f)
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
                graphic.draw(canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            synchronized(lock) {
                for (graphic in graphics.reversed()) {
                    if (graphic.contains(x, y)) {
                        onGraphicClickListener?.invoke(graphic)
                        return true
                    }
                }
            }
        }
        return true
    }
}
