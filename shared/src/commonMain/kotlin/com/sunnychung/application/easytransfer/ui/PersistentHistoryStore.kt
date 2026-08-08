package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import app.cash.sqldelight.db.SqlDriver
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.db.HistoryDatabase
import com.sunnychung.application.easytransfer.ui.model.HistoryItemUi
import com.sunnychung.application.easytransfer.ui.model.TransferStatus
import kotlin.random.Random

internal class PersistentHistoryStore(
    driver: SqlDriver,
    private val payloadStore: HistoryPayloadStore,
) {
    private val database = HistoryDatabase(driver)
    private val queries = database.historyQueries

    fun load(): List<HistoryItemUi> {
        recover()
        return queries.selectAll(::historyRecord).executeAsList()
            .map { record -> record.toHistoryItemUi() }
    }

    fun add(payload: TransferPayload, status: TransferStatus): HistoryItemUi {
        recover()
        val createdAtMillis = currentEpochMillis()
        val id = newHistoryId(createdAtMillis)
        val payloadFileName = "$id.payload"
        val record = HistoryRecord(
            id = id,
            kind = payload.kind,
            status = status,
            title = payload.displayName(),
            detail = "${payload.kind.label} · ${payload.bytes.size.formatByteCount()}",
            sourceLabel = if (status == TransferStatus.Received) "Optical transfer" else "This device",
            payloadFileName = payloadFileName,
            payloadName = payload.name,
            payloadMediaType = payload.mediaType,
            payloadSizeBytes = payload.bytes.size,
            createdAtMillis = createdAtMillis,
        )
        payloadStore.writePayloadAtomically(
            temporaryFileName = "$id.tmp",
            finalFileName = payloadFileName,
            bytes = payload.bytes,
        )
        runCatching {
            queries.transaction {
                queries.insertRecord(
                    id = record.id,
                    kind = record.kind.name,
                    status = record.status.name,
                    title = record.title,
                    detail = record.detail,
                    source_label = record.sourceLabel,
                    payload_file_name = record.payloadFileName,
                    payload_name = record.payloadName,
                    payload_media_type = record.payloadMediaType,
                    payload_size_bytes = record.payloadSizeBytes.toLong(),
                    created_at_millis = record.createdAtMillis,
                )
            }
        }.onFailure {
            payloadStore.deletePayload(payloadFileName)
            throw it
        }
        return record.toHistoryItemUi()
    }

    fun loadPayload(itemId: String): TransferPayload? {
        val record = queries.findById(itemId, ::historyRecord).executeAsOneOrNull() ?: return null
        val bytes = payloadStore.readPayload(record.payloadFileName) ?: return null
        if (bytes.size != record.payloadSizeBytes) return null
        return TransferPayload(
            kind = record.kind,
            bytes = bytes,
            name = record.payloadName,
            mediaType = record.payloadMediaType,
        )
    }

    fun delete(itemId: String) {
        val record = queries.findById(itemId, ::historyRecord).executeAsOneOrNull() ?: return
        queries.transaction {
            queries.deleteById(itemId)
        }
        payloadStore.deletePayload(record.payloadFileName)
    }

    fun clear() {
        queries.transaction {
            queries.deleteAll()
        }
        payloadStore.deleteAllPayloads()
    }

    private fun recover() {
        payloadStore.deleteTemporaryPayloads()
        val records = queries.selectAll(::historyRecord).executeAsList()
        records
            .filter { record -> !payloadStore.payloadExists(record.payloadFileName) }
            .forEach { record ->
                queries.transaction {
                    queries.deleteById(record.id)
                }
            }
        val referencedPayloads = queries.selectPayloadFileNames().executeAsList().toSet()
        payloadStore.deleteOrphanPayloads(referencedPayloads)
    }
}

internal interface HistoryPayloadStore {
    fun payloadExists(fileName: String): Boolean
    fun readPayload(fileName: String): ByteArray?
    fun writePayloadAtomically(
        temporaryFileName: String,
        finalFileName: String,
        bytes: ByteArray,
    )
    fun deletePayload(fileName: String)
    fun deleteAllPayloads()
    fun deleteTemporaryPayloads()
    fun deleteOrphanPayloads(referencedPayloadFileNames: Set<String>)
}

private data class HistoryRecord(
    val id: String,
    val kind: TransferKind,
    val status: TransferStatus,
    val title: String,
    val detail: String,
    val sourceLabel: String,
    val payloadFileName: String,
    val payloadName: String?,
    val payloadMediaType: String?,
    val payloadSizeBytes: Int,
    val createdAtMillis: Long,
)

@Composable
internal expect fun rememberPersistentHistoryStore(): PersistentHistoryStore

internal expect fun currentEpochMillis(): Long

internal expect fun currentLocalHour(): Int

private fun historyRecord(
    id: String,
    kind: String,
    status: String,
    title: String,
    detail: String,
    sourceLabel: String,
    payloadFileName: String,
    payloadName: String?,
    payloadMediaType: String?,
    payloadSizeBytes: Long,
    createdAtMillis: Long,
): HistoryRecord = HistoryRecord(
    id = id,
    kind = enumValueOf(kind),
    status = enumValueOf(status),
    title = title,
    detail = detail,
    sourceLabel = sourceLabel,
    payloadFileName = payloadFileName,
    payloadName = payloadName,
    payloadMediaType = payloadMediaType,
    payloadSizeBytes = payloadSizeBytes.toInt(),
    createdAtMillis = createdAtMillis,
)

private fun HistoryRecord.toHistoryItemUi(): HistoryItemUi = HistoryItemUi(
    id = id,
    title = title,
    detail = detail,
    kind = kind,
    status = status,
    timeLabel = createdAtMillis.toTimeLabel(),
    sourceLabel = sourceLabel,
)

internal fun TransferPayload.displayName(): String = when (kind) {
    TransferKind.Text -> textPreview(maxBytes = 256)?.chunks?.firstOrNull()
        ?.lineSequence()?.firstOrNull()?.take(48).orEmpty().ifBlank { "Text" }
    TransferKind.Link -> textPreview(maxBytes = 256)?.chunks?.firstOrNull()
        .orEmpty().take(48).ifBlank { "Link" }
    TransferKind.Image -> "Received image"
    TransferKind.File -> name ?: "Received file"
}

private fun Long.toTimeLabel(): String {
    val elapsedMillis = (currentEpochMillis() - this).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L
    val elapsedHours = elapsedMillis / 3_600_000L
    val elapsedDays = elapsedMillis / 86_400_000L
    return when {
        elapsedMinutes < 1L -> "Just now"
        elapsedMinutes == 1L -> "1 min ago"
        elapsedMinutes < 60L -> "$elapsedMinutes min ago"
        elapsedHours == 1L -> "1 hour ago"
        elapsedHours < 24L -> "$elapsedHours hours ago"
        elapsedDays == 1L -> "Yesterday"
        elapsedDays < 30L -> "$elapsedDays days ago"
        elapsedDays < 365L -> "${elapsedDays / 30L} months ago"
        else -> "${elapsedDays / 365L} years ago"
    }
}

private fun newHistoryId(createdAtMillis: Long): String =
    "history-$createdAtMillis-${Random.nextInt(from = 100_000, until = 999_999)}"
