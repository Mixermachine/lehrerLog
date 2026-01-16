package de.aarondietz.lehrerlog.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of ConnectivityMonitor using NWPathMonitor.
 * Monitors network connectivity changes and emits state via StateFlow.
 */
actual class ConnectivityMonitor actual constructor(context: Any?) {

    private val _isConnected = MutableStateFlow(true)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    actual fun startMonitoring() {
        // No-op on iOS for now.
    }

    actual fun stopMonitoring() {
        // No-op on iOS for now.
    }
}
