package de.aarondietz.lehrerlog.sync

import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.events.Event

/**
 * WasmJs (Web) implementation of ConnectivityMonitor.
 * Uses the browser's navigator.onLine API and online/offline events.
 */
actual class ConnectivityMonitor actual constructor(context: Any?) {

    private val _isConnected = MutableStateFlow(window.navigator.onLine)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val onlineListener: (Event) -> Unit = {
        _isConnected.value = true
    }

    private val offlineListener: (Event) -> Unit = {
        _isConnected.value = false
    }

    actual fun startMonitoring() {
        window.addEventListener("online", onlineListener)
        window.addEventListener("offline", offlineListener)

        // Update initial state
        _isConnected.value = window.navigator.onLine
    }

    actual fun stopMonitoring() {
        window.removeEventListener("online", onlineListener)
        window.removeEventListener("offline", offlineListener)
    }
}
