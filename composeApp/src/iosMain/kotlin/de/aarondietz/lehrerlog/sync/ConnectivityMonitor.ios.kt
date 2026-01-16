package de.aarondietz.lehrerlog.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.NWPathMonitor
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of ConnectivityMonitor using NWPathMonitor.
 * Monitors network connectivity changes and emits state via StateFlow.
 */
actual class ConnectivityMonitor {

    private val pathMonitor = NWPathMonitor()

    private val _isConnected = MutableStateFlow(false)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    actual fun startMonitoring() {
        pathMonitor.setPathUpdateHandler { path ->
            val status = nw_path_get_status(path)
            _isConnected.value = status == nw_path_status_satisfied
        }

        pathMonitor.start(dispatch_get_main_queue())
    }

    actual fun stopMonitoring() {
        pathMonitor.cancel()
    }
}
