package com.bilingual.dictionary.ocr

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class TextAnalyzer(
    private val overlay: GraphicOverlay,
    private val translator: OcrTranslator,
    private val isLiveAnalysisEnabled: () -> Boolean
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "TextAnalyzer"
        private const val THROTTLE_INTERVAL_MS = 300L
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastAnalyzedTimestamp = 0L
    private var isFlipped = false
    private val isClosed = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)

    fun setCameraFlipped(flipped: Boolean) {
        isFlipped = flipped
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get()) {
            imageProxy.close()
            return
        }

        val currentTimestamp = System.currentTimeMillis()
        if (!isLiveAnalysisEnabled()
            || currentTimestamp - lastAnalyzedTimestamp < THROTTLE_INTERVAL_MS
            || isProcessing.get()
        ) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp
        isProcessing.set(true)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Calculate image orientation after rotation
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                if (!isClosed.get()) {
                    processTextRecognitionResult(visionText, imageWidth, imageHeight)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR recognition error: ${e.message}")
                isProcessing.set(false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processTextRecognitionResult(visionText: Text, imageWidth: Int, imageHeight: Int) {
        scope.launch {
            try {
                val graphics = mutableListOf<OcrGraphic>()

                overlay.setImageSourceInfo(imageWidth, imageHeight, isFlipped)

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val rawText = line.text.trim()
                        if (rawText.length < 2) continue

                        val box = line.boundingBox ?: continue
                        val (translation, entry) = translator.translateText(rawText)

                        if (translation.isNotEmpty() && translation != rawText) {
                            graphics.add(
                                OcrGraphic(
                                    overlay = overlay,
                                    originalText = rawText,
                                    translation = translation,
                                    boundingBox = box,
                                    dictionaryEntry = entry
                                )
                            )
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!isClosed.get()) {
                        overlay.updateGraphics(graphics)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "processTextRecognitionResult error: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun stop() {
        isClosed.set(true)
        isProcessing.set(false)
        try {
            scope.cancel()
            recognizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "stop() cleanup error: ${e.message}")
        }
    }
}
