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
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberPayloadActions(
    onError: (String) -> Unit,
): PayloadActions {
    val coroutineScope = rememberCoroutineScope()
    val currentOnError = rememberUpdatedState(onError)
    val shareLauncher = rememberShareFileLauncher()
    return remember(shareLauncher) {
        object : PayloadActions {
            override val canOpen: Boolean = true
            override val canShare: Boolean = true

            override fun open(payload: TransferPayload) = shareOrOpen(payload)

            override fun share(payload: TransferPayload) = shareOrOpen(payload)

            private fun shareOrOpen(payload: TransferPayload) {
                coroutineScope.launch {
                    runCatching {
                        val file = FileKit.cacheDir / payload.safeSuggestedFileName()
                        file.write(payload.bytes)
                        shareLauncher.launch(file)
                    }.onFailure {
                        currentOnError.value("The received item could not be opened or shared.")
                    }
                }
            }
        }
    }
}

