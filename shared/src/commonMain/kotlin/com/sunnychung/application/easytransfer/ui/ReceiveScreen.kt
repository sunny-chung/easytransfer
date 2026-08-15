package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sunnychung.application.easytransfer.camera.OpticalCameraSettings
import com.sunnychung.application.easytransfer.camera.OpticalCaptureFps
import com.sunnychung.application.easytransfer.camera.supportedOpticalCaptureFps
import com.sunnychung.application.easytransfer.camera.supportedOpticalCameraWidths
import com.sunnychung.application.easytransfer.camera.supportedOpticalDecodeWorkers
import com.sunnychung.application.easytransfer.optical.OpticalReceiveProgress
import com.sunnychung.application.easytransfer.optical.OpticalReceiveResult
import com.sunnychung.application.easytransfer.optical.OpticalReceiver
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.optical.TransferTextPreview
import com.sunnychung.application.easytransfer.optical.estimateTransferProgress
import com.sunnychung.application.easytransfer.optical.expectedFountainOverhead
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.ui.model.ReceivedItemUi
import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Preview(
    name = "Receive screen",
    widthDp = 800,
    heightDp = 1_000,
)
@Composable
internal fun ReceiveScreen(
    receivedItem: ReceivedItemUi? = PreviewData.receivedItem,
    receivedPayload: TransferPayload? = null,
    showPageTitle: Boolean = true,
    cameraEnabled: Boolean = false,
    onTransferReceived: (TransferPayload) -> Unit = {},
    onDismissReceivePrompt: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var receiveStatsMessage by remember { mutableStateOf<String?>(null) }
    var receiveResetSignal by remember { mutableStateOf(0) }
    BoxWithConstraints(modifier = modifier) {
        val wideContent = maxWidth >= 720.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideContent) 40.dp else 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (showPageTitle) {
                PageHeading(
                    title = "Receive",
                    subtitle = "Point this device at the animated optical code on the sender.",
                )
            }
            ScannerPanel(
                resetSignal = receiveResetSignal,
                cameraEnabled = cameraEnabled && receivedItem == null,
                onTransferReceived = { payload, statsMessage ->
                    receiveStatsMessage = statsMessage
                    onTransferReceived(payload)
                },
                modifier = if (wideContent) Modifier.widthIn(max = 760.dp) else Modifier,
            )
            if (BuildFeatures.SHOW_BLUETOOTH_TRANSFER) {
                OutlinedButton(
                    onClick = {},
                    enabled = BuildFeatures.ENABLE_BLUETOOTH_TRANSFER,
                    modifier = if (wideContent) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Bluetooth, contentDescription = null)
                    Text("Receive by Bluetooth", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (receivedItem != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(if (wideContent) 32.dp else 12.dp),
                contentAlignment = if (wideContent) Alignment.Center else Alignment.BottomCenter,
            ) {
                ReceivedActionPrompt(
                    item = receivedItem,
                    payload = receivedPayload,
                    statsMessage = receiveStatsMessage,
                    onDismiss = {
                        receiveStatsMessage = null
                        receiveResetSignal++
                        onDismissReceivePrompt()
                    },
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
        }
    }
}

@Composable
private fun ScannerPanel(
    cameraEnabled: Boolean,
    resetSignal: Int,
    onTransferReceived: (TransferPayload, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val receiver = remember { OpticalReceiver() }
    var progress by remember { mutableStateOf(OpticalReceiveProgress()) }
    var cameraSettings by remember { mutableStateOf(OpticalCameraSettings()) }
    val cameraFpsOptions = supportedOpticalCaptureFps()
    val effectiveCaptureFps = if (cameraSettings.captureFps in cameraFpsOptions) {
        cameraSettings.captureFps
    } else {
        cameraFpsOptions.preferredCaptureFps()
    }
    val cameraWidthOptions = supportedOpticalCameraWidths(effectiveCaptureFps)
    val decodeWorkerOptions = remember { supportedOpticalDecodeWorkers() }
    var hasAppliedInitialCameraWidth by remember { mutableStateOf(false) }
    var hasAppliedInitialDecodeWorkers by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Waiting for an optical transfer") }
    var rateWindowStart by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var framesAtRateWindowStart by remember { mutableStateOf(0) }
    var uniqueFramesPerSecond by remember { mutableStateOf(0f) }
    var activeSessionId by remember { mutableStateOf<Int?>(null) }
    var transferStartedAt by remember { mutableStateOf<TimeMark?>(null) }
    var completedTotalMillis by remember { mutableStateOf<Long?>(null) }
    var completedStatsMessage by remember { mutableStateOf<String?>(null) }
    KeepScreenAwake(active = cameraEnabled)
    LaunchedEffect(decodeWorkerOptions) {
        val preferredDecodeWorkers = decodeWorkerOptions.firstOrNull { workers -> workers.workerCount == 3 }
            ?: decodeWorkerOptions.lastOrNull()
        when {
            !hasAppliedInitialDecodeWorkers && preferredDecodeWorkers != null -> {
                cameraSettings = cameraSettings.copy(decodeWorkers = preferredDecodeWorkers)
                hasAppliedInitialDecodeWorkers = true
            }
            cameraSettings.decodeWorkers !in decodeWorkerOptions -> {
                cameraSettings = cameraSettings.copy(decodeWorkers = preferredDecodeWorkers ?: cameraSettings.decodeWorkers)
            }
        }
    }
    LaunchedEffect(cameraFpsOptions) {
        if (cameraSettings.captureFps !in cameraFpsOptions) {
            cameraSettings = cameraSettings.copy(
                captureFps = effectiveCaptureFps,
            )
        }
    }
    fun clearReceiverState() {
        receiver.reset()
        progress = OpticalReceiveProgress()
        activeSessionId = null
        transferStartedAt = null
        completedTotalMillis = null
        completedStatsMessage = null
        rateWindowStart = TimeSource.Monotonic.markNow()
        framesAtRateWindowStart = 0
        uniqueFramesPerSecond = 0f
        statusMessage = "Waiting for an optical transfer"
    }
    LaunchedEffect(resetSignal) {
        clearReceiverState()
    }
    LaunchedEffect(cameraWidthOptions) {
        val highestResolution = cameraWidthOptions.maxByOrNull { width ->
            width.width.toLong() * width.height.toLong()
        }
        when {
            !hasAppliedInitialCameraWidth && highestResolution != null -> {
                cameraSettings = cameraSettings.copy(width = highestResolution)
                hasAppliedInitialCameraWidth = true
            }
            cameraSettings.width !in cameraWidthOptions -> {
                cameraSettings = cameraSettings.copy(
                    width = cameraWidthOptions.firstOrNull { it.width >= cameraSettings.width.width }
                        ?: highestResolution
                        ?: cameraSettings.width,
                )
            }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Optical receiver",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = ::clearReceiverState) {
                    Text("Clear")
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(390.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFF202735),
            ) {
                if (cameraEnabled) {
                    OpticalCameraScanner(
                        cameraSettings = cameraSettings,
                        onCodeScanned = { frameBytes ->
                            when (val result = receiver.accept(frameBytes)) {
                                OpticalReceiveResult.Ignored -> Unit
                                is OpticalReceiveResult.Receiving -> {
                                    val nextProgress = result.progress
                                    if (
                                        nextProgress.sessionId != null &&
                                        nextProgress.sessionId != activeSessionId
                                    ) {
                                        activeSessionId = nextProgress.sessionId
                                        transferStartedAt = TimeSource.Monotonic.markNow()
                                        completedTotalMillis = null
                                        completedStatsMessage = null
                                        rateWindowStart = TimeSource.Monotonic.markNow()
                                        framesAtRateWindowStart = 0
                                        uniqueFramesPerSecond = 0f
                                    }
                                    if (nextProgress.uniqueFrames < progress.uniqueFrames) {
                                        rateWindowStart = TimeSource.Monotonic.markNow()
                                        framesAtRateWindowStart = 0
                                        uniqueFramesPerSecond = 0f
                                    }
                                    if (
                                        nextProgress.uniqueFrames != progress.uniqueFrames ||
                                        nextProgress.duplicateFrames / 25 != progress.duplicateFrames / 25
                                    ) {
                                        val elapsedMilliseconds = rateWindowStart.elapsedNow().inWholeMilliseconds
                                        if (elapsedMilliseconds >= 2_000) {
                                            uniqueFramesPerSecond =
                                                (nextProgress.uniqueFrames - framesAtRateWindowStart) *
                                                1_000f / elapsedMilliseconds
                                            framesAtRateWindowStart = nextProgress.uniqueFrames
                                            rateWindowStart = TimeSource.Monotonic.markNow()
                                        }
                                        progress = nextProgress
                                        statusMessage = "Receiving ${nextProgress.uniqueFrames} unique frames"
                                    }
                                }

                                is OpticalReceiveResult.Completed -> {
                                    if (
                                        result.progress.sessionId != null &&
                                        result.progress.sessionId != activeSessionId
                                    ) {
                                        activeSessionId = result.progress.sessionId
                                        transferStartedAt = TimeSource.Monotonic.markNow()
                                    }
                                    progress = result.progress
                                    completedTotalMillis = transferStartedAt
                                        ?.elapsedNow()
                                        ?.inWholeMilliseconds
                                        ?: 0L
                                    completedStatsMessage = result.payload.completedStatsMessage(
                                        totalMillis = completedTotalMillis ?: 0L,
                                        transferredBytes = result.progress.totalLength,
                                    )
                                    statusMessage = "Transfer complete"
                                    onTransferReceived(result.payload, completedStatsMessage.orEmpty())
                                }

                                is OpticalReceiveResult.Corrupt -> {
                                    progress = OpticalReceiveProgress()
                                    activeSessionId = null
                                    transferStartedAt = null
                                    completedTotalMillis = null
                                    completedStatsMessage = null
                                    rateWindowStart = TimeSource.Monotonic.markNow()
                                    framesAtRateWindowStart = 0
                                    uniqueFramesPerSecond = 0f
                                    statusMessage = result.reason
                                }
                            }
                        },
                    )
                } else {
                    CameraPreviewContent()
                }
            }
            val elapsedMillis = completedTotalMillis
                ?: transferStartedAt?.elapsedNow()?.inWholeMilliseconds
                ?: 0L
            val progressEstimate = if (progress.uniqueFrames > 0) {
                estimateTransferProgress(
                    sourceBlocks = progress.sourceBlocks,
                    uniqueFrames = progress.uniqueFrames,
                    elapsedMillis = elapsedMillis,
                    solvedBlocks = progress.recoveredBlocks,
                )
            } else {
                null
            }
            val averageBytesPerSecond = progress.goodputBytesPerSecond(elapsedMillis)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { if (progress.isComplete) 1f else progressEstimate?.fraction?.toFloat() ?: 0f },
            )
            Text(
                text = if (progress.uniqueFrames == 0) {
                    "Keep the full animated code inside the frame."
                } else {
                    "${progress.uniqueFrames} unique frames | " +
                        "${progress.duplicateFrames} duplicates skipped | " +
                        "${progress.recoveredBlocks}/${progress.sourceBlocks} blocks recovered"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress.uniqueFrames > 0) {
                val percent = if (progress.isComplete) 100.0 else (progressEstimate?.fraction ?: 0.0) * 100.0
                Text(
                    text = "${percent.formatPercent()} | " +
                        "${(uniqueFramesPerSecond * 10).roundToInt() / 10f} unique frames/s | " +
                        "expected ${progressEstimate?.expectedFrames ?: progress.typicalUniqueFrameTarget} frames",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${averageBytesPerSecond.formatTransferRate()} | " +
                        "ETA ${progressEstimate?.etaMillis.formatEta()} | " +
                        "elapsed ${elapsedMillis.formatDuration()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            completedTotalMillis?.let { totalMillis ->
                Text(
                    text = completedStatsMessage ?: "Completed in ${totalMillis.formatDuration()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Receiver tuning",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TuningOptionGroup(
                    title = "Camera width",
                    options = cameraWidthOptions,
                    selectedOption = cameraSettings.width,
                    onOptionSelected = { cameraSettings = cameraSettings.copy(width = it) },
                    optionLabel = { it.label },
                    columns = 4,
                )
                TuningOptionGroup(
                    title = "Capture FPS",
                    options = cameraFpsOptions,
                    selectedOption = cameraSettings.captureFps,
                    onOptionSelected = { cameraSettings = cameraSettings.copy(captureFps = it) },
                    optionLabel = { "${it.label} fps" },
                    columns = 2,
                )
                if (decodeWorkerOptions.size > 1) {
                    TuningOptionGroup(
                        title = "Decode workers",
                        options = decodeWorkerOptions,
                        selectedOption = cameraSettings.decodeWorkers,
                        onOptionSelected = { cameraSettings = cameraSettings.copy(decodeWorkers = it) },
                        optionLabel = { it.label },
                        columns = 2,
                    )
                }
            }
        }
    }
}

private fun OpticalReceiveProgress.goodputBytesPerSecond(elapsedMillis: Long): Double {
    if (frameBytes <= 0 || uniqueFrames <= 0) return 0.0
    val overhead = expectedFountainOverhead(sourceBlocks)
    val effectiveSeconds = maxOf(0.001, elapsedMillis / 1_000.0)
    return uniqueFrames.toDouble() * blockLength / overhead / effectiveSeconds
}

private fun Double.formatTransferRate(): String = when {
    this >= 1_024.0 -> "${((this / 102.4).roundToInt() / 10.0)} KB/s"
    this > 0.0 -> "${roundToInt()} B/s"
    else -> "0 B/s"
}

private fun Double.formatPercent(): String = if (this < 10.0) {
    "${((this * 10).roundToInt() / 10.0)}%"
} else {
    "${roundToInt()}%"
}

private fun Long?.formatEta(): String = this?.formatDuration() ?: "calculating"

private fun TransferPayload.completedStatsMessage(
    totalMillis: Long,
    transferredBytes: Int,
): String {
    val effectiveMillis = totalMillis.coerceAtLeast(1L)
    val averageSpeed = transferredBytes.toDouble() * 1_000.0 / effectiveMillis
    val sizeSummary = if (transferredBytes != bytes.size) {
        "transferred ${transferredBytes.formatByteCount()} | payload ${bytes.size.formatByteCount()}"
    } else {
        bytes.size.formatByteCount()
    }
    return "Completed in ${totalMillis.formatDuration()} | " +
        "average ${averageSpeed.formatTransferRate()} | " +
        sizeSummary
}

private fun Long.formatDuration(): String {
    val totalSeconds = ((this + 999L) / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

@Composable
internal expect fun OpticalCameraScanner(
    cameraSettings: OpticalCameraSettings,
    onCodeScanned: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
private fun CameraPreviewContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CameraTargetOverlay()
        Text(
            text = "Camera appears here when receiving",
            modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
internal fun CameraTargetOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(230.dp),
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
            border = BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)),
        ) {}
    }
}

@Composable
internal fun CameraMessage(
    message: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(clickableModifier)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
        Text(
            text = message,
            modifier = Modifier.padding(top = 16.dp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ReceivedActionPrompt(
    item: ReceivedItemUi,
    payload: TransferPayload?,
    statsMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showTextPreview by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    val payloadSaver = rememberPayloadSaver(
        onSaved = { actionMessage = "Saved successfully" },
        onError = { actionMessage = it },
    )
    val payloadActions = rememberPayloadActions(onError = { actionMessage = it })
    val primaryLabel = when (item.kind) {
        TransferKind.Text -> "Preview text"
        TransferKind.Link -> "Open link"
        TransferKind.Image -> "Open image"
        TransferKind.File -> "Open file"
    }
    val primaryIcon = when (item.kind) {
        TransferKind.Text -> Icons.Outlined.Visibility
        TransferKind.Link -> Icons.AutoMirrored.Outlined.OpenInNew
        TransferKind.Image,
        TransferKind.File,
        -> Icons.AutoMirrored.Outlined.OpenInNew
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Received successfully",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Added to History",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                NavigationIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransferKindBadge(kind = item.kind, emphasized = true)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${item.detail} · ${item.sizeLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "From ${item.sourceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        statsMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
            Text(
                text = "What would you like to do?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = {
                    when (payload?.kind) {
                        TransferKind.Text -> showTextPreview = true
                        TransferKind.Link -> payload.textPreview(maxBytes = 16 * 1_024)
                            ?.chunks?.joinToString(separator = "")
                            ?.let { uri ->
                                runCatching { uriHandler.openUri(uri) }
                                    .onFailure { actionMessage = "No app could open this URI." }
                            }
                        TransferKind.Image,
                        TransferKind.File,
                        -> payloadActions.open(payload)

                        null -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = payload == null ||
                    payload.kind == TransferKind.Text ||
                    payload.kind == TransferKind.Link ||
                    payloadActions.canOpen,
            ) {
                Icon(imageVector = primaryIcon, contentDescription = null)
                Text(text = primaryLabel, modifier = Modifier.padding(start = 8.dp))
            }
            if (payload?.kind == TransferKind.Text) {
                OutlinedButton(
                    onClick = {
                        if (payload.bytes.size <= 2 * 1_024 * 1_024) {
                            clipboardManager.setText(AnnotatedString(payload.text().orEmpty()))
                            actionMessage = "Copied to clipboard"
                        } else {
                            actionMessage = "This text is too large to copy safely. Save it as a file instead."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("Copy text", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (payload?.kind == TransferKind.Link) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(payload.text().orEmpty()))
                        actionMessage = "Copied to clipboard"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text("Copy link", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { showTextPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null)
                    Text("Preview URI", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (payload?.canPreviewImage() == true) {
                OutlinedButton(
                    onClick = { showImagePreview = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null)
                    Text("Preview image", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (payload?.canPreviewVideo() == true) {
                OutlinedButton(
                    onClick = { payloadActions.open(payload) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = payloadActions.canOpen,
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null)
                    Text("Preview video", modifier = Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(
                onClick = { payload?.let(payloadSaver::save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = payload != null && payloadSaver.isSupported,
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Text("Save as", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { payload?.let(payloadActions::share) },
                modifier = Modifier.fillMaxWidth(),
                enabled = payload != null && payloadActions.canShare,
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Text("Share to", modifier = Modifier.padding(start = 8.dp))
            }
            actionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done for now")
            }
        }
    }

    if (showTextPreview && payload != null) {
        TextPreviewDialog(
            preview = payload.textPreview() ?: TransferTextPreview(emptyList(), false),
            onDismiss = { showTextPreview = false },
        )
    }
    if (showImagePreview && payload != null) {
        ImagePreviewDialog(
            payload = payload,
            onDismiss = { showImagePreview = false },
        )
    }
}

@Composable
internal fun TextPreviewDialog(
    preview: TransferTextPreview,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Text preview",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    NavigationIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close preview",
                        onClick = onDismiss,
                    )
                }
                if (preview.isTruncated) {
                    Text(
                        text = "Showing the first 512 KB. Save the file to view the complete text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(preview.chunks) { chunk ->
                        Text(text = chunk, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
internal fun ImagePreviewDialog(
    payload: TransferPayload,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var imageBitmap by remember(payload.bytes) { mutableStateOf<ImageBitmap?>(null) }
    var isDecoding by remember(payload.bytes) { mutableStateOf(true) }
    LaunchedEffect(payload.bytes) {
        isDecoding = true
        imageBitmap = withContext(Dispatchers.Default) {
            payload.bytes.decodePreviewImageBitmap()
        }
        isDecoding = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Image preview",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    NavigationIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Close preview",
                        onClick = onDismiss,
                    )
                }
                val decodedImageBitmap = imageBitmap
                if (isDecoding) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (decodedImageBitmap != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = decodedImageBitmap,
                            contentDescription = payload.safeSuggestedFileName(),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "This image could not be previewed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

internal fun TransferPayload.canPreviewImage(): Boolean =
    kind == TransferKind.Image || actionMediaType().startsWith("image/")

internal fun TransferPayload.canPreviewVideo(): Boolean =
    false // actionMediaType().startsWith("video/")

private fun List<OpticalCaptureFps>.preferredCaptureFps(): OpticalCaptureFps =
    maxByOrNull { fps -> fps.framesPerSecond }
        ?: OpticalCaptureFps.Fps30
