package de.aarondietz.lehrerlog

import kotlinx.browser.window

actual fun isDebugBuild(): Boolean {
    val host = window.location.hostname
    return host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0"
}
