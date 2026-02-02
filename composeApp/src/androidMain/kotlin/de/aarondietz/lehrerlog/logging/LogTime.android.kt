package de.aarondietz.lehrerlog.logging

import java.time.Instant

actual fun currentLogTimestamp(): String {
    return Instant.now().toString()
}
