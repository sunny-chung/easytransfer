package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.kashif.cameraK.permissions.providePermissions
import com.sunnychung.application.easytransfer.camera.OpticalCameraSettings
import com.sunnychung.application.easytransfer.camera.desktopCameraGrabber
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter

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
            onClick = if (permissionDenied) {
                ::openCameraPermissionSettings
            } else {
                null
            },
        )
        return
    }

    val callbackScope = rememberCoroutineScope()
    val currentOnCodeScanned = rememberUpdatedState(onCodeScanned)
    val cameraSession = remember(cameraSettings) {
        DesktopOpticalCameraSession(
            cameraSettings = cameraSettings,
            callbackScope = callbackScope,
            onCodeScanned = { code -> currentOnCodeScanned.value(code) },
        )
    }
    DisposableEffect(cameraSession) {
        cameraSession.start()
        onDispose { cameraSession.close() }
    }
    val cameraState by cameraSession.state.collectAsState()

    Box(modifier = modifier) {
        when (val state = cameraState) {
            DesktopCameraState.Initializing -> CameraMessage("Starting camera")
            is DesktopCameraState.Error -> CameraMessage(state.message)
            is DesktopCameraState.Ready -> {
                state.previewFrame?.let { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = "Camera Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                CameraTargetOverlay()
            }
        }
    }
}

private sealed interface DesktopCameraState {
    data object Initializing : DesktopCameraState
    data class Ready(val previewFrame: ImageBitmap?) : DesktopCameraState
    data class Error(val message: String) : DesktopCameraState
}

private class DesktopOpticalCameraSession(
    private val cameraSettings: OpticalCameraSettings,
    private val callbackScope: CoroutineScope,
    private val onCodeScanned: (ByteArray) -> Unit,
) {
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val cameraDispatcher = singleThreadDispatcher("EasyTransfer optical camera")
    private val decodeDispatcher = fixedThreadDispatcher(
        name = "EasyTransfer optical decoder",
        threadCount = cameraSettings.decodeWorkers.workerCount,
    )
    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + cameraDispatcher)
    private val activeGrabber = AtomicReference<FrameGrabber?>(null)
    private val inFlightDecodes = AtomicInteger(0)
    private var cameraJob: Job? = null

    private val frameIntervalNanos = 1_000_000_000L / cameraSettings.captureFps.framesPerSecond
    private val decodeWorkerCount = cameraSettings.decodeWorkers.workerCount

    private val mutableState = MutableStateFlow<DesktopCameraState>(DesktopCameraState.Initializing)
    val state: StateFlow<DesktopCameraState> = mutableState

    fun start() {
        if (!started.compareAndSet(false, true)) return
        cameraJob = sessionScope.launch {
            val converter = Java2DFrameConverter()
            val grabber = desktopCameraGrabber(cameraSettings)
            activeGrabber.set(grabber)
            try {
                grabber.start()
                mutableState.value = DesktopCameraState.Ready(previewFrame = null)

                var lastPreviewFrameAt = 0L
                var lastDecodeStartedAt = 0L
                while (!closed.get() && isActive) {
                    val frame = try {
                        grabber.grab()
                    } catch (cause: Throwable) {
                        if (closed.get() || !isActive) break
                        throw cause
                    }
                    if (frame?.image == null) continue
                    val image = converter.convert(frame) ?: continue
                    val now = System.nanoTime()

                    if (now - lastPreviewFrameAt >= DESKTOP_PREVIEW_FRAME_INTERVAL_NANOS) {
                        lastPreviewFrameAt = now
                        mutableState.value = DesktopCameraState.Ready(
                            previewFrame = image
                                .previewCopy(DESKTOP_PREVIEW_MAX_DIMENSION)
                                .toComposeImageBitmap(),
                        )
                    }

                    if (now - lastDecodeStartedAt >= frameIntervalNanos &&
                        inFlightDecodes.get() < decodeWorkerCount
                    ) {
                        lastDecodeStartedAt = now
                        decode(image.copyForDecoding())
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                if (!closed.get()) {
                    mutableState.value = DesktopCameraState.Error(
                        message = cause.message ?: "Camera could not start",
                    )
                }
            } finally {
                runCatching { grabber.release() }
                activeGrabber.compareAndSet(grabber, null)
                converter.close()
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        cameraJob?.cancel()
        runCatching { activeGrabber.getAndSet(null)?.release() }
        sessionScope.cancel()
        decodeDispatcher.close()
        cameraDispatcher.close()
    }

    private fun decode(image: BufferedImage) {
        inFlightDecodes.incrementAndGet()
        sessionScope.launch(decodeDispatcher) {
            try {
                JvmByteQrDecoder().decode(image)?.let { code ->
                    callbackScope.launch { onCodeScanned(code) }
                }
            } finally {
                inFlightDecodes.decrementAndGet()
            }
        }
    }
}

private fun singleThreadDispatcher(name: String): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }.asCoroutineDispatcher()

private fun fixedThreadDispatcher(
    name: String,
    threadCount: Int,
): ExecutorCoroutineDispatcher =
    Executors.newFixedThreadPool(threadCount) { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }.asCoroutineDispatcher()

private fun openCameraPermissionSettings() {
    val osName = System.getProperty("os.name").lowercase(Locale.US)
    when {
        "mac" in osName -> startProcess(
            "open",
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera",
        )
        "win" in osName -> startProcess(
            "cmd",
            "/c",
            "start",
            "ms-settings:privacy-webcam",
        )
        else -> startProcess("gnome-control-center", "privacy") ||
            startProcess("xdg-open", "settings://privacy/camera")
    }
}

private fun startProcess(vararg command: String): Boolean =
    runCatching {
        ProcessBuilder(*command).start()
        true
    }.getOrDefault(false)

private fun BufferedImage.copyForDecoding(): BufferedImage = BufferedImage(
    colorModel,
    copyData(null),
    colorModel.isAlphaPremultiplied,
    null,
)

private fun BufferedImage.previewCopy(maxDimension: Int): BufferedImage {
    val longestSide = maxOf(width, height)
    if (longestSide <= maxDimension) {
        return BufferedImage(
            colorModel,
            copyData(null),
            colorModel.isAlphaPremultiplied,
            null,
        )
    }

    val scale = maxDimension.toDouble() / longestSide.toDouble()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val targetType = if (type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_RGB else type
    val preview = BufferedImage(targetWidth, targetHeight, targetType)
    val graphics = preview.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return preview
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
        return decodeCandidate(croppedImage)
            ?: decodeCandidate(image)
            ?: decodeCandidate(croppedImage.horizontallyFlipped())
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
private const val DESKTOP_PREVIEW_FRAME_INTERVAL_NANOS = 1_000_000_000L / 12L
private const val DESKTOP_PREVIEW_MAX_DIMENSION = 960
