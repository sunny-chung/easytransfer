package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunnychung.application.easytransfer.ui.model.AppSection
import com.sunnychung.application.easytransfer.ui.model.EasyTransferUiState
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.optical.TransferPayload

private enum class WindowSize {
    Compact,
    Medium,
    Expanded,
}

private val PrimarySections = listOf(
    AppSection.Home,
    AppSection.Send,
    AppSection.Receive,
    AppSection.History,
)

@Preview(
    name = "Adaptive app screen",
    widthDp = 1_024,
    heightDp = 768,
)
@Composable
fun EasyTransferScreen(
    state: EasyTransferUiState = EasyTransferUiState(
        historyItems = PreviewData.historyItems,
    ),
    onSectionSelected: (AppSection) -> Unit = {},
    onTransferKindSelected: (TransferKind) -> Unit = {},
    onTransferStarted: (TransferPayload) -> Unit = {},
    onTransferReceived: (TransferPayload) -> Unit = {},
    onDismissReceivePrompt: () -> Unit = {},
    onHistoryItemDeleted: (String) -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    onHistoryPayloadRequested: (String) -> TransferPayload? = { null },
    cameraEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sendDraftState = remember { SendDraftState() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        val windowSize = when {
            maxWidth < 600.dp -> WindowSize.Compact
            maxWidth < 1_100.dp -> WindowSize.Medium
            else -> WindowSize.Expanded
        }

        when (windowSize) {
            WindowSize.Compact -> CompactAppLayout(
                state = state,
                onSectionSelected = onSectionSelected,
                onTransferKindSelected = onTransferKindSelected,
                onTransferStarted = onTransferStarted,
                onTransferReceived = onTransferReceived,
                onDismissReceivePrompt = onDismissReceivePrompt,
                onHistoryItemDeleted = onHistoryItemDeleted,
                onHistoryCleared = onHistoryCleared,
                onHistoryPayloadRequested = onHistoryPayloadRequested,
                cameraEnabled = cameraEnabled,
                sendDraftState = sendDraftState,
            )

            WindowSize.Medium -> MediumAppLayout(
                state = state,
                onSectionSelected = onSectionSelected,
                onTransferKindSelected = onTransferKindSelected,
                onTransferStarted = onTransferStarted,
                onTransferReceived = onTransferReceived,
                onDismissReceivePrompt = onDismissReceivePrompt,
                onHistoryItemDeleted = onHistoryItemDeleted,
                onHistoryCleared = onHistoryCleared,
                onHistoryPayloadRequested = onHistoryPayloadRequested,
                cameraEnabled = cameraEnabled,
                sendDraftState = sendDraftState,
            )

            WindowSize.Expanded -> ExpandedAppLayout(
                state = state,
                onSectionSelected = onSectionSelected,
                onTransferKindSelected = onTransferKindSelected,
                onTransferStarted = onTransferStarted,
                onTransferReceived = onTransferReceived,
                onDismissReceivePrompt = onDismissReceivePrompt,
                onHistoryItemDeleted = onHistoryItemDeleted,
                onHistoryCleared = onHistoryCleared,
                onHistoryPayloadRequested = onHistoryPayloadRequested,
                cameraEnabled = cameraEnabled,
                sendDraftState = sendDraftState,
            )
        }
    }
}

