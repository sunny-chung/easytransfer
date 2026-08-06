package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.sunnychung.application.easytransfer.optical.TransferPayload
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.dialogs.compose.rememberShareFileLauncher
import io.github.vinceglb.filekit.write
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@Composable
internal actual fun rememberPayloadActions(
    onError: (String) -> Unit,
): PayloadActions {
    val coroutineScope = rememberCoroutineScope()
    val currentOnError = rememberUpdatedState(onError)
    val shareLauncher = rememberShareFileLauncher()
    val documentPreviewer = remember {
        IosDocumentPreviewer(onError = { message -> currentOnError.value(message) })
    }
    return remember(shareLauncher, documentPreviewer) {
        object : PayloadActions {
            override val canOpen: Boolean = true
            override val canShare: Boolean = true

            override fun open(payload: TransferPayload) {
                coroutineScope.launch {
                    runCatching {
                        val fileUrl = payload.writeToPreviewFile()
                        documentPreviewer.open(fileUrl)
                    }.onFailure {
                        currentOnError.value("No app could open this received item.")
                    }
                }
            }

            override fun share(payload: TransferPayload) {
                coroutineScope.launch {
                    runCatching {
                        val file = FileKit.cacheDir / payload.safeSuggestedFileName()
                        file.write(payload.bytes)
                        shareLauncher.launch(file)
                    }.onFailure {
                        currentOnError.value("The received item could not be shared.")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosDocumentPreviewer(
    private val onError: (String) -> Unit,
) {
    private var controller: UIDocumentInteractionController? = null
    private var delegate: IosDocumentPreviewDelegate? = null

    fun open(fileUrl: NSURL) {
        val rootViewController = UIApplication.sharedApplication.activeRootViewController()
            ?: run {
                onError("No window is available to open this received item.")
                return
            }
        val nextController = UIDocumentInteractionController.interactionControllerWithURL(fileUrl)
        val nextDelegate = IosDocumentPreviewDelegate(rootViewController)
        nextController.delegate = nextDelegate

        controller = nextController
        delegate = nextDelegate

        if (!nextController.presentPreviewAnimated(true)) {
            val didPresentOpenMenu = nextController.presentOpenInMenuFromRect(
                rect = CGRectZero.readValue(),
                inView = rootViewController.view,
                animated = true,
            )
            if (!didPresentOpenMenu) {
                onError("No app could preview this received item.")
            }
        }
    }
}

private class IosDocumentPreviewDelegate(
    private val rootViewController: UIViewController,
) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
    override fun documentInteractionControllerViewControllerForPreview(
        controller: UIDocumentInteractionController,
    ): UIViewController = rootViewController.topPresentedViewController()
}

private tailrec fun UIViewController.topPresentedViewController(): UIViewController {
    val nextViewController = presentedViewController ?: return this
    return nextViewController.topPresentedViewController()
}

private fun UIApplication.activeRootViewController(): UIViewController? {
    keyWindow?.rootViewController?.let { return it.topPresentedViewController() }

    val appWindows = windows.filterIsInstance<UIWindow>()
    for (window in appWindows) {
        if (window.isKeyWindow()) {
            window.rootViewController?.let { return it.topPresentedViewController() }
        }
    }
    for (window in appWindows) {
        window.rootViewController?.let { return it.topPresentedViewController() }
    }
    return null
}

@OptIn(ExperimentalForeignApi::class)
private fun TransferPayload.writeToPreviewFile(): NSURL {
    val directoryPath = NSTemporaryDirectory().trimEnd('/') + "/easytransfer-received"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directoryPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val filePath = "$directoryPath/${safeSuggestedFileName()}"
    if (!bytes.toNSData().writeToFile(filePath, atomically = true)) {
        error("Could not write received file.")
    }
    return NSURL.fileURLWithPath(filePath)
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(
        bytes = pinned.addressOf(0),
        length = size.toULong(),
    )
}
