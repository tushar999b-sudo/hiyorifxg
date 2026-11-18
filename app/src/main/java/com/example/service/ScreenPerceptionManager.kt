package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

data class ScreenPerceptionResult(
    val source: String,
    val summary: String,
    val ocrText: String = "",
    val screenshotBase64: String? = null
)

class ScreenPerceptionManager(private val context: Context) {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private var mediaProjection: MediaProjection? = null

    private val _hasMediaProjection = MutableStateFlow(false)
    val hasMediaProjection: StateFlow<Boolean> = _hasMediaProjection.asStateFlow()

    private val _lastScreenSummary = MutableStateFlow("No screen perception captured yet.")
    val lastScreenSummary: StateFlow<String> = _lastScreenSummary.asStateFlow()

    fun initMediaProjection(resultCode: Int, data: Intent) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection?.stop()
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        _hasMediaProjection.value = mediaProjection != null
    }

    fun releaseMediaProjection() {
        mediaProjection?.stop()
        mediaProjection = null
        _hasMediaProjection.value = false
    }

    suspend fun perceiveScreen(includeVisionBase64: Boolean = false): ScreenPerceptionResult {
        val accService = HiyoriAccessibilityService.instance
        val nodeTree = accService?.getScreenTreeSummary().orEmpty()

        var ocrText = ""
        var base64Img: String? = null

        val bitmap = captureMediaProjectionBitmap()
        if (bitmap != null) {
            ocrText = runMlKitOcr(bitmap)
            if (includeVisionBase64) {
                base64Img = bitmapToBase64Jpeg(bitmap)
            }
            bitmap.recycle()
        }

        val combined = buildString {
            if (nodeTree.isNotBlank() && !nodeTree.startsWith("Accessibility node tree is empty")) {
                append(nodeTree)
            } else {
                append("Accessibility Service not active or node tree empty.")
            }
            if (ocrText.isNotBlank()) {
                append("\n\n[ML Kit OCR Text]:\n")
                append(ocrText.take(1200))
            }
            val notifs = HiyoriNotificationService.notifications.value.take(3)
            if (notifs.isNotEmpty()) {
                append("\n\n[Recent Notifications]:\n")
                notifs.forEach { n ->
                    append("- ${n.title}: ${n.text}\n")
                }
            }
        }.trim()

        _lastScreenSummary.value = combined
        return ScreenPerceptionResult(
            source = when {
                nodeTree.isNotBlank() && ocrText.isNotBlank() -> "Accessibility + ML Kit OCR"
                nodeTree.isNotBlank() -> "Accessibility Node Tree"
                ocrText.isNotBlank() -> "MediaProjection + ML Kit OCR"
                else -> "Fallback Context"
            },
            summary = combined,
            ocrText = ocrText,
            screenshotBase64 = base64Img
        )
    }

    private suspend fun captureMediaProjectionBitmap(): Bitmap? {
        val projection = mediaProjection ?: return null
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtMost(1080)
        val height = metrics.heightPixels.coerceAtMost(2400)
        val density = metrics.densityDpi

        return withTimeoutOrNull(2500L) {
            val deferred = CompletableDeferred<Bitmap?>()
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            var virtualDisplay: VirtualDisplay? = null

            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bmp = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    image.close()
                    virtualDisplay?.release()
                    reader.close()
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                    if (cropped !== bmp) bmp.recycle()
                    deferred.complete(cropped)
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = projection.createVirtualDisplay(
                "HiyoriScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )

            deferred.await()
        }
    }

    suspend fun runMlKitOcr(bitmap: Bitmap): String {
        val deferred = CompletableDeferred<String>()
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                deferred.complete(visionText.text.trim())
            }
            .addOnFailureListener {
                deferred.complete("")
            }
        return withTimeoutOrNull(4000L) { deferred.await() }.orEmpty()
    }

    private suspend fun bitmapToBase64Jpeg(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
