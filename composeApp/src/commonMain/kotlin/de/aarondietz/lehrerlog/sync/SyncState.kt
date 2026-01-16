package de.aarondietz.lehrerlog.sync

/**
 * Represents the current state of the synchronization process.
 */
sealed class SyncState {
    /**
     * No sync is currently happening
     */
    object Idle : SyncState()

    /**
     * Sync is in progress
     * @param progress Optional progress indicator (0.0 to 1.0)
     * @param message Optional status message
     */
    data class Syncing(val progress: Float? = null, val message: String? = null) : SyncState()

    /**
     * Sync completed successfully
     * @param itemsSynced Number of items that were synchronized
     */
    data class Success(val itemsSynced: Int = 0) : SyncState()

    /**
     * Sync failed with an error
     * @param error The error message
     * @param canRetry Whether the sync can be retried
     */
    data class Error(val error: String, val canRetry: Boolean = true) : SyncState()
}

/**
 * Summary of sync statistics
 */
data class SyncStats(
    val lastSyncTimestamp: Long = 0,
    val pendingChanges: Int = 0,
    val failedAttempts: Int = 0
)
