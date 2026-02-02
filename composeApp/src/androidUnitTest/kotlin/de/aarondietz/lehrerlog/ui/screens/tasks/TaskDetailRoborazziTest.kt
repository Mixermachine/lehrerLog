package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
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
class TaskDetailRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureTaskDetailDialog() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)
        val submission = SharedTestFixtures.testSubmissionDto(task.id, student.id)

        composeTestRule.setContent {
            LehrerLogTheme {
                TaskDetailDialog(
                    state = TaskDetailState(
                        task = task,
                        students = listOf(student),
                        submissions = listOf(submission)
                    ),
                    onDismiss = {},
                    onRefresh = {},
                    onEditTask = { _, _, _, _ -> },
                    onDeleteTask = {},
                    onMarkInPerson = {},
                    onUpdateSubmission = { _, _, _ -> },
                    onUploadAssignmentFile = {},
                    onUploadSubmissionFile = { _, _, _ -> }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioTaskDetail
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
