package com.sunnychung.application.easytransfer.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.bytedeco.ffmpeg.avdevice.AVDeviceInfo
import org.bytedeco.ffmpeg.avdevice.AVDeviceInfoList
import org.bytedeco.ffmpeg.global.avdevice.avdevice_free_list_devices
import org.bytedeco.ffmpeg.global.avdevice.avdevice_list_input_sources
import org.bytedeco.ffmpeg.global.avdevice.avdevice_register_all
import org.bytedeco.ffmpeg.global.avformat.av_find_input_format
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

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
    VerifiedFfmpegCameraGrabber(cameraSettings)

private fun createFfmpegGrabber(
    cameraSettings: OpticalCameraSettings,
    preferLinuxMjpeg: Boolean,
): FFmpegFrameGrabber = when (currentDesktopOperatingSystem()) {
    DesktopOperatingSystem.Mac -> FFmpegFrameGrabber("0").apply {
        format = "avfoundation"
    }
    DesktopOperatingSystem.Windows -> FFmpegFrameGrabber(firstWindowsVideoInput()).apply {
        format = "dshow"
        setOption("rtbufsize", WINDOWS_REAL_TIME_BUFFER_SIZE)
    }
    DesktopOperatingSystem.Linux -> FFmpegFrameGrabber(bestLinuxVideoDevice()).apply {
        format = "video4linux2"
        if (preferLinuxMjpeg) setOption("input_format", "mjpeg")
    }
}.apply {
    frameRate = cameraSettings.captureFps.framesPerSecond.toDouble()
    imageWidth = cameraSettings.targetWidth
    imageHeight = cameraSettings.targetHeight
    numBuffers = 1
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

private fun firstWindowsVideoInput(): String {
    avdevice_register_all()
    val directShow = av_find_input_format("dshow")
    if (directShow == null || directShow.isNull) {
        throw FrameGrabber.Exception("FFmpeg DirectShow support is not available.")
    }

    val deviceList = AVDeviceInfoList(null as Pointer?)
    val deviceCount = avdevice_list_input_sources(
        directShow,
        null as String?,
        null,
        deviceList,
    )
    return try {
        if (deviceCount <= 0 || deviceList.isNull) {
            throw FrameGrabber.Exception("No Windows camera was found by FFmpeg DirectShow.")
        }
        val videoDeviceIndices = (0 until deviceList.nb_devices())
            .filter { index -> deviceList.devices(index).isVideoInput() }
        val defaultDeviceIndex = deviceList.default_device()
            .takeIf { index -> index in videoDeviceIndices }
        val selectedDeviceIndex = defaultDeviceIndex ?: videoDeviceIndices.firstOrNull()
            ?: throw FrameGrabber.Exception("FFmpeg DirectShow found no video input device.")
        deviceList.devices(selectedDeviceIndex).deviceName().asDirectShowVideoInput()
    } finally {
        avdevice_free_list_devices(deviceList)
    }
}

private fun AVDeviceInfo.isVideoInput(): Boolean {
    if (deviceName().startsWith(DIRECT_SHOW_VIDEO_PREFIX, ignoreCase = true)) return true
    val mediaTypes = media_types()
    if (mediaTypes == null || mediaTypes.isNull) return false
    return (0 until nb_media_types()).any { index ->
        mediaTypes.get(index.toLong()) == AVMEDIA_TYPE_VIDEO
    }
}

private fun AVDeviceInfo.deviceName(): String {
    val name = device_name()
    if (name == null || name.isNull) {
        throw FrameGrabber.Exception("FFmpeg returned a camera without a device name.")
    }
    return name.getString()
}

private fun String.asDirectShowVideoInput(): String =
    if (startsWith(DIRECT_SHOW_VIDEO_PREFIX, ignoreCase = true)) this else "$DIRECT_SHOW_VIDEO_PREFIX$this"

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

private class VerifiedFfmpegCameraGrabber(
    private val cameraSettings: OpticalCameraSettings,
) : FrameGrabber() {
    private val delegateLock = Any()
    @Volatile private var isClosed = false
    @Volatile private var isReleased = false
    private var delegate: FFmpegFrameGrabber? = null

    override fun start() = synchronized(delegateLock) {
        if (isReleased) throw FrameGrabber.Exception("Camera has been released.")
        isClosed = false
        var lastFailure: Throwable? = null
        val linuxMjpegPreferences = if (currentDesktopOperatingSystem() == DesktopOperatingSystem.Linux) {
            listOf(true, false)
        } else {
            listOf(false)
        }
        for (preferLinuxMjpeg in linuxMjpegPreferences) {
            val nextDelegate = createFfmpegGrabber(cameraSettings, preferLinuxMjpeg)
            try {
                nextDelegate.start()
                nextDelegate.requireRequestedResolution(cameraSettings)
                delegate = nextDelegate
                return@synchronized
            } catch (cause: Throwable) {
                runCatching { nextDelegate.release() }
                lastFailure = cause
            }
        }
        val cause = lastFailure
        if (cause is FrameGrabber.Exception) throw cause
        throw FrameGrabber.Exception("FFmpeg could not start the desktop camera.", cause)
    }

    override fun stop() {
        val currentDelegate = synchronized(delegateLock) {
            isClosed = true
            delegate
        }
        currentDelegate?.stop()
    }

    override fun release() {
        val currentDelegate = synchronized(delegateLock) {
            isReleased = true
            isClosed = true
            delegate.also { delegate = null }
        }
        try {
            currentDelegate?.stop()
        } catch (_: Throwable) {
            // Continue to release native resources even if the device was already stopped.
        }
        currentDelegate?.release()
    }

    override fun trigger() = synchronized(delegateLock) {
        if (isClosed) throw FrameGrabber.Exception("Camera is not started.")
        delegate?.trigger() ?: throw FrameGrabber.Exception("Camera is not started.")
    }

    override fun grab(): Frame {
        if (isClosed) throw FrameGrabber.Exception("Camera is not started.")
        val currentDelegate = synchronized(delegateLock) {
            if (isClosed) throw FrameGrabber.Exception("Camera is not started.")
            delegate
        } ?: throw FrameGrabber.Exception("Camera is not started.")
        val frame = currentDelegate.grab()
        if (isClosed) throw FrameGrabber.Exception("Camera is not started.")
        // The caller converts the frame after grab() returns. Return an owned
        // copy so conversion can never read FFmpeg's released buffer.
        return frame.clone()
    }
}

private fun FFmpegFrameGrabber.requireRequestedResolution(cameraSettings: OpticalCameraSettings) {
    val matchesRequestedResolution =
        imageWidth == cameraSettings.targetWidth && imageHeight == cameraSettings.targetHeight
    val matchesRotatedResolution =
        imageWidth == cameraSettings.targetHeight && imageHeight == cameraSettings.targetWidth
    if (!matchesRequestedResolution && !matchesRotatedResolution) {
        throw FrameGrabber.Exception(
            "Camera opened at " + imageWidth + "x" + imageHeight +
                " instead of " + cameraSettings.targetWidth + "x" + cameraSettings.targetHeight + ".",
        )
    }
}

private const val DIRECT_SHOW_VIDEO_PREFIX = "video="
private const val WINDOWS_REAL_TIME_BUFFER_SIZE = "256M"
