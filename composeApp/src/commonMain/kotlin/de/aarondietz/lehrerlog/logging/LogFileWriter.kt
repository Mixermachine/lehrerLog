package de.aarondietz.lehrerlog.logging

/**
 * Platform-specific log file writer with rotation support.
 */
expect class LogFileWriter() {
    fun initialize(maxFileSizeMB: Int = 2, maxFiles: Int = 5, maxAgeDays: Int = 7)

    fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null
    )

    fun getLogFiles(): List<LogFileEntry>

    fun readLogFile(path: String): String

    fun clearLogs()

    fun getCurrentLogSize(): Long

    fun rotateNow()
}
