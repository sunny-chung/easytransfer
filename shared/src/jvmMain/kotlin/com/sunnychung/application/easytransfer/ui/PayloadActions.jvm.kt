package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.sunnychung.application.easytransfer.optical.TransferPayload
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPayloadActions(
    onError: (String) -> Unit,
): PayloadActions {
    val coroutineScope = rememberCoroutineScope()
    val currentOnError = rememberUpdatedState(onError)
    val canOpen = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
    return remember(canOpen) {
        object : PayloadActions {
            override val canOpen: Boolean = canOpen
            override val canShare: Boolean = false

            override fun open(payload: TransferPayload) {
                coroutineScope.launch {
                    runCatching {
                        val file = withContext(Dispatchers.IO) {
                            File(System.getProperty("java.io.tmpdir"), "EasyTransfer/received")
                                .also(File::mkdirs)
                                .resolve(payload.safeSuggestedFileName())
                                .also { it.writeBytes(payload.bytes) }
                        }
                        Desktop.getDesktop().open(file)
                    }.onFailure {
                        currentOnError.value("No app could open this received item.")
                    }
                }
            }

            override fun share(payload: TransferPayload) {
                currentOnError.value("System sharing is not available on this desktop.")
            }
        }
    }
}

