package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig

/**
 * Creates a class-specific logger with automatic tag detection.
 */
inline fun <reified T : Any> T.logger(): Logger {
    return createLogger(T::class.simpleName ?: "Unknown")
}

/**
 * Creates a logger with a custom tag.
 */
fun Any.logger(tag: String): Logger {
    return createLogger(tag)
}

@PublishedApi
internal fun createLogger(tag: String): Logger {
    val config = LoggerConfig
    return Logger(
        config = StaticConfig(
            logWriterList = config.getLogWriters(),
            minSeverity = config.getMinSeverity()
        ),
        tag = tag
    )
}
