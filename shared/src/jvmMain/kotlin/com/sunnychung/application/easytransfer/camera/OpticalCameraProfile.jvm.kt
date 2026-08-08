package com.sunnychung.application.easytransfer.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber
import org.bytedeco.javacv.VideoInputFrameGrabber
import org.bytedeco.opencv.global.opencv_core.IPL_DEPTH_8U
import org.bytedeco.opencv.global.opencv_imgproc.CV_BGR2GRAY
import org.bytedeco.opencv.global.opencv_imgproc.cvCvtColor
import org.bytedeco.opencv.opencv_core.IplImage
import org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_AUTOFOCUS
import org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_BUFFERSIZE
import org.bytedeco.videoinput.global.videoInputLib.VI_MEDIASUBTYPE_MJPG
import org.bytedeco.videoinput.videoInput as VideoInput

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
            DirectShowQrFrameGrabber(0)
                .configuredFromRequest()
                .apply { start() }
        }.recoverCatching {
            OpenCVFrameGrabber(0)
                .configuredFromRequest()
                .apply {
                    format = "MJPG"
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

private class DirectShowQrFrameGrabber(
    private val deviceNumber: Int,
) : FrameGrabber() {
    private val converter = OpenCVFrameConverter.ToIplImage()
    private var videoInputDevice: VideoInput? = null
    private var bgrImage: IplImage? = null
    private var grayImage: IplImage? = null
    private var bgrImageData: BytePointer? = null

    override fun start() {
        val nextVideoInput = VideoInput().apply {
            setUseCallback(false)
            setRequestedMediaSubType(VI_MEDIASUBTYPE_MJPG)
            if (frameRate > 0) {
                setIdealFramerate(deviceNumber, frameRate.toInt())
            }
        }
        val didSetup = nextVideoInput.setupDevice(
            deviceNumber,
            if (imageWidth > 0) imageWidth else DEFAULT_WINDOWS_CAMERA_WIDTH,
            if (imageHeight > 0) imageHeight else DEFAULT_WINDOWS_CAMERA_HEIGHT,
        )
        if (!didSetup) {
            throw FrameGrabber.Exception("videoInput.setupDevice() could not setup device.")
        }
        nextVideoInput.configureAutofocus(deviceNumber)
        videoInputDevice = nextVideoInput
    }

    override fun stop() {
        videoInputDevice?.stopDevice(deviceNumber)
        videoInputDevice = null
    }

    override fun trigger() {
        val input = videoInputDevice ?: throw FrameGrabber.Exception("Camera is not started.")
        val imageData = ensureBgrImageData(input)
        repeat(numBuffers + 1) {
            input.getPixels(deviceNumber, imageData, false, true)
        }
    }

    override fun grab(): Frame {
        val input = videoInputDevice ?: throw FrameGrabber.Exception("Camera is not started.")
        val imageData = ensureBgrImageData(input)
        if (!input.getPixels(deviceNumber, imageData, false, true)) {
            throw FrameGrabber.Exception("videoInput.getPixels() could not get pixels.")
        }
        timestamp = System.nanoTime() / 1_000
        return if (imageMode == ImageMode.GRAY) {
            val bgr = bgrImage ?: throw FrameGrabber.Exception("Camera image is not initialized.")
            val gray = grayImage?.takeIf { image -> image.width() == bgr.width() && image.height() == bgr.height() }
                ?: IplImage.create(bgr.width(), bgr.height(), IPL_DEPTH_8U, 1).also { image -> grayImage = image }
            cvCvtColor(bgr, gray, CV_BGR2GRAY)
            converter.convert(gray)
        } else {
            converter.convert(bgrImage ?: throw FrameGrabber.Exception("Camera image is not initialized."))
        }
    }

    override fun release() {
        stop()
        converter.close()
    }

    override fun getImageWidth(): Int = videoInputDevice?.getWidth(deviceNumber) ?: imageWidth

    override fun getImageHeight(): Int = videoInputDevice?.getHeight(deviceNumber) ?: imageHeight

    private fun ensureBgrImageData(input: VideoInput): BytePointer {
        val width = input.getWidth(deviceNumber)
        val height = input.getHeight(deviceNumber)
        val currentImage = bgrImage
        if (currentImage == null || currentImage.width() != width || currentImage.height() != height) {
            bgrImage = IplImage.create(width, height, IPL_DEPTH_8U, 3)
            bgrImageData = bgrImage?.imageData()
        }
        return bgrImageData ?: throw FrameGrabber.Exception("Camera image buffer is not initialized.")
    }
}

private fun VideoInput.configureAutofocus(deviceNumber: Int) {
    val didEnableAutofocus = setVideoSettingCamera(
        deviceNumber,
        propFocus(),
        0,
        DIRECTSHOW_CONTROL_AUTO,
        false,
    )
    if (!didEnableAutofocus) {
        setVideoSettingCameraPct(
            deviceNumber,
            propFocus(),
            DIRECTSHOW_CLOSE_FOCUS_PERCENT,
            DIRECTSHOW_CONTROL_MANUAL,
        )
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

private const val DEFAULT_WINDOWS_CAMERA_WIDTH = 1_280
private const val DEFAULT_WINDOWS_CAMERA_HEIGHT = 720
private const val DIRECTSHOW_CONTROL_AUTO = 1
private const val DIRECTSHOW_CONTROL_MANUAL = 2
private const val DIRECTSHOW_CLOSE_FOCUS_PERCENT = 0.15f
