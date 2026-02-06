package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ParentFlowInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun parentStudents_selectionRowIsClickable() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        var selectedStudentId: String? = null

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentStudentsScreenContent(
                    state = ParentStudentsUiState(students = listOf(student)),
                    onSelectStudent = { selectedStudentId = it },
                    onRefresh = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(UiTestTags.parentStudentItem(student.id)).performClick()
        assertEquals(student.id, selectedStudentId)
    }

    @Test
    fun parentAssignments_rendersAssignmentTitle() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId)

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentAssignmentsScreenContent(
                    state = ParentAssignmentsUiState(
                        selectedStudentId = SharedTestFixtures.testStudentId,
                        assignments = listOf(task)
                    )
                )
            }
        }

        assertTrue(
            runCatching {
                composeTestRule.onNodeWithText(SharedTestFixtures.testTaskTitle).fetchSemanticsNode()
            }.isSuccess
        )
    }

    @Test
    fun parentSubmissions_rendersSubmissionDetails() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val submission = SharedTestFixtures
            .testSubmissionDto(SharedTestFixtures.testTaskId, SharedTestFixtures.testStudentId)
            .copy(
                grade = SharedTestFixtures.testVersion.toDouble(),
                note = SharedTestFixtures.testTaskDescription
            )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentSubmissionsScreenContent(
                    state = ParentSubmissionsUiState(
                        selectedStudentId = SharedTestFixtures.testStudentId,
                        submissions = listOf(submission)
                    )
                )
            }
        }

        assertTrue(
            runCatching {
                composeTestRule.onNodeWithText(SharedTestFixtures.testTaskDescription, substring = true).fetchSemanticsNode()
            }.isSuccess
        )
    }
}
