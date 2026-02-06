package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.aarondietz.lehrerlog.InMemoryTokenStorage
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.createTestHttpClient
import de.aarondietz.lehrerlog.data.repository.SchoolClassRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import de.aarondietz.lehrerlog.data.repository.TaskRepository
import de.aarondietz.lehrerlog.respondJson
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TasksInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun createViewModel(): TasksViewModel {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)
        val summary = SharedTestFixtures.testTaskSubmissionSummaryDto(task.id)
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        val submission = SharedTestFixtures.testSubmissionDto(task.id, student.id)
        val user = SharedTestFixtures.testUserDto(SharedTestFixtures.testSchoolId)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/auth/me" -> respondJson(json.encodeToString(user))
                "/api/classes" -> respondJson(json.encodeToString(listOf(schoolClass)))
                "/api/tasks" -> respondJson(json.encodeToString(listOf(task)))
                "/api/tasks/${task.id}/summary" -> respondJson(json.encodeToString(summary))
                "/api/tasks/${task.id}/submissions" -> {
                    if (request.method == HttpMethod.Post) {
                        respondJson(json.encodeToString(submission))
                    } else {
                        respondJson(json.encodeToString(listOf(submission)))
                    }
                }

                "/api/students" -> respondJson(json.encodeToString(listOf(student)))
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        return TasksViewModel(
            taskRepository = TaskRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            schoolClassRepository = SchoolClassRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            studentRepository = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            authRepository = AuthRepository(httpClient, tokenStorage)
        )
    }

    @Test
    fun tasksScreen_primaryInteractionsAreReachable() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val task = SharedTestFixtures.testTaskDto(schoolClass.id)

        composeTestRule.setContent {
            LehrerLogTheme {
                TasksScreen(viewModel = viewModel)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UiTestTags.tasksClassSelectorToggle).performClick()
        runCatching {
            composeTestRule.onNodeWithTag(UiTestTags.tasksClassMenuItem(schoolClass.id)).performClick()
        }
        runCatching {
            composeTestRule.onNodeWithTag(UiTestTags.tasksCard(task.id)).performClick()
        }
        composeTestRule.onNodeWithTag(UiTestTags.tasksAddFab).performClick()
        runCatching {
            composeTestRule.onNodeWithTag(UiTestTags.addTaskDialogTitleField).fetchSemanticsNode()
            composeTestRule.onNodeWithTag(UiTestTags.addTaskDialogCancelButton).performClick()
        }
    }
}
