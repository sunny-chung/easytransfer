package com.sunnychung.application.easytransfer.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.VideoInputFrameGrabber
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.elements.AppSink

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
    createDesktopGrabber(cameraSettings).configuredFor(cameraSettings)

private fun createDesktopGrabber(cameraSettings: OpticalCameraSettings): FrameGrabber = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac -> FFmpegFrameGrabber("0").apply {
        format = "avfoundation"
    }
    DesktopOperatingSystem.Windows -> WindowsMediaFoundationCameraGrabber(cameraSettings)
    DesktopOperatingSystem.Linux -> FFmpegFrameGrabber(bestLinuxVideoDevice())
}

private fun <T : FrameGrabber> T.configuredFor(cameraSettings: OpticalCameraSettings): T = apply {
    frameRate = cameraSettings.captureFps.framesPerSecond.toDouble()
    imageWidth = cameraSettings.targetWidth
    imageHeight = cameraSettings.targetHeight
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

private class WindowsMediaFoundationCameraGrabber(
    private val cameraSettings: OpticalCameraSettings,
) : FrameGrabber() {
    private var delegate: FrameGrabber? = null

    override fun start() {
        val nextDelegate = runCatching {
            createGStreamerMediaFoundationDelegate()
        }.getOrElse {
            VideoInputFrameGrabber(0).configuredFor(cameraSettings).apply { start() }
        }
        delegate = nextDelegate
    }

    override fun stop() {
        delegate?.stop()
    }

    override fun release() {
        delegate?.release()
        delegate = null
    }

    override fun trigger() {
        delegate?.trigger() ?: throw FrameGrabber.Exception("Camera is not started.")
    }

    override fun grab(): Frame =
        delegate?.grab() ?: throw FrameGrabber.Exception("Camera is not started.")

    private fun createGStreamerMediaFoundationDelegate(): FrameGrabber {
        val grabber = GStreamerMediaFoundationFrameGrabber().configuredFor(cameraSettings)
        return try {
            grabber.start()
            grabber
        } catch (cause: Throwable) {
            runCatching { grabber.release() }
            throw cause
        }
    }
}

private class GStreamerMediaFoundationFrameGrabber : FrameGrabber() {
    private val converter = Java2DFrameConverter()
    private var pipeline: Pipeline? = null
    private var appSink: AppSink? = null

    override fun start() {
        GStreamerRuntime.ensureInitialized()
        val requestedWidth = imageWidth.coerceAtLeast(MINIMUM_CAMERA_WIDTH)
        val requestedHeight = imageHeight.coerceAtLeast(MINIMUM_CAMERA_HEIGHT)
        val requestedFps = frameRate.toInt().coerceAtLeast(MINIMUM_CAMERA_FPS)
        val nextPipeline = Gst.parseLaunch(
            "mfvideosrc device-index=0 ! " +
                "video/x-raw,width=$requestedWidth,height=$requestedHeight,framerate=$requestedFps/1 ! " +
                "queue leaky=downstream max-size-buffers=1 ! " +
                "videoconvert ! video/x-raw,format=BGRx ! " +
                "appsink name=$APP_SINK_NAME sync=false max-buffers=1 drop=true",
        ) as Pipeline
        try {
            val nextAppSink = nextPipeline.getElementByName(APP_SINK_NAME) as AppSink
            pipeline = nextPipeline
            appSink = nextAppSink
            nextPipeline.play()
        } catch (cause: Throwable) {
            nextPipeline.setState(State.NULL)
            throw cause
        }
    }

    override fun stop() {
        pipeline?.setState(State.NULL)
        appSink = null
        pipeline = null
    }

    override fun release() {
        stop()
    }

    override fun trigger() = Unit

    override fun grab(): Frame {
        val sample = appSink?.pullSample() ?: throw Exception("No GStreamer camera sample is available.")
        return try {
            val buffer = sample.buffer ?: throw Exception("GStreamer camera sample does not contain a buffer.")
            val caps = sample.caps ?: throw Exception("GStreamer camera sample does not contain caps.")
            val structure = caps.getStructure(0)
            val width = structure.getInteger("width")
            val height = structure.getInteger("height")
            val mappedBuffer = buffer.map(false)
            try {
                converter.convert(mappedBuffer.toBgrxImage(width, height))
            } finally {
                buffer.unmap()
            }
        } finally {
            sample.dispose()
        }
    }
}

private object GStreamerRuntime {
    private var initialized = false

    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        Gst.init("EasyTransfer")
        initialized = true
    }
}

private fun ByteBuffer.toBgrxImage(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val pixels = (image.raster.dataBuffer as DataBufferInt).data
    val source = duplicate()
    val rowStride = (source.remaining() / height).coerceAtLeast(width * BYTES_PER_BGRX_PIXEL)
    for (y in 0 until height) {
        val rowOffset = y * rowStride
        for (x in 0 until width) {
            val pixelOffset = rowOffset + x * BYTES_PER_BGRX_PIXEL
            val blue = source.get(pixelOffset).toInt() and 0xFF
            val green = source.get(pixelOffset + 1).toInt() and 0xFF
            val red = source.get(pixelOffset + 2).toInt() and 0xFF
            pixels[y * width + x] = (red shl 16) or (green shl 8) or blue
        }
    }
    return image
}

private const val APP_SINK_NAME = "easytransfer_camera_sink"
private const val BYTES_PER_BGRX_PIXEL = 4
private const val MINIMUM_CAMERA_WIDTH = 1
private const val MINIMUM_CAMERA_HEIGHT = 1
private const val MINIMUM_CAMERA_FPS = 1
