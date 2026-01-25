package de.aarondietz.lehrerlog

import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.StudentDto

object SharedTestFixtures {
    const val snapshotRoot = "src/commonTest/snapshots"
    const val roborazziSmokeTest = "RoborazziSmokeTest"
    const val scenarioEmpty = "Empty"
    const val scenarioClassCardExpanded = "ClassCardExpanded"
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
}
