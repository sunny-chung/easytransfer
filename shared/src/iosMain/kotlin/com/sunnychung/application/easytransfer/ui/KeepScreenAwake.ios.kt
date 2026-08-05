package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
internal actual fun KeepScreenAwake(active: Boolean) {
    DisposableEffect(active) {
        val application = UIApplication.sharedApplication
        val previousIdleTimerDisabled = application.idleTimerDisabled
        if (active) application.idleTimerDisabled = true
        onDispose { application.idleTimerDisabled = previousIdleTimerDisabled }
    }
}

