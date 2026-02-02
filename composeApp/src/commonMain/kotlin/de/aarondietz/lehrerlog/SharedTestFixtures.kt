package de.aarondietz.lehrerlog

import androidx.compose.runtime.mutableStateListOf
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.logging.LogFileEntry
import de.aarondietz.lehrerlog.logging.LogOverview

/**
 * Roborazzi test utilities
 */
object SharedTestFixtures {
    const val snapshotRoot = "src/commonTest/snapshots"
    const val roborazziSmokeTest = "RoborazziSmokeTest"
    const val scenarioEmpty = "Empty"
    const val scenarioClassCardExpanded = "ClassCardExpanded"
    const val scenarioParentStudents = "ParentStudents"
    const val scenarioParentAssignments = "ParentAssignments"
    const val scenarioParentSubmissions = "ParentSubmissions"
    const val scenarioParentInviteRedeem = "ParentInviteRedeem"
    const val scenarioParentInviteDialog = "ParentInviteDialog"
    const val scenarioParentLinks = "ParentLinks"
    const val scenarioLateOverview = "LateOverview"
    const val scenarioTaskDetail = "TaskDetail"
    const val scenarioSettingsLogs = "SettingsLogs"
    const val scenarioLogin = "Login"
    const val scenarioLoginError = "LoginError"
    const val scenarioRegister = "Register"
    const val scenarioRegisterError = "RegisterError"
    const val scenarioLatePeriodManagement = "LatePeriodManagement"
    const val scenarioParentInviteManagement = "ParentInviteManagement"
    const val scenarioTasksScreen = "TasksScreen"
    const val roborazziTitle = "Roborazzi Snapshot"
    const val roborazziFieldLabel = "Input Label"
    const val roborazziFieldValue = "Example Value"

    const val testSchoolId = "school-001"
    const val testClassId = "class-001"
    const val testStudentId = "student-001"
    const val testClassName = "Class 1A"
    const val testStudentFirstName = "Test"
    const val testStudentLastName = "Student"
    const val testCreatedAt = "2026-01-01T00:00:00Z"
    const val testUpdatedAt = "2026-01-01T00:00:00Z"
    const val testVersion = 1L
    const val testTaskId = "task-001"
    const val testSubmissionId = "submission-001"
    const val testTaskTitle = "Math Homework"
    const val testTaskDescription = "Solve exercises 1-5"
    const val testDueAt = "2026-01-05"
    const val testSubmittedAt = "2026-01-04T10:00:00Z"
    const val testParentInviteCode = "AB12CD34"
    const val testParentInviteEmail = "parent@example.com"
    const val testParentInvitePassword = "ParentPass123!"
    const val testParentInviteFirstName = "Pat"
    const val testParentInviteLastName = "Guardian"
    const val testParentLinkId = "parent-link-001"
    const val testParentUserId = "parent-user-001"
    const val testParentInviteId = "parent-invite-001"
    const val testLatePeriodId = "period-001"
    const val testLatePeriodName = "Semester 1"
    const val testLatePeriodStartsAt = "2026-01-01T00:00:00Z"
    const val testLoginEmail = "teacher@example.com"
    const val testLoginPassword = "Password123!"
    const val testLoginError = "Invalid credentials."
    const val testSchoolQuery = "Gymnasium Musterstadt"
    const val testRegisterError = "Passwords do not match."
    const val testAuthUserId = "auth-user-001"
    const val testAuthFirstName = "Alex"
    const val testAuthLastName = "Teacher"
    const val testAuthRole = "TEACHER"
    const val testAuthAccessToken = "access-token-001"
    const val testAuthRefreshToken = "refresh-token-001"
    const val testAuthExpiresIn = 900L
    const val testBaseUrl = "http://localhost"
    const val testSchoolSearchCode = "TEST-SCHOOL"
    const val testSchoolSearchName = "Test School"
    const val testSchoolSearchCity = "Test City"
    const val testSchoolSearchPostcode = "12345"
    const val testLogTimestamp = "2026-01-01T00:00:00Z"
    const val testLogLevel = "INFO"
    const val testLogTag = "TestLogger"
    const val testLogMessage = "Log entry for diagnostics."
    const val testLogFileName = "app_2026-01-01.log"
    const val testLogFilePath = "app_2026-01-01.log"
    const val testLogFileSizeBytes = 2048L
    const val testLogFileModifiedAt = 1735689600000L
    const val testLogTotalSizeBytes = 4096L
    const val testLogCurrentSizeBytes = 2048L
    const val testLogPreviewLine = "2026-01-01T00:00:00Z [INFO] [TestLogger] Log entry for diagnostics."
    const val testLogPreviewLineSecondary = "2026-01-01T00:00:01Z [WARN] [TestLogger] Second log line."
    const val testStorageOwnerId = "storage-owner-001"
    const val testStoragePlanId = "storage-plan-001"
    const val testStoragePlanName = "Standard"
    const val testStorageMaxTotalBytes = 10_000_000L
    const val testStorageMaxFileBytes = 2_000_000L
    const val testStorageUsedBytes = 2_000_000L
    const val testStorageRemainingBytes = 8_000_000L
    const val testTaskSummaryTotalStudents = 2
    const val testTaskSummarySubmittedStudents = 1
    const val testPunishmentId = "punishment-001"
    const val testPunishmentTriggeredAt = "2026-01-02T10:00:00Z"
    const val testFileId = "file-001"
    const val testFileObjectKey = "tasks/task-001/file-001"
    const val testFileContentType = "application/pdf"
    const val testFileCreatedAt = "2026-01-01T00:00:00Z"
    const val testFileName = "assignment.pdf"
    val testFileBytes = "file-content".encodeToByteArray()

