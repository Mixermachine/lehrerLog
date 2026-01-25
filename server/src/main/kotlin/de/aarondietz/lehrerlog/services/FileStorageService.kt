package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.FileMetadataDto
import de.aarondietz.lehrerlog.db.tables.SubmissionFiles
import de.aarondietz.lehrerlog.db.tables.TaskFiles
import de.aarondietz.lehrerlog.db.tables.TaskSubmissions
import de.aarondietz.lehrerlog.db.tables.TaskTargets
import de.aarondietz.lehrerlog.db.tables.Tasks
import de.aarondietz.lehrerlog.db.tables.ParentStudentLinks
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class FileStorageService(
    private val storageService: StorageService = StorageService(),
    private val basePath: Path = defaultBasePath()
) {

    fun storeTaskFile(taskId: UUID, schoolId: UUID, userId: UUID, fileName: String, contentType: String, sizeBytes: Long, input: java.io.InputStream): FileMetadataDto {
        ensureTaskInSchool(taskId, schoolId)

        val quota = storageService.reserveBytes(userId, schoolId, sizeBytes)
        val fileId = UUID.randomUUID()
        val objectKey = "tasks/$taskId/$fileId"
        val targetPath = basePath.resolve(objectKey)

        return try {
            Files.createDirectories(targetPath.parent)
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)

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
            Files.deleteIfExists(targetPath)
            throw e
        }
    }

    fun storeSubmissionFile(submissionId: UUID, schoolId: UUID, userId: UUID, fileName: String, contentType: String, sizeBytes: Long, input: java.io.InputStream): FileMetadataDto {
        ensureSubmissionInSchool(submissionId, schoolId)

        storageService.reserveBytes(userId, schoolId, sizeBytes)
        val fileId = UUID.randomUUID()
        val objectKey = "submissions/$submissionId/$fileId"
        val targetPath = basePath.resolve(objectKey)

        return try {
            Files.createDirectories(targetPath.parent)
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)

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
            Files.deleteIfExists(targetPath)
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
                ResolvedFile(basePath.resolve(row[TaskFiles.objectKey]), row[TaskFiles.mimeType], row[TaskFiles.sizeBytes])
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
                ResolvedFile(basePath.resolve(row[SubmissionFiles.objectKey]), row[SubmissionFiles.mimeType], row[SubmissionFiles.sizeBytes])
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
                ResolvedFile(basePath.resolve(row[TaskFiles.objectKey]), row[TaskFiles.mimeType], row[TaskFiles.sizeBytes])
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
                ResolvedFile(basePath.resolve(row[SubmissionFiles.objectKey]), row[SubmissionFiles.mimeType], row[SubmissionFiles.sizeBytes])
            }

        submissionFile
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

    data class ResolvedFile(
        val path: Path,
        val mimeType: String,
        val sizeBytes: Long
    )

}

private fun defaultBasePath(): Path {
    val env = System.getenv("FILE_STORAGE_PATH")?.trim()
    return if (!env.isNullOrBlank()) {
        Path.of(env)
    } else {
        Path.of("data", "files")
    }
}
