package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.optical.TransferKind

internal interface PayloadSaver {
    val isSupported: Boolean

    fun save(payload: TransferPayload)
}

@Composable
internal expect fun rememberPayloadSaver(
    onSaved: () -> Unit = {},
    onError: (String) -> Unit = {},
): PayloadSaver

internal fun TransferPayload.suggestedFileName(): String = name ?: when (kind) {
    TransferKind.Text -> "received-text.txt"
    TransferKind.Link -> "received-link.txt"
    TransferKind.Image -> "received-image.bin"
    TransferKind.File -> "received-file.bin"
}
