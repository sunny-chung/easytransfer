package com.sunnychung.application.easytransfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.ui.EasyTransferScreen
import com.sunnychung.application.easytransfer.ui.EasyTransferTheme
import com.sunnychung.application.easytransfer.ui.displayName
import com.sunnychung.application.easytransfer.ui.formatByteCount
import com.sunnychung.application.easytransfer.ui.label
import com.sunnychung.application.easytransfer.ui.prewarmQrRenderer
import com.sunnychung.application.easytransfer.ui.rememberPersistentHistoryStore
import com.sunnychung.application.easytransfer.ui.model.AppSection
import com.sunnychung.application.easytransfer.ui.model.EasyTransferUiState
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.ui.model.ReceivedItemUi
import com.sunnychung.application.easytransfer.ui.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Preview(
    name = "App shell",
    widthDp = 390,
    heightDp = 844,
)
@Composable
fun App() {
    val isPreview = LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()
    val historyStore = rememberPersistentHistoryStore()
    var selectedSection by remember { mutableStateOf(AppSection.Home) }
    var selectedKind by remember { mutableStateOf<TransferKind?>(null) }
    var historyItems by remember { mutableStateOf(PreviewData.historyItems.takeIf { isPreview }.orEmpty()) }
    var receivedPayload by remember { mutableStateOf<TransferPayload?>(null) }

    LaunchedEffect(historyStore, isPreview) {
        if (!isPreview) {
            historyItems = withContext(Dispatchers.Default) {
                historyStore.load()
            }
        }
    }

    LaunchedEffect(isPreview) {
        if (!isPreview) {
            withContext(Dispatchers.Default) {
                prewarmQrRenderer()
            }
        }
    }

    fun addHistoryItem(payload: TransferPayload, status: TransferStatus) {
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    historyStore.add(payload, status)
                }
            }.onSuccess { item ->
                historyItems = listOf(item) + historyItems
            }
        }
    }

    fun deleteHistoryItem(itemId: String) {
        historyItems = historyItems.filterNot { item -> item.id == itemId }
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    historyStore.delete(itemId)
                }
            }
        }
    }

    fun clearHistory() {
        historyItems = emptyList()
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    historyStore.clear()
                }
            }
        }
    }

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
            onSectionSelected = { section ->
                if (section != AppSection.Settings) {
                    selectedSection = section
                }
            },
            onTransferKindSelected = {
                selectedKind = it
                selectedSection = AppSection.Send
            },
            onTransferStarted = { payload ->
                addHistoryItem(payload, TransferStatus.Sent)
            },
            onTransferReceived = { payload ->
                addHistoryItem(payload, TransferStatus.Received)
                receivedPayload = payload
            },
            onDismissReceivePrompt = { receivedPayload = null },
            onHistoryItemDeleted = ::deleteHistoryItem,
            onHistoryCleared = ::clearHistory,
            onHistoryPayloadRequested = historyStore::loadPayload,
        )
    }
}

private fun TransferPayload.toReceivedItemUi(): ReceivedItemUi = ReceivedItemUi(
    title = displayName(),
    detail = kind.label,
    kind = kind,
    sizeLabel = bytes.size.formatByteCount(),
    sourceLabel = "Optical transfer",
)
