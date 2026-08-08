package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sunnychung.application.easytransfer.optical.OpticalSender
import com.sunnychung.application.easytransfer.optical.OpticalCompressionMode
import com.sunnychung.application.easytransfer.optical.OpticalErrorCorrection
import com.sunnychung.application.easytransfer.optical.OpticalFrameSize
import com.sunnychung.application.easytransfer.optical.OpticalTransferSettings
import com.sunnychung.application.easytransfer.optical.OpticalTxFps
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.optical.TransferKind
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
internal class SendDraftState {
    var textPayload by mutableStateOf("")
    var linkPayload by mutableStateOf("")
    var selectedFile by mutableStateOf<TransferPayload?>(null)
    var fileError by mutableStateOf<String?>(null)
    var activeTransfer by mutableStateOf<TransferPayload?>(null)
    var transferSettings by mutableStateOf(OpticalTransferSettings())
}

@Composable
internal fun rememberSendDraftState(): SendDraftState = remember { SendDraftState() }

@Preview(
    name = "Send screen",
    widthDp = 800,
    heightDp = 1_000,
)
@Composable
internal fun SendScreen(
    selectedKind: TransferKind? = TransferKind.Text,
    showPageTitle: Boolean = true,
    onTransferKindSelected: (TransferKind) -> Unit = {},
    onTransferStarted: (TransferPayload) -> Unit = {},
    draftState: SendDraftState? = null,
    modifier: Modifier = Modifier,
) {
    val activeKind = selectedKind ?: TransferKind.Text
    val draft = draftState ?: rememberSendDraftState()
    val coroutineScope = rememberCoroutineScope()
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) {
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        TransferPayload(
                            kind = TransferKind.Image,
                            bytes = file.readBytes(),
                            name = file.name,
                            mediaType = file.mimeType()?.toString()?.takeIf { mediaType ->
                                mediaType.startsWith("image/")
                            } ?: "image/*",
                        )
                    }
                }.onSuccess {
                    draft.selectedFile = it
                    draft.fileError = null
                }.onFailure {
                    draft.fileError = "That image could not be read. Choose another image."
                }
            }
        }
    }
    val filePicker = rememberFilePickerLauncher(type = FileKitType.File()) { file ->
        if (file != null) {
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        TransferPayload(
                            kind = TransferKind.File,
                            bytes = file.readBytes(),
                            name = file.name,
                            mediaType = file.mimeType()?.toString(),
                        )
                    }
                }.onSuccess {
                    draft.selectedFile = it
                    draft.fileError = null
                }.onFailure {
                    draft.fileError = "That file could not be read. Choose another file."
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val wideContent = maxWidth >= 720.dp
        val selectedFileForKind = draft.selectedFile?.takeIf { payload -> payload.kind == activeKind }
        val readyPayload = when (activeKind) {
            TransferKind.Text -> draft.textPayload.takeIf { it.isNotBlank() }?.let(TransferPayload::text)
            TransferKind.Link -> draft.linkPayload.takeIf { it.isValidTransferUri() }?.let(TransferPayload::link)
            TransferKind.Image,
            TransferKind.File,
            -> selectedFileForKind
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wideContent) 40.dp else 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (showPageTitle) {
                PageHeading(
                    title = "Send",
                    subtitle = "Choose content and show its animated optical code to another device.",
                )
            }
            TransferKindSelector(
                activeKind = activeKind,
                useCompactGrid = !wideContent,
                onTransferKindSelected = onTransferKindSelected,
            )
            if (wideContent) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PayloadCard(
                        kind = activeKind,
                        textPayload = draft.textPayload,
                        linkPayload = draft.linkPayload,
                        selectedFile = selectedFileForKind,
                        fileError = draft.fileError,
                        onTextPayloadChange = { draft.textPayload = it },
                        onLinkPayloadChange = { draft.linkPayload = it },
                        onChooseFile = {
                            if (activeKind == TransferKind.Image) imagePicker.launch() else filePicker.launch()
                        },
                        modifier = Modifier.weight(1.2f),
                    )
                    SendMethodCard(
                        isContentReady = readyPayload != null,
                        transferSettings = draft.transferSettings,
                        onTransferSettingsChanged = { draft.transferSettings = it },
                        onStartTransfer = {
                            readyPayload?.let { payload ->
                                draft.activeTransfer = payload
                                onTransferStarted(payload)
                            }
                        },
                        modifier = Modifier.weight(0.8f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PayloadCard(
                        kind = activeKind,
                        textPayload = draft.textPayload,
                        linkPayload = draft.linkPayload,
                        selectedFile = selectedFileForKind,
                        fileError = draft.fileError,
                        onTextPayloadChange = { draft.textPayload = it },
                        onLinkPayloadChange = { draft.linkPayload = it },
                        onChooseFile = {
                            if (activeKind == TransferKind.Image) imagePicker.launch() else filePicker.launch()
                        },
                    )
                    SendMethodCard(
                        isContentReady = readyPayload != null,
                        transferSettings = draft.transferSettings,
                        onTransferSettingsChanged = { draft.transferSettings = it },
                        onStartTransfer = {
                            readyPayload?.let { payload ->
                                draft.activeTransfer = payload
                                onTransferStarted(payload)
                            }
                        },
                    )
                }
            }
        }
    }

    draft.activeTransfer?.let { payload ->
        OpticalSendDialog(
            payload = payload,
            transferSettings = draft.transferSettings,
            onDismiss = { draft.activeTransfer = null },
        )
    }
}

