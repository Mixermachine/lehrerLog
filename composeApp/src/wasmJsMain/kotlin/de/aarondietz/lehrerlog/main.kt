package de.aarondietz.lehrerlog

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    registerServiceWorker()

    ComposeViewport {
        App()
    }
}