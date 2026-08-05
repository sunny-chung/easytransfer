/*
 * Ported from decimen-optical-transfer/shared/fountain.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// LT (Luby transform) fountain code — the trick that makes a one-way optical
// channel practical.
//
// The sender emits an endless stream of frames; frame `seq` is the XOR of a
// pseudorandom subset of the file's blocks, with both the subset size
// (degree, drawn from a robust-soliton distribution) and the block indices
// derived deterministically from `seq`. The receiver rebuilds the file from
// ANY ~K·1.15 distinct frames, in any order: a dropped frame costs a little
// time, never correctness. No back-channel, no retransmission, and sender
// and receiver frame rates don't need to match at all.
//
// Determinism warning that cost a debugging session: sender and receiver
// must build bit-identical degree distributions, but JavaScript's Math.log
// is implementation-approximated — V8 (sender) and JavaScriptCore (iPhone
// receiver) may differ by an ulp and silently desynchronize the streams.
// dlog() below uses only exactly-specified IEEE-754 ops.

private const val LN_2 = 0.6931471805599453
private const val SOLITON_C = 0.1
private const val SOLITON_DELTA = 0.5
private const val UINT_RANGE = 4294967296.0

/** Deterministic natural log: exact-ops range reduction + atanh series. */
private fun deterministicLog(input: Double): Double {
    var exponent = 0
    var mantissa = input
    while (mantissa >= 1.5) {
        mantissa /= 2.0
        exponent++
    }
    while (mantissa < 0.75) {
        mantissa *= 2.0
        exponent--
    }
    val z = (mantissa - 1.0) / (mantissa + 1.0)
    val zSquared = z * z
    var term = z
    var sum = 0.0
    for (n in 1..21 step 2) {
        sum += term / n
        term *= zSquared
    }
    return exponent * LN_2 + 2.0 * sum
}

/** Robust-soliton degree CDF for k source blocks. */
private fun solitonCdf(blockCount: Int): DoubleArray {
    if (blockCount == 1) return doubleArrayOf(1.0)

    val r = max(
        1.0,
        SOLITON_C * deterministicLog(blockCount / SOLITON_DELTA) * sqrt(blockCount.toDouble()),
    )
    val spike = min(blockCount, ceil(blockCount / r).toInt())
    val cdf = DoubleArray(blockCount)
    var total = 0.0
    for (degree in 1..blockCount) {
        val rho = if (degree == 1) {
            1.0 / blockCount
        } else {
            1.0 / (degree * (degree - 1).toDouble())
        }
        val tau = when {
            degree < spike -> r / (degree.toDouble() * blockCount)
            degree == spike -> r * max(0.0, deterministicLog(r / SOLITON_DELTA)) / blockCount
            else -> 0.0
        }
        total += rho + tau
        cdf[degree - 1] = total
    }
    cdf.indices.forEach { index -> cdf[index] /= total }
    cdf[cdf.lastIndex] = 1.0
    return cdf
}

private fun frameSeed(sessionId: Int, sequence: UInt): UInt {
    var hash = (sessionId + 1).toUInt() * 0x9E3779B1u xor (sequence + 0x85EBCA6Bu)
    hash = (hash xor (hash shr 13)) * 0xC2B2AE35u
    return hash xor (hash shr 16)
}

/** The block indices XORed into frame `seq` — identical on both ends. */
private fun frameIndices(
    blockCount: Int,
    cdf: DoubleArray,
    sessionId: Int,
    sequence: UInt,
): IntArray {
    val random = SplitMix32(frameSeed(sessionId, sequence))
    // inverse-CDF sample the degree
    val sample = random.nextUInt().toDouble() / UINT_RANGE
    var low = 0
    var high = blockCount - 1
    while (low < high) {
        val middle = (low + high) ushr 1
        if (cdf[middle] >= sample) high = middle else low = middle + 1
    }
    val degree = min(blockCount, low + 1)
    if (degree > blockCount shr 3) {
        // large degree: partial Fisher–Yates over an identity array
        val scratch = IntArray(blockCount) { it }
        return IntArray(degree) { index ->
            val swapIndex = index + (random.nextUInt() % (blockCount - index).toUInt()).toInt()
            val temporary = scratch[index]
            scratch[index] = scratch[swapIndex]
            scratch[swapIndex] = temporary
            scratch[index]
        }
    }

    val selected = LinkedHashSet<Int>(degree)
    while (selected.size < degree) {
        selected += (random.nextUInt() % blockCount.toUInt()).toInt()
    }
    return selected.toIntArray()
}

