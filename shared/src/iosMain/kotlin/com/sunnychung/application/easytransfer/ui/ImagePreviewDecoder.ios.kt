package com.sunnychung.application.easytransfer.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

internal actual fun ByteArray.decodePreviewImageBitmap(): ImageBitmap? =
    runCatching {
        SkiaImage.makeFromEncoded(this).toComposeImageBitmap()
    }.getOrNull()