    fun snapshotPath(testClass: String, scenario: String): String {
        return "$snapshotRoot/${testClass}_${scenario}.png"
    }

    fun testSchoolClassDto(): SchoolClassDto {
        return SchoolClassDto(
            id = testClassId,
            schoolId = testSchoolId,
            name = testClassName,
            alternativeName = null,
            studentCount = 1,
            version = testVersion,
            createdAt = testCreatedAt,
            updatedAt = testUpdatedAt
        )
    }

    fun testStudent(): Student {
        return Student(
            id = testStudentId,
            firstName = testStudentFirstName,
            lastName = testStudentLastName
        )
    }

    fun testSchoolClass(): SchoolClass {
        return SchoolClass(
            id = testClassId,
            name = testClassName,
            students = mutableStateListOf(testStudent())
        )
    }

    fun testStudentDto(classId: String): StudentDto {
        return StudentDto(
            id = testStudentId,
            schoolId = testSchoolId,
            firstName = testStudentFirstName,
            lastName = testStudentLastName,
            classIds = listOf(classId),
            version = testVersion,
            createdAt = testCreatedAt,
            updatedAt = testUpdatedAt
        )
    }

    fun testTaskDto(classId: String): TaskDto {
        return TaskDto(
            id = testTaskId,
            schoolId = testSchoolId,
            schoolClassId = classId,
            title = testTaskTitle,
            description = testTaskDescription,
            dueAt = testDueAt,
            version = testVersion,
            createdAt = testCreatedAt,
            updatedAt = testUpdatedAt
        )
    }

    fun testSubmissionDto(taskId: String, studentId: String): TaskSubmissionDto {
        return TaskSubmissionDto(
            id = testSubmissionId,
            taskId = taskId,
            studentId = studentId,
            submissionType = TaskSubmissionType.FILE,
            grade = null,
            note = null,
            lateStatus = LateStatus.ON_TIME,
            latePeriodId = null,
            decidedBy = null,
            decidedAt = null,
            submittedAt = testSubmittedAt,
            createdAt = testCreatedAt,
            updatedAt = testUpdatedAt
        )
    }

    fun testParentLinkDto(): ParentLinkDto {
        return ParentLinkDto(
            id = testParentLinkId,
            parentUserId = testParentUserId,
            studentId = testStudentId,
            status = ParentLinkStatus.ACTIVE,
            createdAt = testCreatedAt,
            revokedAt = null
        )
    }

