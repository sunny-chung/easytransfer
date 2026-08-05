package com.sunnychung.application.easytransfer.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.sunnychung.application.easytransfer.optical.TransferPayload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPayloadActions(
    onError: (String) -> Unit,
): PayloadActions {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnError = rememberUpdatedState(onError)
    return remember(context) {
        object : PayloadActions {
            override val canOpen: Boolean = true
            override val canShare: Boolean = true

            override fun open(payload: TransferPayload) {
                coroutineScope.launch {
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            payload.writeToActionCache(context.cacheDir),
                        )
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, payload.actionMediaType())
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Open with"))
                    }.onFailure {
                        currentOnError.value("No app could open this received item.")
                    }
                }
            }

            override fun share(payload: TransferPayload) {
                coroutineScope.launch {
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            payload.writeToActionCache(context.cacheDir),
                        )
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType(payload.actionMediaType())
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Share to"))
                    }.onFailure {
                        currentOnError.value("The received item could not be shared.")
                    }
                }
            }
        }
    }
}

private suspend fun TransferPayload.writeToActionCache(cacheDirectory: File): File =
    withContext(Dispatchers.IO) {
        File(cacheDirectory, "easy_transfer_received").also(File::mkdirs)
            .resolve(safeSuggestedFileName())
            .also { file -> file.writeBytes(bytes) }
    }

