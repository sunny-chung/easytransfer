/*
 * Gzip deflate support for DCF2 compatibility with decimen-optical-transfer/shared/protocol.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

private const val MAX_DEFLATE_DISTANCE = 32_768
private const val MIN_DEFLATE_MATCH = 3
private const val MAX_DEFLATE_MATCH = 258
private const val MAX_MATCH_CHAIN = 96

internal fun gzipWithFixedHuffmanDeflate(bytes: ByteArray): ByteArray? = runCatching {
    val deflate = FixedHuffmanDeflater(bytes).deflate()
    ByteArray(10 + deflate.size + 8).also { output ->
        output[0] = 0x1f.toByte()
        output[1] = 0x8b.toByte()
        output[2] = 8.toByte()
        output[3] = 0.toByte()
        output[8] = 4.toByte()
        output[9] = 255.toByte()
        deflate.copyInto(output, destinationOffset = 10)
        output.writeUInt32LittleEndian(10 + deflate.size, crc32ForGzip(bytes))
        output.writeUInt32LittleEndian(10 + deflate.size + 4, bytes.size.toUInt())
    }
}.getOrNull()

private class FixedHuffmanDeflater(private val input: ByteArray) {
    private val writer = DeflateBitWriter()
    private val hashHeads = IntArray(65_536) { -1 }
    private val previous = IntArray(input.size) { -1 }

    fun deflate(): ByteArray {
        writer.writeBits(1, 1) // BFINAL
        writer.writeBits(1, 2) // BTYPE=01, fixed Huffman
        var offset = 0
        while (offset < input.size) {
            val match = findMatch(offset)
            if (match != null) {
                writeLengthDistance(match.length, match.distance)
                repeat(match.length) { index -> insert(offset + index) }
                offset += match.length
            } else {
                writeFixedSymbol(input[offset].toUByte().toInt())
                insert(offset)
                offset++
            }
        }
        writeFixedSymbol(256)
        return writer.toByteArray()
    }

    private fun findMatch(offset: Int): DeflateMatch? {
        val hash = hashAt(offset) ?: return null
        var candidate = hashHeads[hash]
        var chain = 0
        var bestLength = 0
        var bestDistance = 0
        while (candidate >= 0 && offset - candidate <= MAX_DEFLATE_DISTANCE && chain < MAX_MATCH_CHAIN) {
            val length = matchLength(offset, candidate)
            if (length > bestLength) {
                bestLength = length
                bestDistance = offset - candidate
                if (length == MAX_DEFLATE_MATCH) break
            }
            candidate = previous[candidate]
            chain++
        }
        return if (bestLength >= MIN_DEFLATE_MATCH) DeflateMatch(bestLength, bestDistance) else null
    }

    private fun insert(offset: Int) {
        val hash = hashAt(offset) ?: return
        previous[offset] = hashHeads[hash]
        hashHeads[hash] = offset
    }

    private fun hashAt(offset: Int): Int? {
        if (offset + 2 >= input.size) return null
        val a = input[offset].toUByte().toInt()
        val b = input[offset + 1].toUByte().toInt()
        val c = input[offset + 2].toUByte().toInt()
        return ((a * 2_573) xor (b * 769) xor c) and 0xffff
    }

    private fun matchLength(offset: Int, candidate: Int): Int {
        val maxLength = minOf(MAX_DEFLATE_MATCH, input.size - offset)
        var length = 0
        while (length < maxLength && input[offset + length] == input[candidate + length]) {
            length++
        }
        return length
    }

    private fun writeLengthDistance(length: Int, distance: Int) {
        val lengthIndex = LENGTH_BASES.indexOfLast { it <= length }
        writeFixedSymbol(257 + lengthIndex)
        writer.writeBits(length - LENGTH_BASES[lengthIndex], LENGTH_EXTRA_BITS[lengthIndex])

        val distanceIndex = DISTANCE_BASES.indexOfLast { it <= distance }
        writer.writeBits(distanceIndex.reverseBits(5), 5)
        writer.writeBits(distance - DISTANCE_BASES[distanceIndex], DISTANCE_EXTRA_BITS[distanceIndex])
    }

    private fun writeFixedSymbol(symbol: Int) {
        val (code, bitCount) = when (symbol) {
            in 0..143 -> 0x30 + symbol to 8
            in 144..255 -> 0x190 + symbol - 144 to 9
            in 256..279 -> symbol - 256 to 7
            in 280..287 -> 0xc0 + symbol - 280 to 8
            else -> error("Invalid fixed Huffman symbol")
        }
        writer.writeBits(code.reverseBits(bitCount), bitCount)
    }
}

private data class DeflateMatch(
    val length: Int,
    val distance: Int,
)

private class DeflateBitWriter {
    private val bytes = mutableListOf<Byte>()
    private var bitBuffer = 0
    private var bitCount = 0

    fun writeBits(value: Int, count: Int) {
        if (count == 0) return
        bitBuffer = bitBuffer or ((value and ((1 shl count) - 1)) shl bitCount)
        bitCount += count
        while (bitCount >= 8) {
            bytes += bitBuffer.toByte()
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    fun toByteArray(): ByteArray {
        if (bitCount > 0) {
            bytes += bitBuffer.toByte()
            bitBuffer = 0
            bitCount = 0
        }
        return bytes.toByteArray()
    }
}

private fun ByteArray.writeUInt32LittleEndian(offset: Int, value: UInt) {
    repeat(4) { index -> this[offset + index] = (value shr (index * 8)).toByte() }
}

private fun Int.reverseBits(count: Int): Int {
    var input = this
    var output = 0
    repeat(count) {
        output = (output shl 1) or (input and 1)
        input = input ushr 1
    }
    return output
}

private fun crc32ForGzip(bytes: ByteArray): UInt {
    var crc = 0xffffffffu
    bytes.forEach { byte ->
        var value = (crc xor byte.toUByte().toUInt()) and 0xffu
        repeat(8) {
            value = if ((value and 1u) != 0u) (value shr 1) xor 0xedb88320u else value shr 1
        }
        crc = (crc shr 8) xor value
    }
    return crc xor 0xffffffffu
}
