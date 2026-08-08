package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sunnychung.application.easytransfer.db.HistoryDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

@Composable
internal actual fun rememberPersistentHistoryStore(): PersistentHistoryStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        PersistentHistoryStore(
            driver = AndroidSqliteDriver(
                schema = HistoryDatabase.Schema,
                context = context,
                name = "history.db",
            ),
            payloadStore = JavaHistoryPayloadStore(File(context.filesDir, "history/payloads")),
        )
    }
}

internal actual fun currentEpochMillis(): Long = System.currentTimeMillis()

internal actual fun currentLocalHour(): Int =
    Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

private class JavaHistoryPayloadStore(
    private val directory: File,
) : HistoryPayloadStore {
    override fun payloadExists(fileName: String): Boolean =
        payloadFile(fileName).isFile

    override fun readPayload(fileName: String): ByteArray? =
        runCatching { payloadFile(fileName).takeIf { it.isFile }?.readBytes() }.getOrNull()

    override fun writePayloadAtomically(
        temporaryFileName: String,
        finalFileName: String,
        bytes: ByteArray,
    ) {
        directory.mkdirs()
        val temporaryFile = payloadFile(temporaryFileName)
        val finalFile = payloadFile(finalFileName)
        FileOutputStream(temporaryFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (finalFile.exists()) {
            finalFile.delete()
        }
        check(temporaryFile.renameTo(finalFile)) { "Could not move history payload into place." }
    }

    override fun deletePayload(fileName: String) {
        payloadFile(fileName).delete()
    }

    override fun deleteAllPayloads() {
        directory.mkdirs()
        directory
            .listFiles { file ->
                file.isFile && (file.name.endsWith(".payload") || file.name.endsWith(".tmp"))
            }
            .orEmpty()
            .forEach { file -> file.delete() }
    }

    override fun deleteTemporaryPayloads() {
        directory.mkdirs()
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(".tmp") }
            .orEmpty()
            .forEach { file -> file.delete() }
    }

    override fun deleteOrphanPayloads(referencedPayloadFileNames: Set<String>) {
        directory.mkdirs()
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(".payload") }
            .orEmpty()
            .filterNot { file -> file.name in referencedPayloadFileNames }
            .forEach { file -> file.delete() }
    }

    private fun payloadFile(fileName: String): File = File(directory, fileName)
}
