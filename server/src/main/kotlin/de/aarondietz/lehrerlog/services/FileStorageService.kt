package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.FileMetadataDto
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import de.aarondietz.lehrerlog.db.tables.*
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class FileStorageService(
    private val storageService: StorageService = StorageService(),
    private val basePath: Path = defaultBasePath(),
    private val objectStorageClient: ObjectStorageClient? = ObjectStorageClient.fromEnv()
) {

    fun storeTaskFile(
        taskId: UUID,
        schoolId: UUID,
        userId: UUID,
        fileName: String,
        contentType: String,
        sizeBytes: Long,
        input: java.io.InputStream
    ): FileMetadataDto {
        ensureTaskInSchool(taskId, schoolId)

        val quota = storageService.reserveBytes(userId, schoolId, sizeBytes)
        val fileId = UUID.randomUUID()
        val objectKey = "tasks/$taskId/$fileId"
        return try {
            if (objectStorageClient != null) {
                objectStorageClient.putObject(objectKey, contentType, sizeBytes, input)
            } else {
                val targetPath = basePath.resolve(objectKey)
                Files.createDirectories(targetPath.parent)
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }

            transaction {
                TaskFiles.insert {
                    it[id] = fileId
                    it[TaskFiles.taskId] = taskId
                    it[TaskFiles.objectKey] = objectKey
                    it[TaskFiles.sizeBytes] = sizeBytes
                    it[TaskFiles.mimeType] = contentType
                }
            }

            FileMetadataDto(
                id = fileId.toString(),
                objectKey = objectKey,
                sizeBytes = sizeBytes,
                mimeType = contentType,
                createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
            )
        } catch (e: Exception) {
            storageService.releaseBytes(userId, schoolId, sizeBytes)
            if (objectStorageClient == null) {
                val targetPath = basePath.resolve(objectKey)
                Files.deleteIfExists(targetPath)
            }
            throw e
        }
    }

    fun storeSubmissionFile(
        submissionId: UUID,
        schoolId: UUID,
        userId: UUID,
        fileName: String,
        contentType: String,
        sizeBytes: Long,
        input: java.io.InputStream
    ): FileMetadataDto {
        ensureSubmissionInSchool(submissionId, schoolId)

        storageService.reserveBytes(userId, schoolId, sizeBytes)
        val fileId = UUID.randomUUID()
        val objectKey = "submissions/$submissionId/$fileId"
        return try {
            if (objectStorageClient != null) {
                objectStorageClient.putObject(objectKey, contentType, sizeBytes, input)
            } else {
                val targetPath = basePath.resolve(objectKey)
                Files.createDirectories(targetPath.parent)
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }

            transaction {
                SubmissionFiles.insert {
                    it[id] = fileId
                    it[SubmissionFiles.submissionId] = submissionId
                    it[SubmissionFiles.objectKey] = objectKey
                    it[SubmissionFiles.sizeBytes] = sizeBytes
                    it[SubmissionFiles.mimeType] = contentType
                }
            }

            FileMetadataDto(
                id = fileId.toString(),
                objectKey = objectKey,
                sizeBytes = sizeBytes,
                mimeType = contentType,
                createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
            )
        } catch (e: Exception) {
            storageService.releaseBytes(userId, schoolId, sizeBytes)
            if (objectStorageClient == null) {
                val targetPath = basePath.resolve(objectKey)
                Files.deleteIfExists(targetPath)
            }
            throw e
        }
    }

    fun resolveFile(fileId: UUID, schoolId: UUID): ResolvedFile? = transaction {
        val taskFile = TaskFiles.selectAll()
            .where { TaskFiles.id eq fileId }
            .firstOrNull()
            ?.let { row ->
                val taskId = row[TaskFiles.taskId].value
                val taskSchoolId = Tasks.selectAll()
                    .where { Tasks.id eq taskId }
                    .firstOrNull()
                    ?.get(Tasks.schoolId)
                    ?.value
                    ?: return@let null
                if (taskSchoolId != schoolId) {
                    return@let null
                }
                ResolvedFile(
                    location = resolveLocation(row[TaskFiles.objectKey]),
                    mimeType = row[TaskFiles.mimeType],
                    sizeBytes = row[TaskFiles.sizeBytes],
                    source = FileSource.TASK
                )
            }

        if (taskFile != null) {
            return@transaction taskFile
        }

        val submissionFile = SubmissionFiles.selectAll()
            .where { SubmissionFiles.id eq fileId }
            .firstOrNull()
            ?.let { row ->
                val submissionId = row[SubmissionFiles.submissionId]
                val submissionRow = TaskSubmissions.selectAll()
                    .where { TaskSubmissions.id eq submissionId }
                    .firstOrNull()
                    ?: return@let null
                val taskId = submissionRow[TaskSubmissions.taskId].value
                val taskSchoolId = Tasks.selectAll()
                    .where { Tasks.id eq taskId }
                    .firstOrNull()
                    ?.get(Tasks.schoolId)
                    ?.value
                    ?: return@let null
                if (taskSchoolId != schoolId) {
                    return@let null
                }
                ResolvedFile(
                    location = resolveLocation(row[SubmissionFiles.objectKey]),
                    mimeType = row[SubmissionFiles.mimeType],
                    sizeBytes = row[SubmissionFiles.sizeBytes],
                    source = FileSource.SUBMISSION
                )
            }

        submissionFile
    }

    fun resolveFileForParent(fileId: UUID, parentUserId: UUID): ResolvedFile? = transaction {
        val taskFile = TaskFiles.selectAll()
            .where { TaskFiles.id eq fileId }
            .firstOrNull()
            ?.let { row ->
                val taskId = row[TaskFiles.taskId].value
                val hasAccess = TaskTargets
                    .join(
                        ParentStudentLinks,
                        JoinType.INNER,
                        TaskTargets.studentId,
                        ParentStudentLinks.studentId
                    )
                    .select(TaskTargets.taskId)
                    .where {
                        (TaskTargets.taskId eq taskId) and
                                (ParentStudentLinks.parentUserId eq parentUserId) and
                                (ParentStudentLinks.status eq ParentLinkStatus.ACTIVE.name)
                    }
                    .any()
                if (!hasAccess) {
                    return@let null
                }
                ResolvedFile(
                    location = resolveLocation(row[TaskFiles.objectKey]),
                    mimeType = row[TaskFiles.mimeType],
                    sizeBytes = row[TaskFiles.sizeBytes],
                    source = FileSource.TASK
                )
            }

        if (taskFile != null) {
            return@transaction taskFile
        }

        val submissionFile = SubmissionFiles.selectAll()
            .where { SubmissionFiles.id eq fileId }
            .firstOrNull()
            ?.let { row ->
                val submissionId = row[SubmissionFiles.submissionId]
                val submissionRow = TaskSubmissions.selectAll()
                    .where { TaskSubmissions.id eq submissionId }
                    .firstOrNull()
                    ?: return@let null
                val studentId = submissionRow[TaskSubmissions.studentId].value
                val hasAccess = ParentStudentLinks
                    .select(ParentStudentLinks.id)
                    .where {
                        (ParentStudentLinks.parentUserId eq parentUserId) and
                                (ParentStudentLinks.studentId eq studentId) and
                                (ParentStudentLinks.status eq ParentLinkStatus.ACTIVE.name)
                    }
                    .any()
                if (!hasAccess) {
                    return@let null
                }
                ResolvedFile(
                    location = resolveLocation(row[SubmissionFiles.objectKey]),
                    mimeType = row[SubmissionFiles.mimeType],
                    sizeBytes = row[SubmissionFiles.sizeBytes],
                    source = FileSource.SUBMISSION
                )
            }

        submissionFile
    }

    fun getTaskFileMetadata(taskId: UUID, schoolId: UUID?): FileMetadataDto? {
        return transaction {
            TaskFiles.selectAll()
                .where { TaskFiles.taskId eq taskId }
                .firstOrNull()
                ?.let { row ->
                    // Verify task belongs to school if schoolId provided
                    if (schoolId != null) {
                        val taskBelongsToSchool = Tasks.selectAll()
                            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
                            .count() > 0
                        if (!taskBelongsToSchool) {
                            return@transaction null
                        }
                    }

                    FileMetadataDto(
                        id = row[TaskFiles.id].toString(),
                        objectKey = row[TaskFiles.objectKey],
                        sizeBytes = row[TaskFiles.sizeBytes],
                        mimeType = row[TaskFiles.mimeType],
                        createdAt = row[TaskFiles.createdAt].toString()
                    )
                }
        }
    }

    private fun ensureTaskInSchool(taskId: UUID, schoolId: UUID) {
        val exists = transaction {
            Tasks.selectAll()
                .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
                .count() > 0
        }
        if (!exists) {
            throw IllegalArgumentException("Task not found")
        }
    }

    private fun ensureSubmissionInSchool(submissionId: UUID, schoolId: UUID) {
        val exists = transaction {
            val submission = TaskSubmissions.selectAll()
                .where { TaskSubmissions.id eq submissionId }
                .firstOrNull() ?: return@transaction false
            val taskId = submission[TaskSubmissions.taskId].value
            Tasks.selectAll()
                .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
                .count() > 0
        }
        if (!exists) {
            throw IllegalArgumentException("Submission not found")
        }
    }

    enum class FileSource {
        TASK,
        SUBMISSION
    }

    sealed class FileLocation {
        data class Local(val path: Path) : FileLocation()
        data class ObjectStorage(val objectKey: String) : FileLocation()
    }

    data class ResolvedFile(
        val location: FileLocation,
        val mimeType: String,
        val sizeBytes: Long,
        val source: FileSource
    )

    fun openStream(resolvedFile: ResolvedFile): java.io.InputStream {
        return when (val location = resolvedFile.location) {
            is FileLocation.Local -> Files.newInputStream(location.path)
            is FileLocation.ObjectStorage -> {
                val client = objectStorageClient
                    ?: throw IllegalStateException("Object storage is not configured.")
                client.openObjectStream(location.objectKey)
            }
        }
    }

    private fun resolveLocation(objectKey: String): FileLocation {
        return if (objectStorageClient != null) {
            FileLocation.ObjectStorage(objectKey)
        } else {
            FileLocation.Local(basePath.resolve(objectKey))
        }
    }
}

private fun defaultBasePath(): Path {
    val env = System.getenv("FILE_STORAGE_PATH")?.trim()
    return if (!env.isNullOrBlank()) {
        Path.of(env)
    } else {
        Path.of("data", "files")
    }
}
