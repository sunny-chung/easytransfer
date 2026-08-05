/*
 * Gzip inflate support for DCF2 compatibility with decimen-optical-transfer/shared/protocol.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

private const val GZIP_ID_0 = 0x1f
private const val GZIP_ID_1 = 0x8b
private const val GZIP_DEFLATE_METHOD = 8
private const val GZIP_FLAG_TEXT = 0x01
private const val GZIP_FLAG_HEADER_CRC = 0x02
private const val GZIP_FLAG_EXTRA = 0x04
private const val GZIP_FLAG_NAME = 0x08
private const val GZIP_FLAG_COMMENT = 0x10
private const val GZIP_SUPPORTED_FLAGS =
    GZIP_FLAG_TEXT or GZIP_FLAG_HEADER_CRC or GZIP_FLAG_EXTRA or GZIP_FLAG_NAME or GZIP_FLAG_COMMENT

internal val LENGTH_BASES = intArrayOf(
    3, 4, 5, 6, 7, 8, 9, 10,
    11, 13, 15, 17, 19, 23, 27, 31,
    35, 43, 51, 59, 67, 83, 99, 115,
    131, 163, 195, 227, 258,
)

internal val LENGTH_EXTRA_BITS = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0,
    1, 1, 1, 1, 2, 2, 2, 2,
    3, 3, 3, 3, 4, 4, 4, 4,
    5, 5, 5, 5, 0,
)

internal val DISTANCE_BASES = intArrayOf(
    1, 2, 3, 4, 5, 7, 9, 13,
    17, 25, 33, 49, 65, 97, 129, 193,
    257, 385, 513, 769, 1025, 1537, 2049, 3073,
    4097, 6145, 8193, 12289, 16385, 24577,
)

internal val DISTANCE_EXTRA_BITS = intArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2,
    3, 3, 4, 4, 5, 5, 6, 6,
    7, 7, 8, 8, 9, 9, 10, 10,
    11, 11, 12, 12, 13, 13,
)

private val CODE_LENGTH_ORDER = intArrayOf(
    16, 17, 18, 0, 8, 7, 9, 6,
    10, 5, 11, 4, 12, 3, 13, 2,
    14, 1, 15,
)

internal fun gzipInflate(bytes: ByteArray, maxBytes: Int): ByteArray? = runCatching {
    if (maxBytes <= 0 || bytes.size < 18) return@runCatching null
    if (
        bytes[0].toUByte().toInt() != GZIP_ID_0 ||
        bytes[1].toUByte().toInt() != GZIP_ID_1 ||
        bytes[2].toUByte().toInt() != GZIP_DEFLATE_METHOD
    ) {
        return@runCatching null
    }
    val flags = bytes[3].toUByte().toInt()
    if ((flags and GZIP_SUPPORTED_FLAGS.inv()) != 0) return@runCatching null
    var offset = 10
    if ((flags and GZIP_FLAG_EXTRA) != 0) {
        if (offset + 2 > bytes.size) return@runCatching null
        val extraLength = bytes.readUInt16LittleEndian(offset)
        offset += 2 + extraLength
        if (offset > bytes.size) return@runCatching null
    }
    if ((flags and GZIP_FLAG_NAME) != 0) {
        offset = bytes.skipNullTerminated(offset) ?: return@runCatching null
    }
    if ((flags and GZIP_FLAG_COMMENT) != 0) {
        offset = bytes.skipNullTerminated(offset) ?: return@runCatching null
    }
    if ((flags and GZIP_FLAG_HEADER_CRC) != 0) {
        offset += 2
        if (offset > bytes.size) return@runCatching null
    }
    if (offset + 8 > bytes.size) return@runCatching null

    val trailerOffset = bytes.size - 8
    val inflated = DeflateReader(bytes, offset, trailerOffset, maxBytes).inflate()
    val expectedCrc = bytes.readUInt32LittleEndian(trailerOffset)
    val expectedSize = bytes.readUInt32LittleEndian(trailerOffset + 4)
    if (crc32(inflated) != expectedCrc) return@runCatching null
    if (inflated.size.toUInt() != expectedSize) return@runCatching null
    inflated
}.getOrNull()

private class DeflateReader(
    input: ByteArray,
    startOffset: Int,
    endOffset: Int,
    private val maxBytes: Int,
) {
    private val bits = BitReader(input, startOffset, endOffset)
    private val output = GrowingByteBuffer(maxBytes)

    fun inflate(): ByteArray {
        var isFinalBlock = false
        while (!isFinalBlock) {
            isFinalBlock = bits.readBits(1) == 1
            when (bits.readBits(2)) {
                0 -> inflateStoredBlock()
                1 -> inflateCompressedBlock(FIXED_LITERAL_LENGTH_TREE, FIXED_DISTANCE_TREE)
                2 -> {
                    val (literalLengthTree, distanceTree) = readDynamicTrees()
                    inflateCompressedBlock(literalLengthTree, distanceTree)
                }
                else -> error("Unsupported deflate block type")
            }
        }
        return output.toByteArray()
    }

    private fun inflateStoredBlock() {
        bits.alignToByte()
        val length = bits.readBits(16)
        val invertedLength = bits.readBits(16)
        if ((length xor 0xffff) != invertedLength) error("Stored block length mismatch")
        repeat(length) { output.append(bits.readBits(8).toByte()) }
    }

    private fun inflateCompressedBlock(
        literalLengthTree: HuffmanTree,
        distanceTree: HuffmanTree,
    ) {
        while (true) {
            when (val symbol = literalLengthTree.decode(bits)) {
                in 0..255 -> output.append(symbol.toByte())
                256 -> return
                in 257..285 -> {
                    val lengthIndex = symbol - 257
                    val length = LENGTH_BASES[lengthIndex] + bits.readBits(LENGTH_EXTRA_BITS[lengthIndex])
                    val distanceSymbol = distanceTree.decode(bits)
                    if (distanceSymbol !in DISTANCE_BASES.indices) error("Invalid distance symbol")
                    val distance = DISTANCE_BASES[distanceSymbol] + bits.readBits(DISTANCE_EXTRA_BITS[distanceSymbol])
                    output.copyFromDistance(distance, length)
                }
                else -> error("Invalid literal/length symbol")
            }
        }
    }

    private fun readDynamicTrees(): Pair<HuffmanTree, HuffmanTree> {
        val literalLengthCount = bits.readBits(5) + 257
        val distanceCount = bits.readBits(5) + 1
        val codeLengthCount = bits.readBits(4) + 4
        val codeLengthLengths = IntArray(19)
        repeat(codeLengthCount) { index ->
            codeLengthLengths[CODE_LENGTH_ORDER[index]] = bits.readBits(3)
        }
        val codeLengthTree = HuffmanTree(codeLengthLengths)
        val lengths = IntArray(literalLengthCount + distanceCount)
        var index = 0
        while (index < lengths.size) {
            when (val symbol = codeLengthTree.decode(bits)) {
                in 0..15 -> lengths[index++] = symbol
                16 -> {
                    if (index == 0) error("Repeat length has no previous value")
                    val repeatCount = bits.readBits(2) + 3
                    repeat(repeatCount) {
                        if (index >= lengths.size) error("Too many code lengths")
                        lengths[index] = lengths[index - 1]
                        index++
                    }
                }
                17 -> {
                    val repeatCount = bits.readBits(3) + 3
                    repeat(repeatCount) {
                        if (index >= lengths.size) error("Too many code lengths")
                        lengths[index++] = 0
                    }
                }
                18 -> {
                    val repeatCount = bits.readBits(7) + 11
                    repeat(repeatCount) {
                        if (index >= lengths.size) error("Too many code lengths")
                        lengths[index++] = 0
                    }
                }
                else -> error("Invalid code-length symbol")
            }
        }
        return HuffmanTree(lengths.copyOfRange(0, literalLengthCount)) to
            HuffmanTree(lengths.copyOfRange(literalLengthCount, lengths.size))
    }
}

private class BitReader(
    private val bytes: ByteArray,
    private var offset: Int,
    private val endOffset: Int,
) {
    private var bitBuffer = 0
    private var bitCount = 0

    fun readBits(count: Int): Int {
        while (bitCount < count) {
            if (offset >= endOffset) error("Unexpected end of deflate stream")
            bitBuffer = bitBuffer or (bytes[offset++].toUByte().toInt() shl bitCount)
            bitCount += 8
        }
        val value = bitBuffer and ((1 shl count) - 1)
        bitBuffer = bitBuffer ushr count
        bitCount -= count
        return value
    }

    fun alignToByte() {
        bitBuffer = 0
        bitCount = 0
    }
}

private class HuffmanTree(lengths: IntArray) {
    private val maxBits = lengths.maxOrNull() ?: 0
    private val symbolsByKey = mutableMapOf<Int, Int>()

    init {
        require(maxBits > 0) { "Empty Huffman tree" }
        val counts = IntArray(maxBits + 1)
        lengths.forEach { length ->
            if (length > 0) counts[length]++
        }
        val nextCodes = IntArray(maxBits + 1)
        var code = 0
        for (bits in 1..maxBits) {
            code = (code + counts[bits - 1]) shl 1
            nextCodes[bits] = code
        }
        lengths.forEachIndexed { symbol, length ->
            if (length > 0) {
                val canonicalCode = nextCodes[length]++
                symbolsByKey[(length shl 16) or canonicalCode.reverseBits(length)] = symbol
            }
        }
    }

    fun decode(reader: BitReader): Int {
        var code = 0
        for (length in 1..maxBits) {
            code = code or (reader.readBits(1) shl (length - 1))
            symbolsByKey[(length shl 16) or code]?.let { return it }
        }
        error("Invalid Huffman code")
    }
}

private class GrowingByteBuffer(private val maxSize: Int) {
    private var bytes = ByteArray(8192)
    var size = 0
        private set

    fun append(byte: Byte) {
        ensureCapacity(size + 1)
        bytes[size++] = byte
    }

    fun copyFromDistance(distance: Int, length: Int) {
        if (distance <= 0 || distance > size) error("Invalid deflate distance")
        ensureCapacity(size + length)
        repeat(length) {
            bytes[size] = bytes[size - distance]
            size++
        }
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensureCapacity(requiredSize: Int) {
        if (requiredSize > maxSize) error("Inflated output is too large")
        if (requiredSize <= bytes.size) return
        var nextSize = bytes.size
        while (nextSize < requiredSize) nextSize *= 2
        bytes = bytes.copyOf(nextSize.coerceAtMost(maxSize))
    }
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

private fun ByteArray.skipNullTerminated(startOffset: Int): Int? {
    var offset = startOffset
    while (offset < size) {
        if (this[offset++] == 0.toByte()) return offset
    }
    return null
}

private fun ByteArray.readUInt16LittleEndian(offset: Int): Int =
    this[offset].toUByte().toInt() or (this[offset + 1].toUByte().toInt() shl 8)

private fun ByteArray.readUInt32LittleEndian(offset: Int): UInt {
    var value = 0u
    repeat(4) { index ->
        value = value or (this[offset + index].toUByte().toUInt() shl (index * 8))
    }
    return value
}

private fun crc32(bytes: ByteArray): UInt {
    var crc = 0xffffffffu
    bytes.forEach { byte ->
        var value = (crc xor byte.toUByte().toUInt()) and 0xffu
        repeat(8) {
            value = if ((value and 1u) != 0u) {
                (value shr 1) xor 0xedb88320u
            } else {
                value shr 1
            }
        }
        crc = (crc shr 8) xor value
    }
    return crc xor 0xffffffffu
}

private val FIXED_LITERAL_LENGTH_TREE = HuffmanTree(
    IntArray(288) { symbol ->
        when (symbol) {
            in 0..143 -> 8
            in 144..255 -> 9
            in 256..279 -> 7
            else -> 8
        }
    },
)

private val FIXED_DISTANCE_TREE = HuffmanTree(IntArray(32) { 5 })
