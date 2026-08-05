/*
 * Receiver tuning adapted from decimen-optical-transfer/receive/main.ts.
 * Copyright (c) 2026 BashAlarmist. Licensed under the MIT License.
 * See third_party/decimen-optical-transfer/LICENSE.
 */
package com.sunnychung.application.easytransfer.camera

// Capture widths match the settings exposed by the original receiver UI.
enum class OpticalCameraWidth(
    val label: String,
    val width: Int,
) {
    Width960(label = "960", width = 960),
    Width1280(label = "1280", width = 1_280),
    Width1920(label = "1920", width = 1_920),
    ;

    val height: Int
        get() = width * 3 / 4
}

enum class OpticalCaptureFps(
    val label: String,
    val framesPerSecond: Int,
) {
    Fps30(label = "30", framesPerSecond = 30),
    Fps60(label = "60", framesPerSecond = 60),
}

enum class OpticalDecodeWorkers(
    val label: String,
    val workerCount: Int,
) {
    Workers2(label = "2", workerCount = 2),
    Workers3(label = "3", workerCount = 3),
}

data class OpticalCameraSettings(
    val width: OpticalCameraWidth = OpticalCameraWidth.Width1280,
    val captureFps: OpticalCaptureFps = OpticalCaptureFps.Fps60,
    val decodeWorkers: OpticalDecodeWorkers = OpticalDecodeWorkers.Workers2,
) {
    val targetWidth: Int
        get() = width.width

    val targetHeight: Int
        get() = width.height
}
