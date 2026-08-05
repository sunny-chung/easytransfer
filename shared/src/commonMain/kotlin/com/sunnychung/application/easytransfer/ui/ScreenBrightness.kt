package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun BoostScreenBrightness(
    active: Boolean,
    brightness: Float = 0.82f,
)