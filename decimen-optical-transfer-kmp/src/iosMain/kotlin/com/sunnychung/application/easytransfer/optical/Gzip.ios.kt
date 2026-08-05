/*
 * Gzip helpers for DCF2 compatibility with decimen-optical-transfer/shared/protocol.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

internal actual fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray? = gzipInflate(bytes, maxBytes)

internal actual fun gzip(bytes: ByteArray): ByteArray? = gzipWithFixedHuffmanDeflate(bytes)
