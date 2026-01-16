package de.aarondietz.lehrerlog.sync

import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.api.SyncApi
import de.aarondietz.lehrerlog.lehrerLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.aarondietz.lehrerlog.currentTimeMillis

/**
 * Manages synchronization between local database and server.
 * Monitors connectivity and automatically syncs when online.
 */
class SyncManager(
    private val database: lehrerLog,
    private val syncApi: SyncApi,
    private val connectivityMonitor: ConnectivityMonitor,
    private val logger: Logger
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncStats = MutableStateFlow(SyncStats())
    val syncStats: StateFlow<SyncStats> = _syncStats.asStateFlow()

    private var autoSyncJob: Job? = null
    private var isSyncing = false

    /**
     * Start automatic sync monitoring.
     * Syncs when connectivity is restored and periodically when online.
     */
    fun startAutoSync() {
        logger.i { "Starting auto-sync monitoring" }
        connectivityMonitor.startMonitoring()

        autoSyncJob = scope.launch {
            var wasConnected = false
            connectivityMonitor.isConnected
                .collect { isConnected ->
                    logger.d { "Connectivity changed: isConnected=$isConnected, wasConnected=$wasConnected" }
                    if (isConnected && !wasConnected) {
                        // Sync when connectivity is restored
                        wasConnected = true
                        logger.i { "Connectivity restored, triggering sync in 1 second" }
                        delay(1000) // Small delay to ensure connection is stable
                        sync()

                        // Start periodic sync every 5 minutes while online
                        while (isActive && connectivityMonitor.isConnected.value) {
                            logger.d { "Scheduling next periodic sync in 5 minutes" }
                            delay(300_000) // 5 minutes
                            if (connectivityMonitor.isConnected.value) {
                                logger.d { "Triggering periodic sync" }
                                sync()
                            }
                        }
                    } else if (!isConnected) {
                        logger.w { "Lost connectivity" }
                        wasConnected = false
                    }
                }
        }
    }

    /**
     * Stop automatic sync monitoring.
     */
    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
        connectivityMonitor.stopMonitoring()
    }

    /**
     * Manually trigger a sync operation.
     * Pushes local changes and pulls remote changes.
     */
    suspend fun sync(): Boolean {
        logger.i { "sync() called - isSyncing=$isSyncing" }
        if (isSyncing) {
            logger.w { "Sync already in progress, skipping" }
            return false
        }

        if (!connectivityMonitor.isConnected.value) {
            logger.w { "No internet connection, cannot sync" }
            _syncState.value = SyncState.Error("No internet connection", canRetry = true)
            return false
        }

        isSyncing = true
        _syncState.value = SyncState.Syncing(message = "Starting sync...")
        logger.i { "Starting sync process" }

        return try {
            // Step 1: Push local changes
            logger.d { "Phase 1: Pushing local changes" }
            _syncState.value = SyncState.Syncing(progress = 0.3f, message = "Pushing local changes...")
            val pushSuccess = pushLocalChanges()

            if (!pushSuccess) {
                logger.e { "Failed to push local changes" }
                _syncState.value = SyncState.Error("Failed to push local changes", canRetry = true)
                return false
            }

            logger.d { "Phase 2: Pulling remote changes" }
            // Step 2: Pull remote changes
            _syncState.value = SyncState.Syncing(progress = 0.6f, message = "Pulling remote changes...")
            val itemsSynced = pullRemoteChanges()

            // Update stats
            _syncStats.value = _syncStats.value.copy(
                lastSyncTimestamp = currentTimeMillis(),
                pendingChanges = getPendingChangesCount(),
                failedAttempts = 0
            )

            logger.i { "Sync completed successfully, itemsSynced=$itemsSynced" }
            _syncState.value = SyncState.Success(itemsSynced = itemsSynced)
            true
        } catch (e: Exception) {
            logger.e(e) { "Sync failed with exception: ${e.message}" }
            _syncStats.value = _syncStats.value.copy(
                failedAttempts = _syncStats.value.failedAttempts + 1
            )
            _syncState.value = SyncState.Error(e.message ?: "Sync failed", canRetry = true)
            false
        } finally {
            isSyncing = false
            logger.d { "Sync process ended, isSyncing=false" }
        }
    }

    /**
     * Push local unsynced changes to the server.
     */
    private suspend fun pushLocalChanges(): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val pendingChanges = database.pendingSyncQueries.getAllPending().executeAsList()

                logger.d { "Found ${pendingChanges.size} pending changes to push" }

                if (pendingChanges.isEmpty()) {
                    logger.d { "No pending changes, skipping push" }
                    return@withContext true
                }

                // Group changes and send to server
                val changes = pendingChanges.mapNotNull { pending ->
                    logger.d { "Processing pending change: entityType=${pending.entityType}, entityId=${pending.entityId}, operation=${pending.operation}" }
                    val entityData = fetchEntityData(pending.entityType, pending.entityId)
                    val version = getEntityVersion(pending.entityType, pending.entityId)

                    // Skip if entity data not found (might have been deleted)
                    if (pending.operation != "DELETE" && entityData == null) {
                        logger.w { "Skipping change - entity data not found for entityId=${pending.entityId}" }
                        return@mapNotNull null
                    }

                    logger.d { "Prepared change request: entityType=${pending.entityType}, version=$version, hasData=${entityData != null}" }

                    PushChangeRequest(
                        entityType = pending.entityType,
                        entityId = pending.entityId,
                        operation = pending.operation,
                        version = version,
                        data = entityData
                    )
                }

                logger.i { "Pushing ${changes.size} changes to server" }

                val response = syncApi.pushChanges(
                    PushChangesRequest(changes = changes)
                )

                logger.i { "Push response: successCount=${response.successCount}, failureCount=${response.failureCount}" }

                // Process results and remove successfully synced items
                response.results.forEach { result ->
                    if (result.success) {
                        // Remove from pending queue
                        database.pendingSyncQueries.deletePendingByEntityId(result.entityId)

                        // Mark entity as synced
                        markEntityAsSynced(result.entityId)
                    } else if (result.conflict) {
                        // Handle conflict - for now, just increment retry count
                        val pending = pendingChanges.find { it.entityId == result.entityId }
                        if (pending != null) {
                            database.pendingSyncQueries.incrementRetryCount(
                                lastError = result.errorMessage ?: "Conflict",
                                id = pending.id
                            )
                        }
                    }
                }

                response.failureCount == 0
            } catch (e: Exception) {
                logger.e(e) { "Exception during pushLocalChanges: ${e.message}" }
                false
            }
        }
    }

    /**
     * Pull changes from the server and apply them locally.
     */
    private suspend fun pullRemoteChanges(): Int {
        return withContext(Dispatchers.Default) {
            try {
                var totalItemsSynced = 0

                // Get last sync ID for each entity type
                val studentLastSyncId = database.syncMetadataQueries
                    .getLastSyncId("STUDENT")
                    .executeAsOneOrNull()
                    ?.lastSyncLogId ?: 0L

                val classLastSyncId = database.syncMetadataQueries
                    .getLastSyncId("SCHOOL_CLASS")
                    .executeAsOneOrNull()
                    ?.lastSyncLogId ?: 0L

                // Pull student changes
                var response = syncApi.getChanges(sinceLogId = studentLastSyncId)
                totalItemsSynced += applyChanges(response.changes)

                // Update last sync ID
                if (response.changes.isNotEmpty()) {
                    database.syncMetadataQueries.updateLastSyncId(
                        lastSyncLogId = response.lastSyncId,
                        lastSyncTimestamp = currentTimeMillis(),
                        entityType = "STUDENT"
                    )
                }

                // Pull class changes
                response = syncApi.getChanges(sinceLogId = classLastSyncId)
                totalItemsSynced += applyChanges(response.changes)

                // Update last sync ID
                if (response.changes.isNotEmpty()) {
                    database.syncMetadataQueries.updateLastSyncId(
                        lastSyncLogId = response.lastSyncId,
                        lastSyncTimestamp = currentTimeMillis(),
                        entityType = "SCHOOL_CLASS"
                    )
                }

                totalItemsSynced
            } catch (e: Exception) {
                0
            }
        }
    }

    /**
     * Apply remote changes to local database.
     */
    private suspend fun applyChanges(changes: List<SyncChangeDto>): Int {
        var appliedCount = 0

        for (change in changes) {
            try {
                when (change.entityType) {
                    "STUDENT" -> applyStudentChange(change)
                    "SCHOOL_CLASS" -> applySchoolClassChange(change)
                }
                appliedCount++
            } catch (e: Exception) {
                // Log error but continue processing other changes
            }
        }

        return appliedCount
    }

    /**
     * Apply a student change to local database.
     */
    private suspend fun applyStudentChange(change: SyncChangeDto) {
        when (change.operation) {
            "CREATE", "UPDATE" -> {
                // TODO: Parse data and insert/update student
                // For now, just mark as synced if exists
            }
            "DELETE" -> {
                database.studentQueries.deleteStudent(change.entityId)
            }
        }
    }

    /**
     * Apply a school class change to local database.
     */
    private suspend fun applySchoolClassChange(change: SyncChangeDto) {
        when (change.operation) {
            "CREATE", "UPDATE" -> {
                // TODO: Parse data and insert/update class
                // For now, just mark as synced if exists
            }
            "DELETE" -> {
                database.schoolClassQueries.deleteClass(change.entityId)
            }
        }
    }

    /**
     * Mark an entity as synced in the local database.
     */
    private suspend fun markEntityAsSynced(entityId: String) {
        try {
            database.studentQueries.markAsSynced(entityId)
        } catch (e: Exception) {
            try {
                database.schoolClassQueries.markAsSynced(entityId)
            } catch (e: Exception) {
                // Entity not found
            }
        }
    }

    /**
     * Get the count of pending changes.
     */
    private fun getPendingChangesCount(): Int {
        return try {
            database.pendingSyncQueries.countPending().executeAsOne().toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Fetch entity data from local database as JSON string.
     */
    private fun fetchEntityData(entityType: String, entityId: String): String? {
        return try {
            val json = Json { encodeDefaults = true }
            when (entityType) {
                "STUDENT" -> {
                    val student = database.studentQueries.getStudentById(entityId).executeAsOneOrNull()
                    student?.let {
                        val dto = StudentDto(
                            id = it.id,
                            schoolId = it.schoolId,
                            firstName = it.firstName,
                            lastName = it.lastName,
                            classIds = emptyList(),
                            version = it.version,
                            createdAt = it.createdAt.toString(),
                            updatedAt = it.updatedAt.toString()
                        )
                        json.encodeToString(dto)
                    }
                }
                "SCHOOL_CLASS" -> {
                    val schoolClass = database.schoolClassQueries.getClassById(entityId).executeAsOneOrNull()
                    schoolClass?.let {
                        val dto = SchoolClassDto(
                            id = it.id,
                            schoolId = it.schoolId,
                            name = it.name,
                            alternativeName = it.alternativeName,
                            studentCount = 0,
                            version = it.version,
                            createdAt = it.createdAt.toString(),
                            updatedAt = it.updatedAt.toString()
                        )
                        json.encodeToString(dto)
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get entity version from local database.
     */
    private fun getEntityVersion(entityType: String, entityId: String): Long {
        return try {
            when (entityType) {
                "STUDENT" -> {
                    database.studentQueries.getStudentById(entityId).executeAsOneOrNull()?.version ?: 1L
                }
                "SCHOOL_CLASS" -> {
                    database.schoolClassQueries.getClassById(entityId).executeAsOneOrNull()?.version ?: 1L
                }
                else -> 1L
            }
        } catch (e: Exception) {
            1L
        }
    }
}
