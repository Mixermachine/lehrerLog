package de.aarondietz.lehrerlog.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific connectivity monitor to detect online/offline status.
 * Implementations should emit `true` when connected, `false` when disconnected.
 */
expect class ConnectivityMonitor {
    /**
     * Flow that emits the current connectivity status.
     * `true` when connected to network, `false` when offline.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Start monitoring connectivity changes.
     * Should be called when the app becomes active.
     */
    fun startMonitoring()

    /**
     * Stop monitoring connectivity changes.
     * Should be called when the app goes to background.
     */
    fun stopMonitoring()
}
