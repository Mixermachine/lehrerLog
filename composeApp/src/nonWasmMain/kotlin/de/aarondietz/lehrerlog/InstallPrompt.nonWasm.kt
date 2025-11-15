package de.aarondietz.lehrerlog

// Default no-op implementations for non-web targets
actual fun isIos(): Boolean = false
actual fun isStandalone(): Boolean = false
actual fun initInstallPrompt(onCanInstall: (Boolean) -> Unit) {}
actual suspend fun triggerInstall(): Boolean = false
actual fun registerServiceWorker() {}
