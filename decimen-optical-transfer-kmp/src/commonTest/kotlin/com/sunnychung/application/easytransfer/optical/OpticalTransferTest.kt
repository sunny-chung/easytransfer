package com.sunnychung.application.easytransfer.optical

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpticalTransferTest {
    @Test
    fun frameProtocolRoundTrips() {
        val header = OpticalFrameHeader(
            sessionId = 0xCAFE,
            sequence = 0xFEDCBA98u,
            blockCount = 23,
            blockLength = 4,
            totalLength = 91,
            payloadFnv = 0x12345678u,
        )
        val block = byteArrayOf(1, 2, 3, 4)
        val packed = packOpticalFrame(header, block)

        val parsed = requireNotNull(parseOpticalFrame(packed))

        assertContentEquals(
            byteArrayOf(0xD1.toByte(), 0x0C, 0xFE.toByte(), 0xCA.toByte()),
            packed.copyOfRange(0, 4),
        )
        assertEquals(header, parsed.header)
        assertContentEquals(block, parsed.block)
        assertEquals(0x4F9F2CABu, fnv1a("hello".encodeToByteArray()))
    }

    @Test
    fun rejectsMalformedFrames() {
        assertNull(parseOpticalFrame(ByteArray(20)))
        assertNull(parseOpticalFrame(ByteArray(21)))
    }

    @Test
    fun fountainTransferCompletesAfterDroppedAndReorderedFrames() {
        val content = ByteArray(12_345) { index -> (index * 31).toByte() }
        val sender = OpticalSender(
            payload = TransferPayload(
                kind = TransferKind.File,
                bytes = content,
                name = "sample.bin",
                mediaType = "application/octet-stream",
            ),
            sessionId = 0x1234,
            blockLength = 256,
        )
        val receiver = OpticalReceiver()
        val frames = List(sender.blockCount * 3) { sender.nextFrame() }
            .filterIndexed { index, _ -> index % 4 != 0 }
            .chunked(7)
            .flatMap { it.reversed() }

        val completed = frames.firstNotNullOfOrNull { frame ->
            receiver.accept(frame) as? OpticalReceiveResult.Completed
        }

        val result = assertIs<OpticalReceiveResult.Completed>(completed)
        assertEquals(TransferKind.File, result.payload.kind)
        assertEquals("sample.bin", result.payload.name)
        assertContentEquals(content, result.payload.bytes)
    }

    @Test
    fun receiverIgnoresUnrelatedQrCodesAndDuplicates() {
        val receiver = OpticalReceiver()
        assertIs<OpticalReceiveResult.Ignored>(receiver.accept("https://example.com"))

        val sender = OpticalSender(TransferPayload.text("hello"), sessionId = 7, blockLength = 64)
        val frame = sender.nextFrame()
        assertIs<OpticalReceiveResult.Completed>(receiver.accept(frame))
        assertIs<OpticalReceiveResult.Ignored>(receiver.accept(frame))
    }

    @Test
    fun largeTransfersCompleteAcrossDifferentSessions() {
        val blockLength = 720
        val payload = ByteArray(120 * 1_024) { index -> (index * 31).toByte() }
        val blockCount = (payload.size + blockLength - 1) / blockLength

        repeat(24) { sessionId ->
            val encoder = FountainEncoder(payload, blockLength, sessionId)
            val decoder = FountainDecoder(
                blockCount = encoder.blockCount,
                blockLength = blockLength,
                sessionId = sessionId,
                totalLength = payload.size,
            )
            var sequence = 0u
            while (!decoder.isComplete && sequence < (blockCount * 4).toUInt()) {
                decoder.addFrame(sequence, encoder.encode(sequence))
                sequence++
            }

            assertTrue(
                decoder.isComplete,
                "Session $sessionId did not complete after ${decoder.newFrameCount} unique frames",
            )
            assertContentEquals(payload, decoder.assemble())
        }
    }

    @Test
    fun duplicateFramesAreExcludedFromCollectedFrameCount() {
        val payload = ByteArray(4_096) { index -> index.toByte() }
        val encoder = FountainEncoder(payload, blockLength = 128, sessionId = 42)
        val decoder = FountainDecoder(
            blockCount = encoder.blockCount,
            blockLength = encoder.blockLength,
            sessionId = encoder.sessionId,
            totalLength = payload.size,
        )
        val frame = encoder.encode(0u)

        repeat(5) { decoder.addFrame(0u, frame) }

        assertEquals(1, decoder.newFrameCount)
        assertEquals(4, decoder.duplicateFrameCount)
    }
}
