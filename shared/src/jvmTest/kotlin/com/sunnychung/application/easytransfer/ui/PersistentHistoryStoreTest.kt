package com.sunnychung.application.easytransfer.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sunnychung.application.easytransfer.db.HistoryDatabase
import com.sunnychung.application.easytransfer.optical.TransferKind
import com.sunnychung.application.easytransfer.optical.TransferPayload
import com.sunnychung.application.easytransfer.ui.model.TransferStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentHistoryStoreTest {
    @Test
    fun clearDeletesRecordsAndPayloads() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HistoryDatabase.Schema.create(driver)
        val payloadStore = InMemoryHistoryPayloadStore()
        val store = PersistentHistoryStore(driver, payloadStore)

        val textItem = store.add(
            payload = TransferPayload(
                kind = TransferKind.Text,
                bytes = "hello".encodeToByteArray(),
                name = null,
                mediaType = "text/plain",
            ),
            status = TransferStatus.Sent,
        )
        val fileItem = store.add(
            payload = TransferPayload(
                kind = TransferKind.File,
                bytes = byteArrayOf(1, 2, 3),
                name = "sample.bin",
                mediaType = "application/octet-stream",
            ),
            status = TransferStatus.Received,
        )

        assertEquals(2, store.load().size)
        assertTrue(payloadStore.payloads.isNotEmpty())

        store.clear()

        assertEquals(emptyList(), store.load())
        assertNull(store.loadPayload(textItem.id))
        assertNull(store.loadPayload(fileItem.id))
        assertTrue(payloadStore.payloads.isEmpty())
    }
}

private class InMemoryHistoryPayloadStore : HistoryPayloadStore {
    val payloads = mutableMapOf<String, ByteArray>()

    override fun payloadExists(fileName: String): Boolean =
        payloads.containsKey(fileName)

    override fun readPayload(fileName: String): ByteArray? =
        payloads[fileName]

    override fun writePayloadAtomically(
        temporaryFileName: String,
        finalFileName: String,
        bytes: ByteArray,
    ) {
        payloads[temporaryFileName] = bytes
        payloads[finalFileName] = payloads.remove(temporaryFileName) ?: bytes
    }

    override fun deletePayload(fileName: String) {
        payloads.remove(fileName)
    }

    override fun deleteAllPayloads() {
        payloads.clear()
    }

    override fun deleteTemporaryPayloads() {
        payloads.keys
            .filter { fileName -> fileName.endsWith(".tmp") }
            .forEach { fileName -> payloads.remove(fileName) }
    }

    override fun deleteOrphanPayloads(referencedPayloadFileNames: Set<String>) {
        payloads.keys
            .filter { fileName -> fileName.endsWith(".payload") }
            .filterNot { fileName -> fileName in referencedPayloadFileNames }
            .forEach { fileName -> payloads.remove(fileName) }
    }
}
