/*
 * Progress estimation ported from decimen-optical-transfer/shared/progress.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class OpticalReceivePhase {
    Collecting,
    Decoding,
}

data class OpticalTransferProgressEstimate(
    val fraction: Double,
    val expectedFrames: Int,
    val etaMillis: Long?,
    val phase: OpticalReceivePhase,
)

/** Distinct frames per source block an LT stream needs, as a function of k. */
fun expectedFountainOverhead(sourceBlocks: Int): Double {
    val blockCount = max(1, sourceBlocks)
    return min(1.6, max(1.15, 1.1 + 2.45 / sqrt(blockCount.toDouble())))
}

fun estimateTransferProgress(
    sourceBlocks: Int,
    uniqueFrames: Int,
    elapsedMillis: Long,
    solvedBlocks: Int = 0,
): OpticalTransferProgressEstimate {
    val minimumFrames = max(1, sourceBlocks)
    val expectedFrames = max(
        minimumFrames + 1,
        ceil(minimumFrames * expectedFountainOverhead(minimumFrames)).toInt(),
    )
    val expectedRedundancy = expectedFrames - minimumFrames
    val frameFraction = when {
        uniqueFrames < minimumFrames -> 0.86 * (uniqueFrames.toDouble() / minimumFrames)
        uniqueFrames <= expectedFrames -> 0.86 +
            0.1 * ((uniqueFrames - minimumFrames).toDouble() / expectedRedundancy)
        else -> {
            val extra = (uniqueFrames - expectedFrames).toDouble() / expectedRedundancy
            0.96 + 0.03 * (1 - exp(-extra))
        }
    }
    val decodedFraction = 0.99 * min(1.0, solvedBlocks.toDouble() / minimumFrames)
    val fraction = min(0.99, max(frameFraction, decodedFraction))
    val phase = if (uniqueFrames < minimumFrames) {
        OpticalReceivePhase.Collecting
    } else {
        OpticalReceivePhase.Decoding
    }
    val elapsedSeconds = elapsedMillis / 1_000.0
    val rate = if (elapsedSeconds > 0.0) uniqueFrames / elapsedSeconds else 0.0
    val overshoot = uniqueFrames - expectedFrames
    val target = if (overshoot < 0) {
        expectedFrames
    } else {
        expectedFrames + expectedRedundancy * (overshoot / expectedRedundancy + 1)
    }
    val etaMillis = if (uniqueFrames >= 3 && elapsedSeconds >= 1.0 && rate > 0.0) {
        (((target - uniqueFrames) / rate) * 1_000.0).toLong().coerceAtLeast(0L)
    } else {
        null
    }
    return OpticalTransferProgressEstimate(
        fraction = fraction,
        expectedFrames = expectedFrames,
        etaMillis = etaMillis,
        phase = phase,
    )
}
