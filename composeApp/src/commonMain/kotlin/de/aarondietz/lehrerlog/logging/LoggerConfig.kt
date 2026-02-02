package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * Centralized logger configuration shared by all logger instances.
 */
object LoggerConfig {
    private var fileWriter: LogFileWriter? = null
    private var minSeverity: Severity = Severity.Info
    private var fileLoggingEnabled: Boolean = true
    private var consoleLoggingEnabled: Boolean = true
    private var extraLogWriters: List<LogWriter> = emptyList()

    fun initialize(
        fileWriter: LogFileWriter? = null,
        minSeverity: Severity = Severity.Info,
        enableFileLogging: Boolean = true,
        enableConsoleLogging: Boolean = true,
        extraLogWriters: List<LogWriter> = emptyList()
    ) {
        this.fileWriter = fileWriter
        this.minSeverity = minSeverity
        this.fileLoggingEnabled = enableFileLogging
        this.consoleLoggingEnabled = enableConsoleLogging
        this.extraLogWriters = extraLogWriters
    }

    fun getLogWriters(): List<LogWriter> {
        return buildList {
            if (consoleLoggingEnabled) {
                add(platformLogWriter())
            }
            addAll(extraLogWriters)
            if (fileLoggingEnabled && fileWriter != null) {
                add(KermitFileLogWriter(fileWriter!!))
            }
        }
    }

    fun getMinSeverity(): Severity = minSeverity
}
