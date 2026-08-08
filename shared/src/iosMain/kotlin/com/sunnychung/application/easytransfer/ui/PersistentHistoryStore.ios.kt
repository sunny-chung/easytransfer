package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sunnychung.application.easytransfer.db.HistoryDatabase
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.localtime
import platform.posix.time
import platform.posix.time_tVar

@Composable
internal actual fun rememberPersistentHistoryStore(): PersistentHistoryStore = remember {
    val historyDirectory = applicationSupportHistoryPath()
    PersistentHistoryStore(
        driver = NativeSqliteDriver(
            schema = HistoryDatabase.Schema,
            name = "history.db",
        ),
        payloadStore = IosHistoryPayloadStore("$historyDirectory/payloads"),
    )
}

internal actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970() * 1_000.0).toLong()

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentLocalHour(): Int = memScoped {
    val now = alloc<time_tVar>()
    time(now.ptr)
    localtime(now.ptr)?.pointed?.tm_hour ?: 12
}

private fun applicationSupportHistoryPath(): String {
    val applicationSupportPath = NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).filterIsInstance<String>().firstOrNull()
        ?: error("Application Support directory is not available.")
    return applicationSupportPath.trimEnd('/') + "/EasyTransfer/history"
}

@OptIn(ExperimentalForeignApi::class)
private class IosHistoryPayloadStore(
    private val directoryPath: String,
) : HistoryPayloadStore {
    override fun payloadExists(fileName: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(pathFor(fileName))

    override fun readPayload(fileName: String): ByteArray? =
        runCatching {
            NSData.dataWithContentsOfFile(pathFor(fileName))
                ?.toByteArray()
        }.getOrNull()

    override fun writePayloadAtomically(
        temporaryFileName: String,
        finalFileName: String,
        bytes: ByteArray,
    ) {
        ensureDirectory()
        val temporaryPath = pathFor(temporaryFileName)
        val finalPath = pathFor(finalFileName)
        if (!bytes.toNSData().writeToFile(temporaryPath, atomically = true)) {
            error("Could not write history payload.")
        }
        NSFileManager.defaultManager.removeItemAtPath(finalPath, error = null)
        if (!NSFileManager.defaultManager.moveItemAtPath(temporaryPath, finalPath, error = null)) {
            error("Could not move history payload into place.")
        }
    }

    override fun deletePayload(fileName: String) {
        NSFileManager.defaultManager.removeItemAtPath(pathFor(fileName), error = null)
    }

    override fun deleteAllPayloads() {
        (payloadFileNamesWithSuffix(".payload") + payloadFileNamesWithSuffix(".tmp"))
            .distinct()
            .forEach(::deletePayload)
    }

    override fun deleteTemporaryPayloads() {
        payloadFileNamesWithSuffix(".tmp").forEach(::deletePayload)
    }

    override fun deleteOrphanPayloads(referencedPayloadFileNames: Set<String>) {
        payloadFileNamesWithSuffix(".payload")
            .filterNot { fileName -> fileName in referencedPayloadFileNames }
            .forEach(::deletePayload)
    }

    private fun payloadFileNamesWithSuffix(suffix: String): List<String> {
        ensureDirectory()
        return NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(directoryPath, error = null)
            ?.filterIsInstance<String>()
            .orEmpty()
            .filter { fileName -> fileName.endsWith(suffix) }
    }

    private fun ensureDirectory() {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    private fun pathFor(fileName: String): String = "$directoryPath/$fileName"
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(
        bytes = pinned.addressOf(0),
        length = size.toULong(),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val pointer = bytes ?: return ByteArray(0)
    return pointer.reinterpret<ByteVar>().readBytes(length.toInt())
}
