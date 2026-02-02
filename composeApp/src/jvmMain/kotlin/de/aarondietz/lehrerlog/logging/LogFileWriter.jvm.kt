package de.aarondietz.lehrerlog.logging

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

actual class LogFileWriter {
    private val lock = Any()
    private var initialized = false
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024
    private var maxFiles: Int = 5
    private var maxAgeDays: Int = 7
    private lateinit var logDir: File
    private lateinit var currentLogFile: File
    private var currentDate: String = ""

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int, maxAgeDays: Int) {
        this.maxFileSizeBytes = maxFileSizeMB.coerceAtLeast(1) * 1024L * 1024L
        this.maxFiles = maxFiles.coerceAtLeast(1)
        this.maxAgeDays = maxAgeDays.coerceAtLeast(1)

        val userHome = System.getProperty("user.home")
        logDir = File(userHome, ".lehrerlog/logs").apply { mkdirs() }
        currentDate = currentDateString()
        currentLogFile = File(logDir, "app_${currentDate}.log")
        if (!currentLogFile.exists()) {
            currentLogFile.createNewFile()
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
        synchronized(lock) {
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

            try {
                currentLogFile.appendText(logEntry)
            } catch (_: IOException) {
                // Swallow IO errors to avoid crashing log calls.
            }
        }
    }

    actual fun getLogFiles(): List<LogFileEntry> {
        if (!initialized || !logDir.exists()) return emptyList()
        return logDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("app_") && it.name.endsWith(".log") }
            ?.map { file ->
                LogFileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModifiedAt = file.lastModified()
                )
            }
            ?.sortedByDescending { it.lastModifiedAt }
            ?: emptyList()
    }

    actual fun readLogFile(path: String): String {
        val file = File(path)
        return if (file.exists() && file.isFile) {
            file.readText()
        } else {
            ""
        }
    }

    actual fun clearLogs() {
        if (!initialized || !logDir.exists()) return
        synchronized(lock) {
            logDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("app_") && it.name.endsWith(".log") }
                ?.forEach { it.delete() }
        }
    }

    actual fun getCurrentLogSize(): Long {
        return if (::currentLogFile.isInitialized && currentLogFile.exists()) {
            currentLogFile.length()
        } else {
            0L
        }
    }

    actual fun rotateNow() {
        if (!initialized) return
        synchronized(lock) {
            rotateNowLocked()
        }
    }

    private fun ensureCurrentLogFileLocked() {
        val today = currentDateString()
        if (today != currentDate) {
            currentDate = today
            currentLogFile = File(logDir, "app_${currentDate}.log")
            if (!currentLogFile.exists()) {
                currentLogFile.createNewFile()
            }
            cleanupOldLogsLocked()
        }
    }

    private fun rotateNowLocked() {
        if (!::currentLogFile.isInitialized || !currentLogFile.exists()) return
        val rotatedName = "app_${currentDateTimeString()}.log"
        val rotatedFile = File(logDir, rotatedName)
        currentLogFile.renameTo(rotatedFile)
        currentLogFile = File(logDir, "app_${currentDate}.log")
        if (!currentLogFile.exists()) {
            currentLogFile.createNewFile()
        }
        cleanupOldLogsLocked()
    }

    private fun cleanupOldLogsLocked() {
        if (!logDir.exists()) return
        val files = logDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("app_") && it.name.endsWith(".log") }
            ?: return

        val nowMillis = System.currentTimeMillis()
        val maxAgeMillis = maxAgeDays * 24L * 60L * 60L * 1000L
        files.filter { nowMillis - it.lastModified() > maxAgeMillis }
            .forEach { it.delete() }

        val remaining = logDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("app_") && it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (remaining.size > maxFiles) {
            remaining.drop(maxFiles).forEach { it.delete() }
        }
    }

    private fun currentDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE)
    }

    private fun currentDateTimeString(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        return LocalDateTime.now(ZoneId.systemDefault()).format(formatter)
    }
}
