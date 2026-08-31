package com.bilingual.dictionary.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bilingual.dictionary.DictionaryApplication
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.SearchMode
import com.bilingual.dictionary.data.repository.DictionaryRepository
import com.bilingual.dictionary.databinding.ActivityCameraBinding
import com.bilingual.dictionary.ocr.GraphicOverlay
import com.bilingual.dictionary.ocr.OcrGraphic
import com.bilingual.dictionary.ocr.OcrTranslator
import com.bilingual.dictionary.ocr.TextAnalyzer
import com.bilingual.dictionary.ui.dialog.WordDetailBottomSheet
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CameraActivity"
        const val EXTRA_SELECTED_WORD = "extra_selected_word"
    }

    private lateinit var binding: ActivityCameraBinding
    private lateinit var repository: DictionaryRepository
    private lateinit var ocrTranslator: OcrTranslator
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var textAnalyzer: TextAnalyzer? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isTorchOn = false
    private var isLiveMode = true

    private var tts: TextToSpeech? = null
    private val staticRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限以进行取景与拍照翻译", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processGalleryImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityCameraBinding.inflate(layoutInflater)
            setContentView(binding.root)

            repository = (application as DictionaryApplication).repository
            ocrTranslator = OcrTranslator(repository)
            cameraExecutor = Executors.newSingleThreadExecutor()

            initTts()
            setupUI()

            if (allPermissionsGranted()) {
                startCamera()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}")
        }
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.w(TAG, "TTS init error: ${e.message}")
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Flashlight Toggle
        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        // Camera Lens Switch
        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindCameraUseCases()
        }

        // Mode Switching (Live AR vs Photo / Gallery)
        binding.chipGroupCameraMode.setOnCheckedStateChangeListener { _, checkedIds ->
            isLiveMode = checkedIds.contains(binding.chipModeLive.id)
            updateModeUI()
        }

        // Gallery import button
        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Shutter Button (Capture photo)
        binding.btnShutter.setOnClickListener {
            takePhoto()
        }

        // Retake Button
        binding.btnRetake.setOnClickListener {
            resumeLivePreview()
        }

        // Tap-to-look-up on GraphicOverlay
        binding.graphicOverlay.onGraphicClickListener = { graphic ->
            handleGraphicClick(graphic)
        }
    }

    private fun updateModeUI() {
        binding.graphicOverlay.clear()
        ocrTranslator.clearCache()
        // Always reset back to live preview when switching modes
        binding.ivCapturedPhoto.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.btnRetake.visibility = View.GONE

        if (isLiveMode) {
            binding.tvCameraTitle.text = "实时取景 AR 翻译"
            binding.tvLiveHint.visibility = View.VISIBLE
            binding.tvLiveHint.text = "将镜头对准英语或马来语单词，点击浮框可查详细释义"
            binding.btnShutter.visibility = View.GONE
            binding.btnGallery.visibility = View.VISIBLE
        } else {
            binding.tvCameraTitle.text = "拍照 / 相册翻译"
            binding.tvLiveHint.visibility = View.VISIBLE
            binding.tvLiveHint.text = "按下快门拍照或从相册选图，即可提取全文翻译"
            binding.btnShutter.visibility = View.VISIBLE
            binding.btnGallery.visibility = View.VISIBLE
        }
    }

    private fun handleGraphicClick(graphic: OcrGraphic) {
        lifecycleScope.launch {
            val entry = graphic.dictionaryEntry ?: run {
                val results = repository.lookup(graphic.originalText, SearchMode.AUTO_DETECT, offlineOnly = false)
                results.firstOrNull() ?: DictionaryEntry(
                    id = 0,
                    word = graphic.originalText,
                    displayWord = graphic.originalText,
                    lang = "auto",
                    definition = graphic.translation
                )
            }

            val sheet = WordDetailBottomSheet.newInstance(
                entry = entry,
                tts = tts,
                onFavoriteToggled = {
                    lifecycleScope.launch { repository.toggleFavorite(it) }
                },
                onOpenInMain = { wordToOpen ->
                    val intent = Intent().apply {
                        putExtra(EXTRA_SELECTED_WORD, wordToOpen)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            )
            sheet.show(supportFragmentManager, WordDetailBottomSheet.TAG)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val rotation = binding.previewView.display?.rotation ?: 0
        val targetRatio = androidx.camera.core.AspectRatio.RATIO_16_9

        binding.previewView.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .setTargetAspectRatio(targetRatio)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(targetRatio)
            .setTargetRotation(rotation)
            .build()

        textAnalyzer = TextAnalyzer(
            overlay = binding.graphicOverlay,
            translator = ocrTranslator,
            isLiveAnalysisEnabled = { isLiveMode && binding.ivCapturedPhoto.visibility != View.VISIBLE }
        ).apply {
            setCameraFlipped(lensFacing == CameraSelector.LENS_FACING_FRONT)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetAspectRatio(targetRatio)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, textAnalyzer!!)
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalysis
            )
            isTorchOn = false
            binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding error: ${e.message}")
        }
    }

    private fun toggleFlash() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            isTorchOn = !isTorchOn
            cam.cameraControl.enableTorch(isTorchOn)
            binding.btnFlash.setImageResource(
                if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        } else {
            Toast.makeText(this, "当前镜头不支持闪光灯", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.progressBarOcr.visibility = View.VISIBLE

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    runOnUiThread {
                        if (bitmap != null) {
                            displayAndProcessStaticImage(bitmap)
                        } else {
                            binding.progressBarOcr.visibility = View.GONE
                            Toast.makeText(this@CameraActivity, "照片捕获失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        binding.progressBarOcr.visibility = View.GONE
                        Toast.makeText(this@CameraActivity, "拍照失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun processGalleryImage(uri: Uri) {
        binding.progressBarOcr.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        displayAndProcessStaticImage(bitmap)
                    } else {
                        binding.progressBarOcr.visibility = View.GONE
                        Toast.makeText(this@CameraActivity, "无法读取所选图片", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarOcr.visibility = View.GONE
                    Toast.makeText(this@CameraActivity, "图片载入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayAndProcessStaticImage(bitmap: Bitmap) {
        binding.previewView.visibility = View.GONE
        binding.ivCapturedPhoto.visibility = View.VISIBLE
        binding.ivCapturedPhoto.setImageBitmap(bitmap)

        binding.btnShutter.visibility = View.GONE
        binding.btnRetake.visibility = View.VISIBLE
        binding.tvLiveHint.text = "正在识别并翻译图片内容..."

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        staticRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                processStaticVisionText(visionText, bitmap.width, bitmap.height)
            }
            .addOnFailureListener { e ->
                binding.progressBarOcr.visibility = View.GONE
                Toast.makeText(this, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processStaticVisionText(visionText: Text, imageWidth: Int, imageHeight: Int) {
        lifecycleScope.launch(Dispatchers.Default) {
            val graphics = mutableListOf<OcrGraphic>()
            binding.graphicOverlay.setImageSourceInfo(imageWidth, imageHeight, isFlipped = false, isFitCenter = true)

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val rawText = line.text.trim()
                    if (rawText.length < 2) continue

                    val box = line.boundingBox ?: continue
                    val (translation, entry) = ocrTranslator.translateText(rawText, allowOnline = true)

                    if (translation.isNotEmpty() && translation != rawText) {
                        graphics.add(
                            OcrGraphic(
                                overlay = binding.graphicOverlay,
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
                binding.progressBarOcr.visibility = View.GONE
                binding.graphicOverlay.updateGraphics(graphics)
                binding.tvLiveHint.text = "共识别 ${graphics.size} 处文本，点击任意卡片查看详细释义"
            }
        }
    }

    private fun resumeLivePreview() {
        binding.ivCapturedPhoto.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.graphicOverlay.clear()
        updateModeUI()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null

            val rotation = image.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "imageProxyToBitmap error: ${e.message}")
            null
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            textAnalyzer?.stop()
            staticRecognizer.close()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy cleanup error: ${e.message}")
        }
    }
}
