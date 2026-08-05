/*
 * Ported from decimen-optical-transfer/shared/protocol.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

// Frame protocol: every QR frame is fully self-describing, so there is NO
// handshake — the receiver locks onto a stream mid-flight, and a new session
// id on any frame simply starts a fresh transfer.
//
// Layout (little-endian), 20 bytes, followed by `blockLen` payload bytes:
//   0  u8   magic 0xD1
//   1  u8   magic 0x0C
//   2  u16  sessionId   random per sender start
//   4  u32  seq         drives the fountain PRNG (see fountain.ts)
//   8  u16  k           source block count
//  10  u16  blockLen    payload bytes per frame
//  12  u32  totalLen    file length in bytes
//  16  u32  payloadFnv  FNV-1a of the whole file — verified on completion

internal const val OPTICAL_HEADER_LENGTH = 20
private const val MAGIC_0 = 0xD1
private const val MAGIC_1 = 0x0C

internal data class OpticalFrameHeader(
    val sessionId: Int,
    val sequence: UInt,
    val blockCount: Int,
    val blockLength: Int,
    val totalLength: Int,
    val payloadFnv: UInt,
)

internal data class OpticalFrame(
    val header: OpticalFrameHeader,
    val block: ByteArray,
)

internal fun packOpticalFrame(
    header: OpticalFrameHeader,
    block: ByteArray,
): ByteArray {
    require(header.sessionId in 0..0xFFFF)
    require(header.blockCount in 1..0xFFFF)
    require(header.blockLength in 1..0xFFFF)
    require(block.size == header.blockLength)
    require(header.totalLength > 0)

    return ByteArray(OPTICAL_HEADER_LENGTH + block.size).also { output ->
        output[0] = MAGIC_0.toByte()
        output[1] = MAGIC_1.toByte()
        output.writeUInt16LittleEndian(2, header.sessionId)
        output.writeUInt32LittleEndian(4, header.sequence)
        output.writeUInt16LittleEndian(8, header.blockCount)
        output.writeUInt16LittleEndian(10, header.blockLength)
        output.writeUInt32LittleEndian(12, header.totalLength.toUInt())
        output.writeUInt32LittleEndian(16, header.payloadFnv)
        block.copyInto(output, destinationOffset = OPTICAL_HEADER_LENGTH)
    }
}

internal fun parseOpticalFrame(bytes: ByteArray): OpticalFrame? {
    if (bytes.size <= OPTICAL_HEADER_LENGTH) return null
    if (bytes[0].toUByte().toInt() != MAGIC_0 || bytes[1].toUByte().toInt() != MAGIC_1) return null

    val header = OpticalFrameHeader(
        sessionId = bytes.readUInt16LittleEndian(2),
        sequence = bytes.readUInt32LittleEndian(4),
        blockCount = bytes.readUInt16LittleEndian(8),
        blockLength = bytes.readUInt16LittleEndian(10),
        totalLength = bytes.readUInt32LittleEndian(12).toLong().takeIf { it <= Int.MAX_VALUE }?.toInt()
            ?: return null,
        payloadFnv = bytes.readUInt32LittleEndian(16),
    )
    if (header.blockCount == 0 || header.blockLength == 0 || header.totalLength == 0) return null
    if (bytes.size != OPTICAL_HEADER_LENGTH + header.blockLength) return null

    return OpticalFrame(
        header = header,
        block = bytes.copyOfRange(OPTICAL_HEADER_LENGTH, bytes.size),
    )
}

internal fun fnv1a(bytes: ByteArray): UInt {
    var hash = 0x811C9DC5u
    bytes.forEach { byte ->
        hash = hash xor byte.toUByte().toUInt()
        hash *= 0x01000193u
    }
    return hash
}

/** splitmix32 — deterministic across JS engines (integer ops only). */
internal class SplitMix32(seed: UInt) {
    private var state = seed

    fun nextUInt(): UInt {
        state += 0x9E3779B9u
        var value = state xor (state shr 16)
        value *= 0x21F0AAADu
        value = value xor (value shr 15)
        value *= 0x735A2D97u
        value = value xor (value shr 15)
        return value
    }
}

private fun ByteArray.writeUInt16LittleEndian(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
}

private fun ByteArray.writeUInt32LittleEndian(offset: Int, value: UInt) {
    repeat(4) { byteIndex ->
        this[offset + byteIndex] = (value shr (byteIndex * 8)).toByte()
    }
}

private fun ByteArray.readUInt16LittleEndian(offset: Int): Int =
    this[offset].toUByte().toInt() or (this[offset + 1].toUByte().toInt() shl 8)

private fun ByteArray.readUInt32LittleEndian(offset: Int): UInt {
    var result = 0u
    repeat(4) { byteIndex ->
        result = result or (this[offset + byteIndex].toUByte().toUInt() shl (byteIndex * 8))
    }
    return result
}
