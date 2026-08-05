package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun KeepScreenAwake(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active, view) {
        val previousKeepScreenOn = view.keepScreenOn
        if (active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previousKeepScreenOn }
    }
}

