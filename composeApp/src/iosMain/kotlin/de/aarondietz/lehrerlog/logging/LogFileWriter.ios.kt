package de.aarondietz.lehrerlog.logging

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual class LogFileWriter {
    private val lock = NSLock()
    private var initialized = false
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024
    private var maxFiles: Int = 5
    private var maxAgeDays: Int = 7
    private lateinit var logDir: String
    private lateinit var currentLogFile: String
    private var currentDate: String = ""
    private val fileManager = NSFileManager.defaultManager

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int, maxAgeDays: Int) {
        this.maxFileSizeBytes = maxFileSizeMB.coerceAtLeast(1) * 1024L * 1024L
        this.maxFiles = maxFiles.coerceAtLeast(1)
        this.maxAgeDays = maxAgeDays.coerceAtLeast(1)

        val appSupportUrl = fileManager.URLsForDirectory(
            NSApplicationSupportDirectory,
            NSUserDomainMask
        ).firstOrNull() as? NSURL

        val basePath = appSupportUrl?.path ?: NSTemporaryDirectory()
        logDir = "$basePath/logs"
        fileManager.createDirectoryAtPath(
            logDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        currentDate = currentDateString()
        currentLogFile = "$logDir/app_${currentDate}.log"
        if (!fileManager.fileExistsAtPath(currentLogFile)) {
            fileManager.createFileAtPath(currentLogFile, contents = null, attributes = null)
        }
        cleanupOldLogsLocked()
        initialized = true
    }

    actual fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (!initialized) return
        lock.lock()
        try {
            ensureCurrentLogFileLocked()
            if (getCurrentLogSize() >= maxFileSizeBytes) {
                rotateNowLocked()
            }

            val logEntry = buildString {
                append(timestamp)
                append(" [").append(level).append("]")
                append(" [").append(tag).append("] ")
                append(message)
                if (throwable != null) {
                    append("\n").append(throwable.stackTraceToString())
                }
                append("\n")
            }

            val existing = readLogFile(currentLogFile)
            val combined = existing + logEntry
            val data = NSString.create(string = combined).dataUsingEncoding(NSUTF8StringEncoding) ?: return
            fileManager.createFileAtPath(currentLogFile, contents = data, attributes = null)
        } finally {
            lock.unlock()
        }
    }

    actual fun getLogFiles(): List<LogFileEntry> {
        if (!initialized) return emptyList()
        val entries = fileManager.contentsOfDirectoryAtPath(logDir, error = null)
            ?.filterIsInstance<String>()
            ?: return emptyList()
        return entries.filter { it.startsWith("app_") && it.endsWith(".log") }
            .map { name ->
                val path = "$logDir/$name"
                val attrs = fileManager.attributesOfItemAtPath(path, error = null)
                val size = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
                val modified = (attrs?.get(NSFileModificationDate) as? NSDate)
                    ?.timeIntervalSince1970
                    ?.times(1000)
                    ?.toLong() ?: 0L
                LogFileEntry(
                    name = name,
                    path = path,
                    sizeBytes = size,
                    lastModifiedAt = modified
                )
            }
            .sortedByDescending { it.lastModifiedAt }
    }

    actual fun readLogFile(path: String): String {
        return NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) ?: ""
    }

    actual fun clearLogs() {
        if (!initialized) return
        lock.lock()
        try {
            val entries = fileManager.contentsOfDirectoryAtPath(logDir, error = null)
                ?.filterIsInstance<String>()
                ?: return
            entries.filter { it.startsWith("app_") && it.endsWith(".log") }
                .forEach { name ->
                    fileManager.removeItemAtPath("$logDir/$name", error = null)
                }
        } finally {
            lock.unlock()
        }
    }

    actual fun getCurrentLogSize(): Long {
        val attrs = fileManager.attributesOfItemAtPath(currentLogFile, error = null)
        return (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }

    actual fun rotateNow() {
        if (!initialized) return
        lock.lock()
        try {
            rotateNowLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun ensureCurrentLogFileLocked() {
        val today = currentDateString()
        if (today != currentDate) {
            currentDate = today
            currentLogFile = "$logDir/app_${currentDate}.log"
            if (!fileManager.fileExistsAtPath(currentLogFile)) {
                fileManager.createFileAtPath(currentLogFile, contents = null, attributes = null)
            }
            cleanupOldLogsLocked()
        }
    }

    private fun rotateNowLocked() {
        val rotatedName = "app_${currentDateTimeString()}.log"
        val rotatedPath = "$logDir/$rotatedName"
        fileManager.moveItemAtPath(currentLogFile, toPath = rotatedPath, error = null)
        currentLogFile = "$logDir/app_${currentDate}.log"
        if (!fileManager.fileExistsAtPath(currentLogFile)) {
            fileManager.createFileAtPath(currentLogFile, contents = null, attributes = null)
        }
        cleanupOldLogsLocked()
    }

    private fun cleanupOldLogsLocked() {
        val entries = fileManager.contentsOfDirectoryAtPath(logDir, error = null)
            ?.filterIsInstance<String>()
            ?: return
        val logFiles = entries.filter { it.startsWith("app_") && it.endsWith(".log") }
        val nowMillis = NSDate().timeIntervalSince1970 * 1000.0
        val maxAgeMillis = maxAgeDays * 24L * 60L * 60L * 1000L

        logFiles.forEach { name ->
            val path = "$logDir/$name"
            val attrs = fileManager.attributesOfItemAtPath(path, error = null)
            val modified = (attrs?.get(NSFileModificationDate) as? NSDate)
                ?.timeIntervalSince1970
                ?.times(1000)
                ?: return@forEach
            if (nowMillis - modified > maxAgeMillis) {
                fileManager.removeItemAtPath(path, error = null)
            }
        }

        val remaining = fileManager.contentsOfDirectoryAtPath(logDir, error = null)
            ?.filterIsInstance<String>()
            ?: return
        val remainingEntries = remaining.filter { it.startsWith("app_") && it.endsWith(".log") }
            .map { name ->
                val path = "$logDir/$name"
                val attrs = fileManager.attributesOfItemAtPath(path, error = null)
                val modified = (attrs?.get(NSFileModificationDate) as? NSDate)
                    ?.timeIntervalSince1970
                    ?.times(1000)
                    ?.toLong() ?: 0L
                name to modified
            }
            .sortedByDescending { it.second }

        if (remainingEntries.size > maxFiles) {
            remainingEntries.drop(maxFiles).forEach { (name, _) ->
                fileManager.removeItemAtPath("$logDir/$name", error = null)
            }
        }
    }

    private fun currentDateString(): String {
        val formatter = NSISO8601DateFormatter()
        val dateString = formatter.stringFromDate(NSDate())
        return dateString.substringBefore("T")
    }

    private fun currentDateTimeString(): String {
        val formatter = NSISO8601DateFormatter()
        return formatter.stringFromDate(NSDate())
            .replace(":", "-")
            .replace(".", "-")
    }
}
