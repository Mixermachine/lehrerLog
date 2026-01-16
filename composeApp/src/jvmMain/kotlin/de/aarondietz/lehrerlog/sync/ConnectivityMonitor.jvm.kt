package de.aarondietz.lehrerlog.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

/**
 * JVM (Desktop) implementation of ConnectivityMonitor.
 * Uses periodic network checks to determine connectivity status.
 */
actual class ConnectivityMonitor actual constructor(context: Any?) {

    private val _isConnected = MutableStateFlow(checkConnectivity())
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    actual fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (isActive) {
                val isConnected = checkConnectivity()
                _isConnected.value = isConnected
                delay(5000) // Check every 5 seconds
            }
        }
    }

    actual fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    private fun checkConnectivity(): Boolean {
        return try {
            // Try to connect to Google's DNS server
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), 2000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
