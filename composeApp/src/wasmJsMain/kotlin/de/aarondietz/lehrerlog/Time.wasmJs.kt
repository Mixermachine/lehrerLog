package de.aarondietz.lehrerlog

@OptIn(ExperimentalWasmJsInterop::class)
private fun nowMillis(): Double = js("Date.now()")

actual fun currentTimeMillis(): Long = nowMillis().toLong()
