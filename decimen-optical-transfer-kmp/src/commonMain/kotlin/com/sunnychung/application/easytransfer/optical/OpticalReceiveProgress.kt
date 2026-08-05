/*
 * Receiver progress behavior adapted from decimen-optical-transfer/receive/main.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

data class OpticalReceiveProgress(
    val sessionId: Int? = null,
    val uniqueFrames: Int = 0,
    val typicalUniqueFrameTarget: Int = 1,
    val duplicateFrames: Int = 0,
    val recoveredBlocks: Int = 0,
    val sourceBlocks: Int = 0,
    val blockLength: Int = 0,
    val frameBytes: Int = 0,
    val totalLength: Int = 0,
    val isComplete: Boolean = false,
) {
    val framesCollected: Int
        get() = uniqueFrames
}
