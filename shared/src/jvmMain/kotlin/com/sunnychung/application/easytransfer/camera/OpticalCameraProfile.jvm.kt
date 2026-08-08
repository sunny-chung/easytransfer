package com.sunnychung.application.easytransfer.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.VideoInputFrameGrabber

@Composable
internal actual fun supportedOpticalCameraWidths(
    captureFps: OpticalCaptureFps,
): List<OpticalCameraWidth> = remember(captureFps) {
    DesktopCameraModeDetector.supportedWidths(captureFps)
}

@Composable
internal actual fun supportedOpticalCaptureFps(): List<OpticalCaptureFps> = remember {
    DesktopCameraModeDetector.supportedFps()
}

internal actual fun supportedOpticalDecodeWorkers(): List<OpticalDecodeWorkers> =
    OpticalDecodeWorkers.entries.toList()

internal fun desktopCameraGrabber(cameraSettings: OpticalCameraSettings): FrameGrabber =
    createDesktopGrabber().apply {
        frameRate = cameraSettings.captureFps.framesPerSecond.toDouble()
        imageWidth = cameraSettings.targetWidth
        imageHeight = cameraSettings.targetHeight
    }

private object DesktopCameraModeDetector {
    private val modesByFps = mutableMapOf<OpticalCaptureFps, List<DesktopCameraMode>>()

    fun supportedWidths(captureFps: OpticalCaptureFps): List<OpticalCameraWidth> =
        modesFor(captureFps)
            .groupBy { mode -> mode.width }
            .map { (width, modes) ->
                val highestHeight = modes.maxOf { mode -> mode.height }
                OpticalCameraWidth(
                    label = width.toString(),
                    width = width,
                    height = highestHeight,
                )
            }
            .sortedBy { width -> width.width }
            .ifEmpty { desktopFallbackWidths() }

    fun supportedFps(): List<OpticalCaptureFps> {
        val supported = OpticalCaptureFps.entries.filter { fps -> supportsAnyMode(fps) }
        return supported.ifEmpty { listOf(OpticalCaptureFps.Fps30) }
    }

    private fun modesFor(captureFps: OpticalCaptureFps): List<DesktopCameraMode> =
        synchronized(modesByFps) {
            modesByFps.getOrPut(captureFps) {
                detectModes(captureFps)
            }
        }

    private fun supportsAnyMode(captureFps: OpticalCaptureFps): Boolean {
        synchronized(modesByFps) {
            modesByFps[captureFps]?.let { modes -> return modes.isNotEmpty() }
        }
        return desktopFpsProbeCandidates().any { candidate ->
            probeMode(candidate, captureFps) != null
        }
    }

    private fun detectModes(captureFps: OpticalCaptureFps): List<DesktopCameraMode> =
        desktopModeCandidates()
            .asSequence()
            .mapNotNull { candidate -> probeMode(candidate, captureFps) }
            .distinct()
            .filter { mode -> mode.width >= MINIMUM_DESKTOP_CAMERA_WIDTH && mode.height >= MINIMUM_DESKTOP_CAMERA_HEIGHT }
            .toList()

    private fun probeMode(
        candidate: DesktopCameraMode,
        captureFps: OpticalCaptureFps,
    ): DesktopCameraMode? {
        val grabber = createDesktopGrabber()
        return runCatching {
            grabber.frameRate = captureFps.framesPerSecond.toDouble()
            grabber.imageWidth = candidate.width
            grabber.imageHeight = candidate.height
            grabber.start()
            val width = grabber.imageWidth
            val height = grabber.imageHeight
            DesktopCameraMode(width = width, height = height)
        }.getOrNull()
            ?.takeIf { mode -> mode.width > 0 && mode.height > 0 }
            .also {
                runCatching { grabber.stop() }
                runCatching { grabber.release() }
            }
    }
}

private fun createDesktopGrabber(): FrameGrabber = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac -> FFmpegFrameGrabber("0").apply {
        format = "avfoundation"
    }
    DesktopOperatingSystem.Windows -> VideoInputFrameGrabber(0)
    DesktopOperatingSystem.Linux -> FFmpegFrameGrabber(bestLinuxVideoDevice())
}

private fun desktopModeCandidates(): List<DesktopCameraMode> = listOf(
    DesktopCameraMode(3_840, 2_160),
    DesktopCameraMode(2_560, 1_440),
    DesktopCameraMode(2_048, 1_536),
    DesktopCameraMode(1_920, 1_440),
    DesktopCameraMode(1_920, 1_080),
    DesktopCameraMode(1_760, 1_328),
    DesktopCameraMode(1_600, 1_200),
    DesktopCameraMode(1_552, 1_552),
    DesktopCameraMode(1_440, 1_080),
    DesktopCameraMode(1_328, 1_760),
    DesktopCameraMode(1_280, 960),
    DesktopCameraMode(1_280, 720),
    DesktopCameraMode(1_080, 1_920),
    DesktopCameraMode(1_024, 768),
    DesktopCameraMode(960, 720),
    DesktopCameraMode(800, 600),
    DesktopCameraMode(640, 480),
)

private fun desktopFpsProbeCandidates(): List<DesktopCameraMode> = listOf(
    DesktopCameraMode(640, 480),
    DesktopCameraMode(1_280, 720),
    DesktopCameraMode(1_920, 1_080),
)

private fun desktopFallbackWidths(): List<OpticalCameraWidth> = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac,
    DesktopOperatingSystem.Windows,
    -> listOf(
        OpticalCameraWidth(label = "640", width = 640, height = 480),
        OpticalCameraWidth(label = "1280", width = 1_280, height = 720),
        OpticalCameraWidth(label = "1920", width = 1_920, height = 1_080),
    )
    DesktopOperatingSystem.Linux -> OpticalCameraWidth.defaults
}

private fun bestLinuxVideoDevice(): String {
    val defaultDevice = java.io.File("/dev/video0")
    if (defaultDevice.exists() && defaultDevice.canRead()) return defaultDevice.absolutePath
    return java.io.File("/dev")
        .listFiles { file -> file.name.startsWith("video") && file.canRead() }
        ?.sortedBy { file -> file.name }
        ?.firstOrNull()
        ?.absolutePath
        ?: defaultDevice.absolutePath
}

private fun currentDesktopOperatingSystem(): DesktopOperatingSystem {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> DesktopOperatingSystem.Mac
        osName.contains("windows") -> DesktopOperatingSystem.Windows
        else -> DesktopOperatingSystem.Linux
    }
}

private data class DesktopCameraMode(
    val width: Int,
    val height: Int,
)

private enum class DesktopOperatingSystem {
    Mac,
    Windows,
    Linux,
}

private const val MINIMUM_DESKTOP_CAMERA_WIDTH = 640
private const val MINIMUM_DESKTOP_CAMERA_HEIGHT = 360
