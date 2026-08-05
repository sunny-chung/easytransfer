/*
 * Sender behavior ported from decimen-optical-transfer/send/main.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

import kotlin.random.Random

// Sender: turn a file into an endless fountain-coded QR stream.
//
// Tuning notes from the experiments this PoC is distilled from:
// - Frame payload sets the QR version; denser wins on goodput as long as the
//   receiver can still decode it. 1465 bytes ≈ V27 is a safe middle ground
//   for arbitrary monitors; 2953 (V40) is the ceiling and works phone-to-
//   phone at close range.
// - The mask pattern is pinned (any declared mask is valid to a decoder);
//   this skips the spec's 8-way mask evaluation and speeds generation ~4×.
// - Displays need each frame shown for ≥2 refresh cycles or captures catch
//   the transition; 24 fps on a 60 Hz screen is comfortable.
// - Error correction stays at L by default: the fountain layer already
//   handles erasures, and a frame is either decoded whole or discarded.
//
class OpticalSender(
    payload: TransferPayload,
    val sessionId: Int = Random.nextInt(from = 1, until = 0x10000),
    val blockLength: Int = DEFAULT_OPTICAL_FRAME_BYTES - OPTICAL_HEADER_LENGTH,
    compressionEnabled: Boolean = true,
) {
    private val packedPayload = TransferPayloadCodec.pack(
        payload = payload,
        compressionEnabled = compressionEnabled,
    )
    private val encodedPayload = packedPayload.container
    private val encoder = FountainEncoder(encodedPayload, blockLength, sessionId)
    private val payloadFnv = fnv1a(encodedPayload)
    private var sequence = 0u

    val blockCount: Int
        get() = encoder.blockCount
    val totalLength: Int
        get() = encodedPayload.size
    val compression: TransferPayloadCompression
        get() = packedPayload.compression
    val originalPayloadLength: Int
        get() = packedPayload.originalSize
    val transmittedPayloadLength: Int
        get() = packedPayload.transmittedSize

    fun nextFrame(): ByteArray {
        val currentSequence = sequence++
        return packOpticalFrame(
            header = OpticalFrameHeader(
                sessionId = sessionId,
                sequence = currentSequence,
                blockCount = encoder.blockCount,
                blockLength = blockLength,
                totalLength = encodedPayload.size,
                payloadFnv = payloadFnv,
            ),
            block = encoder.encode(currentSequence),
        )
    }
}
