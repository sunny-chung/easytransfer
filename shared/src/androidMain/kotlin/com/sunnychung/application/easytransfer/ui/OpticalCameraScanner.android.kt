package com.sunnychung.application.easytransfer.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sunnychung.application.easytransfer.camera.OpticalCameraSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import zxingcpp.BarcodeReader as ZxingCppBarcodeReader

@OptIn(ExperimentalCamera2Interop::class)
@Composable
internal actual fun OpticalCameraScanner(
    cameraSettings: OpticalCameraSettings,
    onCodeScanned: (ByteArray) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    val currentOnCodeScanned = rememberUpdatedState(onCodeScanned)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            permissionDenied = !granted
        },
    )

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.hasCameraPermission()
                hasCameraPermission = granted
                if (granted) {
                    permissionDenied = false
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && !permissionDenied) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraMessage(
            message = if (permissionDenied) {
                "Camera access is required. Enable it in system settings and return here."
            } else {
                "Allow camera access to receive optical transfers."
            },
            modifier = modifier,
            onClick = if (permissionDenied) {
                { context.openCameraPermissionSettings() }
            } else {
                null
            },
        )
        return
    }
    if (lifecycleOwner == null) {
        CameraMessage(
            message = "Camera could not find an active lifecycle.",
            modifier = modifier,
        )
        return
    }

    DisposableEffect(context, lifecycleOwner, previewView, cameraSettings) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val qrAnalyzer = ZxingQrAnalyzer(
            decodeWorkerCount = cameraSettings.decodeWorkers.workerCount,
            onCodeScanned = { code -> currentOnCodeScanned.value(code) },
        )
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                cameraProvider.bindOpticalScanner(
                    previewView = previewView,
                    lifecycleOwner = lifecycleOwner,
                    cameraSettings = cameraSettings,
                    analyzerExecutor = analyzerExecutor,
                    analyzer = qrAnalyzer,
                )
            }.onFailure { throwable ->
                cameraError = throwable.message ?: "Camera could not start"
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
            qrAnalyzer.close()
            analyzerExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        CameraTargetOverlay()
        cameraError?.let { message -> CameraMessage(message = message) }
    }
}

@ExperimentalCamera2Interop
private fun ProcessCameraProvider.bindOpticalScanner(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    cameraSettings: OpticalCameraSettings,
    analyzerExecutor: ExecutorService,
    analyzer: ImageAnalysis.Analyzer,
) {
    val targetResolution = Size(cameraSettings.targetWidth, cameraSettings.targetHeight)
    val fpsRange = Range(
        cameraSettings.captureFps.framesPerSecond,
        cameraSettings.captureFps.framesPerSecond,
    )
    val previewBuilder = Preview.Builder()
        .setTargetResolution(targetResolution)
        .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
    Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
        fpsRange,
    )
    val preview = previewBuilder.build().also { preview ->
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    val analysisBuilder = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setTargetResolution(targetResolution)
        .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
    Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(
        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
        fpsRange,
    )
    val analysis = analysisBuilder.build().also { imageAnalysis ->
        imageAnalysis.setAnalyzer(
            analyzerExecutor,
            analyzer,
        )
    }

    bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        analysis,
    )
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

private fun Context.openCameraPermissionSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        startActivity(intent)
    }
}

private class ZxingQrAnalyzer(
    decodeWorkerCount: Int,
    private val onCodeScanned: (ByteArray) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerCount = decodeWorkerCount.coerceAtLeast(1)
    private val busyWorkers = AtomicInteger(0)
    private val decodeExecutor = Executors.newFixedThreadPool(workerCount)
    private val readers = object : ThreadLocal<ZxingCppBarcodeReader>() {
        override fun initialValue(): ZxingCppBarcodeReader = createQrReader()
    }

    override fun analyze(image: ImageProxy) {
        if (busyWorkers.incrementAndGet() > workerCount) {
            busyWorkers.decrementAndGet()
            image.close()
            return
        }
        runCatching {
            decodeExecutor.execute {
                try {
                    decode(image)?.let { value ->
                        mainHandler.post { onCodeScanned(value) }
                    }
                } finally {
                    image.close()
                    busyWorkers.decrementAndGet()
                }
            }
        }.onFailure {
            image.close()
            busyWorkers.decrementAndGet()
        }
    }

    override fun close() {
        decodeExecutor.shutdown()
    }

    private fun decode(image: ImageProxy): ByteArray? {
        val reader = readers.get()
        return runCatching {
            reader.read(image).firstNotNullOfOrNull { result ->
                result.bytes?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }
}

private fun createQrReader(): ZxingCppBarcodeReader = ZxingCppBarcodeReader(
    ZxingCppBarcodeReader.Options(
        formats = setOf(ZxingCppBarcodeReader.Format.QR_CODE),
        maxNumberOfSymbols = 1,
    ),
)
