package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sunnychung.application.easytransfer.db.HistoryDatabase
import java.io.File
import java.io.FileOutputStream
import java.nio.file.StandardCopyOption
import java.util.Calendar
import net.harawata.appdirs.AppDirsFactory

@Composable
internal actual fun rememberPersistentHistoryStore(): PersistentHistoryStore = remember {
    val userDataDirectory = AppDirsFactory.getInstance().getUserDataDir(
        "EasyTransfer",
        null,
        "Sunny Chung",
    )
    val historyDirectory = File(userDataDirectory, "history")
    check(historyDirectory.isDirectory || historyDirectory.mkdirs()) {
        "Could not create history directory: ${historyDirectory.absolutePath}"
    }
    val databaseFile = File(historyDirectory, "history.db")
    val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
    HistoryDatabase.Schema.create(driver)
    PersistentHistoryStore(
        driver = driver,
        payloadStore = JavaHistoryPayloadStore(File(historyDirectory, "payloads")),
    )
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
        java.nio.file.Files.move(
            temporaryFile.toPath(),
            finalFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
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
