package de.aarondietz.lehrerlog.ui.screens.parent_management

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.ParentInviteRepository
import de.aarondietz.lehrerlog.data.repository.ParentLinksRepository
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
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
class ParentInviteManagementScreenRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun createViewModel(): ParentInviteManagementViewModel {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val inviteResponse = SharedTestFixtures.testParentInviteCreateResponse()
        val parentLink = SharedTestFixtures.testParentLinkDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/parent-invites" -> respondJson(json.encodeToString(inviteResponse))
                "/api/parent-links" -> respondJson(json.encodeToString(listOf(parentLink)))
                "/api/parent-links/${parentLink.id}/revoke" -> respondJson("{}")
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        return ParentInviteManagementViewModel(
            parentInviteRepository = ParentInviteRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            parentLinksRepository = ParentLinksRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        )
    }

    @Test
    fun captureParentInviteManagementScreen() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()
        val studentName = "${SharedTestFixtures.testStudentFirstName} ${SharedTestFixtures.testStudentLastName}"

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentInviteManagementScreen(
                    studentId = SharedTestFixtures.testStudentId,
                    studentName = studentName,
                    onBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.runOnIdle {
            viewModel.generateInvite(SharedTestFixtures.testStudentId)
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioParentInviteManagement
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
