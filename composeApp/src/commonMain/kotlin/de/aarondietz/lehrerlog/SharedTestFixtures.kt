package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.data.LatePeriodSummaryDto
import de.aarondietz.lehrerlog.data.LateStatus
import de.aarondietz.lehrerlog.data.LateStudentStatsDto
import de.aarondietz.lehrerlog.data.ParentInviteCreateResponse
import de.aarondietz.lehrerlog.data.ParentInviteDto
import de.aarondietz.lehrerlog.data.ParentInviteStatus
import de.aarondietz.lehrerlog.data.ParentLinkDto
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import de.aarondietz.lehrerlog.data.SchoolClass
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.Student
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionType
import androidx.compose.runtime.mutableStateListOf

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
}
