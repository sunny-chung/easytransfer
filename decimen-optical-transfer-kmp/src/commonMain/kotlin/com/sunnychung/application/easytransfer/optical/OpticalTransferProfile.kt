/*
 * Transfer tuning adapted from decimen-optical-transfer/send/main.ts and receive/main.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.optical

// Each value below is one of the sender settings exposed by the original UI.
enum class OpticalTxFps(
    val label: String,
    val framesPerSecond: Int,
) {
    Fps10(label = "10", framesPerSecond = 10),
    Fps15(label = "15", framesPerSecond = 15),
    Fps20(label = "20", framesPerSecond = 20),
    Fps24(label = "24", framesPerSecond = 24),
    Fps30(label = "30", framesPerSecond = 30),
    Fps60(label = "60", framesPerSecond = 60),
}

// frameBytes includes the 20-byte protocol header, as it does upstream.
enum class OpticalFrameSize(
    val label: String,
    val frameBytes: Int,
) {
    Bytes500(label = "500", frameBytes = 500),
    Bytes1000(label = "1000", frameBytes = 1_000),
    Bytes1465(label = "1465", frameBytes = 1_465),
    Bytes1850(label = "1850", frameBytes = 1_850),
    Bytes2331(label = "2331", frameBytes = 2_331),
    Bytes2953(label = "2953", frameBytes = 2_953),
    ;

    val blockLength: Int
        get() = frameBytes - OPTICAL_HEADER_LENGTH
}

enum class OpticalErrorCorrection(
    val label: String,
) {
    Low(label = "L"),
    Medium(label = "M"),
    Quartile(label = "Q"),
    High(label = "H"),
}

enum class OpticalCompressionMode(
    val label: String,
    val isEnabled: Boolean,
) {
    Enabled(label = "On", isEnabled = true),
    Disabled(label = "Off", isEnabled = false),
}

data class OpticalTransferSettings(
    val txFps: OpticalTxFps = OpticalTxFps.Fps60,
    val frameSize: OpticalFrameSize = OpticalFrameSize.Bytes2953,
    val errorCorrection: OpticalErrorCorrection = OpticalErrorCorrection.Low,
    val compressionMode: OpticalCompressionMode = OpticalCompressionMode.Enabled,
) {
    val framesPerSecond: Int
        get() = txFps.framesPerSecond

    val frameBytes: Int
        get() = frameSize.frameBytes

    val blockLength: Int
        get() = frameSize.blockLength

    val frameIntervalMillis: Long
        get() = (1_000L + framesPerSecond - 1) / framesPerSecond

    val qrByteCapacity: Int
        get() = when (errorCorrection) {
            OpticalErrorCorrection.Low -> 2_953
            OpticalErrorCorrection.Medium -> 2_331
            OpticalErrorCorrection.Quartile -> 1_663
            OpticalErrorCorrection.High -> 1_273
        }

    val canRenderRawFrame: Boolean
        get() = frameBytes <= qrByteCapacity

    val isCompressionEnabled: Boolean
        get() = compressionMode.isEnabled
}

const val DEFAULT_OPTICAL_FRAME_BYTES = 2_953
