package de.aarondietz.lehrerlog.logging

import kotlinx.browser.localStorage
import kotlin.math.max

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => Date.now()""")
external fun nowMillis(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => !!(navigator.storage && navigator.storage.getDirectory)""")
external fun isOpfsSupported(): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(fileName, content) => {
    if (!navigator.storage || !navigator.storage.getDirectory) return;
    navigator.storage.getDirectory().then(async (dir) => {
        const handle = await dir.getFileHandle(fileName, { create: true });
        const file = await handle.getFile();
        const writable = await handle.createWritable({ keepExistingData: true });
        await writable.write({ type: 'write', position: file.size, data: content });
        await writable.close();
    }).catch(() => {});
}"""
)
external fun appendOpfsLog(fileName: String, content: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => new Date().toISOString().slice(0, 10)""")
external fun currentDateString(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => new Date().toISOString().replace(/[:.]/g, '-')"""
)
external fun currentDateTimeString(): String

private const val INDEX_KEY = "lehrerlog_log_index"
private const val CURRENT_KEY = "lehrerlog_log_current"
private const val LOG_PREFIX = "lehrerlog_log_"

private data class LogMeta(
    val name: String,
    val lastModifiedAt: Long,
    val sizeBytes: Long
)

actual class LogFileWriter {
    private var initialized = false
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024
    private var maxFiles: Int = 5
    private var maxAgeDays: Int = 7
    private var currentDate: String = ""
    private val opfsAvailable: Boolean = isOpfsSupported()

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int, maxAgeDays: Int) {
        this.maxFileSizeBytes = maxFileSizeMB.coerceAtLeast(1) * 1024L * 1024L
        this.maxFiles = maxFiles.coerceAtLeast(1)
        this.maxAgeDays = maxAgeDays.coerceAtLeast(1)

        currentDate = currentDateString()
        val index = loadIndex()
        val currentName = resolveCurrentFileName(currentDate)
        setCurrentFileName(currentName)
        ensureFileEntry(currentName, index)
        cleanupOldLogs(index)
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
        ensureCurrentLogFile()
        if (getCurrentLogSize() >= maxFileSizeBytes) {
            rotateNow()
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

        val currentName = getCurrentFileName()
        val storageKey = storageKey(currentName)
        val existing = safeGetItem(storageKey).orEmpty()
        val updated = existing + logEntry
        safeSetItem(storageKey, updated)
        updateMeta(currentName, updated.length.toLong())

        if (opfsAvailable) {
            appendOpfsLog(currentName, logEntry)
        }
    }

    actual fun getLogFiles(): List<LogFileEntry> {
        if (!initialized) return emptyList()
        return loadIndex()
            .sortedByDescending { it.lastModifiedAt }
            .map { meta ->
                LogFileEntry(
                    name = meta.name,
                    path = meta.name,
                    sizeBytes = meta.sizeBytes,
                    lastModifiedAt = meta.lastModifiedAt
                )
            }
    }

    actual fun readLogFile(path: String): String {
        return safeGetItem(storageKey(path)).orEmpty()
    }

    actual fun clearLogs() {
        if (!initialized) return
        val index = loadIndex()
        index.forEach { meta ->
            safeRemoveItem(storageKey(meta.name))
        }
        saveIndex(emptyList())
        currentDate = currentDateString()
        val currentName = resolveCurrentFileName(currentDate)
        setCurrentFileName(currentName)
        ensureFileEntry(currentName, mutableListOf())
    }

    actual fun getCurrentLogSize(): Long {
        val currentName = getCurrentFileName()
        val meta = loadIndex().firstOrNull { it.name == currentName }
        return meta?.sizeBytes ?: 0L
    }

    actual fun rotateNow() {
        if (!initialized) return
        val index = loadIndex()
        val currentName = getCurrentFileName()
        val currentKey = storageKey(currentName)
        val content = safeGetItem(currentKey).orEmpty()

        val rotatedName = "app_${currentDateTimeString()}.log"
        safeSetItem(storageKey(rotatedName), content)
        safeRemoveItem(currentKey)

        val now = nowMillis().toLong()
        val updatedIndex = index.filterNot { it.name == currentName }.toMutableList()
        updatedIndex.add(
            LogMeta(
                name = rotatedName,
                lastModifiedAt = now,
                sizeBytes = content.length.toLong()
            )
        )

        currentDate = currentDateString()
        val newCurrent = resolveCurrentFileName(currentDate)
        setCurrentFileName(newCurrent)
        ensureFileEntry(newCurrent, updatedIndex)
        cleanupOldLogs(updatedIndex)
    }

    private fun ensureCurrentLogFile() {
        val today = currentDateString()
        if (today != currentDate) {
            currentDate = today
            val index = loadIndex()
            val currentName = resolveCurrentFileName(today)
            setCurrentFileName(currentName)
            ensureFileEntry(currentName, index)
            cleanupOldLogs(index)
        }
    }

    private fun resolveCurrentFileName(today: String): String {
        val saved = safeGetItem(CURRENT_KEY)
        val expected = "app_${today}.log"
        return if (saved != null && saved.startsWith("app_${today}")) saved else expected
    }

    private fun getCurrentFileName(): String {
        return safeGetItem(CURRENT_KEY) ?: "app_${currentDateString()}.log"
    }

    private fun setCurrentFileName(name: String) {
        safeSetItem(CURRENT_KEY, name)
    }

    private fun ensureFileEntry(fileName: String, index: MutableList<LogMeta>) {
        val content = safeGetItem(storageKey(fileName)).orEmpty()
        val now = nowMillis().toLong()
        val entry = LogMeta(fileName, now, content.length.toLong())
        val existingIndex = index.indexOfFirst { it.name == fileName }
        if (existingIndex >= 0) {
            index[existingIndex] = entry
        } else {
            index.add(entry)
        }
        safeSetItem(storageKey(fileName), content)
        saveIndex(index)
    }

    private fun updateMeta(fileName: String, sizeBytes: Long) {
        val index = loadIndex().toMutableList()
        val now = nowMillis().toLong()
        val updatedSize = max(sizeBytes, 0L)
        val updated = LogMeta(fileName, now, updatedSize)
        val existingIndex = index.indexOfFirst { it.name == fileName }
        if (existingIndex >= 0) {
            index[existingIndex] = updated
        } else {
            index.add(updated)
        }
        saveIndex(index)
    }

    private fun cleanupOldLogs(index: MutableList<LogMeta>) {
        val now = nowMillis().toLong()
        val maxAgeMillis = maxAgeDays * 24L * 60L * 60L * 1000L
        val filtered = index.filter { now - it.lastModifiedAt <= maxAgeMillis }.toMutableList()
        val removed = index.filterNot { meta -> filtered.any { it.name == meta.name } }
        removed.forEach { meta ->
            safeRemoveItem(storageKey(meta.name))
        }

        val sorted = filtered.sortedByDescending { it.lastModifiedAt }.toMutableList()
        if (sorted.size > maxFiles) {
            val toRemove = sorted.drop(maxFiles)
            toRemove.forEach { meta ->
                safeRemoveItem(storageKey(meta.name))
            }
            sorted.retainAll(sorted.take(maxFiles))
        }

        saveIndex(sorted)
    }

    private fun storageKey(name: String): String = "$LOG_PREFIX$name"

    private fun loadIndex(): MutableList<LogMeta> {
        val raw = safeGetItem(INDEX_KEY) ?: return mutableListOf()
        if (raw.isBlank()) return mutableListOf()
        return raw.split("\n")
            .mapNotNull { parseMeta(it) }
            .toMutableList()
    }

    private fun saveIndex(entries: List<LogMeta>) {
        val encoded = entries.joinToString("\n") {
            "${it.name}|${it.lastModifiedAt}|${it.sizeBytes}"
        }
        safeSetItem(INDEX_KEY, encoded)
    }

    private fun parseMeta(line: String): LogMeta? {
        val parts = line.split("|")
        if (parts.size != 3) return null
        val name = parts[0]
        val modified = parts[1].toLongOrNull() ?: return null
        val size = parts[2].toLongOrNull() ?: return null
        return LogMeta(name, modified, size)
    }

    private fun safeGetItem(key: String): String? {
        return try {
            localStorage.getItem(key)
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeSetItem(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (_: Throwable) {
            // Ignore storage errors.
        }
    }

    private fun safeRemoveItem(key: String) {
        try {
            localStorage.removeItem(key)
        } catch (_: Throwable) {
            // Ignore storage errors.
        }
    }

}
