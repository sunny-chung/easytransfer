package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.optical.TransferTextPreview
import com.sunnychung.application.easytransfer.ui.model.HistoryItemUi
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.ui.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Preview(
    name = "History screen",
    widthDp = 800,
    heightDp = 1_000,
)
@Composable
internal fun HistoryScreen(
    historyItems: List<HistoryItemUi> = PreviewData.historyItems,
    showPageTitle: Boolean = true,
    onHistoryItemDeleted: (String) -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    onHistoryPayloadRequested: (String) -> TransferPayload? = { null },
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val payloadSaver = rememberPayloadSaver()
    val payloadActions = rememberPayloadActions()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var activeActionItemId by remember { mutableStateOf<String?>(null) }
    var previewPayload by remember { mutableStateOf<TransferPayload?>(null) }
    var showTextPreview by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val visibleItems = remember(historyItems, searchQuery, selectedFilter) {
        historyItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() || listOf(item.title, item.detail, item.sourceLabel)
                .any { it.contains(searchQuery, ignoreCase = true) }
            val matchesFilter = when (selectedFilter) {
                "Received" -> item.status == TransferStatus.Received
                "Sent" -> item.status == TransferStatus.Sent
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }
    fun dismissActionMenu() {
        activeActionItemId = null
    }
    fun loadPayload(itemId: String, onLoaded: (TransferPayload) -> Unit) {
        coroutineScope.launch {
            val payload = withContext(Dispatchers.Default) {
                onHistoryPayloadRequested(itemId)
            }
            payload?.let(onLoaded)
        }
    }
    fun openPayload(payload: TransferPayload) {
        when (payload.kind) {
            TransferKind.Link -> payload.textPreview(maxBytes = 16 * 1_024)
                ?.chunks?.joinToString(separator = "")
                ?.let { uri -> runCatching { uriHandler.openUri(uri) } }
            TransferKind.Image,
            TransferKind.File,
            -> payloadActions.open(payload)
            TransferKind.Text -> Unit
        }
    }
    fun copyPayload(payload: TransferPayload) {
        if (payload.bytes.size <= MAX_CLIPBOARD_COPY_BYTES) {
            payload.text()?.let { text ->
                clipboardManager.setText(AnnotatedString(text))
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .dismissKeyboardOnTap()
            .padding(horizontal = if (showPageTitle) 40.dp else 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showPageTitle) {
            PageHeading(
                title = "History",
                subtitle = "Everything you send or receive stays easy to find.",
            )
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search transfers") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Received", "Sent").forEach { label ->
                    FilterChip(
                        selected = selectedFilter == label,
                        onClick = { selectedFilter = label },
                        label = { Text(label) },
                    )
                }
            }
            OutlinedButton(
                enabled = historyItems.isNotEmpty(),
                onClick = {
                    dismissActionMenu()
                    showClearConfirmation = true
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                )
                Text(
                    text = "Clear",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Recent",
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                items = visibleItems,
                key = { item -> item.id },
            ) { item ->
                val isActiveActionItem = activeActionItemId == item.id
                HistoryRow(
                    item = item,
                    onClick = {
                        activeActionItemId = item.id
                    },
                    onDeleteClick = { onHistoryItemDeleted(item.id) },
                    showActionMenu = isActiveActionItem,
                    canPreview = item.kind.canPreviewInHistory(),
                    canCopy = item.kind.canCopyFromHistory(),
                    canOpen = item.kind.canOpenFromHistory(payloadActions.canOpen),
                    canSave = payloadSaver.isSupported,
                    canShare = payloadActions.canShare,
                    onDismissActionMenu = ::dismissActionMenu,
                    onPreviewClick = {
                        dismissActionMenu()
                        loadPayload(item.id) { payload ->
                            when {
                                payload.textPreview() != null -> {
                                    previewPayload = payload
                                    showTextPreview = true
                                }
                                payload.canPreviewImage() -> {
                                    previewPayload = payload
                                    showImagePreview = true
                                }
                            }
                        }
                    },
                    onCopyClick = {
                        dismissActionMenu()
                        loadPayload(item.id, ::copyPayload)
                    },
                    onOpenClick = {
                        dismissActionMenu()
                        loadPayload(item.id, ::openPayload)
                    },
                    onSaveClick = {
                        dismissActionMenu()
                        loadPayload(item.id, payloadSaver::save)
                    },
                    onShareClick = {
                        dismissActionMenu()
                        loadPayload(item.id, payloadActions::share)
                    },
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = "Received items are added here immediately, even if you choose an action later.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showTextPreview) {
        TextPreviewDialog(
            preview = previewPayload?.textPreview() ?: TransferTextPreview(emptyList(), false),
            onDismiss = {
                showTextPreview = false
                previewPayload = null
            },
        )
    }
    if (showImagePreview) {
        previewPayload?.let { payload ->
            ImagePreviewDialog(
                payload = payload,
                onDismiss = {
                    showImagePreview = false
                    previewPayload = null
                },
            )
        }
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear history?") },
            text = { Text("This deletes every history record and stored payload from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onHistoryCleared()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun TransferKind.canPreviewInHistory(): Boolean = when (this) {
    TransferKind.Text,
    TransferKind.Link,
    TransferKind.Image,
    -> true
    TransferKind.File -> false
}

private fun TransferKind.canOpenFromHistory(platformCanOpen: Boolean): Boolean = when (this) {
    TransferKind.Link -> true
    TransferKind.Image,
    TransferKind.File,
    -> platformCanOpen
    TransferKind.Text -> false
}

private fun TransferKind.canCopyFromHistory(): Boolean =
    this == TransferKind.Text || this == TransferKind.Link

private const val MAX_CLIPBOARD_COPY_BYTES = 2 * 1_024 * 1_024
