package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.kashif.cameraK.compose.CameraKScreen
import com.kashif.cameraK.compose.rememberCameraKState
import com.kashif.cameraK.enums.AspectRatio
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.permissions.providePermissions
import com.kashif.cameraK.state.CameraConfiguration
import com.kashif.cameraK.state.CameraKPlugin
import com.kashif.cameraK.state.CameraKStateHolder
import com.sunnychung.application.easytransfer.camera.OpticalCameraSettings
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal actual fun OpticalCameraScanner(
    cameraSettings: OpticalCameraSettings,
    onCodeScanned: (ByteArray) -> Unit,
    modifier: Modifier,
) {
    val permissions = providePermissions()
    var hasCameraPermission by remember { mutableStateOf(permissions.hasCameraPermission()) }
    var permissionDenied by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        permissions.RequestCameraPermission(
            onGranted = { hasCameraPermission = true },
            onDenied = { permissionDenied = true },
        )
        CameraMessage(
            message = if (permissionDenied) {
                "Camera access is required. Enable it in system settings and return here."
            } else {
                "Allow camera access to receive optical transfers."
            },
            modifier = modifier,
        )
        return
    }

    val currentOnCodeScanned = rememberUpdatedState(onCodeScanned)
    val opticalQrPlugin = remember(cameraSettings) {
        JvmOpticalQrScannerPlugin(
            cameraSettings = cameraSettings,
            onCodeScanned = { code -> currentOnCodeScanned.value(code) },
        )
    }
    val cameraConfiguration = remember(cameraSettings) {
        CameraConfiguration(
            cameraLens = CameraLens.BACK,
            aspectRatio = AspectRatio.RATIO_4_3,
            targetResolution = cameraSettings.targetWidth to cameraSettings.targetHeight,
        )
    }
    val cameraState by rememberCameraKState(
        config = cameraConfiguration,
        setupPlugins = { stateHolder -> stateHolder.attachPlugin(opticalQrPlugin) },
    )
    CameraKScreen(
        cameraState = cameraState,
        modifier = modifier,
        loadingContent = { CameraMessage("Starting camera") },
        errorContent = { CameraMessage(it.message ?: "Camera could not start") },
        overlay = { CameraTargetOverlay() },
    ) {}
}

private class JvmOpticalQrScannerPlugin(
    cameraSettings: OpticalCameraSettings,
    private val onCodeScanned: (ByteArray) -> Unit,
) : CameraKPlugin {
    private val frameIntervalNanos = 1_000_000_000L / cameraSettings.captureFps.framesPerSecond
    private val decodeWorkerCount = cameraSettings.decodeWorkers.workerCount
    private val inFlightDecodes = AtomicInteger(0)
    private var scannerJob: Job? = null
    private var lastDecodeStartedAt = 0L

    override fun onAttach(stateHolder: CameraKStateHolder) {
        scannerJob = stateHolder.pluginScope.launch(Dispatchers.Default) {
            val controller = stateHolder.getReadyCameraController()
            controller?.frameFlow?.collect { image ->
                val now = System.nanoTime()
                if (now - lastDecodeStartedAt < frameIntervalNanos) return@collect
                if (inFlightDecodes.get() >= decodeWorkerCount) return@collect
                lastDecodeStartedAt = now
                inFlightDecodes.incrementAndGet()
                stateHolder.pluginScope.launch(Dispatchers.Default) {
                    try {
                        JvmByteQrDecoder().decode(image)?.let { code ->
                            stateHolder.pluginScope.launch { onCodeScanned(code) }
                        }
                    } finally {
                        inFlightDecodes.decrementAndGet()
                    }
                }
            }
        }
    }

    override fun onDetach() {
        val job = scannerJob ?: return
        scannerJob = null
        job.cancel()
    }
}

private class JvmByteQrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            },
        )
    }

    fun decode(image: BufferedImage): ByteArray? {
        val croppedImage = image.centerQrCrop()
        return decodeCandidate(croppedImage) ?: decodeCandidate(image)
    }

    private fun decodeCandidate(image: BufferedImage): ByteArray? {
        val source = BufferedImageLuminanceSource(image)
        val result = decode(BinaryBitmap(GlobalHistogramBinarizer(source)))
            ?: decode(BinaryBitmap(HybridBinarizer(source)))
        return result?.byteModePayload() ?: result?.text?.toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun decode(bitmap: BinaryBitmap): Result? = try {
        reader.decodeWithState(bitmap)
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}

private fun BufferedImage.centerQrCrop(): BufferedImage {
    val side = (min(width, height) * CENTER_CROP_RATIO).toInt().coerceAtLeast(MINIMUM_CROP_SIZE)
    if (side >= width || side >= height) return this
    val x = (width - side) / 2
    val y = (height - side) / 2
    return getSubimage(x, y, side, side)
}

private fun Result.byteModePayload(): ByteArray? {
    val segments = resultMetadata?.get(ResultMetadataType.BYTE_SEGMENTS) as? Iterable<*> ?: return null
    val byteSegments = segments.filterIsInstance<ByteArray>()
    if (byteSegments.isEmpty()) return null
    val totalSize = byteSegments.sumOf { it.size }
    val output = ByteArray(totalSize)
    var offset = 0
    byteSegments.forEach { segment ->
        segment.copyInto(output, destinationOffset = offset)
        offset += segment.size
    }
    return output
}

private const val CENTER_CROP_RATIO = 0.82
private const val MINIMUM_CROP_SIZE = 360
