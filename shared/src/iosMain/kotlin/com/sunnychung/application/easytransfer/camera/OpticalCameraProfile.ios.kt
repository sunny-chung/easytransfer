package com.sunnychung.application.easytransfer.camera

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVFrameRateRange
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.position
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions

@OptIn(ExperimentalForeignApi::class)
internal actual fun supportedOpticalCameraWidths(
    captureFps: OpticalCaptureFps,
): List<OpticalCameraWidth> {
    val frameRate = captureFps.framesPerSecond.toDouble()
    val device = preferredBackCameraDevice()
        ?: return OpticalCameraWidth.defaults

    return device.formats
        .asSequence()
        .filterIsInstance<AVCaptureDeviceFormat>()
        .filter { format -> format.supportsFrameRate(frameRate) }
        .mapNotNull { format -> format.videoDimensions() }
        .groupBy { dimensions -> dimensions.width }
        .map { (width, dimensions) ->
            val highestHeight = dimensions.maxOf { it.height }
            OpticalCameraWidth(
                label = width.toString(),
                width = width,
                height = highestHeight,
            )
        }
        .sortedBy { width -> width.width }
        .ifEmpty { OpticalCameraWidth.defaults }
}

internal actual fun supportedOpticalDecodeWorkers(): List<OpticalDecodeWorkers> =
    OpticalDecodeWorkers.entries.toList()

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
private fun AVCaptureDeviceFormat.videoDimensions(): IosVideoDimensions? {
    val description = formatDescription ?: return null
    return CMVideoFormatDescriptionGetDimensions(description).useContents {
        IosVideoDimensions(
            width = maxOf(width, height),
            height = minOf(width, height),
        )
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
