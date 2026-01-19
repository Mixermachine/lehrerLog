package de.aarondietz.lehrerlog

@OptIn(ExperimentalWasmJsInterop::class)
external interface JsInstallPrompt : JsAny {
    fun prompt()
}

@OptIn(ExperimentalWasmJsInterop::class)
external interface JsUserChoice : JsAny {
    val outcome: String
}

var deferredPrompt: JsInstallPrompt? = null

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(callback) => {
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        callback(e);
    });
}""")
external fun onBeforeInstallPrompt(callback: (JsInstallPrompt) -> Unit)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(callback) => {
    window.addEventListener('appinstalled', () => {
        callback();
    });
}""")
external fun onAppInstalled(callback: () -> Unit)

actual fun initInstallPrompt(onCanInstall: (Boolean) -> Unit) {
    onBeforeInstallPrompt { prompt ->
        deferredPrompt = prompt
        onCanInstall(true)
    }

    onAppInstalled {
        deferredPrompt = null
        onCanInstall(false)
    }
}

actual suspend fun triggerInstall(): Boolean {
    val prompt = deferredPrompt ?: return false
    prompt.prompt()
    return true
}

private val iosCheckResult: Boolean = isIosImpl()
actual fun isIos(): Boolean = iosCheckResult

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => /iphone|ipad|ipod/i.test(navigator.userAgent)""")
external fun isIosImpl(): Boolean

private val standaloneCheckResult: Boolean = isStandaloneImpl()
actual fun isStandalone(): Boolean = standaloneCheckResult

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    if ('standalone' in navigator && navigator.standalone === true) return true;
    return window.matchMedia('(display-mode: standalone)').matches;
}""")
external fun isStandaloneImpl(): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/service-worker.js')
            .then(() => console.log('SW registered'))
            .catch((e) => console.log('SW error:', e));
    }
}""")
actual external fun registerServiceWorker()
