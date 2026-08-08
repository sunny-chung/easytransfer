package com.sunnychung.application.easytransfer

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        icon = painterResource("icons/transfer-icon.png"),
        title = "EasyTransfer",
    ) {
        App()
    }
}
