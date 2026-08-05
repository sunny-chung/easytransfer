package com.sunnychung.application.easytransfer.ui

import com.sunnychung.application.easytransfer.optical.TransferKind

internal val TransferKind.label: String
    get() = when (this) {
        TransferKind.Text -> "Text"
        TransferKind.Link -> "Link"
        TransferKind.Image -> "Image"
        TransferKind.File -> "File"
    }

internal val TransferKind.description: String
    get() = when (this) {
        TransferKind.Text -> "Paste or type a message"
        TransferKind.Link -> "Share a link or app URI"
        TransferKind.Image -> "Choose from your gallery"
        TransferKind.File -> "Select any file or binary"
    }