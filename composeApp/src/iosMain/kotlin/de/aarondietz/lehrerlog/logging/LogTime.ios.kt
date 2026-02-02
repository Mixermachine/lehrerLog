package de.aarondietz.lehrerlog.logging

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter

actual fun currentLogTimestamp(): String {
    val formatter = NSISO8601DateFormatter()
    return formatter.stringFromDate(NSDate())
}
