package com.tianshang.guard.ui.qr

import android.graphics.ImageFormat
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.tianshang.guard.core.quish.QrContent
import com.tianshang.guard.core.quish.QrDecision
import com.tianshang.guard.core.quish.QrCodeDecoder
import com.tianshang.guard.domain.InterceptQrUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QrScanState(
    val scanning: Boolean = true,
    val detected: QrContent? = null,
    val decision: QrDecision? = null,
    val error: String? = null
)

class QrPreviewViewModel(
    private val qrDecoder: QrCodeDecoder,
    private val interceptQrUseCase: InterceptQrUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanState())
    val state: StateFlow<QrScanState> = _state.asStateFlow()

    private var lastScanMs = 0L
    private val scanIntervalMs = 1500L

    fun onQrDetected(raw: String) {
        if (System.currentTimeMillis() - lastScanMs < scanIntervalMs) return
        lastScanMs = System.currentTimeMillis()

        val content = qrDecoder.decode(raw)
        val decision = interceptQrUseCase.execute(raw)
        _state.value = QrScanState(
            scanning = false,
            detected = content,
            decision = decision
        )
    }

    fun resetScan() {
        _state.value = QrScanState()
    }
}

@OptIn(ExperimentalGetImage::class)
class QrImageAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = QRCodeMultiReader()
    private var lastAnalysisMs = 0L
    private val analysisIntervalMs = 500L

    @Suppress("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisMs < analysisIntervalMs) {
            imageProxy.close()
            return
        }

        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        if (image.format != ImageFormat.YUV_420_888 && image.format != ImageFormat.NV21) {
            imageProxy.close()
            return
        }

        val planes = image.planes
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val pixels = IntArray(imageProxy.width * imageProxy.height)

        yBuffer.rewind()
        for (i in pixels.indices) {
            val y = yBuffer.get(i).toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
        }

        try {
            val source = RGBLuminanceSource(imageProxy.width, imageProxy.height, pixels)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeMultiple(bitmap).firstOrNull()
            if (result != null) {
                lastAnalysisMs = now
                onQrDetected(result.text)
            }
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }
}
