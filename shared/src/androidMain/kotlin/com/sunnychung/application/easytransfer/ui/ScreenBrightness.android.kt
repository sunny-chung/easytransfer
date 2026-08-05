package com.sunnychung.application.easytransfer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun BoostScreenBrightness(
    active: Boolean,
    brightness: Float,
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(active, brightness, activity) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (active && window != null) {
            val attributes = window.attributes
            attributes.screenBrightness = brightness.coerceIn(0f, 1f)
            window.attributes = attributes
        }
        onDispose {
            if (window != null) {
                val attributes = window.attributes
                attributes.screenBrightness = previousBrightness
                window.attributes = attributes
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}