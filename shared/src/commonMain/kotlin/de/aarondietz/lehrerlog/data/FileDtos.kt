package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class FileMetadataDto(
    val id: String,
    val objectKey: String,
    val sizeBytes: Long,
    val mimeType: String,
    val createdAt: String
)

