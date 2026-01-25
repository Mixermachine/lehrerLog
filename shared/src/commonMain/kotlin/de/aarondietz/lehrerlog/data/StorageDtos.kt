package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
enum class StorageOwnerType {
    SCHOOL,
    TEACHER
}

@Serializable
data class StorageQuotaDto(
    val ownerType: StorageOwnerType,
    val ownerId: String,
    val planId: String,
    val planName: String,
    val maxTotalBytes: Long,
    val maxFileBytes: Long,
    val usedTotalBytes: Long,
    val remainingBytes: Long
)

@Serializable
data class StorageUsageDto(
    val ownerType: StorageOwnerType,
    val ownerId: String,
    val usedTotalBytes: Long,
    val updatedAt: String
)

