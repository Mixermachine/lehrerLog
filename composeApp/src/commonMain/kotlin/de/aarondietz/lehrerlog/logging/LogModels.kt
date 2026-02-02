package de.aarondietz.lehrerlog.logging

data class LogFileEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedAt: Long
)

data class LogOverview(
    val files: List<LogFileEntry>,
    val totalSizeBytes: Long,
    val currentFileSizeBytes: Long,
    val previewLines: List<String>
)

data class LogSharePayload(
    val title: String,
    val text: String,
    val fileName: String
)
