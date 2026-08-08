package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.sunnychung.application.easytransfer.optical.TransferPayload
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPayloadSaver(
    onSaved: () -> Unit,
    onError: (String) -> Unit,
): PayloadSaver {
    val coroutineScope = rememberCoroutineScope()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnError by rememberUpdatedState(onError)
    var pendingPayload by remember { mutableStateOf<TransferPayload?>(null) }
    val launcher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { destination ->
        val payload = pendingPayload
        if (destination != null && payload != null) {
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        destination.write(payload.bytes)
                    }
                }
                    .onSuccess { currentOnSaved() }
                    .onFailure { currentOnError("The received file could not be saved.") }
            }
        }
    }
    return object : PayloadSaver {
        override val isSupported: Boolean = true

        override fun save(payload: TransferPayload) {
            pendingPayload = payload
            val fileName = payload.suggestedFileName()
            launcher.launch(
                suggestedName = fileName.substringBeforeLast('.', fileName),
                defaultExtension = fileName.substringAfterLast('.', "").ifBlank { null },
            )
        }
    }
}
