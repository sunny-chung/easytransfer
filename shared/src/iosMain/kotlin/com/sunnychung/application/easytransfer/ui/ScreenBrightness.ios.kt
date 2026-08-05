package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIScreen

@Composable
internal actual fun BoostScreenBrightness(
    active: Boolean,
    brightness: Float,
) {
    DisposableEffect(active, brightness) {
        val screen = UIScreen.mainScreen
        val previousBrightness = screen.brightness
        if (active) {
            screen.brightness = brightness.coerceIn(0f, 1f).toDouble()
        }
        onDispose { screen.brightness = previousBrightness }
    }
}