@Composable
private fun CompactAppLayout(
    state: EasyTransferUiState,
    onSectionSelected: (AppSection) -> Unit,
    onTransferKindSelected: (TransferKind) -> Unit,
    onTransferStarted: (TransferPayload) -> Unit,
    onTransferReceived: (TransferPayload) -> Unit,
    onDismissReceivePrompt: () -> Unit,
    onHistoryItemDeleted: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onHistoryPayloadRequested: (String) -> TransferPayload?,
    cameraEnabled: Boolean,
    sendDraftState: SendDraftState,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompactHeader(title = state.selectedSection.label)
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                PrimarySections.forEach { section ->
                    NavigationBarItem(
                        selected = state.selectedSection == section,
                        onClick = { onSectionSelected(section) },
                        icon = {
                            Icon(
                                imageVector = section.icon(),
                                contentDescription = null,
                            )
                        },
                        label = { Text(section.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        AppSectionContent(
            state = state,
            onSectionSelected = onSectionSelected,
            onTransferKindSelected = onTransferKindSelected,
            onTransferStarted = onTransferStarted,
            onTransferReceived = onTransferReceived,
            onDismissReceivePrompt = onDismissReceivePrompt,
            onHistoryItemDeleted = onHistoryItemDeleted,
            onHistoryCleared = onHistoryCleared,
            onHistoryPayloadRequested = onHistoryPayloadRequested,
            cameraEnabled = cameraEnabled,
            sendDraftState = sendDraftState,
            showPageTitle = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}

@Composable
private fun MediumAppLayout(
    state: EasyTransferUiState,
    onSectionSelected: (AppSection) -> Unit,
    onTransferKindSelected: (TransferKind) -> Unit,
    onTransferStarted: (TransferPayload) -> Unit,
    onTransferReceived: (TransferPayload) -> Unit,
    onDismissReceivePrompt: () -> Unit,
    onHistoryItemDeleted: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onHistoryPayloadRequested: (String) -> TransferPayload?,
    cameraEnabled: Boolean,
    sendDraftState: SendDraftState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AppNavigationRail(
            selectedSection = state.selectedSection,
            onSectionSelected = onSectionSelected,
        )
        AppSectionContent(
            state = state,
            onSectionSelected = onSectionSelected,
            onTransferKindSelected = onTransferKindSelected,
            onTransferStarted = onTransferStarted,
            onTransferReceived = onTransferReceived,
            onDismissReceivePrompt = onDismissReceivePrompt,
            onHistoryItemDeleted = onHistoryItemDeleted,
            onHistoryCleared = onHistoryCleared,
            onHistoryPayloadRequested = onHistoryPayloadRequested,
            cameraEnabled = cameraEnabled,
            sendDraftState = sendDraftState,
            showPageTitle = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpandedAppLayout(
    state: EasyTransferUiState,
    onSectionSelected: (AppSection) -> Unit,
    onTransferKindSelected: (TransferKind) -> Unit,
    onTransferStarted: (TransferPayload) -> Unit,
    onTransferReceived: (TransferPayload) -> Unit,
    onDismissReceivePrompt: () -> Unit,
    onHistoryItemDeleted: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onHistoryPayloadRequested: (String) -> TransferPayload?,
    cameraEnabled: Boolean,
    sendDraftState: SendDraftState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AppSidebar(
            selectedSection = state.selectedSection,
            onSectionSelected = onSectionSelected,
        )
        AppSectionContent(
            state = state,
            onSectionSelected = onSectionSelected,
            onTransferKindSelected = onTransferKindSelected,
            onTransferStarted = onTransferStarted,
            onTransferReceived = onTransferReceived,
            onDismissReceivePrompt = onDismissReceivePrompt,
            onHistoryItemDeleted = onHistoryItemDeleted,
            onHistoryCleared = onHistoryCleared,
            onHistoryPayloadRequested = onHistoryPayloadRequested,
            cameraEnabled = cameraEnabled,
            sendDraftState = sendDraftState,
            showPageTitle = true,
            modifier = Modifier.weight(1f),
        )
        ActivityPanel(
            historyItems = state.historyItems,
            onViewAllClick = { onSectionSelected(AppSection.History) },
            onHistoryItemDeleted = onHistoryItemDeleted,
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun CompactHeader(
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppNavigationRail(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            BrandMark(modifier = Modifier.padding(vertical = 20.dp))
        },
    ) {
        PrimarySections.forEach { section ->
            NavigationRailItem(
                selected = selectedSection == section,
                onClick = { onSectionSelected(section) },
                icon = {
                    Icon(
                        imageVector = section.icon(),
                        contentDescription = null,
                    )
                },
                label = { Text(section.label) },
            )
        }
    }
}

@Composable
private fun AppSidebar(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Text(
                text = "EasyTransfer",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 20.dp),
        ) {
            PrimarySections.forEach { section ->
                SidebarItem(
                    label = section.label,
                    icon = section.icon(),
                    selected = selectedSection == section,
                    onClick = { onSectionSelected(section) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            text = "This device",
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Studio Desktop",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Ready to receive",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun AppSectionContent(
    state: EasyTransferUiState,
    onSectionSelected: (AppSection) -> Unit,
    onTransferKindSelected: (TransferKind) -> Unit,
    onTransferStarted: (TransferPayload) -> Unit,
    onTransferReceived: (TransferPayload) -> Unit,
    onDismissReceivePrompt: () -> Unit,
    onHistoryItemDeleted: (String) -> Unit,
    onHistoryCleared: () -> Unit,
    onHistoryPayloadRequested: (String) -> TransferPayload?,
    cameraEnabled: Boolean,
    sendDraftState: SendDraftState,
    showPageTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    when (state.selectedSection) {
        AppSection.Home -> HomeScreen(
            historyItems = state.historyItems,
            showPageTitle = showPageTitle,
            onTransferKindSelected = onTransferKindSelected,
            onReceiveClick = { onSectionSelected(AppSection.Receive) },
            onHistoryClick = { onSectionSelected(AppSection.History) },
            modifier = modifier,
        )

        AppSection.Send -> SendScreen(
            selectedKind = state.selectedTransferKind,
            showPageTitle = showPageTitle,
            onTransferKindSelected = onTransferKindSelected,
            onTransferStarted = onTransferStarted,
            draftState = sendDraftState,
            modifier = modifier,
        )

        AppSection.Receive -> ReceiveScreen(
            receivedItem = state.receivedItem,
            receivedPayload = state.receivedPayload,
            showPageTitle = showPageTitle,
            onTransferReceived = onTransferReceived,
            onDismissReceivePrompt = onDismissReceivePrompt,
            cameraEnabled = cameraEnabled,
            modifier = modifier,
        )

        AppSection.History -> HistoryScreen(
            historyItems = state.historyItems,
            showPageTitle = showPageTitle,
            onHistoryItemDeleted = onHistoryItemDeleted,
            onHistoryCleared = onHistoryCleared,
            onHistoryPayloadRequested = onHistoryPayloadRequested,
            modifier = modifier,
        )

        AppSection.Settings -> SettingsScreen(
            showPageTitle = showPageTitle,
            modifier = modifier,
        )
    }
}

private fun AppSection.icon(): ImageVector = when (this) {
    AppSection.Home -> Icons.Outlined.Home
    AppSection.Send -> Icons.AutoMirrored.Outlined.Send
    AppSection.Receive -> Icons.Outlined.QrCodeScanner
    AppSection.History -> Icons.Outlined.History
    AppSection.Settings -> Icons.Outlined.Settings
}
