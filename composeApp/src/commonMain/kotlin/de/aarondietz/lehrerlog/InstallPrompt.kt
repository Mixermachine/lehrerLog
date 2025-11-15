package de.aarondietz.lehrerlog

expect fun isIos(): Boolean
expect fun isStandalone(): Boolean
expect fun initInstallPrompt(onCanInstall: (Boolean) -> Unit)
expect suspend fun triggerInstall(): Boolean
expect fun registerServiceWorker()
