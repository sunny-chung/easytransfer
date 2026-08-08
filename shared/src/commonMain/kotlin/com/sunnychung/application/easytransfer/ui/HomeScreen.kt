package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunnychung.application.easytransfer.ui.model.HistoryItemUi
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.optical.TransferKind

@Preview(
    name = "Home screen",
    widthDp = 800,
    heightDp = 1_000,
)
@Composable
internal fun HomeScreen(
    historyItems: List<HistoryItemUi> = PreviewData.historyItems,
    showPageTitle: Boolean = true,
    onTransferKindSelected: (TransferKind) -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val greeting = greetingForHour(currentLocalHour())
    val uriHandler = LocalUriHandler.current
    BoxWithConstraints(modifier = modifier) {
        val wideContent = maxWidth >= 720.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideContent) 40.dp else 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (showPageTitle) {
                PageHeading(
                    title = greeting,
                    subtitle = "Move anything between screens with light and a camera.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "What would you like to move?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SendHero(
                onTransferKindSelected = onTransferKindSelected,
                wideLayout = wideContent,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(title = "Receive")
                ReceiveMethodCard(
                    title = "Receive optical transfer",
                    description = "Use this device's camera to scan an animated code",
                    icon = Icons.Outlined.QrCodeScanner,
                    onClick = onReceiveClick,
                    modifier = if (wideContent) Modifier.fillMaxWidth(0.5f) else Modifier,
                )
                if (BuildFeatures.SHOW_BLUETOOTH_TRANSFER) {
                    ReceiveMethodCard(
                        title = "Receive by Bluetooth",
                        description = "Receive from a nearby paired device",
                        icon = Icons.Outlined.Bluetooth,
                        onClick = {},
                        enabled = BuildFeatures.ENABLE_BLUETOOTH_TRANSFER,
                        modifier = if (wideContent) Modifier.fillMaxWidth(0.5f) else Modifier,
                    )
                }
            }

            if (!wideContent && historyItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading(
                        title = "Recent",
                        actionLabel = "View all",
                        onActionClick = onHistoryClick,
                    )
                    historyItems.take(3).forEach { item ->
                        HistoryRow(item = item)
                    }
                }
            }

            ProtocolAcknowledgement(
                onAuthorClick = {
                    uriHandler.openUri("https://github.com/bashalarmistalt/decimen-optical-transfer/")
                },
            )
        }
    }
}

@Composable
private fun ProtocolAcknowledgement(
    onAuthorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Optical transfer protocol is developed by",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAuthorClick) {
            Text("Bash Alarmist")
        }
    }
}

private fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

@Composable
private fun SendHero(
    onTransferKindSelected: (TransferKind) -> Unit,
    wideLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(
            modifier = Modifier.padding(if (wideLayout) 28.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "Send something",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Choose what you have. We’ll guide you from there.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                )
            }
            if (wideLayout) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransferKind.entries.forEach { kind ->
                        TransferKindCard(
                            kind = kind,
                            onClick = { onTransferKindSelected(kind) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransferKind.entries.chunked(2).forEach { rowKinds ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowKinds.forEach { kind ->
                                TransferKindCard(
                                    kind = kind,
                                    onClick = { onTransferKindSelected(kind) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferKindCard(
    kind: TransferKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.13f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransferKindBadge(
                kind = kind,
                emphasized = true,
            )
            Text(
                text = kind.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReceiveMethodCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(11.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Start",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
