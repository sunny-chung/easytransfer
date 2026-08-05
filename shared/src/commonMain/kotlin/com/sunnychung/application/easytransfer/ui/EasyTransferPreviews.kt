package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sunnychung.application.easytransfer.ui.model.AppSection
import com.sunnychung.application.easytransfer.ui.model.EasyTransferUiState
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.optical.TransferKind

@Preview(
    name = "Phone · Home",
    group = "Adaptive app",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
)
@Composable
private fun PhoneHomePreview() {
    PreviewFrame(
        state = EasyTransferUiState(
            selectedSection = AppSection.Home,
            historyItems = PreviewData.historyItems,
        ),
    )
}

@Preview(
    name = "Phone · Send file",
    group = "Adaptive app",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
)
@Composable
private fun PhoneSendPreview() {
    PreviewFrame(
        state = EasyTransferUiState(
            selectedSection = AppSection.Send,
            selectedTransferKind = TransferKind.File,
            historyItems = PreviewData.historyItems,
        ),
    )
}

@Preview(
    name = "Tablet · Received actions",
    group = "Adaptive app",
    widthDp = 820,
    heightDp = 1_180,
    showBackground = true,
)
@Composable
private fun TabletReceivePreview() {
    PreviewFrame(
        state = EasyTransferUiState(
            selectedSection = AppSection.Receive,
            historyItems = listOf(PreviewData.receivedHistoryItem) + PreviewData.historyItems,
            receivedItem = PreviewData.receivedItem,
        ),
    )
}

@Preview(
    name = "Desktop · Home",
    group = "Adaptive app",
    widthDp = 1_440,
    heightDp = 900,
    showBackground = true,
)
@Composable
private fun DesktopHomePreview() {
    PreviewFrame(
        state = EasyTransferUiState(
            selectedSection = AppSection.Home,
            historyItems = PreviewData.historyItems,
        ),
    )
}

@Preview(
    name = "Desktop · Receive",
    group = "Adaptive app",
    widthDp = 1_440,
    heightDp = 900,
    showBackground = true,
)
@Composable
private fun DesktopReceivePreview() {
    PreviewFrame(
        state = EasyTransferUiState(
            selectedSection = AppSection.Receive,
            historyItems = PreviewData.historyItems,
        ),
    )
}

@Composable
private fun PreviewFrame(
    state: EasyTransferUiState,
) {
    EasyTransferTheme {
        EasyTransferScreen(
            state = state,
            onSectionSelected = {},
            onTransferKindSelected = {},
            onTransferStarted = {},
            onTransferReceived = {},
            onDismissReceivePrompt = {},
        )
    }
}
