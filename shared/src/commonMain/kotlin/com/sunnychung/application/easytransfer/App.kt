package com.sunnychung.application.easytransfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.ui.EasyTransferScreen
import com.sunnychung.application.easytransfer.ui.EasyTransferTheme
import com.sunnychung.application.easytransfer.ui.formatByteCount
import com.sunnychung.application.easytransfer.ui.label
import com.sunnychung.application.easytransfer.ui.model.AppSection
import com.sunnychung.application.easytransfer.ui.model.EasyTransferUiState
import com.sunnychung.application.easytransfer.ui.model.HistoryItemUi
import com.sunnychung.application.easytransfer.ui.model.ReceivedItemUi
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.ui.model.TransferStatus

@Preview(
    name = "App shell",
    widthDp = 390,
    heightDp = 844,
)
@Composable
fun App() {
    val isPreview = LocalInspectionMode.current
    var selectedSection by remember { mutableStateOf(AppSection.Home) }
    var selectedKind by remember { mutableStateOf<TransferKind?>(null) }
    var historyItems by remember { mutableStateOf(emptyList<HistoryItemUi>()) }
    var receivedPayload by remember { mutableStateOf<TransferPayload?>(null) }

    EasyTransferTheme {
        EasyTransferScreen(
            state = EasyTransferUiState(
                selectedSection = selectedSection,
                selectedTransferKind = selectedKind,
                historyItems = historyItems,
                receivedItem = receivedPayload?.toReceivedItemUi(),
                receivedPayload = receivedPayload,
            ),
            cameraEnabled = !isPreview,
            onSectionSelected = { selectedSection = it },
            onTransferKindSelected = {
                selectedKind = it
                selectedSection = AppSection.Send
            },
            onTransferStarted = { payload ->
                historyItems = listOf(payload.toHistoryItem(TransferStatus.Sent)) + historyItems
            },
            onTransferReceived = { payload ->
                historyItems = listOf(payload.toHistoryItem(TransferStatus.Received)) + historyItems
                receivedPayload = payload
            },
            onDismissReceivePrompt = { receivedPayload = null },
        )
    }
}

private fun TransferPayload.toHistoryItem(status: TransferStatus): HistoryItemUi = HistoryItemUi(
    title = displayName(),
    detail = "${kind.label} · ${bytes.size.formatByteCount()}",
    kind = kind,
    status = status,
    timeLabel = "Just now",
    sourceLabel = if (status == TransferStatus.Received) "Optical transfer" else "This device",
)

private fun TransferPayload.toReceivedItemUi(): ReceivedItemUi = ReceivedItemUi(
    title = displayName(),
    detail = kind.label,
    kind = kind,
    sizeLabel = bytes.size.formatByteCount(),
    sourceLabel = "Optical transfer",
)

private fun TransferPayload.displayName(): String = name ?: when (kind) {
    TransferKind.Text -> textPreview(maxBytes = 256)?.chunks?.firstOrNull()
        ?.lineSequence()?.firstOrNull()?.take(48).orEmpty().ifBlank { "Text" }
    TransferKind.Link -> textPreview(maxBytes = 256)?.chunks?.firstOrNull()
        .orEmpty().take(48).ifBlank { "Link" }
    TransferKind.Image -> "Received image"
    TransferKind.File -> "Received file"
}
