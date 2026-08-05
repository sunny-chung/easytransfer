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
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVFrameRateRange
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreAnimation.CATransaction
import platform.CoreAnimation.kCATransactionDisableActions
import platform.CoreGraphics.CGRect
import platform.CoreImage.CIQRCodeDescriptor
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSObject
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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

    val scanner = remember(cameraSettings) {
        runCatching {
            IosOpticalScanner(
                cameraSettings = cameraSettings,
                onCodeScanned = { code -> currentOnCodeScanned.value(code) },
            )
        }.onFailure { throwable ->
            cameraError = throwable.message ?: "Camera could not start"
        }.getOrNull()
    }

    DisposableEffect(scanner) {
        scanner?.start()
        onDispose { scanner?.stop() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (scanner != null) {
            UIKitView(
                factory = { scanner.previewView },
                modifier = Modifier.fillMaxSize().background(Color.Black),
                onResize = { view: UIView, rect: CValue<CGRect> ->
                    CATransaction.begin()
                    CATransaction.setValue(true, kCATransactionDisableActions)
                    view.layer.setFrame(rect)
                    scanner.previewLayer.setFrame(rect)
                    CATransaction.commit()
                },
            )
            CameraTargetOverlay()
        }
        cameraError?.let { message -> CameraMessage(message = message) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosOpticalScanner(
    cameraSettings: OpticalCameraSettings,
    onCodeScanned: (ByteArray) -> Unit,
) {
    val previewView = UIView()
    private val captureSession = AVCaptureSession()
    val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
    private val delegate = IosQrMetadataDelegate(onCodeScanned)

    init {
        captureSession.sessionPreset = cameraSettings.sessionPreset()
        val device = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == AVCaptureDevicePositionBack }
            ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: error("Back camera is not available")
        configureFrameRate(device, cameraSettings.captureFps.framesPerSecond)
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: error("Camera input could not be created")
        if (captureSession.canAddInput(input)) {
            captureSession.addInput(input)
        } else {
            error("Camera input could not be added")
        }

        val metadataOutput = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(metadataOutput)) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
            metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        } else {
            error("QR metadata output could not be added")
        }

        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewView.layer.addSublayer(previewLayer)
    }

    fun start() {
        captureSession.startRunning()
    }

    fun stop() {
        captureSession.stopRunning()
    }
}

private class IosQrMetadataDelegate(
    private val onCodeScanned: (ByteArray) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        didOutputMetadataObjects
            .asSequence()
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .firstOrNull { it.type == AVMetadataObjectTypeQRCode }
            ?.bytesValue()
            ?.let(onCodeScanned)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVMetadataMachineReadableCodeObject.bytesValue(): ByteArray? {
    val descriptorPayload = (descriptor as? CIQRCodeDescriptor)
        ?.errorCorrectedPayload
        ?.toByteArray()
    if (descriptorPayload != null) return descriptorPayload
    return stringValue?.encodeToByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val pointer = bytes ?: return ByteArray(0)
    return pointer.reinterpret<ByteVar>().readBytes(length.toInt())
}

private fun OpticalCameraSettings.sessionPreset(): String = when (width.width) {
    in 0..960 -> AVCaptureSessionPreset640x480
    in 961..1_280 -> AVCaptureSessionPreset1280x720
    else -> AVCaptureSessionPreset1920x1080
}

@OptIn(ExperimentalForeignApi::class)
private fun configureFrameRate(
    device: AVCaptureDevice,
    framesPerSecond: Int,
) {
    val frameRate = framesPerSecond.toDouble()
    val format = device.formats
        .asSequence()
        .filterIsInstance<AVCaptureDeviceFormat>()
        .firstOrNull { candidate ->
            candidate.videoSupportedFrameRateRanges
                .asSequence()
                .filterIsInstance<AVFrameRateRange>()
                .any { range -> range.minFrameRate <= frameRate && range.maxFrameRate >= frameRate }
        } ?: return
    if (!device.lockForConfiguration(null)) return
    try {
        device.activeFormat = format
        val duration = CMTimeMake(value = 1, timescale = framesPerSecond)
        device.activeVideoMinFrameDuration = duration
        device.activeVideoMaxFrameDuration = duration
    } finally {
        device.unlockForConfiguration()
    }
}