private fun xorInto(destination: ByteArray, source: ByteArray) {
    destination.indices.forEach { index ->
        destination[index] = (destination[index].toInt() xor source[index].toInt()).toByte()
    }
}

internal class FountainEncoder(
    payload: ByteArray,
    val blockLength: Int,
    val sessionId: Int,
) {
    val blockCount: Int = calculateBlockCount(payload.size, blockLength)
    private val blocks = List(blockCount) { blockIndex ->
        ByteArray(blockLength).also { block ->
            val start = blockIndex * blockLength
            payload.copyInto(
                destination = block,
                startIndex = start,
                endIndex = min(start + blockLength, payload.size),
            )
        }
    }
    private val cdf = solitonCdf(blockCount)

    init {
        require(payload.isNotEmpty())
    }

    fun encode(sequence: UInt): ByteArray = ByteArray(blockLength).also { output ->
        frameIndices(blockCount, cdf, sessionId, sequence).forEach { blockIndex ->
            xorInto(output, blocks[blockIndex])
        }
    }
}

private fun calculateBlockCount(payloadLength: Int, blockLength: Int): Int {
    require(payloadLength > 0) { "Payload cannot be empty" }
    require(blockLength > 0) { "Block length must be positive" }
    return max(1, ceil(payloadLength.toDouble() / blockLength).toInt()).also { blockCount ->
        require(blockCount <= 0xFFFF) { "Payload requires too many source blocks" }
    }
}

private class PendingFrame(
    val indices: MutableSet<Int>,
    val bytes: ByteArray,
)

internal class FountainDecoder(
    val blockCount: Int,
    val blockLength: Int,
    val sessionId: Int,
    val totalLength: Int,
) {
    private val cdf = solitonCdf(blockCount)
    private val solved = arrayOfNulls<ByteArray>(blockCount)
    private val byBlock = mutableMapOf<Int, MutableSet<PendingFrame>>()
    private val seen = mutableSetOf<UInt>()

    var solvedCount: Int = 0
        private set
    var newFrameCount: Int = 0
        private set
    var duplicateFrameCount: Int = 0
        private set

    val isComplete: Boolean
        get() = solvedCount >= blockCount

    init {
        require(blockCount > 0)
        require(blockLength > 0)
        require(totalLength > 0)
    }

    fun addFrame(sequence: UInt, block: ByteArray) {
        require(block.size == blockLength)
        if (!seen.add(sequence)) {
            duplicateFrameCount++
            return
        }
        newFrameCount++
        if (isComplete) return

        val indices = frameIndices(blockCount, cdf, sessionId, sequence).toMutableSet()
        val bytes = block.copyOf()
        indices.toList().forEach { blockIndex ->
            solved[blockIndex]?.let { solvedBytes ->
                xorInto(bytes, solvedBytes)
                indices.remove(blockIndex)
            }
        }
        when (indices.size) {
            0 -> return // fully redundant
            1 -> resolve(indices.first(), bytes)
            else -> {
                val pendingFrame = PendingFrame(indices, bytes)
                indices.forEach { blockIndex ->
                    byBlock.getOrPut(blockIndex) { mutableSetOf() }.add(pendingFrame)
                }
            }
        }
    }

    /** Peeling cascade: solve a block, reduce every frame waiting on it, repeat.
     * Note for progress UX: this cascade back-loads — blocks solved hockey-
     * sticks near the end while frame ARRIVAL is linear. Show frames collected,
     * not blocks solved, or your progress bar will look stalled then teleport. */
    private fun resolve(firstBlock: Int, firstBytes: ByteArray) {
        val queue = mutableListOf(firstBlock to firstBytes)
        while (queue.isNotEmpty()) {
            val (blockIndex, bytes) = queue.removeAt(queue.lastIndex)
            if (solved[blockIndex] != null) continue
            solved[blockIndex] = bytes
            solvedCount++
            val waiting = byBlock.remove(blockIndex) ?: continue
            waiting.forEach { pendingFrame ->
                xorInto(pendingFrame.bytes, bytes)
                pendingFrame.indices.remove(blockIndex)
                if (pendingFrame.indices.size == 1) {
                    val remaining = pendingFrame.indices.first()
                    byBlock[remaining]?.remove(pendingFrame)
                    if (solved[remaining] == null) queue += remaining to pendingFrame.bytes
                }
            }
        }
    }

    fun assemble(): ByteArray? {
        if (!isComplete) return null
        return ByteArray(totalLength).also { output ->
            solved.forEachIndexed { blockIndex, block ->
                val start = blockIndex * blockLength
                val length = min(blockLength, totalLength - start)
                if (length > 0) {
                    requireNotNull(block).copyInto(output, start, endIndex = length)
                }
            }
        }
    }
}