@Composable
private fun TransferKindSelector(
    activeKind: TransferKind,
    useCompactGrid: Boolean,
    onTransferKindSelected: (TransferKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (useCompactGrid) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransferKind.entries.chunked(2).forEach { kinds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    kinds.forEach { kind ->
                        TransferKindButton(
                            kind = kind,
                            selected = activeKind == kind,
                            onClick = { onTransferKindSelected(kind) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransferKind.entries.forEach { kind ->
                TransferKindButton(
                    kind = kind,
                    selected = activeKind == kind,
                    onClick = { onTransferKindSelected(kind) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TransferKindButton(
    kind: TransferKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransferKindBadge(kind = kind, modifier = Modifier.size(28.dp))
            Text(
                text = kind.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PayloadCard(
    kind: TransferKind,
    textPayload: String,
    linkPayload: String,
    selectedFile: TransferPayload?,
    fileError: String?,
    onTextPayloadChange: (String) -> Unit,
    onLinkPayloadChange: (String) -> Unit,
    onChooseFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Add ${kind.label.lowercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = kind.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (kind) {
                TransferKind.Text -> TextPayloadField(
                    kind = kind,
                    value = textPayload,
                    onValueChange = onTextPayloadChange,
                )

                TransferKind.Link -> TextPayloadField(
                    kind = kind,
                    value = linkPayload,
                    onValueChange = onLinkPayloadChange,
                )

                TransferKind.Image,
                TransferKind.File,
                -> FileDropArea(
                    kind = kind,
                    selectedFile = selectedFile,
                    onChooseFile = onChooseFile,
                )
            }
            fileError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "Nothing leaves this device until you start the optical transfer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextPayloadField(
    kind: TransferKind,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLinkInvalid = kind == TransferKind.Link && value.isNotBlank() && !value.isValidTransferUri()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (kind == TransferKind.Text) 154.dp else 64.dp),
        label = { Text(if (kind == TransferKind.Text) "Message" else "Link or URI") },
        supportingText = if (isLinkInvalid) {
            { Text("Enter a complete URI with a scheme, such as https:, mailto:, or myapp:") }
        } else {
            null
        },
        isError = isLinkInvalid,
        singleLine = kind == TransferKind.Link,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun FileDropArea(
    kind: TransferKind,
    selectedFile: TransferPayload?,
    onChooseFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .clickable(onClick = onChooseFile),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = selectedFile?.name
                    ?: if (kind == TransferKind.Image) "Choose an image" else "Choose a file",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = selectedFile?.bytes?.size?.formatByteCount() ?: "Tap to browse this device",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SendMethodCard(
    isContentReady: Boolean,
    transferSettings: OpticalTransferSettings,
    onTransferSettingsChanged: (OpticalTransferSettings) -> Unit,
    onStartTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text(
                text = "Optical transfer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show animated code",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Keep this screen facing the receiving camera",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Transfer tuning",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TuningOptionGroup(
                    title = "TX FPS",
                    options = OpticalTxFps.entries.toList(),
                    selectedOption = transferSettings.txFps,
                    onOptionSelected = { onTransferSettingsChanged(transferSettings.copy(txFps = it)) },
                    optionLabel = { "${it.label} fps" },
                    columns = 3,
                )
                TuningOptionGroup(
                    title = "Bytes per frame",
                    options = OpticalFrameSize.entries.toList(),
                    selectedOption = transferSettings.frameSize,
                    onOptionSelected = { onTransferSettingsChanged(transferSettings.copy(frameSize = it)) },
                    optionLabel = { it.label },
                    columns = 3,
                )
                TuningOptionGroup(
                    title = "Error correction",
                    options = OpticalErrorCorrection.entries.toList(),
                    selectedOption = transferSettings.errorCorrection,
                    onOptionSelected = { onTransferSettingsChanged(transferSettings.copy(errorCorrection = it)) },
                    optionLabel = { it.label },
                    columns = 4,
                )
                TuningOptionGroup(
                    title = "Compression",
                    options = OpticalCompressionMode.entries.toList(),
                    selectedOption = transferSettings.compressionMode,
                    onOptionSelected = { onTransferSettingsChanged(transferSettings.copy(compressionMode = it)) },
                    optionLabel = { it.label },
                    columns = 2,
                )
                Text(
                    text = "Compression uses gzip only when it reduces the official DCF2 payload.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (BuildFeatures.SHOW_BLUETOOTH_TRANSFER) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = BuildFeatures.ENABLE_BLUETOOTH_TRANSFER && isContentReady,
                ) {
                    Icon(Icons.Outlined.Bluetooth, contentDescription = null)
                    Text("Send by Bluetooth", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (!transferSettings.canRenderRawFrame) {
                Text(
                    text = "ECC ${transferSettings.errorCorrection.label} supports up to " +
                        "${transferSettings.qrByteCapacity} bytes per QR frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onStartTransfer,
                modifier = Modifier.fillMaxWidth(),
                enabled = isContentReady && transferSettings.canRenderRawFrame,
            ) {
                Text(
                    when {
                        !isContentReady -> "Add content to continue"
                        !transferSettings.canRenderRawFrame -> "Choose a lower frame size or ECC"
                        else -> "Start optical transfer"
                    },
                )
            }
        }
    }
}

@Composable
private fun OpticalSendDialog(
    payload: TransferPayload,
    transferSettings: OpticalTransferSettings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
//    BoostScreenBrightness(active = true)
    KeepScreenAwake(active = true)
    var sender by remember(payload, transferSettings) { mutableStateOf<OpticalSender?>(null) }
    var senderErrorMessage by remember(payload, transferSettings) { mutableStateOf<String?>(null) }
    var frame by remember(payload, transferSettings) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(payload, transferSettings) {
        sender = null
        senderErrorMessage = null
        frame = null
        delay(120)
        val senderResult = withContext(Dispatchers.Default) {
            runCatching {
                OpticalSender(
                    payload = payload,
                    blockLength = transferSettings.blockLength,
                    compressionEnabled = transferSettings.isCompressionEnabled,
                )
            }
        }
        senderResult
            .onSuccess { preparedSender ->
                sender = preparedSender
                frame = withContext(Dispatchers.Default) {
                    preparedSender.nextFrame()
                }
            }
            .onFailure { cause ->
                senderErrorMessage = cause.message ?: "Transfer cannot start"
            }
    }
    LaunchedEffect(sender, transferSettings) {
        val activeSender = sender ?: return@LaunchedEffect
        while (true) {
            delay(transferSettings.frameIntervalMillis)
            frame = withContext(Dispatchers.Default) {
                activeSender.nextFrame()
            }
        }
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sending ${payload.name ?: payload.kind.label.lowercase()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Keep the code visible until the receiver finishes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NavigationIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "Stop sending",
                        onClick = onDismiss,
                    )
                }
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentFrame = frame
                    val qrSide = minOf(maxWidth, maxHeight)
                    if (!transferSettings.canRenderRawFrame) {
                        Text(
                            text = "This ECC level cannot encode ${transferSettings.frameBytes} bytes per QR frame.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    } else if (currentFrame != null) {
                        ByteQrCodeImage(
                            frame = currentFrame,
                            errorCorrection = transferSettings.errorCorrection,
                            contentDescription = "Animated optical transfer code",
                            modifier = Modifier
                                .size(qrSide)
                                .background(Color.White)
                                .padding(12.dp),
                        )
                    } else {
                        if (senderErrorMessage == null) {
                            CameraMessage(message = "Preparing optical transfer")
                        } else {
                            Text(
                                text = senderErrorMessage ?: "Transfer cannot start",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val preparedSender = sender
                    if (preparedSender != null) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = if (preparedSender != null) {
                            "${transferSettings.framesPerSecond} fps | " +
                                "${transferSettings.frameBytes} bytes/frame | " +
                                "ECC ${transferSettings.errorCorrection.label} | " +
                                "compression ${preparedSender.compression.label} | " +
                                "${preparedSender.originalPayloadLength.formatByteCount()} → " +
                                preparedSender.transmittedPayloadLength.formatByteCount()
                        } else {
                            senderErrorMessage ?: "Preparing transfer"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop sending")
                }
            }
        }
    }
}

internal fun Int.formatByteCount(): String = when {
    this >= 1_048_576 -> "${(this / 104_857.6).toInt() / 10.0} MB"
    this >= 1_024 -> "${(this / 102.4).toInt() / 10.0} KB"
    else -> "$this bytes"
}
