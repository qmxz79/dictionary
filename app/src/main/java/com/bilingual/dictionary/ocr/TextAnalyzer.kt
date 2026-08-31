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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextAnalyzer(
    private val overlay: GraphicOverlay,
    private val translator: OcrTranslator,
    private val isLiveAnalysisEnabled: () -> Boolean
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "TextAnalyzer"
        private const val THROTTLE_INTERVAL_MS = 250L
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastAnalyzedTimestamp = 0L
    private var isFlipped = false

    fun setCameraFlipped(flipped: Boolean) {
        isFlipped = flipped
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (!isLiveAnalysisEnabled() || currentTimestamp - lastAnalyzedTimestamp < THROTTLE_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Calculate image orientation
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                processTextRecognitionResult(visionText, imageWidth, imageHeight)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR recognition error: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processTextRecognitionResult(visionText: Text, imageWidth: Int, imageHeight: Int) {
        scope.launch {
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
                overlay.updateGraphics(graphics)
            }
        }
    }

    fun stop() {
        recognizer.close()
    }
}
