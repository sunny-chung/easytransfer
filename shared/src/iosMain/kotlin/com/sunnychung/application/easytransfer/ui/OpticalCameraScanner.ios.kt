package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import com.sunnychung.application.easytransfer.camera.OpticalCameraSettings
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionInterruptionEndedNotification
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureSessionRuntimeErrorNotification
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance
import platform.AVFoundation.AVFrameRateRange
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.descriptor
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isExposurePointOfInterestSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isFocusPointOfInterestSupported
import platform.AVFoundation.isLowLightBoostSupported
import platform.AVFoundation.isSmoothAutoFocusSupported
import platform.AVFoundation.isWhiteBalanceModeSupported
import platform.AVFoundation.position
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.setAutomaticallyEnablesLowLightBoostWhenAvailable
import platform.AVFoundation.setExposureMode
import platform.AVFoundation.setExposurePointOfInterest
import platform.AVFoundation.setFocusMode
import platform.AVFoundation.setFocusPointOfInterest
import platform.AVFoundation.setSmoothAutoFocusEnabled
import platform.AVFoundation.setSubjectAreaChangeMonitoringEnabled
import platform.AVFoundation.setWhiteBalanceMode
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.valueForKey
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun OpticalCameraScanner(
    cameraSettings: OpticalCameraSettings,
    onCodeScanned: (ByteArray) -> Unit,
    modifier: Modifier,
) {
    val currentOnCodeScanned = rememberUpdatedState(onCodeScanned)
    var permissionStatus by remember {
        mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo))
    }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) {
            permissionStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (permissionStatus == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    permissionStatus = if (granted) AVAuthorizationStatusAuthorized else AVAuthorizationStatusDenied
                }
            }
        }
    }

    when (permissionStatus) {
        AVAuthorizationStatusDenied,
        AVAuthorizationStatusRestricted,
        -> {
            CameraMessage(
                message = "Camera access is required. Enable it in system settings and return here.",
                modifier = modifier,
                onClick = ::openCameraPermissionSettings,
            )
            return
        }

        AVAuthorizationStatusNotDetermined -> {
            CameraMessage(
                message = "Allow camera access to receive optical transfers.",
                modifier = modifier,
            )
            return
        }
    }

    val scanner = remember {
        runCatching {
            IosOpticalScanner(
                cameraSettings = cameraSettings,
                onCodeScanned = { code -> currentOnCodeScanned.value(code) },
                onError = { message -> cameraError = message },
            )
        }.onFailure { throwable ->
            cameraError = throwable.message ?: "Camera could not start"
        }.getOrNull()
    }

    LaunchedEffect(scanner, cameraSettings) {
        scanner?.applySettings(cameraSettings)
    }

    DisposableEffect(scanner) {
        scanner?.start()
        onDispose { scanner?.shutdown() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (scanner != null) {
            UIKitView(
                factory = { scanner.previewView },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
            CameraTargetOverlay()
        }
        cameraError?.let { message -> CameraMessage(message = message) }
    }
}

private fun openCameraPermissionSettings() {
    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { settingsUrl ->
        UIApplication.sharedApplication.openURL(
            url = settingsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosOpticalScanner(
    cameraSettings: OpticalCameraSettings,
    onCodeScanned: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val captureSession = AVCaptureSession()
    private val sessionQueue = dispatch_queue_create("com.sunnychung.easytransfer.ios-camera-session", null)
    private val notificationCenter = NSNotificationCenter.defaultCenter
    val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
    val previewView: UIView = IosPreviewView(previewLayer)
    private val delegate = IosQrMetadataDelegate(
        cameraSettings = cameraSettings,
        onCodeScanned = onCodeScanned,
    )
    private val notificationObservers = mutableListOf<Any>()
    private val device: AVCaptureDevice
    private var receiverActive = false
    private var appActive = true
    private var isShutdown = false
    private var currentSettings = cameraSettings

    init {
        device = preferredBackCameraDevice()
            ?: error("Back camera is not available")
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: error("Camera input could not be created")
        val metadataOutput = AVCaptureMetadataOutput()

        captureSession.beginConfiguration()
        try {
            if (captureSession.canAddInput(input)) {
                captureSession.addInput(input)
            } else {
                error("Camera input could not be added")
            }
            if (captureSession.canAddOutput(metadataOutput)) {
                captureSession.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(delegate, delegate.callbackQueue)
                metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            } else {
                error("QR metadata output could not be added")
            }
            configureSession(cameraSettings)
        } finally {
            captureSession.commitConfiguration()
        }
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        registerNotifications()
    }

    fun start() {
        receiverActive = true
        startSessionIfNeeded()
    }

    fun applySettings(cameraSettings: OpticalCameraSettings) {
        currentSettings = cameraSettings
        delegate.applySettings(cameraSettings)
        dispatch_async(sessionQueue) {
            if (isShutdown) return@dispatch_async
            runCatching {
                captureSession.beginConfiguration()
                try {
                    configureSession(cameraSettings)
                } finally {
                    captureSession.commitConfiguration()
                }
                if (receiverActive && appActive && !captureSession.running) {
                    captureSession.startRunning()
                }
            }.onFailure { throwable ->
                reportError(throwable.message ?: "Camera settings could not be applied")
            }
        }
    }

    fun shutdown() {
        receiverActive = false
        isShutdown = true
        notificationObservers.forEach { observer ->
            notificationCenter.removeObserver(observer)
        }
        notificationObservers.clear()
        dispatch_async(sessionQueue) {
            if (captureSession.running) {
                captureSession.stopRunning()
            }
        }
    }

    private fun configureSession(cameraSettings: OpticalCameraSettings) {
        cameraSettings.sessionPreset()?.let { preset ->
            if (captureSession.canSetSessionPreset(preset)) {
                captureSession.sessionPreset = preset
            }
        }
        configureCameraFormat(
            device = device,
            targetWidth = cameraSettings.targetWidth,
            targetHeight = cameraSettings.targetHeight,
            framesPerSecond = cameraSettings.captureFps.framesPerSecond,
        )
    }

    private fun startSessionIfNeeded() {
        dispatch_async(sessionQueue) {
            if (!isShutdown && receiverActive && appActive && !captureSession.running) {
                captureSession.startRunning()
            }
        }
    }

    private fun stopSessionIfNeeded() {
        dispatch_async(sessionQueue) {
            if (captureSession.running) {
                captureSession.stopRunning()
            }
        }
    }

    private fun registerNotifications() {
        listOf(
            UIApplicationDidBecomeActiveNotification,
            UIApplicationWillEnterForegroundNotification,
        ).forEach { name ->
            val observer = notificationCenter.addObserverForName(
                name = name,
                `object` = null,
                queue = null,
            ) {
                appActive = true
                applySettings(currentSettings)
                startSessionIfNeeded()
            }
            notificationObservers += observer
        }
        listOf(
            AVCaptureSessionInterruptionEndedNotification,
            AVCaptureSessionRuntimeErrorNotification,
        ).forEach { name ->
            val observer = notificationCenter.addObserverForName(
                name = name,
                `object` = captureSession,
                queue = null,
            ) {
                applySettings(currentSettings)
                startSessionIfNeeded()
            }
            notificationObservers += observer
        }
        listOf(
            UIApplicationWillResignActiveNotification,
            UIApplicationDidEnterBackgroundNotification,
        ).forEach { name ->
            val observer = notificationCenter.addObserverForName(
                name = name,
                `object` = null,
                queue = null,
            ) {
                appActive = false
                stopSessionIfNeeded()
            }
            notificationObservers += observer
        }
    }

    private fun reportError(message: String) {
        dispatch_async(dispatch_get_main_queue()) {
            onError(message)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPreviewView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }
}

private class IosQrMetadataDelegate(
    cameraSettings: OpticalCameraSettings,
    private val onCodeScanned: (ByteArray) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    val callbackQueue = dispatch_queue_create("com.sunnychung.easytransfer.ios-qr-metadata", null)
    private val workerQueues = List(MAX_IOS_QR_DECODE_WORKERS) { index ->
        dispatch_queue_create("com.sunnychung.easytransfer.ios-qr-worker-$index", null)
    }
    private var activeWorkerCount = cameraSettings.decodeWorkers.workerCount.coerceIn(
        minimumValue = 1,
        maximumValue = workerQueues.size,
    )
    private var busyWorkerCount = 0
    private var nextWorkerIndex = 0
    private var lastEmittedCode: ByteArray? = null

    fun applySettings(cameraSettings: OpticalCameraSettings) {
        dispatch_async(callbackQueue) {
            activeWorkerCount = cameraSettings.decodeWorkers.workerCount.coerceIn(
                minimumValue = 1,
                maximumValue = workerQueues.size,
            )
        }
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val metadataObject = didOutputMetadataObjects
            .asSequence()
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstOrNull { it.type == AVMetadataObjectTypeQRCode }
            ?: return
        if (busyWorkerCount >= activeWorkerCount) return
        val metadataSnapshot = metadataObject.toSnapshot()

        busyWorkerCount += 1
        val workerQueue = workerQueues[nextWorkerIndex % activeWorkerCount]
        nextWorkerIndex = (nextWorkerIndex + 1) % activeWorkerCount
        dispatch_async(workerQueue) {
            val code = metadataSnapshot.bytesValue()
            dispatch_async(callbackQueue) {
                busyWorkerCount -= 1
                code
                    ?.takeUnless(::isConsecutiveDuplicate)
                    ?.let(::emitCode)
            }
        }
    }

    private fun emitCode(code: ByteArray) {
        val emittedCode = code.copyOf()
        lastEmittedCode = emittedCode
        dispatch_async(dispatch_get_main_queue()) {
            onCodeScanned(emittedCode)
        }
    }

    private fun isConsecutiveDuplicate(code: ByteArray): Boolean {
        val previousCode = lastEmittedCode ?: return false
        return previousCode.contentEquals(code)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVMetadataMachineReadableCodeObject.toSnapshot(): IosQrMetadataSnapshot {
    val descriptorObject = (descriptor as Any?) as? NSObject
    val descriptorPayload = descriptorObject
        ?.valueForKey("errorCorrectedPayload")
        ?.let { payload -> payload as? NSData }
    val descriptorVersion = descriptorObject
        ?.valueForKey("symbolVersion")
        ?.let { version -> version as? NSNumber }
        ?.intValue
    return IosQrMetadataSnapshot(
        descriptorPayload = descriptorPayload,
        descriptorVersion = descriptorVersion,
        stringValue = stringValue,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosQrMetadataSnapshot.bytesValue(): ByteArray? {
    descriptorPayload
        ?.toByteArray()
        ?.let { payload ->
            runCatching {
                payload.decodeQrByteSegments(symbolVersion = descriptorVersion)
            }.getOrNull()
        }
        ?.let { payload -> return payload }
    return stringValue?.toIso88591ByteArrayOrNull()
}

private data class IosQrMetadataSnapshot(
    val descriptorPayload: NSData?,
    val descriptorVersion: Int?,
    val stringValue: String?,
)

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val pointer = bytes ?: return ByteArray(0)
    return pointer.reinterpret<ByteVar>().readBytes(length.toInt())
}

private fun String.toIso88591ByteArrayOrNull(): ByteArray? {
    if (any { character -> character.code > 0xFF }) return null
    return ByteArray(length) { index -> this[index].code.toByte() }
}

private fun ByteArray.decodeQrByteSegments(symbolVersion: Int?): ByteArray? {
    val version = symbolVersion ?: return null
    if (version !in QR_SYMBOL_VERSION_RANGE) return null
    val bitStream = QrBitStream(this)
    val segments = mutableListOf<ByteArray>()
    while (bitStream.remainingBits >= QR_MODE_BITS) {
        when (val mode = bitStream.readBits(QR_MODE_BITS)) {
            QR_MODE_TERMINATOR -> break
            QR_MODE_NUMERIC -> bitStream.skipNumericSegment(version)
            QR_MODE_ALPHANUMERIC -> bitStream.skipAlphanumericSegment(version)
            QR_MODE_STRUCTURED_APPEND -> bitStream.skipBits(QR_STRUCTURED_APPEND_BITS)
            QR_MODE_BYTE -> {
                val length = bitStream.readBits(version.qrByteCountBits())
                segments += bitStream.readBytes(length)
            }
            QR_MODE_FNC1_FIRST_POSITION,
            QR_MODE_FNC1_SECOND_POSITION,
            -> Unit
            QR_MODE_ECI -> bitStream.skipEciDesignator()
            QR_MODE_KANJI -> bitStream.skipKanjiSegment(version)
            else -> return null
        }
    }
    if (segments.isEmpty()) return null
    return ByteArray(segments.sumOf { segment -> segment.size }).also { output ->
        var offset = 0
        segments.forEach { segment ->
            segment.copyInto(output, destinationOffset = offset)
            offset += segment.size
        }
    }
}

private class QrBitStream(
    private val data: ByteArray,
) {
    private var bitIndex = 0

    val remainingBits: Int
        get() = data.size * Byte.SIZE_BITS - bitIndex

    fun readBits(count: Int): Int {
        if (count < 0 || count > remainingBits) error("QR bitstream ended early")
        var value = 0
        repeat(count) {
            val byteValue = data[bitIndex / Byte.SIZE_BITS].toInt() and 0xFF
            val bitValue = (byteValue shr (Byte.SIZE_BITS - 1 - bitIndex % Byte.SIZE_BITS)) and 1
            value = (value shl 1) or bitValue
            bitIndex += 1
        }
        return value
    }

    fun readBytes(count: Int): ByteArray =
        ByteArray(count) { readBits(Byte.SIZE_BITS).toByte() }

    fun skipBits(count: Int) {
        if (count < 0 || count > remainingBits) error("QR bitstream ended early")
        bitIndex += count
    }

    fun skipNumericSegment(version: Int) {
        val length = readBits(version.qrNumericCountBits())
        skipBits(length / 3 * 10 + numericRemainderBits(length % 3))
    }

    fun skipAlphanumericSegment(version: Int) {
        val length = readBits(version.qrAlphanumericCountBits())
        skipBits(length / 2 * 11 + length % 2 * 6)
    }

    fun skipKanjiSegment(version: Int) {
        val length = readBits(version.qrKanjiCountBits())
        skipBits(length * 13)
    }

    fun skipEciDesignator() {
        val firstByte = readBits(Byte.SIZE_BITS)
        when {
            firstByte and 0x80 == 0 -> Unit
            firstByte and 0xC0 == 0x80 -> skipBits(Byte.SIZE_BITS)
            firstByte and 0xE0 == 0xC0 -> skipBits(Byte.SIZE_BITS * 2)
            else -> error("Unsupported QR ECI designator")
        }
    }
}

private fun Int.qrNumericCountBits(): Int = when (this) {
    in 1..9 -> 10
    in 10..26 -> 12
    else -> 14
}

private fun Int.qrAlphanumericCountBits(): Int = when (this) {
    in 1..9 -> 9
    in 10..26 -> 11
    else -> 13
}

private fun Int.qrByteCountBits(): Int = if (this <= 9) 8 else 16

private fun Int.qrKanjiCountBits(): Int = when (this) {
    in 1..9 -> 8
    in 10..26 -> 10
    else -> 12
}

private fun numericRemainderBits(remainder: Int): Int = when (remainder) {
    0 -> 0
    1 -> 4
    else -> 7
}

private const val QR_MODE_BITS = 4
private const val QR_MODE_TERMINATOR = 0
private const val QR_MODE_NUMERIC = 1
private const val QR_MODE_ALPHANUMERIC = 2
private const val QR_MODE_STRUCTURED_APPEND = 3
private const val QR_MODE_BYTE = 4
private const val QR_MODE_FNC1_FIRST_POSITION = 5
private const val QR_MODE_ECI = 7
private const val QR_MODE_KANJI = 8
private const val QR_MODE_FNC1_SECOND_POSITION = 9
private const val QR_STRUCTURED_APPEND_BITS = 16
private val QR_SYMBOL_VERSION_RANGE = 1..40
private const val MAX_IOS_QR_DECODE_WORKERS = 3

private fun OpticalCameraSettings.sessionPreset(): String? = when (width.width) {
    in 0..960 -> AVCaptureSessionPreset640x480
    in 961..1_280 -> AVCaptureSessionPreset1280x720
    else -> AVCaptureSessionPreset1920x1080
}

private fun preferredBackCameraDevice(): AVCaptureDevice? =
    listOf(
        AVCaptureDeviceTypeBuiltInTripleCamera,
        AVCaptureDeviceTypeBuiltInDualWideCamera,
        AVCaptureDeviceTypeBuiltInDualCamera,
        AVCaptureDeviceTypeBuiltInWideAngleCamera,
    ).firstNotNullOfOrNull { deviceType ->
        AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = deviceType,
            mediaType = AVMediaTypeVideo,
            position = AVCaptureDevicePositionBack,
        )
    }
        ?: AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == AVCaptureDevicePositionBack }
        ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)

@OptIn(ExperimentalForeignApi::class)
private fun configureCameraFormat(
    device: AVCaptureDevice,
    targetWidth: Int,
    targetHeight: Int,
    framesPerSecond: Int,
) {
    val frameRate = framesPerSecond.toDouble()
    val format = device.formats
        .asSequence()
        .filterIsInstance<AVCaptureDeviceFormat>()
        .mapNotNull { candidate ->
            val dimensions = candidate.videoDimensions() ?: return@mapNotNull null
            if (!candidate.supportsFrameRate(frameRate)) return@mapNotNull null
            IosCameraFormatChoice(
                format = candidate,
                width = maxOf(dimensions.width, dimensions.height),
                height = minOf(dimensions.width, dimensions.height),
                targetFrameRate = frameRate,
            )
        }
        .minWithOrNull(
            compareBy<IosCameraFormatChoice>(
                { choice -> if (choice.width >= targetWidth) 0 else 1 },
                { choice -> kotlin.math.abs(choice.width - targetWidth) },
                { choice -> kotlin.math.abs(choice.height - targetHeight) },
                { choice -> kotlin.math.abs(choice.maxFrameRate - frameRate) },
            ),
        )
        ?.format ?: return
    if (!device.lockForConfiguration(null)) return
    try {
        device.activeFormat = format
        val duration = CMTimeMake(value = 1, timescale = framesPerSecond)
        device.activeVideoMinFrameDuration = duration
        device.activeVideoMaxFrameDuration = duration
        device.configureScannerImageControls()
    } finally {
        device.unlockForConfiguration()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVCaptureDevice.configureScannerImageControls() {
    val centerPoint = scannerPointOfInterest()
    if (isFocusPointOfInterestSupported()) {
        setFocusPointOfInterest(centerPoint)
    }
    when {
        isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus) -> {
            setFocusMode(AVCaptureFocusModeContinuousAutoFocus)
        }
        isFocusModeSupported(AVCaptureFocusModeAutoFocus) -> {
            setFocusMode(AVCaptureFocusModeAutoFocus)
        }
    }
    if (isSmoothAutoFocusSupported()) {
        setSmoothAutoFocusEnabled(true)
    }
    setSubjectAreaChangeMonitoringEnabled(true)

    if (isExposurePointOfInterestSupported()) {
        setExposurePointOfInterest(centerPoint)
    }
    if (isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
        setExposureMode(AVCaptureExposureModeContinuousAutoExposure)
    }
    if (isWhiteBalanceModeSupported(AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance)) {
        setWhiteBalanceMode(AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance)
    }
    if (isLowLightBoostSupported()) {
        setAutomaticallyEnablesLowLightBoostWhenAvailable(true)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun scannerPointOfInterest() = CGPointMake(0.5, 0.5)

private data class IosCameraFormatChoice(
    val format: AVCaptureDeviceFormat,
    val width: Int,
    val height: Int,
    val targetFrameRate: Double,
) {
    val maxFrameRate: Double = format.videoSupportedFrameRateRanges
        .asSequence()
        .filterIsInstance<AVFrameRateRange>()
        .filter { range -> range.minFrameRate <= targetFrameRate && range.maxFrameRate >= targetFrameRate }
        .maxOf { range -> range.maxFrameRate }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVCaptureDeviceFormat.videoDimensions(): IosVideoDimensions? {
    val description = formatDescription ?: return null
    return CMVideoFormatDescriptionGetDimensions(description).useContents {
        IosVideoDimensions(width = width, height = height)
    }
}

private fun AVCaptureDeviceFormat.supportsFrameRate(frameRate: Double): Boolean =
    videoSupportedFrameRateRanges
        .asSequence()
        .filterIsInstance<AVFrameRateRange>()
        .any { range ->
            range.minFrameRate <= frameRate && range.maxFrameRate >= frameRate
        }

private data class IosVideoDimensions(
    val width: Int,
    val height: Int,
)
