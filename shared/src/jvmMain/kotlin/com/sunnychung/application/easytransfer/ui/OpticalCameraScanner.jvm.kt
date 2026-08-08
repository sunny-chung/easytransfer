package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.kashif.cameraK.controller.DesktopCameraControllerBuilder
import com.kashif.cameraK.enums.AspectRatio
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.permissions.providePermissions
import com.kashif.cameraK.state.CameraConfiguration
import com.kashif.cameraK.state.CameraKPlugin
import com.kashif.cameraK.state.CameraKState
import com.kashif.cameraK.state.CameraKStateHolder
import com.sunnychung.application.easytransfer.camera.OpticalCameraSettings
import com.sunnychung.application.easytransfer.camera.desktopCameraGrabber
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.awt.image.RescaleOp
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
    val cameraState by rememberDesktopCameraState(
        config = cameraConfiguration,
        cameraSettings = cameraSettings,
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

@Composable
private fun rememberDesktopCameraState(
    config: CameraConfiguration,
    cameraSettings: OpticalCameraSettings,
    setupPlugins: suspend (CameraKStateHolder) -> Unit,
): State<CameraKState> {
    val coroutineScope = rememberCoroutineScope()
    val stateHolder = remember(config, cameraSettings) {
        CameraKStateHolder(
            cameraConfiguration = config,
            controllerFactory = {
                DesktopCameraControllerBuilder()
                    .apply {
                        setImageFormat(config.imageFormat)
                        setDirectory(config.directory)
                        setAspectRatio(config.aspectRatio)
                        setCameraLens(config.cameraLens)
                        setGrabber(desktopCameraGrabber(cameraSettings))
                        config.targetResolution?.let { (width, height) ->
                            setResolution(width, height)
                        }
                    }
                    .build()
            },
            coroutineScope = coroutineScope,
        )
    }

    LaunchedEffect(stateHolder) {
        setupPlugins(stateHolder)
        stateHolder.initialize()
    }

    DisposableEffect(stateHolder) {
        onDispose { stateHolder.shutdown() }
    }

    return stateHolder.cameraState.collectAsState()
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
                put(DecodeHintType.TRY_HARDER, true)
            },
        )
    }

    fun decode(image: BufferedImage): ByteArray? {
        val croppedImage = image.centerQrCrop()
        val enhancedCrop = croppedImage.enhancedForQr()
        return decodeCandidate(croppedImage)
            ?: decodeCandidate(enhancedCrop)
            ?: decodeCandidate(image)
            ?: decodeCandidate(croppedImage.horizontallyFlipped())
            ?: decodeCandidate(enhancedCrop.horizontallyFlipped())
            ?: decodeCandidate(image.horizontallyFlipped())
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

private fun BufferedImage.horizontallyFlipped(): BufferedImage {
    val targetType = if (type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_RGB else type
    val flippedImage = BufferedImage(width, height, targetType)
    val graphics = flippedImage.createGraphics()
    try {
        graphics.drawImage(
            this,
            0,
            0,
            width,
            height,
            width,
            0,
            0,
            height,
            null,
        )
    } finally {
        graphics.dispose()
    }
    return flippedImage
}

private fun BufferedImage.enhancedForQr(): BufferedImage {
    val rgbImage = toRgbImage()
    val sharpened = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    QR_SHARPEN_OP.filter(rgbImage, sharpened)
    val contrasted = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    QR_CONTRAST_OP.filter(sharpened, contrasted)
    return contrasted
}

private fun BufferedImage.toRgbImage(): BufferedImage {
    if (type == BufferedImage.TYPE_INT_RGB) return this
    val rgbImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = rgbImage.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return rgbImage
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
private val QR_SHARPEN_OP = ConvolveOp(
    Kernel(
        3,
        3,
        floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f,
        ),
    ),
)
private val QR_CONTRAST_OP = RescaleOp(1.25f, -16f, null)
