package com.sunnychung.application.easytransfer.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun supportedOpticalCameraWidths(
    captureFps: OpticalCaptureFps,
): List<OpticalCameraWidth> {
    val context = LocalContext.current
    return remember(context, captureFps) {
        context.supportedCameraWidths(captureFps)
    }
}

@Composable
internal actual fun supportedOpticalCaptureFps(): List<OpticalCaptureFps> {
    val context = LocalContext.current
    return remember(context) {
        context.supportedCameraFps()
    }
}

internal actual fun supportedOpticalDecodeWorkers(): List<OpticalDecodeWorkers> =
    OpticalDecodeWorkers.entries.toList()

private fun Context.supportedCameraWidths(captureFps: OpticalCaptureFps): List<OpticalCameraWidth> =
    runCatching {
        val characteristics = defaultBackCameraCharacteristics() ?: return OpticalCameraWidth.defaults
        if (!characteristics.supportsFrameRate(captureFps.framesPerSecond)) {
            return OpticalCameraWidth.defaults
        }
        val sizes = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.toList()
            .orEmpty()
        sizes.toCameraWidths()
    }.getOrDefault(OpticalCameraWidth.defaults)

private fun Context.supportedCameraFps(): List<OpticalCaptureFps> =
    runCatching {
        val characteristics = defaultBackCameraCharacteristics() ?: return listOf(OpticalCaptureFps.Fps30)
        val ranges = characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.toList()
            .orEmpty()
        OpticalCaptureFps.entries
            .filter { fps -> ranges.any { range -> range.contains(fps.framesPerSecond) } }
            .ifEmpty { listOf(OpticalCaptureFps.Fps30) }
    }.getOrDefault(listOf(OpticalCaptureFps.Fps30))

private fun Context.defaultBackCameraCharacteristics(): CameraCharacteristics? {
    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
    return cameraManager.cameraIdList
        .asSequence()
        .map { cameraId -> cameraManager.getCameraCharacteristics(cameraId) }
        .firstOrNull { characteristics ->
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        ?: cameraManager.cameraIdList
            .firstOrNull()
            ?.let { cameraId -> cameraManager.getCameraCharacteristics(cameraId) }
}

private fun CameraCharacteristics.supportsFrameRate(framesPerSecond: Int): Boolean =
    get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        ?.any { range -> range.contains(framesPerSecond) } == true

private fun List<Size>.toCameraWidths(): List<OpticalCameraWidth> =
    groupBy { size -> size.width }
        .map { (width, sizes) ->
            OpticalCameraWidth(
                label = width.toString(),
                width = width,
                height = sizes.maxOf { size -> size.height },
            )
        }
        .filter { width -> width.width >= MINIMUM_CAMERA_WIDTH && width.height >= MINIMUM_CAMERA_HEIGHT }
        .sortedBy { width -> width.width }
        .ifEmpty { OpticalCameraWidth.defaults }

private fun Range<Int>.contains(value: Int): Boolean =
    lower <= value && upper >= value

private const val MINIMUM_CAMERA_WIDTH = 640
private const val MINIMUM_CAMERA_HEIGHT = 360
