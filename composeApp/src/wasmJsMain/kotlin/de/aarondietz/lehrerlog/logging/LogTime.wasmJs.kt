package de.aarondietz.lehrerlog.logging

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => new Date().toISOString()""")
external fun currentIsoTimestamp(): String

actual fun currentLogTimestamp(): String = currentIsoTimestamp()