    fun testParentInviteCreateResponse(): ParentInviteCreateResponse {
        return ParentInviteCreateResponse(
            invite = ParentInviteDto(
                id = testParentInviteId,
                studentId = testStudentId,
                status = ParentInviteStatus.ACTIVE,
                expiresAt = testUpdatedAt,
                createdAt = testCreatedAt
            ),
            code = testParentInviteCode
        )
    }

    fun testLatePeriodDto(): LatePeriodDto {
        return LatePeriodDto(
            id = testLatePeriodId,
            name = testLatePeriodName,
            startsAt = testLatePeriodStartsAt,
            endsAt = null,
            isActive = true,
            createdAt = testCreatedAt
        )
    }

    fun testLatePeriodSummaryDto(): LatePeriodSummaryDto {
        return LatePeriodSummaryDto(
            periodId = testLatePeriodId,
            name = testLatePeriodName,
            startsAt = testLatePeriodStartsAt,
            endsAt = null,
            totalMissed = 2,
            totalPunishments = 1
        )
    }

    fun testLateStudentStatsDto(studentId: String): LateStudentStatsDto {
        return LateStudentStatsDto(
            studentId = studentId,
            totalMissed = 2,
            missedSincePunishment = 1,
            punishmentRequired = true
        )
    }

    fun testFileMetadataDto(): FileMetadataDto {
        return FileMetadataDto(
            id = testFileId,
            objectKey = testFileObjectKey,
            sizeBytes = testFileBytes.size.toLong(),
            mimeType = testFileContentType,
            createdAt = testFileCreatedAt
        )
    }

    fun testLogOverview(): LogOverview {
        return LogOverview(
            files = listOf(
                LogFileEntry(
                    name = testLogFileName,
                    path = testLogFilePath,
                    sizeBytes = testLogFileSizeBytes,
                    lastModifiedAt = testLogFileModifiedAt
                )
            ),
            totalSizeBytes = testLogTotalSizeBytes,
            currentFileSizeBytes = testLogCurrentSizeBytes,
            previewLines = listOf(testLogPreviewLine, testLogPreviewLineSecondary)
        )
    }

    fun testUserDto(schoolId: String? = testSchoolId): de.aarondietz.lehrerlog.auth.UserDto {
        return de.aarondietz.lehrerlog.auth.UserDto(
            id = testAuthUserId,
            email = testLoginEmail,
            firstName = testAuthFirstName,
            lastName = testAuthLastName,
            role = testAuthRole,
            schoolId = schoolId
        )
    }

    fun testAuthResponse(schoolId: String? = testSchoolId): de.aarondietz.lehrerlog.auth.AuthResponse {
        return de.aarondietz.lehrerlog.auth.AuthResponse(
            accessToken = testAuthAccessToken,
            refreshToken = testAuthRefreshToken,
            expiresIn = testAuthExpiresIn,
            user = testUserDto(schoolId)
        )
    }

    fun testSchoolSearchResultDto(): SchoolSearchResultDto {
        return SchoolSearchResultDto(
            code = testSchoolSearchCode,
            name = testSchoolSearchName,
            city = testSchoolSearchCity,
            postcode = testSchoolSearchPostcode
        )
    }

    fun testTaskSubmissionSummaryDto(taskId: String): TaskSubmissionSummaryDto {
        return TaskSubmissionSummaryDto(
            taskId = taskId,
            totalStudents = testTaskSummaryTotalStudents,
            submittedStudents = testTaskSummarySubmittedStudents
        )
    }

    fun testStorageQuotaDto(): StorageQuotaDto {
        return StorageQuotaDto(
            ownerType = StorageOwnerType.TEACHER,
            ownerId = testStorageOwnerId,
            planId = testStoragePlanId,
            planName = testStoragePlanName,
            maxTotalBytes = testStorageMaxTotalBytes,
            maxFileBytes = testStorageMaxFileBytes,
            usedTotalBytes = testStorageUsedBytes,
            remainingBytes = testStorageRemainingBytes
        )
    }

    fun testPunishmentRecordDto(studentId: String, periodId: String): PunishmentRecordDto {
        return PunishmentRecordDto(
            id = testPunishmentId,
            studentId = studentId,
            periodId = periodId,
            triggeredAt = testPunishmentTriggeredAt,
            resolvedAt = null,
            resolvedBy = null,
            note = null
        )
    }
}
