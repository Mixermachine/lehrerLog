package de.aarondietz.lehrerlog.sync

import kotlinx.serialization.Serializable

/**
 * Represents a single change entry in the sync log.
 */
@Serializable
data class SyncChangeDto(
    val id: Long,
    val entityType: String,
    val entityId: String,
    val operation: String, // CREATE, UPDATE, DELETE
    val timestamp: Long,
    val data: String? = null // JSON payload for CREATE/UPDATE operations
)

/**
 * Response containing changes since a specific sync log ID.
 */
@Serializable
data class SyncChangesResponse(
    val changes: List<SyncChangeDto>,
    val lastSyncId: Long,
    val hasMore: Boolean = false
)

/**
 * Request to push local changes to the server.
 */
@Serializable
data class PushChangeRequest(
    val entityType: String,
    val entityId: String,
    val operation: String, // CREATE, UPDATE, DELETE
    val version: Long,
    val data: String? = null // JSON payload for CREATE/UPDATE operations
)

/**
 * Request body for pushing multiple changes.
 */
@Serializable
data class PushChangesRequest(
    val changes: List<PushChangeRequest>
)

/**
 * Result of a single push change operation.
 */
@Serializable
data class PushChangeResult(
    val entityId: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val conflict: Boolean = false,
    val serverVersion: Long? = null
)

/**
 * Response after pushing changes to the server.
 */
@Serializable
data class PushChangesResponse(
    val results: List<PushChangeResult>,
    val successCount: Int,
    val failureCount: Int
)
