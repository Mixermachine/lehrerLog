package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.screens.students.ParentLinksDialog
import de.aarondietz.lehrerlog.ui.screens.students.ParentLinksUiState
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ParentScreensRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureParentStudents() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val state = ParentStudentsUiState(
            students = listOf(student),
            selectedStudentId = student.id,
            isLoading = false,
            errorMessage = null
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentStudentsScreenContent(
                    state = state,
                    onSelectStudent = {},
                    onRefresh = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioParentStudents
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureParentAssignments() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId)
        val state = ParentAssignmentsUiState(
            students = listOf(student),
            selectedStudentId = student.id,
            assignments = listOf(task),
            isLoading = false,
            errorMessage = null
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentAssignmentsScreenContent(state = state)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioParentAssignments
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureParentSubmissions() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val submission = SharedTestFixtures.testSubmissionDto(
            taskId = SharedTestFixtures.testTaskId,
            studentId = student.id
        )
        val state = ParentSubmissionsUiState(
            students = listOf(student),
            selectedStudentId = student.id,
            submissions = listOf(submission),
            isLoading = false,
            errorMessage = null
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentSubmissionsScreenContent(state = state)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioParentSubmissions
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureParentLinksDialog() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val link = SharedTestFixtures.testParentLinkDto()
        val state = ParentLinksUiState(
            links = listOf(link),
            isLoading = false,
            error = null,
            studentId = SharedTestFixtures.testStudentId
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentLinksDialog(
                    state = state,
                    onDismiss = {},
                    onRevoke = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioParentLinks
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
