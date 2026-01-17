package de.aarondietz.lehrerlog

private fun nowMillis(): Double = js("Date.now()")

actual fun currentTimeMillis(): Long = nowMillis().toLong()
