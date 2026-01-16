package de.aarondietz.lehrerlog

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        alwaysOnTop = false,
        title = "lehrerlog",
    ) {
        App()
    }
}