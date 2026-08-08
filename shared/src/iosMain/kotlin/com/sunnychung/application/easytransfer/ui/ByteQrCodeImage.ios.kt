package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.sunnychung.application.easytransfer.optical.OpticalErrorCorrection
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreImage.CIFilter
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Foundation.setValue
import platform.QuartzCore.kCAFilterNearest
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import platform.UIKit.accessibilityLabel
import platform.UIKit.isAccessibilityElement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun ByteQrCodeImage(
    frame: ByteArray,
    errorCorrection: OpticalErrorCorrection,
    contentDescription: String?,
    modifier: Modifier,
) {
    var image by remember { mutableStateOf<UIImage?>(null) }
    val renderRequests = remember { Channel<QrRenderRequest>(Channel.CONFLATED) }
    DisposableEffect(renderRequests) {
        onDispose { renderRequests.close() }
    }
    LaunchedEffect(frame, errorCorrection) {
        renderRequests.trySend(QrRenderRequest(frame = frame, errorCorrection = errorCorrection))
    }
    LaunchedEffect(renderRequests) {
        for (request in renderRequests) {
            image = withContext(Dispatchers.Default) {
                request.frame.toQrImage(request.errorCorrection)
            }
        }
    }
    UIKitView(
        factory = {
            UIImageView(image = image).apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                layer.magnificationFilter = kCAFilterNearest
                isAccessibilityElement = contentDescription != null
                accessibilityLabel = contentDescription
            }
        },
        update = { imageView -> imageView.image = image },
        modifier = modifier,
    )
}

internal actual fun prewarmQrRenderer() {
    byteArrayOf(0).toQrImage(OpticalErrorCorrection.Low)
}

private data class QrRenderRequest(
    val frame: ByteArray,
    val errorCorrection: OpticalErrorCorrection,
)

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toQrImage(errorCorrection: OpticalErrorCorrection): UIImage? {
    val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null
    filter.setDefaults()
    filter.setValue(toNSData(), forKey = "inputMessage")
    filter.setValue(errorCorrection.label, forKey = "inputCorrectionLevel")
    return filter.outputImage?.let { outputImage -> UIImage.imageWithCIImage(outputImage) }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(
        bytes = pinned.addressOf(0),
        length = size.toULong(),
    )
}
