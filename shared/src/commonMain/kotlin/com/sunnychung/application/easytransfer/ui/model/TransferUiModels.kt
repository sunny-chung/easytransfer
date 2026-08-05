package com.sunnychung.application.easytransfer.ui.model

import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.optical.TransferKind

enum class AppSection(
    val label: String,
) {
    Home("Home"),
    Send("Send"),
    Receive("Receive"),
    History("History"),
    Settings("Settings"),
}

enum class TransferStatus {
    Received,
    Sent,
}

data class HistoryItemUi(
    val title: String,
    val detail: String,
    val kind: TransferKind,
    val status: TransferStatus,
    val timeLabel: String,
    val sourceLabel: String,
)

data class ReceivedItemUi(
    val title: String,
    val detail: String,
    val kind: TransferKind,
    val sizeLabel: String,
    val sourceLabel: String,
)

data class EasyTransferUiState(
    val selectedSection: AppSection = AppSection.Home,
    val selectedTransferKind: TransferKind? = null,
    val historyItems: List<HistoryItemUi> = emptyList(),
    val receivedItem: ReceivedItemUi? = null,
    val receivedPayload: TransferPayload? = null,
)

object PreviewData {
    val receivedHistoryItem = HistoryItemUi(
        title = "Project brief.pdf",
        detail = "PDF document · 2.4 MB",
        kind = TransferKind.File,
        status = TransferStatus.Received,
        timeLabel = "Just now",
        sourceLabel = "Jamie’s iPad",
    )

    val historyItems = listOf(
        HistoryItemUi(
            title = "Trip photos.zip",
            detail = "24 files · 18.4 MB",
            kind = TransferKind.File,
            status = TransferStatus.Received,
            timeLabel = "2 min ago",
            sourceLabel = "Alex's phone",
        ),
        HistoryItemUi(
            title = "Design review notes",
            detail = "Looks good—move the primary action…",
            kind = TransferKind.Text,
            status = TransferStatus.Sent,
            timeLabel = "18 min ago",
            sourceLabel = "Office Mac",
        ),
        HistoryItemUi(
            title = "maps.app.goo.gl/coffee",
            detail = "Coffee shop directions",
            kind = TransferKind.Link,
            status = TransferStatus.Received,
            timeLabel = "Yesterday",
            sourceLabel = "Pixel Tablet",
        ),
        HistoryItemUi(
            title = "whiteboard.jpg",
            detail = "3024 × 4032 · 2.8 MB",
            kind = TransferKind.Image,
            status = TransferStatus.Sent,
            timeLabel = "Yesterday",
            sourceLabel = "Home PC",
        ),
    )

    val receivedItem = ReceivedItemUi(
        title = "Project brief.pdf",
        detail = "PDF document",
        kind = TransferKind.File,
        sizeLabel = "2.4 MB",
        sourceLabel = "Jamie’s iPad",
    )
}
