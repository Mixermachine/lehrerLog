package de.aarondietz.lehrerlog.logging

import de.aarondietz.lehrerlog.coroutines.AppDispatchers
import kotlinx.coroutines.withContext

class LogRepository(
    private val logFileWriter: LogFileWriter
) {
    suspend fun loadOverview(maxPreviewLines: Int = 200): Result<LogOverview> = runCatching {
        withContext(AppDispatchers.io) {
            val files = logFileWriter.getLogFiles()
            val totalSize = files.sumOf { it.sizeBytes }
            val currentSize = logFileWriter.getCurrentLogSize()
            val previewLines = files.firstOrNull()?.let { entry ->
                readPreviewLines(entry.path, maxPreviewLines)
            } ?: emptyList()
            LogOverview(
                files = files,
                totalSizeBytes = totalSize,
                currentFileSizeBytes = currentSize,
                previewLines = previewLines
            )
        }
    }

    suspend fun clearLogs(): Result<Unit> = runCatching {
        withContext(AppDispatchers.io) {
            logFileWriter.clearLogs()
        }
    }

    suspend fun buildSharePayload(): Result<LogSharePayload> = runCatching {
        withContext(AppDispatchers.io) {
            val files = logFileWriter.getLogFiles().sortedBy { it.lastModifiedAt }
            val combined = buildString {
                files.forEach { entry ->
                    append("===== ").append(entry.name).append(" =====\n")
                    val content = logFileWriter.readLogFile(entry.path)
                    append(content.trimEnd())
                    append("\n\n")
                }
            }.ifBlank { "(no logs)" }

            LogSharePayload(
                title = "LehrerLog Logs",
                text = combined.trimEnd(),
                fileName = "lehrerlog-logs.txt"
            )
        }
    }

    private fun readPreviewLines(path: String, maxLines: Int): List<String> {
        val content = logFileWriter.readLogFile(path)
        if (content.isBlank()) return emptyList()
        val lines = content.split("\n").filter { it.isNotBlank() }
        return if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
    }
}
