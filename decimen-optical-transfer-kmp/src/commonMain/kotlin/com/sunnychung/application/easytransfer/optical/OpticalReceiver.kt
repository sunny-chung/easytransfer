/*
 * Receiver behavior ported from decimen-optical-transfer/receive/main.ts and receive/worker.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

// Progress estimate retained from the original receiver. It does not change
// the fountain decoder or impose a fixed frame count.
// Receiver: camera → WASM QR decode in workers → fountain decoder → file.
//
// Field lessons baked in:
// - iOS treats `frameRate: {ideal: 60}` as a suggestion and delivers 30.
//   Demand `exact` first (it works at 1280-wide), fall back to `ideal`.
// - requestVideoFrameCallback chains survive a stopped stream and resume on
//   the next one — a generation counter prevents zombie capture loops.
// - Progress must track frames COLLECTED: LT peeling back-loads its solve
//   cascade, so blocks-solved looks stalled and then teleports to done.
//
// Original web worker note retained for the same drop-tolerant design rule:
// QR decode worker: zxing-cpp compiled to WASM. (Safari has never shipped
// BarcodeDetector — WebKit bug 281848 — so WASM is the only portable way.)
// One frame in flight per worker; the main thread drops frames when all
// workers are busy. Frames are disposable — the fountain doesn't care.
class OpticalReceiver {
    private var session: ReceiverSession? = null
    private var completedSessionId: Int? = null

    fun reset() {
        session = null
        completedSessionId = null
    }

    fun accept(bytes: ByteArray): OpticalReceiveResult {
        val frame = parseOpticalFrame(bytes) ?: return OpticalReceiveResult.Ignored
        val header = frame.header
        if (header.sessionId == completedSessionId) return OpticalReceiveResult.Ignored
        val current = session
        if (current == null || !current.matches(header)) {
            session = ReceiverSession(header)
        }
        val activeSession = requireNotNull(session)
        activeSession.decoder.addFrame(header.sequence, frame.block)
        val progress = activeSession.progress()
        val assembled = activeSession.decoder.assemble()
            ?: return OpticalReceiveResult.Receiving(progress)
        if (fnv1a(assembled) != activeSession.payloadFnv) {
            session = null
            return OpticalReceiveResult.Corrupt("Transfer checksum did not match")
        }
        val payload = TransferPayloadCodec.decode(assembled) ?: run {
            session = null
            return OpticalReceiveResult.Corrupt("Transfer metadata could not be read")
        }
        completedSessionId = header.sessionId
        session = null
        return OpticalReceiveResult.Completed(
            payload = payload,
            progress = progress.copy(
                typicalUniqueFrameTarget = progress.uniqueFrames,
                isComplete = true,
            ),
        )
    }
}

private class ReceiverSession(header: OpticalFrameHeader) {
    private val identity = header.streamIdentity()
    val decoder = FountainDecoder(
        blockCount = header.blockCount,
        blockLength = header.blockLength,
        sessionId = header.sessionId,
        totalLength = header.totalLength,
    )
    val payloadFnv = header.payloadFnv

    fun matches(header: OpticalFrameHeader): Boolean = identity == header.streamIdentity()

    fun progress(): OpticalReceiveProgress = OpticalReceiveProgress(
        sessionId = decoder.sessionId,
        uniqueFrames = decoder.newFrameCount,
        typicalUniqueFrameTarget = estimateTransferProgress(
            sourceBlocks = decoder.blockCount,
            uniqueFrames = decoder.newFrameCount,
            elapsedMillis = 0L,
            solvedBlocks = decoder.solvedCount,
        ).expectedFrames,
        duplicateFrames = decoder.duplicateFrameCount,
        recoveredBlocks = decoder.solvedCount,
        sourceBlocks = decoder.blockCount,
        blockLength = decoder.blockLength,
        frameBytes = decoder.blockLength + OPTICAL_HEADER_LENGTH,
        totalLength = decoder.totalLength,
    )
}

private fun OpticalFrameHeader.streamIdentity(): String = listOf(
    sessionId,
    blockCount,
    blockLength,
    totalLength,
    payloadFnv,
).joinToString(separator = ":")
