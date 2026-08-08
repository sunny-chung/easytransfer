package com.sunnychung.application.easytransfer.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.OpenCVFrameGrabber
import org.bytedeco.javacv.VideoInputFrameGrabber
import org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_AUTOFOCUS
import org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_BUFFERSIZE

@Composable
internal actual fun supportedOpticalCameraWidths(
    captureFps: OpticalCaptureFps,
): List<OpticalCameraWidth> = remember(captureFps) {
    desktopCameraWidths()
}

@Composable
internal actual fun supportedOpticalCaptureFps(): List<OpticalCaptureFps> = remember {
    desktopCaptureFps()
}

internal actual fun supportedOpticalDecodeWorkers(): List<OpticalDecodeWorkers> =
    OpticalDecodeWorkers.entries.toList()

internal fun desktopCameraGrabber(cameraSettings: OpticalCameraSettings): FrameGrabber =
    createDesktopGrabber().apply {
        frameRate = cameraSettings.captureFps.framesPerSecond.toDouble()
        imageWidth = cameraSettings.targetWidth
        imageHeight = cameraSettings.targetHeight
    }

private fun createDesktopGrabber(): FrameGrabber = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac -> FFmpegFrameGrabber("0").apply {
        format = "avfoundation"
    }
    DesktopOperatingSystem.Windows -> WindowsAutofocusFrameGrabber()
    DesktopOperatingSystem.Linux -> FFmpegFrameGrabber(bestLinuxVideoDevice())
}

private class WindowsAutofocusFrameGrabber : FrameGrabber() {
    private var delegate: FrameGrabber? = null

    override fun start() {
        delegate = runCatching {
            OpenCVFrameGrabber(0)
                .configuredFromRequest()
                .apply {
                    setOption(CAP_PROP_AUTOFOCUS, 1.0)
                    setOption(CAP_PROP_BUFFERSIZE, 1.0)
                    start()
                }
        }.getOrElse {
            VideoInputFrameGrabber(0)
                .configuredFromRequest()
                .apply { start() }
        }
    }

    override fun stop() {
        delegate?.stop()
    }

    override fun trigger() {
        delegate?.trigger() ?: throw FrameGrabber.Exception("Camera is not started.")
    }

    override fun grab(): Frame =
        delegate?.grab() ?: throw FrameGrabber.Exception("Camera is not started.")

    override fun release() {
        delegate?.release()
        delegate = null
    }

    override fun getImageWidth(): Int = delegate?.imageWidth ?: imageWidth

    override fun getImageHeight(): Int = delegate?.imageHeight ?: imageHeight

    private fun <T : FrameGrabber> T.configuredFromRequest(): T = apply {
        frameRate = this@WindowsAutofocusFrameGrabber.frameRate
        imageWidth = this@WindowsAutofocusFrameGrabber.imageWidth
        imageHeight = this@WindowsAutofocusFrameGrabber.imageHeight
    }
}

private fun desktopCameraWidths(): List<OpticalCameraWidth> = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac -> listOf(
        OpticalCameraWidth(label = "640", width = 640, height = 480),
        OpticalCameraWidth(label = "1080", width = 1_080, height = 1_920),
        OpticalCameraWidth(label = "1280", width = 1_280, height = 720),
        OpticalCameraWidth(label = "1328", width = 1_328, height = 1_760),
        OpticalCameraWidth(label = "1552", width = 1_552, height = 1_552),
        OpticalCameraWidth(label = "1760", width = 1_760, height = 1_328),
        OpticalCameraWidth(label = "1920", width = 1_920, height = 1_080),
    )
    DesktopOperatingSystem.Windows -> listOf(
        OpticalCameraWidth(label = "640", width = 640, height = 480),
        OpticalCameraWidth(label = "1280", width = 1_280, height = 720),
        OpticalCameraWidth(label = "1920", width = 1_920, height = 1_080),
    )
    DesktopOperatingSystem.Linux -> OpticalCameraWidth.defaults
}

private fun desktopCaptureFps(): List<OpticalCaptureFps> = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac -> listOf(OpticalCaptureFps.Fps15, OpticalCaptureFps.Fps30)
    DesktopOperatingSystem.Windows,
    DesktopOperatingSystem.Linux,
    -> listOf(OpticalCaptureFps.Fps30)
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

private enum class DesktopOperatingSystem {
    Mac,
    Windows,
    Linux,
}
