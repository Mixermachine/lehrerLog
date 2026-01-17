package de.aarondietz.lehrerlog.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of ConnectivityMonitor using NWPathMonitor.
 * Monitors network connectivity changes and emits state via StateFlow.
 */
actual class ConnectivityMonitor actual constructor(context: Any?) {

    private val _isConnected = MutableStateFlow(false)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val monitor = nw_path_monitor_create()
    private var started = false

    @OptIn(ExperimentalForeignApi::class)
    actual fun startMonitoring() {
        if (started) return
        started = true
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            _isConnected.value = status == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    actual fun stopMonitoring() {
        if (!started) return
        started = false
        nw_path_monitor_cancel(monitor)
    }
}
