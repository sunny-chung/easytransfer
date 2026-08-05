/*
 * Receiver result behavior adapted from decimen-optical-transfer/receive/main.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

sealed interface OpticalReceiveResult {
    data object Ignored : OpticalReceiveResult

    data class Receiving(
        val progress: OpticalReceiveProgress,
    ) : OpticalReceiveResult

    data class Completed(
        val payload: TransferPayload,
        val progress: OpticalReceiveProgress,
    ) : OpticalReceiveResult

    data class Corrupt(
        val reason: String,
    ) : OpticalReceiveResult
}

