package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import com.sunnychung.application.easytransfer.optical.TransferPayload

internal interface PayloadActions {
    val canOpen: Boolean
    val canShare: Boolean

    fun open(payload: TransferPayload)

    fun share(payload: TransferPayload)
}

@Composable
internal expect fun rememberPayloadActions(
    onError: (String) -> Unit = {},
): PayloadActions

internal fun TransferPayload.safeSuggestedFileName(): String = suggestedFileName()
    .map { character ->
        if (character.isLetterOrDigit() || character in ".-_ ") character else '_'
    }
    .joinToString(separator = "")
    .take(160)
    .ifBlank { "received-file.bin" }

internal fun TransferPayload.actionMediaType(): String =
    mediaType?.substringBefore(';')?.takeIf { it.contains('/') } ?: "application/octet-stream"

