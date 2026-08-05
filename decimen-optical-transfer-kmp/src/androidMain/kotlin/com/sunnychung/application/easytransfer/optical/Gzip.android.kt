/*
 * Gzip helpers for DCF2 compatibility with decimen-optical-transfer/shared/protocol.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal actual fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray? = runCatching {
    GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return@runCatching null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}.getOrNull()

internal actual fun gzip(bytes: ByteArray): ByteArray? = runCatching {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { gzip -> gzip.write(bytes) }
    output.toByteArray()
}.getOrNull()
